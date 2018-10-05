package de.hterhors.obie.tools.ml.stopcrit.training;

public interface IStopTrainingCriterion {

	public boolean checkConditions(double mean) throws InterruptedException;

}
