package de.hterhors.obie.ml.corpus.distributor;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.ml.corpus.BigramCorpusProvider;
import de.hterhors.obie.ml.variables.OBIEInstance;

/**
 * Merges training, development and test data shuffles them and redistributes
 * the data according to the specification in the setting.
 * 
 * @author hterhors
 *
 * @param <T>
 * @date Oct 13, 2017
 */
public class ShuffleCorpusDistributor extends AbstractCorpusDistributor {

	protected static Logger log = LogManager.getRootLogger();

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The proportion of the training data.
	 */
	public final int trainingProportion;

	/**
	 * The proportion of the development data.
	 */
	public final int developmentProportion;

	/**
	 * The proportion of the test data.
	 */
	public final int testProportion;

	/**
	 * The random to shuffle the documents.
	 */
	public final Random rnd;

	/**
	 * The seed that was used to initialize the random.
	 */
	public final long seed;

	private ShuffleCorpusDistributor(float corpusSizeFraction, int trainingProportion, int developmentProportion,
			int testProportion, long seed) {
		super(corpusSizeFraction);

		this.trainingProportion = trainingProportion;
		this.developmentProportion = developmentProportion;
		this.testProportion = testProportion;
		this.seed = seed;
		this.rnd = new Random(seed);
	}

	private int proportionsSum() {
		return trainingProportion + developmentProportion + testProportion;
	}

	public int numberOfTrainingData(final int totalNumberOfDocuments) {
		return Math.round(corpusSizeFraction
				* (((float) trainingProportion / (float) proportionsSum()) * totalNumberOfDocuments));
	}

	public int numberOfDevelopmentData(final int totalNumberOfDocuments) {
		return Math.round(corpusSizeFraction
				* (((float) developmentProportion / (float) proportionsSum()) * totalNumberOfDocuments));
	}

	public int numberOfTestData(final int totalNumberOfDocuments) {
		return Math.round(
				corpusSizeFraction * (((float) testProportion / (float) proportionsSum()) * totalNumberOfDocuments));
	}

	@Override
	public String toString() {
		return "ShuffleCorpusConfig [trainingProportion=" + trainingProportion + ", developmentProportion="
				+ developmentProportion + ", testProportion=" + testProportion + ", rnd=" + rnd + ", seed=" + seed
				+ "]";
	}

	public static class Builder extends AbstractConfigBuilder<Builder> {

		/**
		 * The proportion of the training data.
		 */
		int trainingProportion = 70;

		/**
		 * The proportion of the development data.
		 */
		int developmentProportion = 10;

		/**
		 * The proportion of the test data.
		 */
		int testProportion = 20;

		/**
		 * The seed that was used to initialize the random.
		 */
		long seed = 100L;

		public Builder setTrainingProportion(int trainingProportion) {
			this.trainingProportion = trainingProportion;
			return this;

		}

		public Builder setDevelopmentProportion(int developmentProportion) {
			this.developmentProportion = developmentProportion;
			return this;

		}

		public Builder setTestProportion(int testProportion) {
			this.testProportion = testProportion;
			return this;

		}

		public Builder setSeed(long seed) {
			this.seed = seed;
			return this;

		}

		public int getTrainingProportion() {
			return trainingProportion;
		}

		public int getDevelopmentProportion() {
			return developmentProportion;
		}

		public int getTestProportion() {
			return testProportion;
		}

		public long getSeed() {
			return seed;
		}

		public ShuffleCorpusDistributor build() {
			return new ShuffleCorpusDistributor(corpusSizeFraction, trainingProportion, developmentProportion,
					testProportion, seed);
		}

		@Override
		protected Builder getDistributor() {
			return this;
		}

	}

	/**
	 * 
	 * Builds a new corpus for training development and test based on the input
	 * documents. All documents are shuffled and redistributed according to the
	 * configuration.
	 * 
	 * @param config
	 * @param trainingDocuments
	 * @param developmentDocuments
	 * @param testDocuments
	 * @param investigationRestriction
	 */
	@Override
	public Distributor distributeInstances(BigramCorpusProvider corpusProvider) {

		log.info("Collect and shuffle documents...");

		Collections.shuffle(corpusProvider.allExistingInternalInstances, rnd);

		final int totalNumberOfDocuments = corpusProvider.allExistingInternalInstances.size();

		final int numberForTraining = numberOfTrainingData(totalNumberOfDocuments);
		final int numberForDevelopment = numberOfDevelopmentData(totalNumberOfDocuments);
		final int numberForTest = numberOfTestData(totalNumberOfDocuments);

		if (numberForTraining + numberForDevelopment + numberForTest != (int) Math
				.round(corpusProvider.allExistingInternalInstances.size() * corpusSizeFraction))
			log.warn(
					"WARN!!! Could not redistribute data accordingly! Change number of documents for data from "
							+ numberForTest + " to "
							+ (Math.round(corpusProvider.allExistingInternalInstances.size() * corpusSizeFraction)
									- Math.round(corpusSizeFraction * (numberForTraining + numberForDevelopment)))
							+ "!");

		return new Distributor() {

			@Override
			public Distributor distributeTrainingInstances(List<OBIEInstance> trainingDocuments) {
				trainingDocuments.addAll(corpusProvider.allExistingInternalInstances.subList(0, numberForTraining));
				return this;
			}

			@Override
			public Distributor distributeDevelopmentInstances(List<OBIEInstance> developmentDocuments) {
				developmentDocuments.addAll(corpusProvider.allExistingInternalInstances.subList(numberForTraining,
						numberForTraining + numberForDevelopment));
				return this;
			}

			@Override
			public Distributor distributeTestInstances(List<OBIEInstance> testDocuments) {
				testDocuments.addAll(corpusProvider.allExistingInternalInstances.subList(
						numberForTraining + numberForDevelopment, corpusProvider.allExistingInternalInstances.size()));
				return this;
			}
		};
	}

	@Override
	public String getDistributorID() {
		return "Shuffle";
	}

}
