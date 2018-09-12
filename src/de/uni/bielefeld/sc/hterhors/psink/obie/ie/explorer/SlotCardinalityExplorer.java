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

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.utils.OBIEUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.explorer.utils.ExplorationUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.InvestigationRestriction;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.ReflectionUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.EntityAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;

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
	class FieldPath {
		/**
		 * The index of the object that holds this field in the upper list of objects.
		 */
		final public int objectsListIndex;

		/**
		 * The next field of the object.
		 */
		final public Field field;

		public FieldPath(int listIndex, Field field) {
			this.objectsListIndex = listIndex;
			this.field = field;
		}
	}

	private Set<Class<? extends IOBIEThing>> exploreClassesWithoutTextualEvidence;

	final private boolean exploreOnOntologyLevel;

	final private boolean exploreExistingTemplates;

	private OBIEState currentState = null;

	final private int maxNumberOfEntityElements;
	final private int maxNumberOfDataTypeElementsInList;

	private final InvestigationRestriction investigationRestriction;

	private String currentAnnotationID;

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
	public SlotCardinalityExplorer(OBIERunParameter param) {
		this.maxNumberOfEntityElements = param.maxNumberOfEntityElements;
		this.maxNumberOfDataTypeElementsInList = param.maxNumberOfDataTypeElements;
		this.exploreClassesWithoutTextualEvidence = param.exploreClassesWithoutTextualEvidence;

		this.exploreOnOntologyLevel = param.exploreOnOntologyLevel;
		this.exploreExistingTemplates = param.exploreExistingTemplates;
		this.investigationRestriction = param.investigationRestriction;
		this.rnd = param.rndForSampling;
	}

	@Override
	public List<OBIEState> getNextStates(OBIEState previousState) {
		this.currentState = previousState;

		List<OBIEState> generatedStates = new LinkedList<OBIEState>();

		// System.out.println(previousState);

		Collection<EntityAnnotation> annotations = new OBIEState(previousState).getCurrentPrediction()
				.getEntityAnnotations();

		for (EntityAnnotation psinkAnnotation : annotations) {
			this.currentAnnotationID = psinkAnnotation.getAnnotationID();

			List<StateInstancePair> generatedClasses = new ArrayList<>();

			IOBIEThing clonedBaseClass = OBIEUtils.deepConstructorClone(psinkAnnotation.getAnnotationInstance());

			topDownRecursiveListCardinalityChanger(previousState.getInstance(), generatedClasses, clonedBaseClass,
					clonedBaseClass, new ArrayList<FieldPath>());
			// topDownRecursiveListCardinalityChanger(previousState.getInstance(),
			// generatedClasses,
			// psinkAnnotation.getAnnotationInstance());

			for (StateInstancePair scioClass : generatedClasses) {

				generatedStates.add(scioClass.state);
			}

		}
		// System.out.println("GENSTATES:");
		// generatedStates.forEach(System.out::println);
		// System.out.println("''''");

		Collections.shuffle(generatedStates, rnd);
		return generatedStates;

	}

	@SuppressWarnings("unchecked")
	private void topDownRecursiveListCardinalityChanger(OBIEInstance document,
			List<StateInstancePair> generatedClasses, IOBIEThing baseClass, IOBIEThing classToModify,
			List<FieldPath> previousFieldPath) {
		if (baseClass == null)
			return;

		if (classToModify == null)
			return;

		List<Field> fields = ReflectionUtils.getDeclaredOntologyFields(classToModify.getClass());

		/*
		 * For all fields:
		 */
		for (Field field : fields) {

			if (!investigationRestriction.investigateField(field.getName())) {
				continue;
			}

			if (field.isAnnotationPresent(RelationTypeCollection.class)) {
				/*
				 * Alter list.
				 */
				removeInstanceFromList(generatedClasses, baseClass, classToModify, field, previousFieldPath);

				boolean callRec = addNewInstanceToList(document, generatedClasses, baseClass, classToModify, field,
						previousFieldPath);

				if (callRec) {

					/*
					 * Recursively go deeper in all elements of the list.
					 */
					try {

						int index = 0;
						for (IOBIEThing thing : (List<IOBIEThing>) field.get(classToModify)) {
							List<FieldPath> fieldPath = new ArrayList<>(previousFieldPath);
							fieldPath.add(new FieldPath(index, field));
							topDownRecursiveListCardinalityChanger(document, generatedClasses, baseClass, thing,
									fieldPath);
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
					List<FieldPath> fieldPath = new ArrayList<>(previousFieldPath);
					fieldPath.add(new FieldPath(0, field));
					topDownRecursiveListCardinalityChanger(document, generatedClasses, baseClass,
							(IOBIEThing) field.get(classToModify), fieldPath);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
			}

		}
	}

	@SuppressWarnings("unchecked")
	private void removeInstanceFromList(List<StateInstancePair> generatedClasses, IOBIEThing baseClass,
			IOBIEThing listHoldingClass, Field field, List<FieldPath> fieldPath) {

		List<IOBIEThing> listOfClassesForField;
		try {
			/*
			 * Generate states for lists of objects. Change just one element of the list.
			 */
			listOfClassesForField = (ArrayList<IOBIEThing>) field.get(listHoldingClass);
		} catch (IllegalArgumentException | IllegalAccessException e1) {
			e1.printStackTrace();
			return;
		}

		for (int i = 0; i < listOfClassesForField.size(); i++) {
			List<IOBIEThing> modifiedList = new ArrayList<>();

			for (int j = 0; j < listOfClassesForField.size(); j++) {
				if (i != j)
					modifiedList.add(OBIEUtils.deepConstructorClone(listOfClassesForField.get(j)));
			}
			try {
				/*
				 * Clone base class. Modify list by following the fieldPath to the list to get
				 * clonedListHoldingClass.
				 */
				IOBIEThing clonedBaseClass = OBIEUtils.deepConstructorClone(baseClass);

				IOBIEThing clonedListHoldingClass = getListHoldingClassByFieldPath(fieldPath, clonedBaseClass);

				/*
				 * Add new list to the list holding class which is a property of the cloned base
				 * class.
				 */

				Field genClassField = ReflectionUtils.getDeclaredFieldByName(clonedListHoldingClass.getClass(),
						field.getName());

				// Field genClassField =
				// clonedListHoldingClass.getClass().getDeclaredField(field.getName());
				// genClassField.setAccessible(true);
				genClassField.set(clonedListHoldingClass, modifiedList);

				/*
				 * Add cloned base class, which contains the object that holds the modified
				 * list.
				 */

				if (clonedBaseClass == null)
					continue;

				OBIEState generatedState = new OBIEState(this.currentState);
				EntityAnnotation entity = generatedState.getCurrentPrediction().getEntity(this.currentAnnotationID);
				entity.setAnnotationInstance(clonedBaseClass);

				generatedState.removeRecUsedPreFilledObject(listOfClassesForField.get(i));
				// System.out.println("Candidate: " +
				// generatedState.preFilledUsedObjects);

				generatedClasses.add(new StateInstancePair(generatedState, null));

			} catch (SecurityException | IllegalAccessException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				/*
				 * Skip.
				 */
				System.err.println(e.getMessage());
			}

		}

	}

	private boolean addNewInstanceToList(OBIEInstance document, List<StateInstancePair> generatedClasses,
			IOBIEThing baseClass, IOBIEThing listHoldingClass, Field field, List<FieldPath> fieldPath) {

		if (baseClass == null)
			return true;

		try {

			/*
			 * Get class type of generic list.
			 */
			@SuppressWarnings("unchecked")
			Class<? extends IOBIEThing> slotSuperType = ((Class<? extends IOBIEThing>) ((ParameterizedType) field
					.getGenericType()).getActualTypeArguments()[0]);

			Set<IOBIEThing> candidateInstances;
			/*
			 * Add a new instance to the list for each possible candidate.
			 */
			boolean wasNOTModByPreFilledTemplate = exploreExistingTemplates == false;

			if (exploreExistingTemplates) {
				if (wasNOTModByPreFilledTemplate = (candidateInstances = currentState
						.getPreFilledTemplates(slotSuperType)) == null) {

					candidateInstances = new HashSet<>();

					if (exploreOnOntologyLevel) {
						candidateInstances = ExplorationUtils.getSlotTypeCandidates(document, slotSuperType,
								exploreClassesWithoutTextualEvidence);
					} else {
						candidateInstances = ExplorationUtils.getSlotFillerCandidates(document, slotSuperType,
								exploreClassesWithoutTextualEvidence);
					}

				}
			} else {

				if (exploreOnOntologyLevel) {
					candidateInstances = ExplorationUtils.getSlotTypeCandidates(document, slotSuperType,
							exploreClassesWithoutTextualEvidence);
				} else {
					candidateInstances = ExplorationUtils.getSlotFillerCandidates(document, slotSuperType,
							exploreClassesWithoutTextualEvidence);
				}

			}

			/*
			 * Generate states for lists of objects. Change just one element of the list.
			 */
			@SuppressWarnings("unchecked")
			List<IOBIEThing> oldList = (ArrayList<IOBIEThing>) field.get(listHoldingClass);

			if (field.isAnnotationPresent(DatatypeProperty.class)
					&& oldList.size() >= maxNumberOfDataTypeElementsInList) {
				return wasNOTModByPreFilledTemplate;
			}

			if (oldList.size() >= maxNumberOfEntityElements) {
				return wasNOTModByPreFilledTemplate;
			}

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

				try {
					List<IOBIEThing> newList = new ArrayList<>();
					for (IOBIEThing thing : oldList) {
						/*
						 * Copy all elements to the new list.
						 */
						newList.add(OBIEUtils.deepConstructorClone(thing));
					}

					newList.add(candidateClass);

					/*
					 * Clone base class. Modify list by following the fieldPath to the list to get
					 * clonedListHoldingClass.
					 */
					IOBIEThing clonedBaseClass = OBIEUtils.deepConstructorClone(baseClass);

					IOBIEThing clonedListHoldingClass = getListHoldingClassByFieldPath(fieldPath, clonedBaseClass);

					/*
					 * Add new list to the list holding class which is a property of the cloned base
					 * class.
					 */
					Field genClassField = ReflectionUtils.getDeclaredFieldByName(clonedListHoldingClass.getClass(),
							field.getName());
					genClassField.set(clonedListHoldingClass, newList);

					/*
					 * Add cloned base class, which contains the object that holds the modified
					 * list.
					 */
					// generatedClasses.add(clonedBaseClass);

					OBIEState generatedState = new OBIEState(this.currentState);
					EntityAnnotation entity = generatedState.getCurrentPrediction()
							.getEntity(this.currentAnnotationID);
					entity.setAnnotationInstance(clonedBaseClass);

					generatedState.addUsedPreFilledObject(candidateClass);

					// System.out.println("Candidate: " +
					// generatedState.preFilledUsedObjects);

					generatedClasses.add(new StateInstancePair(generatedState, candidateClass));

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
	private IOBIEThing getListHoldingClassByFieldPath(List<FieldPath> fieldPath, IOBIEThing clonedBaseClass)
			throws IllegalAccessException {
		IOBIEThing clonedListHoldingClass = clonedBaseClass;
		for (FieldPath f : fieldPath) {
			if (f.field.isAnnotationPresent(RelationTypeCollection.class)) {
				clonedListHoldingClass = (IOBIEThing) ((List<IOBIEThing>) f.field.get(clonedListHoldingClass))
						.get(f.objectsListIndex);
			} else {
				clonedListHoldingClass = (IOBIEThing) f.field.get(clonedListHoldingClass);
			}
		}
		return clonedListHoldingClass;
	}
}
