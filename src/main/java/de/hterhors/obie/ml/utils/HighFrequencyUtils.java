package de.hterhors.obie.ml.utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.AbstractOBIEIndividual;
import de.hterhors.obie.core.ontology.IndividualFactory;
import de.hterhors.obie.core.ontology.OntologyInitializer;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.variables.NERLClassAnnotation;
import de.hterhors.obie.ml.variables.NERLIndividualAnnotation;
import de.hterhors.obie.ml.variables.NamedEntityLinkingAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;

public class HighFrequencyUtils {

	private static final long SEED = 100L;

	public static Logger log = LogManager.getLogger(HighFrequencyUtils.class);

	public static ClassFrequencyPair getMostFrequentClass(Class<? extends IOBIEThing> slotType, OBIEInstance instance) {
		final List<ClassFrequencyPair> l = getMostFrequentClasses(slotType, instance, 1);
		if (l != null && !l.isEmpty())
			return l.get(0);

		return ClassFrequencyPair.nullValue;
	}

	public static IndividualFrequencyPair getMostFrequentIndividual(Class<? extends IOBIEThing> slotType,
			OBIEInstance instance) {
		final List<IndividualFrequencyPair> l = getMostFrequentIndividuals(slotType, instance, 1);
		if (l != null && !l.isEmpty())
			return l.get(0);

		return IndividualFrequencyPair.nullValue;
	}

	private static final Map<ClassCacheKey, List<ClassFrequencyPair>> classCache = new ConcurrentHashMap<>();

	private static final Map<ClassCacheKey, List<IndividualFrequencyPair>> individualCache = new ConcurrentHashMap<>();

	public static class ClassFrequencyPair implements Comparable<ClassFrequencyPair> {

		public static final ClassFrequencyPair nullValue = new ClassFrequencyPair(null, null, null, 0);
		final public Class<? extends IOBIEThing> clazz;
		final public int frequency;
		final public String datatypeValue;
		final public String textMention;

		public ClassFrequencyPair(Class<? extends IOBIEThing> clazz, String textMention, String dataTypeValue,
				int frequency) {
			this.clazz = clazz;
			this.frequency = frequency;
			this.textMention = textMention;
			this.datatypeValue = dataTypeValue;
		}

		@Override
		public int compareTo(ClassFrequencyPair o) {
			return -Integer.compare(frequency, o.frequency);
		}
	}

	public static class IndividualFrequencyPair implements Comparable<IndividualFrequencyPair> {

		public static final IndividualFrequencyPair nullValue = new IndividualFrequencyPair(null, null, null, 0);
		final public AbstractOBIEIndividual individual;
		final public Class<? extends IOBIEThing> belongingClazz;
		final public int frequency;
		final public String textMention;

		public IndividualFrequencyPair(Class<? extends IOBIEThing> belongingClazz, AbstractOBIEIndividual individual,
				String textMention, int frequency) {
			this.individual = individual;
			if (belongingClazz != null) {
				this.belongingClazz = ReflectionUtils.getImplementationClass(belongingClazz);
			} else {
				this.belongingClazz = null;
			}
			this.frequency = frequency;
			this.textMention = textMention;
		}

		@Override
		public int compareTo(IndividualFrequencyPair o) {
			return Integer.compare(frequency, o.frequency);
		}

	}

	private static class ClassCacheKey {

		final Class<? extends IOBIEThing> classType;
		final String instanceName;

		public ClassCacheKey(Class<? extends IOBIEThing> classType, String instanceName) {
			this.classType = classType;
			this.instanceName = instanceName;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((classType == null) ? 0 : classType.hashCode());
			result = prime * result + ((instanceName == null) ? 0 : instanceName.hashCode());
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
			ClassCacheKey other = (ClassCacheKey) obj;
			if (classType == null) {
				if (other.classType != null)
					return false;
			} else if (!classType.equals(other.classType))
				return false;
			if (instanceName == null) {
				if (other.instanceName != null)
					return false;
			} else if (!instanceName.equals(other.instanceName))
				return false;
			return true;
		}

	}

	@SuppressWarnings("unchecked")
	public static List<ClassFrequencyPair> getMostFrequentClasses(Class<? extends IOBIEThing> interfaceType,
			OBIEInstance instance, final int n) {

		ClassCacheKey ck = new ClassCacheKey(interfaceType, instance.getName());

		if (classCache.containsKey(ck)) {
			return classCache.get(ck).subList(0, Math.min(n, classCache.get(ck).size()));
		}

		NamedEntityLinkingAnnotations ner = instance.getNamedEntityLinkingAnnotations();

		List<ClassFrequencyPair> bestClasses = new ArrayList<>();

		final Map<Class<? extends IOBIEThing>, Set<NERLClassAnnotation>> evidences = new HashMap<>();
		final List<Class<? extends IOBIEThing>> bestClassTypes = new ArrayList<>();

		if (ReflectionUtils.isAnnotationPresent(interfaceType, DatatypeProperty.class) ) {
			for (int i = 0; i < n; i++) {
				bestClassTypes.add(ReflectionUtils.getImplementationClass(interfaceType));

				final Class<? extends IOBIEThing> bestClassType = ReflectionUtils.getImplementationClass(interfaceType);

				if (ner.containsClassAnnotations(bestClassType)) {
					final Map<String, Integer> maxMention = new HashMap<>();

					for (NERLClassAnnotation ann : ner.getClassAnnotations(bestClassType)) {
						maxMention.put(ann.text, maxMention.getOrDefault(ann.text, 0) + 1);
					}

					final Map.Entry<String, Integer> maxDataTypeValue;

					Optional<Entry<String, Integer>> optional = maxMention.entrySet().stream()
							.max(new Comparator<Map.Entry<String, Integer>>() {
								@Override
								public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {
									return Integer.compare(o1.getValue(), o2.getValue());
								}
							});

					if (optional.isPresent()) {
						maxDataTypeValue = optional.get();

						for (NERLClassAnnotation ann : ner.getClassAnnotations(bestClassType)) {
							if (ann.text.equals(maxDataTypeValue.getKey())) {
								evidences.putIfAbsent(bestClassType, new HashSet<>());
								evidences.get(bestClassType).add(ann);
								break;
							}
						}
					}
				}
			}
		} else {
			for (Class<? extends IOBIEThing> classType : ner.getAvailableClassTypes()) {

				if (interfaceType.isAssignableFrom(classType)) {

					evidences.put(classType, ner.getClassAnnotations(classType));

				}
			}
		}

		if (evidences.isEmpty()) {
			classCache.put(ck, bestClasses);
			return bestClasses;
		}

		final List<Map.Entry<Class<? extends IOBIEThing>, Set<NERLClassAnnotation>>> entryList = sort(evidences);

		for (int i = 0; i < entryList.size(); i++) {
			final Class<? extends IOBIEThing> bestClassType = entryList.get(i).getKey();
			/**
			 * TODO: NOTE: get random from set! Maybe buggy, if we are not only interested
			 * in the class type.
			 */
			if (!evidences.get(bestClassType).iterator().hasNext())
				continue;

			final NERLClassAnnotation nerAnnotation = evidences.get(bestClassType).iterator().next();
			log.debug(nerAnnotation);
			if (ReflectionUtils.isAnnotationPresent(interfaceType, DatatypeProperty.class) ) {
				bestClasses.add(new ClassFrequencyPair((Class<IOBIEThing>) bestClassType, nerAnnotation.text,
						nerAnnotation.getDTValueIfAnyElseTextMention(), evidences.get(bestClassType).size()));
			} else {
				bestClasses.add(new ClassFrequencyPair((Class<IOBIEThing>) bestClassType, nerAnnotation.text, null,
						evidences.get(bestClassType).size()));
			}
		}
		classCache.put(ck, bestClasses);
		return classCache.get(ck).subList(0, Math.min(n, classCache.get(ck).size()));
	}

	public static List<IndividualFrequencyPair> getMostFrequentIndividuals(Class<? extends IOBIEThing> slotType,
			OBIEInstance instance, final int n) {

		try {

			ClassCacheKey ck = new ClassCacheKey(slotType, instance.getName());

			if (individualCache.containsKey(ck)) {
				return individualCache.get(ck).subList(0, Math.min(n, individualCache.get(ck).size()));
			}

			final Map<AbstractOBIEIndividual, Set<NERLIndividualAnnotation>> evidences = new HashMap<>();
			/*
			 * Get nerl-annotations.
			 */
			final NamedEntityLinkingAnnotations nerlas = instance.getNamedEntityLinkingAnnotations();

			final Field factoryField = ReflectionUtils.getAccessibleFieldByName(
					ReflectionUtils.getImplementationClass(slotType),
					OntologyInitializer.INDIVIDUAL_FACTORY_FIELD_NAME);

			/*
			 * Get possible individuals for given class
			 */

			final IndividualFactory<?> factory = (IndividualFactory<?>) factoryField.get(null);
			/*
			 * For each individual search for nerl-annotations. If some exists store in list
			 * as evidence.
			 */
			for (AbstractOBIEIndividual individual : factory.getIndividuals()) {

				final Set<NERLIndividualAnnotation> individualNerlAnnotations;
				if ((individualNerlAnnotations = nerlas.getIndividualAnnotations(individual)) != null)
					evidences.put(individual, individualNerlAnnotations);

			}
			/*
			 * If no evidence save empty list in cache.
			 */
			if (evidences.isEmpty()) {
				individualCache.put(ck, Collections.emptyList());
				return individualCache.get(ck);
			}

			/*
			 * Sort individuals.
			 */
			final List<Map.Entry<AbstractOBIEIndividual, Set<NERLIndividualAnnotation>>> entryList = sort(evidences);

			final List<IndividualFrequencyPair> sortedIFPairs = new ArrayList<>();

			for (int i = 0; i < entryList.size(); i++) {
				AbstractOBIEIndividual bestClassType = entryList.get(i).getKey();
				/**
				 * TODO: NOTE: get random from set! Maybe buggy, if we are not only interested
				 * in the class type but also in the textmention.
				 */
				if (!evidences.get(bestClassType).iterator().hasNext())
					continue;

				final NERLIndividualAnnotation nerAnnotation = evidences.get(bestClassType).iterator().next();

				log.debug(nerAnnotation);

				sortedIFPairs.add(new IndividualFrequencyPair(slotType, nerAnnotation.relatedIndividual,
						nerAnnotation.text, evidences.get(bestClassType).size()));
			}
			/*
			 * Put all sorted pairs but return only n.
			 */
			individualCache.put(ck, sortedIFPairs);
			return individualCache.get(ck).subList(0, Math.min(n, individualCache.get(ck).size()));
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		return null;
	}

	/**
	 * Sorts a list of Map entries.
	 * 
	 * @param evidences
	 * @return
	 */
	private static <K, V> List<Map.Entry<K, Set<V>>> sort(final Map<K, Set<V>> evidences) {
		final List<Map.Entry<K, Set<V>>> entryList = new ArrayList<>(evidences.entrySet());

		/*
		 * Sort entries, to get in same order. This is needed to keep same order over
		 * multiple runs. Then we can sort with random seed.
		 */
		Collections.sort(entryList, new Comparator<Map.Entry<K, Set<V>>>() {

			@Override
			public int compare(Entry<K, Set<V>> o1, Entry<K, Set<V>> o2) {
				return -Integer.compare(o1.getValue().size(), o2.getValue().size());
			}
		});
		/*
		 * Shuffle list so entries with same values appears in different orders every
		 * time.
		 */
		Collections.shuffle(entryList, new Random(SEED));

		/*
		 * Sort entries according to their frequency
		 */
		Collections.sort(entryList, new Comparator<Map.Entry<K, Set<V>>>() {

			@Override
			public int compare(Entry<K, Set<V>> o1, Entry<K, Set<V>> o2) {
				return -Integer.compare(o1.getValue().size(), o2.getValue().size());
			}
		});
		return entryList;
	}

}
