package de.hterhors.obie.ml.corpus.distributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.tools.corpus.OBIECorpus.Instance;
import de.hterhors.obie.ml.corpus.BigramCorpusProvider;
import de.hterhors.obie.ml.corpus.distributor.ShuffleCorpusDistributor.Builder;
import de.hterhors.obie.ml.variables.OBIEInstance;

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

	private OriginalCorpusDistributor(final float corpusSizeFraction) {
		super(corpusSizeFraction);
	}

	public static class Builder extends AbstractConfigBuilder<Builder> {

		public OriginalCorpusDistributor build() {
			return new OriginalCorpusDistributor(corpusSizeFraction);
		}

		@Override
		protected Builder getDistributor() {
			return this;
		}
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

		final Map<String, OBIEInstance> easyAccessMap = new HashMap<>();
		for (OBIEInstance internalInstance : corpusProvider.allExistingInternalInstances) {
			easyAccessMap.put(internalInstance.getName(), internalInstance);
		}

		/**
		 * TODO: Not very efficient! Convert interalINstances to Map with name as key.
		 */
		return new Distributor() {

			@Override
			public Distributor distributeTrainingInstances(List<OBIEInstance> trainingDocuments) {

				for (String name : corpusProvider.getRawCorpus().getTrainingInstances().keySet()) {
					final float fraction = (float) trainingDocuments.size()
							/ corpusProvider.getRawCorpus().getTrainingInstances().size();

					if (fraction >= corpusSizeFraction)
						break;

					/*
					 * As we work with the real distribution given the raw corpus,
					 * allExistingInternalInstances may not contain the document if it violates
					 * previous restrictions.
					 */
					if (easyAccessMap.containsKey(name))
						trainingDocuments.add(easyAccessMap.get(name));
				}
				return this;
			}

			@Override
			public Distributor distributeDevelopmentInstances(List<OBIEInstance> developmentDocuments) {
				for (String name : corpusProvider.getRawCorpus().getDevelopInstances().keySet()) {
					final float fraction = (float) developmentDocuments.size()
							/ corpusProvider.getRawCorpus().getDevelopInstances().size();

					if (fraction >= corpusSizeFraction)
						break;

					/*
					 * As we work with the real distribution given the raw corpus,
					 * allExistingInternalInstances may not contain the document if it violates
					 * previous restrictions.
					 */
					if (easyAccessMap.containsKey(name))
						developmentDocuments.add(easyAccessMap.get(name));
				}
				return this;
			}

			@Override
			public Distributor distributeTestInstances(List<OBIEInstance> testDocuments) {
				for (String name : corpusProvider.getRawCorpus().getTestInstances().keySet()) {
					final float fraction = (float) testDocuments.size()
							/ corpusProvider.getRawCorpus().getTestInstances().size();

					if (fraction >= corpusSizeFraction)
						break;

					/*
					 * As we work with the real distribution given the raw corpus,
					 * allExistingInternalInstances may not contain the document if it violates
					 * previous restrictions.
					 */
					if (easyAccessMap.containsKey(name))
						testDocuments.add(easyAccessMap.get(name));
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
