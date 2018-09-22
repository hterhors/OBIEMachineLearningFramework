package de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.NamedIndividual;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.OntologyModelContent;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IDataType;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.InvestigationRestriction;

public class OBIEClassFormatter {

	private static final String ONE_DEPTH = "    ";

	public static String format(IOBIEThing scioClass, InvestigationRestriction investigationRestriction) {
		if (scioClass == null)
			return "null";
		return format(scioClass, false, investigationRestriction);
	}

	public static String format(IOBIEThing scioClass) {
		if (scioClass == null)
			return "null";
		return format(scioClass, false, InvestigationRestriction.noRestrictionInstance);
	}

	public static String format(IOBIEThing scioClass, boolean printAll) {
		try {

			return toStringUsingRelfections(scioClass, 0, printAll, InvestigationRestriction.noRestrictionInstance);

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String format(IOBIEThing scioClass, boolean printAll,
			InvestigationRestriction investigationRestriction) {
		try {

			return toStringUsingRelfections(scioClass, 0, printAll, investigationRestriction);

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private static String toStringUsingRelfections(IOBIEThing c, int depth, boolean printAll,
			InvestigationRestriction investigationRestriction) throws IllegalArgumentException, IllegalAccessException {
		StringBuilder sb = new StringBuilder();

		if (c == null)
			return null;

		if (printAll) {
			sb.append("(" + c.getCharacterOnset() + "-" + c.getCharacterOffset() + ")");
		}

		if (c.getClass().isAnnotationPresent(DatatypeProperty.class))
			sb.append(getDepth(depth) + c.getClass().getSimpleName() + ": \"" + c.getTextMention() + "\" ("
					+ ((IDataType) c).getSemanticValue() + ")");
		else
			sb.append(getDepth(depth) + c.getClass().getSimpleName());

		sb.append("\n");
		depth++;
		List<Field> fields = Arrays.asList(c.getClass().getDeclaredFields()).stream()
				.filter(f -> f.isAnnotationPresent(OntologyModelContent.class)).collect(Collectors.toList());

		for (Field field : fields) {

			if (!investigationRestriction.investigateField(field.getName()))
				continue;

			field.setAccessible(true);
			sb.append(getDepth(depth) + field.getName() + ":");
			if (field.get(c) == null) {
				sb.append(ONE_DEPTH + "null\n");
			} else {

				if (field.isAnnotationPresent(RelationTypeCollection.class)) {
					@SuppressWarnings("unchecked")
					List<IOBIEThing> list = (List<IOBIEThing>) field.get(c);
					if (list.isEmpty()) {
						sb.append(ONE_DEPTH + "{}");
					}
					for (IOBIEThing l : list) {
						if (l == null) {
							sb.append("\nnull");
						} else {
							if (printAll) {
								sb.append(ONE_DEPTH);
								sb.append("(" + l.getCharacterOnset() + "-" + l.getCharacterOffset() + ": \""
										+ l.getTextMention() + "\")");
							}
							if (l.getClass().isAnnotationPresent(DatatypeProperty.class)) {
								sb.append("\n");
								sb.append(getDepth(depth + 1) + l.getClass().getSimpleName() + ": \""
										+ l.getTextMention() + "\" (" + ((IDataType) l).getSemanticValue() + ")");
							} else if (l.getClass().isAnnotationPresent(NamedIndividual.class)) {
								sb.append("\n");
								sb.append(getDepth(depth + 1) + l.getClass().getSimpleName());
							} else {
								sb.append("\n");
								sb.append(toStringUsingRelfections(l, depth + 1, printAll, investigationRestriction));
							}
						}
					}
					sb.append("\n");
				} else {
					if (printAll) {
						sb.append(ONE_DEPTH);
						sb.append("(" + ((IOBIEThing) field.get(c)).getCharacterOnset() + "-"
								+ ((IOBIEThing) field.get(c)).getCharacterOffset() + ": \""
								+ ((IOBIEThing) field.get(c)).getTextMention() + "\")");
					}
					IOBIEThing cn = (IOBIEThing) field.get(c);
					if (cn.getClass().isAnnotationPresent(DatatypeProperty.class)) {
						sb.append(getDepth(depth + 1) + cn.getClass().getSimpleName() + ": \"" + cn.getTextMention()
								+ "\" (" + ((IDataType) cn).getSemanticValue() + ")\n");
					} else if (cn.getClass().isAnnotationPresent(NamedIndividual.class)) {
						sb.append(ONE_DEPTH + cn.getClass().getSimpleName() + "\n");
					} else {
						sb.append("\n");
						sb.append(toStringUsingRelfections(cn, depth + 1, printAll, investigationRestriction));
					}
				}
			}

		}

		depth--;
		return sb.toString();
	}

	private static String getDepth(int i) {
		StringBuffer depth = new StringBuffer();
		for (int j = 0; j < i; j++) {
			depth.append(ONE_DEPTH);
		}
		return depth.toString();
	}

}
