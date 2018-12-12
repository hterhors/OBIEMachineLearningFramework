package de.hterhors.obie.ml.evaluation.evaluator;

import java.util.List;

import de.hterhors.obie.core.evaluation.PRF1;
import de.hterhors.obie.core.ontology.InvestigationRestriction;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.evaluation.IOrListCondition;

public interface IOBIEEvaluator {

	public <G extends IOBIEThing, P extends IOBIEThing> PRF1 prf1(G gold, P predictions);

	public <G extends IOBIEThing, P extends IOBIEThing> double f1(G gold, P prediction);

	public <G extends IOBIEThing, P extends IOBIEThing> double recall(G gold, P prediction);

	public <G extends IOBIEThing, P extends IOBIEThing> double precision(G gold, P prediction);

	public PRF1 prf1(List<? extends IOBIEThing> gold, List<? extends IOBIEThing> predictions);

	public double recall(List<? extends IOBIEThing> gold, List<? extends IOBIEThing> predictions);

	public double precision(List<? extends IOBIEThing> gold, List<? extends IOBIEThing> predictions);

	public double f1(List<? extends IOBIEThing> gold, List<? extends IOBIEThing> predictions);

	public void clearCache();

	public boolean isIgnoreEmptyInstancesOnEvaluation();

	public boolean isPenalizeCardinality();

	public InvestigationRestriction getInvestigationRestrictions();

	public void setInvestigationRestrictions(InvestigationRestriction investigationRestriction);

	public IOrListCondition getOrListCondition();

	public int getMaxEvaluationDepth();

	public boolean isEnableCaching();

	public int getMaxNumberOfAnnotations();

}
