package de.hterhors.obie.ml.corpus.distributor;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.ml.corpus.BigramCorpusProvider;
import de.hterhors.obie.ml.corpus.distributor.ActiveLearningDistributor.Builder.EMode;
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
	private final Random random;

	/**
	 * The fraction of the training data to start with.
	 */
	private final float initialTrainingSelectionFraction;

	/**
	 * The proportion of the training data.
	 */
	private final int trainingProportion;

	/**
	 * The proportion of the test data.
	 */
	private final int testProportion;

	final private int bAbsolute;
	final private float bPercentage;
	final EMode mode;

	private ActiveLearningDistributor(float corpusSizeFraction, long initializationSelectionSeed,
			float initialTrainingSelectionFraction, int bAbsolute, float bPercentage, final int trainingProportion,
			final int testProportion, final EMode mode) {
		super(corpusSizeFraction);

		Objects.requireNonNull(mode);

		this.random = new Random(initializationSelectionSeed);
		this.initialTrainingSelectionFraction = initialTrainingSelectionFraction;
		this.trainingProportion = trainingProportion;
		this.testProportion = testProportion;
		this.mode = mode;
		this.bPercentage = bPercentage;
		this.bAbsolute = bAbsolute;

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

		public static enum EMode {
			PERCENTAGE, ABSOLUT;
		}

		/**
		 * Selection of data sets from the training data.
		 */
		private long initializationSelectionSeed = new Random().nextLong();

		/**
		 * The fraction of the training data to start with.
		 */
		private float initialTrainingSelectionFraction = 1 / 10;

		/**
		 * The number of new training data per active learning step.
		 */
		private int bAbsolute = 1;

		/**
		 * The number of new training data per active learning step.
		 */
		private float bPercentage = 1 / 10;

		/**
		 * The proportion of the training data.
		 */
		private int trainingProportion = 80;

		/**
		 * The proportion of the test data.
		 */
		private int testProportion = 20;

		private EMode mode;

		/**
		 */
		public EMode getMode() {
			return mode;
		}

		/**
		 * The mode of whether the number of training data added in each iteration
		 * should be based on percentage or absolute.
		 * 
		 * @param mode PERCENTAGE or ABSOLUTE
		 * @return
		 */
		public Builder setMode(EMode mode) {
			this.mode = mode;
			return this;
		}

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
		public float getBPercentage() {
			return bPercentage;
		}

		/**
		 * Set percentage number of training data that should be drawn in every step
		 * 
		 * @param b the absolute value to set
		 * @return
		 */
		public Builder setBPercentage(float bPercentage) {
			this.bPercentage = bPercentage;
			return this;
		}

		/**
		 * @return the b
		 */
		public int getBAbsolute() {
			return bAbsolute;
		}

		/**
		 * Set absolute number of training data that should be drawn in every step
		 * 
		 * @param bAbsolute the absolute value to set
		 * @return
		 */
		public Builder setBAbsolute(int bAbsolute) {
			this.bAbsolute = bAbsolute;
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
					initialTrainingSelectionFraction, bAbsolute, bPercentage, trainingProportion, testProportion, mode);
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
		Collections.sort(corpusProvider.allExistingInternalInstances, OBIEInstance.COMPARE_BY_NAME);
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

		this.b = mode == EMode.ABSOLUT ? bAbsolute : (int) (bPercentage * (float) numberForTraining);

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

	/**
	 * The number of new training data per active learning step.
	 */
	private int b;

	public int getB() {
		return b;
	}

}
