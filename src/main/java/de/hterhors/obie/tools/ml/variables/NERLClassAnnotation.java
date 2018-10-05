package de.hterhors.obie.tools.ml.variables;

import de.hterhors.obie.tools.ml.dtinterpreter.IDatatypeInterpretation;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;

public class NERLClassAnnotation implements INERLAnnotation {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final String NAMED_ENTITY_ANNOTATION_PREFIX = "NERL_Class_Annotation:";

	final public String annotationID;

	final public String text;

	final public int onset;

	final public Class<? extends IOBIEThing> classType;

	final public IDatatypeInterpretation semanticInterpretation;

	public NERLClassAnnotation(String text, int offset, Class<? extends IOBIEThing> relatedSCIOClassType,
			IDatatypeInterpretation semanticInterpretation) {

		this.semanticInterpretation = semanticInterpretation;
		this.text = text;
		this.onset = offset;
		this.classType = relatedSCIOClassType;
		this.annotationID = NAMED_ENTITY_ANNOTATION_PREFIX + relatedSCIOClassType.getSimpleName() + offset + text;
	}

	public String getDTValueIfAnyElseTextMention() {
		return semanticInterpretation == null ? text : semanticInterpretation.normalize().asFormattedString();
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
		NERLClassAnnotation other = (NERLClassAnnotation) obj;
		if (annotationID == null) {
			if (other.annotationID != null)
				return false;
		} else if (!annotationID.equals(other.annotationID))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "NERLClassAnnotation [text=" + text + ", onset=" + onset + ", rootClassType=" + classType.getSimpleName()
				+ ", semanticInterpretation=" + semanticInterpretation + "]";
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
