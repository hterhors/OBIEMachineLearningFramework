package de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation.evaluator;

import java.util.List;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.evaluation.PRF1;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation.IOrListCondition;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.InvestigationRestriction;

public interface IOBIEEvaluator {

	public PRF1 prf1(List<? extends IOBIEThing> gold, List<? extends IOBIEThing> predictions);

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
