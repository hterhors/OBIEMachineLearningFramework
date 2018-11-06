package de.hterhors.obie.ml.activelearning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.evaluation.PRF1;
import de.hterhors.obie.ml.run.AbstractRunner;
import de.hterhors.obie.ml.tools.baseline.RandomBaseline;
import de.hterhors.obie.ml.variables.OBIEInstance;

public class FullDocumentRandFillerRanker implements IActiveLearningDocumentRanker {

	final Logger log = LogManager.getRootLogger();

	final Random random;
	final AbstractRunner runner;

	private final RandomBaseline randomFiller;

	public FullDocumentRandFillerRanker(AbstractRunner runner) {
		random = new Random();
		this.runner = runner;
		this.randomFiller = new RandomBaseline(runner.getParameter(), random.nextLong());
	}

	@Override
	public List<OBIEInstance> rank(List<OBIEInstance> remainingInstances) {
		log.info("Apply random filler...");
		log.info("Copy...");
		List<OBIEInstance> randomized = new ArrayList<>(remainingInstances);

		List<RankedInstance> entropyInstances = new ArrayList<>();

		log.info("Fill ranomized and evaluate...");
		for (OBIEInstance obieInstance : randomized) {

			PRF1 score = runner.getParameter().evaluator.prf1(obieInstance.getGoldAnnotation().getTemplateAnnotations()
					.stream().map(f -> f.getThing()).collect(Collectors.toList()),
					this.randomFiller.predictFillerByRandom(obieInstance));
			entropyInstances.add(new RankedInstance(1-score.getF1(), obieInstance));
		}
		
		Collections.sort(entropyInstances);

		return entropyInstances.stream().map(e -> e.instance).collect(Collectors.toList());
	}

}
