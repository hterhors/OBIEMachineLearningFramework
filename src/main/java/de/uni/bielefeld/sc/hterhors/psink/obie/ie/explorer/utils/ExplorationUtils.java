package de.uni.bielefeld.sc.hterhors.psink.obie.ie.explorer.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.AbstractOBIEIndividual;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.IndividualFactory;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.OntologyFieldNames;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.AssignableSubClasses;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.AssignableSubInterfaces;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DirectSiblings;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.ImplementationClass;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.NamedIndividual;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.SuperRootClasses;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.ReflectionUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.INERLAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.NERLClassAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.NERLIndividualAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;

/**
 * 
 * @author hterhors
 *
 * @date Dec 19, 2017
 */
public class ExplorationUtils {

	private static final Map<Class<? extends IOBIEThing>, Map<Class<? extends IOBIEThing>, Boolean>> isDifferentiableToAllSiblingsCache = new ConcurrentHashMap<>();

	private ExplorationUtils() {
	}

	/**
	 * This method returns true if the field is used as a auxiliary construct in the
	 * ontology. These are all properties that do not have any meaning but just
	 * collecting entities. One example is SuppliedCompound. This class combines the
	 * Compound and its Supplier but carries no additional information on its own.
	 * An entity is considered as auxiliary class if it is the only class that can
	 * be filled in a property. That means there are no subclasses or sibling
	 * classes. (except of DataTypeProperty and NamedIndividual, those are always no
	 * aux. types).
	 * 
	 * @param field representing the property
	 * @return true if the class is just a auxiliary construct class, else false.
	 */
	public static boolean isAuxiliaryProperty(Class<?> propertyInterface) {

		/**
		 * TODO: Check this in PSINK project!
		 * 
		 * WHAT happens if the incoming class is not an interface?
		 *
		 * Why return false if the class is not an interface?
		 */
		if (!propertyInterface.isInterface())
			throw new IllegalArgumentException("Exptected interface but class is of type class:" + propertyInterface);

		if (!propertyInterface.isAnnotationPresent(ImplementationClass.class))
			return false;

		final Class<?> implClass = propertyInterface.getAnnotation(ImplementationClass.class).get();

		/*
		 * No NamedIndividual
		 */
		if (implClass.isAnnotationPresent(DatatypeProperty.class))
			return false;
		/*
		 * No DataType
		 */
		if (implClass.isAnnotationPresent(NamedIndividual.class))
			return false;

		/**
		 * TODO: not just the first interface... or get interfaces by annotation... if a
		 * class if subclass of multiple classes then we need to iterate over all super
		 * classes.
		 */
		final Class<?> rootImplClasses[] = implClass.getAnnotation(SuperRootClasses.class).get();
		/*
		 * No siblings, grand siblings...
		 */

		/**
		 * TODO: Test multiple root types.
		 */
		for (Class<?> rootImplClass : rootImplClasses) {

			if (rootImplClass.getAnnotation(AssignableSubClasses.class).get().length != 0)
				return false;
		}
		return true;

	}

	/**
	 * 
	 * This is needed if the root class changes. e.g. from Ratmodel to CatModel
	 * transfer all sharing properties. Copies all fields from copyFromClass to
	 * copyToClass if it contains those fields.
	 * 
	 * Copies only ontologymodel fields. do not copy offset onset textmention and
	 * annotation id.
	 * 
	 * DO NOT COPY datatype value fields although it is content of the ontologymodel
	 * FIXME: WHY WHY WHY ??? !f.isAnnotationPresent(DataTypeProperty.class) &&
	 * 
	 * @param copyToClass
	 * @param copyFromClass
	 * @return
	 */
	public static <B extends IOBIEThing> IOBIEThing copyOntologyModelFields(IOBIEThing copyToClass, B copyFromClass) {

		List<Field> copyToFields = ReflectionUtils.getDeclaredOntologyFields(copyToClass.getClass()).stream()
				.filter(f -> !Modifier.isStatic(f.getModifiers())).collect(Collectors.toList());
		for (Field toField : copyToFields) {
			try {

				if (copyFromClass != null) {
					Field copyFromField = ReflectionUtils.getDeclaredFieldByName(copyFromClass.getClass(),
							toField.getName());
					if (copyFromField != null)
						toField.set(copyToClass, copyFromField.get(copyFromClass));
				} else {
					/*
					 * CHECKME: Set value to null only if it is not OneToMany
					 */
					if (!toField.isAnnotationPresent(RelationTypeCollection.class))
						toField.set(copyToClass, null);
				}

			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (SecurityException e) {
				e.printStackTrace();
			}

		}
		return copyToClass;

	}

	/**
	 * Gets candidates based on the ontology. Returns all candidates that have at
	 * least one evidence in the text or is in the exploreWithoutEvidence list.
	 * 
	 * @param instance
	 * @param slotSuperType
	 * @param exploreClassesWithoutTextualEvidence
	 * @return
	 */
	public static Set<IOBIEThing> getSlotTypeCandidates(OBIEInstance instance,
			Class<? extends IOBIEThing> slotSuperType,
			Set<Class<? extends IOBIEThing>> exploreClassesWithoutTextualEvidence) {

		Set<IOBIEThing> candidates = new HashSet<>();
		/*
		 * Add candidate for root super class
		 */

		if (!slotSuperType.isAnnotationPresent(ImplementationClass.class)) {
			return candidates;
		}

		addSlotTypeCandidates(instance, slotSuperType, candidates,
				slotSuperType.getAnnotation(ImplementationClass.class).get(), exploreClassesWithoutTextualEvidence);

		/*
		 * TODO: insert
		 */
		try {
			@SuppressWarnings("unchecked")
			IndividualFactory<AbstractOBIEIndividual> individualFactory = (IndividualFactory<AbstractOBIEIndividual>) slotSuperType
					.getField("individualFactory").get(null);

			Collection<AbstractOBIEIndividual> individuals = individualFactory.getIndividuals();

			/*
			 * Get all possible candidates for individuals and filter by mentions in the
			 * text.
			 */
			for (AbstractOBIEIndividual individual : individuals) {
				addSlotTypeIndividualCandidates(instance, slotSuperType, candidates, individual,
						exploreClassesWithoutTextualEvidence);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		/*
		 * Get all possible candidates and filter by mentions in the text.
		 */
		for (Class<? extends IOBIEThing> slotFillerType : slotSuperType.getAnnotation(AssignableSubInterfaces.class)
				.get()) {
			addSlotTypeCandidates(instance, slotSuperType, candidates,
					slotFillerType.getAnnotation(ImplementationClass.class).get(),
					exploreClassesWithoutTextualEvidence);
		}
		return candidates;
	}

	private static void addSlotTypeCandidates(OBIEInstance instance, Class<? extends IOBIEThing> slotSuperType,
			Set<IOBIEThing> candidates, Class<? extends IOBIEThing> slotFillerType,
			Set<Class<? extends IOBIEThing>> exploreClassesWithoutTextualEvidence) {

		boolean keepClass = includeClassForSampling(slotFillerType);

		if (!keepClass)
			return;

		/*
		 * If the class can be differentiated between all of its siblings, then we can
		 * create this class without evidence in the text because there is a different
		 * property which makes this class important.
		 *
		 *
		 * If baseClassType is of type that should be sampled anyway even without
		 * textual evidence, add all direct candidates.
		 */

		/*
		 * We need to check whether a child class of that class can be an aux class.
		 * This happens if the direct interface is not an aux class but has subclasses
		 * that are.
		 */
		if (slotFillerType != null && exploreClassesWithoutTextualEvidence.contains(slotFillerType)
				|| (isDifferentiableToAllSiblingClasses(slotFillerType, slotSuperType)
						|| isAuxiliaryProperty(slotSuperType))) {
			/**
			 * TESTME: Is it sufficient to create just a single state with this class.
			 * Otherwise create a state for each mention in the text. (This should be only
			 * necessary if the position or text of this "auxiliary" class is important.
			 */
			try {

				Field annotationIDField = ReflectionUtils.getDeclaredFieldByName(slotFillerType,
						OntologyFieldNames.ANNOTATION_ID_FIELD_NAME);

				if (annotationIDField != null) {

					IOBIEThing newInstance = createNewInstance(slotFillerType);

					annotationIDField.set(newInstance, UUID.randomUUID().toString());

					candidates.add(newInstance);
				}
			} catch (InstantiationException | IllegalAccessException | SecurityException e) {
				e.printStackTrace();
			}
		} else {
			/**
			 * If not we need explicit text mentions to create this class.
			 */
			/**
			 * TODO: PARAMETERIZE TODO: should this whole else part be executed always or
			 * just in else? Create annotation for classes that do not need evidences?
			 */
			/*
			 * 
			 * Early pruning! Do not generate state where absolutely no evidence is in the
			 * text. for either classes or individuals
			 *
			 */

			if (slotFillerType != null
					&& !instance.getNamedEntityLinkingAnnotations().containsClassAnnotations(slotFillerType)) {
				return;
			}

			/*
			 * If the type is data type property then create an annotation instance for each
			 * mention in the text.
			 */
			if (slotFillerType.isAnnotationPresent(DatatypeProperty.class)) {
				/**
				 * 
				 */
				for (NERLClassAnnotation nera : instance.getNamedEntityLinkingAnnotations()
						.getClassAnnotationsBySemanticValues(slotFillerType)) {
					try {
						IOBIEThing newInstance = createNewInstance(slotFillerType);
						fillBasicFields(newInstance, nera);
						fillSemanticInterpretationField(newInstance, nera.getDTValueIfAnyElseTextMention());
						fillIDField(newInstance, nera.annotationID);
						candidates.add(newInstance);
					} catch (InstantiationException | IllegalAccessException | SecurityException
							| NoSuchFieldException e) {
						e.printStackTrace();
					}
				}

			} else {
				/*
				 * Else create exactly one instance without textual reference.
				 */
				try {
					IOBIEThing newInstance = createNewInstance(slotFillerType);
					fillIDField(newInstance, UUID.randomUUID().toString());
					candidates.add(newInstance);
				} catch (InstantiationException | IllegalAccessException | SecurityException | NoSuchFieldException
						| IllegalArgumentException e) {
					e.printStackTrace();
				}
			}

		}
	}

	private static void addSlotTypeIndividualCandidates(OBIEInstance instance,
			Class<? extends IOBIEThing> slotSuperType, Set<IOBIEThing> candidates,
			AbstractOBIEIndividual individualCandidate,
			Set<Class<? extends IOBIEThing>> exploreClassesWithoutTextualEvidence) {

		boolean keepIndividual = includeIndividualForSampling(individualCandidate);

		if (!keepIndividual)
			return;

		/**
		 * If not we need explicit text mentions to create this class.
		 */
		/**
		 * TODO: PARAMETERIZE TODO: should this whole else part be executed always or
		 * just in else? Create annotation for classes that do not need evidences?
		 */
		/*
		 * 
		 * Early pruning! Do not generate state where absolutely no evidence is in the
		 * text. for either classes or individuals
		 *
		 */

		if (individualCandidate != null
				&& !instance.getNamedEntityLinkingAnnotations().containsIndividualAnnotations(individualCandidate)) {
			return;
		}

		if (individualCandidate != null) {
			/*
			 * Else create exactly one instance without textual reference.
			 */
			try {
				IOBIEThing newInstance = createNewIndividual(slotSuperType, individualCandidate);
				fillIDField(newInstance, UUID.randomUUID().toString());
				candidates.add(newInstance);
			} catch (InstantiationException | IllegalAccessException | SecurityException | NoSuchFieldException
					| IllegalArgumentException e) {
				e.printStackTrace();
			}
		}

	}

	private static void fillIDField(IOBIEThing newInstance, String annotationID)
			throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		// Field annotationIDField =
		// newInstance.getClass().getDeclaredField(AbstractOntologyEnvironment.ANNOTATION_ID_FIELD);
		// annotationIDField.setAccessible(true);

		Field annotationIDField = ReflectionUtils.getDeclaredFieldByName(newInstance.getClass(),
				OntologyFieldNames.ANNOTATION_ID_FIELD_NAME);
		annotationIDField.set(newInstance, annotationID);
	}

	/**
	 * Returns candidates based on the evidences in the text.
	 * 
	 * @param instance
	 * @param slotSuperType
	 * @param exploreClassesWithoutTextualEvidence
	 * @return
	 */
	public static Set<IOBIEThing> getSlotFillerCandidates(OBIEInstance instance,
			Class<? extends IOBIEThing> slotSuperType,
			Set<Class<? extends IOBIEThing>> exploreClassesWithoutTextualEvidence) {

		Set<IOBIEThing> candidates = new HashSet<>();
		/*
		 * Add candidate for root super class
		 */
		addFillerCandidates(instance, slotSuperType, candidates,
				slotSuperType.getAnnotation(ImplementationClass.class).get(), exploreClassesWithoutTextualEvidence);

		/**
		 * TODO: insert
		 */
		try {
			@SuppressWarnings("unchecked")
			IndividualFactory<AbstractOBIEIndividual> individualFactory = (IndividualFactory<AbstractOBIEIndividual>) slotSuperType
					.getField("individualFactory").get(null);

			Collection<AbstractOBIEIndividual> individuals = individualFactory.getIndividuals();

			/*
			 * Get all possible candidates for individuals and filter by mentions in the
			 * text.
			 */
			for (AbstractOBIEIndividual individual : individuals) {
				addFillerIndividualCandidates(instance, slotSuperType, candidates, individual,
						exploreClassesWithoutTextualEvidence);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		/*
		 * Get all possible candidates and filter by mentions in the text.
		 */
		for (Class<? extends IOBIEThing> slotFillerType : slotSuperType.getAnnotation(AssignableSubInterfaces.class)
				.get()) {
			addFillerCandidates(instance, slotSuperType, candidates,
					slotFillerType.getAnnotation(ImplementationClass.class).get(),
					exploreClassesWithoutTextualEvidence);
		}
		return candidates;
	}

	private static void addFillerIndividualCandidates(OBIEInstance psinkDocument,
			Class<? extends IOBIEThing> baseClassType_interface, Set<IOBIEThing> candidates,
			AbstractOBIEIndividual individual, Set<Class<? extends IOBIEThing>> exploreClassesWithoutTextualEvidence) {

		boolean keepIndividual = includeIndividualForSampling(individual);

		if (!keepIndividual)
			return;

		/**
		 * TODO: should this be executed always or just in else? Create annotation for
		 * classes that do not need evidences?
		 */
		/**
		 * If not we need explicit text mentions to create this class.
		 */
		Set<NERLIndividualAnnotation> possibleNERAnnotations = psinkDocument.getNamedEntityLinkingAnnotations()
				.getIndividualAnnotations(individual);
		/*
		 * 
		 * Remember: Early pruning! Do not generate state where absolutely no evidence
		 * is in the text.
		 *
		 */
		if (possibleNERAnnotations == null) {
			return;
		}

		for (NERLIndividualAnnotation nera : possibleNERAnnotations) {
			try {

				IOBIEThing newInstance = createNewIndividual(baseClassType_interface, individual);

				fillBasicFields(newInstance, nera);
				fillIDField(newInstance, nera.annotationID);
				candidates.add(newInstance);
			} catch (InstantiationException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
				e.printStackTrace();
			}
		}
	}

	private static void addFillerCandidates(OBIEInstance psinkDocument,
			Class<? extends IOBIEThing> baseClassType_interface, Set<IOBIEThing> candidates,
			Class<? extends IOBIEThing> candidateType_class,
			Set<Class<? extends IOBIEThing>> exploreClassesWithoutTextualEvidence) {

		boolean keepClass = includeClassForSampling(candidateType_class);

		if (!keepClass)
			return;

		/*
		 * If the class can be differentiated between all of its siblings, then we can
		 * create this class without evidence in the text because there is a different
		 * property which makes this class important.
		 *
		 *
		 * If baseClassType is of type that should be sampled anyway even without
		 * textual evidence, add all direct candidates.
		 */

		/*
		 * We need to check whether a child class of that class can be an aux class.
		 * This happens if the direct interface is not an aux class but has subclasses
		 * that are.
		 */
		if (exploreClassesWithoutTextualEvidence.contains(candidateType_class)
				|| (isDifferentiableToAllSiblingClasses(candidateType_class, baseClassType_interface)
						|| isAuxiliaryProperty(baseClassType_interface))) {
			/**
			 * TESTME: Is it sufficient to create just a single state with this class.
			 * Otherwise create a state for each mention in the text. (This should be only
			 * necessary if the position or text of this "auxiliary" class is important.
			 */
			try {
				Field annotationIDField = ReflectionUtils.getDeclaredFieldByName(candidateType_class,
						OntologyFieldNames.ANNOTATION_ID_FIELD_NAME);

				if (annotationIDField != null) {

					IOBIEThing newInstance = createNewInstance(candidateType_class);
					annotationIDField.set(newInstance, UUID.randomUUID().toString());

					candidates.add(newInstance);
				}
			} catch (InstantiationException | IllegalAccessException | SecurityException e) {
				e.printStackTrace();
			}
		} else {

			/**
			 * TODO: should this be executed always or just in else? Create annotation for
			 * classes that do not need evidences?
			 */
			/**
			 * If not we need explicit text mentions to create this class.
			 */
			Set<NERLClassAnnotation> possibleNERAnnotations = psinkDocument.getNamedEntityLinkingAnnotations()
					.getClassAnnotations(candidateType_class);
			/*
			 * 
			 * Remember: Early pruning! Do not generate state where absolutely no evidence
			 * is in the text.
			 *
			 */
			if (possibleNERAnnotations == null) {
				return;
			}

			for (NERLClassAnnotation nera : possibleNERAnnotations) {
				try {

					IOBIEThing newInstance = createNewInstance(candidateType_class);

					fillBasicFields(newInstance, nera);
					fillIDField(newInstance, nera.annotationID);
					fillSemanticInterpretationField(newInstance, nera.getDTValueIfAnyElseTextMention());
					candidates.add(newInstance);
				} catch (InstantiationException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private static void fillSemanticInterpretationField(IOBIEThing newInstance, String dtOrTextValue)
			throws IllegalArgumentException, IllegalAccessException {
		if (newInstance.getClass().isAnnotationPresent(DatatypeProperty.class)) {

			Field scioValueField = ReflectionUtils.getDeclaredFieldByName(newInstance.getClass(),
					OntologyFieldNames.SEMANTIC_VALUE_FIELD_NAME);
			scioValueField.set(newInstance, dtOrTextValue);
		}
	}

	/**
	 * Creates a new instance of the given class type. The new instance is then pre
	 * filled with properties that are auxiliarily classes.
	 * 
	 * @param ipsinkThing
	 * @return
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	private static IOBIEThing createNewInstance(Class<? extends IOBIEThing> ipsinkThing)
			throws InstantiationException, IllegalAccessException {
		IOBIEThing newInstance = (IOBIEThing) ipsinkThing.newInstance();

		for (Field field : ReflectionUtils.getDeclaredOntologyFields(newInstance.getClass())) {

			// if (!field.isAnnotationPresent(OntologyModelContent.class))
			// continue;
			//
			// field.setAccessible(true);
			/*
			 * NOTE: Pre fill auxiliary fields as default.
			 */
			if (isAuxiliaryProperty(field.getType())) {
				field.set(newInstance, field.getType().getAnnotation(ImplementationClass.class).get().newInstance());
			}
		}
		return newInstance;
	}

	/**
	 * Creates a new instance of the given class type. The new instance is then pre
	 * filled with properties that are auxiliarily classes.
	 * 
	 * @param ipsinkThing
	 * @return
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	private static IOBIEThing createNewIndividual(Class<? extends IOBIEThing> ipsinkThing,
			AbstractOBIEIndividual individual) throws InstantiationException, IllegalAccessException {
		IOBIEThing newInstance = (IOBIEThing) ipsinkThing.newInstance();

		Field individualField = ReflectionUtils.getDeclaredFieldByName(ipsinkThing, "individual");
		individualField.set(newInstance, individual);

		for (Field field : ReflectionUtils.getDeclaredOntologyFields(newInstance.getClass())) {
			/*
			 * NOTE: Pre fill auxiliary fields as default.
			 */
			if (isAuxiliaryProperty(field.getType())) {
				field.set(newInstance, field.getType().getAnnotation(ImplementationClass.class).get().newInstance());
			}
		}
		return newInstance;
	}

	private static void fillBasicFields(IOBIEThing genClass, INERLAnnotation nera)
			throws NoSuchFieldException, IllegalAccessException

	{

		// Field textMentionField =
		// genClass.getClass().getDeclaredField(AbstractOntologyEnvironment.SCIO_TEXT_MENTION);
		// textMentionField.setAccessible(true);

		Field textMentionField = ReflectionUtils.getDeclaredFieldByName(genClass.getClass(),
				OntologyFieldNames.TEXT_MENTION_FIELD_NAME);
		textMentionField.set(genClass, nera.getText());

		// Field offsetField =
		// genClass.getClass().getDeclaredField(AbstractOntologyEnvironment.CHARACTER_OFFSET_FIELD);
		// offsetField.setAccessible(true);

		Field offsetField = ReflectionUtils.getDeclaredFieldByName(genClass.getClass(),
				OntologyFieldNames.CHARACTER_OFFSET_FIELD_NAME);
		offsetField.set(genClass, Integer.valueOf(nera.getOnset() + nera.getText().length()));

		// Field onsetField =
		// genClass.getClass().getDeclaredField(AbstractOntologyEnvironment.CHARACTER_ONSET_FIELD);
		// onsetField.setAccessible(true);

		Field onsetField = ReflectionUtils.getDeclaredFieldByName(genClass.getClass(),
				OntologyFieldNames.CHARACTER_ONSET_FIELD_NAME);
		onsetField.set(genClass, Integer.valueOf(nera.getOnset()));
	}

	/**
	 * Checks whether the given class is mainly used as construction class in the
	 * ontology. E.g. CompoundTreatment. The CompoundTreatment carries all
	 * information in its properties but the class name does not have information to
	 * distinguish its purpose as e.g. KetaminTreatment would have.
	 * 
	 * This method returns true if the class has at least one property that has no
	 * sibling classes. This makes it differentiable to its siblings.
	 * 
	 * @param classType
	 * @param baseClassType_interface
	 * @return
	 */
	public static boolean isDifferentiableToAllSiblingClasses(Class<? extends IOBIEThing> classType,
			final Class<? extends IOBIEThing> baseClassType_interface) {

		if (classType.isAnnotationPresent(NamedIndividual.class))
			return false;

		if (classType.isAnnotationPresent(DatatypeProperty.class))
			return false;

		if (isAuxiliaryProperty(baseClassType_interface))
			return false;

		if (!classType.isAnnotationPresent(DirectSiblings.class)) {
			return true;
		}

		if (isDifferentiableToAllSiblingsCache.containsKey(classType)
				&& isDifferentiableToAllSiblingsCache.get(classType).containsKey(baseClassType_interface))
			return isDifferentiableToAllSiblingsCache.get(classType).get(baseClassType_interface);

		isDifferentiableToAllSiblingsCache.putIfAbsent(classType, new HashMap<>());

		final List<Class<? extends IOBIEThing>> siblings = new ArrayList<>(
				Arrays.asList(classType.getAnnotation(DirectSiblings.class).get()));

		siblings.addAll(Arrays.stream(classType.getAnnotation(SuperRootClasses.class).get())

				.flatMap(c -> Arrays.stream(c.getAnnotation(AssignableSubClasses.class).get()))
				.collect(Collectors.toList()));

		/*
		 * CHECKME: If there are no siblings it is basically distinguishable to all
		 * others.
		 */
		boolean isDifferentiableToAllSiblings = true;

		for (Class<? extends IOBIEThing> sibClass : siblings) {

			if (sibClass == classType)
				continue;

			if (!includeClassForSampling(sibClass))
				continue;

			final Set<String> sibPropertyNames = ReflectionUtils.getDeclaredOntologyFields(sibClass).stream()
					.map(f -> f.getName()).collect(Collectors.toSet());

			final Set<String> diffPropertyNames = ReflectionUtils.getDeclaredOntologyFields(classType).stream()
					.map(f -> f.getName()).filter(n -> !sibPropertyNames.contains(n)).collect(Collectors.toSet());
			// final Set<String> sibPropertyNames =
			// Arrays.stream(sibClass.getDeclaredFields())
			// .filter(f ->
			// f.isAnnotationPresent(OntologyModelContent.class)).map(f ->
			// f.getName())
			// .collect(Collectors.toSet());
			//
			// final Set<String> diffPropertyNames =
			// Arrays.stream(classType.getDeclaredFields())
			// .filter(f ->
			// f.isAnnotationPresent(OntologyModelContent.class)).map(f ->
			// f.getName())
			// .filter(n ->
			// !sibPropertyNames.contains(n)).collect(Collectors.toSet());

			isDifferentiableToAllSiblings &= !diffPropertyNames.isEmpty();

		}

		isDifferentiableToAllSiblingsCache.get(classType).put(baseClassType_interface, isDifferentiableToAllSiblings);
		return isDifferentiableToAllSiblings;

	}

	/**
	 * Hand made rules for including specific classes for sampling. These classes
	 * are all leaf classes from the ontology and some extra classes that are
	 * frequently annotated although they are not leaf classes.
	 * 
	 * EXTEND: Hand made rules for skipping/keeping the candidate classes for
	 * sampling.
	 * 
	 * @param ipsinkThing
	 * @return true if the input class can be used for sampling.
	 */
	private static boolean includeClassForSampling(Class<? extends IOBIEThing> ipsinkThing) {

		/*
		 * TODO: Specify: for now pass all
		 */
		// /*
		// * If no leaf class for input = class
		// */
		// if ((!ipsinkThing.isInterface() &&
		// ipsinkThing.isAnnotationPresent(AssignableSubClasses.class)))
		// return false;
		//
		// /*
		// * If no leaf class for input = interface
		// */
		// if (ipsinkThing.isInterface() &&
		// ipsinkThing.getAnnotation(ImplementationClass.class).implementationClass()
		// .isAnnotationPresent(AssignableSubClasses.class))
		// return false;

		return true;
	}

	private static boolean includeIndividualForSampling(AbstractOBIEIndividual individualCandidate) {
		// TODO Auto-generated method stub
		return true;
	}
}
