package de.uni.bielefeld.sc.hterhors.psink.obie.ie.ner;

import java.util.Map;
import java.util.Set;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.AbstractOBIEIndividual;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.NERLClassAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.NERLIndividualAnnotation;

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
