package de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.distributor;

import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.BigramCorpusProvider;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;

/**
 * Merges training, development and test data shuffles them and redistributes
 * the data according to the specification in the setting.
 * 
 * @author hterhors
 *
 * @param <T>
 * @date Oct 13, 2017
 */
public class FoldCrossCorpusDistributor extends AbstractCorpusDistributor {
	protected static Logger log = LogManager.getRootLogger();
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The random to shuffle the documents.
	 */
	public final Random rnd;

	/**
	 * Th e seed that was used to initialize the random.
	 */
	public final long seed;

	/**
	 * Number of folds.
	 */
	public final int n;

	private FoldCrossCorpusDistributor(int n, long seed) {
		log.info("Create new corpus diributor of type " + this.getClass().getName());

		this.n = n;
		this.seed = seed;
		this.rnd = new Random(seed);
	}

	@Override
	public String toString() {
		return "FoldCrossCorpusConfig [rnd=" + rnd + ", seed=" + seed + ", n=" + n + "]";
	}

	public static class Builder extends AbstractConfigBuilder<Builder> {

		/**
		 * Number of folds.
		 */
		int n = 10;

		/**
		 * The seed that was used to initialize the random.
		 */
		long seed = 100L;

		public Builder setN(int n) {
			this.n = n;
			return this;

		}

		public Builder setSeed(long seed) {
			this.seed = seed;
			return this;

		}

		public int getN() {
			return n;
		}

		public long getSeed() {
			return seed;
		}

		public FoldCrossCorpusDistributor build() {
			return new FoldCrossCorpusDistributor(n, seed);
		};

	}

	@Override
	public Distributor distributeInstances(BigramCorpusProvider corpusProvider) {
		log.info("Number of instances per fold: " + corpusProvider.internalInstances.size() / n);

		return new Distributor() {

			@Override
			public Distributor distributeTrainingInstances(List<OBIEInstance> l) {
				l.addAll(corpusProvider.internalInstances);
				return this;
			}

			@Override
			public Distributor distributeDevelopmentInstances(List<OBIEInstance> l) {
				return this;
			}

			@Override
			public Distributor distributeTestInstances(List<OBIEInstance> l) {
				return this;
			}
		};

	}

	@Override
	public String getDistributorID() {
		return "FoldCross";
	}
}
