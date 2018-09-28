package de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.SuperRootClasses;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.tokenizer.Token;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.ner.regex.BasicRegExPattern;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.InBetweenContextTemplate.Scope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.scope.OBIEFactorScope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.ReflectionUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.TemplateAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.NERLClassAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;
import factors.Factor;
import learning.Vector;

/**
 * This template creates feature in form of an in between context. Each feature
 * contains the parent class annotations and its property slot annotation and
 * the text in between. Further we capture the
 * 
 * @author hterhors
 *
 * @date Jan 15, 2018
 */
public class InBetweenContextTemplate extends AbstractOBIETemplate<Scope> {

	private static final String SPLITTER = " ";

	private static final String LEFT = "<";

	private static final String RIGHT = ">";

	private static final String END_DOLLAR = "$";

	private static final String START_CIRCUMFLEX = "^";

	private static final int MIN_TOKEN_LENGTH = 2;

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final int MAX_TOKENS_DISTANCE = 10;

	private static Logger log = LogManager.getFormatterLogger(InBetweenContextTemplate.class.getName());

	/**
	 * Whether distant supervision is enabled for this template or not. This effects
	 * the way of calculating the factors and features!
	 */
	private final boolean enableDistantSupervision;

	public InBetweenContextTemplate(OBIERunParameter parameter) {
		super(parameter);
		this.enableDistantSupervision = parameter.exploreOnOntologyLevel;
	}

	static class Position {

		final String classNameType;
		final int tokenIndex;

		public Position(String classNameType, int tokenIndex) {
			this.classNameType = classNameType;
			this.tokenIndex = tokenIndex;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((classNameType == null) ? 0 : classNameType.hashCode());
			result = prime * result + tokenIndex;
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
			Position other = (Position) obj;
			if (classNameType == null) {
				if (other.classNameType != null)
					return false;
			} else if (!classNameType.equals(other.classNameType))
				return false;
			if (tokenIndex != other.tokenIndex)
				return false;
			return true;
		}

	}

	class Scope extends OBIEFactorScope {

		final OBIEInstance internalInstance;
		final Position fromPosition;
		final Position toPosition;

		public Scope(Set<Class<? extends IOBIEThing>> influencedVariable, final Position fromPosition,
				final Position toPosition, Class<? extends IOBIEThing> entityRootClassType,
				final OBIEInstance internalInstance) {
			super(influencedVariable, entityRootClassType, InBetweenContextTemplate.this, internalInstance,
					fromPosition, toPosition, entityRootClassType);
			this.internalInstance = internalInstance;
			this.fromPosition = fromPosition;
			this.toPosition = toPosition;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		for (TemplateAnnotation entity : state.getCurrentPrediction().getTemplateAnnotations()) {
			addFactorRecursive(factors, state.getInstance(), entity.rootClassType, null,
					entity.get());
		}

		return factors;
	}

	private void addFactorRecursive(final List<Scope> factors, OBIEInstance internalInstance,
			Class<? extends IOBIEThing> rootClassType, final IOBIEThing parent, IOBIEThing child) {

		if (child == null)
			return;

		/*
		 * Add factor parent - child relation
		 */
		if (parent != null) {
			final Set<Class<? extends IOBIEThing>> influencedVariables = new HashSet<>();
			if (enableDistantSupervision) {

				if (internalInstance.getNamedEntityLinkingAnnotations().containsClassAnnotations(child.getClass())
						&& internalInstance.getNamedEntityLinkingAnnotations().containsClassAnnotations(parent.getClass())) {
					Set<NERLClassAnnotation> parentNeras = internalInstance.getNamedEntityLinkingAnnotations()
							.getClassAnnotations(parent.getClass()).stream().collect(Collectors.toSet());

					Set<NERLClassAnnotation> childNeras;
					if (child.getClass().isAnnotationPresent(DatatypeProperty.class)) {
						childNeras = internalInstance.getNamedEntityLinkingAnnotations()
								.getClassAnnotationsByTextMention(child.getClass(), child.getTextMention());
					} else {
						childNeras = internalInstance.getNamedEntityLinkingAnnotations()
								.getClassAnnotations(child.getClass()).stream().collect(Collectors.toSet());
					}

					if (childNeras != null) {

						for (NERLClassAnnotation parentNera : parentNeras) {
							for (NERLClassAnnotation childNera : childNeras) {

								Integer fromPosition = Integer
										.valueOf(parentNera.onset + parentNera.text.length());
								Class<? extends IOBIEThing> classType1 = parentNera.classType;
								Integer toPosition = Integer.valueOf(childNera.onset);
								Class<? extends IOBIEThing> classType2 = childNera.classType;
								/*
								 * Switch "from" and "to" if from is after to position.
								 */
								if (fromPosition > toPosition) {
									fromPosition = Integer.valueOf(childNera.onset + childNera.text.length());
									classType1 = childNera.classType;

									toPosition = Integer.valueOf(parentNera.onset);
									classType2 = parentNera.classType;
								}
								addFactor(factors, influencedVariables, fromPosition, toPosition, classType1,
										classType2, internalInstance, rootClassType);

							}
						}
					} else {
						/*
						 * TODO: no child neras found. to nothing? This happens if the ner for data type
						 * properties failed. e.g. the string we search is fifteen to 25 ml. This can
						 * not be pares by the semantic interpretation. Thus the child neras are emtpy.
						 */
					}
				}

			} else {
				Integer fromPosition = parent.getCharacterOffset();
				Integer toPosition = child.getCharacterOnset();

				if (fromPosition != null && toPosition != null) {
					Class<? extends IOBIEThing> classType1 = parent.getClass();
					Class<? extends IOBIEThing> classType2 = child.getClass();
					/*
					 * Switch "from" and "to" if from is after to position.
					 */
					if (fromPosition > toPosition) {
						fromPosition = child.getCharacterOffset();
						toPosition = parent.getCharacterOnset();
						classType1 = child.getClass();
						classType2 = parent.getClass();
					}
					addFactor(factors, influencedVariables, fromPosition, toPosition, classType1, classType2,
							internalInstance, rootClassType);
				}
			}
		}
		ReflectionUtils.getDeclaredOntologyFields(child.getClass()).forEach(field -> {
			try {
				if (field.isAnnotationPresent(RelationTypeCollection.class)) {
					for (IOBIEThing listObject : (List<IOBIEThing>) field.get(child)) {
						addFactorRecursive(factors, internalInstance, rootClassType, child, listObject);
					}
				} else {
					addFactorRecursive(factors, internalInstance, rootClassType, child, (IOBIEThing) field.get(child));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

	}

	private void addFactor(final List<Scope> factors, final Set<Class<? extends IOBIEThing>> influencedVariables,
			Integer fromPosition, Integer toPosition, Class<? extends IOBIEThing> classType1,
			Class<? extends IOBIEThing> classType2, OBIEInstance internalInstance,
			Class<? extends IOBIEThing> rootEntityType) {
		try {

			/*
			 * Inclusive
			 */
			int fromTokenIndex = internalInstance.charPositionToTokenPosition(fromPosition);
			/*
			 * Exclusive
			 */
			int toTokenIndex = internalInstance.charPositionToTokenPosition(toPosition);

			if (toTokenIndex - fromTokenIndex <= MAX_TOKENS_DISTANCE && toTokenIndex - fromTokenIndex >= 0) {
				factors.add(new Scope(influencedVariables, new Position(classType1.getSimpleName(), fromTokenIndex),
						new Position(classType2.getSimpleName(), toTokenIndex), rootEntityType, internalInstance));

				for (Class<? extends IOBIEThing> rootClassType1 : classType1.getAnnotation(SuperRootClasses.class)
						.get()) {
					if (!rootClassType1.isAnnotationPresent(DatatypeProperty.class))

						for (Class<? extends IOBIEThing> rootClassType2 : classType2
								.getAnnotation(SuperRootClasses.class).get()) {

							if (!rootClassType2.isAnnotationPresent(DatatypeProperty.class))

								/*
								 * Add features for root classes of annotations.
								 */

								factors.add(new Scope(influencedVariables,
										new Position(rootClassType1.getSimpleName(), fromTokenIndex),
										new Position(rootClassType2.getSimpleName(), toTokenIndex), rootEntityType,
										internalInstance));

						}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.warn(classType1);
			System.exit(1);
		}

	}

	@Override
	public void computeFactor(Factor<Scope> factor) {

		Vector featureVector = factor.getFeatureVector();

		List<Token> tokens = factor.getFactorScope().internalInstance.getTokens();

		final String fromName = factor.getFactorScope().fromPosition.classNameType;
		final int fromTokenIndex = factor.getFactorScope().fromPosition.tokenIndex;

		final String toName = factor.getFactorScope().toPosition.classNameType;
		final int toTokenIndex = factor.getFactorScope().toPosition.tokenIndex;

		StringBuffer fullTokenFeature = new StringBuffer(LEFT + fromName + RIGHT + SPLITTER);

		// plus 2 for start and end symbol
		String[] inBetweenContext = new String[toTokenIndex - fromTokenIndex + 2];
		inBetweenContext[0] = START_CIRCUMFLEX;
		int index = 1;
		for (int i = fromTokenIndex; i < toTokenIndex; i++) {
			/*
			 * Each token as single feature.
			 */
			StringBuffer feature = new StringBuffer(LEFT + fromName + RIGHT + SPLITTER);
			feature.append(tokens.get(i).getText());
			feature.append(SPLITTER);
			feature.append(LEFT + toName + RIGHT);
			featureVector.set(feature.toString(), true);

			inBetweenContext[index++] = tokens.get(i).getText();

			/*
			 * Collect all in between tokens.
			 */
			fullTokenFeature.append(tokens.get(i).getText());
			fullTokenFeature.append(SPLITTER);
		}
		fullTokenFeature.append(LEFT + toName + RIGHT);
		featureVector.set(fullTokenFeature.toString(), true);

		inBetweenContext[index] = END_DOLLAR;
		if (toTokenIndex - fromTokenIndex != 0)
			getTokenNgrams(featureVector, fromName, toName, inBetweenContext);

	}

	private void getTokenNgrams(Vector featureVector, String fromClassName, String toClassName, String[] tokens) {

		final int maxNgramSize = tokens.length;
		for (int ngram = 1; ngram < maxNgramSize; ngram++) {
			for (int i = 0; i < maxNgramSize - 1; i++) {

				/*
				 * Do not include start symbol.
				 */
				if (i + ngram == 1)
					continue;

				/*
				 * Break if size exceeds token length
				 */
				if (i + ngram > maxNgramSize)
					break;

				StringBuffer fBuffer = new StringBuffer();
				for (int t = i; t < i + ngram; t++) {

					if (tokens[t].isEmpty())
						continue;

					if (BasicRegExPattern.STOP_WORDS.contains(tokens[t].toLowerCase()))
						continue;

					fBuffer.append(tokens[t]).append(SPLITTER);

				}

				final String featureName = fBuffer.toString().trim();

				if (featureName.length() < MIN_TOKEN_LENGTH)
					continue;

				if (featureName.isEmpty())
					continue;

				featureVector.set(
						LEFT + fromClassName + RIGHT + SPLITTER + featureName + SPLITTER + LEFT + toClassName + RIGHT,
						true);

			}
		}

	}

}