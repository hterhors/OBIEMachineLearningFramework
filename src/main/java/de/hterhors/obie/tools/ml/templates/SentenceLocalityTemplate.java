package de.hterhors.obie.tools.ml.templates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.annotations.OntologyModelContent;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.tools.ml.run.param.OBIERunParameter;
import de.hterhors.obie.tools.ml.templates.SentenceLocalityTemplate.Scope;
import de.hterhors.obie.tools.ml.templates.scope.OBIEFactorScope;
import de.hterhors.obie.tools.ml.variables.OBIEInstance;
import de.hterhors.obie.tools.ml.variables.OBIEState;
import de.hterhors.obie.tools.ml.variables.TemplateAnnotation;
import factors.Factor;
import learning.Vector;

/**
 * The {@link SentenceLocalityTemplate} measures the distance of annotated
 * properties of an annotation. Factors are generated for each pair of
 * properties of the same parent class and each pair of parent class-property.
 * 
 * This template should help to find out if parent and properties needs to be in
 * the same sentence or how far away they can be.
 * 
 * NOTE: Since the gold annotations does not always (or rather rarely) have
 * character / sentence positions annotated this template might not work as
 * expected. During sampling, the system assigns possible candidate classes
 * (including their character offset and surface form) to the properties.
 * However, as the objective function does not take this information into
 * account, the correctly annotated class type (standing not even close to its
 * supposed to be parent) might be not the expected one (the closest or which
 * might be contextual enriched).
 * 
 * However, this template should work (at least a bit) if there is enough
 * training data or even training over multiple epochs (if the training
 * instances are randomized!). As the correct candidate for a property is always
 * (at least for some ontology classes like scio:{@link AnimalModel}) close to
 * its parent. This needs to be investigated!
 * 
 * @see {@link ClosestCandidateTempalte}
 * 
 * @author hterhors
 *
 * @date Dec 12, 2017
 */
public class SentenceLocalityTemplate extends AbstractOBIETemplate<Scope> {

	public SentenceLocalityTemplate(OBIERunParameter parameter) {
		super(parameter);
		// TODO Auto-generated constructor stub
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The logger.
	 */
	private static Logger log = LogManager.getFormatterLogger(SentenceLocalityTemplate.class.getName());

	// /**
	// * The different sentence distances we want to investigate. The features
	// are
	// * binned using this list.
	// */
	// private static final List<Integer> sentenceDistances = Arrays.asList(1,
	// 2, 3, 4, 5, 6, 7, 8, 9, 10);

	static class Scope extends OBIEFactorScope {

		private final Class<? extends IOBIEThing> class1;
		private final Class<? extends IOBIEThing> class2;
		private final int sentenceDistance;

		public Scope(Set<Class<? extends IOBIEThing>> influencedVariable,
				Class<? extends IOBIEThing> entityRootClassType, AbstractOBIETemplate<?> template,
				OBIEInstance document, Class<? extends IOBIEThing> class1, Class<? extends IOBIEThing> class2,
				final int sentenceDistance) {
			super(influencedVariable, entityRootClassType, template, class1, class2, sentenceDistance,
					entityRootClassType);
			this.class1 = class1;
			this.class2 = class2;
			this.sentenceDistance = sentenceDistance;
		}

		@Override
		public String toString() {
			return "FactorVariables [class1=" + class1 + ", class2=" + class2 + ", getInfluencedVariables()="
					+ getInfluencedVariables() + "]";
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		for (TemplateAnnotation entity : state.getCurrentPrediction().getTemplateAnnotations()) {
			addRecursive(factors, entity.rootClassType, state.getInstance(), entity.getTemplateAnnotation());
		}
		return factors;
	}

	private void addRecursive(final List<Scope> factors, Class<? extends IOBIEThing> entityRootClassType,
			OBIEInstance instance, final IOBIEThing ontologyInstance) {

		if (ontologyInstance == null)
			return;

		/*
		 * Get all properties
		 */
		final List<IOBIEThing> properties = collectValuesFromFields(ontologyInstance);

		/*
		 * Sort them to ensure always the same order.
		 */
		Collections.sort(properties, new Comparator<IOBIEThing>() {

			@Override
			public int compare(IOBIEThing arg0, IOBIEThing arg1) {
				return arg0.getClass().getSimpleName().compareTo(arg1.getClass().getSimpleName());
			}
		});

		/*
		 * Add factors for each pair of properties and each pair of property-parent
		 * relation
		 */
		for (int i = 0; i < properties.size(); i++) {
			final IOBIEThing propertyClass1 = properties.get(i);
			for (int j = i + 1; j < properties.size(); j++) {
				final IOBIEThing propertyClass2 = properties.get(j);
				addFactor(entityRootClassType, instance, propertyClass1, propertyClass2, factors);
				addFactor(entityRootClassType, instance, ontologyInstance, propertyClass2, factors);
			}
			addFactor(entityRootClassType, instance, ontologyInstance, propertyClass1, factors);
			/*
			 * Call recursive for all properties as new parent.
			 */
			addRecursive(factors, entityRootClassType, instance, propertyClass1);
		}
	}

	/**
	 * Collects values from the field of a given ontology instance.
	 * 
	 * @param ontologyInstance the object of the ontology from that the data should
	 *                         be collected.
	 * @return a list of ontology instances, coming from the fields of the input
	 *         ontology instance.
	 */
	private List<IOBIEThing> collectValuesFromFields(final IOBIEThing ontologyInstance) {
		return Arrays.stream(ontologyInstance.getClass().getDeclaredFields())
				.filter(f -> f.isAnnotationPresent(OntologyModelContent.class)).map(f -> {
					try {
						f.setAccessible(true);
						if (f.isAnnotationPresent(RelationTypeCollection.class)) {
							/**
							 * TODO: Integrate lists.
							 */
							throw new NotImplementedException(
									"SentenceLocalityTemplate can not handle OneToManyRelations for class: "
											+ ontologyInstance);
						} else {
							return (IOBIEThing) f.get(ontologyInstance);
						}

					} catch (Exception e) {
						e.printStackTrace();
					}
					return null;
				}).filter(e -> e != null).collect(Collectors.toList());
	}

	/**
	 * Adds a factor to the given list of factors. The scope of this factor is
	 * defined by {@link Scope}. A factor is defined by the pair of
	 * ontologyClassTypes and the sentence distance they annotations have.
	 * 
	 * @param entityRootClassType
	 * @param instance
	 * @param class1
	 * @param class2
	 * @param factors
	 */
	private void addFactor(Class<? extends IOBIEThing> entityRootClassType, OBIEInstance instance,
			IOBIEThing class1, IOBIEThing class2, List<Scope> factors) {

		if (class1.getCharacterOnset() == null)
			return;

		if (class2.getCharacterOnset() == null)
			return;

		final Set<Class<? extends IOBIEThing>> influencedVariables = new HashSet<>();

		influencedVariables.add(class1.getClass());
		influencedVariables.add(class2.getClass());

		final int class1SentenceIndex = instance.charPositionToToken(class1.getCharacterOnset()).getSentenceIndex();
		final int class2SentenceIndex = instance.charPositionToToken(class2.getCharacterOnset()).getSentenceIndex();
		final int distance = Math.abs(class1SentenceIndex - class2SentenceIndex);
		// System.out.println("#####");
		// System.out.println(
		// class1SentenceIndex + "-->" + class1.getClass().getSimpleName() + ":"
		// + class1.getTextMention());
		// System.out.println(
		// class2SentenceIndex + "-->" + class2.getClass().getSimpleName() + ":"
		// + class2.getTextMention());
		// System.out.println("#####");
		factors.add(new Scope(influencedVariables, entityRootClassType, this, instance, class1.getClass(),
				class2.getClass(), distance));

	}

	@Override
	public void computeFactor(Factor<Scope> factor) {
		Vector featureVector = factor.getFeatureVector();

		Class<? extends IOBIEThing> class1Type = factor.getFactorScope().class1;
		Class<? extends IOBIEThing> class2Type = factor.getFactorScope().class2;
		final int sentenceDistance = factor.getFactorScope().sentenceDistance;
		/*
		 * Add features for annotated types
		 */
		addFeatures(featureVector, class1Type, class2Type, sentenceDistance);

		/*
		 * Make it more general by adding root class types as features as well.
		 */
		// if (!class1Type.isAnnotationPresent(DataTypeProperty.class))
		// class1Type = class1Type.getAnnotation(RootSuperClass.class).get();
		// if (!class2Type.isAnnotationPresent(DataTypeProperty.class))
		// class2Type = class2Type.getAnnotation(RootSuperClass.class).get();
		//
		// /*
		// * Add features for root classes of annotations.
		// */
		// addFeatures(featureVector, class1Type, class2Type, sentenceDistance);
	}

	private void addFeatures(Vector featureVector, Class<? extends IOBIEThing> class1Type,
			Class<? extends IOBIEThing> class2Type, int sentenceDistance) {

		final String class1Name = class1Type.getSimpleName();
		final String class2Name = class2Type.getSimpleName();
		/*
		 * Add features as bins
		 */
		// System.out.println(class1Name + "->" + class2Name + ": " +
		// sentenceDistance);

		// for (final int localityDist : localityDist) {

		for (int localityDist = 0; localityDist < sentenceDistance; localityDist++) {

			featureVector.set(class1Name + "->" + class2Name + " sentence dist < " + localityDist,
					localityDist < sentenceDistance);
			// featureVector.set(class1Name + "->" + class2Name + " sentence
			// dist > " + localityDist,
			// sentenceDistance > localityDist);
			// featureVector.set(class1Name + "->" + class2Name + " sentence
			// dist = " + localityDist,
			// sentenceDistance == localityDist);

		}
	}

}
