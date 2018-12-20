package de.hterhors.obie.ml.utils;

import java.lang.reflect.Field;
import java.util.List;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.InvestigationRestriction;
import de.hterhors.obie.core.ontology.OntologyInitializer;
import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IDatatype;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.core.tools.visualization.graphml.templates.NamedIndividual;

public class OBIEClassFormatter {

	private static final String ONE_DEPTH = "    ";

	public static String format(IOBIEThing scioClass
//			, InvestigationRestriction investigationRestriction
	) {
		if (scioClass == null)
			return "null";
		return format(scioClass, false
//				, investigationRestriction
		);
	}

//	public static String format(IOBIEThing scioClass) {
//		if (scioClass == null)
//			return "null";
//		return format(scioClass, false
////				, InvestigationRestriction.noRestrictionInstance
//		);
//	}

	public static String format(IOBIEThing scioClass, boolean printAll) {
		try {

			return toStringUsingRelfections(scioClass, 0, printAll
//					, InvestigationRestriction.noRestrictionInstance
			);

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

//	public static String format(IOBIEThing scioClass, boolean printAll
////			,
////			InvestigationRestriction investigationRestriction
//	) {
//		try {
//
//			return toStringUsingRelfections(scioClass, 0, printAll
////					, investigationRestriction
//			);
//
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		return null;
//	}

	private static String toStringUsingRelfections(IOBIEThing c, int depth, boolean printAll
//			,
//			InvestigationRestriction investigationRestriction
	) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
		StringBuilder sb = new StringBuilder();

		if (c == null)
			return null;

		if (printAll) {
			sb.append("(" + c.getCharacterOnset() + "-" + c.getCharacterOffset() + ")");
		}

		if (ReflectionUtils.isAnnotationPresent(c.getClass(), DatatypeProperty.class))
			sb.append(getDepth(depth) + ReflectionUtils.simpleName(c.getClass()) + ": \"" + c.getTextMention() + "\" ("
					+ ((IDatatype) c).getInterpretedValue() + ")");
		else {
			AbstractIndividual individual = ((AbstractIndividual) c.getClass()
					.getField(OntologyInitializer.INDIVIDUAL_FIELD_NAME).get(c));
			sb.append(getDepth(depth) + ReflectionUtils.simpleName(c.getClass()));
			sb.append(individual == null ? " " : (" " + (printAll ? individual.nameSpace : "") + individual.name));
		}

		sb.append("\n");
		depth++;

		List<Field> fields = ReflectionUtils.getSlots(c.getClass(), c.getInvestigationRestriction());

		for (Field slot : fields) {

//			if (!investigationRestriction.investigateField(field.getName()))
//				continue;

			slot.setAccessible(true);
			sb.append(getDepth(depth) + slot.getName() + ":");
			if (slot.get(c) == null) {
				sb.append(ONE_DEPTH + "null\n");
			} else {

				if (ReflectionUtils.isAnnotationPresent(slot, RelationTypeCollection.class)) {
					@SuppressWarnings("unchecked")
					List<IOBIEThing> list = (List<IOBIEThing>) slot.get(c);
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
							if (ReflectionUtils.isAnnotationPresent(l.getClass(), DatatypeProperty.class)) {
								sb.append("\n");
								sb.append(getDepth(depth + 1) + ReflectionUtils.simpleName(l.getClass()) + ": \""
										+ l.getTextMention() + "\" (" + ((IDatatype) l).getInterpretedValue() + ")");
//							} else if (l.getClass().isAnnotationPresent(NamedIndividual.class)) {
//								sb.append("\n");
//								sb.append(getDepth(depth + 1) + l.getClass()));
							} else {
								sb.append("\n");
								sb.append(toStringUsingRelfections(l, depth + 1, printAll
//										, investigationRestriction
								));
							}
						}
					}
					sb.append("\n");
				} else {
					if (printAll) {
						sb.append(ONE_DEPTH);
						sb.append("(" + ((IOBIEThing) slot.get(c)).getCharacterOnset() + "-"
								+ ((IOBIEThing) slot.get(c)).getCharacterOffset() + ": \""
								+ ((IOBIEThing) slot.get(c)).getTextMention() + "\")");
					}
					IOBIEThing cn = (IOBIEThing) slot.get(c);
					if (ReflectionUtils.isAnnotationPresent(cn.getClass(), DatatypeProperty.class)) {
						sb.append(getDepth(depth + 1) + ReflectionUtils.simpleName(cn.getClass()) + ": \""
								+ cn.getTextMention() + "\" (" + ((IDatatype) cn).getInterpretedValue() + ")\n");
//					} else if (cn.getClass().isAnnotationPresent(NamedIndividual.class)) {
//						sb.append(ONE_DEPTH + cn.getClass()) + "\n");
					} else {
						sb.append("\n");
						sb.append(toStringUsingRelfections(cn, depth + 1, printAll
//								, investigationRestriction
						));
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
