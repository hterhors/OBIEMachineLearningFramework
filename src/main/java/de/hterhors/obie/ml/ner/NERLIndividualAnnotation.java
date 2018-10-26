package de.hterhors.obie.ml.ner;

import java.io.Serializable;

import de.hterhors.obie.core.ontology.AbstractIndividual;

public class NERLIndividualAnnotation implements INERLAnnotation {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

//	private static final String NAMED_ENTITY_ANNOTATION_PREFIX = "NERL_Individual_Annotation:";

//	final public String annotationID;

	final public String text;

	final public int onset;

	final public AbstractIndividual relatedIndividual;

	public NERLIndividualAnnotation(String text, int offset, AbstractIndividual relatedIndividual) {

		this.text = text;
		this.onset = offset;
		this.relatedIndividual = relatedIndividual;
//		this.annotationID = NAMED_ENTITY_ANNOTATION_PREFIX + relatedIndividual.name + offset + text;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + onset;
		result = prime * result + ((relatedIndividual == null) ? 0 : relatedIndividual.hashCode());
		result = prime * result + ((text == null) ? 0 : text.hashCode());
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
		if (onset != other.onset)
			return false;
		if (relatedIndividual == null) {
			if (other.relatedIndividual != null)
				return false;
		} else if (!relatedIndividual.equals(other.relatedIndividual))
			return false;
		if (text == null) {
			if (other.text != null)
				return false;
		} else if (!text.equals(other.text))
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
