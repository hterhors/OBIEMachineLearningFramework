package de.hterhors.obie.ml.stopcrit.training;

public interface IStopTrainingCriterion {

	public boolean checkConditions(double mean) throws InterruptedException;

}
