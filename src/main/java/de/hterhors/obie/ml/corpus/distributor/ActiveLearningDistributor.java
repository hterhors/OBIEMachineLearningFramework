package de.hterhors.obie.ml.corpus.distributor;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.ml.corpus.BigramCorpusProvider;
import de.hterhors.obie.ml.corpus.distributor.ShuffleCorpusDistributor.Builder;
import de.hterhors.obie.ml.variables.OBIEInstance;

/**
 * Based on the algorithm of David D Lewis et al. explained in "A Sequential
 * Algorithm for Training Text Classifiers"
 * 
 * 1. Create an initial classifier
 * 
 *
 * 2. While teacher is willing to label examples
 * 
 * (a) Apply the current classifier to each unlabeled example
 * 
 * (b) Find the b examples for which the classifier is least certain of class
 * membership
 * 
 * (c) Have the teacher label the subsample of b examples
 * 
 * (d) Train a new classifier on all labeled examples
 * 
 * 
 * 
 * @author hterhors
 * 
 *
 * @date May 16, 2018
 */
public class ActiveLearningDistributor extends AbstractCorpusDistributor {

	protected static Logger log = LogManager.getRootLogger();

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Shuffle of data sets from the training data.
	 */
	public final Random random;

	/**
	 * The fraction of the training data to start with.
	 */
	public final float initialTrainingSelectionFraction;

	/**
	 * The proportion of the training data.
	 */
	public final int trainingProportion;

	/**
	 * The proportion of the test data.
	 */
	public final int testProportion;

	/**
	 * The number of new training data per active learning step.
	 */
	public final int b;

	private ActiveLearningDistributor(float corpusSizeFraction, long initializationSelectionSeed,
			float initialTrainingSelectionFraction, int b, final int trainingProportion, final int testProportion) {
		super(corpusSizeFraction);

		this.random = new Random(initializationSelectionSeed);
		this.initialTrainingSelectionFraction = initialTrainingSelectionFraction;
		this.trainingProportion = trainingProportion;
		this.testProportion = testProportion;
		this.b = b;

	}

	private int totalProportion() {
		return trainingProportion + testProportion;
	}

	public int numberOfTotalTrainingData(final int totalNumberOfDocuments) {
		return Math.round(
				corpusSizeFraction * ((float) trainingProportion / (float) totalProportion()) * totalNumberOfDocuments);
	}

	public int numberOfTestData(final int totalNumberOfDocuments) {
		return Math.round(
				corpusSizeFraction * ((float) testProportion / (float) totalProportion()) * totalNumberOfDocuments);
	}

	public static class Builder extends AbstractConfigBuilder<Builder> {

		/**
		 * Selection of data sets from the training data.
		 */
		private long initializationSelectionSeed = 100L;

		/**
		 * The fraction of the training data to start with.
		 */
		private float initialTrainingSelectionFraction = 1 / 10;

		/**
		 * The number of new training data per active learning step.
		 */
		private int b = 1;

		/**
		 * The proportion of the training data.
		 */
		private int trainingProportion = 80;

		/**
		 * The proportion of the test data.
		 */
		private int testProportion = 20;

		/**
		 * @return the initializationSelectionSeed
		 */
		public long getInitializationSelectionSeed() {
			return initializationSelectionSeed;
		}

		/**
		 * @param initializationSelectionSeed the initializationSelectionSeed to set
		 * @return
		 */
		public Builder setSeed(long initializationSelectionSeed) {
			this.initializationSelectionSeed = initializationSelectionSeed;
			return this;

		}

		/**
		 * @return the initialTrainingSelectionFraction
		 */
		public double getInitialTrainingSelectionFraction() {
			return initialTrainingSelectionFraction;
		}

		/**
		 * @param initialTrainingSelectionFraction the initialTrainingSelectionFraction
		 *                                         to set
		 * @return
		 */
		public Builder setInitialTrainingSelectionFraction(float initialTrainingSelectionFraction) {
			this.initialTrainingSelectionFraction = initialTrainingSelectionFraction;
			return this;

		}

		/**
		 * @return the b
		 */
		public int getB() {
			return b;
		}

		/**
		 * @param b the b to set
		 * @return
		 */
		public Builder setB(int b) {
			this.b = b;
			return this;
		}

		public int getTrainingProportion() {
			return trainingProportion;
		}

		public Builder setTrainingProportion(int trainingProportion) {
			this.trainingProportion = trainingProportion;
			return this;
		}

		public int getTestProportion() {
			return testProportion;
		}

		public Builder setTestProportion(int testProportion) {
			this.testProportion = testProportion;
			return this;
		}

		@Override
		public ActiveLearningDistributor build() {
			return new ActiveLearningDistributor(corpusSizeFraction, initializationSelectionSeed,
					initialTrainingSelectionFraction, b, trainingProportion, testProportion);
		};

		@Override
		protected Builder getDistributor() {
			return this;
		}
	}

	@Override
	public Distributor distributeInstances(BigramCorpusProvider corpusProvider) {
		/**
		 * TODO: If development is given as input than take this as initial if desired.
		 */
		Collections.shuffle(corpusProvider.allExistingInternalInstances, random);

		final int totalNumberOfDocuments = Math.round(corpusProvider.allExistingInternalInstances.size());

		final int numberForTraining = numberOfTotalTrainingData(totalNumberOfDocuments);
		final int numberForTest = numberOfTestData(totalNumberOfDocuments);

		if (Math.round(corpusSizeFraction * (numberForTraining + numberForTest)) != Math
				.round(corpusSizeFraction * corpusProvider.allExistingInternalInstances.size()))
			log.warn("WARN!!! Could not redistribute data accordingly! Change number of documents for data from "
					+ Math.round(corpusSizeFraction * (numberForTraining + numberForTest)) + " to "
					+ Math.round(corpusSizeFraction * corpusProvider.allExistingInternalInstances.size()) + "!");

		final int trainIndex = Math.max(1, (int) Math.round(initialTrainingSelectionFraction * numberForTraining));

		return new Distributor() {

			@Override
			public Distributor distributeTrainingInstances(List<OBIEInstance> trainingDocuments) {
				trainingDocuments.addAll(corpusProvider.allExistingInternalInstances.subList(0, trainIndex));
				return this;
			}

			@Override
			public Distributor distributeDevelopmentInstances(List<OBIEInstance> developmentDocuments) {
				developmentDocuments
						.addAll(corpusProvider.allExistingInternalInstances.subList(trainIndex, numberForTraining));
				return this;
			}

			@Override
			public Distributor distributeTestInstances(List<OBIEInstance> testDocuments) {
				testDocuments.addAll(corpusProvider.allExistingInternalInstances.subList(numberForTraining,
						Math.round(corpusSizeFraction * corpusProvider.allExistingInternalInstances.size())));
				return this;
			}
		};
	}

	@Override
	public String getDistributorID() {
		return "ActiveLearning";
	}

}
