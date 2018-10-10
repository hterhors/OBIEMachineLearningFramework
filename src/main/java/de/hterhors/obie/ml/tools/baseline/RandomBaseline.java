package de.hterhors.obie.ml.tools.baseline;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.commons.collections.set.SynchronizedSet;

import de.hterhors.obie.core.evaluation.PRF1;
import de.hterhors.obie.core.evaluation.PRF1Container;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.corpus.BigramInternalCorpus;
import de.hterhors.obie.ml.explorer.utils.ExplorationUtils;
import de.hterhors.obie.ml.run.param.OBIERunParameter;
import de.hterhors.obie.ml.utils.HighFrequencyUtils;
import de.hterhors.obie.ml.utils.HighFrequencyUtils.ClassFrequencyPair;
import de.hterhors.obie.ml.utils.HighFrequencyUtils.IndividualFrequencyPair;
import de.hterhors.obie.ml.utils.OBIEClassFormatter;
import de.hterhors.obie.ml.utils.ReflectionUtils;
import de.hterhors.obie.ml.variables.OBIEInstance;

/**
 * Performs a random assignment to all slots guided by the parameter settings
 * for exploration strategies.
 * 
 * @author hterhors
 *
 */
public class RandomBaseline {

	private final OBIERunParameter param;

	private final Random random;

	public RandomBaseline(OBIERunParameter param, final long randomSeed) {
		this.param = param;
		this.random = new Random(randomSeed);
	}

	public PRF1Container run(BigramInternalCorpus corpus) {

		double meanPrecision = 0;
		double meanRecall = 0;
		double meanF1 = 0;

		for (OBIEInstance doc : corpus.getInternalInstances()) {

			System.out.println("_____________" + doc.getName() + "_______________");

			List<IOBIEThing> gold = doc.getGoldAnnotation().getTemplateAnnotations().stream()
					.map(e -> e.getTemplateAnnotation()).collect(Collectors.toList());
			List<IOBIEThing> predictions = predictFillerByRandom(doc);

			System.out.println("___________GOLD___________");
			doc.getGoldAnnotation().getTemplateAnnotations()
					.forEach(s -> System.out.println(OBIEClassFormatter.format(s.getTemplateAnnotation(), false)));
			System.out.println("___________RANDOM___________");
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

	private List<IOBIEThing> predictFillerByRandom(OBIEInstance instance) {

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

					predictionClass = individual.belongingClazz.getConstructor(String.class, String.class, String.class)
							.newInstance((individual.individual.nameSpace + individual.individual.name), null,
									individual.textMention);
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
		final List<Field> fields = ReflectionUtils.getAccessibleOntologyFields(predictionModel.getClass());

		for (Field slot : fields) {

			if (slot.isAnnotationPresent(RelationTypeCollection.class)) {

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