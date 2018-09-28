package de.uni.bielefeld.sc.hterhors.psink.obie.ie.explorer;

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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.utils.OBIEUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.explorer.utils.ExplorationUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.InvestigationRestriction;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.ReflectionUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.TemplateAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;

/**
 * Samples over existing (pre-filled) ontological templates for specific other
 * templates. E.g. the sampler samples over Injuries, AnimalModels and
 * Treatments for the ExperimentalGroup-template.
 * 
 * @author hterhors
 *
 * @date Mar 14, 2018
 */
public class SlotFillerExplorer extends AbstractOBIEExplorer {

	private static Logger log = LogManager.getFormatterLogger(SlotFillerExplorer.class.getName());

	private final IExplorationCondition explorationCondition;

	private Set<Class<? extends IOBIEThing>> exploreClassesWithoutTextualEvidence;

	public final InvestigationRestriction investigationRestriction;

	private OBIEState currentState = null;

	private final boolean exploreOnOntologyLevel;

	private final boolean exploreExistingTemplates;

	private final Random rnd;

	/*
	 * only possible if exploreOnOntologyLevel and exploreExistingTemplates are not
	 * true.
	 */
	private final boolean enableDiscourseProgression;

	public SlotFillerExplorer(OBIERunParameter param) {
		this.exploreClassesWithoutTextualEvidence = param.exploreClassesWithoutTextualEvidence;
		if (param.explorationCondition != null)
			this.explorationCondition = param.explorationCondition;
		else
			this.explorationCondition = (a, b, c) -> true;

		this.investigationRestriction = param.investigationRestriction;

		this.exploreOnOntologyLevel = param.exploreOnOntologyLevel;
		this.exploreExistingTemplates = param.exploreExistingTemplates;
		this.enableDiscourseProgression = !this.exploreExistingTemplates && !this.exploreOnOntologyLevel
				&& param.enableDiscourseProgression;
		this.rnd = param.rndForSampling;
	}

	private String currentInstanceAnnotationID;

	/**
	 * NOT THREAD SAFE!
	 */
	@Override
	public List<OBIEState> getNextStates(OBIEState previousState) {

		this.currentState = previousState;

		// System.out.println("Instance: " + previousState);
		List<OBIEState> generatedStates = new LinkedList<OBIEState>();
		// System.out.println("################");
		// System.out.println("Curtrent State: ");
		// previousState.preFilledUsedObjects.forEach(System.out::println);
		// System.out.println("################");
		Collection<TemplateAnnotation> annotations = new OBIEState(previousState).getCurrentPrediction()
				.getTemplateAnnotations();
		for (TemplateAnnotation psinkAnnotation : annotations) {
			try {

				final int rootEntitySentenceIndex;
				if (enableDiscourseProgression) {
					if (psinkAnnotation.get().getCharacterOnset() == null) {
						rootEntitySentenceIndex = 0;
					} else {
						rootEntitySentenceIndex = previousState.getInstance()
								.charPositionToToken(psinkAnnotation.get().getCharacterOnset())
								.getSentenceIndex();
					}
				} else {
					rootEntitySentenceIndex = 0;
				}

				this.currentInstanceAnnotationID = psinkAnnotation.getAnnotationID();

				for (StateInstancePair state : topDownRecursiveFieldFilling(previousState.getInstance(),
						psinkAnnotation.get(), psinkAnnotation.rootClassType,
						psinkAnnotation.rootClassType, rootEntitySentenceIndex, true)) {
					generatedStates.add(state.state);
				}

			} catch (InstantiationException | IllegalAccessException e) {
				e.printStackTrace();
			} catch (NoSuchFieldException e) {
				e.printStackTrace();
			}

		}
		Collections.shuffle(generatedStates, rnd);
		return generatedStates;
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
	 * @param currentInstanceAnnotationID
	 * @param baseInstance
	 * @param baseClassType_interface
	 * @param rootEntitySentenceIndex
	 * @param rootEntitySentenceIndex
	 * @return
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 */
	public List<StateInstancePair> topDownRecursiveFieldFilling(OBIEInstance internalInstance, IOBIEThing baseInstance,
			Class<? extends IOBIEThing> baseClassType_interface, Class<? extends IOBIEThing> rootEntityClassType,
			int rootEntitySentenceIndex, final boolean sampleRootClassType)
			throws InstantiationException, IllegalAccessException, NoSuchFieldException {

		List<StateInstancePair> generatedInstances = new LinkedList<>();

		if (baseInstance == null && baseClassType_interface == null)
			return generatedInstances;
		/*
		 * Is true if the modification was not by a pre filled template.
		 */
		boolean wasNotModByPreFilled = true;

		if (!sampleRootClassType || sampleRootClassType && investigationRestriction.investigateClassType) {

			wasNotModByPreFilled = addVariations(internalInstance, baseInstance, baseClassType_interface,
					generatedInstances, rootEntitySentenceIndex);

			/*
			 * TODO: Add this?
			 */
			if (!sampleRootClassType && wasNotModByPreFilled) {

				/*
				 * Add empty only if not pre filled was used. If null should be part of pre
				 * filled add null to set of pre filled templates.
				 */
				OBIEState generatedState = new OBIEState(this.currentState);
				TemplateAnnotation entity = generatedState.getCurrentPrediction()
						.getEntity(this.currentInstanceAnnotationID);
				entity.setAnnotationInstance(null);

				generatedInstances.add(new StateInstancePair(generatedState, null));
			}
		}

		if (!wasNotModByPreFilled || baseInstance == null)
			return generatedInstances;

		callRecursiveForProperties(internalInstance, baseInstance, generatedInstances, rootEntityClassType,
				rootEntitySentenceIndex);

		return generatedInstances;
	}

	private void callRecursiveForProperties(OBIEInstance internalInstance, IOBIEThing baseInstance,
			List<StateInstancePair> generatedInstances, Class<? extends IOBIEThing> rootEntityClassType,
			int rootEntitySentenceIndex) throws IllegalAccessException, InstantiationException, NoSuchFieldException {

		List<Field> fields = ReflectionUtils.getDeclaredOntologyFields(baseInstance.getClass());

		// List<Field> fields =
		// Arrays.stream(baseInstance.getClass().getDeclaredFields())
		// .filter(f ->
		// (f.isAnnotationPresent(OntologyModelContent.class))).collect(Collectors.toList());

		/*
		 * For all fields:
		 */
		for (Field field : fields) {

			if (!investigationRestriction.investigateField(field.getName())) {
				continue;
			}
			// field.setAccessible(true);

			if (field.isAnnotationPresent(RelationTypeCollection.class)) {
				/*
				 * Generate states for lists of objects. Change just one element of the list.
				 */
				@SuppressWarnings("unchecked")
				List<IOBIEThing> listOfInstancesForField = (ArrayList<IOBIEThing>) field.get(baseInstance);

				@SuppressWarnings("unchecked")
				Class<? extends IOBIEThing> childBaseClassType = (Class<? extends IOBIEThing>) ((ParameterizedType) field
						.getGenericType()).getActualTypeArguments()[0];

				for (IOBIEThing childBaseInstance : listOfInstancesForField) {
					recursiveFieldFillingForListElement(internalInstance, baseInstance, generatedInstances,
							field.getName(), listOfInstancesForField, childBaseInstance, childBaseClassType,
							rootEntityClassType, rootEntitySentenceIndex);
				}

			} else {
				/*
				 * Sample for all possible properties of the chosen class. E.g.
				 * WistarRatModel->{hasAgeCategory,hasGender...,}
				 */
				IOBIEThing propertyInstance = (IOBIEThing) field.get(baseInstance);
				Class<? extends IOBIEThing> propertyClassType = (Class<? extends IOBIEThing>) field.getType();
				recursiveFieldFillingForSingleElement(internalInstance, baseInstance, generatedInstances,
						field.getName(), propertyInstance, propertyClassType, rootEntityClassType,
						rootEntitySentenceIndex);
			}

		}
	}

	/**
	 * Was modified by prefilled Template, return false.
	 * 
	 * @param internalInstance
	 * @param baseInstance
	 * @param slotSuperType
	 * @param generatedInstances
	 * @param rootEntitySentenceIndex
	 * @return
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 */
	private boolean addVariations(OBIEInstance internalInstance, IOBIEThing baseInstance,
			Class<? extends IOBIEThing> slotSuperType, List<StateInstancePair> generatedInstances,
			int rootEntitySentenceIndex) throws InstantiationException, IllegalAccessException, NoSuchFieldException {

		Set<IOBIEThing> candidateInstances;

		boolean wasNOTModByPreFilledTemplate = exploreExistingTemplates == false;

		if (wasNOTModByPreFilledTemplate || (wasNOTModByPreFilledTemplate = (candidateInstances = currentState
				.getPreFilledTemplates(slotSuperType)) == null)) {

			candidateInstances = new HashSet<>();
			/*
			 * Basic fields are already set. Only OntologyModelContent fields are missing.
			 * 
			 * For all values for field:
			 */

			if (exploreOnOntologyLevel) {
				for (IOBIEThing emptyCandidateInstance : ExplorationUtils.getSlotTypeCandidates(internalInstance,
						slotSuperType, exploreClassesWithoutTextualEvidence)) {
					emptyCandidateInstance = ExplorationUtils.copyOntologyModelFields(emptyCandidateInstance,
							baseInstance);
					candidateInstances.add(emptyCandidateInstance);
				}
			} else {
				for (IOBIEThing emptyCandidateInstance : ExplorationUtils.getSlotFillerCandidates(internalInstance,
						slotSuperType, exploreClassesWithoutTextualEvidence)) {

					if (enableDiscourseProgression) {
						/**
						 * If the discourse progression is enabled we do not want to sample for slot
						 * candidates which are mentioned before their parent class. This holds only
						 * true for the rootClass.
						 */
						if (emptyCandidateInstance.getCharacterOnset() != null) {
							final int slotEntitySentenceIndex = internalInstance
									.charPositionToToken(emptyCandidateInstance.getCharacterOnset()).getSentenceIndex();
							if (rootEntitySentenceIndex > slotEntitySentenceIndex) {
								continue;
							}
						}

					}

					emptyCandidateInstance = ExplorationUtils.copyOntologyModelFields(emptyCandidateInstance,
							baseInstance);

					candidateInstances.add(emptyCandidateInstance);
				}

			}
		}
		/*
		 * Basic fields are already set. Only OntologyModelContent fields are missing.
		 * 
		 * For all values for field:
		 */

		for (IOBIEThing filledCandidateInstance : candidateInstances) {
			/*
			 * Do not reuse used pre existing candidates.
			 */
			if (!wasNOTModByPreFilledTemplate
					&& this.currentState.preFilledObjectWasAlreadyUsed(filledCandidateInstance)) {
				continue;
			}

			final IOBIEThing clonedClass = OBIEUtils.deepConstructorClone(filledCandidateInstance);

			OBIEState generatedState = new OBIEState(this.currentState);
			TemplateAnnotation entity = generatedState.getCurrentPrediction().getEntity(this.currentInstanceAnnotationID);

			if (!wasNOTModByPreFilledTemplate) {
				generatedState.addUsedPreFilledObject(filledCandidateInstance);

			}
			entity.setAnnotationInstance(clonedClass);

			// System.out.println("Candidate: " +
			// generatedState.preFilledUsedObjects);

			generatedInstances.add(new StateInstancePair(generatedState, clonedClass));
		}
		return wasNOTModByPreFilledTemplate;
	}

	/**
	 * 
	 * @param internalInstance
	 * @param baseClass               the class that holds the list as a property.
	 * @param generatedClasses        the list of all collected new base classes.
	 * @param fieldName               the name of the field. It is used to get
	 *                                access to field in the new generated base
	 *                                classes.
	 * @param oldList                 the list we are currently iterating over. This
	 *                                list wont be modified but cloned and modified.
	 * @param childBaseClass          the current element in the list we are looking
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
	private void recursiveFieldFillingForListElement(OBIEInstance internalInstance, IOBIEThing baseClass,
			List<StateInstancePair> generatedClasses, String fieldName, List<IOBIEThing> oldList,
			IOBIEThing childBaseClass, Class<? extends IOBIEThing> listBaseClassType,
			Class<? extends IOBIEThing> rootEntityClassType, int rootEntitySentenceIndex)
			throws InstantiationException, IllegalAccessException, NoSuchFieldException {

		/**
		 * A list of all elements except of the element that's gone to be changed.
		 */
		List<IOBIEThing> baseList = new ArrayList<>();

		/*
		 * Copy old list except of the current element.
		 */
		for (IOBIEThing thing : oldList) {
			if (thing != childBaseClass)
				baseList.add(OBIEUtils.deepConstructorClone(thing));
		}

		/*
		 * Get and add possible values for current element.
		 */
		for (StateInstancePair possibleElementValue : topDownRecursiveFieldFilling(internalInstance, childBaseClass,
				listBaseClassType, rootEntityClassType, rootEntitySentenceIndex, false)) {

			if (!explorationCondition.matchesExplorationContitions(baseClass, fieldName, possibleElementValue.instance))
				continue;

			/*
			 * Copy current baseClass so that we can replace the list.
			 */
			IOBIEThing newClass = OBIEUtils.deepConstructorClone(baseClass);

			/*
			 * Copy the baseList elements to a new list that we will add to the newClass. We
			 * can copy all values since the base class does no longer contain the current
			 * element.
			 */
			ArrayList<IOBIEThing> newList = new ArrayList<>();
			for (IOBIEThing thing : baseList) {
				newList.add(OBIEUtils.deepConstructorClone(thing));
			}

			/*
			 * Add the new value to the new list.
			 */
			newList.add(possibleElementValue.instance);

			/*
			 * Add new list to new class.
			 */
			Field listFieldOfNewClass = newClass.getClass().getDeclaredField(fieldName);
			listFieldOfNewClass.setAccessible(true);
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
			generatedState.addUsedPreFilledObject(possibleElementValue.instance);
			TemplateAnnotation entity = generatedState.getCurrentPrediction().getEntity(this.currentInstanceAnnotationID);

			// System.out.println("Remove: " + childBaseClass);
			generatedState.removeRecUsedPreFilledObject(

					childBaseClass

			);
			// System.out.println();
			// System.out.println("Results to:");
			// generatedState.preFilledUsedObjects.forEach(System.out::println);
			// System.out.println("------");

			entity.setAnnotationInstance(newClass);

			generatedClasses.add(new StateInstancePair(generatedState, newClass));

		}
	}

	private void recursiveFieldFillingForSingleElement(OBIEInstance internalInstance, IOBIEThing baseInstance,
			List<StateInstancePair> generatedInstances, String fieldName, IOBIEThing childInstance,
			Class<? extends IOBIEThing> childClassType, Class<? extends IOBIEThing> rootEntityClassType,
			int rootEntitySentenceIndex) throws InstantiationException, IllegalAccessException, NoSuchFieldException {

		for (StateInstancePair modAtFieldClass : topDownRecursiveFieldFilling(internalInstance, childInstance,
				childClassType, rootEntityClassType, rootEntitySentenceIndex, false)) {

			/**
			 * TODO: Allow setting fields to null again?
			 */
			// if (modAtFieldClass == null) {
			// continue;
			// }

			if (!explorationCondition.matchesExplorationContitions(baseInstance, fieldName, modAtFieldClass.instance))
				continue;

			IOBIEThing genClass = OBIEUtils.deepConstructorClone(baseInstance);

			Field genClassField;

			try {
				genClassField = genClass.getClass().getDeclaredField(fieldName);

				genClassField.setAccessible(true);
				genClassField.set(genClass, modAtFieldClass.instance);

				// generatedInstances.add(genClass);

				OBIEState generatedState = new OBIEState(this.currentState);
				generatedState.addUsedPreFilledObject(modAtFieldClass.instance);

				TemplateAnnotation entity = generatedState.getCurrentPrediction()
						.getEntity(this.currentInstanceAnnotationID);

				generatedState.removeRecUsedPreFilledObject(childInstance);

				entity.setAnnotationInstance(genClass);

				generatedInstances.add(new StateInstancePair(generatedState, genClass));

			} catch (NoSuchFieldException | SecurityException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				/*
				 * Skip.
				 */
				// System.err.println(e.getMessage());
			}
		}
	}

}
