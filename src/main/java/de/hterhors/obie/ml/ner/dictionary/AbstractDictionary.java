package de.hterhors.obie.ml.ner.dictionary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.core.tokenizer.RegExTokenizer;
import de.hterhors.obie.core.tokenizer.Token;
import de.hterhors.obie.core.tokenizer.Tokenization;

public abstract class AbstractDictionary implements IDictionary {

	private final Class<? extends IOBIEThing> ontologyClazz;

	private Set<String> surfaceForms = new HashSet<>();
	private Set<String> tokens = new HashSet<>();

	public AbstractDictionary(Class<? extends IOBIEThing> ontologyClazz) {
		this.ontologyClazz = ontologyClazz;
	}

	protected List<IDictionary> dictionaries = new ArrayList<>();

	public static final List<String> ENGLISH_STOP_WORDS = Arrays.asList("a", "an", "and", "are", "as", "at", "be",
			"but", "by", "for", "if", "in", "into", "is", "it", "no", "not", "of", "on", "or", "such", "that", "the",
			"their", "then", "there", "these", "they", "this", "to", "was", "will", "with", "very", "from", "all");

	final public static List<String> NUMBERS_STOP_WORDS = Arrays.asList("i", "ii", "iii", "1", "2", "3", "4", "5", "6",
			"7", "8", "9", "0", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "one", "two", "three",
			"four", "five", "six", "seven", "eight", "nine", "zero", "first", "second", "third");

	protected DictionaryEntry toDictionaryEntry(String type, String name) {
		surfaceForms.add(name.toLowerCase());
		for (Tokenization tokenization : RegExTokenizer.tokenize(Arrays.asList(name.toLowerCase()))) {
			for (Token token : tokenization.tokens) {
				tokens.add(token.getText());
			}
		}
		return new DictionaryEntry(type, name);
	}

	@Override
	public boolean containsSurfaceForm(String surfaceForm) {
		return surfaceForms.contains(surfaceForm.toLowerCase());
	}

	@Override
	public boolean containsToken(String surfaceForm) {
		return tokens.contains(surfaceForm.toLowerCase());
	}
}
