package de.hterhors.obie.ml.activelearning;

import de.hterhors.obie.ml.variables.OBIEInstance;

public class RankedInstance implements Comparable<RankedInstance> {

	protected final double value;
	protected final OBIEInstance instance;

	public RankedInstance(double value, OBIEInstance instance) {
		this.value = value;
		this.instance = instance;
	}

	@Override
	public int compareTo(RankedInstance o) {
		/*
		 * Highest first
		 */
		return -Double.compare(value, o.value);
	}

	@Override
	public String toString() {
		return "RankedInstance [value=" + value + ", instance=" + instance + "]";
	}

}
