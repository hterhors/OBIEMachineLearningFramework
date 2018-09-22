package de.uni.bielefeld.sc.hterhors.psink.obie.ie.stopcrit.training;

public interface IStopTrainingCriterion {

	public boolean checkConditions(double mean) throws InterruptedException;

}
