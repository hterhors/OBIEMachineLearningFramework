package de.hterhors.obie.ml.ner.candidateRetrieval;

import de.hterhors.obie.core.ontology.AbstractIndividual;

public class RetrievalCandidate implements Comparable<RetrievalCandidate> {

	public final AbstractIndividual individual;
	public final double confidence;

	public RetrievalCandidate(AbstractIndividual concept, double value) {
		this.individual = concept;
		this.confidence = value;
	}

	@Override
	public String toString() {
		return "RetrievalCandidate [individual=" + individual + ", confidence=" + confidence + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((individual == null) ? 0 : individual.hashCode());
		long temp;
		temp = Double.doubleToLongBits(confidence);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RetrievalCandidate other = (RetrievalCandidate) obj;
		if (individual == null) {
			if (other.individual != null)
				return false;
		} else if (!individual.equals(other.individual))
			return false;
		if (Double.doubleToLongBits(confidence) != Double.doubleToLongBits(other.confidence))
			return false;
		return true;
	}

	@Override
	public int compareTo(RetrievalCandidate o) {
		return -Double.compare(this.confidence, o.confidence);
	}

}
