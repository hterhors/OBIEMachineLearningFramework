package de.hterhors.obie.ml.evaluation.evaluator;

import java.util.List;

import de.hterhors.obie.core.evaluation.PRF1;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.evaluation.IOrListCondition;
import de.hterhors.obie.ml.run.InvestigationRestriction;

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

	public IOrListCondition getOrListCondition();

	public int getMaxEvaluationDepth();

	public boolean isEnableCaching();

	public int getMaxNumberOfAnnotations();
//	
//	public PRF1 prf1(List<IOBIEThing> gold, List<IOBIEThing> predictions);
//	
//	public PRF1 prf1(IOBIEThing gold, IOBIEThing predictions);
//	
//	double f1(IOBIEThing gold, IOBIEThing prediction);
//	
//	double recall(IOBIEThing gold, IOBIEThing prediction);
//	
//	double precision(IOBIEThing gold, IOBIEThing prediction);
//	
//	double recall(List<IOBIEThing> gold, List<IOBIEThing> predictions);
//	
//	double precision(List<IOBIEThing> gold, List<IOBIEThing> predictions);
//	
//	double f1(List<IOBIEThing> gold, List<IOBIEThing> predictions);
//	
//	public void clearCache();
//	
//	public boolean isIgnoreEmptyInstancesOnEvaluation();
//	
//	public boolean isPenalizeCardinality();
//	
//	public InvestigationRestriction getInvestigationRestrictions();
//	
//	public IOrListCondition getOrListCondition();
//	
//	public int getMaxEvaluationDepth();
//	
//	public boolean isEnableCaching();
//	
//	public int getMaxNumberOfAnnotations();

}
