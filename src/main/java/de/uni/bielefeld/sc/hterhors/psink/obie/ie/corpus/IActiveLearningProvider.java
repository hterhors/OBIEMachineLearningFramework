package de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus;

import de.uni.bielefeld.sc.hterhors.psink.obie.ie.activelearning.IActiveLearningDocumentRanker;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.AbstractOBIERunner;

public interface IActiveLearningProvider {

	/**
	 * * Function for updating training data within active learning life cycle.
	 * 
	 * @param runner
	 * @param selector
	 * @return true if there are still training data to add left.
	 */
	public boolean updateActiveLearning(AbstractOBIERunner runner, IActiveLearningDocumentRanker selector);

	public int getCurrentActiveLearningIteration();

}
