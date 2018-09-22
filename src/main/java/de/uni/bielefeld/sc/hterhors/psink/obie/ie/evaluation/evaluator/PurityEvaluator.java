package de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation.evaluator;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.evaluation.PRF1;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.instances.EmptyOBIEInstance;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IDataType;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation.IOrListCondition;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.exceptions.NotSupportedException;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.InvestigationRestriction;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.ReflectionUtils;

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

	/**
	 * We need this to compare lists of data types efficiently.
	 */
	private final NamedEntityLinkingEvaluator listOfDataTypesEvaluator;

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
		listOfDataTypesEvaluator = new NamedEntityLinkingEvaluator(maxEvaluationDepth, penalizeCardinality,
				investigationRestrictions, orListCondition, maxNumberOfAnnotations, ignoreEmptyInstancesOnEvaluation);
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

		final PRF1 puritySC = puritySimilarity(unModGold, unModPredictions, 0);

		final PRF1 invPuritySC = puritySimilarity(unModPredictions, unModGold, 0);

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

	private PRF1 singleEntityEvaluation(IOBIEThing gold, IOBIEThing prediction) {
		// System.out.println("prediction" +
		// OBIEFormatter.format(prediction));
		PRF1 s = compareObjectWise(gold, prediction, 0);
		// System.out.println("Single : " + s);
		return s;
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
	private PRF1 loopOverFields(IOBIEThing goldClass, IOBIEThing predictedClass, Field goldField, Field predictionField,
			final int depth) {

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
				/**
				 * Enter here for plain clustering.
				 */
				adderScore = standardSimilarity(goldList, predictionList);
			} else if ((predictionField != null || goldField != null) && orListCondition.isTrue(goldField)
					&& orListCondition.isTrue(predictionField)) {
				adderScore = orListSimilarity(goldList, predictionList, depth);
			} else {
				adderScore = puritySimilarity(goldList, predictionList, depth);
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

	private PRF1 puritySimilarity(final List<IOBIEThing> gold, final List<IOBIEThing> prediction, final int depth) {

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

}
