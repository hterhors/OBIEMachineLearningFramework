package de.uni.bielefeld.sc.hterhors.psink.obie.ie.tools.baseline;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.evaluation.PRF1Container;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DirectInterface;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.ImplementationClass;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.OntologyModelContent;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.BigramInternalCorpus;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.explorer.utils.ExplorationUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.HighFrequencyUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.HighFrequencyUtils.FrequencyPair;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.OBIEClassFormatter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.TemplateAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;

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
					.map(e -> e.get()).collect(Collectors.toList());

			List<IOBIEThing> predictions = predictClassesByFrequency(doc);
			doc.getGoldAnnotation().getTemplateAnnotations()
					.forEach(s -> System.out.println(OBIEClassFormatter.format(s.get(), false)));
			System.out.println("____________________________");
			predictions.forEach(f -> System.out.println(OBIEClassFormatter.format(f, false)));

			final double precision = param.evaluator.precision(gold, predictions);
			final double recall = param.evaluator.recall(gold, predictions);
			final double f1 = param.evaluator.f1(gold, predictions);
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
	 * Try to project the gold data to an empty class template. All properties that
	 * can be found in the document can be automatically project.
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
	private List<IOBIEThing> predictClassesByFrequency(OBIEInstance instance) {

		List<IOBIEThing> predictions = new ArrayList<>();

		for (TemplateAnnotation goldAnnotation : instance.getGoldAnnotation().getTemplateAnnotations()) {

			IOBIEThing goldClass = (IOBIEThing) goldAnnotation.get();
			IOBIEThing predictionClass = null;
			try {
				predictionClass = goldClass.getClass().newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
				e.printStackTrace();
			}

			/*
			 * Adapt classType
			 */
			// if (goldClass.getClass() != null) {
			// if (instance.getNER().containsAnnotations(goldClass.getClass()))
			// {
			// predictionClass =
			// SamplerHelper.copyOntologyModelFields(goldClass.getClass().newInstance(),
			// predictionClass);
			// }
			// }
			if (goldClass.getClass().isAnnotationPresent(DatatypeProperty.class)) {
				predictionClass = projectDataTypeClass(instance, goldClass);
			} else {
				fillPropertiesRecursivly(instance, predictionClass);
			}

			/*
			 * Add only one prediction class.
			 */
			if (predictions.size() < MAX_PREDICTIONS_TO_ADD)
				predictions.add(predictionClass);

		}

		return predictions;
	}

	private IOBIEThing projectDataTypeClass(OBIEInstance instance, IOBIEThing goldModel) {
		/*
		 * If field is not a list we need to find only one entry.
		 */
		final Class<? extends IOBIEThing> fieldInterfaceType = goldModel.getClass().getAnnotation(DirectInterface.class)
				.get();
		final Class<? extends IOBIEThing> fieldClassType = fieldInterfaceType.getAnnotation(ImplementationClass.class)
				.get();
		IOBIEThing predictionModel = null;
		/*
		 * Search for data in the mention annotation data.
		 */
		try {
			if (ExplorationUtils.isAuxiliaryProperty(fieldInterfaceType)) {
				/*
				 * annotation data. We still check if the field should be filled anyway (without
				 * textual evidence). This makes sense on fields that are not dependent on text.
				 * For instance helper classes or grouping classes.
				 */

				predictionModel = fieldClassType.newInstance();
			} else {
				FrequencyPair fp = HighFrequencyUtils.determineMostFrequentClass(fieldInterfaceType, instance);

				if (fp.bestClass != null) {
					if (fp.dataTypeValue == null)
						predictionModel = fp.bestClass.newInstance();
					else
						predictionModel = fp.bestClass.getConstructor(String.class, String.class, String.class)
								.newInstance(null, fp.textMention, fp.dataTypeValue);
				}
			}
		} catch (IllegalArgumentException | IllegalAccessException | InstantiationException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
		}
		return predictionModel;
	}

	@SuppressWarnings("unchecked")
	private void fillPropertiesRecursivly(OBIEInstance instance, IOBIEThing predictionModel) {
		if (predictionModel == null)
			return;

		/*
		 * Add factors for object type properties.
		 */
		List<Field> fields = Arrays.stream(predictionModel.getClass().getDeclaredFields())
				.filter(f -> f.isAnnotationPresent(OntologyModelContent.class)).collect(Collectors.toList());

		for (Field field : fields) {
			field.setAccessible(true);

			if (field.isAnnotationPresent(RelationTypeCollection.class)) {
				List<IOBIEThing> elements = new ArrayList<>();
				/*
				 * Get values for that list.
				 */
				final Class<? extends IOBIEThing> fieldGenericInterfaceType = ((Class<? extends IOBIEThing>) ((ParameterizedType) field
						.getGenericType()).getActualTypeArguments()[0]);
				final Class<? extends IOBIEThing> fieldGenericClassType = fieldGenericInterfaceType
						.getAnnotation(ImplementationClass.class).get();

				/*
				 * If the mention annotation data contains evidence for that requested class.
				 */
				if (ExplorationUtils.isAuxiliaryProperty(fieldGenericInterfaceType)) {
					for (int i = 0; i < MAX_PREDICTIONS_TO_ADD; i++) {
						/*
						 * Add n auxiliary classes.
						 */
						try {
							elements.add(fieldGenericClassType.newInstance());
						} catch (InstantiationException | IllegalAccessException e) {
							e.printStackTrace();
						}
					}
				} else {
					/*
					 * Get n most frequent classes.
					 */

					List<IOBIEThing> bestNElements = HighFrequencyUtils
							.determineMostFrequentClasses(fieldGenericInterfaceType, instance, MAX_PREDICTIONS_TO_ADD)
							.stream().map(fp -> {
								try {

									if (fp.dataTypeValue == null)
										return fp.bestClass.newInstance();
									else
										return fp.bestClass.getConstructor(String.class, String.class, String.class)
												.newInstance(null, fp.textMention, fp.dataTypeValue);

								} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
										| InvocationTargetException | NoSuchMethodException | SecurityException e) {
									e.printStackTrace();
								}
								return null;
							}).collect(Collectors.toList());

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
					fillPropertiesRecursivly(instance, element);
				}
				try {
					/*
					 * If the list is not empty we set it.
					 */
					if (!elements.isEmpty()) {
						field.set(predictionModel, elements);
					}
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}

			} else {
				/*
				 * If field is not a list we need to find only one entry.
				 */
				final Class<? extends IOBIEThing> fieldInterfaceType = (Class<? extends IOBIEThing>) field.getType();
				final Class<? extends IOBIEThing> fieldClassType = fieldInterfaceType
						.getAnnotation(ImplementationClass.class).get();
				IOBIEThing property = null;
				/*
				 * Search for data in the mention annotation data.
				 */
				try {
					if (ExplorationUtils.isAuxiliaryProperty(fieldInterfaceType)) {
						/*
						 * annotation data. We still check if the field should be filled anyway (without
						 * textual evidence). This makes sense on fields that are not dependent on text.
						 * For instance helper classes or grouping classes.
						 */

						property = fieldClassType.newInstance();
					} else {
						FrequencyPair fp = HighFrequencyUtils.determineMostFrequentClass(fieldInterfaceType, instance);

						if (fp.bestClass != null) {
							if (fp.dataTypeValue == null)
								property = fp.bestClass.newInstance();
							else
								property = fp.bestClass.getConstructor(String.class, String.class, String.class)
										.newInstance(null, fp.dataTypeValue, fp.textMention);
						}
					}
					field.set(predictionModel, property);
				} catch (IllegalArgumentException | IllegalAccessException | InstantiationException
						| InvocationTargetException | NoSuchMethodException | SecurityException e) {
					e.printStackTrace();
				}
				/*
				 * We call this method with the new added class recursively.
				 */
				fillPropertiesRecursivly(instance, property);
			}
		}
	}

}
