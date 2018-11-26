package de.hterhors.obie.ml.activelearning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import corpus.SampledInstance;
import de.hterhors.obie.ml.run.AbstractRunner;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import sampling.Explorer;

public class FullDocumentRandomRanker implements IActiveLearningDocumentRanker {

	final Logger log = LogManager.getRootLogger();

	final Random random;
	final AbstractRunner runner;

	public FullDocumentRandomRanker(AbstractRunner runner) {
		random = new Random(123456L);
//		random = ((ActiveLearningDistributor) runner.getParameter().corpusDistributor).random;
		this.runner = runner;
	}

	@Override
	public List<OBIEInstance> rank(List<OBIEInstance> remainingInstances) {
		log.info("Apply random rank...");
		log.info("Copy...");
		List<OBIEInstance> randomized = new ArrayList<>(remainingInstances);

//		List<RankedInstance> objectiveInstances = new ArrayList<>();

		log.info("Sort...");
		Collections.sort(randomized, OBIEInstance.COMPARE_BY_NAME);

		log.info("Shuffle...");
		Collections.shuffle(randomized, random);

//		log.info("Analyze objectve score...");
//		List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> predictions = runner
//				.test(remainingInstances);
//
//		for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> sampledInstance : predictions) {
//			runner.scoreWithObjectiveFunction(sampledInstance.getState());
//
//			final double inverseObjectiveRank = 1 - sampledInstance.getState().getObjectiveScore();
//			objectiveInstances.add(new RankedInstance(inverseObjectiveRank, sampledInstance.getInstance()));
//		}
//		Collections.sort(objectiveInstances);
//		SpearmansCorrelation s = new SpearmansCorrelation();
//
//		double[] entropy = randomized.stream().map(i -> (double) i.getName().hashCode())
//				.mapToDouble(Double::doubleValue).toArray();
//		double[] objective = objectiveInstances.stream().map(i -> (double) i.instance.getName().hashCode())
//				.mapToDouble(Double::doubleValue).toArray();
//
//		double correlation = s.correlation(entropy, objective);
//
//		final double meanObjectiveF1Score = objectiveInstances.stream().map(i -> i.value).reduce(0D, Double::sum)
//				/ objectiveInstances.size();
//
//		log.info("Spearmans Correlation: " + correlation);
//		log.info("Mean F1 score (Objective): " + meanObjectiveF1Score);
		return randomized;
	}

}
