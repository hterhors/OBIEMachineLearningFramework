package de.hterhors.obie.ml.run;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import de.hterhors.obie.ml.corpus.BigramCorpusProvider;
import de.hterhors.obie.ml.corpus.distributor.ActiveLearningDistributor;
import de.hterhors.obie.ml.corpus.distributor.FoldCrossCorpusDistributor;
import de.hterhors.obie.ml.objfunc.REObjectiveFunction;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import learning.ObjectiveFunction;
import learning.Trainer;
import learning.Trainer.EpochCallback;
import sampling.DefaultSampler;

public class DefaultSlotFillingRunner extends AbstractOBIERunner {

	private final Random random;
	private final Set<Integer> epochsTrainedWithObjective = new HashSet<>();
	private final Set<Integer> epochsTrainedGreedily = new HashSet<>();

	private final boolean callGC;

	public DefaultSlotFillingRunner(RunParameter parameter, boolean callGC) {
		super(parameter);
		this.callGC = callGC;

		log.info("Initialize sampling settings...");

		this.random = new Random(100L);

		for (int epoch = 1; epoch <= parameter.epochs; epoch++) {
			if (epoch != 2 && (epoch == 1 || this.random.nextDouble() >= 0.9))
				epochsTrainedWithObjective.add(epoch);
		}
		for (int epoch = 1; epoch <= parameter.epochs; epoch++) {
			if (epoch != 2 && (epoch == 1 || this.random.nextDouble() >= 0.5))
				epochsTrainedGreedily.add(epoch);
		}
		log.info("Epochs trained with objective score: " + epochsTrainedWithObjective);
		log.info("Epochs trained greedily: " + epochsTrainedGreedily);
	}

	public DefaultSlotFillingRunner(RunParameter parameter) {
		this(parameter, true);
	}

	@Override
	public ObjectiveFunction<OBIEState, InstanceTemplateAnnotations> getObjectiveFunction() {
		return new REObjectiveFunction(getParameter());
	}

	@Override
	protected List<EpochCallback> addEpochCallbackOnTrain(
			DefaultSampler<OBIEInstance, OBIEState, InstanceTemplateAnnotations> sampler) {
		return Arrays.asList(
				//
				new EpochCallback() {

					@Override
					public void onEndEpoch(Trainer caller, int epoch, int numberOfEpochs, int numberOfInstances) {

//						if (numberOfEpochs == epoch) {
						if (!(getParameter().corpusDistributor instanceof ActiveLearningDistributor
								|| getParameter().corpusDistributor instanceof FoldCrossCorpusDistributor)
								|| numberOfEpochs == epoch) {

							saveModel(epoch);
							trainWithObjective = false;
						}
						if (callGC) {
							log.info("Call GC manually...");
							System.gc();
						}
					}

				},
				//
				new EpochCallback() {

					@Override
					public void onEndEpoch(Trainer caller, int epoch, int numberOfEpochs, int numberOfInstances) {
//						log.info(OBIEState.dropOutInvestigation.size());
//						OBIEState.dropOutInvestigation.entrySet().forEach(log::info);
						OBIEState.dropOutInstanceCache.clear();
					}

				},
				//
				new EpochCallback() {
					@Override
					public void onStartEpoch(Trainer caller, int epoch, int numberOfEpochs, int numberOfInstances) {
						try {
							trainWithObjective = epochsTrainedWithObjective.contains(epoch);
							sampleGreedy = true;// epochsTrainedGreedily.contains(epoch);

							if (trainWithObjective) {
								if (sampleGreedy) {
									log.info("Use objective score and greedy sampling...");
									sampler.setTrainSamplingStrategy(
											RunParameter.greedyTrainSamplingStrategyObjectiveScore);
								} else {
									log.info("Use objective score and probability sampling...");
									sampler.setTrainSamplingStrategy(
											RunParameter.linearTrainSamplingStrategyObjectiveScore);
								}
								sampler.setTrainAcceptStrategy(RunParameter.trainAcceptanceStrategyObjectiveScore);
							} else {
								if (sampleGreedy) {
									log.info("Use model score and greedy sampling...");
									sampler.setTrainSamplingStrategy(
											RunParameter.greedyTrainSamplingStrategyModelScore);
								} else {
									log.info("Use model score and probability sampling...");
									sampler.setTrainSamplingStrategy(
											RunParameter.linearTrainSamplingStrategyModelScore);
								}
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
