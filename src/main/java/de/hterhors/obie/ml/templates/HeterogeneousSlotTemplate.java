package de.hterhors.obie.ml.templates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.interfaces.IDatatype;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.run.param.OBIERunParameter;
import de.hterhors.obie.ml.templates.HeterogeneousSlotTemplate.Scope;
import de.hterhors.obie.ml.utils.ReflectionUtils;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import factors.Factor;
import factors.FactorScope;
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

	class Scope extends FactorScope {

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

		public Scope(Class<? extends IOBIEThing> entityRootClassType, AbstractOBIETemplate<?> template,
				Class<? extends IOBIEThing> classType, String text, int numberOfSlot) {
			super(template, entityRootClassType, classType, text, numberOfSlot);
			this.classType = classType;
			this.text = text;
			this.numberOfSlot = numberOfSlot;
		}
	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();

		Map<Class<? extends IOBIEThing>, List<TemplateAnnotation>> groupedBy = new HashMap<>();
		for (TemplateAnnotation entity : state.getCurrentPrediction().getTemplateAnnotations()) {
			groupedBy.putIfAbsent(entity.rootClassType, new ArrayList<>());
			groupedBy.get(entity.rootClassType).add(entity);

		}
		for (Entry<Class<? extends IOBIEThing>, List<TemplateAnnotation>> entities : groupedBy.entrySet()) {
			factors.addAll(addFactor(entities.getKey(), entities.getValue()));
		}
		return factors;
	}

	private List<Scope> addFactor(Class<? extends IOBIEThing> entityRootClassType, List<TemplateAnnotation> list) {
		List<Scope> factors = new ArrayList<>();

		if (list.isEmpty())
			return factors;

		Map<Class<? extends IOBIEThing>, Map<String, Integer>> countBy = new HashMap<>();

		for (TemplateAnnotation ann : list) {
			countBy.putIfAbsent(ann.getTemplateAnnotation().getClass(), new HashMap<>());
			if (ReflectionUtils.isAnnotationPresent(ann.getTemplateAnnotation().getClass(), DatatypeProperty.class)) {
				final String text = ((IDatatype) ann.getTemplateAnnotation()).getSemanticValue();
				countBy.get(ann.getTemplateAnnotation().getClass()).put(text,
						countBy.get(ann.getTemplateAnnotation().getClass()).getOrDefault(text, 0) + 1);
			}

		}

		for (Entry<Class<? extends IOBIEThing>, Map<String, Integer>> countsByClass : countBy.entrySet()) {

			final Set<Class<? extends IOBIEThing>> influencedVariables = new HashSet<>();
			influencedVariables.add(countsByClass.getKey());
			for (Entry<String, Integer> countsByText : countsByClass.getValue().entrySet()) {
				factors.add(new Scope(entityRootClassType, this, countsByClass.getKey(), countsByText.getKey(),
						countsByText.getValue()));
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
