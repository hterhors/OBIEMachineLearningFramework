package de.hterhors.obie.ml.corpus.distributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.ml.corpus.BigramCorpusProvider;
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

				List<String> l = new ArrayList<>(corpusProvider.getOriginalTrainingInstances());

				sortAndShuffleIf(l);

				for (String name : l) {
					final float fraction = (float) trainingDocuments.size()
							/ corpusProvider.getOriginalTrainingInstances().size();

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

			private void sortAndShuffleIf(List<String> l) {
				if (corpusSizeFraction != 1.0F) {

					/*
					 * Ensure same order on shuffle, if fraction size is not equals 1;
					 */
					Collections.sort(l);

					/*
					 * Shuffle to not always get the first same elements based on the name.
					 */
					Collections.shuffle(l, new Random(987654321L));
				}
			}

			@Override
			public Distributor distributeDevelopmentInstances(List<OBIEInstance> developmentDocuments) {

				List<String> l = new ArrayList<>(corpusProvider.getOriginalDevelopInstances());

				sortAndShuffleIf(l);

				for (String name : l) {
					final float fraction = (float) developmentDocuments.size()
							/ corpusProvider.getOriginalDevelopInstances().size();

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

				List<String> l = new ArrayList<>(corpusProvider.getOriginalTestInstances());

				sortAndShuffleIf(l);

				for (String name : l) {
					final float fraction = (float) testDocuments.size()
							/ corpusProvider.getOriginalTestInstances().size();

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
