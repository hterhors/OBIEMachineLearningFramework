package de.hterhors.obie.ml.activelearning;

import de.hterhors.obie.ml.variables.OBIEInstance;

public class RankedInstance implements Comparable<RankedInstance> {

	protected final double entropy;
	protected final OBIEInstance instance;

	public RankedInstance(double entropy, OBIEInstance instance) {
		this.entropy = entropy;
		this.instance = instance;
	}

	@Override
	public int compareTo(RankedInstance o) {
		/*
		 * Highest entropy first
		 */
		return -Double.compare(entropy, o.entropy);
	}

	@Override
	public String toString() {
		return "EntropyInstance [entropy=" + entropy + ", instance=" + instance + "]";
	}

}
