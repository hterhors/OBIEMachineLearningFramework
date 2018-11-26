package de.hterhors.obie.ml.ner.candidateRetrieval.jaccard;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.metrics.JaccardSimilarities;
import de.hterhors.obie.ml.ner.candidateRetrieval.AbstractCandidateRetrieval;
import de.hterhors.obie.ml.ner.candidateRetrieval.RetrievalCandidate;
import de.hterhors.obie.ml.ner.dictionary.DictionaryEntry;
import de.hterhors.obie.ml.ner.dictionary.IDictionary;

public class JaccardRetrieval extends AbstractCandidateRetrieval {

	final public static double NGRAM_SIMILARITY_TRESHOLD = 0.5d;

	private final int N_GRAM_SIZE = 3;

	/**
	 * setOfnGrams, diseaseID
	 */
	private final Map<BitSet, Set<AbstractIndividual>> ngrammap;

	private Map<String, List<RetrievalCandidate>> cache = new ConcurrentHashMap<>();

	/*
	 * NGram index mapping.
	 */
	public static Map<String, Integer> ngramIndex = new HashMap<>();

	private IDictionary dictionary;

	public JaccardRetrieval(IDictionary dictionary) {

		this.dictionary = dictionary;
		this.ngrammap = new HashMap<>();

		Map<Set<String>, Set<AbstractIndividual>> ngramsString = new HashMap<>();

		Set<String> allNGrams = new HashSet<>();

		for (Entry<AbstractIndividual, Set<DictionaryEntry>> entry : dictionary.getEntries().entrySet()) {
			addForDictionary(ngramsString, allNGrams, entry);
		}

		for (IDictionary subDict : dictionary.getSubDictionaries()) {
			for (Entry<AbstractIndividual, Set<DictionaryEntry>> entry : subDict.getEntries().entrySet()) {
				addForDictionary(ngramsString, allNGrams, entry);

			}
		}

		for (String ngram : allNGrams) {
			ngramIndex.put(ngram, ngramIndex.size());
		}

		for (Entry<Set<String>, Set<AbstractIndividual>> e : ngramsString.entrySet()) {
			BitSet ngramsBitSet = toBitSet(e.getKey());
			ngrammap.put(ngramsBitSet, e.getValue());
		}

	}

	private void addForDictionary(Map<Set<String>, Set<AbstractIndividual>> ngramsString, Set<String> allNGrams,
			Entry<AbstractIndividual, Set<DictionaryEntry>> entry) {
		final AbstractIndividual concept = entry.getKey();

		for (DictionaryEntry e : entry.getValue()) {

			final Set<String> grams = getBagOfNGram(e.surfaceForm, N_GRAM_SIZE);

			allNGrams.addAll(grams);

			ngramsString.putIfAbsent(grams, new HashSet<>());
			ngramsString.get(grams).add(concept);
		}
	}

	private BitSet toBitSet(Set<String> grams) {

		BitSet bitSet = new BitSet(ngramIndex.size());
		for (String ngram : grams) {
			if (ngramIndex.containsKey(ngram))
				bitSet.set(ngramIndex.get(ngram), true);
		}
		return bitSet;

	}

	private BitSet getBitSetOfNGram(final String text, final int charachterNGramSize) {

		BitSet bongam = new BitSet(ngramIndex.size());

		for (int i = 0; i < text.length() - (charachterNGramSize - 1); i++) {
			String ngram = text.substring(i, i + charachterNGramSize).intern();
			if (ngramIndex.containsKey(ngram))
				bongam.set(ngramIndex.get(ngram), true);
		}

		return bongam;
	}

	public static Set<String> getBagOfNGram(final String text, final int charachterNGramSize) {

		Set<String> bongam = new HashSet<String>();

		for (int i = 0; i < text.length() - (charachterNGramSize - 1); i++) {
			String nGramS = text.substring(i, i + charachterNGramSize).intern();
			bongam.add(nGramS);
		}

		return bongam;
	}

	@Override
	public Collection<RetrievalCandidate> getCandidates(Class<? extends IOBIEThing> classTypeInterface,
			String originalText) {

		originalText = originalText.toLowerCase();

		if (cache.containsKey(originalText))
			return cache.get(originalText);

		final BitSet bitSetGrams = getBitSetOfNGram(originalText, N_GRAM_SIZE);

		List<RetrievalCandidate> ids = Collections.synchronizedList(new ArrayList<>());

		ngrammap.entrySet().parallelStream().forEach(entry -> {

			final double js = JaccardSimilarities.jaccardSimilarity(entry.getKey(), bitSetGrams);
			if (js >= NGRAM_SIMILARITY_TRESHOLD) {
				for (AbstractIndividual jaccardCandidateConcept : entry.getValue()) {
					ids.add(new RetrievalCandidate(jaccardCandidateConcept, js));
				}
			}
		});

		cache.put(originalText, ids);

		return ids.subList(0, Math.min(ids.size(), 10));
	}

	@Override
	public IDictionary getDictionary() {
		return dictionary;
	}

}
