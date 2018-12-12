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
import de.hterhors.obie.ml.run.AbstractRunner;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import learning.Trainer;
import sampling.Explorer;

public class FullDocumentVarianceRanker implements IActiveLearningDocumentRanker {

	final Logger log = LogManager.getRootLogger();
	final private AbstractRunner runner;

	public FullDocumentVarianceRanker(AbstractRunner runner) {
		this.runner = runner;
	}

	@Override
	public List<OBIEInstance> rank(List<OBIEInstance> remainingInstances) {

		List<RankedInstance> entropyInstances = new ArrayList<>();

		List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> results = runner
				.test(remainingInstances);

		for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> predictedInstance : results) {
//			Fred_Beardsley,Packie_Bonner
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

			final double mean = nextStates.stream().map(s -> s.getModelScore()).reduce(0D, Double::sum)
					/ nextStates.size();
			final double variance = Math
					.sqrt(nextStates.stream().map(s -> Math.pow(mean - s.getModelScore(), 2)).reduce(0D, Double::sum)
							/ nextStates.size());

			entropyInstances.add(new RankedInstance(-variance, predictedInstance.getInstance()));

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
		entropyInstances.stream().limit(n).forEach(i -> log.info(i + ":" + i.value));

	}

}
