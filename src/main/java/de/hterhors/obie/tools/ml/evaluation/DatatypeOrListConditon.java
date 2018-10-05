package de.hterhors.obie.tools.ml.evaluation;

import java.lang.reflect.Field;

import de.hterhors.obie.tools.ml.utils.ReflectionUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;

public class DatatypeOrListConditon implements IOrListCondition {

	@Override
	public boolean isTrue(Field field) {
		if (field == null)
			return false;
		return ReflectionUtils.isAnnotationPresent(field, DatatypeProperty.class);
	}

}
