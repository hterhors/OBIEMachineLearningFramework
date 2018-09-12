package de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils;

import java.util.ArrayList;
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

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.ImplementationClass;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.NELAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.NamedEntityLinkingAnnotations;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;

public class HighFrequencyUtils {

	private static final long SEED = 100L;

	public static Logger log = LogManager.getLogger(HighFrequencyUtils.class);

	public static FrequencyPair determineMostFrequentClass(Class<? extends IOBIEThing> interfaceType,
			OBIEInstance instance) {
		final List<FrequencyPair> l = determineMostFrequentClasses(interfaceType, instance, 1);
		if (l != null && !l.isEmpty())
			return l.get(0);

		return FrequencyPair.nullValue;
	}

	private static final Map<CacheKey, List<FrequencyPair>> cache = new ConcurrentHashMap<>();

	public static class FrequencyPair {

		public static final FrequencyPair nullValue = new FrequencyPair(null, null, null, 0);
		final public Class<IOBIEThing> bestClass;
		final public int frequency;
		final public String dataTypeValue;
		final public String textMention;

		public FrequencyPair(Class<IOBIEThing> bestClass, String textMention, String dataTypeValue, int frequency) {
			this.bestClass = bestClass;
			this.frequency = frequency;
			this.textMention = textMention;
			this.dataTypeValue = dataTypeValue;
		}

	}

	private static class CacheKey {

		Class<? extends IOBIEThing> classType;
		String instanceName;

		public CacheKey(Class<? extends IOBIEThing> classType, String instanceName) {
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
			CacheKey other = (CacheKey) obj;
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
	public static List<FrequencyPair> determineMostFrequentClasses(Class<? extends IOBIEThing> interfaceType,
			OBIEInstance instance, final int n) {

		CacheKey ck = new CacheKey(interfaceType, instance.getName());

		if (cache.containsKey(ck)) {
			return cache.get(ck).subList(0, Math.min(n, cache.get(ck).size()));
		}

		NamedEntityLinkingAnnotations ner = instance.getNamedEntityLinkingAnnotations();

		List<FrequencyPair> bestClasses = new ArrayList<>();

		final Map<Class<? extends IOBIEThing>, Set<NELAnnotation>> evidences = new HashMap<>();
		final List<Class<? extends IOBIEThing>> bestClassTypes = new ArrayList<>();

		if (interfaceType.isAnnotationPresent(DatatypeProperty.class)) {
			for (int i = 0; i < n; i++) {
				bestClassTypes.add(interfaceType.getAnnotation(ImplementationClass.class).get());

				final Class<? extends IOBIEThing> bestClassType = interfaceType.getAnnotation(ImplementationClass.class)
						.get();
				if (ner.containsAnnotations(bestClassType)) {
					final Map<String, Integer> maxMention = new HashMap<>();

					for (NELAnnotation ann : ner.getAnnotations(bestClassType)) {
						maxMention.put(ann.textMention, maxMention.getOrDefault(ann.textMention, 0) + 1);
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

						for (NELAnnotation ann : ner.getAnnotations(bestClassType)) {
							if (ann.textMention.equals(maxDataTypeValue.getKey())) {
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

					evidences.put(classType, ner.getAnnotations(classType));

				}
			}
		}

		if (evidences.isEmpty()) {
			cache.put(ck, bestClasses);
			return bestClasses;
		}

		final List<Map.Entry<Class<? extends IOBIEThing>, Set<NELAnnotation>>> entryList = new ArrayList<>(
				evidences.entrySet());

		/*
		 * Sort entries, to get in same order. This is needed to keep same order over
		 * multiple runs. Then we can sort with random seed.
		 */
		Collections.sort(entryList,
				new Comparator<Map.Entry<Class<? extends IOBIEThing>, Set<NELAnnotation>>>() {

					@Override
					public int compare(Entry<Class<? extends IOBIEThing>, Set<NELAnnotation>> o1,
							Entry<Class<? extends IOBIEThing>, Set<NELAnnotation>> o2) {
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
		Collections.sort(entryList,
				new Comparator<Map.Entry<Class<? extends IOBIEThing>, Set<NELAnnotation>>>() {

					@Override
					public int compare(Entry<Class<? extends IOBIEThing>, Set<NELAnnotation>> o1,
							Entry<Class<? extends IOBIEThing>, Set<NELAnnotation>> o2) {
						return -Integer.compare(o1.getValue().size(), o2.getValue().size());
					}
				});

		for (int i = 0; i < entryList.size(); i++) {
			bestClassTypes.add(entryList.get(i).getKey());
		}

		for (Class<? extends IOBIEThing> bestClassType : bestClassTypes) {
			/**
			 * TODO: NOTE: get random from set! Maybe buggy, if we are not only interested
			 * in the class type.
			 */
			if (!evidences.get(bestClassType).iterator().hasNext())
				continue;

			final NELAnnotation nerAnnotation = evidences.get(bestClassType).iterator().next();
			log.debug(nerAnnotation);
			if (interfaceType.isAnnotationPresent(DatatypeProperty.class)) {
				bestClasses.add(new FrequencyPair((Class<IOBIEThing>) bestClassType, nerAnnotation.textMention,
						nerAnnotation.getDTValueIfAnyElseTextMention(), evidences.get(bestClassType).size()));
			} else {
				bestClasses.add(new FrequencyPair((Class<IOBIEThing>) bestClassType, nerAnnotation.textMention, null,
						evidences.get(bestClassType).size()));
			}
		}
		cache.put(ck, bestClasses);
		return cache.get(ck).subList(0, Math.min(n, cache.get(ck).size()));
	}

}
