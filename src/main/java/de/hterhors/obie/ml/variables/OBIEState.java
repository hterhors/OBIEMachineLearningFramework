package de.hterhors.obie.ml.variables;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.jena.util.OneToManyMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.InvestigationRestriction;
import de.hterhors.obie.core.ontology.InvestigationRestriction.RestrictedField;
import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.run.param.EInstantiationType;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.run.utils.SlotTemplateInstantiationUtils;
import de.hterhors.obie.ml.scorer.InstanceCollection;
import de.hterhors.obie.ml.scorer.InstanceCollection.FeatureDataPoint;
import de.hterhors.obie.ml.utils.OBIEClassFormatter;
import de.hterhors.obie.ml.variables.OBIEInstance.EInstanceType;
import exceptions.MissingFactorException;
import factors.Factor;
import factors.FactorScope;
import variables.AbstractState;

public class OBIEState extends AbstractState<OBIEInstance> implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Logger log = LogManager.getFormatterLogger(OBIEState.class);

	private static final DecimalFormat SCORE_FORMAT = new DecimalFormat("0.00000000");

	private final InstanceTemplateAnnotations currentTemplateAnnotations;

	private final Map<Class<? extends IOBIEThing>, Set<IOBIEThing>> preFilledObjectMap;

	private final RunParameter parameter;

	private final Set<IOBIEThing> preFilledUsedObjects;

	/**
	 * Get all pre filled template candidates based on the slot-type if any. If not
	 * it returns null
	 * 
	 * TODO: NOTE: that the set does not distinguishes between slots that have the
	 * same slot-type, not even for different parent classes!
	 * 
	 * @param baseClassType_interface the slot type
	 * @return null if there is no data else the pre filled templates (might be
	 *         empty)
	 */
	public Set<IOBIEThing> getPreFilledTemplates(Class<? extends IOBIEThing> baseClassType_interface) {
		return preFilledObjectMap.get(baseClassType_interface);
	}

	public void addUsedPreFilledTemplate(IOBIEThing thing) {
		preFilledUsedObjects.add(thing);
	}

	private void removeUsedPreFilledObject(IOBIEThing thing) {
		preFilledUsedObjects.remove(thing);
	}

	public boolean preFilledObjectWasAlreadyUsed(IOBIEThing thing) {
		return preFilledUsedObjects.contains(thing);
	}

	public Set<IOBIEThing> getLeftOvers(Class<? extends IOBIEThing> baseClassType_interface) {
		Set<IOBIEThing> leftOvers = new HashSet<>(getPreFilledTemplates(baseClassType_interface));
		leftOvers.removeAll(preFilledUsedObjects);
		return leftOvers;
	}

	public void removeRecUsedPreFilledTemplate(IOBIEThing thing) {

		if (thing == null)
			return;

		removeUsedPreFilledObject(thing);

		/*
		 * remove recursive
		 */
		ReflectionUtils.getNonDatatypeSlots(thing.getClass(), thing.getInvestigationRestriction()).forEach(field -> {
			try {
				if (ReflectionUtils.isAnnotationPresent(field, RelationTypeCollection.class)) {
					for (IOBIEThing element : (List<IOBIEThing>) field.get(thing)) {
						removeRecUsedPreFilledTemplate(element);
					}
				} else {
					removeRecUsedPreFilledTemplate((IOBIEThing) field.get(thing));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

	}

	/**
	 * Clone constructor.
	 * 
	 * @param state
	 */
	public OBIEState(OBIEState state) {
		super(state);
		this.currentTemplateAnnotations = new InstanceTemplateAnnotations(state.currentTemplateAnnotations);
		this.preFilledObjectMap = state.preFilledObjectMap;
		this.parameter = state.parameter;
		this.preFilledUsedObjects = new HashSet<>(state.preFilledUsedObjects);
	}

	/**
	 * Initialize constructor
	 * 
	 * @param instance
	 * @param parameter
	 */
	public OBIEState(OBIEInstance instance, RunParameter parameter) {
		super(instance);

		this.parameter = parameter;
		this.preFilledUsedObjects = new HashSet<>();
		this.currentTemplateAnnotations = new InstanceTemplateAnnotations();
		this.preFilledObjectMap = new HashMap<>();

		final InvestigationRestriction r = getDefaultInvestigationRestriction();

		for (IETmplateAnnotation iobieThing : instance.getGoldAnnotation().getAnnotations()) {
			setRestrictionRec(iobieThing.getThing(), r);
		}

		if (parameter.initializer == EInstantiationType.SPECIFIED) {

			for (Entry<Class<? extends IOBIEThing>, List<IOBIEThing>> inits : parameter.initializationObjects
					.entrySet()) {
				for (IOBIEThing iobieThing : inits.getValue()) {
					this.currentTemplateAnnotations.addAnnotation(new IETmplateAnnotation(inits.getKey(), iobieThing));
				}
			}

		} else {

			int numOfEntities = parameter.numberOfInitializedObjects.number(instance);

			if (parameter.exploreExistingTemplates) {
				throw new NotImplementedException(
						"exploreExistingTemplates is not yet implemented and needs to be defined through parameter!");
//				addExistingTemplatesForExploration(instance);
			}

			for (int i = 0; i < numOfEntities; i++) {
				for (Class<? extends IOBIEThing> searchType : parameter.rootSearchTypes) {
					for (IOBIEThing initInstance : getInitializingObject(instance, searchType, parameter.initializer)) {

						setRestrictionRec(initInstance, r);

						this.currentTemplateAnnotations
								.addAnnotation(new IETmplateAnnotation(searchType, initInstance));
					}
				}
			}

		}
	}

//		/**
//		 * TODO: through parameter to get info
//		 */
//	private void addExistingTemplatesForExploration(InternalInstance instance) {
//		instance.getGoldAnnotation().getEntityAnnotations().stream().forEach(g -> {
//			/**
//			 * TODO: If investigation restriction is active filter by that.
//			 */
//			if (g.rootClassType == IExperimentalGroup.class) {
//				// this.preFilledObjectMap.putIfAbsent(IOrganismModel.class,
//				// new HashSet<>());
//
//				// this.preFilledObjectMap.putIfAbsent(IInjury.class,
//				// new
//				// HashSet<>());
//
//				// this.preFilledObjectMap.putIfAbsent(ITreatment.class,
//				// new
//				// HashSet<>());
//
//				this.preFilledObjectMap.putIfAbsent(IGroupName.class, new HashSet<>());
//				//
//				// this.preFilledObjectMap.get(IOrganismModel.class).addAll(SCIOCorpusProvider
//				// .extractOrganismModelsFromExperimentalGroup((IExperimentalGroup)
//				// g.getOBIEAnnotation()));
//				//
//				// this.preFilledObjectMap.get(IInjury.class).addAll(SCIOCorpusProvider
//				// .extractInjuryFromExperimentalGroup((IExperimentalGroup)
//				// g.getOBIEAnnotation()));
//				//
//				// this.preFilledObjectMap.get(ITreatment.class).addAll(SCIOCorpusProvider
//				// .extractTreatmentsFromExperimentalGroup((IExperimentalGroup)
//				// g.getOBIEAnnotation()));
//
//				for (IOBIEThing thing : SCIOCorpusDataExtractor
//						.extractGroupNamesFromExperimentalGroup((IExperimentalGroup) g.getAnnotationInstance())) {
//					this.preFilledObjectMap.get(IGroupName.class).add(thing);
//				}
//
//			}
//
//		});
//	}

	/**
	 * Adds investigationRestriction to all slot values of the parent value.
	 * 
	 * @param thing
	 * @param r
	 */
	@SuppressWarnings("unchecked")
	private void setRestrictionRec(IOBIEThing thing, InvestigationRestriction r) {

		if (thing == null)
			return;

		try {

			if (ReflectionUtils.isAnnotationPresent(thing.getClass(), DatatypeProperty.class))
				return;

			thing.setInvestigationRestriction(r);

			for (Field slot : ReflectionUtils.getNonDatatypeSlots(thing.getClass(), r)) {

				if (ReflectionUtils.isAnnotationPresent(slot, RelationTypeCollection.class)) {

					for (IOBIEThing sv : (List<IOBIEThing>) slot.get(thing)) {
						setRestrictionRec(sv, r);
					}
				} else {
					setRestrictionRec((IOBIEThing) slot.get(thing), r);
				}

			}

		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private Set<IOBIEThing> getInitializingObject(OBIEInstance instance, Class<? extends IOBIEThing> searchType,
			EInstantiationType initializer) {

		Set<IOBIEThing> set = new HashSet<>();

		if (searchType.isInterface()) {
			searchType = ReflectionUtils.getImplementationClass(searchType);
		} else {
			log.warn("Initialization type is supposed to be an interface, but its not: " + searchType);
		}

		/**
		 * TODO: What todo with data type properties and RANDOM/ WRONG etc.?
		 */
		switch (initializer) {
		case EMPTY:
			if (ReflectionUtils.isAnnotationPresent(searchType, DatatypeProperty.class))
				break;

			set.add(SlotTemplateInstantiationUtils.getEmptyInstance(searchType));
			break;
		/**
		 * TODO: Check if invest restriction is correct here!
		 */
		case RANDOM:
			set.add(SlotTemplateInstantiationUtils.getFullRandomInstance(instance, searchType,
					getDefaultInvestigationRestriction()));
			break;
		/**
		 * TODO: Check if invest restriction is correct here!
		 */
		case WRONG:
			set.add(SlotTemplateInstantiationUtils.getFullWrong(searchType, getDefaultInvestigationRestriction()));
			break;
		case NONE:
			break;
		case FULL_CORRECT:
			set.addAll(SlotTemplateInstantiationUtils.getFullCorrect(instance.getGoldAnnotation()));
			break;

		default:
			throw new NotImplementedException("This initializer is not yet implemented: " + initializer);
		}
		return set;

	}

	public InstanceTemplateAnnotations getCurrentIETemplateAnnotations() {
		return currentTemplateAnnotations;
	}

	public FeatureDataPoint toTrainingPoint(InstanceCollection data, boolean training) {

		final Map<String, Double> features = new HashMap<>();
		try {
			for (Factor<? extends FactorScope> factor : getFactorGraph().getFactors()) {
				for (Entry<String, Double> f : factor.getFeatureVector().getFeatures().entrySet()) {
					features.putIfAbsent(f.getKey(), f.getValue());
					// features.put(f.getKey(),
					// features.getOrDefault(f.getKey(), 0d) + f.getValue());
				}
			}
		} catch (MissingFactorException e) {
			e.printStackTrace();
		}
		return new FeatureDataPoint(data, features, getObjectiveScore(), training);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((currentTemplateAnnotations == null) ? 0 : currentTemplateAnnotations.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		OBIEState other = (OBIEState) obj;
		if (currentTemplateAnnotations == null) {
			if (other.currentTemplateAnnotations != null)
				return false;
		} else if (!currentTemplateAnnotations.equals(other.currentTemplateAnnotations))
			return false;
		return true;
	}

	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("#OfAnnotations:");
		builder.append(currentTemplateAnnotations.getAnnotations().size());
		builder.append(" [");
		builder.append(SCORE_FORMAT.format(modelScore));
		builder.append("]: ");
		builder.append(" [");
		builder.append(SCORE_FORMAT.format(objectiveScore));
		builder.append("]: ");
		for (IETmplateAnnotation e : currentTemplateAnnotations.getAnnotations()) {
			builder.append("\n\t");
			builder.append(OBIEClassFormatter.format(e.getThing(), true));
			builder.append("\n");
		}
		return builder.toString();
	}

	static public final Map<OBIEInstance, InvestigationRestriction> dropOutInstanceCache = new HashMap<>();

	static private final Random r = new Random(new Random(100L).nextLong());

	public InvestigationRestriction getDefaultInvestigationRestriction() {

		InvestigationRestriction inv;
		if (instance.getInstanceType() == EInstanceType.TRAIN) {

			boolean isDropOutActive = false;
			if (isDropOutActive) {

				if (dropOutInstanceCache.containsKey(this.instance)) {
					return dropOutInstanceCache.get(this.instance);
				}

				List<RestrictedField> allFields = InvestigationRestriction
						.getMainSingleFields(parameter.rootSearchTypes.iterator().next());

				Collections.shuffle(allFields, new Random(r.nextLong()));
//			allFields.size() - 1

//				Set<RestrictedField> fields = new HashSet<>(allFields.subList(0, 1));
				Set<RestrictedField> fields = new HashSet<>(
						allFields.subList(0, new Random(r.nextLong()).nextInt(allFields.size())));

				inv = new InvestigationRestriction(fields, true);
				dropOutInstanceCache.put(this.instance, inv);
			} else {
				inv = parameter.defaultTrainInvestigationRestriction;

			}

		} else {
			inv = parameter.defaultTestInvestigationRestriction;
		}
		return inv;
	}

}
