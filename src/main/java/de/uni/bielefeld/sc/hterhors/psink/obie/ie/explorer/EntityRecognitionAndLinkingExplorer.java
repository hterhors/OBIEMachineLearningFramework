package de.uni.bielefeld.sc.hterhors.psink.obie.ie.explorer;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.ImplementationClass;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.tokenizer.Token;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.TemplateAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.NERLClassAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;

public class EntityRecognitionAndLinkingExplorer extends AbstractOBIEExplorer {

	private static Logger log = LogManager.getFormatterLogger(EntityRecognitionAndLinkingExplorer.class.getName());
	private final Set<Class<? extends IOBIEThing>> possibleRootClassTypes;

	private final int maxNumberOfSampleElements;
	final private int maxTokenPerAnnotation = 4;

	final private Random rnd;

	public EntityRecognitionAndLinkingExplorer(OBIERunParameter param) {

		this.possibleRootClassTypes = Collections.unmodifiableSet(param.rootSearchTypes.stream()
				.map(i -> i.getAnnotation(ImplementationClass.class).get()).collect(Collectors.toSet()));
		log.info("Intialize MultiTokenBoundaryExplorer with: " + possibleRootClassTypes);
		this.maxNumberOfSampleElements = param.maxNumberOfEntityElements;
		this.rnd = param.rndForSampling;
	}

	final private static Set<String> stopWords = new HashSet<>(
			Arrays.asList("%", ".", ",", "&", ":", ";", "<", ">", "=", "?", "!"));

	public List<OBIEState> getNextStates(OBIEState previousState) {
		List<OBIEState> generatedStates = new ArrayList<OBIEState>();

		if (previousState.getCurrentPrediction().getTemplateAnnotations().size() >= maxNumberOfSampleElements) {
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

						assign &= !previousState.tokenHasAnnotation(tokens.get(i + nextTokenIndex));

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
							if (classToAnnotate.isAnnotationPresent(DatatypeProperty.class)) {
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
								annotation = classToAnnotate.getConstructor(String.class, String.class, String.class)
										.newInstance(UUID.randomUUID().toString(), value, originalText);

//								}

							} else {
								annotation = classToAnnotate.getConstructor(String.class, String.class)
										.newInstance(UUID.randomUUID().toString(), originalText);
							}

							annotation.setCharacterOnset(charStartIndex);

							TemplateAnnotation tokenAnnotation = new TemplateAnnotation(classToAnnotate, annotation);

							generatedState.getCurrentPrediction().addAnnotation(tokenAnnotation);
							generatedStates.add(generatedState);
						} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
								| InvocationTargetException | NoSuchMethodException | SecurityException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
		Collections.shuffle(generatedStates, rnd);
		// System.out.println("###");
		// generatedStates.forEach(System.out::println);
		// System.out.println("###");
		return generatedStates;
	}
}
