package de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus;

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
