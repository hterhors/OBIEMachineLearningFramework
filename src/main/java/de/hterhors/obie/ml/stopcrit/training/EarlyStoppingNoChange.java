package de.hterhors.obie.ml.stopcrit.training;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EarlyStoppingNoChange implements IStopTrainingCriterion {

	public static Logger log = LogManager.getFormatterLogger(EarlyStoppingNoChange.class.getSimpleName());

	private static final int MIN_EPOCH_TRAINING = 20;
	public List<Double> prevMeans = new ArrayList<>();
	double threshold = 0.001;
	protected static final int TIMES_SAME_EVALUATION_SCORE = 15;

	/**
	 * The best epoch is determined by minimum epoch and minimum error.
	 */
	public int bestEpoch = 0;

	private double bestPerformance = 0;

	@Override
	public boolean checkConditions(final double mean) throws InterruptedException {
		log.info("Previous run performance, mean values = ");
		prevMeans.forEach(log::info);
		log.info("Current epoch = " + (prevMeans.size() + 1));
		log.info("Current run performance, mean value = " + mean);

		breakTraining: {
			if (prevMeans.size() >= MIN_EPOCH_TRAINING) {

				for (int i = prevMeans.size() - 1; i >= prevMeans.size() - TIMES_SAME_EVALUATION_SCORE; i--) {

					final double prevMean = prevMeans.get(i);
					if (Math.abs(mean - prevMean) <= threshold) {
					} else {
						break breakTraining;
					}
				}
				log.info("Stop training due to no change in evaluation results for " + TIMES_SAME_EVALUATION_SCORE
						+ " times.");
				log.info("Best epoch = " + bestEpoch);
				Thread.sleep(5000);
				return true;
			}

		}

		if (mean > bestPerformance) {
			bestEpoch = prevMeans.size();
			bestPerformance = mean;
		}

		prevMeans.add(mean);

		log.info("Best epoch: " + bestEpoch + " with performance: " + bestPerformance);

		Thread.sleep(1000);
		return false;
	}

}
