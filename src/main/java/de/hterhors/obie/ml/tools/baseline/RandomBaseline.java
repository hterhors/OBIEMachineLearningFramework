package de.hterhors.obie.ml.tools.baseline;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import de.hterhors.obie.core.evaluation.PRF1;
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

/**
 * Performs a random assignment to all slots guided by the parameter settings
 * for exploration strategies.
 * 
 * @author hterhors
 *
 */
public class RandomBaseline {

	private final RunParameter param;

	private final Random random;

	public RandomBaseline(RunParameter param, final long randomSeed) {
		this.param = param;
		this.random = new Random(randomSeed);
	}

	public PRF1 run(BigramInternalCorpus corpus) {

		PRF1 mean = new PRF1();

		for (OBIEInstance doc : corpus.getInternalInstances()) {

			System.out.println("_____________" + doc.getName() + "_______________");

			List<IOBIEThing> gold = doc.getGoldAnnotation().getTemplateAnnotations().stream().map(e -> e.getThing())
					.collect(Collectors.toList());
			List<IOBIEThing> predictions = predictFillerByRandom(doc);

			System.out.println("___________GOLD___________");
			doc.getGoldAnnotation().getTemplateAnnotations()
					.forEach(s -> System.out.println(OBIEClassFormatter.format(s.getThing(), false)));
			System.out.println("___________RANDOM___________");
			predictions.forEach(f -> System.out.println(OBIEClassFormatter.format(f, false)));

			PRF1 score = param.evaluator.prf1(gold, predictions);

			final double precision = score.getPrecision();
			final double recall = score.getRecall();
			final double f1 = score.getF1();

			System.out.println("precision = " + precision);
			System.out.println("recall = " + recall);
			System.out.println("f1 = " + f1);
			mean.add(score);
			System.out.println("");
			System.out.println("");
			System.out.println("");
		}

		System.out.println("Random baseline mean-P = " + mean.getPrecision());
		System.out.println("Random baseline mean-R = " + mean.getRecall());
		System.out.println("Random baseline mean-F1 = " + mean.getF1());

		return mean;

	}

	public List<IOBIEThing> predictFillerByRandom(OBIEInstance instance) {

		List<IOBIEThing> predictions = new ArrayList<>();
		int randomNumberOfRootSlotTemplates = getRandomIndexBetween(1, 1);

		for (int i = 0; i < randomNumberOfRootSlotTemplates; i++) {
			try {

				Class<? extends IOBIEThing> randomClazz = ReflectionUtils
						.getImplementationClass(getRandomFromCollection(instance.rootClassTypes));

				IOBIEThing predictionClass = null;
				predictionClass = randomClazz.newInstance();

				List<IndividualFrequencyPair> individualFreqList = HighFrequencyUtils
						.getMostFrequentIndividuals(ReflectionUtils.getDirectInterfaces(randomClazz), instance, 1);
				/**
				 * TODO: allow multiple main templates ???
				 */
				for (IndividualFrequencyPair individual : individualFreqList) {

					predictionClass = individual.belongingClazz.getConstructor(String.class, String.class).newInstance(
							(individual.individual.nameSpace + individual.individual.name), individual.textMention);
					break;
				}

				fillSlotsRec(instance, predictionClass);

				/*
				 * Add only one prediction class.
				 */
				predictions.add(predictionClass);
			} catch (Exception e) {
				e.printStackTrace();
			}
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
		final List<Field> fields = ReflectionUtils.getSlots(predictionModel.getClass());

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
							Integer.MAX_VALUE);

					final int numOfElements = getRandomIndexBetween(0,
							Math.min(param.maxNumberOfDataTypeElements, cfps.size()));

					for (int i = 0; i < numOfElements; i++) {

						ClassFrequencyPair cfp = cfps.get(getRandomIndexBetween(0, cfps.size()));

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

					for (int i = 0; i < getRandomIndexBetween(1, param.maxNumberOfEntityElements); i++) {
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
							HighFrequencyUtils.getMostFrequentIndividuals(slotType, instance, Integer.MAX_VALUE));

					for (Class<? extends IOBIEThing> slotFillerType : ReflectionUtils
							.getAssignableSubInterfaces(slotType)) {

						individualFreqList.addAll(HighFrequencyUtils.getMostFrequentIndividuals(slotFillerType,
								instance, Integer.MAX_VALUE));
					}

					final int numOfElements = getRandomIndexBetween(0,
							Math.min(param.maxNumberOfEntityElements, individualFreqList.size()));

					if (!individualFreqList.isEmpty()) {
						List<IOBIEThing> randomNElements = new ArrayList<>();
						for (int i = 0; i < numOfElements; i++) {
							randomNElements.add(toIndividualClassInstance(getRandomFromCollection(individualFreqList)));
						}

						if (randomNElements != null)
							for (IOBIEThing nonNullValue : randomNElements) {
								if (nonNullValue != null) {
									elements.add(nonNullValue);
								}
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
								instance, Integer.MAX_VALUE);

						if (!cfps.isEmpty()) {
							ClassFrequencyPair cfp = getRandomFromCollection(cfps);

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

						individualFreqList.addAll(
								HighFrequencyUtils.getMostFrequentIndividuals(slotType, instance, Integer.MAX_VALUE));

						for (Class<? extends IOBIEThing> slotFillerType : ReflectionUtils
								.getAssignableSubInterfaces(slotType)) {

							individualFreqList.addAll(HighFrequencyUtils.getMostFrequentIndividuals(slotFillerType,
									instance, Integer.MAX_VALUE));
						}

						if (!individualFreqList.isEmpty())
							property = toIndividualClassInstance(getRandomFromCollection(individualFreqList));

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
					property = individual.belongingClazz.getConstructor(String.class, String.class).newInstance(
							(individual.individual.nameSpace + individual.individual.name), individual.textMention);
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

	private <B> B getRandomFromCollection(Collection<B> collection) {

		final int rndIndex = getRandomIndexBetween(0, collection.size());

		final Iterator<B> it = collection.iterator();

		if (rndIndex > 0) {
			for (int j = 0; j < rndIndex - 1; j++) {
				it.next();
			}
		}
		return it.next();
	}

	private int getRandomIndexBetween(final int x, final int y) {
		if (x == y)
			return x;

		return x + random.nextInt(y - x);
	}
}
