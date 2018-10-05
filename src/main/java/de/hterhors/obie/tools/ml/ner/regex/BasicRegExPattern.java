package de.hterhors.obie.tools.ml.ner.regex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import de.hterhors.obie.core.OntologyAnalyzer;
import de.hterhors.obie.core.ontology.AbstractOBIEIndividual;
import de.hterhors.obie.core.ontology.IndividualFactory;
import de.hterhors.obie.core.ontology.OntologyInitializer;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.tools.ml.utils.ReflectionUtils;

public abstract class BasicRegExPattern {

	/**
	 * Standard set of stop words.
	 */
	public static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList("a", "an", "and", "are", "as", "at", "be",
			"but", "by", "for", "if", "in", "into", "is", "it", "no", "not", "of", "on", "or", "such", "that", "the",
			"their", "then", "there", "these", "they", "this", "to", "was", "will", "with"));

	public abstract Set<String> getAdditionalStopWords();

	private static final String SPECIAL_CHARS = "\\W|_";

	public static final String CAMEL_CASE_SPLIT_PATTERN = "(?<!(^|[A-Z" + SPECIAL_CHARS + "]))(?=[A-Z" + SPECIAL_CHARS
			+ "])|(?<!^)(?=[A-Z" + SPECIAL_CHARS + "][a-z" + SPECIAL_CHARS + "])";

	public static final String PRE_BOUNDS = "(\\b|(?<= ))";
	public static final String POST_BOUNDS = "(\\b|(?= ))";
	public final static String BAD_CHAR = "[^\\x20-\\x7E]+";

	public static final int PATTERN_BITMASK = Pattern.CASE_INSENSITIVE + Pattern.DOTALL;

	private static final int COULD_NOT_GET_INDIVIDUAL_FACTORY_ERROR = -98765;

	protected static String buildRegExpr(final String param1, final String[] param2, final String param3) {

		StringBuffer param2Builer = new StringBuffer();

		if (param2 != null && param2.length > 0) {
			for (int i = 0; i < param2.length; i++) {
				param2Builer.append("(");
				param2Builer.append(".?" + param2[i]);
				if (i + 1 != param2.length)
					param2Builer.append("(-)?");
				param2Builer.append(")?");
			}
		}

		return Pattern.quote(param1) + "(" + (param2Builer.length() == 0 ? "" : Pattern.quote(param2Builer.toString()))
				+ ")?" + (param3 == null || param3.isEmpty() ? "" : "(.?" + Pattern.quote(param3) + ")?");
	}

	protected static String buildRegExpr(final String param1, final String param2, final String[] param3,
			final String param4) {

		StringBuffer param3Builer = new StringBuffer();

		if (param3 != null && param3.length > 0) {
			for (int i = 0; i < param3.length - 1; i++) {
				param3Builer.append(".?" + param3[i]);
				param3Builer.append("|");
			}
			param3Builer.append(".?" + param3[param3.length - 1]);
		}

		return "(" + Pattern.quote(param1) + "(.?" + Pattern.quote(param2) + ")?|" + Pattern.quote(param2) + ")("
				+ (param3Builer.length() == 0 ? "" : "(" + Pattern.quote(param3Builer.toString()) + ")?")
				+ (param4 == null || param4.isEmpty() ? "" : "(.?" + Pattern.quote(param4) + ")?") + ")?";
	}

	/**
	 * 
	 * @param rootClassType interface of the OBIE Class e.g. ITreatment
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <R extends IOBIEThing> Map<Class<? extends R>, Set<Pattern>> autoGeneratePatternForClasses(
			Class<? extends IOBIEThing> rootClassType) {

		Map<Class<? extends R>, Set<Pattern>> autoGeneratedPattern = new HashMap<>();
		Set<Class<? extends IOBIEThing>> relatedRootClasses = OntologyAnalyzer
				.getRelatedClassTypesUnderRoot(rootClassType);

		for (Class<? extends IOBIEThing> obieClassType : relatedRootClasses) {

			if (ReflectionUtils.isAnnotationPresent(obieClassType, DatatypeProperty.class) )
				continue;

			List<String> names = new ArrayList<>();
			/*
			 * TODO: take ontology name instead of className
			 */
			for (String w : obieClassType.getSimpleName().split(CAMEL_CASE_SPLIT_PATTERN)) {

				w = w.replaceAll(SPECIAL_CHARS, "");
				if (STOP_WORDS.contains(w.toLowerCase()) || getAdditionalStopWords().contains(w.toLowerCase()))
					continue;

				if (w.length() < getMinTokenlength())
					continue;

				names.add(w);

			}

			if (names.isEmpty())
				continue;

			Pattern p = null;
			if (names.size() == 1) {
				p = Pattern.compile(PRE_BOUNDS + buildRegExpr(names.get(0), null, null) + POST_BOUNDS, PATTERN_BITMASK);
			} else if (names.size() == 2) {
				p = Pattern.compile(
						PRE_BOUNDS + buildRegExpr(names.get(0), new String[] { names.get(1) }, null) + POST_BOUNDS,
						PATTERN_BITMASK);
			} else if (names.size() == 3) {
				p = Pattern.compile(PRE_BOUNDS
						+ buildRegExpr(names.get(0), names.get(1), new String[] { names.get(2) }, null) + POST_BOUNDS,
						PATTERN_BITMASK);
			} else if (names.size() == 4) {
				p = Pattern.compile(PRE_BOUNDS
						+ buildRegExpr(names.get(0), names.get(1), new String[] { names.get(2) }, names.get(3))
						+ POST_BOUNDS, PATTERN_BITMASK);
			} else if (names.size() == 5) {
				p = Pattern.compile(
						PRE_BOUNDS + buildRegExpr(names.get(0), names.get(1),
								new String[] { names.get(2), names.get(3) }, names.get(4)) + POST_BOUNDS,
						PATTERN_BITMASK);
			} else {
				p = Pattern.compile(
						PRE_BOUNDS + buildRegExpr(names.get(0), names.get(1),
								new String[] { names.get(2), names.get(3) }, names.get(4)) + POST_BOUNDS,
						PATTERN_BITMASK);
			}

			autoGeneratedPattern.putIfAbsent((Class<? extends R>) obieClassType, new HashSet<>());
			autoGeneratedPattern.get(obieClassType).add(p);
		}

		return autoGeneratedPattern;
	}

	@SuppressWarnings("unchecked")
	public Map<AbstractOBIEIndividual, Set<Pattern>> autoGeneratePatternForIndividuals(
			Class<? extends IOBIEThing> rootClassType) {

		Map<AbstractOBIEIndividual, Set<Pattern>> autoGeneratedPattern = new HashMap<>();

		Set<Class<? extends IOBIEThing>> relatedRootClasses = OntologyAnalyzer
				.getRelatedClassTypesUnderRoot(rootClassType);

		for (Class<? extends IOBIEThing> obieClassType : relatedRootClasses) {

			if (ReflectionUtils.isAnnotationPresent(obieClassType, DatatypeProperty.class) )
				continue;

			try {
				Collection<AbstractOBIEIndividual> individuals = ((IndividualFactory<AbstractOBIEIndividual>) ReflectionUtils
						.getDeclaredFieldByName(obieClassType, OntologyInitializer.INDIVIDUAL_FACTORY_FIELD_NAME)
						.get(null)).getIndividuals();

				for (AbstractOBIEIndividual individual : individuals) {

					List<String> names = new ArrayList<>();

					for (String w : individual.name.split(CAMEL_CASE_SPLIT_PATTERN)) {
						w = w.replaceAll(SPECIAL_CHARS, "");
						if (STOP_WORDS.contains(w.toLowerCase()) || getAdditionalStopWords().contains(w.toLowerCase()))
							continue;

						if (w.length() < getMinTokenlength())
							continue;

						names.add(w);
					}

					if (names.isEmpty())
						continue;

					autoGeneratedPattern.putIfAbsent(individual, new HashSet<>());

					Pattern p = null;
					if (names.size() == 1) {
						p = Pattern.compile(PRE_BOUNDS + buildRegExpr(names.get(0), null, null) + POST_BOUNDS,
								PATTERN_BITMASK);
					} else if (names.size() == 2) {
						p = Pattern.compile(PRE_BOUNDS + buildRegExpr(names.get(0), new String[] { names.get(1) }, null)
								+ POST_BOUNDS, PATTERN_BITMASK);
					} else if (names.size() == 3) {
						p = Pattern.compile(PRE_BOUNDS
								+ buildRegExpr(names.get(0), names.get(1), new String[] { names.get(2) }, null)
								+ POST_BOUNDS, PATTERN_BITMASK);
					} else if (names.size() == 4) {
						p = Pattern
								.compile(
										PRE_BOUNDS + buildRegExpr(names.get(0), names.get(1),
												new String[] { names.get(2) }, names.get(3)) + POST_BOUNDS,
										PATTERN_BITMASK);
					} else if (names.size() == 5) {
						p = Pattern
								.compile(PRE_BOUNDS
										+ buildRegExpr(names.get(0), names.get(1),
												new String[] { names.get(2), names.get(3) }, names.get(4))
										+ POST_BOUNDS, PATTERN_BITMASK);
					} else {
						p = Pattern
								.compile(PRE_BOUNDS
										+ buildRegExpr(names.get(0), names.get(1),
												new String[] { names.get(2), names.get(3) }, names.get(4))
										+ POST_BOUNDS, PATTERN_BITMASK);
					}

					autoGeneratedPattern.get(individual).add(p);
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(COULD_NOT_GET_INDIVIDUAL_FACTORY_ERROR);
			}

		}

		return autoGeneratedPattern;
	}

	public abstract int getMinTokenlength();

}
