package de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables;

import java.io.Serializable;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.dtinterpreter.IDatatypeInterpretation;

public class NELAnnotation implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final String NAMED_ENTITY_ANNOTATION_PREFIX = "NEL:";

	final public String annotationID;

	final public String textMention;

	final public int onset;

	final public Class<? extends IOBIEThing> classType;

	final public IDatatypeInterpretation semanticInterpretation;

	public NELAnnotation(String mentionText, int offset, Class<? extends IOBIEThing> relatedSCIOClassType,
			IDatatypeInterpretation semanticInterpretation) {

		this.semanticInterpretation = semanticInterpretation;
		this.textMention = mentionText;
		this.onset = offset;
		this.classType = relatedSCIOClassType;
		this.annotationID = NAMED_ENTITY_ANNOTATION_PREFIX + relatedSCIOClassType.getSimpleName() + offset
				+ mentionText;
	}

	public String getDTValueIfAnyElseTextMention() {
		return semanticInterpretation == null ? textMention : semanticInterpretation.normalize().asFormattedString();
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
		NELAnnotation other = (NELAnnotation) obj;
		if (annotationID == null) {
			if (other.annotationID != null)
				return false;
		} else if (!annotationID.equals(other.annotationID))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "NEL [mentionText=" + textMention + ", onset=" + onset + ", rootClassType=" + classType.getSimpleName()
				+ ", semanticInterpretation=" + semanticInterpretation + "]";
	}

}
