package de.hterhors.obie.tools.ml.dtinterpreter;

import java.util.regex.Pattern;

public abstract class AbstractInterpreter implements IDatatypeInterpretation {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static final String PRE_BOUNDS = "(^|\\b|(?<= ))";
	public static final String POST_BOUNDS = "($|\\b|(?= ))";
	public final static String BAD_CHAR = "[^\\x20-\\x7E]+";

	final protected static String writtenNumbers = PRE_BOUNDS
			+ "(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|forteen|fifteen)";
	final protected static String digits = "\\d{1,3}((\\.|,)\\d)?\\d{0,3}";
	final protected static String connection = "\\s?(times|per|x|-|" + BAD_CHAR + ")\\s?";
	final protected static String relationLessConnection = "(\\sof.{2,5})?";
	final protected static String freeSpace_ = "[^\\d\\w\\.,=]";
	final protected static String freeSpaceQuestionMark_ = freeSpace_ + "?";
	final protected static String connection_ = "(" + freeSpace_ + "(to|and)" + freeSpace_ + "|"
			+ freeSpaceQuestionMark_ + "((\\+?-)|\\+(/|\\\\)-|±|" + freeSpace_ + ")" + freeSpaceQuestionMark_ + ")";

	final public String surfaceForm;

	public AbstractInterpreter(final String surfaceForm) {
		this.surfaceForm = surfaceForm;
	}

	public static final int PATTERN_BITMASK = Pattern.CASE_INSENSITIVE + Pattern.DOTALL;

	/**
	 * Convert unit spelling variations to the unified format.
	 * 
	 * @param unitVariation
	 * @return unified unit
	 */
	protected static String mapVariation(String unitVariation) {
		switch (unitVariation.toLowerCase()) {
		case "milligram":
			return "mg";
		case "kilo":
		case "kilogram":
			return "kg";
		case "gram":
			return "g";
		case "minutes":
		case "minute":
		case "mins":
		case "m":
			return "min";
		case "hours":
		case "h":
			return "hour";
		case "seconds":
		case "second":
			return "s";
		case "milliliter":
			return "ml";
		case "bars":
			return "bar";
		case "celsius":
			return "c";
		case "cm 3":
			return "cm3";
		case "dyns":
			return "dyn";
		case "kdyne":
		case "kdyns":
		case "kdynes":
			return "kdyn";
		case "w":
			return "week";
		case "weeks":
			return "week";
		case "d":
			return "day";
		case "days":
			return "day";
		case "months":
			return "month";
		case "years":
			return "year";
		case "y":
			return "year";
		case "u":
			return "iu";
		case "%":
			return "percentage";
		default:
			return unitVariation;
		}
	}

	protected static double mapWrittenNumbertoInt(String group) {
		switch (group.toLowerCase()) {
		case "one":
			return 1d;
		case "two":
			return 2d;
		case "three":
			return 3d;
		case "four":
			return 4d;
		case "five":
			return 5d;
		case "six":
			return 6d;
		case "seven":
			return 7d;
		case "eight":
			return 8d;
		case "nine":
			return 9d;
		case "ten":
			return 10d;
		case "eleven":
			return 11d;
		case "twelve":
			return 12d;
		case "thirteen":
			return 13d;
		case "fourteen":
			return 14d;
		case "fifteen":
			return 15d;
		}
		return Integer.MIN_VALUE;
	}

	protected static String toValue(String group) {
		return group.replaceAll(",", "");
	}

	protected static String clean(final String dosage) {

		String interprete = dosage.toLowerCase();
		/*
		 * TODO: Bad heuristic!?
		 */
		interprete = interprete.replaceAll(BAD_CHAR, "µ");
		interprete = interprete.replaceAll("\\s", "");
		interprete = interprete.replaceAll("\\.", "");
		return interprete;
	}

	public static double convertValue(double value, IDoubleUnit fromUnit, IDoubleUnit toUnit) {
		if (fromUnit.getType() == toUnit.getType())
			return value * (toUnit.getNumeratorFactor() / fromUnit.getNumeratorFactor())
					* (fromUnit.getDeterminatorFactor() / toUnit.getDeterminatorFactor());
		else
			throw new IllegalArgumentException("Can not convert " + fromUnit.getType() + " to " + toUnit.getType());
	}

	public static double convertValue(double value, ISingleUnit fromUnit, ISingleUnit toUnit) {

		return (value * fromUnit.getFactor()) / toUnit.getFactor();
	}

	@Override
	public boolean exists() {
		return surfaceForm != null;
	}
}
