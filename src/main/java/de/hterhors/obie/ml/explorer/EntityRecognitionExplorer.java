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
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.IndividualFactory;
import de.hterhors.obie.core.ontology.InvestigationRestriction;
import de.hterhors.obie.core.ontology.OntologyInitializer;
import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.core.tokenizer.Token;
import de.hterhors.obie.ml.explorer.utils.ExplorationUtils;
import de.hterhors.obie.ml.ner.NERLClassAnnotation;
import de.hterhors.obie.ml.ner.candidateRetrieval.ICandidateRetrieval;
import de.hterhors.obie.ml.ner.candidateRetrieval.RetrievalCandidate;
import de.hterhors.obie.ml.ner.dictionary.IDictionary;
import de.hterhors.obie.ml.run.AbstractOBIERunner;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.variables.IETmplateAnnotation;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;

public class EntityRecognitionExplorer extends AbstractOBIEExplorer {

	private static Logger log = LogManager.getFormatterLogger(EntityRecognitionExplorer.class.getName());
	private final Set<Class<? extends IOBIEThing>> possibleRootClassTypes;

	private final int maxNumberOfSampleElements;
	final private int maxTokenPerAnnotation = 8;

	final private Random rnd;
	/*
	 * All individuals
	 */
	final private ICandidateRetrieval candidateRetrieval;
	final private IDictionary dictionary;

	public EntityRecognitionExplorer(RunParameter param) {
		super(param);

		Objects.requireNonNull(param.getCandidateRetrieval());

		this.candidateRetrieval = param.getCandidateRetrieval();
		this.dictionary = candidateRetrieval.getDictionary();

		this.possibleRootClassTypes = param.rootSearchTypes;
		log.info("Intialize EntityRecognitionExplorer with: " + possibleRootClassTypes);
		this.maxNumberOfSampleElements = param.maxNumberOfEntityElements;
		this.rnd = param.rndForSampling;
	}

	final private static Set<String> stopWords = new HashSet<>(
			Arrays.asList("%", ".", ",", "&", ":", ";", "<", ">", "=", "?", "!"));

	public List<OBIEState> getNextStates(OBIEState previousState) {
		List<OBIEState> generatedStates = new ArrayList<OBIEState>();

		if (previousState.getCurrentIETemplateAnnotations().getAnnotations().size() >= maxNumberOfSampleElements) {
			generatedStates.add(previousState);
			return generatedStates;
		}

		List<Token> tokens = previousState.getInstance().getTokens();

		/*
		 * Loop over all possible class types.
		 */
		for (Class<? extends IOBIEThing> obieThingInterface : possibleRootClassTypes) {
			final Class<? extends IOBIEThing> obieThingClass = ReflectionUtils
					.getImplementationClass(obieThingInterface);

			if (ReflectionUtils.isAnnotationPresent(obieThingClass, DatatypeProperty.class)) {

				final Set<NERLClassAnnotation> ner = previousState.getInstance().getEntityAnnotations()
						.getClassAnnotations(obieThingClass);
				if (ner != null)
					outer: for (NERLClassAnnotation nerlClassAnnotation : ner) {

						for (IETmplateAnnotation internalAnnotation : previousState.getCurrentIETemplateAnnotations()
								.getAnnotations()) {

							if (checkForAnnotationRec(internalAnnotation.getThing(), nerlClassAnnotation.getOnset(),
									nerlClassAnnotation.getOnset() + nerlClassAnnotation.text.length()))
								continue outer;

						}

						OBIEState generatedState = new OBIEState(previousState);

						try {
							IOBIEThing annotation = obieThingClass.getConstructor(String.class, String.class)
									.newInstance(nerlClassAnnotation.getDTValueIfAnyElseTextMention(),
											nerlClassAnnotation.getText());
							annotation.setCharacterOnset(nerlClassAnnotation.getOnset());
							generatedState.getCurrentIETemplateAnnotations()
									.addAnnotation(new IETmplateAnnotation(obieThingInterface, annotation));
							generatedStates.add(generatedState);
						} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
								| InvocationTargetException | NoSuchMethodException | SecurityException e) {
							e.printStackTrace();
						}
					}
			} else {

				IndividualFactory<AbstractIndividual> individualFactory = null;
				try {
					individualFactory = (IndividualFactory<AbstractIndividual>) ReflectionUtils
							.getAccessibleFieldByName(obieThingClass, OntologyInitializer.INDIVIDUAL_FACTORY_FIELD_NAME)
							.get(null);
				} catch (IllegalArgumentException | IllegalAccessException e1) {
					e1.printStackTrace();
				}

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
									previousState.getCurrentIETemplateAnnotations());

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

							for (RetrievalCandidate candidate : candidateRetrieval.getCandidates(obieThingInterface,
									originalText)) {

								if (!individualFactory.containsIndividualByURI(candidate.type))
									continue;

								/*
								 * Generate annotation for individuals.
								 */

								OBIEState generatedState = new OBIEState(previousState);
								final IOBIEThing annotation = obieThingClass
										.getConstructor(String.class, InvestigationRestriction.class, String.class)
										.newInstance(candidate.type, null, originalText);
								annotation.setCharacterOnset(charStartIndex);
								generatedState.getCurrentIETemplateAnnotations()
										.addAnnotation(new IETmplateAnnotation(obieThingInterface, annotation));
								generatedStates.add(generatedState);

							}

						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}
		}

//		System.out.println(generatedStates.size());
//		Collections.shuffle(generatedStates, new Random(rnd.nextLong()));
//		System.out.println("###");
//		for (OBIEState token : generatedStates) {
//			System.out.println(token);
//		}

//		generatedStates.forEach(System.out::println);
//		System.out.println("###");
		return generatedStates;

	}

	public boolean tokenHasAnnotation(Token token, InstanceTemplateAnnotations prediction) {

		for (IETmplateAnnotation internalAnnotation : prediction.getAnnotations()) {

			if (checkForAnnotationRec(internalAnnotation.getThing(), (int) token.getFromCharPosition(),
					(int) token.getToCharPosition()))
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

		ReflectionUtils.getFields(scioClass.getClass(), scioClass.getInvestigationRestriction()).forEach(field -> {
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
