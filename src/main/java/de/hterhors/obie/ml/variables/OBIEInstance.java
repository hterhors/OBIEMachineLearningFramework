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
 * The OBIEInstance contains information about the annotations (training or test
 * instances), the underlying text and passage information of the text.
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

	public static enum EInstanceType {
		TRAIN, DEV, TEST, UNSET;

	}

	private static Logger log = LogManager.getFormatterLogger(OBIEInstance.class);

	/**
	 * The instance name.
	 */
	final private String name;

	/**
	 * This object holds the (human) labeled, correct result, that should be used
	 * during training and evaluation.
	 */
	final private InstanceTemplateAnnotations goldAnnotation;

	final private String content;

	final private List<Token> tokens;

	private EInstanceType instanceType = EInstanceType.UNSET;

	public EInstanceType getInstanceType() {
		return instanceType;
	}

	public OBIEInstance setInstanceType(EInstanceType instanceType) {
		this.instanceType = instanceType;
		return this;
	}

	/**
	 * Tokens indexed by position.
	 */
	final private Map<Integer, Token> onsetCharPositionTokens = new HashMap<>();
	final private Map<Integer, Token> offsetCharPositionTokens = new HashMap<>();

	public String getName() {
		return name;
	}

	private NamedEntityLinkingAnnotations namedEntityLinkingAnnotations;

	public final Set<Class<? extends IOBIEThing>> rootClassTypes;

	public OBIEInstance(final String documentName, final String documentContent,
			final InstanceTemplateAnnotations goldAnnotations, Set<Class<? extends IOBIEThing>> rootClassTypes) {
		this.goldAnnotation = goldAnnotations;

		this.name = documentName;

		this.content = documentContent;

		this.tokens = RegExTokenizer.tokenize(SentenceSplitter.extractSentences(this.content)).stream()
				.flatMap(t -> t.tokens.stream()).collect(Collectors.toList());

		for (Token token : tokens) {
			onsetCharPositionTokens.put(new Integer(token.getOnsetCharPosition()), token);
			offsetCharPositionTokens.put(new Integer(token.getOffsetCharPosition()), token);
		}
		this.rootClassTypes = Collections.unmodifiableSet(new HashSet<>(rootClassTypes));
	}

	public void setNERLAnnotations(NamedEntityLinkingAnnotations namedEntityLinkingAnnotations) {
		this.namedEntityLinkingAnnotations = namedEntityLinkingAnnotations;
	}

	public int charPositionToTokenPosition(Integer characterPosition) {

		if (onsetCharPositionTokens.containsKey(characterPosition))
			return onsetCharPositionTokens.get(characterPosition).getIndex();

		if (offsetCharPositionTokens.containsKey(characterPosition))
			return offsetCharPositionTokens.get(characterPosition).getIndex() + 1;

		log.warn("____CONTENT_____");
		log.warn(content);
		log.warn("_____FROM____");
		onsetCharPositionTokens.entrySet().stream()
				.filter(t -> Math.abs(t.getKey().intValue() - characterPosition.intValue()) < 20).forEach(log::warn);
		log.warn("____TO_____");
		offsetCharPositionTokens.entrySet().stream()
				.filter(t -> Math.abs(t.getKey().intValue() - characterPosition.intValue()) < 20).forEach(log::warn);
		log.warn("_________");
		log.warn("characterPosition = " + characterPosition);
		log.warn("_________");

		this.tokens.forEach(log::warn);

		throw new IndexOutOfBoundsException("Can not map character position to token position: " + characterPosition);
	}

	public Token charPositionToToken(Integer characterPosition) {

		if (onsetCharPositionTokens.containsKey(characterPosition))
			return onsetCharPositionTokens.get(characterPosition);

		if (offsetCharPositionTokens.containsKey(characterPosition))
			return offsetCharPositionTokens.get(characterPosition);

		log.warn("____CONTENT_____");
		log.warn(content);
		log.warn("_____FROM____");
		onsetCharPositionTokens.entrySet().stream()
				.filter(t -> Math.abs(t.getKey().intValue() - characterPosition.intValue()) < 20).forEach(log::warn);
		log.warn("____TO_____");
		offsetCharPositionTokens.entrySet().stream()
				.filter(t -> Math.abs(t.getKey().intValue() - characterPosition.intValue()) < 20).forEach(log::warn);
		log.warn("_________");
		log.warn("characterPosition = " + characterPosition);
		log.warn("_________");

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

	public NamedEntityLinkingAnnotations getEntityAnnotations() {
		return namedEntityLinkingAnnotations;
	}

//		@Override
//	public int hashCode() {
//		final int prime = 31;
//		int result = 1;
//		result = prime * result + ((name == null) ? 0 : name.hashCode());
//		return result;
//	}
//
//	@Override
//	public boolean equals(Object obj) {
//		if (this == obj)
//			return true;
//		if (obj == null)
//			return false;
//		if (getClass() != obj.getClass())
//			return false;
//		OBIEInstance other = (OBIEInstance) obj;
//		if (name == null) {
//			if (other.name != null)
//				return false;
//		} else if (!name.equals(other.name))
//			return false;
//		return true;
//	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((goldAnnotation == null) ? 0 : goldAnnotation.hashCode());
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
		if (goldAnnotation == null) {
			if (other.goldAnnotation != null)
				return false;
		} else if (!goldAnnotation.equals(other.goldAnnotation))
			return false;
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
