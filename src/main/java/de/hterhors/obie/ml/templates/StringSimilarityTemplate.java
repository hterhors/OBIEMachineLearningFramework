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
import de.hterhors.obie.ml.run.param.OBIERunParameter;
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

	public StringSimilarityTemplate(OBIERunParameter parameter) {
		super(parameter);
		// TODO Auto-generated constructor stub
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
	final private float[] similarities = BinningUtils.getFloatBins(NUMBER_OF_BINS);

	/**
	 * Factor Scope for these variables
	 * 
	 * @author hterhors
	 *
	 * @date May 9, 2017
	 */
	class Scope extends FactorScope {

		final String surfaceForm;
		final String ontologyURI;

		public Scope(Class<? extends IOBIEThing> entityRootClassType, AbstractOBIETemplate<?> template,
				String className, String surfaceForm) {
			super(template, className, surfaceForm, entityRootClassType);
			this.ontologyURI = className;
			this.surfaceForm = surfaceForm;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		for (TemplateAnnotation entity : state.getCurrentTemplateAnnotations().getTemplateAnnotations()) {

			factors.addAll(addFactorRecursive(entity.rootClassType, entity.get()));

		}
		return factors;
	}

	/**
	 * Adds factors recursively for each class and fields that are part of the
	 * ontology model.
	 * 
	 * @param entityRootClassType
	 * 
	 * @param scioClass
	 * @return
	 */
	private List<Scope> addFactorRecursive(Class<? extends IOBIEThing> entityRootClassType, IOBIEThing scioClass) {
		List<Scope> factors = new ArrayList<>();

		if (scioClass == null)
			return factors;

		final String ontologyURI = scioClass.getONTOLOGY_NAME();

		final String surfaceForm = scioClass.getTextMention();

		if (!ReflectionUtils.isAnnotationPresent(scioClass.getClass(), DatatypeProperty.class))
			if (surfaceForm != null) {

				final Set<Class<? extends IOBIEThing>> influencedVariables = new HashSet<>();
				influencedVariables.add(scioClass.getClass());

				factors.add(new Scope(entityRootClassType, this, ontologyURI, surfaceForm));
			}
		/*
		 * Add factors for object type properties.
		 */
		Arrays.stream(scioClass.getClass().getDeclaredFields())
				.filter(f -> (!ReflectionUtils.isAnnotationPresent(f, DatatypeProperty.class)
						&& f.isAnnotationPresent(OntologyModelContent.class)))
				.forEach(field -> {
					field.setAccessible(true);
					try {
						if (field.isAnnotationPresent(RelationTypeCollection.class)) {
							for (IOBIEThing element : (List<IOBIEThing>) field.get(scioClass)) {
								factors.addAll(addFactorRecursive(entityRootClassType, element));
							}
						} else {
							factors.addAll(addFactorRecursive(entityRootClassType, (IOBIEThing) field.get(scioClass)));
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

		final String className = ContentCleaner.bagOfWordsTokenizer(factor.getFactorScope().ontologyURI
				.substring(1 + factor.getFactorScope().ontologyURI.lastIndexOf('/')));
		final String surfaceForm = factor.getFactorScope().surfaceForm;

		// System.out.println("className = " + className);
		// System.out.println("surfaceForm = " + surfaceForm);

		final double similarity = LevenShteinSimilarity.levenshteinSimilarity(className, surfaceForm, 0);

		// System.out.println("similarity = " + similarity);

		for (Float bin : similarities) {
			featureVector.set("LevenShtein similarity for [" + className + "] < " + DIGIT_FORMAT.format(bin),
					similarity < bin);
			featureVector.set("LevenShtein similarity for [" + className + "] >= " + DIGIT_FORMAT.format(bin),
					similarity >= bin);
		}

	}

}
