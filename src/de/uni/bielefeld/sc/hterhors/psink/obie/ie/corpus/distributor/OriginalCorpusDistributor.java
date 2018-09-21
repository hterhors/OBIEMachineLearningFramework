package de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.distributor;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.BigramCorpusProvider;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;

/**
 * Takes the original distribution of documents into training, development and
 * testing.
 * 
 * @author hterhors
 *
 * @param <T>
 * @date Oct 13, 2017
 */
public class OriginalCorpusDistributor extends AbstractCorpusDistributor {
	protected static Logger log = LogManager.getRootLogger();
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private OriginalCorpusDistributor() {
		log.info("Create new corpus diributor of type " + this.getClass().getName());
	}

	public static class Builder extends AbstractConfigBuilder<Builder> {

		public OriginalCorpusDistributor build() {
			return new OriginalCorpusDistributor();
		};

	}

	/**
	 * Builds the original distributed corpus from the raw data. Keeps the training,
	 * develop and test instances.
	 * 
	 * 
	 * @param config
	 * @param trainingInstances        to fill
	 * @param developmentInstances     to fill
	 * @param testInstances            to fill
	 * @param investigationRestriction
	 */
	@Override
	public Distributor distributeInstances(BigramCorpusProvider corpusProvider) {

		/**
		 * TODO: Not very efficient! Convert interalINstances to Map with name as key.
		 */
		return new Distributor() {

			@Override
			public Distributor distributeTrainingInstances(List<OBIEInstance> trainingDocuments) {

				for (String name : corpusProvider.getRawCorpus().getTrainingInstances().keySet()) {
					for (OBIEInstance internalInstance : corpusProvider.internalInstances) {
						if (internalInstance.getName().equals(name))
							trainingDocuments.add(internalInstance);
					}
				}
				return this;
			}

			@Override
			public Distributor distributeDevelopmentInstances(List<OBIEInstance> developmentDocuments) {
				for (String name : corpusProvider.getRawCorpus().getDevelopInstances().keySet()) {
					for (OBIEInstance internalInstance : corpusProvider.internalInstances) {
						if (internalInstance.getName().equals(name))
							developmentDocuments.add(internalInstance);
					}
				}
				return this;
			}

			@Override
			public Distributor distributeTestInstances(List<OBIEInstance> testDocuments) {
				for (String name : corpusProvider.getRawCorpus().getTestInstances().keySet()) {
					for (OBIEInstance internalInstance : corpusProvider.internalInstances) {
						if (internalInstance.getName().equals(name))
							testDocuments.add(internalInstance);
					}
				}
				return this;
			}
		};
	}

	@Override
	public String getDistributorID() {
		return "Origin";
	}

}
