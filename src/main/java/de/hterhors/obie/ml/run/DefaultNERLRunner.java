package de.hterhors.obie.ml.run;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import de.hterhors.obie.ml.corpus.distributor.ActiveLearningDistributor;
import de.hterhors.obie.ml.objfunc.NERLObjectiveFunction;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import learning.ObjectiveFunction;
import learning.Trainer;
import learning.Trainer.EpochCallback;
import sampling.DefaultSampler;

public class DefaultNERLRunner extends AbstractRunner {

	private final Random random;
	private final Set<Integer> epochsTrainedWithObjective = new HashSet<>();

	public DefaultNERLRunner(RunParameter parameter) {
		super(parameter);
		/*
		 * SCIONERRunner TODO: parameterize ?
		 */
		this.random = new Random(100L);
		for (int epoch = 1; epoch <= parameter.epochs; epoch++) {
			if (epoch != 2 && (epoch == 1 || this.random.nextDouble() >= 0.9))
				epochsTrainedWithObjective.add(epoch);
		}

	}

	@Override
	public ObjectiveFunction<OBIEState, InstanceTemplateAnnotations> getObjectiveFunction() {
		return new NERLObjectiveFunction(getParameter());
	}

	@Override
	protected List<EpochCallback> addEpochCallbackOnTrain(
			DefaultSampler<OBIEInstance, OBIEState, InstanceTemplateAnnotations> sampler) {
		return Arrays.asList(
				//
				new EpochCallback() {

					@Override
					public void onEndEpoch(Trainer caller, int epoch, int numberOfEpochs, int numberOfInstances) {

						if (!(getParameter().corpusDistributor instanceof ActiveLearningDistributor)
								|| numberOfEpochs == epoch) {

							saveModel(epoch);
							trainWithObjective = false;
						}
						log.info("Call GC manually...");
						System.gc();
					}

				},
				//
				new EpochCallback() {
					@Override
					public void onStartEpoch(Trainer caller, int epoch, int numberOfEpochs, int numberOfInstances) {
						try {
							if (epochsTrainedWithObjective.contains(epoch)) {
								log.info("Use Objective Score for sampling...");
								trainWithObjective = true;
								sampler.setTrainSamplingStrategy(RunParameter.linearTrainSamplingStrategyObjectiveScore);
								sampler.setTrainAcceptStrategy(RunParameter.trainAcceptanceStrategyObjectiveScore);
							} else {
								trainWithObjective = false;
								log.info("Use Model Score for sampling...");
								sampler.setTrainSamplingStrategy(RunParameter.linearTrainSamplingStrategyModelScore);
								sampler.setTrainAcceptStrategy(RunParameter.trainAcceptanceStrategyModelScore);
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				} // ,
		// new EpochCallback() {
		// @Override
		// public void onEndEpoch(Trainer caller, int epoch, int numberOfEpochs,
		// int numberOfInstances) {
		// try {
		// try {
		// if
		// (corpusProvider.getDevelopCorpus().getInternalInstances().isEmpty())
		// {
		// log.warn("+++++++++++++++++++++++++++++++++++");
		// log.warn(
		// "WARN! No development data available. Can not check early stopping
		// criterion!");
		// log.warn("+++++++++++++++++++++++++++++++++++");
		// return;
		// }
		// } catch (RuntimeException e) {
		// log.warn("+++++++++++++++++++++++++++++++++++");
		// log.warn("Can not perform end epoch evaluation. No Development set
		// available.");
		// log.warn("+++++++++++++++++++++++++++++++++++");
		// return;
		// }
		// PRF1Container prf1 =
		// PredictionEvaluator.evaluateREPredictions(getObjectiveFunction(),
		// predictOnDev(), parameter.evaluator,
		// parameter.investigationRestriction);
		//
		// caller.stopTraining = earlyStopping.checkConditions(prf1.f1);
		// PrintStream ps = new PrintStream(new FileOutputStream(new
		// File("scio/dev_results"), true));
		// ps.println(prf1.toString());
		// ps.close();
		//
		// } catch (Exception e) {
		// e.printStackTrace();
		// }
		// }
		//
		// }
		);
	}

}
