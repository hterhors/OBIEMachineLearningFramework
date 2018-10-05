package de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation;

import java.lang.reflect.Field;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.ReflectionUtils;

public class DatatypeOrListConditon implements IOrListCondition {

	@Override
	public boolean isTrue(Field field) {
		if (field == null)
			return false;
		return ReflectionUtils.isAnnotationPresent(field, DatatypeProperty.class);
	}

}
