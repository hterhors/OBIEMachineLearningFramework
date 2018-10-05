package de.hterhors.obie.tools.ml.variables;

import java.io.Serializable;

import de.hterhors.obie.core.ontology.AbstractOBIEIndividual;

public class NERLIndividualAnnotation implements INERLAnnotation {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final String NAMED_ENTITY_ANNOTATION_PREFIX = "NERL_Individual_Annotation:";

	final public String annotationID;

	final public String text;

	final public int onset;

	final public AbstractOBIEIndividual relatedIndividual;

	public NERLIndividualAnnotation(String text, int offset, AbstractOBIEIndividual relatedIndividual) {

		this.text = text;
		this.onset = offset;
		this.relatedIndividual = relatedIndividual;
		this.annotationID = NAMED_ENTITY_ANNOTATION_PREFIX + relatedIndividual.name + offset + text;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((annotationID == null) ? 0 : annotationID.hashCode());
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
		NERLIndividualAnnotation other = (NERLIndividualAnnotation) obj;
		if (annotationID == null) {
			if (other.annotationID != null)
				return false;
		} else if (!annotationID.equals(other.annotationID))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "NERLIndividualAnnotation [text=" + text + ", onset=" + onset + ", relatedIndividual="
				+ relatedIndividual.name + "]";
	}

	@Override
	public String getText() {
		return text;
	}

	@Override
	public int getOnset() {
		return onset;
	}

}
