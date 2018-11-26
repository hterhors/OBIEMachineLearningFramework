package de.hterhors.obie.ml.ner.regex;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.OntologyAnalyzer;
import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.IndividualFactory;
import de.hterhors.obie.core.ontology.OntologyInitializer;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.dtinterpreter.IDatatypeInterpretation;
import de.hterhors.obie.ml.dtinterpreter.IDatatypeInterpreter;
import de.hterhors.obie.ml.explorer.utils.ExplorationUtils;
import de.hterhors.obie.ml.ner.INamedEntitityLinker;
import de.hterhors.obie.ml.ner.NERLClassAnnotation;
import de.hterhors.obie.ml.ner.NERLIndividualAnnotation;
import de.hterhors.obie.ml.utils.ReflectionUtils;

public abstract class AbstractRegExNERL<T extends IOBIEThing> implements INamedEntitityLinker, Serializable {

	private static Logger log = LogManager.getFormatterLogger(AbstractRegExNERL.class);

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	final private Map<Class<? extends T>, Set<Pattern>> regExPatternForClasses = new HashMap<>();
	final private Map<AbstractIndividual, Set<Pattern>> regExPatternForIndividuals = new HashMap<>();

	final private BasicRegExPattern<T> basicAbstractPattern;

	private IDatatypeInterpreter<T> interpreter;

	private int minNerLength;

	protected AbstractRegExNERL(Set<Class<? extends T>> rootClasses, BasicRegExPattern<T> basicRegExPattern,
			IDatatypeInterpreter<T> interpreter, int minNerLength) {
		this.basicAbstractPattern = basicRegExPattern;
		this.interpreter = interpreter;
		this.minNerLength = minNerLength;

		for (Class<? extends T> rootClassType : rootClasses) {

			addOrMergePatterns(regExPatternForClasses, collectRegexPatternForClasses(rootClassType));

			addOrMergePatterns(regExPatternForIndividuals, collectRegexPatternForIndividuals(rootClassType));

		}
	}

	public Map<Class<? extends IOBIEThing>, Set<NERLClassAnnotation>> annotateClasses(final String content) {

		final Map<Class<? extends IOBIEThing>, Set<NERLClassAnnotation>> docSpeceficRetrieval = new HashMap<>();

		for (Class<? extends T> scioDataTypeClass : regExPatternForClasses.keySet()) {
			docSpeceficRetrieval.put(scioDataTypeClass, new HashSet<>());
		}
		regExPatternForClasses.keySet().parallelStream().forEach(scioDataTypeClass -> {

			Set<NERLClassAnnotation> annotation = docSpeceficRetrieval.get(scioDataTypeClass);

			for (Pattern pattern : regExPatternForClasses.get(scioDataTypeClass)) {

				Matcher matcher = pattern.matcher(content);

				while (matcher.find()) {

					final int index = 0;
					final int offset = matcher.start(index);
					final String text = matcher.group(index);

					if (text.length() < minNerLength)
						continue;

					boolean dt = scioDataTypeClass.isAnnotationPresent(DatatypeProperty.class);

					final IDatatypeInterpretation semanticInterpretation;

					if (dt) {
						semanticInterpretation = interpreter.interpret(scioDataTypeClass, matcher);
					} else {
						semanticInterpretation = null;
					}
					NERLClassAnnotation mention = new NERLClassAnnotation(text, offset, scioDataTypeClass,
							semanticInterpretation);

					annotation.add(mention);
				}
			}
		});

		for (Class<? extends T> scioDataTypeClass : regExPatternForClasses.keySet()) {
			if (docSpeceficRetrieval.get(scioDataTypeClass).isEmpty()) {
				docSpeceficRetrieval.remove(scioDataTypeClass);
			}
		}

		return Collections.unmodifiableMap(docSpeceficRetrieval);

	}

	public Map<AbstractIndividual, Set<NERLIndividualAnnotation>> annotateIndividuals(final String content) {

		final Map<AbstractIndividual, Set<NERLIndividualAnnotation>> docSpeceficRetrieval = new HashMap<>(
				regExPatternForIndividuals.keySet().size());

		for (AbstractIndividual individual : regExPatternForIndividuals.keySet()) {
			docSpeceficRetrieval.put(individual, new HashSet<>());
		}

		regExPatternForIndividuals.keySet().parallelStream().forEach(individual -> {

			Set<NERLIndividualAnnotation> annotation = docSpeceficRetrieval.get(individual);

			for (Pattern pattern : regExPatternForIndividuals.get(individual)) {

				Matcher matcher = pattern.matcher(content);

				while (matcher.find()) {

					final int index = 0;
					final int offset = matcher.start(index);
					final String text = matcher.group(index);

					if (text.length() < minNerLength)
						continue;

					NERLIndividualAnnotation mention = new NERLIndividualAnnotation(text, offset, individual);

					annotation.add(mention);
				}
			}
		});

		for (AbstractIndividual individual : regExPatternForIndividuals.keySet()) {
			if (docSpeceficRetrieval.get(individual).isEmpty()) {
				docSpeceficRetrieval.remove(individual);
			}
		}

		return Collections.unmodifiableMap(docSpeceficRetrieval);

	}

	private Map<AbstractIndividual, Set<Pattern>> collectRegexPatternForIndividuals(Class<? extends T> rootClassType) {

		Map<AbstractIndividual, Set<Pattern>> regExPattern = new HashMap<>();

		addOrMergePatterns(regExPattern, getAutoGeneratedPatternForIndividuals(rootClassType));
		addOrMergePatterns(regExPattern, getAdditionalPatternForIndividuals(rootClassType));

		return regExPattern;
	}

	private Map<AbstractIndividual, Set<Pattern>> getAutoGeneratedPatternForIndividuals(
			Class<? extends T> rootClassType) {

		Map<AbstractIndividual, Set<Pattern>> autoGeneratedPattern = new HashMap<>();

		Map<AbstractIndividual, Set<Pattern>> basicIndividualPattern = basicAbstractPattern
				.getHandMadePatternForIndividuals();

		for (Class<? extends IOBIEThing> classType : OntologyAnalyzer.getRelatedClassTypesUnderRoot(rootClassType)) {
			try {
				if (classType.isAnnotationPresent(DatatypeProperty.class)) {
					continue;
				}

				@SuppressWarnings("unchecked")
				final Collection<AbstractIndividual> individuals = ((IndividualFactory<AbstractIndividual>) ReflectionUtils
						.getAccessibleFieldByName(classType, OntologyInitializer.INDIVIDUAL_FACTORY_FIELD_NAME)
						.get(null)).getIndividuals();

				for (AbstractIndividual individual : individuals) {

					if (basicIndividualPattern.containsKey(individual)) {
						autoGeneratedPattern.put(individual, basicIndividualPattern.get(individual));
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

		addOrMergePatterns(autoGeneratedPattern, basicAbstractPattern.autoGeneratePatternForIndividuals(rootClassType));

		return autoGeneratedPattern;
	}

	private Map<Class<? extends T>, Set<Pattern>> collectRegexPatternForClasses(Class<? extends T> rootClassType) {

		Map<Class<? extends T>, Set<Pattern>> regExPattern = new HashMap<>();

		addOrMergePatterns(regExPattern, getAutoGeneratedPatternForClasses(rootClassType));
		addOrMergePatterns(regExPattern, generateExtendedAuxiliaryClassPattern(rootClassType));
		addOrMergePatterns(regExPattern, getAdditionalPatternForClasses(rootClassType));

		return regExPattern;
	}

	protected Map<Class<? extends T>, Set<Pattern>> getAutoGeneratedPatternForClasses(
			Class<? extends T> rootClassType) {

		Map<Class<? extends T>, Set<Pattern>> pattern = new HashMap<>();

		Map<Class<? extends T>, Set<Pattern>> allHandMadepattern = basicAbstractPattern.getHandMadePatternForClasses();

		for (Class<? extends IOBIEThing> classType : OntologyAnalyzer.getRelatedClassTypesUnderRoot(rootClassType)) {

			if (allHandMadepattern.containsKey(classType)) {
				pattern.put((Class<T>) classType, allHandMadepattern.get(classType));
			} else if (classType.isAnnotationPresent(DatatypeProperty.class)) {
				log.warn("WARN!!! No basic pattern for datatype: " + classType.getSimpleName());
			}

		}

		addOrMergePatterns(pattern, basicAbstractPattern.autoGeneratePatternForClasses(rootClassType));

		return pattern;
	}

	/**
	 * Extends auxiliary classes by related class pattern. We do this, because
	 * auxiliary classes often do not have any meaningful autogenerated pattern.
	 * Important?
	 * 
	 * TODO: Auxiliary classes are sampled anyway, even without text evidence.
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	final private Map<Class<? extends T>, Set<Pattern>> generateExtendedAuxiliaryClassPattern(
			Class<? extends IOBIEThing> rootClassType) {

		Map<Class<? extends T>, Set<Pattern>> extendedAuxiliaryClassPattern = new HashMap<>();

		Set<Class<? extends IOBIEThing>> relatedRootClasses = OntologyAnalyzer
				.getRelatedClassTypesUnderRoot(rootClassType);

		/*
		 * Do not extend root class. This would be to much and it is not important as
		 * the root class cardinality is explored differently.
		 */
		relatedRootClasses.remove(rootClassType);

		for (Class<? extends IOBIEThing> relatedRootClass : relatedRootClasses) {

			final Class<? extends IOBIEThing> interfaceOfRelatedClasstype = ReflectionUtils
					.getDirectInterfaces(relatedRootClass);

			if (ExplorationUtils.isAuxiliary(interfaceOfRelatedClasstype)) {
				Map<Class<? extends T>, Set<Pattern>> relatedClasses = basicAbstractPattern
						.autoGeneratePatternForClasses(interfaceOfRelatedClasstype);

				extendedAuxiliaryClassPattern.put((Class<? extends T>) relatedRootClass, new HashSet<>());
				for (Set<Pattern> extendbyClass : relatedClasses.values()) {
					extendedAuxiliaryClassPattern.get(relatedRootClass).addAll(extendbyClass);
				}
			}

		}
		return extendedAuxiliaryClassPattern;
	}

	protected <Key> void addOrMergePatterns(Map<Key, Set<Pattern>> mergeIn, Map<Key, Set<Pattern>> mergeFrom) {
		for (Key key : mergeFrom.keySet()) {
			mergeIn.putIfAbsent(key, new HashSet<>());
			mergeIn.get(key).addAll(mergeFrom.get(key));
		}
	}

	protected abstract Map<Class<? extends T>, Set<Pattern>> getAdditionalPatternForClasses(
			Class<? extends T> rootClassType);

	protected abstract Map<AbstractIndividual, Set<Pattern>> getAdditionalPatternForIndividuals(
			Class<? extends T> rootClassType);

}
