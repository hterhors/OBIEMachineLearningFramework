package de.uni.bielefeld.sc.hterhors.psink.obie.ie.activelearning;

import java.util.List;

import de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.distributor.ActiveLearningDistributor;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.AbstractOBIERunner;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;

public interface IActiveLearningDocumentRanker {

	List<OBIEInstance> rank(ActiveLearningDistributor distributor, AbstractOBIERunner runner,
			List<OBIEInstance> remainingInstances);

}
