package de.hterhors.obie.ml.ner.dictionary;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IDictionary {

	public List<IDictionary> getSubDictionaries();

	public Map<String, Set<DictionaryEntry>> getEntries();

	public boolean containsSurfaceForm(String surfaceForm);

	public boolean containsToken(String firstToken);

}
