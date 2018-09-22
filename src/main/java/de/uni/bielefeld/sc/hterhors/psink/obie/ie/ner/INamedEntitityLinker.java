package de.uni.bielefeld.sc.hterhors.psink.obie.ie.ner;

import java.util.Map;
import java.util.Set;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.NELAnnotation;

public interface INamedEntitityLinker {

	public Map<Class<? extends IOBIEThing>, Set<NELAnnotation>> annotate(final String content);

}
