package de.hterhors.obie.ml.ner;

import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.dtinterpreter.IDatatypeInterpretation;

public class NERLClassAnnotation implements INERLAnnotation {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

//	private static final String NAMED_ENTITY_ANNOTATION_PREFIX = "NERL_Class_Annotation:";

//	final public String annotationID;

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
//		this.annotationID = NAMED_ENTITY_ANNOTATION_PREFIX + relatedSCIOClassType.getSimpleName() + offset + text;
	}

	public String getDTValueIfAnyElseTextMention() {
		return semanticInterpretation == null ? text : semanticInterpretation.normalize().asFormattedString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((classType == null) ? 0 : classType.hashCode());
		result = prime * result + onset;
		result = prime * result + ((semanticInterpretation == null) ? 0 : semanticInterpretation.hashCode());
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
		NERLClassAnnotation other = (NERLClassAnnotation) obj;
		if (classType == null) {
			if (other.classType != null)
				return false;
		} else if (!classType.equals(other.classType))
			return false;
		if (onset != other.onset)
			return false;
		if (semanticInterpretation == null) {
			if (other.semanticInterpretation != null)
				return false;
		} else if (!semanticInterpretation.equals(other.semanticInterpretation))
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
