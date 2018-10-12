package de.hterhors.obie.ml.corpus.distributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.ml.corpus.BigramCorpusProvider;
import de.hterhors.obie.ml.variables.OBIEInstance;

/**
 * This distributor distributes all instances (train, dev and test are merged)
 * given the specific list of instances names for train, dev and test.
 * 
 * @author hterhors
 *
 */
public class ByInstanceNameDistributor extends AbstractCorpusDistributor {

	protected static Logger log = LogManager.getRootLogger();
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	final public Set<String> namesOfTrainingInstances;
	final public Set<String> namesOfDevelopmentInstances;
	final public Set<String> namesOfTestInstances;

	private ByInstanceNameDistributor(final float corpusSizeFraction, Set<String> namesOfTrainingInstances,
			Set<String> namesOfDevelopmentInstances, Set<String> namesOfTestInstances) {
		super(corpusSizeFraction);
		this.namesOfTrainingInstances = namesOfTrainingInstances;
		this.namesOfDevelopmentInstances = namesOfDevelopmentInstances;
		this.namesOfTestInstances = namesOfTestInstances;
	}

	public static class Builder extends AbstractConfigBuilder<Builder> {

		private Set<String> namesOfTrainingInstances = new HashSet<>();
		private Set<String> namesOfDevelopmentInstances = new HashSet<>();
		private Set<String> namesOfTestInstances = new HashSet<>();

		public ByInstanceNameDistributor build() {

			final long totalSize = namesOfTrainingInstances.size() + namesOfDevelopmentInstances.size()
					+ namesOfTestInstances.size();

			final long mergedSize = Stream.concat(namesOfTrainingInstances.stream(),
					Stream.concat(namesOfDevelopmentInstances.stream(), namesOfTestInstances.stream())).count();

			if (totalSize != mergedSize)
				log.warn("Distribution of instances into train, dev and test are not distinct!");

			return new ByInstanceNameDistributor(corpusSizeFraction,
					Collections.unmodifiableSet(namesOfTrainingInstances),
					Collections.unmodifiableSet(namesOfDevelopmentInstances),
					Collections.unmodifiableSet(namesOfTestInstances));
		}

		public Set<String> getNamesOfTrainingInstances() {
			return namesOfTrainingInstances;
		}

		public Builder setNamesOfTrainingInstances(Set<String> namesOfTrainingInstances) {
			this.namesOfTrainingInstances = namesOfTrainingInstances;
			return this;
		}

		public Set<String> getNamesOfDevelopmentInstances() {
			return namesOfDevelopmentInstances;
		}

		public Builder setNamesOfDevelopmentInstances(Set<String> namesOfDevelopmentInstances) {
			this.namesOfDevelopmentInstances = namesOfDevelopmentInstances;
			return this;
		}

		public Set<String> getNamesOfTestInstances() {
			return namesOfTestInstances;
		}

		public Builder setNamesOfTestInstances(Set<String> namesOfTestInstances) {
			this.namesOfTestInstances = namesOfTestInstances;
			return this;
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

		return new Distributor() {

			@Override
			public Distributor distributeTrainingInstances(List<OBIEInstance> trainingDocuments) {

				final long maxSize = Math.round(((double) namesOfTrainingInstances.size() * corpusSizeFraction));

				trainingDocuments.addAll(corpusProvider.allExistingInternalInstances.stream()
						.filter(s -> namesOfTrainingInstances.contains(s.getName())).limit(maxSize)
						.collect(Collectors.toList()));

				return this;
			}

			@Override
			public Distributor distributeDevelopmentInstances(List<OBIEInstance> developmentDocuments) {

				final long maxSize = Math.round(((double) namesOfDevelopmentInstances.size() * corpusSizeFraction));

				developmentDocuments.addAll(corpusProvider.allExistingInternalInstances.stream()
						.filter(s -> namesOfDevelopmentInstances.contains(s.getName())).limit(maxSize)
						.collect(Collectors.toList()));

				return this;
			}

			@Override
			public Distributor distributeTestInstances(List<OBIEInstance> testDocuments) {

				final long maxSize = Math.round(((double) namesOfTestInstances.size() * corpusSizeFraction));

				testDocuments.addAll(corpusProvider.allExistingInternalInstances.stream()
						.filter(s -> namesOfTestInstances.contains(s.getName())).limit(maxSize)
						.collect(Collectors.toList()));

				return this;
			}
		};
	}

	@Override
	public String getDistributorID() {
		return "ByInstanceNames";
	}
}
