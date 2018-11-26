package de.hterhors.obie.ml.templates;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.core.tokenizer.Token;
import de.hterhors.obie.ml.ner.INERLAnnotation;
import de.hterhors.obie.ml.ner.NERLClassAnnotation;
import de.hterhors.obie.ml.ner.NERLIndividualAnnotation;
import de.hterhors.obie.ml.ner.regex.BasicRegExPattern;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.templates.InBetweenContextTemplate.Scope;
import de.hterhors.obie.ml.utils.ReflectionUtils;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import factors.Factor;
import factors.FactorScope;
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

	public InBetweenContextTemplate(RunParameter parameter) {
		super(parameter);
		this.enableDistantSupervision = parameter.exploreOnOntologyLevel;
	}

	static class PositionPairContainer {

		final String fromClassNameType;
		final int fromTokenIndex;

		final String toClassNameType;
		final int toTokenIndex;

		public PositionPairContainer(String fromClassNameType, int fromTokenIndex, String toClassNameType,
				int toTokenIndex) {
			this.fromClassNameType = fromClassNameType;
			this.fromTokenIndex = fromTokenIndex;
			this.toClassNameType = toClassNameType;
			this.toTokenIndex = toTokenIndex;
		}

	}

	class Scope extends FactorScope {

		public final OBIEInstance internalInstance;
		public final Class<? extends IOBIEThing> parentClass;
		public final AbstractIndividual parentIndividual;
		public final Integer parentCharacterOnset;
		public final Integer parentCharacterOffset;
		public final Class<? extends IOBIEThing> childClass;
		public final AbstractIndividual childIndividual;
		public final Integer childCharacterOnset;
		public final Integer childCharacterOffset;
		public final String childTextMention;

		public Scope(OBIEInstance internalInstance, Class<? extends IOBIEThing> rootClassType,
				Class<? extends IOBIEThing> parentClass, AbstractIndividual parentIndividual,
				Integer parentCharacterOnset, Integer parentCharacterOffset, Class<? extends IOBIEThing> childClass,
				AbstractIndividual childIndividual, Integer childCharacterOnset, Integer childCharacterOffset,
				String childTextMention) {
			super(InBetweenContextTemplate.this, internalInstance, rootClassType, parentClass, parentIndividual,
					parentCharacterOnset, parentCharacterOffset, childClass, childIndividual, childCharacterOnset,
					childCharacterOffset, childTextMention);
			this.parentClass = parentClass;
			this.parentCharacterOnset = parentCharacterOnset;
			this.parentCharacterOffset = parentCharacterOffset;
			this.parentIndividual = parentIndividual;
			this.childClass = childClass;
			this.childIndividual = childIndividual;
			this.childCharacterOnset = childCharacterOnset;
			this.childCharacterOffset = childCharacterOffset;
			this.childTextMention = childTextMention;
			this.internalInstance = internalInstance;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		for (TemplateAnnotation entity : state.getCurrentTemplateAnnotations().getTemplateAnnotations()) {
			callRecursive(factors, state.getInstance(), entity.rootClassType, entity.getThing());
		}

		return factors;
	}

	private void callRecursive(final List<Scope> factors, OBIEInstance internalInstance,
			Class<? extends IOBIEThing> rootClassType, final IOBIEThing parent) {

		if (parent == null)
			return;

		/*
		 * Add factor parent - child relation
		 */
		ReflectionUtils.getSlots(parent.getClass()).forEach(field -> {
			try {
				if (ReflectionUtils.isAnnotationPresent(field, RelationTypeCollection.class)) {
					for (IOBIEThing listObject : (List<IOBIEThing>) field.get(parent)) {

						addFactor(factors, internalInstance, rootClassType, parent, listObject);

						callRecursive(factors, internalInstance, rootClassType, listObject);
					}
				} else {

					IOBIEThing child = (IOBIEThing) field.get(parent);

					addFactor(factors, internalInstance, rootClassType, parent, child);

					callRecursive(factors, internalInstance, rootClassType, child);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

	}

	private void addFactor(final List<Scope> factors, OBIEInstance internalInstance,
			Class<? extends IOBIEThing> rootClassType, final IOBIEThing obieThing, IOBIEThing slotValue) {

		if (slotValue == null)
			return;

		if (enableDistantSupervision) {

			factors.add(new Scope(internalInstance, rootClassType, obieThing.getClass(), obieThing.getIndividual(),
					null, null, slotValue.getClass(), slotValue.getIndividual(), null, null,
					slotValue.getTextMention()));
		} else {

			factors.add(new Scope(internalInstance, rootClassType, obieThing.getClass(), obieThing.getIndividual(),
					obieThing.getCharacterOnset(), obieThing.getCharacterOffset(), slotValue.getClass(),
					slotValue.getIndividual(), slotValue.getCharacterOnset(), slotValue.getCharacterOffset(),
					slotValue.getTextMention()));
		}
	}

	private List<PositionPairContainer> computePositionPairs(OBIEInstance internalInstance,
			Class<? extends IOBIEThing> parentClass, AbstractIndividual parentIndividual, Integer parentCharOnset,
			Integer parentCharOffset, Class<? extends IOBIEThing> childClass, AbstractIndividual childIndividual,
			Integer childCharOnset, Integer childCharOffset, final String childSurfaceForm) {

		List<PositionPairContainer> positionsPairs = new ArrayList<>();

		if (enableDistantSupervision) {

			if (!internalInstance.getNamedEntityLinkingAnnotations().containsIndividualAnnotations(parentIndividual))
				return positionsPairs;

			Set<NERLIndividualAnnotation> parentNerls = internalInstance.getNamedEntityLinkingAnnotations()
					.getIndividualAnnotations(parentIndividual).stream().collect(Collectors.toSet());

			if (ReflectionUtils.isAnnotationPresent(childClass, DatatypeProperty.class)) {

				Set<NERLClassAnnotation> childNerls = internalInstance.getNamedEntityLinkingAnnotations()
						.getClassAnnotationsByTextMention(childClass, childSurfaceForm);
				if (childNerls != null) {

					for (NERLIndividualAnnotation parentNerl : parentNerls) {
						for (NERLClassAnnotation childNerl : childNerls) {

							Integer fromPosition = Integer.valueOf(parentNerl.onset + parentNerl.text.length());
							Class<? extends IOBIEThing> classType1 = parentClass;
							Integer toPosition = Integer.valueOf(childNerl.onset);
							Class<? extends IOBIEThing> classType2 = childNerl.classType;
							/*
							 * Switch "from" and "to" if from is after to position.
							 */
							if (fromPosition > toPosition) {
								fromPosition = Integer.valueOf(childNerl.onset + childNerl.text.length());
								classType1 = childNerl.classType;

								toPosition = Integer.valueOf(parentNerl.onset);
								classType2 = parentClass;
							}
							addPositionPair(positionsPairs, fromPosition, toPosition, classType1, classType2,
									internalInstance);

						}
					}
				} else {
					/*
					 * TODO: no child nerls found. do nothing? This happens if the ner for data type
					 * properties failed. e.g. the string we search is fifteen to 25 ml. This can
					 * not be parsed by the semantic interpretation. Thus the child nerls are empty.
					 */
				}

			} else {

				Set<NERLIndividualAnnotation> childNerls = internalInstance.getNamedEntityLinkingAnnotations()
						.getIndividualAnnotations(childIndividual).stream().collect(Collectors.toSet());
				if (childNerls != null) {

					Class<? extends IOBIEThing> classType1 = parentClass;
					Class<? extends IOBIEThing> classType2 = childClass;

					for (NERLIndividualAnnotation parentNerl : parentNerls) {
						for (NERLIndividualAnnotation childNerl : childNerls) {

							Integer fromPosition = Integer.valueOf(parentNerl.onset + parentNerl.text.length());
							Integer toPosition = Integer.valueOf(childNerl.onset);
							/*
							 * Switch "from" and "to" if from is after to position.
							 */
							if (fromPosition > toPosition) {
								fromPosition = Integer.valueOf(childNerl.onset + childNerl.text.length());
								classType1 = childClass;

								toPosition = Integer.valueOf(parentNerl.onset);
								classType2 = parentClass;
							}
							addPositionPair(positionsPairs, fromPosition, toPosition, classType1, classType2,
									internalInstance);

						}
					}
				} else {
					/*
					 * TODO: no child nerls found. do nothing? This happens if the ner for data type
					 * properties failed. e.g. the string we search is fifteen to 25 ml. This can
					 * not be parsed by the semantic interpretation. Thus the child nerls are empty.
					 */
				}

			}

		} else {
			Integer fromPosition = parentCharOffset;
			Integer toPosition = childCharOnset;

			if (fromPosition != null && toPosition != null) {
				Class<? extends IOBIEThing> fromClassType = parentClass;
				Class<? extends IOBIEThing> toClassType = childClass;
				/*
				 * Switch "from" and "to" if from is after to position.
				 */
				if (fromPosition > toPosition) {
					fromPosition = childCharOffset;
					toPosition = parentCharOnset;
					fromClassType = childClass;
					toClassType = parentClass;
				}
				addPositionPair(positionsPairs, fromPosition, toPosition, fromClassType, toClassType, internalInstance);
			}
		}
		return positionsPairs;
	}

	private void addPositionPair(final List<PositionPairContainer> positionPairs, Integer fromPosition,
			Integer toPosition, Class<? extends IOBIEThing> classType1, Class<? extends IOBIEThing> classType2,
			OBIEInstance internalInstance) {
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

				positionPairs.add(new PositionPairContainer(ReflectionUtils.simpleName(classType1), fromTokenIndex,
						ReflectionUtils.simpleName(classType2), toTokenIndex));

				for (Class<? extends IOBIEThing> rootClassType1 : ReflectionUtils.getSuperRootClasses(classType1)) {
					if (!ReflectionUtils.isAnnotationPresent(rootClassType1, DatatypeProperty.class))

						for (Class<? extends IOBIEThing> rootClassType2 : ReflectionUtils
								.getSuperRootClasses(classType2)) {

							if (!ReflectionUtils.isAnnotationPresent(rootClassType2, DatatypeProperty.class))

								/*
								 * Add features for root classes of annotations.
								 */

								positionPairs.add(new PositionPairContainer(ReflectionUtils.simpleName(rootClassType1),
										fromTokenIndex, ReflectionUtils.simpleName(rootClassType2), toTokenIndex));

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

		final List<Token> tokens = factor.getFactorScope().internalInstance.getTokens();

		final List<PositionPairContainer> positionPairs = computePositionPairs(factor.getFactorScope().internalInstance,
				factor.getFactorScope().parentClass, factor.getFactorScope().parentIndividual,
				factor.getFactorScope().parentCharacterOnset, factor.getFactorScope().parentCharacterOffset,
				factor.getFactorScope().childClass, factor.getFactorScope().childIndividual,
				factor.getFactorScope().childCharacterOnset, factor.getFactorScope().childCharacterOffset,
				factor.getFactorScope().childTextMention);

		for (PositionPairContainer positionPair : positionPairs) {

			final String fromName = positionPair.fromClassNameType;
			final int fromTokenIndex = positionPair.fromTokenIndex;

			final String toName = positionPair.toClassNameType;
			final int toTokenIndex = positionPair.toTokenIndex;

			StringBuffer fullTokenFeature = new StringBuffer(LEFT + fromName + RIGHT + SPLITTER);

			// plus 2 for start and end symbol
			String[] inBetweenContext = new String[toTokenIndex - fromTokenIndex + 2];
			inBetweenContext[0] = START_CIRCUMFLEX;
			int index = 1;
			for (int i = fromTokenIndex; i < toTokenIndex; i++) {
				/*
				 * Each token as single feature.
				 */
				final StringBuffer feature = new StringBuffer(LEFT + fromName + RIGHT + SPLITTER);
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

				final StringBuffer fBuffer = new StringBuffer();
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