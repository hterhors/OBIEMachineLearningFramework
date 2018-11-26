package de.hterhors.obie.ml.templates;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.OntologyModelContent;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.core.tokenizer.ContentCleaner;
import de.hterhors.obie.core.tools.metric.LevenShteinSimilarity;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.templates.StringSimilarityTemplate.Scope;
import de.hterhors.obie.ml.templates.utils.BinningUtils;
import de.hterhors.obie.ml.utils.ReflectionUtils;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import factors.Factor;
import factors.FactorScope;
import learning.Vector;

/**
 * Measures the Levenshtein similarity between the annotated token(s) and the
 * assigned class name.
 * 
 * @author hterhors
 *
 * @date May 9, 2017
 */
public class StringSimilarityTemplate extends AbstractOBIETemplate<Scope> {

	public StringSimilarityTemplate(RunParameter parameter) {
		super(parameter);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private final static DecimalFormat DIGIT_FORMAT = new DecimalFormat("#.##");

	private static final int NUMBER_OF_BINS = 25;

	private static Logger log = LogManager.getFormatterLogger(StringSimilarityTemplate.class.getName());

	/**
	 * Similarity bins.
	 */
	final private float[] similarityBins = BinningUtils.getFloatBins(NUMBER_OF_BINS);

	/**
	 * Factor Scope for these variables
	 * 
	 * @author hterhors
	 *
	 * @date May 9, 2017
	 */
	class Scope extends FactorScope {

		final String surfaceForm;
		final String name;

		public Scope(Class<? extends IOBIEThing> entityRootClassType, String name, String surfaceForm) {
			super(StringSimilarityTemplate.this, entityRootClassType, name, surfaceForm);
			this.name = name;
			this.surfaceForm = surfaceForm;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		for (TemplateAnnotation entity : state.getCurrentTemplateAnnotations().getTemplateAnnotations()) {

			addFactorRecursive(factors, entity.rootClassType, entity.getThing());

		}
		return factors;
	}

	/**
	 * Adds factors recursively for each class and fields that are part of the
	 * ontology model.
	 * 
	 * @param factors2
	 * 
	 * @param entityRootClassType
	 * 
	 * @param scioClass
	 * @return
	 */
	private void addFactorRecursive(List<Scope> factors, Class<? extends IOBIEThing> entityRootClassType,
			IOBIEThing scioClass) {

		if (scioClass == null)
			return;

		if (ReflectionUtils.isAnnotationPresent(scioClass.getClass(), DatatypeProperty.class))
			return;

		final String surfaceForm = scioClass.getTextMention();

		if (surfaceForm != null) {

			factors.add(new Scope(entityRootClassType, scioClass.getClass().getSimpleName(), surfaceForm));
			if (scioClass.getIndividual() != null)
				factors.add(new Scope(entityRootClassType, scioClass.getIndividual().name, surfaceForm));

		}
		/*
		 * Add factors for object type properties.
		 */
		ReflectionUtils.getSlots(scioClass.getClass()).stream()
				.filter(f -> !ReflectionUtils.isAnnotationPresent(f, DatatypeProperty.class)).forEach(field -> {
					try {
						if (ReflectionUtils.isAnnotationPresent(field, RelationTypeCollection.class)) {
							for (IOBIEThing element : (List<IOBIEThing>) field.get(scioClass)) {
								addFactorRecursive(factors, entityRootClassType, element);
							}
						} else {
							addFactorRecursive(factors, entityRootClassType, (IOBIEThing) field.get(scioClass));
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				});
		return;
	}

	@Override
	public void computeFactor(Factor<Scope> factor) {
		Vector featureVector = factor.getFeatureVector();

		final String className = factor.getFactorScope().name;
		final String surfaceForm = factor.getFactorScope().surfaceForm;

		final double similarity = LevenShteinSimilarity.levenshteinSimilarity(className, surfaceForm, 0);

		for (Float bin : similarityBins) {

			if (similarity >= bin) {
				featureVector.set("LevenShtein sim for [" + className + "] >= " + DIGIT_FORMAT.format(bin),
						similarity >= bin);
				break;
			}

		}

	}

}
