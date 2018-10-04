package de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.AbstractOBIEIndividual;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;

public class NamedEntityLinkingAnnotations implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private final Map<Class<? extends IOBIEThing>, Set<NERLClassAnnotation>> classRetrievals;

	private final Map<Class<? extends IOBIEThing>, Map<String, Set<NERLClassAnnotation>>> classRetrievalsByTextMention;

	private final Map<Class<? extends IOBIEThing>, Map<String, Set<NERLClassAnnotation>>> classRetrievalsByDistinctSemanticValues;

	private final Map<AbstractOBIEIndividual, Set<NERLIndividualAnnotation>> individualRetrievals;

	private final Map<AbstractOBIEIndividual, Map<String, Set<NERLIndividualAnnotation>>> individualRetrievalsByTextMention;

	private NamedEntityLinkingAnnotations(Map<Class<? extends IOBIEThing>, Set<NERLClassAnnotation>> classRetrievals,
			Map<AbstractOBIEIndividual, Set<NERLIndividualAnnotation>> individualRetrievals) {

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

	private Map<AbstractOBIEIndividual, Map<String, Set<NERLIndividualAnnotation>>> indexIndividualAnnotationsByText(
			Map<AbstractOBIEIndividual, Set<NERLIndividualAnnotation>> retrievals) {
		final Map<AbstractOBIEIndividual, Map<String, Set<NERLIndividualAnnotation>>> rbtm = new HashMap<>();
		for (Entry<AbstractOBIEIndividual, Set<NERLIndividualAnnotation>> retrieval : retrievals.entrySet()) {

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

	private <K> Map<K, Map<String, Set<INERLAnnotation>>> indexByText2(Map<K, Set<INERLAnnotation>> retrievals) {
		final Map<K, Map<String, Set<INERLAnnotation>>> rbtm = new HashMap<>();
		for (Entry<K, Set<INERLAnnotation>> retrieval : retrievals.entrySet()) {

			rbtm.put(retrieval.getKey(), new HashMap<>());
			for (INERLAnnotation nera : retrieval.getValue()) {
				rbtm.get(retrieval.getKey()).putIfAbsent(nera.getText(), new HashSet<>());
				rbtm.get(retrieval.getKey()).get(nera.getText()).add(nera);
			}
		}
		return Collections.unmodifiableMap(rbtm);
	}

	public static class Builder {

		private final Map<Class<? extends IOBIEThing>, Set<NERLClassAnnotation>> classRetrievals = new HashMap<>();
		private final Map<AbstractOBIEIndividual, Set<NERLIndividualAnnotation>> individualRetrievals = new HashMap<>();

		public Builder() {
		}

		public Builder addClassAnnotations(
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

		public Builder addIndividualAnnotations(
				Map<AbstractOBIEIndividual, Set<NERLIndividualAnnotation>> namedEntityLinkingAnnotations) {

			for (Entry<AbstractOBIEIndividual, Set<NERLIndividualAnnotation>> annotationEntry : namedEntityLinkingAnnotations
					.entrySet()) {

				final Set<NERLIndividualAnnotation> annotations = individualRetrievals
						.getOrDefault(annotationEntry.getKey(), new HashSet<>());
				annotations.addAll(annotationEntry.getValue());
				individualRetrievals.put(annotationEntry.getKey(), annotations);
			}
			return this;

		}

		public NamedEntityLinkingAnnotations build() {
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
	public Set<NERLIndividualAnnotation> getIndividualAnnotationsByTextMention(AbstractOBIEIndividual individual,
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
	public Set<NERLIndividualAnnotation> getIndividualAnnotations(AbstractOBIEIndividual individual) {
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
	public boolean containsIndividualAnnotations(AbstractOBIEIndividual individual) {
		return individual!=null && individualRetrievals.containsKey(individual) && !individualRetrievals.get(individual).isEmpty();
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
	public Set<AbstractOBIEIndividual> getAvailableIndividualTypes() {
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
