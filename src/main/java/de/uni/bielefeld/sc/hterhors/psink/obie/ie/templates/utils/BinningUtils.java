package de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.utils;

public class BinningUtils {


	/**
	 * Helper method to generate equally distributed bins
	 * 
	 * @param size
	 *            number of bins between 0 and 1
	 * @return array of bins
	 */
	public static float[] getFloatBins(final int size) {

		float[] bins = new float[size];
		for (int i = 0; i < size; i++) {
			bins[i] = (i + 1) * (1f / size);
		}

		return bins;
	}

	/**
	 * Helper method to generate equally distributed bins
	 * 
	 * @param size
	 *            number of bins between 0 and 1
	 * @return array of bins
	 */
	public static int[] getIntegerBins(final int size) {

		int[] bins = new int[size];
		for (int i = 0; i < size; i++) {
			bins[i] = i;
		}

		return bins;
	}

}