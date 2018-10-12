package de.hterhors.obie.ml.activelearning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.ml.corpus.distributor.ActiveLearningDistributor;
import de.hterhors.obie.ml.run.AbstractOBIERunner;
import de.hterhors.obie.ml.variables.OBIEInstance;

public class FullDocumentRandomRanker implements IActiveLearningDocumentRanker {

	final Logger log = LogManager.getRootLogger();

	@Override
	public List<OBIEInstance> rank(ActiveLearningDistributor distributor, AbstractOBIERunner runner,
			List<OBIEInstance> remainingInstances) {
		log.info("Apply random rank...");
		log.info("Copy...");
		List<OBIEInstance> randomized = new ArrayList<>(remainingInstances);

		log.info("Shuffle...");
		Collections.shuffle(randomized, distributor.random);
		return remainingInstances;
	}

}
