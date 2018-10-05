package de.hterhors.obie.tools.ml.corpus;

import de.hterhors.obie.tools.ml.activelearning.IActiveLearningDocumentRanker;
import de.hterhors.obie.tools.ml.run.AbstractOBIERunner;

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
