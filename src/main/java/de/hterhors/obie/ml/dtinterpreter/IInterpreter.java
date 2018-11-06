package de.hterhors.obie.ml.dtinterpreter;

import java.util.List;
import java.util.regex.Matcher;

import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;

public interface IInterpreter<B extends IOBIEThing> {

	public List<IDatatypeInterpretation> getPossibleInterpretations(String testMention);

	public IDatatypeInterpretation interpret(Class<? extends B> classType, String textMention);

	public IDatatypeInterpretation interpret(Class<? extends B> dataTypeClass, Matcher matcher);

}
