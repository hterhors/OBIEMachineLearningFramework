package de.hterhors.obie.tools.ml.dtinterpreter;

import java.util.List;
import java.util.regex.Matcher;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;

public interface IInterpreter<B extends IOBIEThing> {

	public List<IDatatypeInterpretation> interpret(String testMention);

	public IDatatypeInterpretation interpret(Class<? extends B> classType, String textMention);

	public IDatatypeInterpretation interpret(Class<? extends B> dataTypeClass, Matcher matcher);

}
