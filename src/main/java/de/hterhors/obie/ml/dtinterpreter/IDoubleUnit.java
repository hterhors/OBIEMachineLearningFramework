package de.hterhors.obie.ml.dtinterpreter;

public interface IDoubleUnit extends IUnit {

	public IDoubleUnitType getType();

	public double getNumeratorFactor();

	public double getDeterminatorFactor();

}
