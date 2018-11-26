package de.hterhors.obie.ml.ner.dictionary;

import java.util.List;
import java.util.Map;
import java.util.Set;

import de.hterhors.obie.core.ontology.AbstractIndividual;

public interface IDictionary {

	public List<IDictionary> getSubDictionaries();

	public Map<AbstractIndividual, Set<DictionaryEntry>> getEntries();

	public boolean containsSurfaceForm(String surfaceForm);

	public boolean containsToken(String firstToken);

//	public int getPriority();
//
//	public void setPriority(final int priority);
//
//	public Map<DictionaryEntry, Set<AbstractIndividual>> getDictionary();
//
//	public Set<String> getMentionsForConcept(AbstractIndividual concept);
//
//	public Set<String> getAllMentions();
//
//	public Map<AbstractIndividual, Set<String>> getConceptBasedDictionary();
//
//	public boolean containsEntry(DictionaryEntry entry);
//
//	public boolean containsSurfaceForm(final String normalizedSurfaceForm);
//
//	public Set<AbstractIndividual> getConceptsForNormalizedSurfaceForm(final String normalizedSurfaceForm);
//
//	public boolean normalizedSurfaceFormMatchesConcept(String normalizedSurfaceForm, AbstractIndividual conceptID);
//
//	public boolean containsToken(String token);
//
//	public boolean noVowelsNormalizedSurfaceFormMatchesConcept(String noVowelsNormalizedSurfaceForm,
//			AbstractIndividual conceptID);
//
//	public boolean containsNoVowelsSurfaceForm(String noVowelsNormalizedSurfaceForm);
//
//	public Set<AbstractIndividual> getConceptsForNoVowelsNormalizedSurfaceForm(String noVowelsNormalizedSurfaceForm);
//
//	public boolean sortedNormalizedSurfaceFormMatchesConcept(String sortedSurfaceForm, AbstractIndividual conceptID);
//
//	public boolean containsSortedSurfaceForm(String sortedSurfaceForm);
//
//	public Set<AbstractIndividual> getConceptsForSortedNormalizedSurfaceForm(String sortedSurfaceForm);

}
