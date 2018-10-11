package de.hterhors.obie.ml.run;

import java.util.Arrays;
import java.util.List;

import de.hterhors.obie.ml.corpus.distributor.ActiveLearningDistributor;
import de.hterhors.obie.ml.objfunc.REObjectiveFunction;
import de.hterhors.obie.ml.run.param.OBIERunParameter;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import learning.ObjectiveFunction;
import learning.Trainer;
import learning.Trainer.EpochCallback;
import sampling.DefaultSampler;

public class StandardRERunner extends AbstractOBIERunner {

	public StandardRERunner(OBIERunParameter parameter) {
		super(parameter);
	}

	@Override
	public ObjectiveFunction<OBIEState, InstanceTemplateAnnotations> getObjectiveFunction() {
		return new REObjectiveFunction(parameter);
	}

	@Override
	protected List<EpochCallback> addEpochCallback(
			DefaultSampler<OBIEInstance, OBIEState, InstanceTemplateAnnotations> sampler) {
		return Arrays.asList(
				//
				new EpochCallback() {

					@Override
					public void onEndEpoch(Trainer caller, int epoch, int numberOfEpochs, int numberOfInstances) {

						if (!(parameter.corpusDistributor instanceof ActiveLearningDistributor)
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
							if (epoch != 2 && (epoch == 1 || Math.random() >= 0.9)) {
								log.info("Use Objective Score for sampling...");
								trainWithObjective = true;
								sampler.setTrainSamplingStrategy(OBIERunParameter.trainSamplingStrategyObjectiveScore);
								sampler.setTrainAcceptStrategy(OBIERunParameter.trainAcceptanceStrategyObjectiveScore);
							} else {
								trainWithObjective = false;
								log.info("Use Model Score for sampling...");
								sampler.setTrainSamplingStrategy(OBIERunParameter.trainSamplingStrategyModelScore);
								sampler.setTrainAcceptStrategy(OBIERunParameter.trainAcceptanceStrategyModelScore);
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
