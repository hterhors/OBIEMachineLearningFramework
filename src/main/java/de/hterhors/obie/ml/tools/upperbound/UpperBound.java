package de.hterhors.obie.ml.tools.upperbound;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.reflect.Reflection;

import de.hterhors.obie.core.evaluation.PRF1;
import de.hterhors.obie.core.ontology.AbstractOBIEIndividual;
import de.hterhors.obie.core.ontology.OntologyInitializer;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IDatatype;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.corpus.BigramInternalCorpus;
import de.hterhors.obie.ml.run.param.OBIERunParameter;
import de.hterhors.obie.ml.utils.OBIEClassFormatter;
import de.hterhors.obie.ml.utils.ReflectionUtils;
import de.hterhors.obie.ml.variables.NERLClassAnnotation;
import de.hterhors.obie.ml.variables.NamedEntityLinkingAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.TemplateAnnotation;

/**
 * Calculates the maximum recall for a specific corpus. The recall is calculated
 * by taking the gold annotations for an instance and project all property
 * values to the findings of the candidate retrieval.
 * 
 * If all properties in gold annotation can be found in the set of annotations
 * that are possible by the candidate retrieval we assume, that the state can be
 * reached.
 * 
 * @author hterhors
 *
 *         Apr 12, 2017
 */
public class UpperBound {

	private static final int MAX_CARDINALITY = 100;

	final private OBIERunParameter parameter;

	public static Logger log = LogManager.getLogger(UpperBound.class);

	public UpperBound(OBIERunParameter parameter, BigramInternalCorpus corpus) {

		/**
		 * Read test data
		 */
		List<OBIEInstance> documents = corpus.getInternalInstances();

		this.parameter = parameter;
		PRF1 upperBound = new PRF1();
		for (OBIEInstance doc : documents) {

			log.info(doc.getName());

			List<IOBIEThing> gold = doc.getGoldAnnotation().getTemplateAnnotations().stream()
					.map(e -> (IOBIEThing) e.getTemplateAnnotation()).collect(Collectors.toList());

			List<IOBIEThing> maxRecallPredictions = getUpperBoundPredictions(doc);

			final PRF1 s = parameter.evaluator.prf1(gold, maxRecallPredictions);
			log.info("score = " + s);

			for (Class<? extends IOBIEThing> clazz : doc.getNamedEntityLinkingAnnotations().getAvailableClassTypes()) {
				log.debug(doc.getNamedEntityLinkingAnnotations().getClassAnnotations(clazz));
			}
			for (AbstractOBIEIndividual individual : doc.getNamedEntityLinkingAnnotations()
					.getAvailableIndividualTypes()) {
				log.debug(doc.getNamedEntityLinkingAnnotations().getIndividualAnnotations(individual));
			}

			upperBound.add(s);
		}
		log.info("UpperBound = " + upperBound);

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
	private List<IOBIEThing> getUpperBoundPredictions(OBIEInstance doc) {

		final List<IOBIEThing> upperBoundPredictions = projectGoldToPredictions(doc);

		log.info("______GoldAnnotations:______");
		doc.getGoldAnnotation().getTemplateAnnotations()
				.forEach(s -> log.info(OBIEClassFormatter.format(s.getTemplateAnnotation(), false)));
		log.info("____________________________");
		log.info("_________Predicted:_________");
		upperBoundPredictions.forEach(f -> log.info(OBIEClassFormatter.format(f, false)));

		return upperBoundPredictions;
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
				IOBIEThing goldModel = (IOBIEThing) goldAnnotation.getTemplateAnnotation();
				IOBIEThing predictionModel = null;

				/*
				 * If the root class is of datatype.
				 */
				if (ReflectionUtils.isAnnotationPresent(goldModel.getClass(), DatatypeProperty.class)) {
					predictionModel = projectDataTypeClass(goldInstance, goldModel, predictionModel);
				} else {
					predictionModel = newClassWithIndividual(goldModel.getClass(), goldModel.getIndividual());

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
						.equals(((IDatatype) goldModel).getSemanticValue())) {
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

		List<Field> slots = ReflectionUtils.getAccessibleOntologyFields(goldModel.getClass());

		for (Field slot : slots) {
			try {
				if (slot.get(goldModel) != null) {
					if (slot.isAnnotationPresent(RelationTypeCollection.class)) {
						List<IOBIEThing> values = new ArrayList<>();

						Field f = ReflectionUtils.getAccessibleFieldByName(predictionModel.getClass(), slot.getName());

						/*
						 * Get values for that list.
						 */
						for (IOBIEThing thing : (List<IOBIEThing>) slot.get(goldModel)) {

							Class<? extends IOBIEThing> clazz = thing.getClass();

							AbstractOBIEIndividual individual = thing.getIndividual();

							/*
							 * If the mention annotation data contains evidence for that requested class.
							 */
							if (parameter.exploreClassesWithoutTextualEvidence.contains(clazz)) {

								/*
								 * If there was no evidence in the text but the class is can be explored without
								 * evidence create n new one. HWere n is the number of objects in the gold list.
								 */

								IOBIEThing property = newClassWithIndividual(clazz, individual);

								values.add(property);
								/*
								 * We call this method with the new added class recursively.
								 */
								addClassesRecursivly(thing, property, ner);

							} else if (ner.containsClassAnnotations(clazz)
									|| ner.containsIndividualAnnotations(individual)) {
								/*
								 * If class is DatatypeProperty we need the exact value.
								 */
								if (ReflectionUtils.isAnnotationPresent(clazz, DatatypeProperty.class)) {
									/*
									 * Search for exact match... break on find.
									 */
									boolean found = false;
									for (NERLClassAnnotation mentionAnnotation : ner.getClassAnnotations(clazz)) {
										if (mentionAnnotation.getDTValueIfAnyElseTextMention()
												.equals(((IDatatype) thing).getSemanticValue())) {
											found = true;
											values.add((IOBIEThing) mentionAnnotation.classType
													.getDeclaredConstructor(String.class, String.class, String.class)
													.newInstance(null, mentionAnnotation.text,
															mentionAnnotation.getDTValueIfAnyElseTextMention()));
											break;
										}
									}
									if (!found) {
										log.info("WARN: Can not fill dt-class: " + slot.getName() + ":"
												+ ((IDatatype) thing).getSemanticValue());
									}
								} else if (ner.containsIndividualAnnotations(individual)) {

									/*
									 * Else we need only the class mentioned anywhere.
									 */

									IOBIEThing property = newClassWithIndividual(clazz, individual);

									values.add(property);
									/*
									 * We call this method with the new added class recursively.
									 */
									addClassesRecursivly(thing, property, ner);
								}

							} else {
								if (ReflectionUtils.isAnnotationPresent(clazz, DatatypeProperty.class)) {
									log.info("WARN: Can not fill field: " + clazz.getSimpleName() + ":"
											+ ((IDatatype) thing).getSemanticValue());
								} else {
									log.info("WARN: Can not fill field: " + clazz.getSimpleName() + " for indiviual: "
											+ individual);
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

						IOBIEThing goldSlotValue = (IOBIEThing) slot.get(goldModel);

						/*
						 * If slot is not of type list, find only one entry.
						 */
						final Class<? extends IOBIEThing> slotType = goldSlotValue.getClass();

						AbstractOBIEIndividual individual = goldSlotValue.getIndividual();
						/*
						 * Search for data in the mention annotation data.
						 */
						if (parameter.exploreClassesWithoutTextualEvidence.contains(slotType)) {

							/*
							 * If we do not find any evidence in the mention annotation data. We still check
							 * if the field should be filled anyway (without textual evidence). This makes
							 * sense on fields that are not dependent on text. For instance helper classes
							 * or grouping classes.
							 */

							Field f = ReflectionUtils.getAccessibleFieldByName(predictionModel.getClass(),
									slot.getName());

							IOBIEThing property = (IOBIEThing) slotType.newInstance();
							f.set(predictionModel, property);
							/*
							 * We call this method with the new added class recursively.
							 */
							addClassesRecursivly(goldSlotValue, property, ner);
						} else if (ner.containsClassAnnotations(slotType)
								|| ner.containsIndividualAnnotations(individual)) {
							/*
							 * If field is DataTypeProeprty we need an exact match.
							 */
							if (ReflectionUtils.isAnnotationPresent(slot, DatatypeProperty.class)) {
								NERLClassAnnotation value = null;
								/*
								 * Search for exact match. Break on find.
								 */
								for (NERLClassAnnotation mentionAnnotation : ner.getClassAnnotations(slotType)) {
									if (mentionAnnotation.getDTValueIfAnyElseTextMention()
											.equals(((IDatatype) goldSlotValue).getSemanticValue())) {
										value = mentionAnnotation;
										break;
									}
								}
								/*
								 * If the value is not null we set it to the object on the specific field.
								 */
								if (value != null) {

									Field f = ReflectionUtils.getAccessibleFieldByName(predictionModel.getClass(),
											slot.getName());

									f.set(predictionModel, (value.classType
											.getDeclaredConstructor(String.class, String.class, String.class)
											.newInstance(null, value.getDTValueIfAnyElseTextMention(), value.text)));
								} else {

									log.info("WARN: Can not fill dt-field: " + slot.getName() + ":"
											+ ((IDatatype) goldSlotValue).getSemanticValue());

								}

							} else if (ner.containsIndividualAnnotations(individual)) {

								/*
								 * Else we need only the class mentioned anywhere.
								 */

								/*
								 * If the field is not a DataTypeProperty we need only any evidence without
								 * textual equivalence.
								 */
								Field f = ReflectionUtils.getAccessibleFieldByName(predictionModel.getClass(),
										slot.getName());

								IOBIEThing property = newClassWithIndividual(slotType, individual);

								f.set(predictionModel, property);
								/*
								 * We call this method with the new added class recursively.
								 */
								addClassesRecursivly(goldSlotValue, property, ner);
							}
						} else {
							if (ReflectionUtils.isAnnotationPresent(slot, DatatypeProperty.class)) {
								log.info("WARN: Can not fill field: " + slotType.getSimpleName() + ":"
										+ ((IDatatype) slot.get(goldModel)).getSemanticValue());
							} else {
								log.info("WARN: Can not fill field: " + slotType.getSimpleName() + " for individual: "
										+ individual);
							}

						}
					}
				} else {
					/*
					 * Empty field.
					 */
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private IOBIEThing newClassWithIndividual(final Class<? extends IOBIEThing> slotType,
			AbstractOBIEIndividual individual) {
		try {
			IOBIEThing property = (IOBIEThing) slotType.newInstance();
			if (individual != null) {

				Field individualField = ReflectionUtils.getAccessibleFieldByName(property.getClass(),
						OntologyInitializer.INDIVIDUAL_FIELD_NAME);

				individualField.set(property, individual);
			}
			return property;
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

}
