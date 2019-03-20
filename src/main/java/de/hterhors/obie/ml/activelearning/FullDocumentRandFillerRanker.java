package de.hterhors.obie.ml.activelearning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.evaluation.PRF1;
import de.hterhors.obie.ml.run.AbstractOBIERunner;
import de.hterhors.obie.ml.tools.baseline.RandomBaseline;
import de.hterhors.obie.ml.variables.OBIEInstance;

public class FullDocumentRandFillerRanker implements IActiveLearningDocumentRanker {

	final Logger log = LogManager.getRootLogger();

	final Random random;
	final AbstractOBIERunner runner;

	private final RandomBaseline randomFiller;

	public FullDocumentRandFillerRanker(AbstractOBIERunner runner) {
		random = new Random();
		this.runner = runner;
		this.randomFiller = new RandomBaseline(runner.getParameter(), random.nextLong());
	}

	@Override
	public List<OBIEInstance> rank(List<OBIEInstance> remainingInstances) {
		log.info("Apply random filler...");
		log.info("Copy...");
//		List<OBIEInstance> randomized = new ArrayList<>(remainingInstances);

		List<RankedInstance> scoredInstances = new ArrayList<>();

		log.info("Fill ranomized and evaluate...");
		for (OBIEInstance obieInstance : remainingInstances) {
			PRF1 score = new PRF1();
			for (int i = 0; i < 10; i++) {
				score.add(
						runner.getParameter().evaluator.prf1(
								obieInstance.getGoldAnnotation().getAnnotations().stream()
										.map(f -> f.getThing()).collect(Collectors.toList()),
								this.randomFiller.predictFillerByRandom(obieInstance)));
			}

			scoredInstances.add(new RankedInstance(score.getF1(), obieInstance));
		}

		Collections.sort(scoredInstances);

		return scoredInstances.stream().map(e -> e.instance).collect(Collectors.toList());
	}

}
