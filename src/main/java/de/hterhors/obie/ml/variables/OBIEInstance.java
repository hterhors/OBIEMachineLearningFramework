package de.hterhors.obie.ml.variables;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import corpus.LabeledInstance;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.core.tokenizer.RegExTokenizer;
import de.hterhors.obie.core.tokenizer.SentenceSplitter;
import de.hterhors.obie.core.tokenizer.Token;
import de.hterhors.obie.ml.ner.NamedEntityLinkingAnnotations;

/**
 * The BiGram Document contains information about the annotations (training or
 * test instances), the underlying text and passage information of the text.
 * 
 * @author hterhors
 *
 *         Mar 23, 2017
 */
public final class OBIEInstance implements LabeledInstance<OBIEInstance, InstanceTemplateAnnotations>, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static final Comparator<OBIEInstance> COMPARE_BY_NAME = new Comparator<OBIEInstance>() {
		@Override
		public int compare(OBIEInstance o1, OBIEInstance o2) {
			return o1.getName().compareTo(o2.getName());
		}
	};

	private static Logger log = LogManager.getFormatterLogger(OBIEInstance.class);

	/**
	 * The documents name.
	 */
	final private String name;

	/**
	 * This object holds the (human) labeled, correct result, that should be used
	 * during training and evaluation.
	 */
	final private InstanceTemplateAnnotations goldAnnotation;

	final private String content;

	final private List<Token> tokens;

	/**
	 * Tokens indexed by position.
	 */
	final private Map<Integer, Token> fromPositionTokens = new HashMap<>();
	final private Map<Integer, Token> toPositionTokens = new HashMap<>();

	public String getName() {
		return name;
	}

	private NamedEntityLinkingAnnotations namedEntityLinkingAnnotations;

	public final Set<Class<? extends IOBIEThing>> rootClassTypes;

	public OBIEInstance(final String documentName, final String documentContent,
			final InstanceTemplateAnnotations goldAnnotations, Set<Class<? extends IOBIEThing>> rootClassTypes) {
		this.goldAnnotation = goldAnnotations;

		this.name = documentName;
		this.content = prepareDocumentContent(documentContent);

		this.tokens = RegExTokenizer.tokenize(getDocumentSentences(documentContent)).stream()
				.flatMap(t -> t.tokens.stream()).collect(Collectors.toList());

		for (Token token : tokens) {
			fromPositionTokens.put(new Integer(token.getFromCharPosition()), token);
			toPositionTokens.put(new Integer(token.getToCharPosition()), token);
		}
		this.rootClassTypes = Collections.unmodifiableSet(new HashSet<>(rootClassTypes));
	}

	private List<String> getDocumentSentences(String rawDocumentContent) {
		final List<String> sentences = SentenceSplitter.extractSentences(rawDocumentContent);
		final List<String> cleanedSentences = new ArrayList<>();

		for (String sentence : sentences) {
			// String cleanedSentence =
			// ContentCleaner.getInstance().process(sentence);
			cleanedSentences.add(sentence);
		}

		return cleanedSentences;
	}

	private String prepareDocumentContent(final String rawDocumentContent) {

		final List<String> sentences = SentenceSplitter.extractSentences(rawDocumentContent);

		final StringBuffer cleanedContent = new StringBuffer();

		for (String sentence : sentences) {
			// String cleanedSentence =
			// ContentCleaner.getInstance().process(sentence)
			cleanedContent.append(sentence);// .append(" ");
		}
		return cleanedContent.toString().trim();
	}

	public void setAnnotations(NamedEntityLinkingAnnotations namedEntityLinkingAnnotations) {
		this.namedEntityLinkingAnnotations = namedEntityLinkingAnnotations;
	}

	public int charPositionToTokenPosition(Integer characterPosition) {

		if (fromPositionTokens.containsKey(characterPosition))
			return fromPositionTokens.get(characterPosition).getIndex();

		if (toPositionTokens.containsKey(characterPosition))
			return toPositionTokens.get(characterPosition).getIndex() + 1;

		log.warn("____CONTENT_____");
		log.warn(content);
		log.warn("_____FROM____");
		fromPositionTokens.entrySet().stream()
				.filter(t -> Math.abs(t.getKey().intValue() - characterPosition.intValue()) < 20).forEach(log::warn);
		log.warn("____TO_____");
		toPositionTokens.entrySet().stream()
				.filter(t -> Math.abs(t.getKey().intValue() - characterPosition.intValue()) < 20).forEach(log::warn);
		log.warn("_________");
		log.warn("_________");

		this.tokens.forEach(log::warn);

		throw new IndexOutOfBoundsException("Can not map character position to token position: " + characterPosition);

	}

	public Token charPositionToToken(Integer characterPosition) {

		if (fromPositionTokens.containsKey(characterPosition))
			return fromPositionTokens.get(characterPosition);

		if (toPositionTokens.containsKey(characterPosition))
			return toPositionTokens.get(characterPosition);

		log.warn("_________");
		toPositionTokens.entrySet().stream()
				.filter(t -> Math.abs(t.getKey().intValue() - characterPosition.intValue()) < 20).forEach(log::warn);
		log.warn("_________");
		fromPositionTokens.entrySet().stream()
				.filter(t -> Math.abs(t.getKey().intValue() - characterPosition.intValue()) < 20).forEach(log::warn);
		log.warn("_________");
		log.warn("characterPosition = " + characterPosition);

		this.tokens.forEach(log::warn);

		throw new IndexOutOfBoundsException("Can not map character position to token: " + characterPosition);

	}

	public InstanceTemplateAnnotations getGoldAnnotation() {
		return goldAnnotation;
	}

	public List<Token> getTokens() {
		return tokens;
	}

	public String getContent() {
		return content;
	}

	@Override
	public OBIEInstance getInstance() {
		return this;
	}

	public NamedEntityLinkingAnnotations getNamedEntityLinkingAnnotations() {
		return namedEntityLinkingAnnotations;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		OBIEInstance other = (OBIEInstance) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "InternalInstance [name=" + name + "]";
	}

}
