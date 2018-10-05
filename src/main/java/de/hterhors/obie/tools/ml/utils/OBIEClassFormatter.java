package de.hterhors.obie.tools.ml.utils;

import java.lang.reflect.Field;
import java.util.List;

import de.hterhors.obie.tools.ml.run.InvestigationRestriction;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.AbstractOBIEIndividual;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.OntologyInitializer;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IDatatype;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.tools.visualization.graphml.templates.NamedIndividual;

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
			InvestigationRestriction investigationRestriction)
			throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
		StringBuilder sb = new StringBuilder();

		if (c == null)
			return null;

		if (printAll) {
			sb.append("(" + c.getCharacterOnset() + "-" + c.getCharacterOffset() + ")");
		}

		if (ReflectionUtils.isAnnotationPresent(c.getClass(), DatatypeProperty.class) )
			sb.append(getDepth(depth) + c.getClass().getSimpleName() + ": \"" + c.getTextMention() + "\" ("
					+ ((IDatatype) c).getSemanticValue() + ")");
		else {
			AbstractOBIEIndividual individual = ((AbstractOBIEIndividual) c.getClass()
					.getField(OntologyInitializer.INDIVIDUAL_FIELD_NAME).get(c));
			sb.append(getDepth(depth) + c.getClass().getSimpleName());
			sb.append(individual == null ? " " : (" " + (printAll ? individual.nameSpace : "") + individual.name));
		}

		sb.append("\n");
		depth++;

		List<Field> fields = ReflectionUtils.getDeclaredOntologyFields(c.getClass());

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
							if (ReflectionUtils.isAnnotationPresent(l.getClass(), DatatypeProperty.class) ) {
								sb.append("\n");
								sb.append(getDepth(depth + 1) + l.getClass().getSimpleName() + ": \""
										+ l.getTextMention() + "\" (" + ((IDatatype) l).getSemanticValue() + ")");
//							} else if (l.getClass().isAnnotationPresent(NamedIndividual.class)) {
//								sb.append("\n");
//								sb.append(getDepth(depth + 1) + l.getClass().getSimpleName());
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
					if (ReflectionUtils.isAnnotationPresent(cn.getClass(), DatatypeProperty.class) ) {
						sb.append(getDepth(depth + 1) + cn.getClass().getSimpleName() + ": \"" + cn.getTextMention()
								+ "\" (" + ((IDatatype) cn).getSemanticValue() + ")\n");
//					} else if (cn.getClass().isAnnotationPresent(NamedIndividual.class)) {
//						sb.append(ONE_DEPTH + cn.getClass().getSimpleName() + "\n");
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
