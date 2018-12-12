package de.hterhors.obie.ml.run.eval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import corpus.SampledInstance;
import de.hterhors.obie.core.evaluation.PRF1;
import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.InvestigationRestriction;
import de.hterhors.obie.core.ontology.InvestigationRestriction.RestrictedField;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.evaluation.evaluator.CartesianSearchEvaluator;
import de.hterhors.obie.ml.evaluation.evaluator.IOBIEEvaluator;
import de.hterhors.obie.ml.evaluation.evaluator.StrictNamedEntityLinkingEvaluator;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import evaluation.EvaluationUtil;
import exceptions.MissingFactorException;
import factors.Factor;
import learning.ObjectiveFunction;
import learning.Vector;

public class EvaluatePrediction {

	public static Logger log = LogManager.getRootLogger();

	public static void evaluateNERLPredictions(
			ObjectiveFunction<OBIEState, InstanceTemplateAnnotations> objectiveFunction,
			List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> predictions,
			IOBIEEvaluator evaluator) {

//		Map<String, Set<EvaluationObject>> gold = new HashMap<>();
//		Map<String, Set<EvaluationObject>> result = new HashMap<>();
		PRF1 mean = new PRF1();

		double p = 0;
		double r = 0;
		double f1 = 0;

		for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> prediction : predictions) {

//			OBIEInstance resultState = prediction.getInstance();
//			InstanceTemplateAnnotations goldAnnotation = prediction.getGoldResult();
//
//			final String key = resultState.getName();
//
//			result.putIfAbsent(key, new HashSet<EvaluationObject>());
//			for (TemplateAnnotation resultEntity : prediction.getState().getCurrentTemplateAnnotations()
//					.getTemplateAnnotations()) {
//				result.get(key).add(new EvaluationObject(resultEntity, evaluator.getInvestigationRestrictions()));
//			}
//
//			gold.putIfAbsent(key, new HashSet<EvaluationObject>());
//			for (TemplateAnnotation goldEntity : goldState.getTemplateAnnotations()) {
//				gold.get(key).add(new EvaluationObject(goldEntity, evaluator.getInvestigationRestrictions()));
//			}
			double f1Concepts = StrictNamedEntityLinkingEvaluator.f1(
					prediction.getGoldResult().getTemplateAnnotations().stream()
							.map(ta -> ta.getThing().getIndividual()).collect(Collectors.toSet()),
					prediction.getState().getCurrentTemplateAnnotations().getTemplateAnnotations().stream()
							.map(ta -> ta.getThing().getIndividual()).collect(Collectors.toSet()));
			double pConcepts = StrictNamedEntityLinkingEvaluator.precision(
					prediction.getGoldResult().getTemplateAnnotations().stream()
							.map(ta -> ta.getThing().getIndividual()).collect(Collectors.toSet()),
					prediction.getState().getCurrentTemplateAnnotations().getTemplateAnnotations().stream()
							.map(ta -> ta.getThing().getIndividual()).collect(Collectors.toSet()));
			double rConcepts = StrictNamedEntityLinkingEvaluator.recall(
					prediction.getGoldResult().getTemplateAnnotations().stream()
							.map(ta -> ta.getThing().getIndividual()).collect(Collectors.toSet()),
					prediction.getState().getCurrentTemplateAnnotations().getTemplateAnnotations().stream()
							.map(ta -> ta.getThing().getIndividual()).collect(Collectors.toSet()));

			p += pConcepts;
			r += rConcepts;
			f1 += f1Concepts;

			PRF1 prf1 = evaluator.prf1(
					prediction.getGoldResult().getTemplateAnnotations().stream().map(ta -> ta.getThing())
							.collect(Collectors.toList()),
					prediction.getState().getCurrentTemplateAnnotations().getTemplateAnnotations().stream()
							.map(ta -> ta.getThing()).collect(Collectors.toList()));

			log.info("_____________" + prediction.getState().getInstance().getName() + "______________");
			log.info("Gold:\t");
			log.info(prediction.getGoldResult().getTemplateAnnotations().stream().map(ta -> ta.getThing())
					.collect(Collectors.toList()));
			log.info("Predictions:\t");
			log.info(prediction.getState().getCurrentTemplateAnnotations().getTemplateAnnotations().stream()
					.map(ta -> ta.getThing()).collect(Collectors.toList()));
			log.info(prf1);
			log.info("ConceptLevel F1: " + f1Concepts + " Precision: " + pConcepts + " Recall: " + rConcepts);
			mean.add(prf1);
		}
		log.info("MICRO: Mean-Precision = " + mean.getPrecision());
		log.info("MICRO: Mean-Recall = " + mean.getRecall());
		log.info("MICRO: Mean-F1 = " + mean.getF1());

		log.info("MICRO: Mean-Concept-Precision = " + p / predictions.size());
		log.info("MICRO: Mean-Concept-Recall = " + r / predictions.size());
		log.info("MICRO: Mean-Concept-F1 = " + f1 / predictions.size());

//		Set<OnlyTextEvaluationObject> goldSet = new HashSet<>();
//		Set<OnlyTextEvaluationObject> predictionSet = new HashSet<>();
//
//		int docIndex = 0;
//		for (Entry<String, Set<EvaluationObject>> state : gold.entrySet()) {
//			log.info("_____________" + state.getKey() + "______________");
//			log.info(state.getKey());
//			log.info("Gold:\t");
//			log.info(gold.get(state.getKey()));
//			log.info("Result:\t");
//			log.info(gold.get(state.getKey()));
//			final int i = docIndex;
//
//			Set<OnlyTextEvaluationObject> goldList = gold.get(state.getKey()).stream()
//					.map(s -> new OnlyTextEvaluationObject(s.scioClass.getTextMention(), i))
//					.collect(Collectors.toSet());
//
//			Set<OnlyTextEvaluationObject> predictionList = result.get(state.getKey()).stream()
//					.map(s -> new OnlyTextEvaluationObject(s.scioClass.getTextMention(), i))
//					.collect(Collectors.toSet());
//
//			final double p = StrictNamedEntityLinkingEvaluator.precision(goldList, predictionList);
//			final double r = StrictNamedEntityLinkingEvaluator.recall(goldList, predictionList);
//			final double f1 = StrictNamedEntityLinkingEvaluator.f1(goldList, predictionList);
//			log.info("Doc-Precisiion = " + p);
//			log.info("Doc-Recall = " + r);
//			log.info("Doc-F1 = " + f1);
//
//			goldSet.addAll(goldList);
//			predictionSet.addAll(predictionList);
//			docIndex++;
//		}
//
//		final double p = StrictNamedEntityLinkingEvaluator.precision(goldSet, predictionSet);
//		final double r = StrictNamedEntityLinkingEvaluator.recall(goldSet, predictionSet);
//		final double f1 = StrictNamedEntityLinkingEvaluator.f1(goldSet, predictionSet);

	}
//	public static void evaluateNERPredictions(IOBIEThing initializingObject,
//			ObjectiveFunction<OBIEState, InstanceTemplateAnnotations> objectiveFunction,
//			List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> predictions,
//			InvestigationRestriction investigationRestriction) {
//		Map<String, Set<EvaluationObject>> gold = new HashMap<>();
//		Map<String, Set<EvaluationObject>> result = new HashMap<>();
//		
//		for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> prediction : predictions) {
//			
//			OBIEInstance resultState = prediction.getInstance();
//			InstanceTemplateAnnotations goldState = prediction.getGoldResult();
//			
//			final String key = resultState.getName();
//			
//			result.putIfAbsent(key, new HashSet<EvaluationObject>());
//			for (TemplateAnnotation resultEntity : prediction.getState().getCurrentTemplateAnnotations()
//					.getTemplateAnnotations()) {
//				if (!resultEntity.getThing().equals(initializingObject))
//					result.get(key).add(new EvaluationObject(resultEntity, investigationRestriction));
//			}
//			
//			gold.putIfAbsent(key, new HashSet<EvaluationObject>());
//			for (TemplateAnnotation goldEntity : goldState.getTemplateAnnotations()) {
//				gold.get(key).add(new EvaluationObject(goldEntity, investigationRestriction));
//			}
//			
//		}
//		
//		for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> prediction : predictions) {
//			OBIEState state = prediction.getState();
//			InstanceTemplateAnnotations goldState = state.getInstance().getGoldAnnotation();
//			objectiveFunction.score(state, goldState);
//		}
//		EvaluationUtil
//		.printPredictionPerformance(predictions.stream().map(p -> p.getState()).collect(Collectors.toList()));
//		
//		Set<OnlyTextEvaluationObject> goldSet = new HashSet<>();
//		Set<OnlyTextEvaluationObject> predictionSet = new HashSet<>();
//		
//		int docIndex = 0;
//		for (Entry<String, Set<EvaluationObject>> state : gold.entrySet()) {
//			log.info("_____________" + state.getKey() + "______________");
//			log.info(state.getKey());
//			log.info("Gold:\t");
//			log.info(gold.get(state.getKey()));
//			log.info("Result:\t");
//			log.info(result.get(state.getKey()));
//			final int i = docIndex;
//			Set<OnlyTextEvaluationObject> goldList = gold.get(state.getKey()).stream()
//					.map(s -> new OnlyTextEvaluationObject(s.scioClass.getTextMention(), i))
//					.collect(Collectors.toSet());
//			
//			Set<OnlyTextEvaluationObject> predictionList = result.get(state.getKey()).stream()
//					.map(s -> new OnlyTextEvaluationObject(s.scioClass.getTextMention(), i))
//					.collect(Collectors.toSet());
//			
//			final double p = StrictNamedEntityLinkingEvaluator.precision(goldList, predictionList);
//			final double r = StrictNamedEntityLinkingEvaluator.recall(goldList, predictionList);
//			final double f1 = StrictNamedEntityLinkingEvaluator.f1(goldList, predictionList);
//			log.info("Doc-Precisiion = " + p);
//			log.info("Doc-Recall = " + r);
//			log.info("Doc-F1 = " + f1);
//			
//			goldSet.addAll(goldList);
//			predictionSet.addAll(predictionList);
//			docIndex++;
//		}
//		
//		final double p = StrictNamedEntityLinkingEvaluator.precision(goldSet, predictionSet);
//		final double r = StrictNamedEntityLinkingEvaluator.recall(goldSet, predictionSet);
//		final double f1 = StrictNamedEntityLinkingEvaluator.f1(goldSet, predictionSet);
//		
//		log.info("Micro-Precisiion = " + p);
//		log.info("Micro-Recall = " + r);
//		log.info("Micro-F1 = " + f1);
//		
//	}

	public static PRF1 evaluateREPredictions(
			ObjectiveFunction<OBIEState, InstanceTemplateAnnotations> objectiveFunction,
			List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> predictions,
			IOBIEEvaluator evaluator) {

		Map<String, Set<EvaluationObject>> gold = new HashMap<>();
		Map<String, Set<EvaluationObject>> result = new HashMap<>();

		for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> prediction : predictions) {

			OBIEInstance resultState = prediction.getInstance();
			InstanceTemplateAnnotations goldState = prediction.getGoldResult();

			final String key = resultState.getName();

			result.putIfAbsent(key, new HashSet<EvaluationObject>());
			for (TemplateAnnotation resultEntity : prediction.getState().getCurrentTemplateAnnotations()
					.getTemplateAnnotations()) {
				if (!resultEntity.getThing().equals(resultEntity.getInitializationThing()))
					result.get(key).add(new EvaluationObject(resultEntity, evaluator.getInvestigationRestrictions()));
			}

			gold.putIfAbsent(key, new HashSet<EvaluationObject>());
			for (TemplateAnnotation goldEntity : goldState.getTemplateAnnotations()) {
				gold.get(key).add(new EvaluationObject(goldEntity, evaluator.getInvestigationRestrictions()));
			}

		}

		for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> prediction : predictions) {
			OBIEState state = prediction.getState();
			InstanceTemplateAnnotations goldState = state.getInstance().getGoldAnnotation();
			objectiveFunction.score(state, goldState);
		}
		EvaluationUtil
				.printPredictionPerformance(predictions.stream().map(p -> p.getState()).collect(Collectors.toList()));

		double meanP = 0;
		double meanR = 0;
		// double meanF1 = 0;

		int TP = 0;
		int FP = 0;
		int FN = 0;

		for (Entry<String, Set<EvaluationObject>> state : gold.entrySet()) {
			log.debug("_____________" + state.getKey() + "______________");
			log.debug("Gold:\t");
			log.debug(gold.get(state.getKey()));
			log.debug("Result:\t");
			log.debug(result.get(state.getKey()));

			List<IOBIEThing> goldList = gold.get(state.getKey()).stream().map(s -> (s.scioClass))
					.collect(Collectors.toList());

			List<IOBIEThing> predictionList = result.get(state.getKey()).stream().map(s -> s.scioClass)
					.collect(Collectors.toList());

			FP += evaluator.prf1(goldList, predictionList).fp;
			TP += evaluator.prf1(goldList, predictionList).tp;
			FN += evaluator.prf1(goldList, predictionList).fn;

			final double p = evaluator.precision(goldList, predictionList);
			final double r = evaluator.recall(goldList, predictionList);
			final double f1 = evaluator.f1(goldList, predictionList);
			log.debug("Doc-Precision = " + p);
			log.debug("Doc-Recall = " + r);
			log.debug("Doc-F1 = " + f1);

			meanP += p;
			meanR += r;
			// meanF1 += f1;

		}
		log.debug("");
		log.debug("");

		PRF1 x = new PRF1(TP, FP, FN);

		meanP /= gold.entrySet().size();
		meanR /= gold.entrySet().size();

		log.info("MICRO: Mean-Precision = " + x.getPrecision());
		log.info("MICRO: Mean-Recall = " + x.getRecall());
		log.info("MICRO: Mean-F1 = " + x.getF1());

		log.info("MACRO: Mean-Precision = " + meanP);
		log.info("MACRO: Mean-Recall = " + meanR);
		log.info("MACRO: Mean-F1 = " + (2 * meanP * meanR) / (meanP + meanR));
		return x;
	}

	public static Map<AbstractIndividual, PRF1> evaluateBinaryClassification(
			List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> predictions) {

		Map<AbstractIndividual, PRF1> results = new HashMap<>();

		Set<AbstractIndividual> existingLabels = new HashSet<>();
		for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> prediction : predictions) {
			for (TemplateAnnotation resultEntity : prediction.getState().getCurrentTemplateAnnotations()
					.getTemplateAnnotations()) {
				existingLabels.add(resultEntity.getThing().getIndividual());
			}
			for (TemplateAnnotation goldEntity : prediction.getGoldResult().getTemplateAnnotations()) {
				existingLabels.add(goldEntity.getThing().getIndividual());
			}
		}

//		PRF1 accurracy = new PRF1();

		for (AbstractIndividual label : existingLabels) {

			results.put(label, new PRF1());

			for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> prediction : predictions) {

				AbstractIndividual goldLabel = prediction.getGoldResult().getTemplateAnnotations().iterator().next()
						.getThing().getIndividual();
				AbstractIndividual predictedLabel = prediction.getState().getCurrentTemplateAnnotations()
						.getTemplateAnnotations().iterator().next().getThing().getIndividual();

				if (goldLabel.equals(label)) {
//					accurracy.tp += goldLabel.equals(predictedLabel) ? 1 : 0;
//					accurracy.fn += !goldLabel.equals(predictedLabel) ? 1 : 0;
					results.get(label).tp += goldLabel.equals(predictedLabel) ? 1 : 0;
					results.get(label).fn += !goldLabel.equals(predictedLabel) ? 1 : 0;

				}

				if (!goldLabel.equals(label)) {
//					accurracy.fp += !goldLabel.equals(predictedLabel) ? 1 : 0;
					results.get(label).fp += !goldLabel.equals(predictedLabel) ? 1 : 0;
				}

			}
		}
//		results.put("accurracy", accurracy);
		return results;
	}

	public static Map<AbstractIndividual, PRF1> evaluatePerTypePredictions(
			ObjectiveFunction<OBIEState, InstanceTemplateAnnotations> objectiveFunction,
			List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> predictions,
			IOBIEEvaluator evaluator) {

		Map<AbstractIndividual, PRF1> results = new HashMap<>();

		Set<AbstractIndividual> existingIndividuals = new HashSet<>();

		for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> prediction : predictions) {

			for (TemplateAnnotation resultEntity : prediction.getState().getCurrentTemplateAnnotations()
					.getTemplateAnnotations()) {
				existingIndividuals.add(resultEntity.getThing().getIndividual());
			}

			for (TemplateAnnotation goldEntity : prediction.getGoldResult().getTemplateAnnotations()) {
				existingIndividuals.add(goldEntity.getThing().getIndividual());
			}

		}

		for (AbstractIndividual individualType : existingIndividuals) {
			log.info(individualType.name);
			Map<String, Set<EvaluationObject>> gold = new HashMap<>();
			Map<String, Set<EvaluationObject>> result = new HashMap<>();

			for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> prediction : predictions) {

				final String key = prediction.getInstance().getName();

				boolean add = false;
				for (TemplateAnnotation goldEntity : prediction.getGoldResult().getTemplateAnnotations()) {
					if (add |= individualType.equals(goldEntity.getThing().getIndividual())) {
						gold.putIfAbsent(key, new HashSet<EvaluationObject>());
						gold.get(key).add(new EvaluationObject(goldEntity, evaluator.getInvestigationRestrictions()));
					}
				}

				if (add) {
					result.putIfAbsent(key, new HashSet<EvaluationObject>());
					for (TemplateAnnotation resultEntity : prediction.getState().getCurrentTemplateAnnotations()
							.getTemplateAnnotations()) {
						result.get(key)
								.add(new EvaluationObject(resultEntity, evaluator.getInvestigationRestrictions()));
					}
				}
			}

			double meanP = 0;
			double meanR = 0;
			// double meanF1 = 0;

			int TP = 0;
			int FP = 0;
			int FN = 0;
			log.info("Number Of Docs:= " + gold.size());

			for (Entry<String, Set<EvaluationObject>> state : gold.entrySet()) {
				log.debug("_____________" + state.getKey() + "______________");
				log.debug("Gold:\t");
				log.debug(gold.get(state.getKey()));
				log.debug("Result:\t");
				log.debug(result.get(state.getKey()));

				List<IOBIEThing> goldList = gold.get(state.getKey()).stream().map(s -> (s.scioClass))
						.collect(Collectors.toList());

				List<IOBIEThing> predictionList = result.get(state.getKey()).stream().map(s -> s.scioClass)
						.collect(Collectors.toList());

				FP += evaluator.prf1(goldList, predictionList).fp;
				TP += evaluator.prf1(goldList, predictionList).tp;
				FN += evaluator.prf1(goldList, predictionList).fn;

				final double p = evaluator.precision(goldList, predictionList);
				final double r = evaluator.recall(goldList, predictionList);
				final double f1 = evaluator.f1(goldList, predictionList);
				log.debug("Doc-Precision = " + p);
				log.debug("Doc-Recall = " + r);
				log.debug("Doc-F1 = " + f1);

				meanP += p;
				meanR += r;
				// meanF1 += f1;

			}

			log.debug("");
			log.debug("");

			PRF1 x = new PRF1(TP, FP, FN);

			meanP /= gold.entrySet().size();
			meanR /= gold.entrySet().size();
			// meanF1 /= gold.entrySet().size();

			log.info("MICRO: Mean-Precision = " + x.getPrecision());
			log.info("MICRO: Mean-Recall = " + x.getRecall());
			log.info("MICRO: Mean-F1 = " + x.getF1());

			log.info("MACRO: Mean-Precision = " + meanP);
			log.info("MACRO: Mean-Recall = " + meanR);
			// log.info("MACRO: Mean-F1 = " + meanF1);
			log.info("MACRO: Mean-F1 = " + (2 * meanP * meanR) / (meanP + meanR));
			results.put(individualType, x);
		}
		return results;
	}

	public static Map<AbstractIndividual, PRF1> analysePerTypePredictions(
			ObjectiveFunction<OBIEState, InstanceTemplateAnnotations> objectiveFunction,
			List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> predictions,
			IOBIEEvaluator evaluator) {

		Map<AbstractIndividual, PRF1> results = new HashMap<>();

		Set<AbstractIndividual> existingIndividuals = new HashSet<>();

		Map<String, List<String>> factors = new HashMap<>();

		for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> prediction : predictions) {

			factors.putIfAbsent(prediction.getInstance().getName(), new ArrayList<>());
			for (TemplateAnnotation resultEntity : prediction.getState().getCurrentTemplateAnnotations()
					.getTemplateAnnotations()) {
				existingIndividuals.add(resultEntity.getThing().getIndividual());
			}

			for (TemplateAnnotation goldEntity : prediction.getGoldResult().getTemplateAnnotations()) {
				existingIndividuals.add(goldEntity.getThing().getIndividual());
			}

			try {
				double modelScore = 0;
				double invertLabelModelScore = 0;

				for (Factor<?> factor : prediction.getState().getFactorGraph().getFactors()) {
					Vector featureVector = factor.getFeatureVector();
					Vector weights = factor.getTemplate().getWeights();
					Vector smaller = null;
					Vector bigger = null;

					if (featureVector.getFeatureNames().size() <= weights.getFeatureNames().size()) {
						smaller = featureVector;
						bigger = weights;
					} else {
						smaller = weights;
						bigger = featureVector;
					}

					if (smaller.getFeatures().size() != 0) {
						for (Entry<String, Double> e : smaller.getFeatures().entrySet()) {
							final double d1 = e.getValue() * bigger.getValueOfFeature(e.getKey());
							final double d2;
							if (e.getKey().startsWith("<OFF>")) {
								d2 = e.getValue() * bigger.getValueOfFeature(e.getKey().replaceFirst("<OFF>", "<NOT>"));
							} else {
								d2 = e.getValue() * bigger.getValueOfFeature(e.getKey().replaceFirst("<NOT>", "<OFF>"));
							}
							modelScore += d1;
							invertLabelModelScore += d2;
							if (d1 != 0) {
								factors.get(prediction.getInstance().getName()).add(e.getKey() + "\t" + (d1 + d2));
							}
						}
					}
				}

				factors.get(prediction.getInstance().getName()).add("MODEL_SCORE\t" + modelScore);
				factors.get(prediction.getInstance().getName())
						.add("INVERT_LABEL_MODEL_SCORE\t" + invertLabelModelScore);
				factors.get(prediction.getInstance().getName())
						.add("CONFIDENCE\t" + (modelScore - invertLabelModelScore));

			} catch (MissingFactorException e) {
				e.printStackTrace();
			}

		}

		for (AbstractIndividual individualType : existingIndividuals) {
			log.info(individualType.name);
			Map<String, Set<EvaluationObject>> gold = new HashMap<>();
			Map<String, Set<EvaluationObject>> result = new HashMap<>();

			/*
			 * Name,Content
			 */
			Map<String, String> nameContent = new HashMap<>();

			for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> prediction : predictions) {

				final String key = prediction.getInstance().getName();

				nameContent.put(key, prediction.getInstance().getContent());

				boolean add = false;

				for (TemplateAnnotation goldEntity : prediction.getGoldResult().getTemplateAnnotations()) {
					if (add |= individualType.equals(goldEntity.getThing().getIndividual())) {
						gold.putIfAbsent(key, new HashSet<EvaluationObject>());
						gold.get(key).add(new EvaluationObject(goldEntity, evaluator.getInvestigationRestrictions()));
					}
				}

				if (add) {
					result.putIfAbsent(key, new HashSet<EvaluationObject>());
					for (TemplateAnnotation resultEntity : prediction.getState().getCurrentTemplateAnnotations()
							.getTemplateAnnotations()) {
						result.get(key)
								.add(new EvaluationObject(resultEntity, evaluator.getInvestigationRestrictions()));
					}
				}

			}

			log.info("Number Of Docs:= " + gold.size());

			for (Entry<String, Set<EvaluationObject>> state : gold.entrySet()) {

				List<IOBIEThing> goldList = gold.get(state.getKey()).stream().map(s -> (s.scioClass))
						.collect(Collectors.toList());

				List<IOBIEThing> predictionList = result.get(state.getKey()).stream().map(s -> s.scioClass)
						.collect(Collectors.toList());

				final double f1 = evaluator.f1(goldList, predictionList);

				if (f1 != 1) {
					log.info("_____________" + state.getKey() + "______________");
					log.info(nameContent.get(state.getKey()));

					log.info("Gold:\t");
					log.info(gold.get(state.getKey()));
					log.info("Result:\t");
					log.info(result.get(state.getKey()));

					Collections.sort(factors.get(state.getKey()), (a, b) -> -Double
							.compare(Double.parseDouble(a.split("\t")[1]), Double.parseDouble(b.split("\t")[1])));

					for (String factor : factors.get(state.getKey())) {
//					if (factor.startsWith("CONFIDENCE"))
						log.info(factor);
					}

				}

			}
			log.info("");
			log.info("");

		}
		return results;
	}

	public static void evaluatePerSlotPredictions(
			ObjectiveFunction<OBIEState, InstanceTemplateAnnotations> objectiveFunction,
			List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> predictions,
			IOBIEEvaluator evaluator, boolean detailedOutput) {

		Map<String, Set<EvaluationObject>> gold = new HashMap<>();
		Map<String, Set<EvaluationObject>> result = new HashMap<>();

		for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> prediction : predictions) {

			OBIEInstance resultState = prediction.getInstance();
			InstanceTemplateAnnotations goldState = prediction.getGoldResult();

			final String key = resultState.getName();

			result.putIfAbsent(key, new HashSet<EvaluationObject>());
			for (TemplateAnnotation resultEntity : prediction.getState().getCurrentTemplateAnnotations()
					.getTemplateAnnotations()) {
				if (!resultEntity.getThing().equals(resultEntity.getInitializationThing()))
					result.get(key).add(new EvaluationObject(resultEntity, evaluator.getInvestigationRestrictions()));
			}

			gold.putIfAbsent(key, new HashSet<EvaluationObject>());
			for (TemplateAnnotation goldEntity : goldState.getTemplateAnnotations()) {
				gold.get(key).add(new EvaluationObject(goldEntity, evaluator.getInvestigationRestrictions()));
			}

		}

		for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> prediction : predictions) {
			OBIEState state = prediction.getState();
			InstanceTemplateAnnotations goldState = state.getInstance().getGoldAnnotation();
			objectiveFunction.score(state, goldState);
		}

//		EvaluationUtil
//				.printPredictionPerformance(predictions.stream().map(p -> p.getState()).collect(Collectors.toList()));

		/*
		 * WE ASSUME ALL ELEMENTS IN SET HAVE THE SAME TYPE!
		 */
		Class<? extends IOBIEThing> classType = gold.get(gold.keySet().iterator().next()).iterator().next().scioClass
				.getClass();

		List<Set<RestrictedField>> restrictFieldsList = InvestigationRestriction
				.getFieldRestrictionCombinations(classType, InvestigationRestriction.getMainSingleFields(classType));

		List<InvestigationRestriction> restrictions = new ArrayList<>();
		restrictions.add(new InvestigationRestriction(classType, Collections.emptySet(), true));

		for (Set<RestrictedField> set : restrictFieldsList) {
			if (set.size() > 1) {
				/**
				 * TODO: allow more than a single field here: parameterize
				 */
				continue;
			}
			for (int i = 1; i < 3; i++) {
				restrictions.add(new InvestigationRestriction(classType, set, i % 2 == 0));
			}
		}

		for (InvestigationRestriction rest : restrictions) {

			evaluator = new CartesianSearchEvaluator(true, evaluator.getMaxEvaluationDepth(),
					evaluator.isPenalizeCardinality(), rest, evaluator.getOrListCondition(), 7,
					evaluator.isIgnoreEmptyInstancesOnEvaluation());

			if (detailedOutput) {
				log.info("#############################");
				log.info(evaluator.getInvestigationRestrictions());
				log.info("#############################");
			}

			double meanP = 0;
			double meanR = 0;
			double meanF1 = 0;
			for (Entry<String, Set<EvaluationObject>> state : gold.entrySet()) {
				if (detailedOutput) {
					log.info("_____________" + state.getKey() + "______________");
					log.info("Gold:\t");
					log.info(gold.get(state.getKey()));
					log.info("Result:\t");
					log.info(result.get(state.getKey()));
				}
				List<IOBIEThing> goldList = gold.get(state.getKey()).stream().map(s -> (s.scioClass))
						.collect(Collectors.toList());

				List<IOBIEThing> predictionList = result.get(state.getKey()).stream().map(s -> s.scioClass)
						.collect(Collectors.toList());

				final double p = evaluator.precision(goldList, predictionList);
				final double r = evaluator.recall(goldList, predictionList);
				final double f1 = evaluator.f1(goldList, predictionList);
				if (detailedOutput) {
					log.info("Doc-Precision = " + p);
					log.info("Doc-Recall = " + r);
					log.info("Doc-F1 = " + f1);
				}
				meanP += p;
				meanR += r;
				meanF1 += f1;

			}

			log.info("");
			log.info("");

			log.info("#############################");
			if (detailedOutput)
				log.info(evaluator.getInvestigationRestrictions());
			else
				log.info(evaluator.getInvestigationRestrictions().summarize());

			meanP /= gold.entrySet().size();
			meanR /= gold.entrySet().size();
			meanF1 /= gold.entrySet().size();
			log.info("Mean-Precision = " + meanP);
			log.info("Mean-Recall = " + meanR);
			log.info("Mean-F1 = " + meanF1);
			log.info("#############################");

		}
	}

}
