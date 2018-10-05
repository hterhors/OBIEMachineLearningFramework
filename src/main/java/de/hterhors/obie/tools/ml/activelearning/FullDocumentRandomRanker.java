package de.hterhors.obie.tools.ml.activelearning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.hterhors.obie.tools.ml.corpus.distributor.ActiveLearningDistributor;
import de.hterhors.obie.tools.ml.run.AbstractOBIERunner;
import de.hterhors.obie.tools.ml.variables.OBIEInstance;

public class FullDocumentRandomRanker implements IActiveLearningDocumentRanker {

	@Override
	public List<OBIEInstance> rank(ActiveLearningDistributor distributor, AbstractOBIERunner runner,
			List<OBIEInstance> remainingInstances) {

		List<OBIEInstance> randomized = new ArrayList<>(remainingInstances);

		Collections.shuffle(randomized, distributor.random);

		return remainingInstances;
	}

}
