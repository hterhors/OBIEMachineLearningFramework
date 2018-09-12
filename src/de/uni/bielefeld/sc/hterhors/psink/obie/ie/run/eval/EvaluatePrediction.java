package de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.eval;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import corpus.SampledInstance;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.evaluation.PRF1;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.evaluation.PRF1Container;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation.evaluator.AbstractOBIEEvaluator;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation.evaluator.CartesianSearchEvaluator;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation.evaluator.IEvaluator;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation.evaluator.NamedEntityLinkingEvaluator;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.InvestigationRestriction;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.InvestigationRestriction.RestrictedField;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.EntityAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.InstanceEntityAnnotations;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;
import evaluation.EvaluationUtil;
import learning.ObjectiveFunction;

public class EvaluatePrediction {

	public static void evaluateNERPredictions(IOBIEThing initializingObject,
			ObjectiveFunction<OBIEState, InstanceEntityAnnotations> objectiveFunction,
			List<SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>> predictions,
			InvestigationRestriction investigationRestriction) {
		Map<String, Set<EvaluationObject>> gold = new HashMap<>();
		Map<String, Set<EvaluationObject>> result = new HashMap<>();

		for (SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState> prediction : predictions) {

			OBIEInstance resultState = prediction.getInstance();
			InstanceEntityAnnotations goldState = prediction.getGoldResult();

			final String key = resultState.getName();

			result.putIfAbsent(key, new HashSet<EvaluationObject>());
			for (EntityAnnotation resultEntity : prediction.getState().getCurrentPrediction()
					.getEntityAnnotations()) {
				if (!resultEntity.getAnnotationInstance().equals(initializingObject))
					result.get(key).add(new EvaluationObject(resultEntity, investigationRestriction));
			}

			gold.putIfAbsent(key, new HashSet<EvaluationObject>());
			for (EntityAnnotation goldEntity : goldState.getEntityAnnotations()) {
				gold.get(key).add(new EvaluationObject(goldEntity, investigationRestriction));
			}

		}

		for (SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState> prediction : predictions) {
			OBIEState state = prediction.getState();
			InstanceEntityAnnotations goldState = state.getInstance().getGoldAnnotation();
			objectiveFunction.score(state, goldState);
		}
		EvaluationUtil
				.printPredictionPerformance(predictions.stream().map(p -> p.getState()).collect(Collectors.toList()));

		Set<OnlyTextEvaluationObject> goldSet = new HashSet<>();
		Set<OnlyTextEvaluationObject> predictionSet = new HashSet<>();

		int docIndex = 0;
		for (Entry<String, Set<EvaluationObject>> state : gold.entrySet()) {
			System.out.println("_____________" + state.getKey() + "______________");
			System.out.println(state.getKey());
			System.out.println("Gold:\t");
			System.out.println(gold.get(state.getKey()));
			System.out.println("Result:\t");
			System.out.println(result.get(state.getKey()));
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
			System.out.println("Doc-Precisiion = " + p);
			System.out.println("Doc-Recall = " + r);
			System.out.println("Doc-F1 = " + f1);

			goldSet.addAll(goldList);
			predictionSet.addAll(predictionList);
			docIndex++;
		}

		final double p = NamedEntityLinkingEvaluator.precision(goldSet, predictionSet);
		final double r = NamedEntityLinkingEvaluator.recall(goldSet, predictionSet);
		final double f1 = NamedEntityLinkingEvaluator.f1(goldSet, predictionSet);

		System.out.println("Micro-Precisiion = " + p);
		System.out.println("Micro-Recall = " + r);
		System.out.println("Micro-F1 = " + f1);

	}

	public static PRF1Container evaluateREPredictions(
			ObjectiveFunction<OBIEState, InstanceEntityAnnotations> objectiveFunction,
			List<SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>> predictions, IEvaluator evaluator) {
		Map<String, Set<EvaluationObject>> gold = new HashMap<>();
		Map<String, Set<EvaluationObject>> result = new HashMap<>();

		for (SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState> prediction : predictions) {

			OBIEInstance resultState = prediction.getInstance();
			InstanceEntityAnnotations goldState = prediction.getGoldResult();

			final String key = resultState.getName();

			result.putIfAbsent(key, new HashSet<EvaluationObject>());
			for (EntityAnnotation resultEntity : prediction.getState().getCurrentPrediction()
					.getEntityAnnotations()) {
				if (!resultEntity.getAnnotationInstance().equals(resultEntity.getInitializationClass()))
					result.get(key).add(new EvaluationObject(resultEntity, evaluator.getInvestigationRestrictions()));
			}

			gold.putIfAbsent(key, new HashSet<EvaluationObject>());
			for (EntityAnnotation goldEntity : goldState.getEntityAnnotations()) {
				gold.get(key).add(new EvaluationObject(goldEntity, evaluator.getInvestigationRestrictions()));
			}

		}

		for (SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState> prediction : predictions) {
			OBIEState state = prediction.getState();
			InstanceEntityAnnotations goldState = state.getInstance().getGoldAnnotation();
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
			System.out.println("_____________" + state.getKey() + "______________");
			System.out.println(state.getKey());
			System.out.println("Gold:\t");
			System.out.println(gold.get(state.getKey()));
			System.out.println("Result:\t");
			System.out.println(result.get(state.getKey()));

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
			System.out.println("Doc-Precisiion = " + p);
			System.out.println("Doc-Recall = " + r);
			System.out.println("Doc-F1 = " + f1);

			meanP += p;
			meanR += r;
			// meanF1 += f1;

		}
		System.out.println();
		System.out.println();

		PRF1 x = new PRF1(TP, FP, FN);

		meanP /= gold.entrySet().size();
		meanR /= gold.entrySet().size();
		// meanF1 /= gold.entrySet().size();

		System.out.println("MICRO: Mean-Precisiion = " + x.getPrecision());
		System.out.println("MICRO: Mean-Recall = " + x.getRecall());
		System.out.println("MICRO: Mean-F1 = " + x.getF1());

		System.out.println("MACRO: Mean-Precisiion = " + meanP);
		System.out.println("MACRO: Mean-Recall = " + meanR);
		// System.out.println("MACRO: Mean-F1 = " + meanF1);
		System.out.println("MACRO: Mean-F1 = " + (2 * meanP * meanR) / (meanP + meanR));
		return new PRF1Container(meanP, meanR, (2 * meanP * meanR) / (meanP + meanR));
	}

	public static double evaluatePurityPredictions(ObjectiveFunction<OBIEState, InstanceEntityAnnotations> objectiveFunction,
			List<SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>> predictions, IEvaluator evaluator,
			InvestigationRestriction investigationRestriction) {
		Map<String, Set<EvaluationObject>> gold = new HashMap<>();
		Map<String, Set<EvaluationObject>> result = new HashMap<>();

		for (SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState> prediction : predictions) {

			OBIEInstance resultState = prediction.getInstance();
			InstanceEntityAnnotations goldState = prediction.getGoldResult();

			final String key = resultState.getName();

			result.putIfAbsent(key, new HashSet<EvaluationObject>());
			for (EntityAnnotation resultEntity : prediction.getState().getCurrentPrediction()
					.getEntityAnnotations()) {
				if (!resultEntity.getAnnotationInstance().equals(resultEntity.getInitializationClass()))
					result.get(key).add(new EvaluationObject(resultEntity, investigationRestriction));
			}

			gold.putIfAbsent(key, new HashSet<EvaluationObject>());
			for (EntityAnnotation goldEntity : goldState.getEntityAnnotations()) {
				gold.get(key).add(new EvaluationObject(goldEntity, investigationRestriction));
			}

		}

		for (SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState> prediction : predictions) {
			OBIEState state = prediction.getState();
			InstanceEntityAnnotations goldState = state.getInstance().getGoldAnnotation();
			objectiveFunction.score(state, goldState);
		}
		EvaluationUtil
				.printPredictionPerformance(predictions.stream().map(p -> p.getState()).collect(Collectors.toList()));

		double meanF1 = 0;

		for (Entry<String, Set<EvaluationObject>> state : gold.entrySet()) {
			System.out.println("_____________" + state.getKey() + "______________");
			System.out.println(state.getKey());
			System.out.println("Gold:\t");
			System.out.println(gold.get(state.getKey()));
			System.out.println("Result:\t");
			System.out.println(result.get(state.getKey()));

			List<IOBIEThing> goldList = gold.get(state.getKey()).stream().map(s -> (s.scioClass))
					.collect(Collectors.toList());

			List<IOBIEThing> predictionList = result.get(state.getKey()).stream().map(s -> s.scioClass)
					.collect(Collectors.toList());

			final double f1 = evaluator.f1(goldList, predictionList);
			System.out.println("Doc-F1 = " + f1);

			meanF1 += f1;

		}
		System.out.println();
		System.out.println();

		// PRF1ScoreContainer x = new PRF1ScoreContainer(TP, FP, FN);

		// meanP /= gold.entrySet().size();
		// meanR /= gold.entrySet().size();
		meanF1 /= gold.entrySet().size();

		System.out.println("Purity-InversePurity F_Rij = " + meanF1);

		return meanF1;
	}

	public static void evaluatePerSlotPredictions(ObjectiveFunction<OBIEState, InstanceEntityAnnotations> objectiveFunction,
			List<SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>> predictions,
			AbstractOBIEEvaluator evaluator, InvestigationRestriction investigationRestriction) {

		Map<String, Set<EvaluationObject>> gold = new HashMap<>();
		Map<String, Set<EvaluationObject>> result = new HashMap<>();

		for (SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState> prediction : predictions) {

			OBIEInstance resultState = prediction.getInstance();
			InstanceEntityAnnotations goldState = prediction.getGoldResult();

			final String key = resultState.getName();

			result.putIfAbsent(key, new HashSet<EvaluationObject>());
			for (EntityAnnotation resultEntity : prediction.getState().getCurrentPrediction()
					.getEntityAnnotations()) {
				if (!resultEntity.getAnnotationInstance().equals(resultEntity.getInitializationClass()))
					result.get(key).add(new EvaluationObject(resultEntity, investigationRestriction));
			}

			gold.putIfAbsent(key, new HashSet<EvaluationObject>());
			for (EntityAnnotation goldEntity : goldState.getEntityAnnotations()) {
				gold.get(key).add(new EvaluationObject(goldEntity, investigationRestriction));
			}

		}

		for (SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState> prediction : predictions) {
			OBIEState state = prediction.getState();
			InstanceEntityAnnotations goldState = state.getInstance().getGoldAnnotation();
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

		for (Set<RestrictedField> set : restrictFieldsList) {
			final int a = set.size() == 2 ? 2 : 3;
			for (int i = 1; i < a; i++) {

				evaluator = new CartesianSearchEvaluator(true, evaluator.getMaxEvaluationDepth(),
						evaluator.isPenalizeCardinality(), new InvestigationRestriction(classType, set, i % 2 == 0),
						evaluator.getOrListCondition(), 7, evaluator.isIgnoreEmptyInstancesOnEvaluation());
				System.out.println("#############################");
				System.out.println(evaluator.getInvestigationRestrictions());
				System.out.println("#############################");

				double meanP = 0;
				double meanR = 0;
				double meanF1 = 0;
				for (Entry<String, Set<EvaluationObject>> state : gold.entrySet()) {
					System.out.println("_____________" + state.getKey() + "______________");
					System.out.println(state.getKey());
					System.out.println("Gold:\t");
					System.out.println(gold.get(state.getKey()));
					System.out.println("Result:\t");
					System.out.println(result.get(state.getKey()));

					List<IOBIEThing> goldList = gold.get(state.getKey()).stream().map(s -> (s.scioClass))
							.collect(Collectors.toList());

					List<IOBIEThing> predictionList = result.get(state.getKey()).stream().map(s -> s.scioClass)
							.collect(Collectors.toList());

					final double p = evaluator.precision(goldList, predictionList);
					final double r = evaluator.recall(goldList, predictionList);
					final double f1 = evaluator.f1(goldList, predictionList);
					System.out.println("Doc-Precisiion = " + p);
					System.out.println("Doc-Recall = " + r);
					System.out.println("Doc-F1 = " + f1);

					meanP += p;
					meanR += r;
					meanF1 += f1;

				}

				System.out.println();
				System.out.println();
				System.out.println("#############################");
				System.out.println(evaluator.getInvestigationRestrictions());
				System.out.println("#############################");
				meanP /= gold.entrySet().size();
				meanR /= gold.entrySet().size();
				meanF1 /= gold.entrySet().size();
				System.out.println("Mean-Precisiion = " + meanP);
				System.out.println("Mean-Recall = " + meanR);
				System.out.println("Mean-F1 = " + meanF1);

			}
		}
	}

}
