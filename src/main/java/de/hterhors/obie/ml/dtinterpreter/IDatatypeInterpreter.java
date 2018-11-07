package de.hterhors.obie.ml.dtinterpreter;

import java.io.Serializable;
import java.util.List;
import java.util.regex.Matcher;

import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;

public interface IDatatypeInterpreter<B extends IOBIEThing> extends Serializable {

	public List<IDatatypeInterpretation> getPossibleInterpretations(String testMention);

	public IDatatypeInterpretation interpret(Class<? extends B> datatypeClass, String textMention);

	public IDatatypeInterpretation interpret(Class<? extends B> datatypeClass, Matcher matcher);

}
