package de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.distributor;

import java.util.List;

import org.apache.jena.atlas.logging.Log;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;

public abstract class AbstractCorpusDistributor implements ICorpusDistributor {

	protected static Logger log = LogManager.getRootLogger();

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	protected static final float DEFAULT_CORPUS_SIZE_FRACTION = 1F;

	final public float corpusSizeFraction;

	public AbstractCorpusDistributor(final float corpusSizeFraction) {
		if (corpusSizeFraction > 0) {
			this.corpusSizeFraction = corpusSizeFraction;
		} else {
			log.warn("Corpus distributor fraction size was set to 0 or a negative value. Reset to full corpus size!");
			this.corpusSizeFraction = 1F;
		}
		log.info("Initialize corpus diributor of type " + getDistributorID() + " with fraction size: "
				+ corpusSizeFraction);
	}

	public static abstract class AbstractConfigBuilder<B extends AbstractConfigBuilder<B>> {
		float corpusSizeFraction = AbstractCorpusDistributor.DEFAULT_CORPUS_SIZE_FRACTION;

		public abstract AbstractCorpusDistributor build();

		public float getCorpusSizeFraction() {
			return corpusSizeFraction;
		}

		public B setCorpusSizeFraction(float corpusSizeFraction) {
			this.corpusSizeFraction = corpusSizeFraction;
			return getDistributor();
		}

		protected abstract B getDistributor();

	}

	public static interface Distributor {

		Distributor distributeTrainingInstances(List<OBIEInstance> trainingInstances);

		Distributor distributeDevelopmentInstances(List<OBIEInstance> developmentInstances);

		Distributor distributeTestInstances(List<OBIEInstance> testInstances);
	}

}
