package de.hterhors.obie.tools.ml.corpus;

public interface IFoldCrossProvider {

	/**
	 * Retruns the index of the current fold.
	 * 
	 * @return
	 */
	public int getCurrentFoldIndex();

	/**
	 * Automatically increases the fold index by +1.
	 */
	public boolean nextFold();

}
