package de.hterhors.obie.ml.explorer;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.InvestigationRestriction;
import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.explorer.utils.ExplorationUtils;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.utils.OBIEUtils;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;

public class SlotFillerExplorer extends AbstractOBIEExplorer {

	private static Logger log = LogManager.getFormatterLogger(SlotFillerExplorer.class.getName());

	private final IExplorationCondition explorationCondition;

	private Set<Class<? extends IOBIEThing>> exploreClassesWithoutTextualEvidence;

//	private final InvestigationRestriction investigationRestriction;

	private OBIEState currentState = null;

	private final boolean exploreOnOntologyLevel;

	private final boolean exploreExistingTemplates;

	private final Random rnd;

	/*
	 * only possible if exploreOnOntologyLevel and exploreExistingTemplates are not
	 * true.
	 */
	private final boolean enableDiscourseProgression;

	private final boolean restrictExplorationOnConceptsInInstance;

	private long currentTempalateAnnotationID;

	private int currentRootEntitySentenceIndex;

	/**
	 * NOT THREAD SAFE!
	 */
	public SlotFillerExplorer(RunParameter param) {
		super(param);
		this.restrictExplorationOnConceptsInInstance = param.restrictExplorationToFoundConcepts;
		this.exploreClassesWithoutTextualEvidence = param.exploreClassesWithoutTextualEvidence;
		if (param.explorationCondition != null)
			this.explorationCondition = param.explorationCondition;
		else
			this.explorationCondition = (a, b, c) -> true;

//		this.investigationRestriction = param.investigationRestriction;

		this.exploreOnOntologyLevel = param.exploreOnOntologyLevel;
		this.exploreExistingTemplates = param.exploreExistingTemplates;
		this.enableDiscourseProgression = !this.exploreExistingTemplates && !this.exploreOnOntologyLevel
				&& param.enableDiscourseProgression;
		this.rnd = param.rndForSampling;
	}

	/**
	 * NOT THREAD SAFE!
	 */
	@Override
	public List<OBIEState> getNextStates(OBIEState currentState) {

		this.currentState = currentState;

		final List<OBIEState> proposalStates = new LinkedList<OBIEState>();
//		System.out.println("#########################");
//		System.out.println(currentState);
//		System.out.println("#########################");
		final Collection<TemplateAnnotation> templateAnnotations = currentState.getCurrentTemplateAnnotations()
				.getTemplateAnnotations();

//		if (currentState.getInstance().getName().contains("Geoff_Dyson")) {
//			System.out.println("here");
//		}

		for (final TemplateAnnotation templateAnnotation : templateAnnotations) {

			this.currentRootEntitySentenceIndex = getRootEntitySentenceIndex(templateAnnotation);

			this.currentTempalateAnnotationID = templateAnnotation.getAnnotationID();

			for (StateInstancePair stateInstancePair : topDownRecursiveSlotFilling(templateAnnotation.getThing(),
					templateAnnotation.rootClassType, true,
					templateAnnotation.getThing().getInvestigationRestriction())) {
				proposalStates.add(stateInstancePair.state);
			}

		}

		Collections.shuffle(proposalStates, new Random(rnd.nextLong()));
//		if (currentState.getInstance().getName().contains("N223")) {
////			proposalStates.forEach(System.out::println);
//		for (OBIEState s : proposalStates) {
//			System.out.println(s);
//			for (TemplateAnnotation templateAnnotation : s.getCurrentTemplateAnnotations().getTemplateAnnotations()) {
//				System.out.println(templateAnnotation.getThing());
//			}
//		}
//		}
		return proposalStates;
	}

	/**
	 * Returns the sentence index of the root-template annotation.
	 * 
	 * @param currentState
	 * @param templateAnnotation
	 * @return
	 */
	private int getRootEntitySentenceIndex(final TemplateAnnotation templateAnnotation) {
		final int rootEntitySentenceIndex;

		if (enableDiscourseProgression) {
			if (templateAnnotation.getThing().getCharacterOnset() == null) {
				rootEntitySentenceIndex = 0;
			} else {
				rootEntitySentenceIndex = currentState.getInstance()
						.charPositionToToken(templateAnnotation.getThing().getCharacterOnset()).getSentenceIndex();
			}
		} else {
			rootEntitySentenceIndex = 0;
		}
		return rootEntitySentenceIndex;
	}

	private List<StateInstancePair> topDownRecursiveSlotFilling(IOBIEThing parentTemplate,
			Class<? extends IOBIEThing> slotType, InvestigationRestriction investigationRestriction) {
		return topDownRecursiveSlotFilling(parentTemplate, slotType, false, investigationRestriction);
	}

	/**
	 * Baseclass might be contain fields which are not empty. the baseclass is the
	 * starting point to apply changes. Thus all fields are copied to a new empty
	 * instance and then the fields are changed.
	 * 
	 * Only ontology model fields are copied and changed. The textmention/
	 * charoffset/onset is fixed.
	 * 
	 * @param internalInstance
	 * @param currentTempalateAnnotationID
	 * @param parentTemplate
	 * @param slotType
	 * @param rootEntitySentenceIndex
	 * @param rootEntitySentenceIndex
	 * @return
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 */
	private List<StateInstancePair> topDownRecursiveSlotFilling(IOBIEThing parentTemplate,
			Class<? extends IOBIEThing> slotType, final boolean currentlyExploreRootTemplate,
			InvestigationRestriction investigationRestriction) {

		List<StateInstancePair> generatedStates = new LinkedList<>();

		if (parentTemplate == null && slotType == null)
			return generatedStates;
		/*
		 * Is true if the modification filled by a pre-filled template.
		 */
		boolean wasModByPreFilled = false;

		if (!currentlyExploreRootTemplate
				|| currentlyExploreRootTemplate && parentTemplate.getInvestigationRestriction().investigateClassType) {

			Set<IOBIEThing> potentialSlotFiller;

			if (!exploreExistingTemplates || (wasModByPreFilled = (potentialSlotFiller = currentState
					.getPreFilledTemplates(slotType)) != null)) {

				potentialSlotFiller = new HashSet<>();
				/*
				 * Basic fields are already set. Only OntologyModelContent fields are missing.
				 */

				for (IOBIEThing emptyCandidateInstance : ExplorationUtils.getCandidates(currentState.getInstance(),
						slotType, exploreClassesWithoutTextualEvidence, exploreOnOntologyLevel,
						restrictExplorationOnConceptsInInstance, investigationRestriction)) {

					if (!exploreOnOntologyLevel && enableDiscourseProgression) {
						/**
						 * If the discourse progression is enabled we do not want to sample for slot
						 * candidates which are mentioned before their parent class. This is holds only
						 * for the rootClass. TODO: Why?
						 */
						if (emptyCandidateInstance.getCharacterOnset() != null) {
							final int slotEntitySentenceIndex = currentState.getInstance()
									.charPositionToToken(emptyCandidateInstance.getCharacterOnset()).getSentenceIndex();
							if (currentRootEntitySentenceIndex > slotEntitySentenceIndex) {
								continue;
							}
						}

					}

					emptyCandidateInstance = ExplorationUtils.copyOntologyModelFields(emptyCandidateInstance,
							parentTemplate);

					potentialSlotFiller.add(emptyCandidateInstance);

				}
			}

			for (IOBIEThing candidateFiller : potentialSlotFiller) {
				/*
				 * Do not reuse used pre existing candidates.
				 */
				if (wasModByPreFilled && this.currentState.preFilledObjectWasAlreadyUsed(candidateFiller)) {
					continue;
				}

				final IOBIEThing clonedClass = OBIEUtils.deepClone(candidateFiller);

				OBIEState generatedState = new OBIEState(this.currentState);

				if (wasModByPreFilled) {
					generatedState.addUsedPreFilledTemplate(candidateFiller);
				}

				generatedState.getCurrentTemplateAnnotations().getEntity(this.currentTempalateAnnotationID)
						.update(clonedClass);

				generatedStates.add(new StateInstancePair(generatedState, clonedClass));
			}

			if (!currentlyExploreRootTemplate && !wasModByPreFilled) {

				/*
				 * Add empty only if not pre filled was used. If null should be part of pre
				 * filled add null to set of pre filled templates.
				 */
				OBIEState generatedState = new OBIEState(this.currentState);
				generatedState.getCurrentTemplateAnnotations().getEntity(this.currentTempalateAnnotationID)
						.update(null);

				generatedStates.add(new StateInstancePair(generatedState, null));
			}
		}

		/*
		 * If the slots were filled by pre-filled templates we do not have to
		 * investigate that branch of the template structure.
		 */
		if (wasModByPreFilled || parentTemplate == null)
			return generatedStates;

		List<Field> fields = ReflectionUtils.getSlots(parentTemplate.getClass(), investigationRestriction);

		/*
		 * For all fields:
		 */
		for (Field slot : fields) {

//			if (!investigationRestriction.investigateField(slot.getName())) {
//				continue;
//			}

			if (ReflectionUtils.isAnnotationPresent(slot, RelationTypeCollection.class)) {
				slotFillingForListElements(parentTemplate, generatedStates, slot
//						, investigationRestriction
				);
			} else {
				slotFillingForSingleElement(parentTemplate, generatedStates, slot
//						, investigationRestriction
				);
			}

		}
		return generatedStates;
	}

	/**
	 * 
	 * @param internalInstance
	 * @param parentTemplate          the class that holds the list as a property.
	 * @param generatedStates         the list of all collected new base classes.
	 * @param fieldName               the name of the field. It is used to get
	 *                                access to field in the new generated base
	 *                                classes.
	 * @param slotValues              the list we are currently iterating over. This
	 *                                list wont be modified but cloned and modified.
	 * @param slotElement             the current element in the list we are looking
	 *                                at. This element will not be cloned. Instead
	 *                                we create possible values for that element and
	 *                                add it to the list.
	 * @param listBaseClassType       the generic type of the list. It is used to
	 *                                get possible values that can replace the
	 *                                current element.
	 * @param rootEntityClassType
	 * @param rootEntitySentenceIndex
	 * 
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 */
	private void slotFillingForListElements(IOBIEThing parentTemplate, List<StateInstancePair> generatedStates,
			Field slot
//			, InvestigationRestriction investigationRestriction
	) {
		try {

			@SuppressWarnings("unchecked")
			List<IOBIEThing> slotValues = (ArrayList<IOBIEThing>) slot.get(parentTemplate);

			/*
			 * Generate states for lists of objects. Change just one element of the list.
			 */
			final Set<AbstractIndividual> individuals = slotValues.stream().map(s -> s.getIndividual())
					.collect(Collectors.toSet());

			for (IOBIEThing slotElement : slotValues) {

				final String fieldName = slot.getName();
				@SuppressWarnings("unchecked")
				Class<? extends IOBIEThing> listBaseClassType = (Class<? extends IOBIEThing>) ((ParameterizedType) slot
						.getGenericType()).getActualTypeArguments()[0];

				/**
				 * A list of all elements except of the element that's gone to be changed.
				 */
				List<IOBIEThing> baseList = new ArrayList<>();

				/*
				 * Copy old list except of the current element.
				 */
				for (IOBIEThing thing : slotValues) {
					if (thing != slotElement)
						baseList.add(OBIEUtils.deepClone(thing));
				}

				/*
				 * Get and add possible values for current element.
				 */

				for (StateInstancePair possibleElementValue : topDownRecursiveSlotFilling(slotElement,
						listBaseClassType, parentTemplate.getInvestigationRestriction())) {

					/**
					 * Do not add null elements in lists.
					 */
					if (possibleElementValue.instance == null)
						continue;

					if (possibleElementValue.instance.getIndividual() == null)
						continue;

					if (!explorationCondition.matchesExplorationContitions(parentTemplate, fieldName,
							possibleElementValue.instance))
						continue;

					/*
					 * TODO: parameterize Do not allow multiple values that are equal
					 */
					if (individuals.contains(possibleElementValue.instance.getIndividual()))
						continue;

//					if (slotValues.contains(possibleElementValue.instance))
//						continue;

					/*
					 * Copy current baseClass so that we can replace the list.
					 */
					IOBIEThing newClass = OBIEUtils.deepClone(parentTemplate);

					/*
					 * Copy the baseList elements to a new list that we will add to the newClass. We
					 * can copy all values since the base class does no longer contain the current
					 * element.
					 */
					ArrayList<IOBIEThing> newList = new ArrayList<>();
					for (IOBIEThing thing : baseList) {
						newList.add(OBIEUtils.deepClone(thing));
					}

					/*
					 * Add the new value to the new list.
					 */
					newList.add(possibleElementValue.instance);

					/*
					 * Add new list to new class.
					 */
					Field listFieldOfNewClass = ReflectionUtils.getAccessibleFieldByName(newClass.getClass(),
							fieldName);

					listFieldOfNewClass.set(newClass, newList);

					/*
					 * Collect new class.
					 */
					// generatedClasses.add(newClass);
					// System.out.println();
					// System.out.println("CurrentState:");
					// this.currentState.preFilledUsedObjects.forEach(System.out::println);
					// System.out.println("NextCandidateState:");
					// possibleElementValue.state.preFilledUsedObjects.forEach(System.out::println);
					// System.out.println();

					OBIEState generatedState = new OBIEState(this.currentState);
					// System.out.println("Add: " + possibleElementValue.newInstance);
					generatedState.addUsedPreFilledTemplate(possibleElementValue.instance);

					// System.out.println("Remove: " + childBaseClass);
					generatedState.removeRecUsedPreFilledTemplate(

							slotElement

					);
					// System.out.println();
					// System.out.println("Results to:");
					// generatedState.preFilledUsedObjects.forEach(System.out::println);
					// System.out.println("------");

					generatedState.getCurrentTemplateAnnotations().getEntity(this.currentTempalateAnnotationID)
							.update(newClass);

					generatedStates.add(new StateInstancePair(generatedState, newClass));

				}
			}
		} catch (SecurityException | IllegalArgumentException | IllegalAccessException e1) {
			e1.printStackTrace();
		}

	}

	@SuppressWarnings("unchecked")
	private void slotFillingForSingleElement(IOBIEThing parentTemplate, List<StateInstancePair> generatedStates,
			Field slot
//			, InvestigationRestriction investigationRestriction
	) {
		try {
			final String slotName = slot.getName();

			IOBIEThing slotValue = (IOBIEThing) slot.get(parentTemplate);

			final Class<? extends IOBIEThing> slotType = (Class<? extends IOBIEThing>) slot.getType();

			for (StateInstancePair modAtFieldClass : topDownRecursiveSlotFilling(slotValue, slotType,
					parentTemplate.getInvestigationRestriction())) {

//				if (modAtFieldClass.instance == null) {
//					continue;
//				}

				if (!explorationCondition.matchesExplorationContitions(parentTemplate, slotName,
						modAtFieldClass.instance))
					continue;

				IOBIEThing genClass = OBIEUtils.deepClone(parentTemplate);

				try {

					Field genClassField = ReflectionUtils.getAccessibleFieldByName(genClass.getClass(), slotName);
					genClassField.set(genClass, modAtFieldClass.instance);

					OBIEState generatedState = new OBIEState(this.currentState);
					generatedState.addUsedPreFilledTemplate(modAtFieldClass.instance);

					generatedState.removeRecUsedPreFilledTemplate(slotValue);

					generatedState.getCurrentTemplateAnnotations().getEntity(this.currentTempalateAnnotationID)
							.update(genClass);

					generatedStates.add(new StateInstancePair(generatedState, genClass));

				} catch (IllegalArgumentException e) {
					/*
					 * Skip.
					 */
					// System.err.println(e.getMessage());
				}
			}
		} catch (IllegalArgumentException | IllegalAccessException e1) {
			e1.printStackTrace();
		}
	}

}
