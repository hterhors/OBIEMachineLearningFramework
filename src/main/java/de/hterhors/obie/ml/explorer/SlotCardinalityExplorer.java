package de.hterhors.obie.ml.explorer;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.InvestigationRestriction;
import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.explorer.utils.ExplorationUtils;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.utils.OBIEUtils;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;

/**
 * Explore the slot cardinality. This explorer may adds a new empty object to a
 * property.
 * 
 * @author hterhors
 *
 */
public class SlotCardinalityExplorer extends AbstractOBIEExplorer {

	private static Logger log = LogManager.getFormatterLogger(SlotCardinalityExplorer.class.getName());

	/**
	 * Local class to capture the field path through complex objects to keep track.
	 * We need to store the index of the object within a list so that we know which
	 * path we should follow.
	 * 
	 * @author hterhors
	 *
	 * @date Oct 11, 2017
	 */
	class SlotPath {

		/**
		 * The index of the object that holds this field in the upper list of objects.
		 */
		final public int objectsListIndex;

		/**
		 * The next field of the object.
		 */
		final public Field slot;

		public SlotPath(int listIndex, Field field) {
			this.objectsListIndex = listIndex;
			this.slot = field;
		}
	}

	private Set<Class<? extends IOBIEThing>> exploreClassesWithoutTextualEvidence;
	private final boolean restrictExplorationOnConceptsInInstance;

	final private boolean exploreOnOntologyLevel;

	final private boolean exploreExistingTemplates;

	private OBIEState currentState = null;

	final private int maxNumberOfEntityElements;
	final private int maxNumberOfDataTypeElementsInList;

	private final InvestigationRestriction investigationRestriction;

	private long currentAnnotationID;

	private final Random rnd;

	/**
	 * Creates a new SCIOClassExplorer which takes a given scio class and sampled
	 * over all of its possible subclasses and properties.
	 * 
	 * Given a scio class the set of possible candidates are all children AND all
	 * parents, their children and their parents.
	 * 
	 * The algorithm finds the root scio class of a given scio class first and then
	 * collects all children and children children.
	 * 
	 * @param param
	 * 
	 * 
	 * @throws OWLOntologyCreationException
	 * @throws ClassNotFoundException
	 */
	public SlotCardinalityExplorer(RunParameter param) {
		super(param);
		this.maxNumberOfEntityElements = param.maxNumberOfEntityElements;
		this.maxNumberOfDataTypeElementsInList = param.maxNumberOfDataTypeElements;
		this.exploreClassesWithoutTextualEvidence = param.exploreClassesWithoutTextualEvidence;

		this.exploreOnOntologyLevel = param.exploreOnOntologyLevel;
		this.exploreExistingTemplates = param.exploreExistingTemplates;
		this.investigationRestriction = param.investigationRestriction;
		this.rnd = param.rndForSampling;
		this.restrictExplorationOnConceptsInInstance = param.restrictExplorationToFoundConcepts;
	}

	private IOBIEThing currentRootTemplate;

	@Override
	public List<OBIEState> getNextStates(OBIEState currentState) {
		this.currentState = currentState;

		List<OBIEState> proposalStates = new LinkedList<OBIEState>();

		Collection<TemplateAnnotation> templateAnnotations = currentState.getCurrentTemplateAnnotations()
				.getTemplateAnnotations();

		for (TemplateAnnotation templateAnnotation : templateAnnotations) {
			this.currentAnnotationID = templateAnnotation.getAnnotationID();

			List<StateInstancePair> generatedStates = new ArrayList<>();

			currentRootTemplate = OBIEUtils.deepClone(templateAnnotation.getThing());

			topDownRecursiveListCardinalityChanger(generatedStates, currentRootTemplate, new ArrayList<SlotPath>(),
					investigationRestriction);

			for (StateInstancePair scioClass : generatedStates) {

				proposalStates.add(scioClass.state);
			}

		}

		Collections.shuffle(proposalStates, new Random(rnd.nextLong()));
		return proposalStates;

	}

	@SuppressWarnings("unchecked")
	private void topDownRecursiveListCardinalityChanger(List<StateInstancePair> generatedStates,
			IOBIEThing parentTemplate, List<SlotPath> currentFieldPath,
			InvestigationRestriction investigationRestriction) {

		if (parentTemplate == null)
			return;

		List<Field> slots = ReflectionUtils.getSlots(parentTemplate.getClass(), investigationRestriction);

		/*
		 * For all fields:
		 */
		for (Field slot : slots) {

//			if (!investigationRestriction.investigateField(slot.getName())) {
//				continue;
//			}

			if (ReflectionUtils.isAnnotationPresent(slot, RelationTypeCollection.class)) {
				/*
				 * Alter list.
				 */
				removeInstanceFromList(generatedStates, parentTemplate, slot, currentFieldPath);

				boolean callRec = addNewInstanceToList(generatedStates, parentTemplate, slot, currentFieldPath);

				if (callRec) {

					/*
					 * Recursively go deeper in all elements of the list.
					 */
					try {

						int index = 0;
						for (IOBIEThing thing : (List<IOBIEThing>) slot.get(parentTemplate)) {
							List<SlotPath> newFieldPath = new ArrayList<>(currentFieldPath);
							newFieldPath.add(new SlotPath(index, slot));
							topDownRecursiveListCardinalityChanger(generatedStates, thing, newFieldPath,
									investigationRestriction);
							index++;
						}
					} catch (IllegalArgumentException | IllegalAccessException e) {
						e.printStackTrace();
					}

				}
			} else {
				/*
				 * If field was no oneToMany cardinality we call the method with the value of
				 * the field to check whether the child class contains oneToMany relations.
				 */
				try {
					List<SlotPath> newFieldPath = new ArrayList<>(currentFieldPath);
					newFieldPath.add(new SlotPath(0, slot));
					topDownRecursiveListCardinalityChanger(generatedStates, (IOBIEThing) slot.get(parentTemplate),
							newFieldPath, investigationRestriction);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
			}

		}
	}

	@SuppressWarnings("unchecked")
	private void removeInstanceFromList(List<StateInstancePair> generatedStates, IOBIEThing parentTemplate, Field slot,
			List<SlotPath> currentFieldPath) {

		try {
			/*
			 * Generate states for lists of objects. Change just one element of the list.
			 */
			List<IOBIEThing> currentSlotFillerList = (ArrayList<IOBIEThing>) slot.get(parentTemplate);

			for (int i = 0; i < currentSlotFillerList.size(); i++) {

				List<IOBIEThing> modifiedList = cloneExceptElement(currentSlotFillerList, i);

				try {
					/*
					 * Clone base class. Modify list by following the fieldPath to the list to get
					 * clonedListHoldingClass.
					 */
					IOBIEThing clonedBaseClass = OBIEUtils.deepClone(currentRootTemplate);

					IOBIEThing clonedListHoldingClass = getListHoldingClassByFieldPath(currentFieldPath,
							clonedBaseClass);

					/*
					 * Add new list to the list holding class which is a property of the cloned base
					 * class.
					 */

					Field genClassField = ReflectionUtils.getAccessibleFieldByName(clonedListHoldingClass.getClass(),
							slot.getName());

					genClassField.set(clonedListHoldingClass, modifiedList);

					/*
					 * Add cloned base class, which contains the object that holds the modified
					 * list.
					 */

					if (clonedBaseClass == null)
						continue;

					OBIEState generatedState = new OBIEState(this.currentState);
					generatedState.getCurrentTemplateAnnotations().getEntity(this.currentAnnotationID)
							.update(clonedBaseClass);

					generatedState.removeRecUsedPreFilledTemplate(currentSlotFillerList.get(i));
					// System.out.println("Candidate: " +
					// generatedState.preFilledUsedObjects);

					generatedStates.add(new StateInstancePair(generatedState, null));

				} catch (IllegalArgumentException e) {
					/*
					 * Skip.
					 */
					System.err.println(e.getMessage());
				}

			}
		} catch (SecurityException | IllegalArgumentException | IllegalAccessException e1) {
			e1.printStackTrace();
		}
	}

	private List<IOBIEThing> cloneExceptElement(List<IOBIEThing> currentSlotFillerList, int i) {
		List<IOBIEThing> modifiedList = new ArrayList<>();

		for (int j = 0; j < currentSlotFillerList.size(); j++) {
			if (i != j)
				modifiedList.add(OBIEUtils.deepClone(currentSlotFillerList.get(j)));
		}
		return modifiedList;
	}

	private boolean addNewInstanceToList(List<StateInstancePair> generatedThings, IOBIEThing listHoldingThing,
			Field slot, List<SlotPath> slotPath) {

		try {

			/*
			 * Get class type of generic list.
			 */
			@SuppressWarnings("unchecked")
			Class<? extends IOBIEThing> slotSuperType = ((Class<? extends IOBIEThing>) ((ParameterizedType) slot
					.getGenericType()).getActualTypeArguments()[0]);

			Set<IOBIEThing> candidateInstances;
			/*
			 * Add a new instance to the list for each possible candidate.
			 */
			boolean wasNOTModByPreFilledTemplate = exploreExistingTemplates == false;

			if (exploreExistingTemplates) {
				if (wasNOTModByPreFilledTemplate = (candidateInstances = currentState
						.getPreFilledTemplates(slotSuperType)) == null) {

					candidateInstances = ExplorationUtils.getCandidates(this.currentState.getInstance(), slotSuperType,
							exploreClassesWithoutTextualEvidence, exploreOnOntologyLevel,
							restrictExplorationOnConceptsInInstance);

				}
			} else {

				candidateInstances = ExplorationUtils.getCandidates(this.currentState.getInstance(), slotSuperType,
						exploreClassesWithoutTextualEvidence, exploreOnOntologyLevel,
						restrictExplorationOnConceptsInInstance);

			}

			/*
			 * Generate states for lists of objects. Change just one element of the list.
			 */
			@SuppressWarnings("unchecked")
			List<IOBIEThing> oldList = (ArrayList<IOBIEThing>) slot.get(listHoldingThing);

			if (ReflectionUtils.isAnnotationPresent(slot, DatatypeProperty.class)
					&& oldList.size() >= maxNumberOfDataTypeElementsInList) {
				return wasNOTModByPreFilledTemplate;
			}

			if (oldList.size() >= maxNumberOfEntityElements) {
				return wasNOTModByPreFilledTemplate;
			}

			final Set<AbstractIndividual> individuals = oldList.stream().map(s -> s.getIndividual())
					.collect(Collectors.toSet());

			for (IOBIEThing candidateClass : candidateInstances) {

				/*
				 * Do not allow null values in lists!
				 */

				if (candidateClass == null)
					continue;

				/*
				 * Do not reuse used pre existing candidates.
				 */
				if (!wasNOTModByPreFilledTemplate && this.currentState.preFilledObjectWasAlreadyUsed(candidateClass))
					continue;

				/**
				 * TODO: parameterize
				 */
				if (individuals.contains(candidateClass.getIndividual()))
					continue;

//				if (oldList.contains(candidateClass)) {
//					/*
//					 * Do not allow multiple equal items.
//					 */
//					continue;
//				}

				try {
					List<IOBIEThing> newList = new ArrayList<>();
					for (IOBIEThing thing : oldList) {
						/*
						 * Copy all elements to the new list.
						 */
						newList.add(OBIEUtils.deepClone(thing));
					}

					newList.add(candidateClass);

					/*
					 * Clone base class. Modify list by following the fieldPath to the list to get
					 * clonedListHoldingClass.
					 */
					IOBIEThing clonedBaseClass = OBIEUtils.deepClone(currentRootTemplate);

					IOBIEThing clonedListHoldingClass = getListHoldingClassByFieldPath(slotPath, clonedBaseClass);

					/*
					 * Add new list to the list holding class which is a property of the cloned base
					 * class.
					 */
					Field genClassField = ReflectionUtils.getAccessibleFieldByName(clonedListHoldingClass.getClass(),
							slot.getName());
					
					genClassField.set(clonedListHoldingClass, newList);

					/*
					 * Add cloned base class, which contains the object that holds the modified
					 * list.
					 */
					// generatedClasses.add(clonedBaseClass);

					OBIEState generatedState = new OBIEState(this.currentState);
					generatedState.getCurrentTemplateAnnotations().getEntity(this.currentAnnotationID)
							.update(clonedBaseClass);

					generatedState.addUsedPreFilledTemplate(candidateClass);

					// System.out.println("Candidate: " +
					// generatedState.preFilledUsedObjects);

					generatedThings.add(new StateInstancePair(generatedState, candidateClass));

				} catch (SecurityException | IllegalAccessException e) {
					e.printStackTrace();
				} catch (IllegalArgumentException e) {
					/*
					 * Skip.
					 */
					System.err.println(e.getMessage());
				}
			}

			return wasNOTModByPreFilledTemplate;
		} catch (IllegalArgumentException | IllegalAccessException e1) {
			e1.printStackTrace();
			throw new IllegalArgumentException(e1.getMessage());
		}
	}

	/**
	 * Follow the field path through the objects started from the main object that
	 * should be cloned.
	 * 
	 * @param fieldPath
	 * @param clonedBaseClass
	 * @return
	 * @throws IllegalAccessException
	 */
	@SuppressWarnings("unchecked")
	private IOBIEThing getListHoldingClassByFieldPath(List<SlotPath> fieldPath, IOBIEThing clonedBaseClass)
			throws IllegalAccessException {
		IOBIEThing clonedListHoldingClass = clonedBaseClass;
		for (SlotPath f : fieldPath) {
			if (ReflectionUtils.isAnnotationPresent(f.slot, RelationTypeCollection.class)) {
				clonedListHoldingClass = (IOBIEThing) ((List<IOBIEThing>) f.slot.get(clonedListHoldingClass))
						.get(f.objectsListIndex);
			} else {
				clonedListHoldingClass = (IOBIEThing) f.slot.get(clonedListHoldingClass);
			}
		}
		return clonedListHoldingClass;
	}
}
