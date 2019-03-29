package de.hterhors.obie.ml.evaluation;

import java.lang.reflect.Field;

import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;

public class DatatypeOrListConditon implements IOrListCondition {

	@Override
	public boolean isTrue(Field field) {
		if (field == null)
			return false;
		return ReflectionUtils.isAnnotationPresent(field, DatatypeProperty.class);
	}

}
