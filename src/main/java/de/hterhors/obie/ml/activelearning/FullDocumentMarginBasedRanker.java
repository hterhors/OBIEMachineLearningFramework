package de.hterhors.obie.ml.activelearning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.ml.run.AbstractRunner;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;

public class FullDocumentMarginBasedRanker implements IActiveLearningDocumentRanker {

	final Logger log = LogManager.getRootLogger();

	final private AbstractRunner runner;

	public FullDocumentMarginBasedRanker(AbstractRunner runner) {
		this.runner = runner;
	}

	@Override
	public List<OBIEInstance> rank(List<OBIEInstance> remainingInstances) {

		List<RankedInstance> entropyInstances = new ArrayList<>();

		log.info("Compute variations for margin...");
		Map<OBIEInstance, List<OBIEState>> results = runner.collectBestNStates(remainingInstances, 2);

		log.info("Compute margin...");
		for (Entry<OBIEInstance, List<OBIEState>> predictedInstance : results.entrySet()) {

			double margin;

			if (predictedInstance.getValue().size() != 2) {
				// Highest Value =
				margin = Double.MAX_VALUE;
			} else {
				// Always negative
				margin = Math.abs(predictedInstance.getValue().get(0).getModelScore()
						- predictedInstance.getValue().get(1).getModelScore());
			}

			/*
			 * Sorted by highest first
			 */
			entropyInstances.add(new RankedInstance(margin, predictedInstance.getKey()));

		}

		log.info("Sort...");

		Collections.sort(entropyInstances);
		// Reverse order since greater margin = better
		Collections.reverse(entropyInstances);

		logStats(entropyInstances, "margin");

		return entropyInstances.stream().map(e -> e.instance).collect(Collectors.toList());

	}

	final int n = 20;

	private void logStats(List<RankedInstance> entropyInstances, String context) {

		log.info("Next " + n + " " + context + ":");
		entropyInstances.stream().limit(n).forEach(i -> log.info(i.instance.getName() + ":" + i.value));

	}

}
