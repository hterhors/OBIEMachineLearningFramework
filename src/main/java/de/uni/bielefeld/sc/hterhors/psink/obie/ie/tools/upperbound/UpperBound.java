package de.uni.bielefeld.sc.hterhors.psink.obie.ie.tools.upperbound;

import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.evaluation.PRF1;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.OntologyModelContent;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IDataType;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.BigramInternalCorpus;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.OBIEClassFormatter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.TemplateAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.NERLClassAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.NamedEntityLinkingAnnotations;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;

/**
 * Calculates the maximum recall for a specific dataset. The recall is
 * calculated by taking the gold annotations for a document and project all
 * property values to the findings of the candidate retrieval.
 * 
 * If all properties in gold annotation can be found in the set of annotations
 * that are possible by the candidate retrieval we assume, that the state can be
 * reached.
 * 
 * e.g.
 * 
 * @formatter:off Gold:
 * 
 *                OrganismModel -> Ratmodel Gender -> Male AgeCategory -> Adult
 *                Weight -> 200g
 * 
 *                Set of annotations given by candidate retrieval component:
 * 
 *                RatModel: [Evidence in text] Female: [Evidence in text] Male:
 *                [No Evidence] AgeCategory: [Evidence in text] Weight [200g,
 *                1g, 100-200g]
 * 
 * @formatter:on
 * 
 * 				In this example, annotations are found in the text for all
 *               annotations in gold except for Male.
 * 
 * 
 * 
 * @author hterhors
 *
 *         Apr 12, 2017
 * @param <T>
 */
public class UpperBound {

	private static final int MAX_CARDINALITY = 100;

	final private OBIERunParameter parameter;

	public UpperBound(OBIERunParameter parameter, BigramInternalCorpus corpus) throws FileNotFoundException {

		/**
		 * Read test data
		 */
		List<OBIEInstance> documents = corpus.getInternalInstances();

		this.parameter = parameter;
		PRF1 upperBound = new PRF1();
		for (OBIEInstance doc : documents) {

			System.out.println(doc.getName());

			List<IOBIEThing> gold = doc.getGoldAnnotation().getTemplateAnnotations().stream()
					.map(e -> (IOBIEThing) e.get()).collect(Collectors.toList());

			List<IOBIEThing> maxRecallPredictions = getRecallPredictionsForType(doc);

			final PRF1 s = parameter.evaluator.prf1(gold, maxRecallPredictions);
			System.out.println("score = " + s);

			for (Class<? extends IOBIEThing> clazz : doc.getNamedEntityLinkingAnnotations().getAvailableClassTypes()) {
				System.out.println(doc.getNamedEntityLinkingAnnotations().getClassAnnotations(clazz));
			}

			upperBound.add(s);
		}
		System.out.println("UpperBound = " + upperBound);

	}

	/**
	 * 
	 * @param type
	 * @param doc
	 * @param mentionData
	 * @return
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws IllegalArgumentException
	 * @throws SecurityException
	 * @throws NoSuchFieldException
	 */
	private List<IOBIEThing> getRecallPredictionsForType(OBIEInstance doc) {

		List<IOBIEThing> maxRecallPredictions = null;
		maxRecallPredictions = projectGoldToPredictions(doc);
		System.out.println("GoldAnnotations:");
		doc.getGoldAnnotation().getTemplateAnnotations()
				.forEach(s -> System.out.println(OBIEClassFormatter.format(s.get(), false)));
		System.out.println("____________________________");
		System.out.println("Predicted:");
		maxRecallPredictions.forEach(f -> System.out.println(OBIEClassFormatter.format(f, false)));

		return maxRecallPredictions;
	}

	/**
	 * Try to project the gold data to an empty class template. All properties that
	 * can be found in the document can be automatically project.
	 * 
	 * @param type
	 * 
	 * @param gold
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
	private List<IOBIEThing> projectGoldToPredictions(OBIEInstance goldInstance) {

		List<IOBIEThing> predictions = new ArrayList<>();

		List<TemplateAnnotation> goldAn = new ArrayList<>(goldInstance.getGoldAnnotation().getTemplateAnnotations());
		Collections.shuffle(goldAn);

		int counter = 0;
		boolean maxIsReached;
		for (TemplateAnnotation goldAnnotation : goldAn) {

			maxIsReached = ++counter == MAX_CARDINALITY;
			try {
				IOBIEThing goldModel = (IOBIEThing) goldAnnotation.get();
				IOBIEThing predictionModel = null;

				if (goldModel.getClass().isAnnotationPresent(DatatypeProperty.class)) {
					predictionModel = projectDataTypeClass(goldInstance, goldModel, predictionModel);
				} else {
					predictionModel = goldModel.getClass().newInstance();
					addClassesRecursivly(goldModel, predictionModel, goldInstance.getNamedEntityLinkingAnnotations());
				}

				if (predictionModel != null)
					predictions.add(predictionModel);
			} catch (InstantiationException | IllegalAccessException e) {
				e.printStackTrace();
			}
			if (maxIsReached)
				break;
		}

		return predictions;
	}

	private IOBIEThing projectDataTypeClass(OBIEInstance goldInstance, IOBIEThing goldModel, IOBIEThing predictionModel)
			throws InstantiationException, IllegalAccessException {
		if (goldInstance.getNamedEntityLinkingAnnotations().containsClassAnnotations(goldModel.getClass())) {

			NERLClassAnnotation value = null;

			for (NERLClassAnnotation mentionAnnotation : goldInstance.getNamedEntityLinkingAnnotations()
					.getClassAnnotations(goldModel.getClass())) {
				if (mentionAnnotation.getDTValueIfAnyElseTextMention()
						.equals(((IDataType) goldModel).getSemanticValue())) {
					value = mentionAnnotation;
					break;
				}
			}
			if (value != null) {
				try {
					predictionModel = value.classType.getDeclaredConstructor(String.class, String.class, String.class)
							.newInstance(null, value.text, value.getDTValueIfAnyElseTextMention());
				} catch (IllegalArgumentException | InvocationTargetException | NoSuchMethodException
						| SecurityException e) {
					e.printStackTrace();
				}
			}
		}
		return predictionModel;
	}

	@SuppressWarnings("unchecked")
	private void addClassesRecursivly(IOBIEThing goldModel, IOBIEThing predictionModel,
			NamedEntityLinkingAnnotations ner) {
		/*
		 * Add factors for object type properties.
		 */
		List<Field> fields = Arrays.stream(goldModel.getClass().getDeclaredFields())
				.filter(f -> f.isAnnotationPresent(OntologyModelContent.class)).collect(Collectors.toList());

		for (Field field : fields) {
			try {
				field.setAccessible(true);
				if (field.isAnnotationPresent(RelationTypeCollection.class)) {
					if (field.get(goldModel) != null) {
						List<IOBIEThing> values = new ArrayList<>();
						Field f = predictionModel.getClass().getDeclaredField(field.getName());
						f.setAccessible(true);

						/*
						 * Get values for that list.
						 */
						for (IOBIEThing thing : (List<IOBIEThing>) field.get(goldModel)) {
							/*
							 * If the mention annotation data contains evidence for that requested class.
							 */
							if (parameter.exploreClassesWithoutTextualEvidence.contains(thing.getClass())) {

								/*
								 * If there was no evidence in the text but the class is can be explored without
								 * evidence create n new one. HWere n is the number of objects in the gold list.
								 */
								IOBIEThing property = thing.getClass().newInstance();
								values.add(property);
								/*
								 * We call this method with the new added class recursively.
								 */
								addClassesRecursivly(thing, property, ner);

							} else if (ner.containsClassAnnotations(thing.getClass())) {
								/*
								 * If class is DataTypeProperty we need the exact value.
								 */
								if (thing.getClass().isAnnotationPresent(DatatypeProperty.class)) {
									/*
									 * Search for exact match... break on find.
									 */
									boolean found = false;
									for (NERLClassAnnotation mentionAnnotation : ner
											.getClassAnnotations(thing.getClass())) {
										if (mentionAnnotation.getDTValueIfAnyElseTextMention()
												.equals(((IDataType) thing).getSemanticValue())) {
											found = true;
											values.add((IOBIEThing) mentionAnnotation.classType
													.getDeclaredConstructor(String.class, String.class, String.class)
													.newInstance(null, mentionAnnotation.text,
															mentionAnnotation.getDTValueIfAnyElseTextMention()));
											break;
										}
									}
									if (!found) {
										System.out.println("WARN: Can not fill dt-class: " + field.getName() + ":"
												+ ((IDataType) thing).getSemanticValue());
									}
								} else {
									/*
									 * Else we need only the class mentioned anywhere.
									 */
									IOBIEThing property = thing.getClass().newInstance();
									values.add(property);
									/*
									 * We call this method with the new added class recursively.
									 */
									addClassesRecursivly(thing, property, ner);
								}

							} else {
								if (thing.getClass().isAnnotationPresent(DatatypeProperty.class)) {
									System.out.println("WARN: Can not fill class: " + thing.getClass().getSimpleName()
											+ ":" + ((IDataType) thing).getSemanticValue());
								} else {
									System.out.println("WARN: Can not fill class: " + thing.getClass().getSimpleName());
								}

							}
						}
						/*
						 * If the list is not empty we set it.
						 */
						if (!values.isEmpty()) {
							f.set(predictionModel, values);
						}
					} else {
						/*
						 * Empty list.
						 */
					}
				} else {
					/*
					 * If field is not a list we need to find only one entry.
					 */
					if (field.get(goldModel) != null) {
						final Class<? extends IOBIEThing> scioFieldType = (Class<? extends IOBIEThing>) field
								.get(goldModel).getClass();
						/*
						 * Search for data in the mention annotation data.
						 */
						if (parameter.exploreClassesWithoutTextualEvidence.contains(scioFieldType)) {

							/*
							 * If we do not find any evidence in the mention annotation data. We still check
							 * if the field should be filled anyway (without textual evidence). This makes
							 * sense on fields that are not dependent on text. For instance helper classes
							 * or grouping classes.
							 */
							Field f = predictionModel.getClass().getDeclaredField(field.getName());
							f.setAccessible(true);
							IOBIEThing property = (IOBIEThing) field.get(goldModel).getClass().newInstance();
							f.set(predictionModel, property);
							/*
							 * We call this method with the new added class recursively.
							 */
							addClassesRecursivly((IOBIEThing) field.get(goldModel), property, ner);
						} else if (ner.containsClassAnnotations(scioFieldType)) {
							/*
							 * If field is DataTypeProeprty we need an exact match.
							 */
							if (field.getType().isAnnotationPresent(DatatypeProperty.class)) {
								NERLClassAnnotation value = null;
								/*
								 * Search for exact match. Break on find.
								 */
								for (NERLClassAnnotation mentionAnnotation : ner.getClassAnnotations(scioFieldType)) {
									if (mentionAnnotation.getDTValueIfAnyElseTextMention()
											.equals(((IDataType) field.get(goldModel)).getSemanticValue())) {
										value = mentionAnnotation;
										break;
									}
								}
								/*
								 * If the value is not null we set it to the object on the specific field.
								 */
								if (value != null) {
									Field f = predictionModel.getClass().getDeclaredField(field.getName());
									f.setAccessible(true);
									f.set(predictionModel, (value.classType
											.getDeclaredConstructor(String.class, String.class, String.class)
											.newInstance(null, value.getDTValueIfAnyElseTextMention(), value.text)));
								} else {

									System.out.println("WARN: Can not fill dt-field: " + field.getName() + ":"
											+ ((IDataType) field.get(goldModel)).getSemanticValue());

								}

							} else {
								/*
								 * If the field is not a DataTypeProperty we need only any evidence without
								 * textual equivalence.
								 */
								Field f = predictionModel.getClass().getDeclaredField(field.getName());
								f.setAccessible(true);
								IOBIEThing property = (IOBIEThing) field.get(goldModel).getClass().newInstance();
								f.set(predictionModel, property);
								/*
								 * We call this method with the new added class recursively.
								 */
								addClassesRecursivly((IOBIEThing) field.get(goldModel), property, ner);
							}
						} else {
							if (field.getType().isAnnotationPresent(DatatypeProperty.class)) {
								System.out.println("WARN: Can not fill field: " + scioFieldType.getSimpleName() + ":"
										+ ((IDataType) field.get(goldModel)).getSemanticValue());
							} else {
								System.out.println("WARN: Can not fill field: " + scioFieldType.getSimpleName());
							}

						}
					}
				}

			} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException
					| InstantiationException | InvocationTargetException | NoSuchMethodException e) {
				e.printStackTrace();
			}
		}
	}

}
