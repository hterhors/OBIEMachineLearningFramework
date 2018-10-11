package de.hterhors.obie.ml.templates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.OntologyModelContent;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.run.param.OBIERunParameter;
import de.hterhors.obie.ml.templates.LocalLocalityTemplate.Scope;
import de.hterhors.obie.ml.templates.utils.ClassTypePositionPair;
import de.hterhors.obie.ml.utils.ReflectionUtils;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import factors.Factor;
import factors.FactorScope;
import learning.Vector;

@Deprecated
public class LocalLocalityTemplate extends AbstractOBIETemplate<Scope> {

	public LocalLocalityTemplate(OBIERunParameter parameter) {
		super(parameter);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Logger log = LogManager.getFormatterLogger(LocalLocalityTemplate.class.getName());

	private static final List<Integer> localityDistances = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 30, 40, 50,
			60, 70, 80, 90, 100, 150, 200, 300);

	class Scope extends FactorScope {

		private final ClassTypePositionPair class1;
		private final ClassTypePositionPair class2;
		private final OBIEInstance document;

		public Scope(Class<? extends IOBIEThing> entityRootClassType, AbstractOBIETemplate<?> template,
				OBIEInstance document, ClassTypePositionPair class1, ClassTypePositionPair class2) {
			super(template, class1, class2, entityRootClassType);
			this.class1 = class1;
			this.class2 = class2;
			this.document = document;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		for (TemplateAnnotation entity : state.getCurrentTemplateAnnotations().getTemplateAnnotations()) {
			try {
				factors.addAll(
						addFactorRecursive(entity.rootClassType, state.getInstance(), entity.get()));
			} catch (IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		return factors;
	}

	private List<Scope> addFactorRecursive(Class<? extends IOBIEThing> entityRootClassType, OBIEInstance document,
			final IOBIEThing scioClass) throws IllegalArgumentException, IllegalAccessException, SecurityException {
		List<Scope> factors = new ArrayList<>();

		if (scioClass == null)
			return factors;

		List<IOBIEThing> properties = convertFieldPropertiesToClasses(scioClass);
		Collections.sort(properties, new Comparator<IOBIEThing>() {

			@Override
			public int compare(IOBIEThing arg0, IOBIEThing arg1) {
				return (arg0 == null ? "null" : arg0.getClass().getSimpleName())
						.compareTo((arg1 == null ? "null" : arg1.getClass().getSimpleName()));
			}
		});

		/*
		 * Add factors for object type properties.
		 */

		for (int i = 0; i < properties.size(); i++) {
			IOBIEThing propertyClass1 = properties.get(i);

			for (int j = i + 1; j < properties.size(); j++) {

				IOBIEThing propertyClass2 = properties.get(j);

				addFactor(entityRootClassType, document, propertyClass1, propertyClass2, factors);
				addFactor(entityRootClassType, document, scioClass, propertyClass2, factors);

			}
			addFactor(entityRootClassType, document, scioClass, propertyClass1, factors);
			factors.addAll(addFactorRecursive(entityRootClassType, document, propertyClass1));
		}

		return factors;

	}

	private List<IOBIEThing> convertFieldPropertiesToClasses(final IOBIEThing scioClass) {
		List<IOBIEThing> properties = Arrays.stream(scioClass.getClass().getDeclaredFields())
				.filter(f -> (!f.isAnnotationPresent(DatatypeProperty.class)
						&& f.isAnnotationPresent(OntologyModelContent.class)))
				.map(f -> {
					try {
						f.setAccessible(true);
						return (IOBIEThing) f.get(scioClass);
					} catch (Exception e) {
						e.printStackTrace();
					}
					return null;
				}).filter(e -> e != null).collect(Collectors.toList());
		return properties;
	}

	private void addFactor(Class<? extends IOBIEThing> entityRootClassType, OBIEInstance document, IOBIEThing class1,
			IOBIEThing class2, List<Scope> factors) {

		if (class1 == null)
			return;

		if (class2 == null)
			return;

		if (class1.getClass().isAnnotationPresent(DatatypeProperty.class))
			return;

		if (class2.getClass().isAnnotationPresent(DatatypeProperty.class))
			return;

		if (class1.getCharacterOnset() == null)
			return;

		if (class2.getCharacterOnset() == null)
			return;

		final long class1Onset = document.charPositionToTokenPosition(class1.getCharacterOnset());
		final long class2Onset = document.charPositionToTokenPosition(class2.getCharacterOnset());

		final ClassTypePositionPair class1Pair = new ClassTypePositionPair(class1.getClass(), class1Onset);

		final ClassTypePositionPair class2Pair = new ClassTypePositionPair(class2.getClass(), class2Onset);

		factors.add(new Scope(entityRootClassType, this, document, class1Pair, class2Pair));

	}

	@Override
	public void computeFactor(Factor<Scope> factor) {
		Vector featureVector = factor.getFeatureVector();

		final OBIEInstance document = factor.getFactorScope().document;

		double classDistance = Math
				.abs(factor.getFactorScope().class1.position - factor.getFactorScope().class2.position);

		Class<? extends IOBIEThing> class1Type = factor.getFactorScope().class1.classType;
		Class<? extends IOBIEThing> class2Type = factor.getFactorScope().class2.classType;

		/*
		 * Get all annotation mentions for original class type 1.
		 */
		List<Integer> mentionsForClass1 = document.getNamedEntityLinkingAnnotations().getClassAnnotations(class1Type)
				.stream().map(m -> document.charPositionToTokenPosition(m.onset)).collect(Collectors.toList());

		/*
		 * Get all annotation mentions for original class type 2.
		 */
		List<Integer> mentionsForClass2 = new ArrayList<>(document.getNamedEntityLinkingAnnotations()
				.getClassAnnotations(factor.getFactorScope().class1.classType)).stream()
						.map(m -> document.charPositionToTokenPosition(m.onset)).collect(Collectors.toList());

		double closestClassDistance = Integer.MAX_VALUE;

		for (int i = 0; i < mentionsForClass1.size(); i++) {
			for (int j = 0; j < mentionsForClass2.size(); j++) {
				classDistance = Math.min(classDistance, Math.abs(mentionsForClass1.get(i) - mentionsForClass2.get(j)));
			}
		}

		final boolean isClosestDistance = closestClassDistance == classDistance;

		/*
		 * Add features for annotated class types
		 */
		addFeatures(featureVector, class1Type, class2Type, classDistance, isClosestDistance);

		for (Class<? extends IOBIEThing> rootClassType1 : ReflectionUtils.getSuperRootClasses(class1Type)) {
			if (!class1Type.isAnnotationPresent(DatatypeProperty.class))
				class1Type = rootClassType1;

			for (Class<? extends IOBIEThing> rootClassType2 : ReflectionUtils.getSuperRootClasses(class2Type)) {

				if (!class2Type.isAnnotationPresent(DatatypeProperty.class))
					class2Type = rootClassType2;

				/*
				 * Add features for root classes of annotations.
				 */
				addFeatures(featureVector, class1Type, class2Type, classDistance, isClosestDistance);

			}
		}
	}

	/**
	 * Gets all annotations mentions for the classes and computes the minimum
	 * distance between them. Features are then stored as bins.
	 * 
	 * @param determinatorFactor
	 * @param featureVector
	 * @param instance
	 * @param class1Type
	 * @param class2Type
	 * @param contextType
	 */
	private void addFeatures(Vector featureVector, Class<? extends IOBIEThing> class1Type,
			Class<? extends IOBIEThing> class2Type, double classDistance, boolean isClosestDistance) {

		final String class1Name = class1Type.getSimpleName();
		final String class2Name = class2Type.getSimpleName();
		/*
		 * Add features as bins
		 */
		for (final int localityDist : localityDistances) {

			featureVector.set("IsClosest_+" + isClosestDistance + "_[" + class1Name + "_" + class2Name
					+ "] min token dist < " + localityDist, classDistance < localityDist);
			featureVector.set("IsClosest_+" + isClosestDistance + "_[" + class1Name + "_" + class2Name
					+ "] min token dist > " + localityDist, classDistance > localityDist);
			featureVector.set("IsClosest_+" + isClosestDistance + "_[" + class1Name + "_" + class2Name
					+ "] min token dist = " + localityDist, classDistance == localityDist);

		}
	}

}
