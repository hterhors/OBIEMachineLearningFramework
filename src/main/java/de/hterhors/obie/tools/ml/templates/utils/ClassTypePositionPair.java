package de.hterhors.obie.tools.ml.templates.utils;

import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;

public class ClassTypePositionPair {

	final public Class<? extends IOBIEThing> classType;
	final public long position;

	public ClassTypePositionPair(Class<? extends IOBIEThing> classType, long onset) {
		this.classType = classType;
		this.position = onset;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((classType == null) ? 0 : classType.hashCode());
		result = prime * result + (int) (position ^ (position >>> 32));
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
		ClassTypePositionPair other = (ClassTypePositionPair) obj;
		if (classType == null) {
			if (other.classType != null)
				return false;
		} else if (!classType.equals(other.classType))
			return false;
		if (position != other.position)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "Pair [className=" + classType + ", onset=" + position + "]";
	}

}
