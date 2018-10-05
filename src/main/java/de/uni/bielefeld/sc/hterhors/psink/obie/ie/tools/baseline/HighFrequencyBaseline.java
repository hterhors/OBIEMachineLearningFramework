package de.uni.bielefeld.sc.hterhors.psink.obie.ie.tools.baseline;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.evaluation.PRF1;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.evaluation.PRF1Container;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.BigramInternalCorpus;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation.evaluator.CartesianSearchEvaluator;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.explorer.utils.ExplorationUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.HighFrequencyUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.HighFrequencyUtils.ClassFrequencyPair;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.HighFrequencyUtils.IndividualFrequencyPair;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.OBIEClassFormatter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.ReflectionUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.TemplateAnnotation;

/**
 * 
 * @author hterhors
 *
 * @param <T>
 * @date Oct 12, 2017
 */
public class HighFrequencyBaseline {

	private static final int MAX_PREDICTIONS_TO_ADD = 1;
	private final OBIERunParameter param;

	public HighFrequencyBaseline(OBIERunParameter param) {
		this.param = param;
	}

	public PRF1Container run(BigramInternalCorpus corpus) {

		double meanPrecision = 0;
		double meanRecall = 0;
		double meanF1 = 0;

		for (OBIEInstance doc : corpus.getInternalInstances()) {

			System.out.println(doc.getName());

			List<IOBIEThing> gold = doc.getGoldAnnotation().getTemplateAnnotations().stream()
					.map(e -> e.getTemplateAnnotation()).collect(Collectors.toList());

			List<IOBIEThing> predictions = predictFillerByFrequency(doc);

			doc.getGoldAnnotation().getTemplateAnnotations()
					.forEach(s -> System.out.println(OBIEClassFormatter.format(s.getTemplateAnnotation(), false)));
			System.out.println("____________________________");
			predictions.forEach(f -> System.out.println(OBIEClassFormatter.format(f, false)));

			PRF1 score = param.evaluator.prf1(gold, predictions);

			final double precision = score.getPrecision();
			final double recall = score.getRecall();
			final double f1 = score.getF1();

			System.out.println("precision = " + precision);
			System.out.println("recall = " + recall);
			System.out.println("f1 = " + f1);
			meanPrecision += precision;
			meanRecall += recall;
			meanF1 += f1;
			System.out.println("");
			System.out.println("");
			System.out.println("");
		}
		meanPrecision /= corpus.getInternalInstances().size();
		meanRecall /= corpus.getInternalInstances().size();
		meanF1 /= corpus.getInternalInstances().size();
		System.out.println("Most frequent baseline mean-P = " + meanPrecision);
		System.out.println("Most frequent baseline mean-R = " + meanRecall);
		System.out.println("Most frequent baseline mean-F1 = " + meanF1);
		return new PRF1Container(meanPrecision, meanRecall, meanF1);
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

		for (TemplateAnnotation goldAnnotation : instance.getGoldAnnotation().getTemplateAnnotations()) {

			IOBIEThing goldClass = (IOBIEThing) goldAnnotation.getTemplateAnnotation();
			IOBIEThing predictionClass = null;
			try {
				predictionClass = goldClass.getClass().newInstance();

				List<IndividualFrequencyPair> individualFreqList = HighFrequencyUtils.getMostFrequentIndividuals(
						ReflectionUtils.getDirectInterfaces(goldClass.getClass()), instance, 1);
				/**
				 * TODO: allow multiple main templates ???
				 */
				for (IndividualFrequencyPair individual : individualFreqList) {

					predictionClass = individual.belongingClazz.getConstructor(String.class, String.class, String.class)
							.newInstance((individual.individual.nameSpace + individual.individual.name), null,
									individual.textMention);
					break;
				}

			} catch (Exception e) {
				e.printStackTrace();
			}

			fillSlotsRec(instance, predictionClass);

			/*
			 * Add only one prediction class.
			 */
			if (predictions.size() < MAX_PREDICTIONS_TO_ADD)
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
		final List<Field> fields = ReflectionUtils.getDeclaredOntologyFields(predictionModel.getClass());

		for (Field slot : fields) {

			if (slot.isAnnotationPresent(RelationTypeCollection.class)) {

				final List<IOBIEThing> elements = new ArrayList<>();
				/*
				 * Get values for that list.
				 */
				final Class<? extends IOBIEThing> slotType = ((Class<? extends IOBIEThing>) ((ParameterizedType) slot
						.getGenericType()).getActualTypeArguments()[0]);

				if (ReflectionUtils.isAnnotationPresent(slot, DatatypeProperty.class) ) {

					List<ClassFrequencyPair> cfps = HighFrequencyUtils.getMostFrequentClasses(slotType, instance,
							MAX_PREDICTIONS_TO_ADD);

					for (ClassFrequencyPair cfp : cfps) {

						try {
							if (cfp.clazz != null) {
								if (cfp.datatypeValue == null)
									elements.add(cfp.clazz.newInstance());
								else
									elements.add(cfp.clazz.getConstructor(String.class, String.class, String.class)
											.newInstance(null, cfp.textMention, cfp.datatypeValue));
							}
						} catch (Exception e) {
							e.printStackTrace();
						}

					}
				} else if (ExplorationUtils.isAuxiliaryProperty(slotType)) {

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
					if (ReflectionUtils.isAnnotationPresent(slot, DatatypeProperty.class) ) {

						List<ClassFrequencyPair> cfps = HighFrequencyUtils.getMostFrequentClasses(slotType, instance,
								1);

						for (ClassFrequencyPair cfp : cfps) {

							try {
								if (cfp.clazz != null) {
									if (cfp.datatypeValue == null)
										property = cfp.clazz.newInstance();
									else
										property = cfp.clazz.getConstructor(String.class, String.class, String.class)
												.newInstance(null, cfp.textMention, cfp.datatypeValue);
								}
							} catch (Exception e) {
								e.printStackTrace();
							}

						}
					} else if (ExplorationUtils.isAuxiliaryProperty(slotType)) {
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
					property = individual.belongingClazz.getConstructor(String.class, String.class, String.class)
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
