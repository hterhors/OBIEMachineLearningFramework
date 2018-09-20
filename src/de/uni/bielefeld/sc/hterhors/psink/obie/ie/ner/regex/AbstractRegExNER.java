package de.uni.bielefeld.sc.hterhors.psink.obie.ie.ner.regex;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.OntologyAnalyzer;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DirectInterface;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.OntologyModelContent;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.dtinterpreter.IDatatypeInterpretation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.explorer.utils.ExplorationUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.ner.INamedEntitityLinker;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.NELAnnotation;

public abstract class AbstractRegExNER<R extends IOBIEThing> implements INamedEntitityLinker, Serializable {

	private static Logger log = LogManager.getFormatterLogger(AbstractRegExNER.class);

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

//	protected final Set<Class<? extends IOBIEThing>> rootClassTypes;
//	protected final BigramCorpus trainingCorpus;

//	private static boolean trainingPatternAreBuild = false;
//
//	private static Object traininDataBlock = new Object();
//
//	private static boolean wasPriorKnowledgeBuild = false;
//
//	private static Object priorKnowledgeBlock = new Object();

//	private final static Map<Class<? extends IOBIEThing>, Set<String>> priorKnowledgeWordMap = new HashMap<>();
//	private final static Map<Class<? extends IOBIEThing>, Set<Pattern>> trainingPattern = new HashMap<>();

	final private Map<Class<? extends R>, Set<Pattern>> regExPattern = new HashMap<>();

	protected AbstractRegExNER(Set<Class<? extends IOBIEThing>> rootClasses) {
//
//		for (Entry<Class<? extends IOBIEThing>, Set<String>> tp : priorKnowledgeWordMap.entrySet()) {
//			regExPattern
//					.putIfAbsent((Class<? extends R>) tp.getKey(),
//							tp.getValue().stream()
//									.map(s -> Pattern.compile(BasicRegExPattern.PRE_BOUNDS + Pattern.quote(s)
//											+ BasicRegExPattern.POST_BOUNDS, BasicRegExPattern.PATTERN_BITMASK))
//									.collect(Collectors.toSet()));
//		}
//		for (Class<? extends IOBIEThing> key : trainingPattern.keySet()) {
//			regExPattern.putIfAbsent((Class<? extends R>) key, new HashSet<>());
//			regExPattern.get(key).addAll(trainingPattern.get(key));
//		}

		for (Class<? extends IOBIEThing> rootClassType : rootClasses) {
			addOrMergePatterns(regExPattern, collectRegexPattern(rootClassType));
		}
//		this.rootClassTypes = rootClassTypes;
//		this.trainingCorpus = trainingCorpus;

//		synchronized (traininDataBlock) {
//			if (!trainingPatternAreBuild) {
//				trainingPatternAreBuild = true;
//				trainingPattern.putAll(generateTrainingPatternForRootClassTypes());
//
//			}
//		}
//		synchronized (priorKnowledgeBlock) {
//			if (!wasPriorKnowledgeBuild) {
//				wasPriorKnowledgeBuild = true;
//				if (testCorpus != null)
//					for (InternalInstance ii : testCorpus.getInternalInstances()) {
//						for (InternalAnnotation ia : ii.getGoldAnnotation().getEntities()) {
//							addMentionsRecursive(priorKnowledgeWordMap, ia.getAnnotationInstance());
//						}
//					}
//			}
//		}
	}

//	@SuppressWarnings("unchecked")
	public Map<Class<? extends IOBIEThing>, Set<NELAnnotation>> annotate(final String content) {

//		Map<Class<? extends R>, Set<Pattern>> regExPattern = new HashMap<>();
//
//		for (Entry<Class<? extends IOBIEThing>, Set<String>> tp : priorKnowledgeWordMap.entrySet()) {
//			regExPattern
//					.putIfAbsent((Class<? extends R>) tp.getKey(),
//							tp.getValue().stream()
//									.map(s -> Pattern.compile(BasicRegExPattern.PRE_BOUNDS + Pattern.quote(s)
//											+ BasicRegExPattern.POST_BOUNDS, BasicRegExPattern.PATTERN_BITMASK))
//									.collect(Collectors.toSet()));
//		}
//		for (Class<? extends IOBIEThing> key : trainingPattern.keySet()) {
//			regExPattern.putIfAbsent((Class<? extends R>) key, new HashSet<>());
//			regExPattern.get(key).addAll(trainingPattern.get(key));
//		}
//
//		for (Class<? extends IOBIEThing> rootClassType : regExPattern) {
//			addOrMergePatterns(regExPattern, collectRegexPattern(rootClassType));
//		}
		final Map<Class<? extends R>, Set<NELAnnotation>> docSpeceficRetrieval = new HashMap<>();

		for (Class<? extends R> scioDataTypeClass : regExPattern.keySet()) {

			docSpeceficRetrieval.putIfAbsent(scioDataTypeClass, new HashSet<>());

			for (Pattern pattern : regExPattern.get(scioDataTypeClass)) {
				Matcher matcher = pattern.matcher(content);

				while (matcher.find()) {

					final int index = 0;
					final int offset = matcher.start(index);
					final String text = matcher.group(index);

					if (text.length() < getMinNERLength())
						continue;

					boolean dt = scioDataTypeClass.isAnnotationPresent(DatatypeProperty.class);

					final IDatatypeInterpretation semanticInterpretation;

					if (dt) {
						semanticInterpretation = getSemanticInterpretation(scioDataTypeClass, matcher);
					} else {
						semanticInterpretation = null;
					}
					NELAnnotation mention = new NELAnnotation(text, offset,
							scioDataTypeClass, semanticInterpretation);

					docSpeceficRetrieval.get(scioDataTypeClass).add(mention);
				}
			}
			if (docSpeceficRetrieval.get(scioDataTypeClass).isEmpty()) {
				docSpeceficRetrieval.remove(scioDataTypeClass);
			}
		}

		return Collections.unmodifiableMap(docSpeceficRetrieval);

	}

	protected abstract int getMinNERLength();

	protected abstract IDatatypeInterpretation getSemanticInterpretation(Class<? extends R> dataTypeClass, Matcher matcher);

	private Map<Class<? extends R>, Set<Pattern>> collectRegexPattern(Class<? extends IOBIEThing> rootClassType) {
		Map<Class<? extends R>, Set<Pattern>> regExPattern = new HashMap<>();
		/*
		 * Auto generated pattern from class names.
		 */
		addOrMergePatterns(regExPattern, addPlainRegExPattern(rootClassType));

		Map<Class<? extends R>, Set<Pattern>> handMadePattern = addHandMadePattern(rootClassType);
		addOrMergePatterns(regExPattern, handMadePattern);

		Map<Class<? extends R>, Set<Pattern>> extendedAuxiliaryClassPattern = generateExtendedAuxiliaryClassPattern(
				rootClassType);
		addOrMergePatterns(regExPattern, extendedAuxiliaryClassPattern);

		Map<Class<? extends R>, Set<Pattern>> crossReferencePattern = generateCrossReferencePattern(rootClassType);
		addOrMergePatterns(regExPattern, crossReferencePattern);

		addOrMergePatterns(regExPattern, addFurtherPattern());

		/*
		 * This should be added last as we use regular expression patterns that were
		 * generated before.
		 */
		Map<Class<? extends R>, Set<Pattern>> patternDependendCrossReferencePattern = generateHandMadeCrossReferences(
				regExPattern, rootClassType);

		addOrMergePatterns(regExPattern, patternDependendCrossReferencePattern);

		return regExPattern;
	}

	/**
	 * Extends auxiliary classes by related class pattern. We do this, because
	 * auxiliary classes often do not have any meaningful autogenerated pattern.
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	final private Map<Class<? extends R>, Set<Pattern>> generateExtendedAuxiliaryClassPattern(
			Class<? extends IOBIEThing> rootClassType) {

		Map<Class<? extends R>, Set<Pattern>> extendedAuxiliaryClassPattern = new HashMap<>();

		Set<Class<? extends IOBIEThing>> relatedRootClasses = OntologyAnalyzer
				.getRelatedClassesTypesUnderRoot(rootClassType);

		/*
		 * Do not extend root class. This would be to much and it is not important as
		 * the root class cardinality is sampled differently.
		 */
		relatedRootClasses.remove(rootClassType);

		for (Class<? extends IOBIEThing> relatedRootClass : relatedRootClasses) {

			final Class<? extends IOBIEThing> interfaceOfRelatedClasstype = relatedRootClass
					.getAnnotation(DirectInterface.class).get();

			if (ExplorationUtils.isAuxiliaryProperty(interfaceOfRelatedClasstype)) {

				Map<Class<? extends R>, Set<Pattern>> relatedClasses = addPlainRegExPattern(
						interfaceOfRelatedClasstype);

				extendedAuxiliaryClassPattern.put((Class<? extends R>) relatedRootClass, new HashSet<>());
				for (Set<Pattern> extendbyClass : relatedClasses.values()) {
					extendedAuxiliaryClassPattern.get(relatedRootClass).addAll(extendbyClass);
				}
			}

		}
		return extendedAuxiliaryClassPattern;
	}

//	private Map<Class<? extends IOBIEThing>, Set<Pattern>> generateTrainingPatternForRootClassTypes() {
//		Map<Class<? extends IOBIEThing>, Set<String>> trainingMentions = new HashMap<>();
//
//		for (InternalInstance instance : trainingCorpus.getInternalInstances()) {
//			for (InternalAnnotation annotation : instance.getGoldAnnotation().getEntities()) {
//				addMentionsRecursive(trainingMentions, annotation.getAnnotationInstance());
//			}
//		}
//
//		Map<Class<? extends IOBIEThing>, Set<Pattern>> pattern = new HashMap<>();
//		for (Entry<Class<? extends IOBIEThing>, Set<String>> tp : trainingMentions.entrySet()) {
//			pattern.putIfAbsent(tp.getKey(),
//					tp.getValue().stream()
//							.map(s -> Pattern.compile(
//									BasicRegExPattern.PRE_BOUNDS + Pattern.quote(s) + BasicRegExPattern.POST_BOUNDS,
//									BasicRegExPattern.PATTERN_BITMASK))
//							.collect(Collectors.toSet()));
//		}
//		return pattern;
//
//	}

	@SuppressWarnings("unchecked")
	private void addMentionsRecursive(Map<Class<? extends IOBIEThing>, Set<String>> trainingMentions,
			IOBIEThing scioClass) {

		if (scioClass == null)
			return;
		// !scioClass.getClass().isAnnotationPresent(DataTypeProperty.class) &&
		if (scioClass.getTextMention() != null) {

			if (trainingMentions.containsKey(scioClass.getClass())) {
				trainingMentions.get(scioClass.getClass()).add(scioClass.getTextMention());
			} else {
				Set<String> pattern = new HashSet<>();
				pattern.add(scioClass.getTextMention());
				trainingMentions.put(scioClass.getClass(), pattern);
			}
		}

		/*
		 * Add factors for object type properties.
		 */
		if (!scioClass.getClass().isAnnotationPresent(DatatypeProperty.class))
			Arrays.stream(scioClass.getClass().getDeclaredFields())
					.filter(f -> f.isAnnotationPresent(OntologyModelContent.class)).forEach(field -> {
						field.setAccessible(true);
						try {
							if (field.isAnnotationPresent(RelationTypeCollection.class)) {
								for (IOBIEThing element : (List<IOBIEThing>) field.get(scioClass)) {
									addMentionsRecursive(trainingMentions, element);
								}
							} else {
								addMentionsRecursive(trainingMentions, (IOBIEThing) field.get(scioClass));
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					});
	}

	private void addOrMergePatterns(Map<Class<? extends R>, Set<Pattern>> mergeIn,
			Map<Class<? extends R>, Set<Pattern>> mergeFrom) {
		for (Class<? extends R> key : mergeFrom.keySet()) {
			mergeIn.putIfAbsent(key, new HashSet<>());
			mergeIn.get(key).addAll(mergeFrom.get(key));
		}
	}

	protected abstract Map<Class<? extends R>, Set<Pattern>> addHandMadePattern(
			Class<? extends IOBIEThing> rootClassType);

	protected abstract Map<Class<? extends R>, Set<Pattern>> addFurtherPattern();

	protected abstract Map<Class<? extends R>, Set<Pattern>> generateHandMadeCrossReferences(
			Map<Class<? extends R>, Set<Pattern>> regularExpressionPattern, Class<? extends IOBIEThing> rootClassType);

	protected abstract Map<Class<? extends R>, Set<Pattern>> generateCrossReferencePattern(
			Class<? extends IOBIEThing> rootClassType);

	protected abstract Map<Class<? extends R>, Set<Pattern>> addPlainRegExPattern(
			Class<? extends IOBIEThing> rootClassType);

}
