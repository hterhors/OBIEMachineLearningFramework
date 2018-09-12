package de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.UUID;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.utils.OBIEUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.OBIEClassFormatter;

public class EntityAnnotation implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public EntityAnnotation(EntityAnnotation e) {
		this.rootClassType = e.rootClassType;
		this.obieAnnotation = OBIEUtils.deepConstructorClone(e.obieAnnotation);
		this.annotationID = e.annotationID;
		this.initClass = e.initClass;
	}

	/**
	 * Performs a deep clone on the given object using the serialization and
	 * de-serialization. This method can be used to clone collections of classes
	 * that implements Serialization.
	 */
	public static IOBIEThing deepSerializeClone(IOBIEThing scioClass) {

		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(scioClass);

			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			return (IOBIEThing) ois.readObject();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * The root class type of the annotation. If the scioClass changes during
	 * sampling, the rootClassType remains always the same. It is used for the
	 * scope of the factors.
	 */
	final public Class<? extends IOBIEThing> rootClassType;

	private IOBIEThing obieAnnotation;
	final String annotationID;

	public IOBIEThing getInitializationClass() {
		return initClass;
	}

	/**
	 * TODO What if there are multiple initClasses / multiple rootTypeClasses ?
	 */
	private final IOBIEThing initClass;

	public EntityAnnotation(Class<? extends IOBIEThing> rootClassType, IOBIEThing obieClass) {
		this.initClass = OBIEUtils.deepConstructorClone(obieClass);
		this.rootClassType = rootClassType;
		this.annotationID = UUID.randomUUID().toString();
		this.obieAnnotation = obieClass;

	}

	public void setAnnotationInstance(IOBIEThing annotationInstance) {
		this.obieAnnotation = annotationInstance;
	}

	public IOBIEThing getAnnotationInstance() {
		return obieAnnotation;
	}

	public String getAnnotationID() {
		return annotationID;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((annotationID == null) ? 0 : annotationID.hashCode());
		result = prime * result + ((obieAnnotation == null) ? 0 : obieAnnotation.hashCode());
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
		EntityAnnotation other = (EntityAnnotation) obj;
		if (annotationID == null) {
			if (other.annotationID != null)
				return false;
		} else if (!annotationID.equals(other.annotationID))
			return false;
		if (obieAnnotation == null) {
			if (other.obieAnnotation != null)
				return false;
		} else if (!obieAnnotation.equals(other.obieAnnotation))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "InternalAnnotation [" + OBIEClassFormatter.format(obieAnnotation) + "]";
	}

}
