package de.hterhors.obie.tools.ml.activelearning;

import java.util.List;

import de.hterhors.obie.tools.ml.corpus.distributor.ActiveLearningDistributor;
import de.hterhors.obie.tools.ml.run.AbstractOBIERunner;
import de.hterhors.obie.tools.ml.variables.OBIEInstance;

public interface IActiveLearningDocumentRanker {

	List<OBIEInstance> rank(ActiveLearningDistributor distributor, AbstractOBIERunner runner,
			List<OBIEInstance> remainingInstances);

}
