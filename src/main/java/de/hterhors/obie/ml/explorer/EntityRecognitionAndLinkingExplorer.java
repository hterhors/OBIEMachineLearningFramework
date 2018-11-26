package de.hterhors.obie.ml.explorer;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.core.tokenizer.RegExTokenizer;
import de.hterhors.obie.core.tokenizer.Token;
import de.hterhors.obie.ml.explorer.utils.ExplorationUtils;
import de.hterhors.obie.ml.ner.NERLClassAnnotation;
import de.hterhors.obie.ml.ner.candidateRetrieval.ICandidateRetrieval;
import de.hterhors.obie.ml.ner.candidateRetrieval.RetrievalCandidate;
import de.hterhors.obie.ml.ner.dictionary.IDictionary;
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
	/*
	 * All individuals
	 */
	final private ICandidateRetrieval candidateRetrieval;
	final private IDictionary dictionary;

	public EntityRecognitionAndLinkingExplorer(RunParameter param) {
		super(param);

		this.candidateRetrieval = param.getCandidateRetrieval();
		this.dictionary = candidateRetrieval.getDictionary();

		this.possibleRootClassTypes = param.rootSearchTypes;
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
		for (Class<? extends IOBIEThing> obieThingInterface : possibleRootClassTypes) {

			/*
			 * For all with size 10 ,9 ,8, 7 ....
			 */
			for (int tokenPerAnnotation = maxTokenPerAnnotation; tokenPerAnnotation >= 1; tokenPerAnnotation--) {

				/*
				 * In 10 for all tokens in document T0-T9, T1-T10, ...
				 */
				for (int i = 0; i <= tokens.size() - tokenPerAnnotation; i++) {

					final String firstToken = tokens.get(i).getText();

					if (tokenPerAnnotation == 1 && firstToken.length() == 1)
						continue;

					if (firstToken.trim().isEmpty())
						continue;

					if (!dictionary.containsToken(firstToken))
						continue;

					if (tokenPerAnnotation > 1) {

						final String lastToken = tokens.get(i + tokenPerAnnotation - 1).getText();

						if (lastToken.isEmpty())
							continue;

						if (!dictionary.containsToken(lastToken))
							continue;

					}

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

					if (!assign)
						continue;

					try {
						/*
						 * Add Identity
						 */

						final int charStartIndex = tokens.get(i).getFromCharPosition();
						final int charEndIndex = tokens.get(i + tokenPerAnnotation - 1).getToCharPosition();

						final String originalText = previousState.getInstance().getContent()
								.substring((int) charStartIndex, (int) charEndIndex);

						/**
						 * CHECKME: TODO: add semantic interpretation to data type properties?
						 * 
						 * We need at least to add the original value for data type property as it is
						 * used for comparison.
						 */
						final Class<? extends IOBIEThing> obieThingClass = ReflectionUtils
								.getImplementationClass(obieThingInterface);

						if (ReflectionUtils.isAnnotationPresent(obieThingClass, DatatypeProperty.class)) {
							final Set<NERLClassAnnotation> ner = previousState.getInstance()
									.getNamedEntityLinkingAnnotations()
									.getClassAnnotationsByTextMention(obieThingClass, originalText);

							final String value;
							if (ner != null && !ner.isEmpty()) {
								value = ner.iterator().next().getDTValueIfAnyElseTextMention();
							} else {
								value = originalText;
							}

							OBIEState generatedState = new OBIEState(previousState);

							final IOBIEThing annotation = obieThingClass.getConstructor(String.class, String.class)
									.newInstance(value, originalText);
							annotation.setCharacterOnset(charStartIndex);
							generatedState.getCurrentTemplateAnnotations()
									.addAnnotation(new TemplateAnnotation(obieThingInterface, annotation));
							generatedStates.add(generatedState);

						} else {

							for (RetrievalCandidate individualCandidate : candidateRetrieval
									.getCandidates(obieThingInterface, originalText)) {

								OBIEState generatedState = new OBIEState(previousState);
								final IOBIEThing annotation = obieThingClass.getConstructor(String.class, String.class)
										.newInstance(individualCandidate.individual.nameSpace
												+ individualCandidate.individual.name, originalText);
								annotation.setCharacterOnset(charStartIndex);
								generatedState.getCurrentTemplateAnnotations()
										.addAnnotation(new TemplateAnnotation(obieThingInterface, annotation));
								generatedStates.add(generatedState);

							}
						}

					} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
							| InvocationTargetException | NoSuchMethodException | SecurityException e) {
						e.printStackTrace();
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

		ReflectionUtils.getSlots(scioClass.getClass()).forEach(field -> {
			try {
				if (ReflectionUtils.isAnnotationPresent(field, RelationTypeCollection.class)) {
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

	private static Map<Class<? extends IOBIEThing>, Collection<AbstractIndividual>> individualCache = new HashMap<>();

	private static Collection<AbstractIndividual> getIndividuals(Class<? extends IOBIEThing> slotType) {

		Collection<AbstractIndividual> v;

		if ((v = individualCache.get(slotType)) == null) {
			v = new HashSet<>(ExplorationUtils.getPossibleIndividuals(slotType));
			individualCache.put(slotType, v);
		}

		return v;
	}

}
