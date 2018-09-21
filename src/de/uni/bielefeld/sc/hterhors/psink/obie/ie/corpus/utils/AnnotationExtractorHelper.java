package de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.utils;

import java.lang.reflect.Field;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.ReflectionUtils;

public class AnnotationExtractorHelper {

	private static Logger log = LogManager.getFormatterLogger(AnnotationExtractorHelper.class);

	/**
	 * Applies the limit to properties of type OneToManyRelation. If a list has more
	 * elements than the given limit, the rest will be removed.
	 * 
	 * @param annotation  the annotation.
	 * @param objectLimit the given limit to apply.
	 */
	public static boolean testLimitToAnnnotationElementsRecursively(IOBIEThing annotation, final int objectLimit,
			final int datatypeLimit) {

		if (annotation == null)
			return true;

		final List<Field> fields = ReflectionUtils.getDeclaredOntologyFields(annotation.getClass());

		for (Field field : fields) {
			try {
				if (field.isAnnotationPresent(RelationTypeCollection.class)) {

					@SuppressWarnings("unchecked")
					List<IOBIEThing> elements = (List<IOBIEThing>) field.get(annotation);

					if (elements.size() > (field.isAnnotationPresent(DatatypeProperty.class) ? datatypeLimit
							: objectLimit)) {
						log.warn("Found property that elements in field: " + field.getName() + " exceeds given limit: "
								+ elements.size() + " > " + objectLimit);
						return false;
					}
					for (IOBIEThing element : elements) {
						final boolean m = testLimitToAnnnotationElementsRecursively(element, objectLimit,
								datatypeLimit);
						if (!m)
							return false;
					}
				} else {
					final boolean m = testLimitToAnnnotationElementsRecursively((IOBIEThing) field.get(annotation),
							objectLimit, datatypeLimit);
					if (!m)
						return false;
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		return true;
	}

	/**
	 * Checks at compile time whether the assignment can be done or not.
	 * 
	 * @param typeClass
	 * @param type
	 * @return
	 */
	public static <T extends IOBIEThing> boolean isAssignAbleFrom(Class<T> typeClass, T type) {
		return type != null && typeClass.isAssignableFrom(type.getClass());
	}
}
