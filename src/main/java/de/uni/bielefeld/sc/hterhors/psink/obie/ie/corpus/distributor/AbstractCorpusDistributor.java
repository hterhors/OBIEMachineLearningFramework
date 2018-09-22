package de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.distributor;

import java.util.List;

import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;

public abstract class AbstractCorpusDistributor implements ICorpusDistributor {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static abstract class AbstractConfigBuilder<B extends AbstractConfigBuilder<B>> {

		public abstract AbstractCorpusDistributor build();

	}

	public static interface Distributor {

		Distributor distributeTrainingInstances(List<OBIEInstance> trainingInstances);

		Distributor distributeDevelopmentInstances(List<OBIEInstance> developmentInstances);

		Distributor distributeTestInstances(List<OBIEInstance> testInstances);
	}

}
