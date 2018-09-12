package de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation.evaluator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.evaluation.PRF1;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation.IOrListCondition;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.InvestigationRestriction;

public abstract class AbstractOBIEEvaluator implements IEvaluator {

	protected final boolean penalizeCardinality;

	protected final InvestigationRestriction investigationRestrictions;

	/**
	 * This condition returns true for lists that matches the "OR"-list
	 * paradigm. An comparison of two "OR"-lists returns a score of 1 if the
	 * predicted list contains at least one element of the gold-list and no fps.
	 */
	protected final IOrListCondition orListCondition;

	/**
	 * Maximum depth for sub*-properties which should influence the evaluation
	 * score.
	 */
	protected final int maxEvaluationDepth;

	protected Map<CacheKey, PRF1> cache = new ConcurrentHashMap<>(1000000);

	protected final boolean enableCaching;

	protected final int maxNumberOfAnnotations;

	/**
	 * Whether empty instances should be removed before evaluating or not.
	 */
	protected final boolean ignoreEmptyInstancesOnEvaluation;

	public static class CacheKey {
		final private IOBIEThing goldClass;
		final private IOBIEThing predictedClass;
		final InvestigationRestriction samplingRestrictions;

		public CacheKey(IOBIEThing goldClass, IOBIEThing predictedClass,
				InvestigationRestriction investigationRestrictions) {
			this.goldClass = goldClass;
			this.predictedClass = predictedClass;
			this.samplingRestrictions = investigationRestrictions;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((goldClass == null) ? 0 : goldClass.hashCode());
			result = prime * result + ((samplingRestrictions == null) ? 0 : samplingRestrictions.hashCode());
			result = prime * result + ((predictedClass == null) ? 0 : predictedClass.hashCode());
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
			CacheKey other = (CacheKey) obj;
			if (goldClass == null) {
				if (other.goldClass != null)
					return false;
			} else if (!goldClass.equals(other.goldClass))
				return false;
			if (samplingRestrictions == null) {
				if (other.samplingRestrictions != null)
					return false;
			} else if (!samplingRestrictions.equals(other.samplingRestrictions))
				return false;
			if (predictedClass == null) {
				if (other.predictedClass != null)
					return false;
			} else if (!predictedClass.equals(other.predictedClass))
				return false;
			return true;
		}

	}

	public AbstractOBIEEvaluator(boolean enableCaching, boolean penalizeCardinality,
			InvestigationRestriction propertyRestrictions, IOrListCondition orListCondition, int maxEvaluationDepth,
			final int maxNumberOfAnnotations, final boolean ignoreEmptyInstancesOnEvaluation) {
		this.enableCaching = enableCaching;
		this.penalizeCardinality = penalizeCardinality;
		this.investigationRestrictions = propertyRestrictions;
		this.orListCondition = orListCondition;
		this.maxEvaluationDepth = maxEvaluationDepth;
		this.maxNumberOfAnnotations = maxNumberOfAnnotations;
		this.ignoreEmptyInstancesOnEvaluation = ignoreEmptyInstancesOnEvaluation;
	}

	public void clearCache() {
		cache.clear();
	}

	public boolean isPenalizeCardinality() {
		return penalizeCardinality;
	}

	public InvestigationRestriction getInvestigationRestrictions() {
		return investigationRestrictions;
	}

	public IOrListCondition getOrListCondition() {
		return orListCondition;
	}

	public int getMaxEvaluationDepth() {
		return maxEvaluationDepth;
	}

	public boolean isEnableCaching() {
		return enableCaching;
	}

	public int getMaxNumberOfAnnotations() {
		return maxNumberOfAnnotations;
	}

	public boolean isIgnoreEmptyInstancesOnEvaluation() {
		return ignoreEmptyInstancesOnEvaluation;
	}

}
