package de.hterhors.obie.ml.activelearning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;

import corpus.SampledInstance;
import de.hterhors.obie.ml.corpus.distributor.ActiveLearningDistributor;
import de.hterhors.obie.ml.run.AbstractOBIERunner;
import de.hterhors.obie.ml.variables.InstanceEntityAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import learning.Trainer;
import sampling.Explorer;

public class FullDocumentEntropyRanker implements IActiveLearningDocumentRanker {

	final static class EntropyInstance implements Comparable<EntropyInstance> {

		protected final double entropy;
		protected final OBIEInstance instance;

		public EntropyInstance(double entropy, OBIEInstance instance) {
			this.entropy = entropy;
			this.instance = instance;
		}

		@Override
		public int compareTo(EntropyInstance o) {
			/*
			 * Highest entropy first
			 */
			return -Double.compare(entropy, o.entropy);
		}

		@Override
		public String toString() {
			return "EntropyInstance [entropy=" + entropy + ", instance=" + instance + "]";
		}

	}

	@Override
	public List<OBIEInstance> rank(ActiveLearningDistributor distributor, AbstractOBIERunner runner,
			List<OBIEInstance> remainingInstances) {

		List<EntropyInstance> entropyInstances = new ArrayList<>();

		Level trainerLevel = LogManager.getFormatterLogger(Trainer.class.getName()).getLevel();
		Level runnerLevel = LogManager.getFormatterLogger(AbstractOBIERunner.class).getLevel();

		Configurator.setLevel(Trainer.class.getName(), Level.FATAL);
		Configurator.setLevel(AbstractOBIERunner.class.getName(), Level.FATAL);

		List<SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>> results = runner
				.applyModelTo(remainingInstances);

		Configurator.setLevel(Trainer.class.getName(), trainerLevel);
		Configurator.setLevel(AbstractOBIERunner.class.getName(), runnerLevel);

		//
//		System.out.println("results:___________________________");
//
//		for (SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState> sampledInstance : results) {
//			System.out.println(sampledInstance.getGoldResult());
//			System.out.println("___");
//			System.out.println(sampledInstance.getInstance().getGoldAnnotation());
//			System.out.println("___");
//			System.out.println(sampledInstance.getInstance());
//			System.out.println("___");
//			System.out.println(sampledInstance.getState());
//			System.out.println("___");
//			System.out.println(sampledInstance.getState().getCurrentPrediction());
//			System.out.println("___");
//			System.out.println(sampledInstance.getState().getInstance());
//			System.out.println("_____________________");
//		}

		for (SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState> predictedInstance : results) {

			OBIEState initialState = new OBIEState(predictedInstance.getState());

//			System.out.println("___");
//			System.out.println("initialState: " + initialState);
//			System.out.println("___");
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
//			System.out.println("States: ");
//			nextStates.forEach(System.out::println);

			final double partition = nextStates.stream().map(s -> s.getModelScore()).reduce(0D, Double::sum);

//			System.out.println("partition = " + partition);
			double entropy = 0;

			/*
			 * Compute entropy of state
			 */
			for (OBIEState obieState : nextStates) {

				final double modelProbability = obieState.getModelScore() / partition;

				entropy -= modelProbability * Math.log(modelProbability);

			}

			entropyInstances.add(new EntropyInstance(entropy, predictedInstance.getInstance()));

		}

		Collections.sort(entropyInstances);

//		System.out.println("Entropy:");
//		entropyInstances.forEach(System.out::println);
//		System.exit(1);

		return entropyInstances.stream().map(e -> e.instance).collect(Collectors.toList());
	}

}
