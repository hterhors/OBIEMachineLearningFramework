package de.uni.bielefeld.sc.hterhors.psink.obie.ie.run;

import java.util.Arrays;
import java.util.List;

import de.uni.bielefeld.sc.hterhors.psink.obie.ie.objfunc.REObjectiveFunction;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.InstanceEntityAnnotations;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;
import learning.ObjectiveFunction;
import learning.Trainer;
import learning.Trainer.EpochCallback;
import sampling.DefaultSampler;

public class StandardRERunner extends AbstractOBIERunner {

	public StandardRERunner(OBIERunParameter parameter) {
		super(parameter);
	}

	@Override
	public ObjectiveFunction<OBIEState, InstanceEntityAnnotations> getObjectiveFunction() {
		return new REObjectiveFunction(parameter);
	}

	@Override
	protected List<EpochCallback> addEpochCallback(
			DefaultSampler<OBIEInstance, OBIEState, InstanceEntityAnnotations> sampler) {
		return Arrays.asList(
				//
				new EpochCallback() {
					@Override
					public void onStartEpoch(Trainer caller, int epoch, int numberOfEpochs, int numberOfInstances) {
						try {
							if (epoch == 1 || Math.random() >= 0.9) {
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
