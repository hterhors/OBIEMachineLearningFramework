package de.hterhors.obie.ml.templates;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IDatatype;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.templates.CooccurrenceTemplate.Scope;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import factors.Factor;
import factors.FactorScope;
import learning.Vector;

/**
 * This template measures the co-occurrence between the surface form and the
 * annotated class of each individual annotation as well as co-occurrences
 * between classes within the same obie-template for parent-child and
 * child-child relations.
 * 
 * Each co-occurrence is enriched with the property-chain information. The
 * property chain describes the way through the complex structure of the
 * obie-template.
 * 
 * In case of distant supervision is enabled, we add all surface forms of that
 * type of class with the annotated class at once.
 * 
 * @author hterhors
 *
 * @date Jan 11, 2018
 * 
 *       Probably a bad template!
 * 
 */
public class CooccurrenceTemplate extends AbstractOBIETemplate<Scope> {

	public CooccurrenceTemplate(RunParameter parameter) {
		super(parameter);
		this.enableDistantSupervision = parameter.exploreOnOntologyLevel;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static Logger log = LogManager.getFormatterLogger(CooccurrenceTemplate.class.getName());

	/**
	 * Whether distant supervision is enabled for this template or not. This effects
	 * the way of calculating the factors and features!
	 */
	private final boolean enableDistantSupervision;

	class Scope extends FactorScope {

		/**
		 * The parent class type of the obie-template in a parent-child relation.
		 * Otherwise the first child of the pair.
		 */
		final String value1;

		/**
		 * The class type of the investigated child-property in a parent-child relation.
		 * Otherwise the second child of the pair.
		 */
		final String value2;

//		/**
//		 * The surface forms of the child-property. If distant supervision is enabled
//		 * this set contains surface forms of all annotations that have the class type
//		 * of the child-property. Else the set contains just a single surface form of
//		 * that respective annotation.
//		 */
//		final Set<String> type1SurfaceForms;
//
//		/**
//		 * The surface forms of the parent. If distant supervision is enabled this set
//		 * contains surface forms of all annotations that have the class type of the
//		 * child-property. Else the set contains just a single surface form of that
//		 * respective annotation.
//		 */
//		final Set<String> type2SurfaceForms;

		/**
		 * This describes the property chain which connects the parent class with the
		 * investigated child property. If it is not an parent-child relation this is
		 * null.
		 */
		final String slotName;

		public Scope(Class<? extends IOBIEThing> entityRootClassType, String value1, String propertyChain,
				String value2) {
			super(CooccurrenceTemplate.this, entityRootClassType, value1, value2, propertyChain);
			this.value1 = value1;
			this.value2 = value2;
			this.slotName = propertyChain;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		for (TemplateAnnotation entity : state.getCurrentTemplateAnnotations().getTemplateAnnotations()) {
			addFactorRecursive(factors, entity.rootClassType, state.getInstance(), entity.getThing());
		}

		return factors;
	}

	private void addFactorRecursive(List<Scope> factors, Class<? extends IOBIEThing> rootClassType,
			OBIEInstance obieInstance, IOBIEThing obieClass) {
		try {
			addFactorRecursive(factors, obieInstance, rootClassType, obieClass);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}

	}

	/**
	 * 
	 * childClass might be null e.g. if the field is not set in the parent. The type
	 * of the child Class is NOT null as we then pass the fields type parameter, to
	 * allow null-capturing.
	 * 
	 * @param rootClassType
	 * @param obieInstance
	 * 
	 * 
	 * @param parentThing       the parent can only be null in the initial call.
	 *                          since the root class does not have a parent.
	 * @param childClass
	 * @param propertyNameChain
	 * @return
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	@SuppressWarnings("unchecked")
	private void addFactorRecursive(List<Scope> factors, OBIEInstance obieInstance,
			Class<? extends IOBIEThing> rootClassType, final IOBIEThing parentThing)
			throws IllegalArgumentException, IllegalAccessException {

		if (parentThing == null || parentThing.getIndividual() == null)
			return;

		/*
		 * template-slot relation
		 */
		{
			final String value1 = parentThing.getIndividual().name;
			for (Field slot : ReflectionUtils.getSlots(parentThing.getClass())) {
				if (ReflectionUtils.isAnnotationPresent(slot, RelationTypeCollection.class)) {
					for (IOBIEThing slotValue : (List<? extends IOBIEThing>) slot.get(parentThing)) {
						addScope(factors, obieInstance, rootClassType, value1, slot, slotValue);
					}
				} else {
					IOBIEThing slotValue = (IOBIEThing) slot.get(parentThing);
					addScope(factors, obieInstance, rootClassType, value1, slot, slotValue);
				}
			}
		}
		/*
		 * slot-slot relation.
		 * 
		 * For every distinct slot pair do: for every distinct slotValue pair do: add
		 * factor
		 */
		final List<Field> slots = ReflectionUtils.getSlots(parentThing.getClass());

		{
			for (int i = 0; i < slots.size(); i++) {

				final Field slot1 = slots.get(i);

				final List<IOBIEThing> slot1Values = getFillers(parentThing, slot1);

				for (int j = i + 1; j < slots.size(); j++) {

					final Field slot2 = ReflectionUtils.getSlots(parentThing.getClass()).get(j);

					final List<IOBIEThing> slot2Values = getFillers(parentThing, slot2);

					for (int k = 0; k < slot1Values.size(); k++) {
						final IOBIEThing slotValue1 = slot1Values.get(k);

						if (slotValue1 == null)
							continue;

						final String value1 = getSlotValue(slot1, slotValue1);

						for (int l = 0; l < slot2Values.size(); l++) {
							IOBIEThing slotValue2 = slot2Values.get(l);

							if (slotValue2 == null)
								continue;

							final String value2 = getSlotValue(slot2, slotValue2);

							factors.add(new Scope(rootClassType, value1,
									"(" + slot1.getName() + ")<->(" + slot2.getName() + ")", value2));
						}
					}
				}
			}
		}
		/*
		 * TODO: kinda inefficient
		 * 
		 * inter-slot relation.
		 * 
		 * For every distinct slot pair do: for every distinct slotValue pair do: add
		 * factor
		 */
		{

			for (int i = 0; i < slots.size(); i++) {

				final Field slot = slots.get(i);

				final List<IOBIEThing> slotValues = getFillers(parentThing, slot);

				if (slotValues.size() < 2)
					continue;

				for (int k = 0; k < slotValues.size() - 1; k++) {
					final IOBIEThing slotValue1 = slotValues.get(k);

					if (slotValue1 == null)
						continue;

					final String value1 = getSlotValue(slot, slotValue1);

					for (int l = k + 1; l < slotValues.size(); l++) {
						IOBIEThing slotValue2 = slotValues.get(l);

						if (slotValue2 == null)
							continue;

						final String value2 = getSlotValue(slot, slotValue2);

						factors.add(new Scope(rootClassType, value1, "<(" + slot.getName() + ")>", value2));
					}
				}
			}
		}

		return;
	}

	private String getSlotValue(final Field slot, final IOBIEThing slotValue) {
		final String value;
		if (ReflectionUtils.isAnnotationPresent(slot, DatatypeProperty.class)) {
			value = ((IDatatype) slotValue).getInterpretedValue();
		} else {
			value = slotValue.getIndividual().name;
		}
		return value;
	}

	private void addScope(List<Scope> factors, OBIEInstance obieInstance, Class<? extends IOBIEThing> rootClassType,
			final String value1, Field slot, IOBIEThing slotValue)
			throws IllegalArgumentException, IllegalAccessException {

		if (slotValue == null)
			return;

		final String value2;

		if (ReflectionUtils.isAnnotationPresent(slot, DatatypeProperty.class)) {
			value2 = ((IDatatype) slotValue).getInterpretedValue();

		} else {
			value2 = slotValue.getIndividual().name;
			addFactorRecursive(factors, obieInstance, rootClassType, slotValue);
		}
		factors.add(new Scope(rootClassType, value1, "->" + slot.getName() + "->", value2));
	}

	/**
	 * Returns the surface forms of the given object. If distant supervision is
	 * enabled all surface forms are returned that belongs to the class type of the
	 * given object.If DV is not enabled the returned set contains only the
	 * annotated surface form of the given object.
	 * 
	 * @param filler
	 * @param internalInstance
	 * @return null if there are no annotations for that class
	 */
	private Set<String> getSurfaceForms(final IOBIEThing filler, OBIEInstance internalInstance) {

		if (filler == null)
			return null;

		final Set<String> surfaceForms;
		if (enableDistantSupervision
				&& !ReflectionUtils.isAnnotationPresent(filler.getClass(), DatatypeProperty.class)) {
			/*
			 * If DV is enabled add all surface forms of that class.
			 */
			if (internalInstance.getNamedEntityLinkingAnnotations().containsClassAnnotations(filler.getClass())) {
				surfaceForms = internalInstance.getNamedEntityLinkingAnnotations()
						.getClassAnnotations(filler.getClass()).stream()
						.map(nera -> nera.getDTValueIfAnyElseTextMention()).collect(Collectors.toSet());
			} else {
				return null;
			}
		} else {
			/*
			 * If DV is not enabled add just the surface form of that individual annotation.
			 */
			surfaceForms = new HashSet<>();
			if (ReflectionUtils.isAnnotationPresent(filler.getClass(), DatatypeProperty.class)) {
				surfaceForms.add(((IDatatype) filler).getInterpretedValue());
			} else {
				surfaceForms.add(filler.getTextMention());
			}

		}
		return surfaceForms;
	}

	/**
	 * Returns the values of the field of the given object. If the field is not of
	 * type list, a list with one element is created. The returned list can include
	 * null objects.
	 * 
	 * @param childClass
	 * @param field
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private List<IOBIEThing> getFillers(IOBIEThing childClass, final Field field) {
		final List<IOBIEThing> fillers;

		try {
			if (ReflectionUtils.isAnnotationPresent(field, RelationTypeCollection.class)) {
				fillers = (List<IOBIEThing>) field.get(childClass);
			} else {
				fillers = new ArrayList<>(1);
				IOBIEThing filler = (IOBIEThing) field.get(childClass);
				if (filler != null) {
					fillers.add(filler);
				}
			}
			return fillers;
		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}
	}

	@Override
	public void computeFactor(Factor<Scope> factor) {
		Vector featureVector = factor.getFeatureVector();

		final String value1 = factor.getFactorScope().value1;

		final String value2 = factor.getFactorScope().value2;

		final String property = factor.getFactorScope().slotName;

		/*
		 * Add classType1 co-occ classType2
		 */
		featureVector.set(value1 + property + value2, true);

	}

}
