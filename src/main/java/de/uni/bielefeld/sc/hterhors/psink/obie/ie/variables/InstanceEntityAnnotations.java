package de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.OBIEClassFormatter;

public class InstanceEntityAnnotations implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * Since Entities only hold weak pointer via references to one another, using a
	 * Map is sensible to enable an efficient access to the entities.
	 * 
	 * key = UUID unique for PSINKAnnotation.
	 */
	private final Map<String, EntityAnnotation> entities = new HashMap<>();;

	public InstanceEntityAnnotations() {
	}

	/**
	 * Clone Constructor!
	 * 
	 * @param cloneFrom
	 */
	public InstanceEntityAnnotations(InstanceEntityAnnotations cloneFrom) {
		for (EntityAnnotation e : cloneFrom.entities.values()) {
			this.entities.put(e.annotationID, new EntityAnnotation(e));
		}
	}

	public EntityAnnotation getEntity(String entityID) {
		return entities.get(entityID);
	}

	public Collection<EntityAnnotation> getEntityAnnotations() {
		return Collections.unmodifiableCollection(entities.values());
	}

	public void addAnnotation(EntityAnnotation entity) {
		entities.put(entity.annotationID, entity);
	}

	public void removeEntity(EntityAnnotation entity) {
		entities.remove(entity.annotationID);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((entities == null) ? 0 : entities.hashCode());
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
		InstanceEntityAnnotations other = (InstanceEntityAnnotations) obj;
		if (entities == null) {
			if (other.entities != null)
				return false;
		} else if (!entities.equals(other.entities))
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();

		for (EntityAnnotation e : entities.values()) {
			builder.append("\n\t");
			builder.append(OBIEClassFormatter.format(e.getAnnotationInstance()
//					, investigationRestriction
			));
			builder.append("\n");
		}
		return builder.toString();

	}

}
