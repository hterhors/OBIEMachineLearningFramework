package de.hterhors.obie.ml.evaluation.evaluator;

import java.util.List;

import de.hterhors.obie.core.evaluation.PRF1;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.evaluation.IOrListCondition;
import de.hterhors.obie.ml.run.InvestigationRestriction;

public interface IOBIEEvaluator {

	public PRF1 prf1(List<? extends IOBIEThing> gold, List<? extends IOBIEThing> predictions);

	public PRF1 prf1(IOBIEThing gold, IOBIEThing predictions);

	double f1(IOBIEThing gold, IOBIEThing prediction);

	double recall(IOBIEThing gold, IOBIEThing prediction);

	double precision(IOBIEThing gold, IOBIEThing prediction);

	double recall(List<IOBIEThing> gold, List<IOBIEThing> predictions);

	double precision(List<IOBIEThing> gold, List<IOBIEThing> predictions);

	double f1(List<? extends IOBIEThing> gold, List<? extends IOBIEThing> predictions);

	public void clearCache();

	public boolean isIgnoreEmptyInstancesOnEvaluation();

	public boolean isPenalizeCardinality();

	public InvestigationRestriction getInvestigationRestrictions();

	public IOrListCondition getOrListCondition();

	public int getMaxEvaluationDepth();

	public boolean isEnableCaching();

	public int getMaxNumberOfAnnotations();

}
