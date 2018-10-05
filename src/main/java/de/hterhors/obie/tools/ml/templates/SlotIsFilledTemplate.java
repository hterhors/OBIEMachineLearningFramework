package de.hterhors.obie.tools.ml.templates;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IDatatype;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.tools.ml.run.param.OBIERunParameter;
import de.hterhors.obie.tools.ml.templates.SlotIsFilledTemplate.Scope;
import de.hterhors.obie.tools.ml.templates.scope.OBIEFactorScope;
import de.hterhors.obie.tools.ml.utils.ReflectionUtils;
import de.hterhors.obie.tools.ml.variables.OBIEState;
import de.hterhors.obie.tools.ml.variables.TemplateAnnotation;
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

		final String templateName;
		final int numberOfSlotFiller;
		final int numberOfDistinctSlotFiller;
		final String propertyNameChain;

		public Scope(Class<? extends IOBIEThing> entityRootClassType, AbstractOBIETemplate<?> template,
				String templateName, int numberOfSlotFiller, String propertyNameChain, int numberOfDistinctSlotFiller) {
			super(template, templateName, numberOfSlotFiller, numberOfDistinctSlotFiller, propertyNameChain,
					entityRootClassType);
			this.numberOfSlotFiller = numberOfSlotFiller;
			this.templateName = templateName;
			this.propertyNameChain = propertyNameChain;
			this.numberOfDistinctSlotFiller = numberOfDistinctSlotFiller;
		}
	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();

		for (TemplateAnnotation entity : state.getCurrentPrediction().getTemplateAnnotations()) {

			factors.addAll(addFactorRecursive(entity.rootClassType, entity.getTemplateAnnotation()));
		}

		return factors;
	}

	private List<Scope> addFactorRecursive(final Class<? extends IOBIEThing> entityRootClassType,
			IOBIEThing scioClass) {
		return addFactorRecursive(entityRootClassType, null, scioClass, "", 1, 1);
	}

	@SuppressWarnings("unchecked")
	private List<Scope> addFactorRecursive(final Class<? extends IOBIEThing> templateRootClassType,
			final Class<? extends IOBIEThing> parentClassType, IOBIEThing obieThing, String propertyNameChain,
			int numberOfSlotFiller, int numberOfDistinctSlotFiller) {
		List<Scope> factors = new ArrayList<>();

		if (parentClassType != null) {
			for (Class<? extends IOBIEThing> parentClass : ReflectionUtils.getSuperRootClasses(parentClassType)) {
				factors.add(new Scope(templateRootClassType, this, parentClass.getSimpleName(), numberOfSlotFiller,
						propertyNameChain, numberOfDistinctSlotFiller));
			}
		}
		if (obieThing == null)
			return factors;
		/*
		 * Add factors for object type properties.
		 * 
		 * Parent-Child relation
		 */

		ReflectionUtils.getDeclaredOntologyFields(obieThing.getClass()).forEach(field -> {
			try {
				if (field.isAnnotationPresent(RelationTypeCollection.class)) {
					List<IOBIEThing> data = ((List<IOBIEThing>) field.get(obieThing));

					final int num = data.size();
					final int distinctNum = distinctSize(data, ReflectionUtils.isAnnotationPresent(field, DatatypeProperty.class));

					for (IOBIEThing listObject : data) {
						factors.addAll(addFactorRecursive(templateRootClassType, obieThing.getClass(), listObject,
								propertyNameChain + "->" + field.getName(), num, distinctNum));
					}
				} else {
					factors.addAll(addFactorRecursive(templateRootClassType, obieThing.getClass(),
							(IOBIEThing) field.get(obieThing), propertyNameChain + "->" + field.getName(),
							field.get(obieThing) == null ? 0 : 1, 1));
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

		final String parentClassName = factor.getFactorScope().templateName;
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
