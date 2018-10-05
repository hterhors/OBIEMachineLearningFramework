package de.hterhors.obie.tools.ml.templates;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.OntologyModelContent;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.core.tokenizer.Token;
import de.hterhors.obie.tools.ml.run.param.OBIERunParameter;
import de.hterhors.obie.tools.ml.templates.Word2VecClusterTemplate.Scope;
import de.hterhors.obie.tools.ml.templates.scope.OBIEFactorScope;
import de.hterhors.obie.tools.ml.utils.ReflectionUtils;
import de.hterhors.obie.tools.ml.variables.OBIEInstance;
import de.hterhors.obie.tools.ml.variables.OBIEState;
import de.hterhors.obie.tools.ml.variables.TemplateAnnotation;
import factors.Factor;
import learning.Vector;

public class Word2VecClusterTemplate extends AbstractOBIETemplate<Scope> {

	private static Logger log = LogManager.getFormatterLogger(Word2VecClusterTemplate.class.getName());

	final private Map<String, List<Integer>> clusters = new HashMap<>();

	public Word2VecClusterTemplate(OBIERunParameter parameter) {
		super(parameter);
		try {
			BufferedReader br = new BufferedReader(
					new FileReader(new File("wordvector/kmeans_google-news_200_ranking.vec")));

			String line = null;

			while ((line = br.readLine()) != null) {

				final String data[] = line.split(" ");

				clusters.put(data[0].toUpperCase(),
						Arrays.stream(data).skip(1).map(s -> Integer.parseInt(s.trim())).collect(Collectors.toList()));

			}

			br.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	class Scope extends OBIEFactorScope {

		final OBIEInstance instance;
		final String className;
		final String surfaceForm;
		final int beginTokenIndex;
		final int endTokenIndex;

		public Scope(Set<Class<? extends IOBIEThing>> influencedVariable,
				Class<? extends IOBIEThing> entityRootClassType, AbstractOBIETemplate<?> template,
				OBIEInstance instance, final String className, final String surfaceForm, final int beginTokenIndex,
				final int endTokenIndex) {
			super(influencedVariable, entityRootClassType, template, instance, className, surfaceForm, beginTokenIndex,
					endTokenIndex, entityRootClassType);
			this.instance = instance;
			this.className = className;
			this.beginTokenIndex = beginTokenIndex;
			this.endTokenIndex = endTokenIndex;
			this.surfaceForm = surfaceForm;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		state.getInstance().getTokens();

		for (TemplateAnnotation entity : state.getCurrentPrediction().getTemplateAnnotations()) {
			factors.addAll(
					addFactorRecursive(entity.rootClassType, state.getInstance(), entity.getTemplateAnnotation()));
		}
		return factors;
	}

	private List<Scope> addFactorRecursive(Class<? extends IOBIEThing> entityRootClassType, OBIEInstance instance,
			IOBIEThing scioClass) {
		List<Scope> factors = new ArrayList<>();

		if (scioClass == null)
			return factors;

		final String className = scioClass.getClass().getSimpleName();
		final Integer position = scioClass.getCharacterOnset();
		if (position != null && ReflectionUtils.isAnnotationPresent(scioClass.getClass(), DatatypeProperty.class)) {
			try {

				final String surfaceForm = scioClass.getTextMention();

				int beginTokenIndex = instance.charPositionToTokenPosition(scioClass.getCharacterOnset());
				int endTokenIndex = instance.charPositionToTokenPosition(scioClass.getCharacterOffset());

				final Set<Class<? extends IOBIEThing>> influencedVariables = new HashSet<>();
				influencedVariables.add(scioClass.getClass());

				factors.add(new Scope(influencedVariables, entityRootClassType, this, instance, className, surfaceForm,
						beginTokenIndex, endTokenIndex));
			} catch (Exception e) {
				e.printStackTrace();
				log.warn(scioClass.getTextMention());
				System.exit(1);
			}

		}
		/*
		 * Add factors for object type properties.
		 */
		Arrays.stream(scioClass.getClass().getDeclaredFields())
				.filter(f -> f.isAnnotationPresent(OntologyModelContent.class)).forEach(field -> {
					field.setAccessible(true);
					try {
						if (field.isAnnotationPresent(RelationTypeCollection.class)) {
							for (IOBIEThing element : (List<IOBIEThing>) field.get(scioClass)) {
								factors.addAll(addFactorRecursive(entityRootClassType, instance, element));
							}
						} else {
							factors.addAll(addFactorRecursive(entityRootClassType, instance,
									(IOBIEThing) field.get(scioClass)));
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				});
		return factors;
	}

	@Override
	public void computeFactor(Factor<Scope> factor) {
		Vector featureVector = factor.getFeatureVector();

		final List<Token> tokens = factor.getFactorScope().instance.getTokens();
		final String className = factor.getFactorScope().className;
		final String surfaceForm = factor.getFactorScope().surfaceForm;
		final int beginTokenIndex = factor.getFactorScope().beginTokenIndex;
		final int endTokenIndex = factor.getFactorScope().endTokenIndex;

		boolean isFirstToken = true;
		boolean isLastToken = false;
		final String data[] = surfaceForm.split(" ");
		int index = 1;
		for (String token : data) {

			isLastToken = index == data.length;

			if (clusters.containsKey(token)) {
				int nearestClusters = 0;
				for (Integer cluster : clusters.get(token)) {

					if (isFirstToken)
						featureVector.set(className + "FIRST TOKEN = " + cluster, true);

					if (isLastToken)
						featureVector.set(className + "LAST TOKEN = " + cluster, true);

					featureVector.set(className + " = " + cluster, true);
					nearestClusters++;
					if (nearestClusters == 10)
						break;
				}
			} else {
				// featureVector.set(className + "_UNKNOWN", true);
			}

			isFirstToken = false;
		}

		for (int i = 1; i < 4 && beginTokenIndex - i >= 0; i++) {
			final String token = tokens.get(beginTokenIndex - i).getText();

			if (clusters.containsKey(token)) {
				int nearestClusters = 0;
				for (Integer cluster : clusters.get(token)) {
					featureVector.set("LeftContextOf_" + className + " = " + cluster, true);
					nearestClusters++;
					if (nearestClusters == 10)
						break;
				}
			} else {
				// featureVector.set(className + "_UNKNOWN", true);
			}

		}

		for (int i = 0; i < 3 && endTokenIndex + i < tokens.size(); i++) {
			final String token = tokens.get(endTokenIndex + i).getText();

			if (clusters.containsKey(token)) {
				int nearestClusters = 0;
				for (Integer cluster : clusters.get(token)) {
					featureVector.set("RightContextOf_" + className + " = " + cluster, true);
					nearestClusters++;
					if (nearestClusters == 10)
						break;
				}
			} else {
				// featureVector.set(className + "_UNKNOWN", true);
			}

		}

	}

}
