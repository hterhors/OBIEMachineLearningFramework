package de.hterhors.obie.ml.ner;

import java.util.Map;
import java.util.Set;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;

public interface INamedEntitityLinker {

	/**
	 * TODO: do not diff between class and individuals
	 * 
	 * @param content
	 * @return
	 */
	public Map<Class<? extends IOBIEThing>, Set<NERLClassAnnotation>> annotateClasses(final String content);

	public Map<AbstractIndividual, Set<NERLIndividualAnnotation>> annotateIndividuals(final String content);

}
