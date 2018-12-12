package de.hterhors.obie.ml.variables;

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import de.hterhors.obie.core.ontology.InvestigationRestriction;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.utils.OBIEClassFormatter;
import de.hterhors.obie.ml.utils.OBIEUtils;

public class TemplateAnnotation implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public TemplateAnnotation(TemplateAnnotation e) {
		this.rootClassType = e.rootClassType;
		this.templateAnnotation = OBIEUtils.deepClone(e.templateAnnotation);
		this.annotationID = e.annotationID;
		this.initClass = e.initClass;
//		this.investigationRestriction = e.investigationRestriction;
	}

	/**
	 * The root class type of the annotation. If the scioClass changes during
	 * sampling, the rootClassType remains always the same. It is used for the scope
	 * of the factors.
	 */
	final public Class<? extends IOBIEThing> rootClassType;

	private IOBIEThing templateAnnotation;

	final long annotationID;

//	private InvestigationRestriction investigationRestriction;

	public IOBIEThing getInitializationThing() {
		return initClass;
	}

	/**
	 * TODO What if there are multiple initClasses / multiple rootTypeClasses ?
	 */
	private final IOBIEThing initClass;

	private static final AtomicLong annotationCounter = new AtomicLong();

	public TemplateAnnotation(Class<? extends IOBIEThing> rootClassType, IOBIEThing obieClass
//			,
//			InvestigationRestriction investigationRestriction
			) {
		this.rootClassType = rootClassType;
		this.templateAnnotation = obieClass;
		this.annotationID = annotationCounter.addAndGet(1);
		this.initClass = OBIEUtils.deepClone(obieClass);
//		this.investigationRestriction = investigationRestriction;

	}

	public void update(IOBIEThing templateAnnotation) {
		/**
		 * TODO: do we need to update the investigationRestriction as well?
		 */
		this.templateAnnotation = templateAnnotation;
	}

	public IOBIEThing getThing() {
		return templateAnnotation;
	}

	public long getAnnotationID() {
		return annotationID;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (annotationID ^ (annotationID >>> 32));
		result = prime * result + ((initClass == null) ? 0 : initClass.hashCode());
//		result = prime * result + ((investigationRestriction == null) ? 0 : investigationRestriction.hashCode());
		result = prime * result + ((rootClassType == null) ? 0 : rootClassType.hashCode());
		result = prime * result + ((templateAnnotation == null) ? 0 : templateAnnotation.hashCode());
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
		TemplateAnnotation other = (TemplateAnnotation) obj;
		if (annotationID != other.annotationID)
			return false;
		if (initClass == null) {
			if (other.initClass != null)
				return false;
		} else if (!initClass.equals(other.initClass))
			return false;
//		if (investigationRestriction == null) {
//			if (other.investigationRestriction != null)
//				return false;
//		} else if (!investigationRestriction.equals(other.investigationRestriction))
//			return false;
		if (rootClassType == null) {
			if (other.rootClassType != null)
				return false;
		} else if (!rootClassType.equals(other.rootClassType))
			return false;
		if (templateAnnotation == null) {
			if (other.templateAnnotation != null)
				return false;
		} else if (!templateAnnotation.equals(other.templateAnnotation))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "InternalAnnotation [" + OBIEClassFormatter.format(templateAnnotation) + "]";
	}

//	public InvestigationRestriction getInvestigationRestriction() {
//		return investigationRestriction;
//	}

}
