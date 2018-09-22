package de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IDataType;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.HeterogeneousSlotTemplate.Scope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.scope.OBIEFactorScope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.EntityAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;
import factors.Factor;
import learning.Vector;

/**
 * Measures the actual heterogeneity of actual assigned tokens for slots. Doing
 * this, we try to prevent the system from assigning or using the same value
 * again and again.
 * 
 * @author hterhors
 *
 * @date Nov 13, 2017
 */
public class HeterogeneousSlotTemplate extends AbstractOBIETemplate<Scope> {

	public HeterogeneousSlotTemplate(OBIERunParameter parameter) {
		super(parameter);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static Logger log = LogManager.getFormatterLogger(HeterogeneousSlotTemplate.class.getName());

	class Scope extends OBIEFactorScope {

		/**
		 * The number of slots that have the same text assigned.
		 */
		final public int numberOfSlot;

		/**
		 * The type of the slot that we are looking at.
		 */
		final public Class<? extends IOBIEThing> classType;

		/**
		 * The assigned text.
		 */
		final public String text;

		public Scope(Set<Class<? extends IOBIEThing>> influencedVariables,
				Class<? extends IOBIEThing> entityRootClassType, AbstractOBIETemplate<?> template,
				Class<? extends IOBIEThing> classType, String text, int numberOfSlot) {
			super(influencedVariables, entityRootClassType, template, entityRootClassType, classType, text,
					numberOfSlot);
			this.classType = classType;
			this.text = text;
			this.numberOfSlot = numberOfSlot;
		}
	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();

		Map<Class<? extends IOBIEThing>, List<EntityAnnotation>> groupedBy = new HashMap<>();
		for (EntityAnnotation entity : state.getCurrentPrediction().getEntityAnnotations()) {
			groupedBy.putIfAbsent(entity.rootClassType, new ArrayList<>());
			groupedBy.get(entity.rootClassType).add(entity);

		}
		for (Entry<Class<? extends IOBIEThing>, List<EntityAnnotation>> entities : groupedBy.entrySet()) {
			factors.addAll(addFactor(entities.getKey(), entities.getValue()));
		}
		return factors;
	}

	private List<Scope> addFactor(Class<? extends IOBIEThing> entityRootClassType, List<EntityAnnotation> list) {
		List<Scope> factors = new ArrayList<>();

		if (list.isEmpty())
			return factors;

		Map<Class<? extends IOBIEThing>, Map<String, Integer>> countBy = new HashMap<>();

		for (EntityAnnotation ann : list) {
			countBy.putIfAbsent(ann.getAnnotationInstance().getClass(), new HashMap<>());
			if (ann.getAnnotationInstance().getClass().isAnnotationPresent(DatatypeProperty.class)) {
				final String text = ((IDataType) ann.getAnnotationInstance()).getSemanticValue();
				countBy.get(ann.getAnnotationInstance().getClass()).put(text,
						countBy.get(ann.getAnnotationInstance().getClass()).getOrDefault(text, 0) + 1);
			}

		}

		for (Entry<Class<? extends IOBIEThing>, Map<String, Integer>> countsByClass : countBy.entrySet()) {

			final Set<Class<? extends IOBIEThing>> influencedVariables = new HashSet<>();
			influencedVariables.add(countsByClass.getKey());
			for (Entry<String, Integer> countsByText : countsByClass.getValue().entrySet()) {
				factors.add(new Scope(influencedVariables, entityRootClassType, this, countsByClass.getKey(),
						countsByText.getKey(), countsByText.getValue()));
			}
		}

		return factors;
	}

	@Override
	public void computeFactor(Factor<Scope> factor) {
		Vector featureVector = factor.getFeatureVector();
		featureVector.set(
				"TextForSlot_" + factor.getFactorScope().classType.getSimpleName() + "_Appears_==" + 1 + "_Time",
				factor.getFactorScope().numberOfSlot == 1);
		featureVector.set("Slot: " + factor.getFactorScope().classType.getSimpleName() + "_Appears_!=" + 1 + "_Time",
				factor.getFactorScope().numberOfSlot != 1);
		// featureVector.set("Slot: " +
		// factor.getFactorScope().classType.getSimpleName() + "_Appears_" + 3 +
		// "_Time",
		// factor.getFactorScope().numberOfSlot == 3);
		// featureVector.set("Slot: " +
		// factor.getFactorScope().classType.getSimpleName() + "_Appears_" + 4 +
		// "_Time",
		// factor.getFactorScope().numberOfSlot == 4);

		featureVector.set(
				"Slot: " + factor.getFactorScope().classType.getSimpleName() + "WithText_"
						+ factor.getFactorScope().text + "_Appears_" + 1 + "_Time",
				factor.getFactorScope().numberOfSlot == 1);

		// featureVector.set(
		// "Slot: " + factor.getFactorScope().classType.getSimpleName() +
		// "WithText_"
		// + factor.getFactorScope().text + "_Appears_" + 2 + "_Time",
		// factor.getFactorScope().numberOfSlot == 2);
		// featureVector.set("Slot: " +
		// factor.getFactorScope().classType.getSimpleName() + "WithText_"
		// + factor.getFactorScope().text + "_Appears_" + 3 + "_Time",
		// factor.getFactorScope().numberOfSlot == 3);
		// featureVector.set("Slot: " +
		// factor.getFactorScope().classType.getSimpleName() + "WithText_"
		// + factor.getFactorScope().text + "_Appears_" + 4 + "_Time",
		// factor.getFactorScope().numberOfSlot == 4);

	}

}
