package de.hterhors.obie.ml.templates;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.ner.regex.BasicRegExPattern;
import de.hterhors.obie.ml.run.param.OBIERunParameter;
import de.hterhors.obie.ml.templates.InterTokenTemplate.Scope;
import de.hterhors.obie.ml.utils.ReflectionUtils;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import factors.Factor;
import factors.FactorScope;
import learning.Vector;

/**
 * @author hterhors
 *
 * @date Nov 15, 2017
 */
public class InterTokenTemplate extends AbstractOBIETemplate<Scope> implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Logger log = LogManager.getFormatterLogger(InterTokenTemplate.class);

	private static final String TOKEN_SPLITTER_SPACE = " ";

	private static final String END_SIGN = "$";

	private static final String START_SIGN = "^";

	private static final int MIN_TOKEN_LENGTH = 2;

	private static final String LEFT = "<";

	private static final String RIGHT = ">";
	/**
	 * Whether distant supervision is enabled for this template or not. This effects
	 * the way of calculating the factors and features!
	 */
	private final boolean enableDistantSupervision;

	private final AbstractOBIETemplate<?> thisTemplate;

	public InterTokenTemplate(OBIERunParameter parameter) {
		super(parameter);
		this.thisTemplate = this;
		this.enableDistantSupervision = parameter.exploreOnOntologyLevel;
	}

	class Scope extends FactorScope {

		public String classOrIndividualName;
		public Set<String> surfaceForms;

		public Scope(String classOrIndividualName, Set<String> surfaceForms,
				Class<? extends IOBIEThing> entityRootClassType) {
			super(thisTemplate, entityRootClassType, classOrIndividualName, surfaceForms);
			this.classOrIndividualName = classOrIndividualName;
			this.surfaceForms = surfaceForms;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();

		for (TemplateAnnotation entity : state.getCurrentPrediction().getTemplateAnnotations()) {
			addFactorRecursive(factors, state.getInstance(), entity.rootClassType, entity.getTemplateAnnotation());
		}

		return factors;
	}

	private void addFactorRecursive(List<Scope> factors, OBIEInstance internalInstance,
			Class<? extends IOBIEThing> rootClassType, IOBIEThing scioClass) {

		if (scioClass == null)
			return;

		/*
		 * TODO: include data Type properties?
		 */
		// if
		// (!scioClass.getClass().isAnnotationPresent(DataTypeProperty.class)) {
		final Set<String> surfaceForms = getSurfaceForms(internalInstance, scioClass);

		if (surfaceForms != null) {
			factors.add(new Scope(scioClass.getClass().getSimpleName(), surfaceForms, rootClassType));
			if (scioClass.getIndividual() != null)
				factors.add(new Scope(scioClass.getIndividual().name, surfaceForms, rootClassType));
		}
		// }

		/*
		 * Add factors for object type properties.
		 */
		ReflectionUtils.getAccessibleOntologyFields(scioClass.getClass()).forEach(field -> {
			try {
				if (field.isAnnotationPresent(RelationTypeCollection.class)) {
					for (IOBIEThing element : (List<IOBIEThing>) field.get(scioClass)) {
						addFactorRecursive(factors, internalInstance, rootClassType, element);
					}
				} else {
					addFactorRecursive(factors, internalInstance, rootClassType, (IOBIEThing) field.get(scioClass));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		return;

	}

	/**
	 * Returns the surface forms of the given object. If distant supervision is
	 * enabled all surface forms are returned that belongs to the class type of the
	 * given object.If DV is not enabled the returned set contains only the
	 * annotated surface form of the given object.
	 * 
	 * @param internalInstance
	 * 
	 * @param filler
	 * @return null if there are no annotations for that class
	 */
	private Set<String> getSurfaceForms(OBIEInstance internalInstance, final IOBIEThing filler) {

		if (filler == null)
			return null;

		Set<String> surfaceForms;
		if (enableDistantSupervision) {
			/*
			 * If DV is enabled add all surface forms of that class.
			 */
			if (internalInstance.getNamedEntityLinkingAnnotations().containsClassAnnotations(filler.getClass())) {
				if (ReflectionUtils.isAnnotationPresent(filler.getClass(), DatatypeProperty.class)) {
					surfaceForms = new HashSet<>();
					surfaceForms.add(normalizeSurfaceForm(filler.getTextMention()));
				} else {
					surfaceForms = new HashSet<>();
					surfaceForms.addAll(
							internalInstance.getNamedEntityLinkingAnnotations().getClassAnnotations(filler.getClass())
									.stream().map(nera -> nera.text).collect(Collectors.toList()));
					surfaceForms.addAll(internalInstance.getNamedEntityLinkingAnnotations()
							.getIndividualAnnotations(filler.getIndividual()).stream().map(nera -> nera.text)
							.collect(Collectors.toList()));
				}
			} else {
				return null;
			}
		} else {
			/*
			 * If DV is not enabled add just the surface form of that individual annotation.
			 */
			surfaceForms = new HashSet<>();
			if (ReflectionUtils.isAnnotationPresent(filler.getClass(), DatatypeProperty.class)) {
				// surfaceForms.add(((IDataType) filler).getValue());
				surfaceForms.add(normalizeSurfaceForm(filler.getTextMention()));
			} else {
				surfaceForms.add(filler.getTextMention());
			}

		}
		return surfaceForms;
	}

	private String normalizeSurfaceForm(String textMention) {
		return textMention.replaceAll("[0-9]", "#").replaceAll("[^\\x20-\\x7E]+", "§");
	}

	@Override
	public void computeFactor(Factor<Scope> factor) {

		Vector featureVector = factor.getFeatureVector();

		final String name = factor.getFactorScope().classOrIndividualName;

		final Set<String> surfaceForms = factor.getFactorScope().surfaceForms;

		for (String surfaceForm : surfaceForms) {
			getTokenNgrams(featureVector, name, surfaceForm);
		}

	}

	private void getTokenNgrams(Vector featureVector, String name, String cleanedMention) {

		final String cM = START_SIGN + TOKEN_SPLITTER_SPACE + cleanedMention + TOKEN_SPLITTER_SPACE + END_SIGN;

		final String[] tokens = cM.split(TOKEN_SPLITTER_SPACE);

		final int maxNgramSize = tokens.length;

		featureVector.set(LEFT + name + RIGHT + TOKEN_SPLITTER_SPACE + cM, true);

		/*
		 * TODO: need this?
		 */
//		if (name.isAnnotationPresent(DatatypeProperty.class))
//			return;

		for (int ngram = 1; ngram < maxNgramSize; ngram++) {
			for (int i = 0; i < maxNgramSize - 1; i++) {

				/*
				 * Do not include start symbol.
				 */
				if (i + ngram == 1)
					continue;

				/*
				 * Break if size exceeds token length
				 */
				if (i + ngram > maxNgramSize)
					break;

				StringBuffer fBuffer = new StringBuffer();
				for (int t = i; t < i + ngram; t++) {

					if (tokens[t].isEmpty())
						continue;

					if (BasicRegExPattern.STOP_WORDS.contains(tokens[t].toLowerCase()))
						continue;

					fBuffer.append(tokens[t]).append(TOKEN_SPLITTER_SPACE);

				}

				final String featureName = fBuffer.toString().trim();

				if (featureName.length() < MIN_TOKEN_LENGTH)
					continue;

				if (featureName.isEmpty())
					continue;

				featureVector.set(LEFT + name + RIGHT + TOKEN_SPLITTER_SPACE + featureName, true);

			}
		}

	}

}
