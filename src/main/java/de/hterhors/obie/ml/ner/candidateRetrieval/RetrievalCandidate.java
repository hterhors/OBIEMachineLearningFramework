package de.hterhors.obie.ml.ner.candidateRetrieval;

public class RetrievalCandidate implements Comparable<RetrievalCandidate> {

	public final String type;
	public final double confidence;

	public RetrievalCandidate(String concept, double value) {
		this.type = concept;
		this.confidence = value;
	}

	@Override
	public String toString() {
		return "RetrievalCandidate [individual=" + type + ", confidence=" + confidence + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((type == null) ? 0 : type.hashCode());
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
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
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
