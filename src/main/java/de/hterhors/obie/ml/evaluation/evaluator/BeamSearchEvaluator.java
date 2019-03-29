package de.hterhors.obie.ml.evaluation.evaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.evaluation.PRF1;
import de.hterhors.obie.core.ontology.InvestigationRestriction;
import de.hterhors.obie.core.ontology.instances.EmptyOBIEInstance;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.evaluation.IOrListCondition;

/**
 * Beam Search with parameterized beam size.
 * 
 * @author hterhors
 *
 * @date May 18, 2018
 */
public class BeamSearchEvaluator extends AbstractOBIEEvaluator {

	public static Logger log = LogManager.getFormatterLogger(BeamSearchEvaluator.class.getSimpleName());

	private final int beamSize;

	public BeamSearchEvaluator(final int beamSize) {
		this(beamSize, true, Integer.MAX_VALUE, true,
//				InvestigationRestriction.noRestrictionInstance,
				f -> false,
				Integer.MAX_VALUE, true);
	}

	/**
	 * Creates a new BeamSearchEvaluator. Searches the best assignment using the
	 * beam search algorithm. A beam size of <code>infinite</code> results in a
	 * Cartesian search complexity!
	 * 
	 * @param beamSize                         the beam size use negative for
	 *                                         unlimited.
	 * @param enableCaching
	 * @param maxEvaluationDepth
	 * @param penalizeCardinality
	 * @param investigationRestrictions
	 * @param orListCondition
	 * @param maxNumberOfAnnotations
	 * @param ignoreEmptyInstancesOnEvaluation
	 */
	public BeamSearchEvaluator(final int beamSize, boolean enableCaching, final int maxEvaluationDepth,
			final boolean penalizeCardinality,
//			InvestigationRestriction investigationRestrictions,
			IOrListCondition orListCondition, int maxNumberOfAnnotations,
			final boolean ignoreEmptyInstancesOnEvaluation) {
		super(enableCaching, penalizeCardinality,
//				investigationRestrictions, 
				orListCondition, maxEvaluationDepth,
				maxNumberOfAnnotations, ignoreEmptyInstancesOnEvaluation);
		this.beamSize = beamSize < 1 ? Integer.MAX_VALUE : beamSize;
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
	public double recall(List<? extends IOBIEThing> gold, List<? extends IOBIEThing> predictions) {
		try {
			return beamEvaluation(Collections.unmodifiableList(gold), Collections.unmodifiableList(predictions))
					.getRecall();
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
			return -Integer.MIN_VALUE;
		}
	}

	@Override
	public double precision(List<? extends IOBIEThing> gold, List<? extends IOBIEThing> predictions) {
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
	protected PRF1 explore(final List<? extends IOBIEThing> gold, final List<? extends IOBIEThing> prediction,
			final int depth) {

		final List<BeamAssignmentTree> assignments = new ArrayList<>();

		assignments.add(new BeamAssignmentTree(gold, prediction));

		final BeamAssignmentTree bestAssignments = beamExploration(assignments, beamSize, depth).get(0);

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

	class BeamAssignmentTree implements Comparable<BeamAssignmentTree> {

		final private List<BeamAssignment> assignments;
		final private List<? extends IOBIEThing> remainingGold;
		final private List<? extends IOBIEThing> remainingPrediction;
		final public PRF1 overallSimiliarity;

		/**
		 * Initial.
		 * 
		 * @param assignments
		 * @param gold
		 * @param prediction
		 */
		public BeamAssignmentTree(List<? extends IOBIEThing> gold, List<? extends IOBIEThing> prediction) {
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
