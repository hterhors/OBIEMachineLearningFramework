package de.hterhors.obie.ml.ner.candidateRetrieval.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.ner.candidateRetrieval.AbstractCandidateRetrieval;
import de.hterhors.obie.ml.ner.candidateRetrieval.RetrievalCandidate;
import de.hterhors.obie.ml.ner.dictionary.DictionaryEntry;
import de.hterhors.obie.ml.ner.dictionary.IDictionary;

public class LuceneRetrieval extends AbstractCandidateRetrieval {

	final public static int MAX_RESULTS = 5;
	final public static double SIMILARITY_TRESHOLD = 0.7d;

	private RAMDirectory indexDir;

	private Map<Integer, String> luceneObjectMapping = new HashMap<>();

	private Map<String, List<RetrievalCandidate>> cache = new ConcurrentHashMap<>();
	private final QueryParser parser;

	private IDictionary dictionary;

	public LuceneRetrieval(IDictionary dictionary) {

		this.dictionary = dictionary;

		try {
			indexDir = new RAMDirectory();

			IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_46,
					new StandardAnalyzer(Version.LUCENE_46));
			IndexWriter indexWriter;
			indexWriter = new IndexWriter(indexDir, config);

			int index = 0;

			for (Entry<String, Set<DictionaryEntry>> entry : dictionary.getEntries().entrySet()) {
				add(indexWriter, index, entry);
				index++;
			}

			for (IDictionary subDict : dictionary.getSubDictionaries()) {
				for (Entry<String, Set<DictionaryEntry>> entry : subDict.getEntries().entrySet()) {
					add(indexWriter, index, entry);
					index++;
				}
			}

			indexWriter.prepareCommit();
			indexWriter.commit();
			indexWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		parser = new QueryParser(Version.LUCENE_46, "surfaceForm", new StandardAnalyzer(Version.LUCENE_46));
	}

	private void add(IndexWriter indexWriter, int index, Entry<String, Set<DictionaryEntry>> entry) throws IOException {
		luceneObjectMapping.put(index, entry.getKey());

		for (DictionaryEntry concept : entry.getValue()) {
			Document doc = new Document();

			doc.add(new TextField("individual", String.valueOf(index), Field.Store.YES));
			doc.add(new TextField("surfaceForm", concept.surfaceForm, Field.Store.YES));
			indexWriter.addDocument(doc);
		}
	}

	public List<RetrievalCandidate> getFuzzyCandidates(String search) {
		return getFuzzyCandidates(search, MAX_RESULTS, SIMILARITY_TRESHOLD);
	}

	public List<RetrievalCandidate> getFuzzyCandidates(String search, final int numbOfResults,
			final double minLuceneScore) {

		search = QueryParser.escape(search);

		String fuzzySearch = "";
		for (String s : search.split(" ")) {
			fuzzySearch += s + "~ ";
		}
		// System.out.println("fuzzy Search = " + fuzzySearch);
		// String fuzzySearch = search + "~";
		return getNonFuzzyCandidates(fuzzySearch.trim(), numbOfResults, minLuceneScore);
	}

	public List<RetrievalCandidate> getNonFuzzyCandidates(String search, final int numbOfResults,
			final double minLuceneScore) {

		if (cache.containsKey(search))
			return cache.get(search);

		Map<String, Float> result = new LinkedHashMap<String, Float>(numbOfResults);
		List<RetrievalCandidate> resultList = new ArrayList<RetrievalCandidate>(numbOfResults);

		if (search.trim().isEmpty())
			return resultList;

		IndexSearcher searcher;
		try {
			searcher = new IndexSearcher(DirectoryReader.open(indexDir));

			synTokenQuery(search, numbOfResults, minLuceneScore, result, searcher);
		} catch (IOException | ParseException e) {
			e.printStackTrace();
		}

		result.entrySet().stream().forEach(r -> resultList
				.add(new RetrievalCandidate(luceneObjectMapping.get(Integer.parseInt(r.getKey())), r.getValue())));
		Collections.sort(resultList);

		cache.put(search, resultList);

		return resultList;
	}

	private void synTokenQuery(String search, final int numbOfResults, final double minLuceneScore,
			Map<String, Float> result, IndexSearcher searcher) throws ParseException, IOException {

		search = QueryParser.escape(search);

		Query q = parser.parse(search);
		/*
		 * Works only in String field!!
		 */
		// Query q = new FuzzyQuery(new Term("surfaceFormTokens",
		// QueryParser.escape(search)), 2);

		TopDocs top = searcher.search(q, numbOfResults);

		for (ScoreDoc doc : top.scoreDocs) {
			if (doc.score >= minLuceneScore) {
				final String key = searcher.doc(doc.doc).get("individual");
				if (result.getOrDefault(key, 0f) < doc.score) {
					result.put(key, doc.score);
				}
			}
		}
	}

	@Override
	public Collection<RetrievalCandidate> getCandidates(Class<? extends IOBIEThing> classTypeInterface,
			String originalText) {

		originalText = originalText.toLowerCase();

		return getFuzzyCandidates(originalText);
	}

	@Override
	public IDictionary getDictionary() {
		return dictionary;
	}
}
