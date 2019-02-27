package de.hterhors.obie.ml.tools.baseline;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import de.hterhors.obie.core.evaluation.PRF1;
import de.hterhors.obie.core.ontology.InvestigationRestriction;
import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.corpus.BigramInternalCorpus;
import de.hterhors.obie.ml.explorer.utils.ExplorationUtils;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.utils.HighFrequencyUtils;
import de.hterhors.obie.ml.utils.HighFrequencyUtils.ClassFrequencyPair;
import de.hterhors.obie.ml.utils.HighFrequencyUtils.IndividualFrequencyPair;
import de.hterhors.obie.ml.utils.OBIEClassFormatter;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.TemplateAnnotation;

/**
 * 
 * @author hterhors
 *
 * @param <T>
 * @date Oct 12, 2017
 */
public class HighFrequencyBaseline {

	private static final int MAX_PREDICTIONS_TO_ADD = 1;
	private final RunParameter param;

	public HighFrequencyBaseline(RunParameter param) {
		this.param = param;
	}

	public PRF1 run(BigramInternalCorpus corpus) {

		PRF1 mean = new PRF1();

		for (OBIEInstance doc : corpus.getInternalInstances()) {

//			System.out.println(doc.getName());

			List<IOBIEThing> gold = doc.getGoldAnnotation().getTemplateAnnotations().stream().map(e -> e.getThing())
					.collect(Collectors.toList());

			List<IOBIEThing> predictions = predictFillerByFrequency(doc);
			predictions.forEach(m -> setRestrictionRec(m, param.defaultTestInvestigationRestriction));

//			doc.getGoldAnnotation().getTemplateAnnotations()
//					.forEach(s -> System.out.println(OBIEClassFormatter.format(s.getThing(), false)));
//			System.out.println("____________________________");
//			predictions.forEach(f -> System.out.println(OBIEClassFormatter.format(f, false)));

			PRF1 score = param.evaluator.prf1(gold, predictions);

//			final double precision = score.getPrecision();
//			final double recall = score.getRecall();
//			final double f1 = score.getF1();
//
//			System.out.println("precision = " + precision);
//			System.out.println("recall = " + recall);
//			System.out.println("f1 = " + f1);
			mean.add(score);
//			System.out.println("");
//			System.out.println("");
//			System.out.println("");
		}
//		System.out.println("Most frequent baseline mean-P = " + mean.getPrecision());
//		System.out.println("Most frequent baseline mean-R = " + mean.getRecall());
//		System.out.println("Most frequent baseline mean-F1 = " + mean.getF1());
		return mean;
	}

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

	/**
	 * Try to project the gold data to an empty class template. All slots that can
	 * be found in the document can be automatically project.
	 * 
	 * @param type
	 * 
	 * @param instance
	 * @param data
	 * @return
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws NoSuchFieldException
	 */
	private List<IOBIEThing> predictFillerByFrequency(OBIEInstance instance) {

		List<IOBIEThing> predictions = new ArrayList<>();
		int randomNumberOfRootSlotTemplates = param.numberOfInitializedObjects.number(instance);// getRandomIndexBetween(1,
																								// 1);

		for (TemplateAnnotation goldAnnotation : instance.getGoldAnnotation().getTemplateAnnotations()) {
			IOBIEThing goldClass = (IOBIEThing) goldAnnotation.getThing();
			IOBIEThing predictionClass = null;
			try {

				Class<? extends IOBIEThing> searchClazz = instance.rootClassTypes.iterator().next();
				ClassFrequencyPair classFreqList = HighFrequencyUtils.getMostFrequentClass(searchClazz, instance);

				predictionClass = classFreqList.clazz == null
						? ReflectionUtils.getImplementationClass(searchClazz).newInstance()
						: classFreqList.clazz.newInstance();

				List<IndividualFrequencyPair> individualFreqList = HighFrequencyUtils.getMostFrequentIndividuals(
						ReflectionUtils.getDirectInterfaces(goldClass.getClass()), instance, 1);
				/**
				 * TODO: allow multiple main templates ???
				 */
				for (IndividualFrequencyPair individual : individualFreqList) {

					predictionClass = individual.belongingClazz
							.getConstructor(String.class, InvestigationRestriction.class, String.class)
							.newInstance(individual.individual.getURI(), null, individual.textMention);
					break;
				}

			} catch (Exception e) {
				e.printStackTrace();
			}

			fillSlotsRec(instance, predictionClass);

			/*
			 * Add only one prediction class.
			 */
			if (predictions.size() < randomNumberOfRootSlotTemplates)
				predictions.add(predictionClass);

		}

		return predictions;
	}

	@SuppressWarnings("unchecked")
	private void fillSlotsRec(OBIEInstance instance, IOBIEThing predictionModel) {
		if (predictionModel == null)
			return;

		/*
		 * Add factors for object type properties.
		 */
		final List<Field> fields = ReflectionUtils.getSlots(predictionModel.getClass(),
				param.defaultTestInvestigationRestriction);

		for (Field slot : fields) {

			if (ReflectionUtils.isAnnotationPresent(slot, RelationTypeCollection.class)) {

				final List<IOBIEThing> elements = new ArrayList<>();
				/*
				 * Get values for that list.
				 */
				final Class<? extends IOBIEThing> slotType = ((Class<? extends IOBIEThing>) ((ParameterizedType) slot
						.getGenericType()).getActualTypeArguments()[0]);

				if (ReflectionUtils.isAnnotationPresent(slot, DatatypeProperty.class)) {

					List<ClassFrequencyPair> cfps = HighFrequencyUtils.getMostFrequentClassesOrValue(slotType, instance,
							MAX_PREDICTIONS_TO_ADD);

					for (ClassFrequencyPair cfp : cfps) {

						try {
							if (cfp.clazz != null) {
								if (cfp.datatypeValue == null)
									elements.add(cfp.clazz.newInstance());
								else
									elements.add(cfp.clazz.getConstructor(String.class, String.class)
											.newInstance(cfp.textMention, cfp.datatypeValue));
							}
						} catch (Exception e) {
							e.printStackTrace();
						}

					}
				} else if (ExplorationUtils.isAuxiliary(slotType)) {

					/*
					 * If the mention annotation data contains evidence for that requested class.
					 */
					final Class<? extends IOBIEThing> slotGenericClassType = ReflectionUtils
							.getImplementationClass(slotType);
					for (int i = 0; i < MAX_PREDICTIONS_TO_ADD; i++) {
						/*
						 * Add n auxiliary classes.
						 */
						try {
							elements.add(slotGenericClassType.newInstance());
						} catch (InstantiationException | IllegalAccessException e) {
							e.printStackTrace();
						}
					}
				} else {
					/*
					 * Get n most frequent classes.
					 */

					List<IndividualFrequencyPair> individualFreqList = new ArrayList<>();

					individualFreqList.addAll(
							HighFrequencyUtils.getMostFrequentIndividuals(slotType, instance, MAX_PREDICTIONS_TO_ADD));

					for (Class<? extends IOBIEThing> slotFillerType : ReflectionUtils
							.getAssignableSubInterfaces(slotType)) {

						individualFreqList.addAll(HighFrequencyUtils.getMostFrequentIndividuals(slotFillerType,
								instance, MAX_PREDICTIONS_TO_ADD));
					}

					Collections.sort(individualFreqList);

					List<IOBIEThing> bestNElements = individualFreqList.stream().limit(MAX_PREDICTIONS_TO_ADD)
							.map(fp -> toIndividualClassInstance(fp)).collect(Collectors.toList());

					if (bestNElements != null)
						for (IOBIEThing nonNullValue : bestNElements) {
							if (nonNullValue != null) {
								elements.add(nonNullValue);
							}
						}
				}
				/*
				 * We call this method with the new added class recursively.
				 */
				for (IOBIEThing element : elements) {
					fillSlotsRec(instance, element);
				}
				try {
					/*
					 * If the list is not empty we set it.
					 */
					if (!elements.isEmpty()) {
						slot.set(predictionModel, elements);
					}
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
			} else {

				/*
				 * If field is not a list we need to find only one entry.
				 */
				final Class<? extends IOBIEThing> slotType = (Class<? extends IOBIEThing>) slot.getType();

				IOBIEThing property = null;
				/*
				 * Search for data in the mention annotation data.
				 */
				try {
					if (ReflectionUtils.isAnnotationPresent(slot, DatatypeProperty.class)) {

						List<ClassFrequencyPair> cfps = HighFrequencyUtils.getMostFrequentClassesOrValue(slotType,
								instance, 1);

						for (ClassFrequencyPair cfp : cfps) {

							try {
								if (cfp.clazz != null) {
									if (cfp.datatypeValue == null)
										property = cfp.clazz.newInstance();
									else
										property = cfp.clazz.getConstructor(String.class, String.class)
												.newInstance(cfp.textMention, cfp.datatypeValue);
								}
							} catch (Exception e) {
								e.printStackTrace();
							}

						}
					} else if (ExplorationUtils.isAuxiliary(slotType)) {
						final Class<? extends IOBIEThing> slotClassType = ReflectionUtils
								.getImplementationClass(slotType);
						/*
						 * annotation data. We still check if the field should be filled anyway (without
						 * textual evidence). This makes sense on fields that are not dependent on text.
						 * For instance helper classes or grouping classes.
						 */

						property = slotClassType.newInstance();
					} else {

						List<IndividualFrequencyPair> individualFreqList = new ArrayList<>();

						individualFreqList.addAll(HighFrequencyUtils.getMostFrequentIndividuals(slotType, instance,
								MAX_PREDICTIONS_TO_ADD));

						for (Class<? extends IOBIEThing> slotFillerType : ReflectionUtils
								.getAssignableSubInterfaces(slotType)) {

							individualFreqList.addAll(HighFrequencyUtils.getMostFrequentIndividuals(slotFillerType,
									instance, MAX_PREDICTIONS_TO_ADD));
						}

						Collections.sort(individualFreqList);

						if (!individualFreqList.isEmpty()) {
							property = toIndividualClassInstance(individualFreqList.get(0));
						}
					}

					slot.set(predictionModel, property);

				} catch (Exception e) {
					e.printStackTrace();
				}
				/*
				 * We call this method with the new added class recursively.
				 */
				fillSlotsRec(instance, property);
			}
		}
	}

	private IOBIEThing toIndividualClassInstance(IndividualFrequencyPair individual) {
		try {
			IOBIEThing property;
			if (individual.belongingClazz != null) {
				if (individual.individual == null) {
					property = individual.belongingClazz.newInstance();

				} else {
					/**
					 * TODO: pass individual instead of string!
					 */
					property = individual.belongingClazz
							.getConstructor(String.class, InvestigationRestriction.class, String.class)
							.newInstance((individual.individual.nameSpace + individual.individual.name), null,
									individual.textMention);
				}
			} else {
				property = null;
			}
			return property;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}
