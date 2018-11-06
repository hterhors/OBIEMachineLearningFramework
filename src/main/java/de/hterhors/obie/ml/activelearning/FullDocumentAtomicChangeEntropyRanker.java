package de.hterhors.obie.ml.activelearning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import corpus.SampledInstance;
import de.hterhors.obie.ml.run.AbstractRunner;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import sampling.Explorer;

public class FullDocumentAtomicChangeEntropyRanker implements IActiveLearningDocumentRanker {

	final Logger log = LogManager.getRootLogger();

	final private AbstractRunner runner;

	public FullDocumentAtomicChangeEntropyRanker(AbstractRunner runner) {
		this.runner = runner;
	}

	@Override
	public List<OBIEInstance> rank(List<OBIEInstance> remainingInstances) {

		List<RankedInstance> entropyInstances = new ArrayList<>();
//		List<RankedInstance> objectiveInstances = new ArrayList<>();

		log.info("Predict final states based on current model...");
		List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> results = runner
				.test(remainingInstances);

		log.info("Compute variations for entropy...");
		for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> predictedInstance : results) {
			OBIEState initialState = new OBIEState(predictedInstance.getState());
			List<OBIEState> nextStates = new ArrayList<>();

			/*
			 * Create possible changes using explorers.
			 */
			for (Explorer<OBIEState> explorer : runner.sampler.getExplorers()) {
				nextStates.addAll(explorer.getNextStates(initialState));
			}

			/*
			 * Score with model
			 */
			runner.scoreWithModel(nextStates);

//			runner.scoreWithObjectiveFunction(predictedInstance.getState());

			final double partition = nextStates.stream().map(s -> s.getModelScore()).reduce(0D, Double::sum);

			double entropy = 0;

			/*
			 * Compute entropy of state
			 */
			for (OBIEState obieState : nextStates) {

				final double modelProbability = obieState.getModelScore() / partition;
				entropy -= modelProbability * Math.log(modelProbability);

			}

			final double maxEntropy = Math.log(nextStates.size());

			/*
			 * Normalize by length
			 */
			entropy /= maxEntropy;

//			log.info("####FINAL STATE####");
//			log.info(initialState);
//			log.info("########");
//			nextStates.forEach(log::info);
//			log.info(initialState.getInstance().getName() + "\t" + nextStates.size() + "\t" + entropy);

//			log.info("___");
//			if(initialState.getInstance().getName().equals("Arvo_Kraam")) {
//				log.info("__");
//			}

			entropyInstances.add(new RankedInstance(entropy, predictedInstance.getInstance()));
//			final double inverseObjectiveRank = 1 - predictedInstance.getState().getObjectiveScore();
//			objectiveInstances.add(new RankedInstance(inverseObjectiveRank, predictedInstance.getInstance()));

		}
		log.info("Sort based on entropy...");

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

//		log.info("Spearmans Correlation: " + correlation);
//		log.info("Mean F1 score (Objective): " + meanObjectiveF1Score);

//		System.out.println("Entropy:");
//		entropyInstances.forEach(System.out::println);
//		System.exit(1);

//		logStats(entropyInstances, "entropy");
//		logStats(objectiveInstances, "inverse-objective");

		return entropyInstances.stream().map(e -> e.instance).collect(Collectors.toList());
	}
//
//	final int n = 5;
//
//	private void logStats(List<RankedInstance> entropyInstances, String context) {
//
//		log.info("Next " + n + " " + context + ":");
//		entropyInstances.stream().limit(n).forEach(i -> log.info(i.instance.getName() + ":" + i.value));
//
//	}

}
