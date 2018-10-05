package de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation.evaluator;

import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.evaluation.PRF1;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation.IOrListCondition;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.exceptions.NotSupportedException;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.InvestigationRestriction;

/**
 * 
 * Copied from: Amigo et al. 2011: "Combining Evaluation Metrics via the
 * Unanimous Improvement Ratio and its Application to Clustering Tasks"
 * 
 * 
 * Being C the set of clusters to be evaluated, L the set of categories
 * (reference distribution) and N the number of clustered items, Purity is
 * computed by taking the weighted average of maximal precision values: Purity =
 * SUM_i (|Ci| / N) * max_j Precision(C_i , L_j) where the precision of a
 * cluster C_i for a given category L_j is defined as: Precision(C_i , L_j ) =
 * |C_i SUBSET L_j| / |C_i| Purity penalizes the noise in a cluster, but it does
 * not reward grouping items from the same category together; if we simply make
 * one cluster per item, we reach trivially a maximum purity value. Inverse
 * Purity focuses on the cluster with maximum recall for each category. Inverse
 * Purity is defined as: Inverse Purity = SUM_i (|L_i| / N) * max_j
 * Precision(L_i, C_j)
 * 
 * @author hterhors
 *
 * @date May 17, 2018
 */
public class PurityEvaluator extends AbstractOBIEEvaluator {

	public static Logger log = LogManager.getFormatterLogger(PurityEvaluator.class.getSimpleName());

	public PurityEvaluator(boolean enableCaching, final int maxEvaluationDepth, final boolean penalizeCardinality,
			InvestigationRestriction investigationRestrictions, int maxNumberOfAnnotations,
			final boolean ignoreEmptyInstancesOnEvaluation) {
		this(enableCaching, maxEvaluationDepth, penalizeCardinality, investigationRestrictions, f -> false,
				maxNumberOfAnnotations, ignoreEmptyInstancesOnEvaluation);
	}

	public PurityEvaluator(boolean enableCaching, final int maxEvaluationDepth, final boolean penalizeCardinality,
			InvestigationRestriction investigationRestrictions, IOrListCondition orListCondition,
			int maxNumberOfAnnotations, final boolean ignoreEmptyInstancesOnEvaluation) {
		super(enableCaching, penalizeCardinality, investigationRestrictions, orListCondition, maxEvaluationDepth,
				maxNumberOfAnnotations, ignoreEmptyInstancesOnEvaluation);
	}

	@Override
	public double recall(IOBIEThing gold, IOBIEThing prediction) {
		throw new NotSupportedException("Recall is not supported for purity measurement.");
	}

	@Override
	public double precision(IOBIEThing gold, IOBIEThing prediction) {
		throw new NotSupportedException("Precision is not supported for purity measurement.");
	}

	@Override
	public double recall(List<IOBIEThing> gold, List<IOBIEThing> predictions) {
		throw new NotSupportedException("Recall is not supported for purity measurement.");
	}

	@Override
	public double precision(List<IOBIEThing> gold, List<IOBIEThing> predictions) {
		throw new NotSupportedException("Precision is not supported for purity measurement.");
	}

	final double alpha = 0.5;

	/**
	 * 
	 * van Rijsbergen’s F-measure combines them into a single measure of efficiency
	 * as follows
	 * 
	 * F(R, P) = 1 / ( α( 1 / P ) + (1 − α)( 1 / R ) )
	 * 
	 */
	@Override
	public double f1(List<? extends IOBIEThing> gold, List<? extends IOBIEThing> predictions) {

		final List<IOBIEThing> unModGold = Collections.unmodifiableList(gold);
		final List<IOBIEThing> unModPredictions = Collections.unmodifiableList(predictions);

		final PRF1 puritySC = explore(unModGold, unModPredictions, 0);

		final PRF1 invPuritySC = explore(unModPredictions, unModGold, 0);

		final double purityN = puritySC.tp + puritySC.fn;
		final double invPurityN = invPuritySC.tp + invPuritySC.fn;

		if (purityN == 0 && invPurityN == 0)
			return 0;

		double purity = puritySC.tp / purityN;

		double invPurity = invPuritySC.tp / invPurityN;

		double F_Rijsbergen = 1 / (alpha * (1 / purity) + ((1 - alpha) * (1 / invPurity)));

		return F_Rijsbergen;
	}

	/**
	 * 
	 * van Rijsbergen’s F-measure combines them into a single measure of efficiency
	 * as follows
	 * 
	 * F(R, P) = 1 / ( α( 1 / P ) + (1 − α)( 1 / R ) )
	 * 
	 */
	@Override
	public double f1(final IOBIEThing gold, final IOBIEThing prediction) {

		final PRF1 puritySC = compareObjectWise(gold, prediction, 0);
		final PRF1 invPuritySC = compareObjectWise(prediction, gold, 0);

		final double purityN = puritySC.tp + puritySC.fn;
		final double invPurityN = invPuritySC.tp + invPuritySC.fn;

		double purity = puritySC.tp / purityN;
		double invPurity = invPuritySC.tp / invPurityN;

		double F_Rijsbergen = 1 / (alpha * (1 / purity) + ((1 - alpha) * (1 / invPurity)));

		return F_Rijsbergen;
	}

	@Override
	public PRF1 prf1(List<? extends IOBIEThing> gold, List<? extends IOBIEThing> predictions) {
		throw new NotSupportedException("PRF1 is not supported for purity measurement.");
	}

	public PRF1 prf1(IOBIEThing gold, IOBIEThing prediction) {
		throw new NotSupportedException("PRF1 is not supported for purity measurement.");
	}

	protected PRF1 explore(final List<IOBIEThing> gold, final List<IOBIEThing> prediction, final int depth) {

		PRF1 purityScore = new PRF1();

		for (IOBIEThing goldThing : gold) {

			double maxTP = 0;
			PRF1 bestScore = new PRF1();
			for (IOBIEThing predThing : prediction) {

				PRF1 currentScore = compareObjectWise(goldThing, predThing, depth);
				final double currentTP = currentScore.tp;
				if (currentTP > maxTP) {
					maxTP = currentTP;
					bestScore = currentScore;
				}

			}

			purityScore.add(bestScore);
		}

		return purityScore;
	}

}
