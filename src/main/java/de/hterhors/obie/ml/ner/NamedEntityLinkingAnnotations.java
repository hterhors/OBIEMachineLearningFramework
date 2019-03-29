package de.hterhors.obie.ml.ner;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;

public class NamedEntityLinkingAnnotations implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private final Map<Class<? extends IOBIEThing>, Set<NERLClassAnnotation>> classRetrievals;

	private final Map<Class<? extends IOBIEThing>, Map<String, Set<NERLClassAnnotation>>> classRetrievalsByTextMention;

	private final Map<Class<? extends IOBIEThing>, Map<String, Set<NERLClassAnnotation>>> classRetrievalsByDistinctSemanticValues;

	private final Map<AbstractIndividual, Set<NERLIndividualAnnotation>> individualRetrievals;

	private final Map<AbstractIndividual, Map<String, Set<NERLIndividualAnnotation>>> individualRetrievalsByTextMention;

	private NamedEntityLinkingAnnotations(Map<Class<? extends IOBIEThing>, Set<NERLClassAnnotation>> classRetrievals,
			Map<AbstractIndividual, Set<NERLIndividualAnnotation>> individualRetrievals) {

		/*
		 * 
		 */

		this.classRetrievals = classRetrievals;

		this.classRetrievalsByTextMention = indexClassAnnotationsByText(classRetrievals);

		this.classRetrievalsByDistinctSemanticValues = indexBySemanticValue(classRetrievals);

		/*
		 * 
		 */

		this.individualRetrievals = individualRetrievals;

		this.individualRetrievalsByTextMention = indexIndividualAnnotationsByText(individualRetrievals);

	}

	/**
	 * TODO: get rid of duplicate code
	 */

	private Map<Class<? extends IOBIEThing>, Map<String, Set<NERLClassAnnotation>>> indexClassAnnotationsByText(
			Map<Class<? extends IOBIEThing>, Set<NERLClassAnnotation>> retrievals) {
		final Map<Class<? extends IOBIEThing>, Map<String, Set<NERLClassAnnotation>>> rbtm = new HashMap<>();
		for (Entry<Class<? extends IOBIEThing>, Set<NERLClassAnnotation>> retrieval : retrievals.entrySet()) {

			rbtm.put(retrieval.getKey(), new HashMap<>());
			for (NERLClassAnnotation nera : retrieval.getValue()) {
				rbtm.get(retrieval.getKey()).putIfAbsent(nera.getText(), new HashSet<>());
				rbtm.get(retrieval.getKey()).get(nera.getText()).add(nera);
			}
		}
		return Collections.unmodifiableMap(rbtm);
	}

	private Map<AbstractIndividual, Map<String, Set<NERLIndividualAnnotation>>> indexIndividualAnnotationsByText(
			Map<AbstractIndividual, Set<NERLIndividualAnnotation>> retrievals) {
		final Map<AbstractIndividual, Map<String, Set<NERLIndividualAnnotation>>> rbtm = new HashMap<>();
		for (Entry<AbstractIndividual, Set<NERLIndividualAnnotation>> retrieval : retrievals.entrySet()) {

			rbtm.put(retrieval.getKey(), new HashMap<>());
			for (NERLIndividualAnnotation nera : retrieval.getValue()) {
				rbtm.get(retrieval.getKey()).putIfAbsent(nera.getText(), new HashSet<>());
				rbtm.get(retrieval.getKey()).get(nera.getText()).add(nera);
			}
		}
		return Collections.unmodifiableMap(rbtm);
	}

	private <B> Map<B, Map<String, Set<NERLClassAnnotation>>> indexBySemanticValue(
			Map<B, Set<NERLClassAnnotation>> retrievals) {
		final Map<B, Map<String, Set<NERLClassAnnotation>>> rBDSV = new HashMap<>();
		for (Entry<B, Set<NERLClassAnnotation>> retrieval : retrievals.entrySet()) {

			rBDSV.put(retrieval.getKey(), new HashMap<>());
			for (NERLClassAnnotation nera : retrieval.getValue()) {
				if (nera.semanticInterpretation == null)
					continue;

				rBDSV.get(retrieval.getKey()).putIfAbsent(nera.getDTValueIfAnyElseTextMention(), new HashSet<>());
				rBDSV.get(retrieval.getKey()).get(nera.getDTValueIfAnyElseTextMention()).add(nera);
			}
		}
		return Collections.unmodifiableMap(rBDSV);
	}

	public static class Collector {

		private final Map<Class<? extends IOBIEThing>, Set<NERLClassAnnotation>> classRetrievals = new HashMap<>();
		private final Map<AbstractIndividual, Set<NERLIndividualAnnotation>> individualRetrievals = new HashMap<>();

		public Collector() {
		}

		public Collector addClassAnnotations(
				Map<Class<? extends IOBIEThing>, Set<NERLClassAnnotation>> namedEntityLinkingAnnotations) {

			for (Entry<Class<? extends IOBIEThing>, Set<NERLClassAnnotation>> annotationEntry : namedEntityLinkingAnnotations
					.entrySet()) {

				final Set<NERLClassAnnotation> annotations = classRetrievals.getOrDefault(annotationEntry.getKey(),
						new HashSet<>());

				annotations.addAll(annotationEntry.getValue());
				classRetrievals.put(annotationEntry.getKey(), annotations);
			}
			return this;

		}

		public Collector addIndividualAnnotations(
				Map<AbstractIndividual, Set<NERLIndividualAnnotation>> namedEntityLinkingAnnotations) {

			for (Entry<AbstractIndividual, Set<NERLIndividualAnnotation>> annotationEntry : namedEntityLinkingAnnotations
					.entrySet()) {

				final Set<NERLIndividualAnnotation> annotations = individualRetrievals
						.getOrDefault(annotationEntry.getKey(), new HashSet<>());
				annotations.addAll(annotationEntry.getValue());
				individualRetrievals.put(annotationEntry.getKey(), annotations);
			}
			return this;

		}

		public NamedEntityLinkingAnnotations collect() {
			return new NamedEntityLinkingAnnotations(classRetrievals, individualRetrievals);
		}
	}

	/**
	 * Returns the annotations for a given individual that surfaceForm matches the
	 * given string.
	 * 
	 * @param individual
	 * @return
	 */
	public Set<NERLIndividualAnnotation> getIndividualAnnotationsByTextMention(AbstractIndividual individual,
			String surfaceForm) {

		if (!individualRetrievalsByTextMention.containsKey(individual))
			return null;

		return individualRetrievalsByTextMention.get(individual).get(surfaceForm);
	}

	/**
	 * Returns the annotations for a given class type that surfaceForm matches the
	 * given string.
	 * 
	 * @param classType
	 * @return
	 */
	public Set<NERLClassAnnotation> getClassAnnotationsByTextMention(Class<? extends IOBIEThing> classType,
			String surfaceForm) {

		if (!classRetrievalsByTextMention.containsKey(classType))
			return null;

		return classRetrievalsByTextMention.get(classType).get(surfaceForm);
	}

	/**
	 * Returns the annotations for a given class type that surfaceForm are distinct.
	 * 
	 * @param classType
	 * @return
	 */
	public Set<NERLClassAnnotation> getClassAnnotationsBySemanticValues(Class<? extends IOBIEThing> classType) {
		return classRetrievalsByDistinctSemanticValues.get(classType).values().stream()
				.flatMap(v -> v.stream().limit(1)).collect(Collectors.toSet());
	}

	/**
	 * Returns the annotations for a given class type.
	 * 
	 * @param classType
	 * @return
	 */
	public Set<NERLClassAnnotation> getClassAnnotations(Class<? extends IOBIEThing> classType) {
		return classRetrievals.get(classType);
	}

	/**
	 * Returns the annotations for a given class type.
	 * 
	 * @param classOrIndividualname
	 * @return
	 */
	public Set<NERLIndividualAnnotation> getIndividualAnnotations(AbstractIndividual individual) {
		return individualRetrievals.get(individual);
	}

	/**
	 * Checks whether this document contains annotation data for the given class
	 * type
	 * 
	 * @param classType
	 * @return true if data is available, else false.
	 */
	public boolean containsClassAnnotations(Class<? extends IOBIEThing> classType) {
		return classRetrievals.containsKey(classType) && !classRetrievals.get(classType).isEmpty();
	}

	/**
	 * Checks whether this document contains annotation data for the given class
	 * type
	 * 
	 * @param classOrIndividualname
	 * @return true if data is available, else false.
	 */
	public boolean containsIndividualAnnotations(AbstractIndividual individual) {
		return individual != null && individualRetrievals.containsKey(individual)
				&& !individualRetrievals.get(individual).isEmpty();
	}

	/**
	 * Returns all class types for that are annotations are available.
	 * 
	 * @return Unmodifiable set of all class types.
	 */
	public Set<Class<? extends IOBIEThing>> getAvailableClassTypes() {
		return classRetrievals.keySet();
	}

	/**
	 * Returns all class types for that are annotations are available.
	 * 
	 * @return Unmodifiable set of all class types.
	 */
	public Set<AbstractIndividual> getAvailableIndividualTypes() {
		return individualRetrievals.keySet();
	}

	@Override
	public String toString() {
		return "NamedEntityLinkingAnnotations [classRetrievals=" + classRetrievals + ", individualRetrievals="
				+ individualRetrievals + "]";
	}

	public int numberOfTotalAnnotations() {
		return classRetrievals.size() + individualRetrievals.size();
	}

}
