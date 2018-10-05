package de.uni.bielefeld.sc.hterhors.psink.obie.ie.activelearning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.distributor.ActiveLearningDistributor;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.AbstractOBIERunner;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;

public class FullDocumentRandomRanker implements IActiveLearningDocumentRanker {

	@Override
	public List<OBIEInstance> rank(ActiveLearningDistributor distributor, AbstractOBIERunner runner,
			List<OBIEInstance> remainingInstances) {

		List<OBIEInstance> randomized = new ArrayList<>(remainingInstances);

		Collections.shuffle(randomized, distributor.random);

		return remainingInstances;
	}

}
