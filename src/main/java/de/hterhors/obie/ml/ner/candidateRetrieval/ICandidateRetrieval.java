package de.hterhors.obie.ml.ner.candidateRetrieval;

import java.util.Collection;

import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.ner.dictionary.IDictionary;

public interface ICandidateRetrieval {

	public Collection<RetrievalCandidate> getCandidates(Class<? extends IOBIEThing> classTypeInterface,
			String originalText);

	public IDictionary getDictionary();
}
