package de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;

public class NamedEntityLinkingAnnotations implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private final Map<Class<? extends IOBIEThing>, Set<NELAnnotation>> retrievals;

	private final Map<Class<? extends IOBIEThing>, Map<String, Set<NELAnnotation>>> retrievalsByTextMention;

	private final Map<Class<? extends IOBIEThing>, Map<String, Set<NELAnnotation>>> retrievalsByDistinctSemanticValues;

	private NamedEntityLinkingAnnotations(
			Map<Class<? extends IOBIEThing>, Set<NELAnnotation>> retrievals) {
		this.retrievals = retrievals;
		this.retrievalsByTextMention = indexByTextMention(retrievals);

		this.retrievalsByDistinctSemanticValues = indexBySemanticValue(retrievals);
	}

	private Map<Class<? extends IOBIEThing>, Map<String, Set<NELAnnotation>>> indexBySemanticValue(
			Map<Class<? extends IOBIEThing>, Set<NELAnnotation>> retrievals) {
		final Map<Class<? extends IOBIEThing>, Map<String, Set<NELAnnotation>>> rBDSV = new HashMap<>();
		for (Entry<Class<? extends IOBIEThing>, Set<NELAnnotation>> retrieval : retrievals.entrySet()) {

			rBDSV.put(retrieval.getKey(), new HashMap<>());
			for (NELAnnotation nera : retrieval.getValue()) {
				if (nera.semanticInterpretation == null)
					continue;

				rBDSV.get(retrieval.getKey()).putIfAbsent(nera.getDTValueIfAnyElseTextMention(), new HashSet<>());
				rBDSV.get(retrieval.getKey()).get(nera.getDTValueIfAnyElseTextMention()).add(nera);
			}
		}
		return Collections.unmodifiableMap(rBDSV);
	}

	private Map<Class<? extends IOBIEThing>, Map<String, Set<NELAnnotation>>> indexByTextMention(
			Map<Class<? extends IOBIEThing>, Set<NELAnnotation>> retrievals) {
		final Map<Class<? extends IOBIEThing>, Map<String, Set<NELAnnotation>>> rbtm = new HashMap<>();
		for (Entry<Class<? extends IOBIEThing>, Set<NELAnnotation>> retrieval : retrievals.entrySet()) {

			rbtm.put(retrieval.getKey(), new HashMap<>());
			for (NELAnnotation nera : retrieval.getValue()) {
				rbtm.get(retrieval.getKey()).putIfAbsent(nera.textMention, new HashSet<>());
				rbtm.get(retrieval.getKey()).get(nera.textMention).add(nera);
			}
		}
		return Collections.unmodifiableMap(rbtm);
	}

	public static class Builder {

		private final Map<Class<? extends IOBIEThing>, Set<NELAnnotation>> retrievals = new HashMap<>();

		public Builder() {
		}

		public Builder addAnnotations(
				Map<Class<? extends IOBIEThing>, Set<NELAnnotation>> namedEntityLinkingAnnotations) {

			for (Entry<Class<? extends IOBIEThing>, Set<NELAnnotation>> annotationEntry : namedEntityLinkingAnnotations
					.entrySet()) {

				final Set<NELAnnotation> annotations = retrievals.getOrDefault(annotationEntry.getKey(),
						new HashSet<>());
				annotations.addAll(annotationEntry.getValue());
				retrievals.put(annotationEntry.getKey(), annotations);
			}
			return this;

		}

		public NamedEntityLinkingAnnotations build() {
			return new NamedEntityLinkingAnnotations(retrievals);
		}
	}

	/**
	 * Returns the annotations for a given class type that surfaceForm matches the
	 * given string.
	 * 
	 * @param classType
	 * @return
	 */
	public Set<NELAnnotation> getAnnotationsByTextMention(Class<? extends IOBIEThing> classType,
			String surfaceForm) {

		if (!retrievalsByTextMention.containsKey(classType))
			return null;

		return retrievalsByTextMention.get(classType).get(surfaceForm);
	}

	/**
	 * Returns the annotations for a given class type that surfaceForm are distinct.
	 * 
	 * @param classType
	 * @return
	 */
	public Set<NELAnnotation> getAnnotationsBySemanticValues(
			Class<? extends IOBIEThing> classType) {
		return retrievalsByDistinctSemanticValues.get(classType).values().stream().flatMap(v -> v.stream().limit(1))
				.collect(Collectors.toSet());
	}

	/**
	 * Returns the annotations for a given class type.
	 * 
	 * @param classType
	 * @return
	 */
	public Set<NELAnnotation> getAnnotations(Class<? extends IOBIEThing> classType) {
		return retrievals.get(classType);
	}

	/**
	 * Returns the annotations for a given class type.
	 * 
	 * @param classType
	 * @return
	 */
	public Set<NELAnnotation> getOrDefaultAnnotations(Class<? extends IOBIEThing> classType,
			Set<NELAnnotation> defaultValue) {
		return retrievals.getOrDefault(classType, defaultValue);
	}

	/**
	 * Checks whether this document contains annotation data for the given class
	 * type
	 * 
	 * @param classType
	 * @return true if data is available, else false.
	 */
	public boolean containsAnnotations(Class<? extends IOBIEThing> classType) {
		return retrievals.containsKey(classType) && !retrievals.get(classType).isEmpty();
	}

	/**
	 * Returns all class types for that are annotations are available.
	 * 
	 * @return Unmodifiable set of all class types.
	 */
	public Set<Class<? extends IOBIEThing>> getAvailableClassTypes() {
		return retrievals.keySet();
	}

	public String toString() {
		return "AbstractRegExNER [retrievals=" + retrievals + "]";
	}

	/**
	 * Returns the number of annotations that are available for a given class type.
	 * 
	 * @param classType
	 * @return the number of annotations in the text fopr the given class type.
	 *         Returns 0 if there is no entry.
	 */
	public int getNumberOfAnnotations(Class<? extends IOBIEThing> classType) {
		return retrievals.containsKey(classType) ? retrievals.get(classType).size() : 0;
	}

}
