package de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation.evaluator;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.evaluation.PRF1;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.instances.EmptyOBIEInstance;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IDataType;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation.IOrListCondition;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.InvestigationRestriction;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.ReflectionUtils;

/**
 * Beam Search with parameterized beam size.
 * 
 * @author hterhors
 *
 * @date May 18, 2018
 */
public class BeamSearchEvaluator extends AbstractOBIEEvaluator {

	public static Logger log = LogManager.getFormatterLogger(BeamSearchEvaluator.class.getSimpleName());

	/**
	 * We need this to compare lists of data types efficiently.
	 */
	private final NamedEntityLinkingEvaluator listOfDataTypesEvaluator;

	private static final int BEAM_SIZE = 100;

	public BeamSearchEvaluator(boolean enableCaching, final int maxEvaluationDepth, final boolean penalizeCardinality,
			InvestigationRestriction investigationRestrictions, int maxNumberOfAnnotations,
			final boolean ignoreEmptyInstancesOnEvaluation) {
		this(enableCaching, maxEvaluationDepth, penalizeCardinality, investigationRestrictions, f -> false,
				maxNumberOfAnnotations, ignoreEmptyInstancesOnEvaluation);
	}

	public BeamSearchEvaluator(boolean enableCaching, final int maxEvaluationDepth, final boolean penalizeCardinality,
			InvestigationRestriction investigationRestrictions, IOrListCondition orListCondition,
			int maxNumberOfAnnotations, final boolean ignoreEmptyInstancesOnEvaluation) {
		super(enableCaching, penalizeCardinality, investigationRestrictions, orListCondition, maxEvaluationDepth,
				maxNumberOfAnnotations, ignoreEmptyInstancesOnEvaluation);
		listOfDataTypesEvaluator = new NamedEntityLinkingEvaluator(maxEvaluationDepth, penalizeCardinality, investigationRestrictions,
				orListCondition, maxNumberOfAnnotations, ignoreEmptyInstancesOnEvaluation);

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
			return beamEvaluation(Collections.unmodifiableList(gold), Collections.unmodifiableList(predictions))
					.getRecall();
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
			return -Integer.MIN_VALUE;
		}
	}

	@Override
	public double precision(List<IOBIEThing> gold, List<IOBIEThing> predictions) {
		try {
			return beamEvaluation(Collections.unmodifiableList(gold), Collections.unmodifiableList(predictions))
					.getPrecision();
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
			return -Integer.MIN_VALUE;
		}
	}

	@Override
	public double f1(List<? extends IOBIEThing> gold, List<? extends IOBIEThing> predictions) {
		try {
			return beamEvaluation(Collections.unmodifiableList(gold), Collections.unmodifiableList(predictions))
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
			return beamEvaluation(Collections.unmodifiableList(gold), Collections.unmodifiableList(predictions));
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

	private PRF1 beamEvaluation(List<IOBIEThing> goldList, List<IOBIEThing> predictionList)
			throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {

		if (goldList.size() == 1 && predictionList.size() == 1)
			return singleEntityEvaluation(goldList.get(0), predictionList.get(0));

		PRF1 bestPermutationScore = beamSimilarity(goldList, predictionList, 0);
		// bestPermutationScore = orListSimilarity(goldList, predictionList);
		// System.out.println("Multi : " + bestPermutationScore);
		// System.out.println("totalTime = " + totalTime / 1000L);

		return bestPermutationScore;
	}

	private final PRF1 zeroScore = new PRF1();

	/**
	 * 
	 * @param goldInstance
	 * @param predictedInstance
	 * @param rootClassEvaluation whether the current comparison is on the root
	 *                            class.
	 * @return
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws SecurityException
	 */
	private PRF1 compareObjectWise(IOBIEThing goldInstance, IOBIEThing predictedInstance, int depth) {

		if (goldInstance == null && predictedInstance == null)
			return zeroScore;

		/*
		 * Switch on to get only the scores for same amount of templates. Do not
		 * penalize the amount.
		 */
		if (!penalizeCardinality && (depth == 0 && (goldInstance == EmptyOBIEInstance.emptyInstance
				|| predictedInstance == EmptyOBIEInstance.emptyInstance)))
			return zeroScore;

		// System.out.println("******************************");
		// System.out.println("Start score: " + score);
		// System.out.println("Compare: " + goldClass);
		// System.out.println("With: " + predictedClass);

		final CacheKey ck = new CacheKey(goldInstance, predictedInstance, investigationRestrictions);

		if (enableCaching && cache.containsKey(ck)) {
			return cache.get(ck);
		}

		PRF1 score = new PRF1();

		if (goldInstance == null && predictedInstance != null) {
			/*
			 * If the gold instance does not has the specific field but the
			 * predictedInstance has!
			 */
			score.fp++;
		}

		else if (predictedInstance == null) {
			score.fn++;
		} else if (goldInstance != null && predictedInstance != null) {
			if (goldInstance.getClass().equals(predictedInstance.getClass())) {
				if (goldInstance.getClass().isAnnotationPresent(DatatypeProperty.class)
						&& predictedInstance.getClass().isAnnotationPresent(DatatypeProperty.class)) {
					final String predValue = ((IDataType) predictedInstance).getSemanticValue();
					if (predValue == null) {
						/*
						 * This case happens only if the rootClassType is a data type class.
						 */
						score.fn++;
						return score;
					} else if (((IDataType) goldInstance).getSemanticValue().equals(predValue)) {
						/*
						 * If both classes are same data type property and have the same value.
						 */
						score.tp++;
						return score;
					} else {

						if (depth == 0 && investigationRestrictions.investigateClassType || depth != 0) {

							/*
							 * If they have not the same value.
							 */
							score.fp++;
							score.fn++;
							return score;
						}
					}
				} else {
					if (depth == 0 && investigationRestrictions.investigateClassType || depth != 0) {
						/*
						 * If both classes are the same and no data type properties.
						 */
						if (ignoreEmptyInstancesOnEvaluation && predictedInstance.isEmpty())
							/*
							 * If the predicted instance is null and we want to ignore empty instances than
							 * deal it as its not existent.
							 */
							score.fn++;
						else
							// otherwise add +1 to true positive
							score.tp++;
					}
				}
			} else {
				if (predictedInstance == EmptyOBIEInstance.emptyInstance) {
					// if (depth == 0 &&
					// investigationRestrictions.investigateClassType || depth
					// != 0) {
					score.fn++;
					// }
				} else if (goldInstance == EmptyOBIEInstance.emptyInstance) {
					// if (depth == 0 &&
					// investigationRestrictions.investigateClassType || depth
					// != 0) {
					if (!(ignoreEmptyInstancesOnEvaluation && predictedInstance.isEmpty()))
						score.fp++;
					// }
				} else if (depth == 0 && investigationRestrictions.investigateClassType || depth != 0) {
					score.fp++;
					score.fn++;
				}
			}
		}
		// System.out.println("End score = " + score);

		if (depth == maxEvaluationDepth) {
			return score;
		} else {
			depth++;
		}

		/**
		 * TODO: At the moment we assume that two fields are equivalent if the name is
		 * same. This might be a problem if two completely different classes have fields
		 * that have the same name. Better here to check if the name is equal and both
		 * classes have the same super root class. e.g. RatModel and MouseModel.
		 */
		Set<String> goldFields = new HashSet<>();
		Set<String> predictionFields = new HashSet<>();

		if (goldInstance != null) {
			goldFields = ReflectionUtils.getDeclaredOntologyFieldNames(goldInstance.getClass(),
					investigationRestrictions);
		}
		if (predictedInstance != null) {
			predictionFields = ReflectionUtils.getDeclaredOntologyFieldNames(predictedInstance.getClass(),
					investigationRestrictions);
		}

		/*
		 * Loop over all fields that are in the gold class but not in predicted class.
		 */
		for (String fieldName : goldFields) {

			if (predictionFields.contains(fieldName))
				continue;

			if (!investigationRestrictions.investigateField(fieldName))
				continue;

			Field goldField = ReflectionUtils.getDeclaredFieldByName(goldInstance.getClass(), fieldName);
			Field predictionField;

			if (predictedInstance == EmptyOBIEInstance.emptyInstance || predictedInstance == null) {
				predictionField = null;
			} else {
				predictionField = ReflectionUtils.getDeclaredFieldByName(predictedInstance.getClass(), fieldName);
			}

			score.add(loopOverFields(goldInstance, predictedInstance, goldField, predictionField, depth));
		}

		/*
		 * Loop over remaining fields
		 */
		for (String fieldName : predictionFields) {

			if (!investigationRestrictions.investigateField(fieldName))
				continue;

			Field goldField;
			if (goldInstance == EmptyOBIEInstance.emptyInstance || goldInstance == null) {
				goldField = null;
			} else {
				goldField = ReflectionUtils.getDeclaredFieldByName(goldInstance.getClass(), fieldName);
			}
			final Field predictionField = ReflectionUtils.getDeclaredFieldByName(predictedInstance.getClass(),
					fieldName);

			score.add(loopOverFields(goldInstance, predictedInstance, goldField, predictionField, depth));

		}

		if (enableCaching)
			cache.put(ck, score);

		return score;
	}

	@SuppressWarnings("unchecked")
	private PRF1 loopOverFields(IOBIEThing goldClass, IOBIEThing predictedClass, Field goldField,
			Field predictionField, final int depth) {

		PRF1 score = new PRF1();

		final PRF1 adderScore;
		if ((goldField != null && goldField.isAnnotationPresent(RelationTypeCollection.class))
				|| (predictionField != null && predictionField.isAnnotationPresent(RelationTypeCollection.class))) {

			List<IOBIEThing> goldList = null;
			List<IOBIEThing> predictionList = null;
			/*
			 * Might be null!!!
			 */
			if (goldClass != null && goldField != null) {
				try {
					goldList = (List<IOBIEThing>) goldField.get(goldClass);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
			} else {
				goldList = new ArrayList<>();
			}

			if (predictedClass != null && predictionField != null) {
				try {
					predictionList = (List<IOBIEThing>) predictionField.get(predictedClass);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
			} else {
				predictionList = new ArrayList<>();
			}

			/*
			 * There are 4 Methods to calculate the F1 score!
			 * 
			 * 1) Cartesian-wise: If the list contains non-data type entities, such as
			 * AnimalModels and all entities are important it is necessary to calculate the
			 * Cartesian-wise.
			 * 
			 * 2) OrListCondition: Just a single element of the list is needed to get a
			 * F-score of 1.0 then we need to compute the orList score.
			 * 
			 * 3) List contains only DataType Properties than we can compute the standard f1
			 * score of sets.
			 *
			 * 4) List contains only data type properties but just a single element is
			 * needed. TODO: NOT IMPL YET
			 */
			/*
			 * If the field belongs to an or-list, we need a different method to calculate
			 * the similarity of those lists.
			 */
			if (predictionField != null && predictionField.isAnnotationPresent(DatatypeProperty.class)
					|| goldField != null && goldField.isAnnotationPresent(DatatypeProperty.class)) {
				// System.out.println("In HERE..");
				adderScore = standardSimilarity(goldList, predictionList);
				// adderScore = listOfDataTypesEvaluator.prf1(goldList,
				// predictionList);
				// adderScore = new PRF1ScoreContainer(3,2,1);
				// System.out.println("... done");
			} else if ((predictionField != null && orListCondition.isTrue(predictionField))
					|| (goldField != null && orListCondition.isTrue(goldField))) {
				adderScore = orListSimilarity(goldList, predictionList, depth);
			} else {
				adderScore = beamSimilarity(goldList, predictionList, depth);
			}

		} else {
			IOBIEThing gold = null;
			IOBIEThing pred = null;
			if (goldClass != null && goldField != null)
				try {
					gold = (IOBIEThing) goldField.get(goldClass);
				} catch (NullPointerException | IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
			if (predictedClass != null && predictionField != null) {

				try {
					pred = (IOBIEThing) predictionField.get(predictedClass);
				} catch (NullPointerException | IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
			}
			adderScore = compareObjectWise(gold, pred, depth);
		}

		score.add(adderScore);
		return score;

	}

	// long totalTime = 0;
	/**
	 * Approx 0.1ms per call.
	 * 
	 * @param goldList
	 * @param predictionList
	 * @return
	 */
	private PRF1 standardSimilarity(List<IOBIEThing> goldList, List<IOBIEThing> predictionList) {

		// long t = System.nanoTime();
		PRF1 c = listOfDataTypesEvaluator.prf1(goldList, predictionList);
		// totalTime += System.nanoTime() - t;
		return c;
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
	private PRF1 beamSimilarity(final List<IOBIEThing> gold, final List<IOBIEThing> prediction,
			final int depth) {

		final List<BeamAssignmentTree> assignments = new ArrayList<>();

		assignments.add(new BeamAssignmentTree(gold, prediction));

		final BeamAssignmentTree bestAssignments = beamExploration(assignments, BEAM_SIZE, depth).get(0);

		return bestAssignments.overallSimiliarity;
	}

	private List<BeamAssignmentTree> beamExploration(final List<BeamAssignmentTree> states, final int beamSize,
			final int depth) {

		final List<BeamAssignmentTree> candidates = new ArrayList<>();

		boolean done = true;

		for (BeamAssignmentTree current : states) {

			if (current.checkBreakCondition())
				continue;

			done = false;

			final int maxSize = Math.max(current.remainingGold.size(), current.remainingPrediction.size());

			for (int goldListIndex = 0; goldListIndex < maxSize; goldListIndex++) {

				/*
				 * Get gold object if any, otherwise empty instance.
				 */
				final IOBIEThing goldThing;
				if (current.remainingGold.size() > goldListIndex)
					goldThing = current.remainingGold.get(goldListIndex);
				else
					goldThing = EmptyOBIEInstance.emptyInstance;

				for (int predictionListIndex = 0; predictionListIndex < maxSize; predictionListIndex++) {

					/*
					 * Get prediction object if any, otherwise empty instance.
					 */
					final IOBIEThing predThing;

					if (current.remainingPrediction.size() > predictionListIndex)
						predThing = current.remainingPrediction.get(predictionListIndex);
					else
						predThing = EmptyOBIEInstance.emptyInstance;

					/*
					 * Clone
					 */
					BeamAssignmentTree candidate = new BeamAssignmentTree(current);

					/*
					 * Score
					 */
					PRF1 similarity = compareObjectWise(goldThing, predThing, depth);

					/*
					 * Add assignment to assignment list in tree.
					 */
					candidate.addAssignment(new BeamAssignment(goldThing, predThing, similarity));

					/*
					 * Add candidate to possible successor.
					 */
					candidates.add(candidate);

				}
			}

		}

		if (done)
			return states;

		final List<BeamAssignmentTree> successorStates = candidates.stream().sorted().limit(beamSize)
				.collect(Collectors.toList());

		return beamExploration(successorStates, beamSize, depth);

	}

	/**
	 * Compares the objects of two lists. This method calculates the max score
	 * within all pairs of those two lists. Thus it is not necessary to calculate
	 * the full Cartesian permutation but only each element of firstList with each
	 * element of secondList which results in a computational cost of n² instead of
	 * n!
	 * 
	 * Adds a penalty for each element in prediction that can not be found in gold.
	 * 
	 * @param goldList
	 * @param predictedList
	 * @return
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 */
	private PRF1 orListSimilarity(final List<IOBIEThing> goldList, final List<IOBIEThing> predictedList,
			final int depth) {

		if (goldList == null && predictedList == null) {
			return new PRF1();
		}

		if (goldList == null) {
			return new PRF1(0, 1, 0);
		}

		if (predictedList == null) {
			return new PRF1(0, 0, 1);
		}

		if (goldList.isEmpty() && predictedList.isEmpty())
			return new PRF1();

		final PRF1 bestPermutationScore = new PRF1();
		bestPermutationScore.fn++;

		int predictionPenalty = 0;

		for (final IOBIEThing pred : predictedList) {
			double bestLocalScore = 0;
			for (final IOBIEThing gold : goldList) {
				final PRF1 currentPermutationScore = compareObjectWise(gold, pred, depth);

				if (bestPermutationScore.getF1() <= currentPermutationScore.getF1()) {
					bestPermutationScore.set(currentPermutationScore);
				}
				bestLocalScore = Math.max(bestLocalScore, currentPermutationScore.getF1());
			}
			if (bestLocalScore == 0) {
				predictionPenalty++;
			}
		}
		bestPermutationScore.fp += predictionPenalty;
		return bestPermutationScore;
	}

	class BeamAssignmentTree implements Comparable<BeamAssignmentTree> {

		final private List<BeamAssignment> assignments;
		final private List<IOBIEThing> remainingGold;
		final private List<IOBIEThing> remainingPrediction;
		final public PRF1 overallSimiliarity;

		/**
		 * Initial.
		 * 
		 * @param assignments
		 * @param gold
		 * @param prediction
		 */
		public BeamAssignmentTree(List<IOBIEThing> gold, List<IOBIEThing> prediction) {
			this.assignments = new ArrayList<>();
			this.remainingGold = gold;
			this.remainingPrediction = prediction;
			this.overallSimiliarity = new PRF1();
		}

		/**
		 * Clone.
		 * 
		 * @param tree
		 */
		public BeamAssignmentTree(BeamAssignmentTree tree) {
			this.assignments = new ArrayList<>(tree.assignments);
			this.remainingGold = new ArrayList<>(tree.remainingGold);
			this.remainingPrediction = new ArrayList<>(tree.remainingPrediction);
			this.overallSimiliarity = new PRF1(tree.overallSimiliarity);
		}

		@Override
		public int compareTo(BeamAssignmentTree o) {
			return -Double.compare(overallSimiliarity.getF1(), o.overallSimiliarity.getF1());
		}

		public void addAssignment(BeamAssignment beamAssignment) {
			this.assignments.add(beamAssignment);
			this.remainingGold.remove(beamAssignment.gold);
			this.remainingPrediction.remove(beamAssignment.pred);
			this.overallSimiliarity.add(beamAssignment.similiarity);
		}

		public boolean checkBreakCondition() {
			return remainingGold.size() == 0 && remainingPrediction.size() == 0;
		}

		@Override
		public String toString() {
			return "BeamAssignmentTree [assignments=" + assignments + ", remainingGold=" + remainingGold
					+ ", remainingPrediction=" + remainingPrediction + ", overallSimiliarity=" + overallSimiliarity
					+ "]";
		}

	}

	class BeamAssignment implements Comparable<BeamAssignment> {

		final public IOBIEThing gold;
		final public IOBIEThing pred;
		final public PRF1 similiarity;

		public BeamAssignment(IOBIEThing gold, IOBIEThing pred, PRF1 similiarity) {
			this.gold = gold;
			this.pred = pred;
			this.similiarity = similiarity;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((gold == null) ? 0 : gold.hashCode());
			result = prime * result + ((pred == null) ? 0 : pred.hashCode());
			result = prime * result + ((similiarity == null) ? 0 : similiarity.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			BeamAssignment other = (BeamAssignment) obj;
			if (gold == null) {
				if (other.gold != null)
					return false;
			} else if (!gold.equals(other.gold))
				return false;
			if (pred == null) {
				if (other.pred != null)
					return false;
			} else if (!pred.equals(other.pred))
				return false;
			if (similiarity == null) {
				if (other.similiarity != null)
					return false;
			} else if (!similiarity.equals(other.similiarity))
				return false;
			return true;
		}

		@Override
		public int compareTo(BeamAssignment o) {
			return -Double.compare(this.similiarity.getF1(), o.similiarity.getF1());
		}

		@Override
		public String toString() {
			return "BeamAssignment [gold=" + gold + ", pred=" + pred + ", similiarity=" + similiarity + "]";
		}

	}
}
