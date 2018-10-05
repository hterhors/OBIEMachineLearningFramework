package de.hterhors.obie.ml.dtinterpreter;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.regex.Pattern;

public interface IDatatypeInterpretation extends Serializable {

	public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.########");

	public boolean exists();

	public String asFormattedString();

	public Pattern getPattern();

	public IDatatypeInterpretation normalize();
	
}
