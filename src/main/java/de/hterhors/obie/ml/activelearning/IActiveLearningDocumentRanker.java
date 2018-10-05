package de.hterhors.obie.ml.activelearning;

import java.util.List;

import de.hterhors.obie.ml.corpus.distributor.ActiveLearningDistributor;
import de.hterhors.obie.ml.run.AbstractOBIERunner;
import de.hterhors.obie.ml.variables.OBIEInstance;

public interface IActiveLearningDocumentRanker {

	List<OBIEInstance> rank(ActiveLearningDistributor distributor, AbstractOBIERunner runner,
			List<OBIEInstance> remainingInstances);

}
