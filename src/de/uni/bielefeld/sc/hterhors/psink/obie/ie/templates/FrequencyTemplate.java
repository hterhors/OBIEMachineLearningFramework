package de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DirectInterface;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.OntologyModelContent;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IDataType;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.FrequencyTemplate.Scope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.scope.OBIEFactorScope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.HighFrequencyUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.HighFrequencyUtils.FrequencyPair;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.EntityAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;
import factors.Factor;
import learning.Vector;

/**
 * Captures the frequency of evidence in the text for a ontology class.
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

	private static Logger log = LogManager.getFormatterLogger(FrequencyTemplate.class.getName());

	public FrequencyTemplate(OBIERunParameter parameter) {
		super(parameter);
	}

	/**
	 * The factor scope contains just the class and the frequency value of evidence.
	 * 
	 * @author hterhors
	 *
	 * @date May 9, 2017
	 */
	class Scope extends OBIEFactorScope {

		/**
		 * The document.
		 */
		final OBIEInstance instance;

		/**
		 * The assigned class
		 */
		final Class<IOBIEThing> scioClassType;

		/**
		 * The value for the data type if scioClassType is a Datatype class.
		 */
		final String dataTypeValue;

		/**
		 * The property class type the scioClass is assigned in.
		 */
		final Class<? extends IOBIEThing> propertyInterfaceType;

		public Scope(Set<Class<? extends IOBIEThing>> influencedVariables,
				Class<? extends IOBIEThing> entityRootClassType, AbstractOBIETemplate<?> template,
				OBIEInstance instance, final Class<IOBIEThing> scioClassType, final String dataTypeValue,
				final Class<? extends IOBIEThing> propertyClassType) {
			super(influencedVariables, entityRootClassType, template, scioClassType, dataTypeValue, instance,
					propertyClassType, entityRootClassType);
			this.scioClassType = scioClassType;
			this.dataTypeValue = dataTypeValue;
			this.instance = instance;
			this.propertyInterfaceType = propertyClassType;
		}

		@Override
		public String toString() {
			return "Scope [instance=" + instance + ", scioClassType=" + scioClassType + ", dataTypeValue="
					+ dataTypeValue + ", propertyInterfaceType=" + propertyInterfaceType + "]";
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		for (EntityAnnotation entity : state.getCurrentPrediction().getEntityAnnotations()) {

			factors.addAll(addFactorRecursive(entity.rootClassType, state.getInstance(), entity.getAnnotationInstance(),
					entity.rootClassType));

		}
		return factors;
	}

	@SuppressWarnings("unchecked")
	private List<Scope> addFactorRecursive(Class<? extends IOBIEThing> entityRootClassType, OBIEInstance document,
			IOBIEThing scioClass, Class<? extends IOBIEThing> propertyClassType) {
		List<Scope> factors = new ArrayList<>();

		if (scioClass == null)
			return factors;

		final Set<Class<? extends IOBIEThing>> influencedVariables = new HashSet<>();
		influencedVariables.add(scioClass.getClass());

		if (scioClass.getClass().isAnnotationPresent(DatatypeProperty.class)) {
			factors.add(new Scope(influencedVariables, entityRootClassType, this, document,
					(Class<IOBIEThing>) scioClass.getClass(), ((IDataType) scioClass).getSemanticValue(),
					propertyClassType.isInterface() ? propertyClassType
							: propertyClassType.getAnnotation(DirectInterface.class).get()));
		} else {
			factors.add(new Scope(influencedVariables, entityRootClassType, this, document,
					(Class<IOBIEThing>) scioClass.getClass(), null, propertyClassType.isInterface() ? propertyClassType
							: propertyClassType.getAnnotation(DirectInterface.class).get()));
		}

		/*
		 * Add factors for object type properties.
		 */
		Arrays.stream(scioClass.getClass().getDeclaredFields())
				.filter(f -> f.isAnnotationPresent(OntologyModelContent.class)).forEach(field -> {
					field.setAccessible(true);
					try {
						if (field.isAnnotationPresent(RelationTypeCollection.class)) {
							Class<? extends IOBIEThing> fieldGenericType = (Class<? extends IOBIEThing>) ((ParameterizedType) field
									.getGenericType()).getActualTypeArguments()[0];
							for (IOBIEThing element : (List<IOBIEThing>) field.get(scioClass)) {
								factors.addAll(
										addFactorRecursive(entityRootClassType, document, element, fieldGenericType));
							}

						} else {
							factors.addAll(addFactorRecursive(entityRootClassType, document,
									(IOBIEThing) field.get(scioClass), (Class<? extends IOBIEThing>) field.getType()));
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				});
		return factors;
	}

	@Override
	public void computeFactor(Factor<Scope> factor) {
		Vector featureVector = factor.getFeatureVector();
		if (factor.getFactorScope().propertyInterfaceType.isAnnotationPresent(DatatypeProperty.class)
				&& factor.getFactorScope().dataTypeValue == null) {
			// featureVector.set("DataTypeIsNull", true);
		} else {

			final List<FrequencyPair> mostFrequentClasses = HighFrequencyUtils.determineMostFrequentClasses(
					factor.getFactorScope().propertyInterfaceType, factor.getFactorScope().instance, 2);

			if (!(mostFrequentClasses.isEmpty() || mostFrequentClasses.get(0).bestClass == null)) {

				final boolean realMax;

				if (mostFrequentClasses.size() == 2) {
					realMax = mostFrequentClasses.get(0).frequency > mostFrequentClasses.get(1).frequency;
				} else {
					realMax = true;
				}
				final Class<IOBIEThing> mostFrequentClass = mostFrequentClasses.get(0).bestClass;

				if (factor.getFactorScope().propertyInterfaceType.isAnnotationPresent(DatatypeProperty.class)) {
					final boolean isMaxFrequency = factor.getFactorScope().dataTypeValue
							.equals(mostFrequentClasses.get(0).dataTypeValue);
//						featureVector.set("DT_has_max_evidence", isMaxFrequency);
//						featureVector.set("DT_has_real_max_evidence", isMaxFrequency && realMax);
					featureVector.set(factor.getFactorScope().scioClassType.getSimpleName() + "_has_max_evidence",
							isMaxFrequency);
					featureVector.set(factor.getFactorScope().scioClassType.getSimpleName() + "_has_real_max_evidence",
							isMaxFrequency && realMax);
//						featureVector.set("DataTypeIsNOTNull", true);
				} else {
					final boolean isMaxFrequency = factor.getFactorScope().scioClassType == mostFrequentClass;
//					featureVector.set("Class_has_max_evidence", isMaxFrequency);
//					featureVector.set("Class_has_real_max_evidence", isMaxFrequency && realMax);
					featureVector.set(factor.getFactorScope().scioClassType.getSimpleName() + "_has_max_evidence",
							isMaxFrequency);
					featureVector.set(factor.getFactorScope().scioClassType.getSimpleName() + "_has_real_max_evidence",
							isMaxFrequency && realMax);
					featureVector.set(
							factor.getFactorScope().propertyInterfaceType.getSimpleName() + "_has_max_evidence",
							isMaxFrequency);
					featureVector.set(
							factor.getFactorScope().propertyInterfaceType.getSimpleName() + "_has_real_max_evidence",
							isMaxFrequency && realMax);
				}

			} else {
				featureVector.set(
						factor.getFactorScope().scioClassType.getSimpleName() + "_does_not_need_textual_evidence ",
						true);
				featureVector.set(factor.getFactorScope().propertyInterfaceType.getSimpleName()
						+ "_does_not_need_textual_evidence ", true);
			}
		}
	}

}
