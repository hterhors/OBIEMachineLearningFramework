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
import de.hterhors.obie.core.evaluation.PRF1Container;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.evaluation.evaluator.CartesianSearchEvaluator;
import de.hterhors.obie.ml.evaluation.evaluator.IOBIEEvaluator;
import de.hterhors.obie.ml.evaluation.evaluator.NamedEntityLinkingEvaluator;
import de.hterhors.obie.ml.run.InvestigationRestriction;
import de.hterhors.obie.ml.run.InvestigationRestriction.RestrictedField;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import evaluation.EvaluationUtil;
import learning.ObjectiveFunction;

public class EvaluatePrediction {

	public static Logger log = LogManager.getRootLogger();

	public static void evaluateNERPredictions(IOBIEThing initializingObject,
			ObjectiveFunction<OBIEState, InstanceTemplateAnnotations> objectiveFunction,
			List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> predictions,
			InvestigationRestriction investigationRestriction) {
		Map<String, Set<EvaluationObject>> gold = new HashMap<>();
		Map<String, Set<EvaluationObject>> result = new HashMap<>();

		for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> prediction : predictions) {

			OBIEInstance resultState = prediction.getInstance();
			InstanceTemplateAnnotations goldState = prediction.getGoldResult();

			final String key = resultState.getName();

			result.putIfAbsent(key, new HashSet<EvaluationObject>());
			for (TemplateAnnotation resultEntity : prediction.getState().getCurrentTemplateAnnotations()
					.getTemplateAnnotations()) {
				if (!resultEntity.getThing().equals(initializingObject))
					result.get(key).add(new EvaluationObject(resultEntity, investigationRestriction));
			}

			gold.putIfAbsent(key, new HashSet<EvaluationObject>());
			for (TemplateAnnotation goldEntity : goldState.getTemplateAnnotations()) {
				gold.get(key).add(new EvaluationObject(goldEntity, investigationRestriction));
			}

		}

		for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> prediction : predictions) {
			OBIEState state = prediction.getState();
			InstanceTemplateAnnotations goldState = state.getInstance().getGoldAnnotation();
			objectiveFunction.score(state, goldState);
		}
		EvaluationUtil
				.printPredictionPerformance(predictions.stream().map(p -> p.getState()).collect(Collectors.toList()));

		Set<OnlyTextEvaluationObject> goldSet = new HashSet<>();
		Set<OnlyTextEvaluationObject> predictionSet = new HashSet<>();

		int docIndex = 0;
		for (Entry<String, Set<EvaluationObject>> state : gold.entrySet()) {
			log.info("_____________" + state.getKey() + "______________");
			log.info(state.getKey());
			log.info("Gold:\t");
			log.info(gold.get(state.getKey()));
			log.info("Result:\t");
			log.info(result.get(state.getKey()));
			final int i = docIndex;
			Set<OnlyTextEvaluationObject> goldList = gold.get(state.getKey()).stream()
					.map(s -> new OnlyTextEvaluationObject(s.scioClass.getTextMention(), i))
					.collect(Collectors.toSet());

			Set<OnlyTextEvaluationObject> predictionList = result.get(state.getKey()).stream()
					.map(s -> new OnlyTextEvaluationObject(s.scioClass.getTextMention(), i))
					.collect(Collectors.toSet());

			final double p = NamedEntityLinkingEvaluator.precision(goldList, predictionList);
			final double r = NamedEntityLinkingEvaluator.recall(goldList, predictionList);
			final double f1 = NamedEntityLinkingEvaluator.f1(goldList, predictionList);
			log.info("Doc-Precisiion = " + p);
			log.info("Doc-Recall = " + r);
			log.info("Doc-F1 = " + f1);

			goldSet.addAll(goldList);
			predictionSet.addAll(predictionList);
			docIndex++;
		}

		final double p = NamedEntityLinkingEvaluator.precision(goldSet, predictionSet);
		final double r = NamedEntityLinkingEvaluator.recall(goldSet, predictionSet);
		final double f1 = NamedEntityLinkingEvaluator.f1(goldSet, predictionSet);

		log.info("Micro-Precisiion = " + p);
		log.info("Micro-Recall = " + r);
		log.info("Micro-F1 = " + f1);

	}

	public static PRF1Container evaluateREPredictions(
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
				if (!resultEntity.getThing().equals(resultEntity.getInitializationClass()))
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
			log.debug("Doc-Precisiion = " + p);
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

		log.info("MICRO: Mean-Precisiion = " + x.getPrecision());
		log.info("MICRO: Mean-Recall = " + x.getRecall());
		log.info("MICRO: Mean-F1 = " + x.getF1());

		log.info("MACRO: Mean-Precisiion = " + meanP);
		log.info("MACRO: Mean-Recall = " + meanR);
		// log.info("MACRO: Mean-F1 = " + meanF1);
		log.info("MACRO: Mean-F1 = " + (2 * meanP * meanR) / (meanP + meanR));
		return new PRF1Container(meanP, meanR, (2 * meanP * meanR) / (meanP + meanR));
	}

	public static double evaluatePurityPredictions(
			ObjectiveFunction<OBIEState, InstanceTemplateAnnotations> objectiveFunction,
			List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> predictions,
			IOBIEEvaluator evaluator, InvestigationRestriction investigationRestriction) {
		Map<String, Set<EvaluationObject>> gold = new HashMap<>();
		Map<String, Set<EvaluationObject>> result = new HashMap<>();

		for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> prediction : predictions) {

			OBIEInstance resultState = prediction.getInstance();
			InstanceTemplateAnnotations goldState = prediction.getGoldResult();

			final String key = resultState.getName();

			result.putIfAbsent(key, new HashSet<EvaluationObject>());
			for (TemplateAnnotation resultEntity : prediction.getState().getCurrentTemplateAnnotations()
					.getTemplateAnnotations()) {
				if (!resultEntity.getThing().equals(resultEntity.getInitializationClass()))
					result.get(key).add(new EvaluationObject(resultEntity, investigationRestriction));
			}

			gold.putIfAbsent(key, new HashSet<EvaluationObject>());
			for (TemplateAnnotation goldEntity : goldState.getTemplateAnnotations()) {
				gold.get(key).add(new EvaluationObject(goldEntity, investigationRestriction));
			}

		}

		for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> prediction : predictions) {
			OBIEState state = prediction.getState();
			InstanceTemplateAnnotations goldState = state.getInstance().getGoldAnnotation();
			objectiveFunction.score(state, goldState);
		}
		EvaluationUtil
				.printPredictionPerformance(predictions.stream().map(p -> p.getState()).collect(Collectors.toList()));

		double meanF1 = 0;

		for (Entry<String, Set<EvaluationObject>> state : gold.entrySet()) {
			log.info("_____________" + state.getKey() + "______________");
			log.info(state.getKey());
			log.info("Gold:\t");
			log.info(gold.get(state.getKey()));
			log.info("Result:\t");
			log.info(result.get(state.getKey()));

			List<IOBIEThing> goldList = gold.get(state.getKey()).stream().map(s -> (s.scioClass))
					.collect(Collectors.toList());

			List<IOBIEThing> predictionList = result.get(state.getKey()).stream().map(s -> s.scioClass)
					.collect(Collectors.toList());

			final double f1 = evaluator.f1(goldList, predictionList);
			log.info("Doc-F1 = " + f1);

			meanF1 += f1;

		}
		log.info("");
		log.info("");

		// PRF1ScoreContainer x = new PRF1ScoreContainer(TP, FP, FN);

		// meanP /= gold.entrySet().size();
		// meanR /= gold.entrySet().size();
		meanF1 /= gold.entrySet().size();

		log.info("Purity-InversePurity F_Rij = " + meanF1);

		return meanF1;
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
				if (!resultEntity.getThing().equals(resultEntity.getInitializationClass()))
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
					log.info("Doc-Precisiion = " + p);
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
			log.info("Mean-Precisiion = " + meanP);
			log.info("Mean-Recall = " + meanR);
			log.info("Mean-F1 = " + meanF1);
			log.info("#############################");

		}
	}

}
