package de.hterhors.obie.tools.ml.evaluation.evaluator;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.jena.ext.com.google.common.collect.Collections2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.evaluation.PRF1;
import de.hterhors.obie.core.ontology.instances.EmptyOBIEInstance;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.tools.ml.evaluation.IOrListCondition;
import de.hterhors.obie.tools.ml.run.InvestigationRestriction;

public class CartesianSearchEvaluator extends AbstractOBIEEvaluator {

	public static Logger log = LogManager.getFormatterLogger(CartesianSearchEvaluator.class.getSimpleName());

	private static final Map<Integer, Collection<List<Integer>>> permutationCache = new HashMap<>();

	/**
	 * The maximum size of lists that are comparable with this method. As we need to
	 * calculate all permutations of the list assignment, the computation is limited
	 * to be smaller than Integer.MAX_VALUE. 13! > Integer.MAX_VALUE.
	 */
	private static final int MAX_NUMBER_OF_PERMUTATIONS = 12;

	/**
	 * Print Warning as permutation size of 8! takes quite a bit time to process
	 * multiple times.
	 */
	private static final int WARNING_ON_MAX = 8;

	/**
	 * Default constructor.
	 */
	public CartesianSearchEvaluator() {
		this(true, Integer.MAX_VALUE, true, InvestigationRestriction.noRestrictionInstance, f -> false,
				Integer.MAX_VALUE, true);
	}

	public CartesianSearchEvaluator(boolean enableCaching, final int maxEvaluationDepth,
			final boolean penalizeCardinality, InvestigationRestriction investigationRestrictions,
			int maxNumberOfAnnotations, final boolean ignoreEmptyInstancesOnEvaluation) {
		this(enableCaching, maxEvaluationDepth, penalizeCardinality, investigationRestrictions, f -> false,
				maxNumberOfAnnotations, ignoreEmptyInstancesOnEvaluation);
	}

	public CartesianSearchEvaluator(boolean enableCaching, final int maxEvaluationDepth,
			final boolean penalizeCardinality, InvestigationRestriction investigationRestrictions,
			IOrListCondition orListCondition, int maxNumberOfAnnotations,
			final boolean ignoreEmptyInstancesOnEvaluation) {
		super(enableCaching, penalizeCardinality, investigationRestrictions, orListCondition, maxEvaluationDepth,
				maxNumberOfAnnotations, ignoreEmptyInstancesOnEvaluation);

		for (int i = 0; i <= MAX_NUMBER_OF_PERMUTATIONS; i++) {

			Collection<List<Integer>> indexPermutations;
			if ((indexPermutations = permutationCache.get(i)) == null) {
				indexPermutations = Collections2
						.permutations(IntStream.range(0, i).boxed().collect(Collectors.toList()));
				permutationCache.put(i, indexPermutations);
			}
		}
	}

	@Override
	public double recall(IOBIEThing gold, IOBIEThing prediction) {
		try {
			return singleEntityEvaluation(gold, prediction).getRecall();
		} catch (IllegalArgumentException | SecurityException e) {
			e.printStackTrace();
			return -Integer.MIN_VALUE;
		}
	}

	@Override
	public double precision(IOBIEThing gold, IOBIEThing prediction) {
		try {
			return singleEntityEvaluation(gold, prediction).getPrecision();
		} catch (IllegalArgumentException | SecurityException e) {
			e.printStackTrace();
			return -Integer.MIN_VALUE;
		}
	}

	@Override
	public double recall(List<IOBIEThing> gold, List<IOBIEThing> predictions) {
		try {
			return cartesianEvaluation(Collections.unmodifiableList(gold), Collections.unmodifiableList(predictions))
					.getRecall();
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
			return -Integer.MIN_VALUE;
		}
	}

	@Override
	public double precision(List<IOBIEThing> gold, List<IOBIEThing> predictions) {
		try {
			return cartesianEvaluation(Collections.unmodifiableList(gold), Collections.unmodifiableList(predictions))
					.getPrecision();
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
			return -Integer.MIN_VALUE;
		}
	}

	@Override
	public double f1(List<? extends IOBIEThing> gold, List<? extends IOBIEThing> predictions) {
		try {
			return cartesianEvaluation(Collections.unmodifiableList(gold), Collections.unmodifiableList(predictions))
					.getF1();
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
			return -Integer.MIN_VALUE;
		}
	}

	@Override
	public double f1(IOBIEThing gold, IOBIEThing prediction) {
		try {
			return singleEntityEvaluation(gold, prediction).getF1();
		} catch (IllegalArgumentException | SecurityException e) {
			e.printStackTrace();
			return -Integer.MIN_VALUE;
		}
	}

	@Override
	public PRF1 prf1(List<? extends IOBIEThing> gold, List<? extends IOBIEThing> predictions) {
		try {
			return cartesianEvaluation(Collections.unmodifiableList(gold), Collections.unmodifiableList(predictions));
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		}
		return null;
	}

	public PRF1 prf1(IOBIEThing gold, IOBIEThing prediction) {
		try {
			return singleEntityEvaluation(gold, prediction);
		} catch (IllegalArgumentException | SecurityException e) {
			e.printStackTrace();
		}
		return null;
	}

	private PRF1 singleEntityEvaluation(IOBIEThing gold, IOBIEThing prediction) {
		// System.out.println("prediction" +
		// OBIEFormatter.format(prediction));
		PRF1 s = compareObjectWise(gold, prediction, 0);
		// System.out.println("Single : " + s);
		return s;
	}

	private PRF1 cartesianEvaluation(List<IOBIEThing> goldList, List<IOBIEThing> predictionList)
			throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {

		if (goldList.size() == 1 && predictionList.size() == 1)
			return singleEntityEvaluation(goldList.get(0), predictionList.get(0));

		PRF1 bestPermutationScore = explore(goldList, predictionList, 0);
		// bestPermutationScore = orListSimilarity(goldList, predictionList);
		// System.out.println("Multi : " + bestPermutationScore);
		// System.out.println("totalTime = " + totalTime / 1000L);

		return bestPermutationScore;
	}

	/**
	 * Compares the objects of two lists. This method takes all objects into account
	 * thus it returns the max over all permutations of comparisons. The number of
	 * comparisons is n!
	 * 
	 * @param gold
	 * @param prediction
	 * @return
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 */
	@Override
	protected PRF1 explore(final List<IOBIEThing> gold, final List<IOBIEThing> prediction, final int depth) {

		final int maxSize = Math.max(gold.size(), prediction.size());

		if (maxSize > maxNumberOfAnnotations) {
			log.warn("Skip comparison... to many cases as defined in the parameter!");
			return new PRF1(0, 0, 0);
		}
		if (maxSize > WARNING_ON_MAX) {
			log.warn("WARN! List size to compare are greater than " + WARNING_ON_MAX + ". Size = " + maxSize
					+ ", number of permutations = " + factorial(maxSize)
					+ ". This may result in a long computation time.");
			if (maxSize > MAX_NUMBER_OF_PERMUTATIONS) {
				log.warn("Skip comparison... to many cases!");
				return new PRF1(0, 0, 0);
			}
		}

		Collection<List<Integer>> indexPermutations = permutationCache.get(maxSize);

		final PRF1 bestPermutationScore = new PRF1();
		java.util.stream.Stream<List<Integer>> stream = indexPermutations.stream();

		/*
		 * Remove parallel overhead if list is to small.
		 */
		if (maxSize > 2)
			stream = stream.parallel();

		stream.forEach(permutation -> {
			getBestPermutation(gold, prediction, maxSize, bestPermutationScore, permutation, depth);
		});

		return bestPermutationScore;
	}

	private void getBestPermutation(final List<IOBIEThing> gold, final List<IOBIEThing> prediction, final int maxSize,
			final PRF1 bestPermutationScore, List<Integer> permutation, final int depth) {
		final PRF1 currentPermutationScore = new PRF1();

		for (int goldListIndex = 0; goldListIndex < maxSize; goldListIndex++) {
			final IOBIEThing o1;
			final IOBIEThing o2;

			if (gold.size() > goldListIndex)
				o1 = gold.get(goldListIndex);
			else
				o1 = EmptyOBIEInstance.emptyInstance;

			final int predictionIndex = permutation.get(goldListIndex);

			if (prediction.size() > predictionIndex)
				o2 = prediction.get(predictionIndex);
			else
				o2 = EmptyOBIEInstance.emptyInstance;
			currentPermutationScore.add(compareObjectWise(o1, o2, depth));
		}
		synchronized (bestPermutationScore) {
			if (bestPermutationScore.getF1() <= currentPermutationScore.getF1()) {
				bestPermutationScore.set(currentPermutationScore);
			}
		}
		/*
		 * Shortcut
		 */
		if ((int) bestPermutationScore.getF1() == 1) {
			return;
		}
	}

	private static long factorial(int n) {
		long multi = 1;
		for (int i = 1; i <= n; i++) {
			multi = multi * i;
		}
		return multi;
	}

}
