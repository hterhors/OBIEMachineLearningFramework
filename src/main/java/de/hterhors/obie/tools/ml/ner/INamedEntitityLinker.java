package de.hterhors.obie.tools.ml.ner;

import java.util.Map;
import java.util.Set;

import de.hterhors.obie.tools.ml.variables.NERLClassAnnotation;
import de.hterhors.obie.tools.ml.variables.NERLIndividualAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.AbstractOBIEIndividual;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;

public interface INamedEntitityLinker {

	/**
	 * TODO: do not diff between class and individuals
	 * 
	 * @param content
	 * @return
	 */
	public Map<Class<? extends IOBIEThing>, Set<NERLClassAnnotation>> annotateClasses(final String content);

	public Map<AbstractOBIEIndividual, Set<NERLIndividualAnnotation>> annotateIndividuals(final String content);

}
