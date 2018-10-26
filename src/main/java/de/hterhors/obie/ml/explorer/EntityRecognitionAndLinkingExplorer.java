package de.hterhors.obie.ml.explorer;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.core.tokenizer.Token;
import de.hterhors.obie.ml.ner.NERLClassAnnotation;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.utils.ReflectionUtils;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;

public class EntityRecognitionAndLinkingExplorer extends AbstractOBIEExplorer {

	private static Logger log = LogManager.getFormatterLogger(EntityRecognitionAndLinkingExplorer.class.getName());
	private final Set<Class<? extends IOBIEThing>> possibleRootClassTypes;

	private final int maxNumberOfSampleElements;
	final private int maxTokenPerAnnotation = 4;

	final private Random rnd;

	public EntityRecognitionAndLinkingExplorer(RunParameter param) {

		this.possibleRootClassTypes = Collections.unmodifiableSet(param.rootSearchTypes.stream()
				.map(i -> ReflectionUtils.getImplementationClass(i)).collect(Collectors.toSet()));
		log.info("Intialize MultiTokenBoundaryExplorer with: " + possibleRootClassTypes);
		this.maxNumberOfSampleElements = param.maxNumberOfEntityElements;
		this.rnd = param.rndForSampling;
	}

	final private static Set<String> stopWords = new HashSet<>(
			Arrays.asList("%", ".", ",", "&", ":", ";", "<", ">", "=", "?", "!"));

	public List<OBIEState> getNextStates(OBIEState previousState) {
		List<OBIEState> generatedStates = new ArrayList<OBIEState>();

		if (previousState.getCurrentTemplateAnnotations().getTemplateAnnotations()
				.size() >= maxNumberOfSampleElements) {
			generatedStates.add(previousState);
			return generatedStates;
		}

		List<Token> tokens = previousState.getInstance().getTokens();

		/*
		 * Loop over all possible class types.
		 */
		for (Class<? extends IOBIEThing> classToAnnotate : possibleRootClassTypes) {

			/*
			 * For all with size 10 ,9 ,8, 7 ....
			 */
			for (int tokenPerAnnotation = maxTokenPerAnnotation; tokenPerAnnotation >= 1; tokenPerAnnotation--) {

				/*
				 * In 10 for all tokens in document T0-T9, T1-T10, ...
				 */
				for (int i = 0; i <= tokens.size() - tokenPerAnnotation; i++) {

					boolean assign = true;
					/*
					 * Check each token in T0-T9 for existing annotations.
					 */
					for (int nextTokenIndex = 0; nextTokenIndex < tokenPerAnnotation; nextTokenIndex++) {

						assign &= !tokenHasAnnotation(tokens.get(i + nextTokenIndex),
								previousState.getCurrentTemplateAnnotations());

						if (!assign)
							break;

						final String text = tokens.get(i + nextTokenIndex).getText();

						assign &= !stopWords.contains(text);

						if (!assign)
							break;

					}
					if (assign) {

						try {
							/*
							 * Add Identity
							 */
							OBIEState generatedState = new OBIEState(previousState);

							final int charStartIndex = tokens.get(i).getFromCharPosition();
							final int charEndIndex = tokens.get(i + tokenPerAnnotation - 1).getToCharPosition();

							final String originalText = previousState.getInstance().getContent()
									.substring((int) charStartIndex, (int) charEndIndex);

							IOBIEThing annotation;
							/**
							 * CHECKME: TODO: add semantic interpretation to data type properties?
							 * 
							 * We need at least to add the original value for data type property as it is
							 * used for comparison.
							 */
							if (ReflectionUtils.isAnnotationPresent(classToAnnotate, DatatypeProperty.class)) {
								final Set<NERLClassAnnotation> ner = previousState.getInstance()
										.getNamedEntityLinkingAnnotations()
										.getClassAnnotationsByTextMention(classToAnnotate, originalText);

								final String value;
								if (ner != null && !ner.isEmpty()) {
									value = ner.iterator().next().getDTValueIfAnyElseTextMention();
								} else {
									value = originalText;
								}

//								if (classToAnnotate == GroupName.class) {
//									annotation = new GroupName(UUID.randomUUID().toString(), value, originalText);
//								} else {
								/**
								 * TODO: Test this:
								 */
								annotation = classToAnnotate.getConstructor(String.class, String.class)
										.newInstance(value, originalText);

//								}

							} else {
								String individual = null;
								annotation = classToAnnotate.getConstructor(String.class, String.class)
										.newInstance(individual, originalText);
								throw new NotImplementedException("Search for individuals via candidate selection!");
							}

							annotation.setCharacterOnset(charStartIndex);

							TemplateAnnotation tokenAnnotation = new TemplateAnnotation(classToAnnotate, annotation);

							generatedState.getCurrentTemplateAnnotations().addAnnotation(tokenAnnotation);
							generatedStates.add(generatedState);
						} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
								| InvocationTargetException | NoSuchMethodException | SecurityException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
		Collections.shuffle(generatedStates, new Random(rnd.nextLong()));
		// System.out.println("###");
		// generatedStates.forEach(System.out::println);
		// System.out.println("###");
		return generatedStates;
	}

	public boolean tokenHasAnnotation(Token token, InstanceTemplateAnnotations prediction) {

		boolean containsAnnotation = false;
		for (TemplateAnnotation internalAnnotation : prediction.getTemplateAnnotations()) {

			containsAnnotation = checkForAnnotationRec(internalAnnotation.getThing(), (int) token.getFromCharPosition(),
					(int) token.getToCharPosition());

			if (containsAnnotation)
				return true;

		}
		return false;
	}

	private boolean checkForAnnotationRec(IOBIEThing scioClass, final int fromPosition, final int toPosition) {

		if (scioClass == null)
			return false;

		if (scioClass.getCharacterOnset() == null)
			return false;

		if (scioClass.getCharacterOffset() == null)
			return false;

		if (fromPosition >= scioClass.getCharacterOnset() && toPosition <= scioClass.getCharacterOffset()) {
			return true;
		}

		final AtomicBoolean containsAnnotation = new AtomicBoolean(false);

		ReflectionUtils.getAccessibleOntologyFields(scioClass.getClass()).forEach(field -> {
			try {
				if (field.isAnnotationPresent(RelationTypeCollection.class)) {
					for (IOBIEThing listObject : (List<IOBIEThing>) field.get(scioClass)) {
						containsAnnotation.set(containsAnnotation.get()
								|| checkForAnnotationRec(listObject, fromPosition, toPosition));
						if (containsAnnotation.get())
							return;
					}
				} else {
					containsAnnotation.set(containsAnnotation.get()
							|| checkForAnnotationRec((IOBIEThing) field.get(scioClass), fromPosition, toPosition));
					if (containsAnnotation.get())
						return;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		return containsAnnotation.get();

	}

}
