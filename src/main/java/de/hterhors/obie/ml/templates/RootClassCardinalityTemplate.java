package de.hterhors.obie.ml.templates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.ner.NERLClassAnnotation;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.templates.RootClassCardinalityTemplate.Scope;
import de.hterhors.obie.ml.utils.ReflectionUtils;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import factors.Factor;
import factors.FactorScope;
import learning.Vector;

/**
 * WORKS ONLY FOR ROOT CLASSES NOT FOR PROPERTIES WITH CARDINALITIES.
 * 
 * @author hterhors
 *
 *         Apr 24, 2017
 */
public class RootClassCardinalityTemplate extends AbstractOBIETemplate<Scope> {

	public RootClassCardinalityTemplate(RunParameter parameter) {
		super(parameter);
	}

	private static Logger log = LogManager.getFormatterLogger(RootClassCardinalityTemplate.class.getName());

	/**
	 * Captures cardinality of evidences in text.
	 */
	final private static String TEMPLATE_0 = "Textual_evidence_for_%s_in_%s = %d && %s_cardinality = %d";

	/**
	 * Captures cardinality of different types of a super type (e.g. gender) in
	 * text.
	 */
	final private static String TEMPLATE_1 = "Number_of_%s_in_%s = %d && %s_cardinality = %d";

	/**
	 * Sets num of types of class with number of entities in relation. Measures how
	 * many types are already used.
	 */
	final private static String TEMPLATE_2 = "Unused_%s in %s = %d";

	class Scope extends FactorScope {
		final OBIEInstance document;
		final int rootCardinality;
		final String rootClass;
		final Class<? extends IOBIEThing> propertyClass;

		public Scope(Class<? extends IOBIEThing> entityRootClassType, AbstractOBIETemplate<?> template,
				OBIEInstance document, String rootClass, Class<? extends IOBIEThing> propertyClass,
				int rootCardinality) {
			super(template, document, rootClass, propertyClass, rootCardinality, entityRootClassType);
			this.document = document;
			this.rootClass = rootClass;
			this.propertyClass = propertyClass;
			this.rootCardinality = rootCardinality;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();

		final Map<Class<? extends IOBIEThing>, Integer> countRootClasses = new HashMap<>();

		/*
		 * If there is only one rootClass (e.g. OrganismModel) the entry of the map for
		 * that class should be equal to state.getPredictedResult.getEntities().size();
		 */
		state.getCurrentTemplateAnnotations().getTemplateAnnotations().stream().map(a -> a.getThing())
				.forEach(s -> countRootClasses.put(s.getClass(), 1 + countRootClasses.getOrDefault(s.getClass(), 0)));

		for (TemplateAnnotation entity : state.getCurrentTemplateAnnotations().getTemplateAnnotations()) {

			factors.addAll(addFactors(entity.rootClassType, state.getInstance(), countRootClasses,
					entity.getThing()));

		}
		return factors;
	}

	private List<Scope> addFactors(Class<? extends IOBIEThing> entityRootClassType, OBIEInstance psinkDocument,
			Map<Class<? extends IOBIEThing>, Integer> countRootClasses, final IOBIEThing rootClass) {
		List<Scope> factors = new ArrayList<>();

		ReflectionUtils.getSlots(rootClass.getClass()).forEach(field -> {
			try {
				if (ReflectionUtils.isAnnotationPresent(field,RelationTypeCollection.class)) {
					final int rootCardinality = countRootClasses.get(rootClass.getClass());
					for (IOBIEThing element : (List<IOBIEThing>) field.get(rootClass)) {

						factors.add(new Scope(entityRootClassType, this, psinkDocument,
								rootClass.getClass().getSimpleName(), element.getClass(), rootCardinality));
					}
				} else {

					final IOBIEThing property = ((IOBIEThing) field.get(rootClass));

					if (property != null) {

						Class<? extends IOBIEThing> propertyClassType = property.getClass();

						final int rootCardinality = countRootClasses.get(rootClass.getClass());

						/*
						 * Add feature class type of the field.
						 */
						factors.add(new Scope(entityRootClassType, this, psinkDocument,
								rootClass.getClass().getSimpleName(), propertyClassType, rootCardinality));

					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		return factors;

	}

	@Override
	public void computeFactor(Factor<Scope> factor) {
		Vector featureVector = factor.getFeatureVector();

		final Set<NERLClassAnnotation> evidenceMentions = factor.getFactorScope().document
				.getNamedEntityLinkingAnnotations().getClassAnnotations(factor.getFactorScope().propertyClass);
		int propertyEvidence = evidenceMentions == null ? 0 : evidenceMentions.size();

		featureVector.set("#OfRootClasses = " + factor.getFactorScope().rootCardinality, true);
		featureVector.set("#OfRootClasses < 6", factor.getFactorScope().rootCardinality < 6);

		featureVector.set(String.format(TEMPLATE_0, factor.getFactorScope().propertyClass.getSimpleName(),
				factor.getFactorScope().rootClass, propertyEvidence, factor.getFactorScope().rootClass,
				factor.getFactorScope().rootCardinality), true);

		if (ReflectionUtils.isAnnotationPresent(factor.getFactorScope().propertyClass, DatatypeProperty.class))
			return;

		/*
		 * Add type of the field which is the root of the actual class. (More general)
		 */
		Set<Class<? extends IOBIEThing>> propertyRootClassTypes = ReflectionUtils
				.getSuperRootClasses(factor.getFactorScope().propertyClass);

		for (Class<? extends IOBIEThing> propertyRootClassType : propertyRootClassTypes) {

			featureVector.set(String.format(TEMPLATE_0, propertyRootClassType.getSimpleName(),
					factor.getFactorScope().rootClass, propertyEvidence, factor.getFactorScope().rootClass,
					factor.getFactorScope().rootCardinality), true);

			if (ReflectionUtils.getAssignableSubClasses(propertyRootClassType) == null
					|| ReflectionUtils.getAssignableSubClasses(propertyRootClassType).isEmpty())
				return;

			int countDifferentSubclassEvidences = 0;
			for (Class<? extends IOBIEThing> subClass : ReflectionUtils
					.getAssignableSubClasses(propertyRootClassType)) {
				final Set<NERLClassAnnotation> evidenceList = factor.getFactorScope().document
						.getNamedEntityLinkingAnnotations().getClassAnnotations(subClass);
				countDifferentSubclassEvidences += evidenceList == null || evidenceList.isEmpty() ? 0 : 1;
			}

			featureVector.set(String.format(TEMPLATE_1, propertyRootClassType.getSimpleName(),
					factor.getFactorScope().rootClass, countDifferentSubclassEvidences,
					factor.getFactorScope().rootClass, factor.getFactorScope().rootCardinality), true);

			int unusedCandidates = countDifferentSubclassEvidences - factor.getFactorScope().rootCardinality;

			featureVector.set(String.format(TEMPLATE_2, propertyRootClassType.getSimpleName(),
					factor.getFactorScope().rootClass, unusedCandidates), true);

		}
	}

}
