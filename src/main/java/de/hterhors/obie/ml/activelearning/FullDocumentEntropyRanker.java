package de.hterhors.obie.ml.activelearning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import corpus.SampledInstance;
import de.hterhors.obie.ml.corpus.distributor.ActiveLearningDistributor;
import de.hterhors.obie.ml.run.AbstractOBIERunner;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import learning.Trainer;
import sampling.Explorer;

public class FullDocumentEntropyRanker implements IActiveLearningDocumentRanker {

	final Logger log = LogManager.getRootLogger();

	@Override
	public List<OBIEInstance> rank(ActiveLearningDistributor distributor, AbstractOBIERunner runner,
			List<OBIEInstance> remainingInstances) {

		List<RankedInstance> entropyInstances = new ArrayList<>();

		Level trainerLevel = LogManager.getFormatterLogger(Trainer.class.getName()).getLevel();
		Level runnerLevel = LogManager.getFormatterLogger(AbstractOBIERunner.class).getLevel();

		Configurator.setLevel(Trainer.class.getName(), Level.FATAL);
		Configurator.setLevel(AbstractOBIERunner.class.getName(), Level.FATAL);

		List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> results = runner
				.test(remainingInstances);

		Configurator.setLevel(Trainer.class.getName(), trainerLevel);
		Configurator.setLevel(AbstractOBIERunner.class.getName(), runnerLevel);

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

			entropyInstances.add(new RankedInstance(entropy, predictedInstance.getInstance()));

		}

		Collections.sort(entropyInstances);

//		System.out.println("Entropy:");
//		entropyInstances.forEach(System.out::println);
//		System.exit(1);

		logStats(entropyInstances);

		return entropyInstances.stream().map(e -> e.instance).collect(Collectors.toList());
	}

	final int n = 5;

	private void logStats(List<RankedInstance> entropyInstances) {

		log.info("Next " + n + " entropies:");
		entropyInstances.stream().limit(n).forEach(i -> log.info(i + ":" + i.entropy));

	}

}
