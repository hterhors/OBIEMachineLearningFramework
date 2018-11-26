package de.hterhors.obie.ml.templates;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IDatatype;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.templates.FrequencyTemplate.Scope;
import de.hterhors.obie.ml.utils.HighFrequencyUtils;
import de.hterhors.obie.ml.utils.HighFrequencyUtils.ClassFrequencyPair;
import de.hterhors.obie.ml.utils.HighFrequencyUtils.IndividualFrequencyPair;
import de.hterhors.obie.ml.utils.ReflectionUtils;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import factors.Factor;
import factors.FactorScope;
import learning.Vector;

/**
 * Captures the frequency of evidence in the text for an ontology class or
 * individual.
 * 
 * @author hterhors
 *
 * @date May 9, 2017
 */
public class FrequencyTemplate extends AbstractOBIETemplate<Scope> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final String GREATER_THAN_MAX_EV = " has > max evidence";

	private static final String MAX_EVIDENCE = " has >= max evidence";

	private static Logger log = LogManager.getFormatterLogger(FrequencyTemplate.class.getName());

	public FrequencyTemplate(RunParameter parameter) {
		super(parameter);
	}

	/**
	 * The factor scope contains just the class and the frequency value of evidence.
	 * 
	 * @author hterhors
	 *
	 * @date May 9, 2017
	 */
	class Scope extends FactorScope {

		/**
		 * The document.
		 */
		final OBIEInstance instance;

		/**
		 * The assigned class
		 */
		final Class<IOBIEThing> thingType;

		/**
		 * The individual if any.
		 */
		final AbstractIndividual individual;

		/**
		 * The value for the data type if scioClassType is a Datatype class.
		 */
		final String datatypeValue;

		/**
		 * The property class type the scioClass is assigned in.
		 */
		final Class<? extends IOBIEThing> slotValueType;

		public Scope(Class<? extends IOBIEThing> entityRootClassType, AbstractOBIETemplate<?> template,
				OBIEInstance instance, final Class<IOBIEThing> thingType, final String datatypeValue,
				final Class<? extends IOBIEThing> slotValueType, AbstractIndividual individual) {
			super(template, thingType, datatypeValue, instance, slotValueType, entityRootClassType, individual);
			this.thingType = thingType;
			this.datatypeValue = datatypeValue;
			this.instance = instance;
			this.slotValueType = slotValueType;
			this.individual = individual;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		for (TemplateAnnotation entity : state.getCurrentTemplateAnnotations().getTemplateAnnotations()) {

			addFactorRecursive(factors, entity.rootClassType, state.getInstance(), entity.getThing(),
					entity.rootClassType);

		}
		return factors;
	}

	@SuppressWarnings("unchecked")
	private void addFactorRecursive(List<Scope> factors, Class<? extends IOBIEThing> entityRootClassType,
			OBIEInstance instance, IOBIEThing obieThing, Class<? extends IOBIEThing> propertyClassType) {

		if (obieThing == null)
			return;

		if (ReflectionUtils.isAnnotationPresent(obieThing.getClass(), DatatypeProperty.class)) {
			factors.add(new Scope(entityRootClassType, this, instance, (Class<IOBIEThing>) obieThing.getClass(),
					((IDatatype) obieThing).getSemanticValue(), propertyClassType.isInterface() ? propertyClassType
							: ReflectionUtils.getDirectInterfaces(propertyClassType),
					null));
		} else {
			factors.add(
					new Scope(entityRootClassType, this, instance, (Class<IOBIEThing>) obieThing.getClass(), null,
							propertyClassType.isInterface() ? propertyClassType
									: ReflectionUtils.getDirectInterfaces(propertyClassType),
							obieThing.getIndividual()));
		}

		/*
		 * Add factors for object type properties.
		 */

		ReflectionUtils.getSlots(obieThing.getClass()).forEach(field -> {
			try {
				if (ReflectionUtils.isAnnotationPresent(field, RelationTypeCollection.class)) {
					Class<? extends IOBIEThing> fieldGenericType = (Class<? extends IOBIEThing>) ((ParameterizedType) field
							.getGenericType()).getActualTypeArguments()[0];
					for (IOBIEThing element : (List<IOBIEThing>) field.get(obieThing)) {
						addFactorRecursive(factors, entityRootClassType, instance, element, fieldGenericType);
					}

				} else {
					addFactorRecursive(factors, entityRootClassType, instance, (IOBIEThing) field.get(obieThing),
							(Class<? extends IOBIEThing>) field.getType());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	@Override
	public void computeFactor(Factor<Scope> factor) {
		Vector featureVector = factor.getFeatureVector();

		if (factor.getFactorScope().individual != null) {

			final List<IndividualFrequencyPair> mostFrequentIndividuals = HighFrequencyUtils.getMostFrequentIndividuals(
					factor.getFactorScope().slotValueType, factor.getFactorScope().instance, 2);

			if (!(mostFrequentIndividuals.isEmpty() || mostFrequentIndividuals.get(0).belongingClazz == null)) {

				final boolean realIndMax;

				if (mostFrequentIndividuals.size() == 2) {
					realIndMax = mostFrequentIndividuals.get(0).frequency > mostFrequentIndividuals.get(1).frequency;
				} else {
					realIndMax = true;
				}
				final AbstractIndividual mostFrequentIndividual = mostFrequentIndividuals.get(0).individual;

				final boolean isMaxFrequency = factor.getFactorScope().individual.equals(mostFrequentIndividual);

				/*
				 * For abstract individual type
				 */
				featureVector.set(
						ReflectionUtils.simpleName(factor.getFactorScope().individual.getClass()) + MAX_EVIDENCE,
						isMaxFrequency);
				featureVector.set(ReflectionUtils.simpleName(factor.getFactorScope().individual.getClass())
						+ "_has_real_max_evidence", isMaxFrequency && realIndMax);

				/*
				 * For specific individual
				 */
				featureVector.set(factor.getFactorScope().individual.name + MAX_EVIDENCE, isMaxFrequency);
				featureVector.set(factor.getFactorScope().individual.name + GREATER_THAN_MAX_EV,
						isMaxFrequency && realIndMax);
			}
		}

		final List<ClassFrequencyPair> mostFrequentClasses = HighFrequencyUtils.getMostFrequentClassesOrValue(
				factor.getFactorScope().slotValueType, factor.getFactorScope().instance, 2);

		if (!mostFrequentClasses.isEmpty() && mostFrequentClasses.get(0).clazz != null) {

			final boolean realMax;

			if (mostFrequentClasses.size() == 2) {
				realMax = mostFrequentClasses.get(0).frequency > mostFrequentClasses.get(1).frequency;
			} else {
				realMax = true;
			}

			if (ReflectionUtils.isAnnotationPresent(factor.getFactorScope().slotValueType, DatatypeProperty.class)) {
				if (factor.getFactorScope().datatypeValue == null) {
				} else {

					final boolean isMaxFrequency = factor.getFactorScope().datatypeValue
							.equals(mostFrequentClasses.get(0).datatypeValue);
					featureVector.set(ReflectionUtils.simpleName(factor.getFactorScope().thingType) + MAX_EVIDENCE,
							isMaxFrequency);
					featureVector.set(
							ReflectionUtils.simpleName(factor.getFactorScope().thingType) + GREATER_THAN_MAX_EV,
							isMaxFrequency && realMax);
				}
			} else {

				final Class<? extends IOBIEThing> mostFrequentClass = mostFrequentClasses.get(0).clazz;

				final boolean isMaxFrequency = factor.getFactorScope().thingType == mostFrequentClass;

				final String thingTypeName = ReflectionUtils.simpleName(factor.getFactorScope().thingType);
				final String slotValueTypeName = ReflectionUtils.simpleName(factor.getFactorScope().slotValueType);

				featureVector.set(thingTypeName + MAX_EVIDENCE, isMaxFrequency);
				featureVector.set(thingTypeName + GREATER_THAN_MAX_EV, isMaxFrequency && realMax);
				featureVector.set(slotValueTypeName + MAX_EVIDENCE, isMaxFrequency);
				featureVector.set(slotValueTypeName + GREATER_THAN_MAX_EV, isMaxFrequency && realMax);
			}

		}
	}

}
