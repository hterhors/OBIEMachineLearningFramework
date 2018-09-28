package de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.SuperRootClasses;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IDatatype;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.SlotIsFilledTemplate.Scope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.scope.OBIEFactorScope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.ReflectionUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.TemplateAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;
import factors.Factor;
import learning.Vector;

public class SlotIsFilledTemplate extends AbstractOBIETemplate<Scope> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static Logger log = LogManager.getFormatterLogger(SlotIsFilledTemplate.class.getName());

	public SlotIsFilledTemplate(OBIERunParameter parameter) {
		super(parameter);
	}

	class Scope extends OBIEFactorScope {

		final Class<? extends IOBIEThing> parentClassType;
		final int numberOfSlotFiller;
		final int numberOfDistinctSlotFiller;
		final String propertyNameChain;

		public Scope(Set<Class<? extends IOBIEThing>> influencedVariable,
				Class<? extends IOBIEThing> entityRootClassType, AbstractOBIETemplate<?> template,
				Class<? extends IOBIEThing> parentClassType, int numberOfSlotFiller, String propertyNameChain,
				int numberOfDistinctSlotFiller) {
			super(influencedVariable, entityRootClassType, template, parentClassType, numberOfSlotFiller,
					numberOfDistinctSlotFiller, propertyNameChain, entityRootClassType);
			this.numberOfSlotFiller = numberOfSlotFiller;
			this.parentClassType = parentClassType;
			this.propertyNameChain = propertyNameChain;
			this.numberOfDistinctSlotFiller = numberOfDistinctSlotFiller;
		}
	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();

		for (TemplateAnnotation entity : state.getCurrentPrediction().getTemplateAnnotations()) {

			factors.addAll(addFactorRecursive(entity.rootClassType, entity.get()));
		}

		return factors;
	}

	private List<Scope> addFactorRecursive(final Class<? extends IOBIEThing> entityRootClassType,
			IOBIEThing scioClass) {
		return addFactorRecursive(entityRootClassType, null, scioClass, "", 1, 1);
	}

	@SuppressWarnings("unchecked")
	private List<Scope> addFactorRecursive(final Class<? extends IOBIEThing> entityRootClassType,
			final Class<? extends IOBIEThing> parentClassType, IOBIEThing scioClass, String propertyNameChain,
			int numberOfSlotFiller, int numberOfDistinctSlotFiller) {
		List<Scope> factors = new ArrayList<>();

		final Set<Class<? extends IOBIEThing>> influencedVariables = new HashSet<>();
//		influencedVariables.add(parentClassType);

		if (parentClassType != null) {
			for (Class<? extends IOBIEThing> parentClass : parentClassType.getAnnotation(SuperRootClasses.class)
					.get()) {
				factors.add(new Scope(influencedVariables, entityRootClassType, this, parentClass, numberOfSlotFiller,
						propertyNameChain, numberOfDistinctSlotFiller));
			}
		}
		if (scioClass == null)
			return factors;
		/*
		 * Add factors for object type properties.
		 * 
		 * Parent-Child relation
		 */

		ReflectionUtils.getDeclaredOntologyFields(scioClass.getClass()).forEach(field -> {
			try {
				if (field.isAnnotationPresent(RelationTypeCollection.class)) {
					List<IOBIEThing> data = ((List<IOBIEThing>) field.get(scioClass));

					final int num = data.size();
					final int distinctNum = distinctSize(data, field.isAnnotationPresent(DatatypeProperty.class));

					for (IOBIEThing listObject : data) {
						factors.addAll(addFactorRecursive(entityRootClassType, scioClass.getClass(), listObject,
								propertyNameChain + "->" + field.getName(), num, distinctNum));
					}
				} else {
					factors.addAll(addFactorRecursive(entityRootClassType, scioClass.getClass(),
							(IOBIEThing) field.get(scioClass), propertyNameChain + "->" + field.getName(),
							field.get(scioClass) == null ? 0 : 1, 1));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		return factors;
	}

	private int distinctSize(List<IOBIEThing> data, boolean isDatatype) {

		/**
		 * TODO: FIX THAT DATA MIGHT BE NULL IN EXPLORER!
		 */
		if (data == null)
			return 0;

		if (isDatatype) {
			return (int) data.stream().filter(d -> d != null).map(d -> ((IDatatype) d).getSemanticValue()).unordered()
					.distinct().count();
		} else {
			return (int) data.stream().filter(d -> d != null).map(d -> d.getClass().getName()).unordered().distinct()
					.count();
		}
	}

	@Override
	public void computeFactor(Factor<Scope> factor) {
		Vector featureVector = factor.getFeatureVector();

		final String parentClassName = factor.getFactorScope().parentClassType == null ? null
				: factor.getFactorScope().parentClassType.getSimpleName();
		final boolean slotIsFilled = factor.getFactorScope().numberOfSlotFiller > 0;

		final String propertyNameChain = factor.getFactorScope().propertyNameChain;

		featureVector.set("[" + parentClassName + "]" + propertyNameChain + " contains >2 element",
				factor.getFactorScope().numberOfSlotFiller >= 2);
		featureVector.set("[" + parentClassName + "]" + propertyNameChain + " contains >3 element",
				factor.getFactorScope().numberOfSlotFiller >= 3);
		featureVector.set("[" + parentClassName + "]" + propertyNameChain + " contains >4 element",
				factor.getFactorScope().numberOfSlotFiller >= 4);

		featureVector.set("[" + parentClassName + "]" + propertyNameChain + " contains >2 distinct element",
				factor.getFactorScope().numberOfDistinctSlotFiller >= 2
						&& factor.getFactorScope().numberOfSlotFiller >= 2);
		featureVector.set("[" + parentClassName + "]" + propertyNameChain + " contains >3 distinct element",
				factor.getFactorScope().numberOfDistinctSlotFiller >= 3
						&& factor.getFactorScope().numberOfSlotFiller <= 3);
		featureVector.set("[" + parentClassName + "]" + propertyNameChain + " contains >4 distinct element",
				factor.getFactorScope().numberOfDistinctSlotFiller >= 4
						&& factor.getFactorScope().numberOfSlotFiller >= 4);

		if (factor.getFactorScope().numberOfSlotFiller > 1) {
			final boolean allDistinct = factor.getFactorScope().numberOfSlotFiller == factor
					.getFactorScope().numberOfDistinctSlotFiller;
			featureVector.set("[" + parentClassName + "]" + propertyNameChain + " all distinct", allDistinct);
		}

		featureVector.set("[" + parentClassName + "]" + propertyNameChain + " contains elements", slotIsFilled);
		featureVector.set("[" + parentClassName + "]" + propertyNameChain + " is empty", !slotIsFilled);

	}

}
