package de.hterhors.obie.ml.ner;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;

public interface INamedEntitityLinker extends Serializable {

	/**
	 * TODO: do not diff between class and individuals
	 * 
	 * @param content
	 * @return
	 */
	public Map<Class<? extends IOBIEThing>, Set<NERLClassAnnotation>> annotateClasses(final String instanceName,
			final String content);

	public Map<AbstractIndividual, Set<NERLIndividualAnnotation>> annotateIndividuals(final String instanceName,
			final String content);

}
