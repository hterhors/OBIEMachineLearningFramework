package de.hterhors.obie.ml.corpus;

import java.util.List;

import de.hterhors.obie.ml.activelearning.IActiveLearningDocumentRanker;
import de.hterhors.obie.ml.run.AbstractRunner;
import de.hterhors.obie.ml.variables.OBIEInstance;

public interface IActiveLearningProvider {

	/**
	 * * Function for updating training data within active learning life cycle.
	 * 
	 * @param runner
	 * @param selector
	 * @return the list of new training data.
	 */
	public List<OBIEInstance> updateActiveLearning(AbstractRunner runner, IActiveLearningDocumentRanker selector);

	public int getCurrentActiveLearningIteration();

}
