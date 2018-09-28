package de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.OntologyModelContent;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.InvestigationRestriction;

public class ReflectionUtils {

	private static final Map<Class<? extends IOBIEThing>, List<Field>> chachedFields = new HashMap<>();
	private static final Map<Class<? extends IOBIEThing>, Map<InvestigationRestriction, Set<String>>> chachedFieldNames = new HashMap<>();
	private static final Map<Class<? extends IOBIEThing>, Map<InvestigationRestriction, List<Field>>> chachedFields_invest = new HashMap<>();
	private static final Map<Class<? extends IOBIEThing>, Map<String, Field>> chachedSingleFieldNames = new HashMap<>();

	public static List<Field> getDeclaredOntologyFields(Class<? extends IOBIEThing> clazz) {

		Objects.requireNonNull(clazz);

		if (clazz.isAnnotationPresent(DatatypeProperty.class))
			return Collections.emptyList();

		List<Field> declaredFields;

		if ((declaredFields = chachedFields.get(clazz)) == null) {
			declaredFields = new ArrayList<>();
			for (Field f : clazz.getDeclaredFields()) {
				if (!f.isAnnotationPresent(OntologyModelContent.class))
					continue;

				f.setAccessible(true);
				declaredFields.add(f);
			}
			chachedFields.put(clazz, declaredFields);
		}
		return declaredFields;
	}

	public static List<Field> getDeclaredOntologyFields(Class<? extends IOBIEThing> clazz,
			InvestigationRestriction investigationRestrictionRestrictions) {

		Objects.requireNonNull(clazz);

		if (clazz.isAnnotationPresent(DatatypeProperty.class))
			return Collections.emptyList();

		List<Field> declaredFields;

		Map<InvestigationRestriction, List<Field>> pntr;

		if ((pntr = chachedFields_invest.get(clazz)) == null) {
			pntr = new HashMap<>();
			chachedFields_invest.put(clazz, pntr);
		}

		if ((declaredFields = pntr.get(investigationRestrictionRestrictions)) == null) {
			declaredFields = new ArrayList<>();
			for (Field f : clazz.getDeclaredFields()) {

				if (!f.isAnnotationPresent(OntologyModelContent.class))
					continue;

				if (!investigationRestrictionRestrictions.investigateField(f.getName()))
					continue;

				declaredFields.add(f);
			}
			pntr.put(investigationRestrictionRestrictions, declaredFields);
		}

		return declaredFields;
	}

	public static Set<String> getDeclaredOntologyFieldNames(Class<? extends IOBIEThing> clazz,
			InvestigationRestriction investigationRestrictionRestrictions) {

		Objects.requireNonNull(clazz);

		if (clazz.isAnnotationPresent(DatatypeProperty.class))
			return Collections.emptySet();

		Set<String> declaredFieldNames;

		Map<InvestigationRestriction, Set<String>> pntr;

		if ((pntr = chachedFieldNames.get(clazz)) == null) {
			pntr = new HashMap<>();
			chachedFieldNames.put(clazz, pntr);
		}

		if ((declaredFieldNames = pntr.get(investigationRestrictionRestrictions)) == null) {
			declaredFieldNames = new HashSet<>();
			for (Field f : clazz.getDeclaredFields()) {

				if (!f.isAnnotationPresent(OntologyModelContent.class))
					continue;

				if (!investigationRestrictionRestrictions.investigateField(f.getName()))
					continue;

				declaredFieldNames.add(f.getName());
			}
			pntr.put(investigationRestrictionRestrictions, declaredFieldNames);
		}

		return declaredFieldNames;
	}

	// public static Set<String> getDeclaredOntologyFieldNames(Class<? extends
	// IOBIEThing> clazz,
	// InvestigationRestriction investigationRestrictionRestrictions) {
	//
	// Objects.requireNonNull(clazz);
	//
	// if (clazz.isAnnotationPresent(DataTypeProperty.class))
	// return Collections.emptySet();
	//
	// Set<String> declaredFieldNames;
	//
	// if ((declaredFieldNames = chachedFieldNames.get(clazz)) == null) {
	// declaredFieldNames = new HashSet<>();
	// for (Field f : clazz.getDeclaredFields()) {
	// if (!f.isAnnotationPresent(OntologyModelContent.class))
	// continue;
	// declaredFieldNames.add(f.getName());
	// }
	// chachedFieldNames.put(clazz, declaredFieldNames);
	//
	// }
	//
	// return declaredFieldNames;
	// }

	public static Field getDeclaredFieldByName(Class<? extends IOBIEThing> clazz, final String fieldName) {

		Objects.requireNonNull(clazz);

		Objects.requireNonNull(fieldName);

		Field declaredField;

		Map<String, Field> pntr;

		if ((pntr = chachedSingleFieldNames.get(clazz)) == null) {
			pntr = new HashMap<>();
			chachedSingleFieldNames.put(clazz, pntr);
		}

		if ((declaredField = pntr.get(fieldName)) == null) {
			try {
				declaredField = clazz.getDeclaredField(fieldName);
			} catch (NoSuchFieldException | SecurityException e) {
				pntr.put(fieldName, null);
				return null;
			}
			declaredField.setAccessible(true);
			pntr.put(fieldName, declaredField);
		}

		Objects.requireNonNull(declaredField);
		return declaredField;
	}

}
