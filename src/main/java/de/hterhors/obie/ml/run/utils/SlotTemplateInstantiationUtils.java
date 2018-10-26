package de.hterhors.obie.ml.run.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.commons.lang3.NotImplementedException;

import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.ImplementationClass;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.explorer.utils.ExplorationUtils;
import de.hterhors.obie.ml.ner.NERLClassAnnotation;
import de.hterhors.obie.ml.utils.OBIEUtils;
import de.hterhors.obie.ml.utils.ReflectionUtils;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.TemplateAnnotation;

public class SlotTemplateInstantiationUtils {

	/*
	 * This is used for wrong initialization of a data type value. Its supposed to
	 * be always wrong.
	 */
	private static final String DEFAULT_WRONG_DT_VALUE = "####";

	private static Random rand = new Random(100L);

	/**
	 * The initial object defines the starting point of the sampling procedure.
	 * usually this should be an instance of the searchType. However you cann
	 * provide knowledge from the beginning.
	 * 
	 */

	public static IOBIEThing getEmptyInstance(Class<? extends IOBIEThing> searchType) {
		try {
			return searchType.newInstance();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | SecurityException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static IOBIEThing getFullRandomInstance(OBIEInstance instance, Class<? extends IOBIEThing> searchType) {
		try {
			IOBIEThing o = searchType.newInstance();
			fillRecursive(instance, o, true);
			return o;
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static IOBIEThing getFullWrong(Class<? extends IOBIEThing> searchType) {
		try {
			IOBIEThing o = searchType.newInstance();
			fillRecursive(null, o, false);
			return o;
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Performs a full deep clone-copy of the given gold data.
	 * 
	 * @param instanceAnnotations
	 * @return
	 */
	public static Set<IOBIEThing> getFullCorrect(InstanceTemplateAnnotations instanceAnnotations) {
		Set<IOBIEThing> set = new HashSet<>();
		for (TemplateAnnotation goldAnnotation : instanceAnnotations.getTemplateAnnotations()) {
			set.add(OBIEUtils.deepClone(goldAnnotation.getThing()));

		}
		return set;

	}

	@SuppressWarnings("unchecked")
	private static void fillRecursive(final OBIEInstance instance, final IOBIEThing object, final boolean random) {

		if (object == null)
			return;
		/*
		 * Add factors for object type properties.
		 */
		Arrays.stream(object.getClass().getDeclaredFields())
				.filter(f -> ReflectionUtils.isAnnotationPresent(f, DatatypeProperty.class)).forEach(field -> {
					field.setAccessible(true);
					try {
						if (field.isAnnotationPresent(RelationTypeCollection.class)) {

							Class<? extends IOBIEThing> slotSuperType = ((Class<? extends IOBIEThing>) ((ParameterizedType) field
									.getGenericType()).getActualTypeArguments()[0]);
							if (slotSuperType.isAnnotationPresent(ImplementationClass.class)) {
								if (ReflectionUtils.isAnnotationPresent(field, DatatypeProperty.class)) {
									slotSuperType = ReflectionUtils.getImplementationClass(slotSuperType);
									final IOBIEThing value = getValueForDT(instance, random, slotSuperType);
									((List<IOBIEThing>) field.get(object)).add(value);
								} else {
									final IOBIEThing value = getValueForNonDT(instance, random, slotSuperType);
									((List<IOBIEThing>) field.get(object)).add(value);
									fillRecursive(instance, value, random);
								}
							} else {
								throw new NotImplementedException(
										"Initialization can not be done. Can not handle fields with interface types that do not have an implementation class:"
												+ slotSuperType);
							}
						} else {
							Class<? extends IOBIEThing> slotSuperType = (Class<? extends IOBIEThing>) field.getType();
							if (slotSuperType.isAnnotationPresent(ImplementationClass.class)) {
								if (ReflectionUtils.isAnnotationPresent(field, DatatypeProperty.class)) {
									slotSuperType = ReflectionUtils.getImplementationClass(slotSuperType);
									final IOBIEThing value = getValueForDT(instance, random, slotSuperType);
									field.set(object, value);
								} else {
									final IOBIEThing value = getValueForNonDT(instance, random, slotSuperType);
									field.set(object, value);
									fillRecursive(instance, value, random);
								}
							} else {
								throw new NotImplementedException(
										"Initialization can not be done. Can not handle fields with interface types that do not have an implementation class: "
												+ slotSuperType);
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				});

	}

	private static IOBIEThing getValueForDT(OBIEInstance instance, boolean random,
			final Class<? extends IOBIEThing> slotSuperType)
			throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		final String textValue;
		final String dtValue;
		if (random) {
			final Set<NERLClassAnnotation> candidates = instance.getNamedEntityLinkingAnnotations()
					.getClassAnnotations(slotSuperType);
			if (candidates != null && !candidates.isEmpty()) {
				int index = rand.nextInt(candidates.size());
				Iterator<NERLClassAnnotation> iter = candidates.iterator();
				for (int i = 0; i < index; i++) {
					iter.next();
				}
				final NERLClassAnnotation nera = iter.next();
				textValue = nera.text;
				dtValue = nera.getDTValueIfAnyElseTextMention();
			} else {
				textValue = null;
				dtValue = null;
			}
		} else {
			textValue = DEFAULT_WRONG_DT_VALUE;
			dtValue = DEFAULT_WRONG_DT_VALUE;
		}

		IOBIEThing value = slotSuperType.getConstructor(String.class, String.class).newInstance(textValue, dtValue);
		return value;
	}

	private static IOBIEThing getValueForNonDT(OBIEInstance instance, boolean random,
			final Class<? extends IOBIEThing> slotSuperType) throws InstantiationException, IllegalAccessException {
		final IOBIEThing value;
		if (random) {
			Set<IOBIEThing> candidates = ExplorationUtils.getCandidates(instance, slotSuperType, new HashSet<>(), false,
					false);
			if (candidates != null && !candidates.isEmpty()) {
				int index = rand.nextInt(candidates.size());
				Iterator<IOBIEThing> iter = candidates.iterator();
				for (int i = 0; i < index; i++) {
					iter.next();
				}
				value = iter.next();
			} else {
				value = null;
			}
		} else {
			value = slotSuperType.newInstance();
		}
		return value;
	}

}
