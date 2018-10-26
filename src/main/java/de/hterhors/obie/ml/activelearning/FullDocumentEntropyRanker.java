package de.hterhors.obie.ml.activelearning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.ml.run.AbstractRunner;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;

public class FullDocumentEntropyRanker implements IActiveLearningDocumentRanker {

	private static final int N = 200;

	final Logger log = LogManager.getRootLogger();

	final private AbstractRunner runner;

	public FullDocumentEntropyRanker(AbstractRunner runner) {
		this.runner = runner;
	}

	@Override
	public List<OBIEInstance> rank(List<OBIEInstance> remainingInstances) {

		List<RankedInstance> entropyInstances = new ArrayList<>();
//		List<RankedInstance> objectiveInstances = new ArrayList<>();

		log.info("Compute variations for entropy...");
		Map<OBIEInstance, List<OBIEState>> results = runner.collectBestNStates(remainingInstances, N);

		log.info("Compute entropy...");
		for (Entry<OBIEInstance, List<OBIEState>> predictedInstance : results.entrySet()) {

			final double partition = predictedInstance.getValue().stream().map(s -> s.getModelScore()).reduce(0D,
					Double::sum);

			double entropy = 0;

			/*
			 * Compute entropy of state
			 */
			for (OBIEState obieState : predictedInstance.getValue()) {

				final double modelProbability = obieState.getModelScore() / partition;
				entropy -= modelProbability * Math.log(modelProbability);

			}

			final double maxEntropy = Math.log(predictedInstance.getValue().size());

//			runner.scoreWithObjectiveFunction(predictedInstance.getValue().get(0));

			/*
			 * Normalize by length
			 */
			entropy /= maxEntropy;

			entropyInstances.add(new RankedInstance(entropy, predictedInstance.getKey()));
//			final double inverseObjectiveRank = 1 - predictedInstance.getValue().get(0).getObjectiveScore();
//			objectiveInstances.add(new RankedInstance(inverseObjectiveRank, predictedInstance.getKey()));

		}
		log.info("Sort...");

		Collections.sort(entropyInstances);
//		Collections.sort(objectiveInstances);

//		SpearmansCorrelation s = new SpearmansCorrelation();
//
//		double[] entropy = entropyInstances.stream().map(i -> (double) i.instance.getName().hashCode())
//				.mapToDouble(Double::doubleValue).toArray();
//		double[] objective = objectiveInstances.stream().map(i -> (double) i.instance.getName().hashCode())
//				.mapToDouble(Double::doubleValue).toArray();
//
//		double correlation = s.correlation(entropy, objective);
//
//		final double meanObjectiveF1Score = objectiveInstances.stream().map(i -> i.value).reduce(0D, Double::sum)
//				/ objectiveInstances.size();
//
//		log.info("Spearmans Correlation: " + correlation);
//		log.info("Mean F1 score (Objective): " + meanObjectiveF1Score);

		logStats(entropyInstances, "entropy");
//		logStats(objectiveInstances, "inverse-objective");

		return entropyInstances.stream().map(e -> e.instance).collect(Collectors.toList());
	}

	final int n = 5;

	private void logStats(List<RankedInstance> entropyInstances, String context) {

		log.info("Next " + n + " " + context + ":");
		entropyInstances.stream().limit(n).forEach(i -> log.info(i.instance.getName() + ":" + i.value));

	}

}
