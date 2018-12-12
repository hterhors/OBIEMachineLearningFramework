package de.hterhors.obie.ml.templates;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.ner.regex.BasicRegExPattern;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.templates.InterTokenTemplate.Scope;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import factors.Factor;
import factors.FactorScope;
import learning.Vector;

/**
 * @author hterhors
 *
 * @date Nov 15, 2017
 */
public class InterTokenTemplate extends AbstractOBIETemplate<Scope> implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Logger log = LogManager.getFormatterLogger(InterTokenTemplate.class);

	private static final String TOKEN_SPLITTER_SPACE = " ";

	private static final String END_SIGN = "$";

	private static final String START_SIGN = "^";

	private static final int MIN_TOKEN_LENGTH = 2;

	private static final String LEFT = "<";

	private static final String RIGHT = ">";
	/**
	 * Whether distant supervision is enabled for this template or not. This effects
	 * the way of calculating the factors and features!
	 */
	private final boolean enableDistantSupervision;

	private final AbstractOBIETemplate<?> thisTemplate;

	public InterTokenTemplate(RunParameter parameter) {
		super(parameter);
		this.thisTemplate = this;
		this.enableDistantSupervision = parameter.exploreOnOntologyLevel;
	}

	class Scope extends FactorScope {

		public Class<? extends IOBIEThing> classType;
		public final AbstractIndividual individual;
		public final String surfaceForm;
		public OBIEInstance instance;

		public Scope(OBIEInstance internalInstance, Class<? extends IOBIEThing> entityRootClassType,
				Class<? extends IOBIEThing> classType, AbstractIndividual individual, String surfaceForm) {
			super(thisTemplate, internalInstance, entityRootClassType, classType, individual, surfaceForm);
			this.classType = classType;
			this.individual = individual;
			this.surfaceForm = surfaceForm;
			this.instance = internalInstance;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();

		for (TemplateAnnotation entity : state.getCurrentTemplateAnnotations().getTemplateAnnotations()) {
			addFactorRecursive(factors, state.getInstance(), entity.rootClassType, entity.getThing());
		}

		return factors;
	}

	private void addFactorRecursive(List<Scope> factors, OBIEInstance internalInstance,
			Class<? extends IOBIEThing> rootClassType, IOBIEThing obieThing) {

		if (obieThing == null)
			return;

		/*
		 * TODO: include data Type properties?
		 */
		// if
		// (!scioClass.getClass().isAnnotationPresent(DataTypeProperty.class)) {

		factors.add(new Scope(internalInstance, rootClassType, obieThing.getClass(), obieThing.getIndividual(),
				obieThing.getTextMention()));

		/*
		 * Add factors for object type properties.
		 */
		ReflectionUtils.getSlots(obieThing.getClass()).forEach(field -> {
			try {
				if (ReflectionUtils.isAnnotationPresent(field, RelationTypeCollection.class)) {
					for (IOBIEThing element : (List<IOBIEThing>) field.get(obieThing)) {
						addFactorRecursive(factors, internalInstance, rootClassType, element);
					}
				} else {
					addFactorRecursive(factors, internalInstance, rootClassType, (IOBIEThing) field.get(obieThing));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		return;

	}

	/**
	 * Returns the surface forms of the given object. If distant supervision is
	 * enabled all surface forms are returned that belongs to the class type of the
	 * given object.If DV is not enabled the returned set contains only the
	 * annotated surface form of the given object.
	 * 
	 * @param internalInstance
	 * 
	 * @param classType
	 * @return null if there are no annotations for that class
	 */
	private Set<String> getSurfaceForms(OBIEInstance internalInstance, final Class<? extends IOBIEThing> classType,
			AbstractIndividual individual, final String surfaceForm) {

		if (classType == null)
			return null;

		Set<String> surfaceForms = new HashSet<>();

		if (ReflectionUtils.isAnnotationPresent(classType, DatatypeProperty.class)) {

			surfaceForms.add(normalizeSurfaceForm(surfaceForm));

		} else {

			if (enableDistantSupervision) {
				/*
				 * If DSV is enabled add all surface forms of that class / individual.
				 */

				if (internalInstance.getNamedEntityLinkingAnnotations().containsClassAnnotations(classType)) {
					surfaceForms
							.addAll(internalInstance.getNamedEntityLinkingAnnotations().getClassAnnotations(classType)
									.stream().map(nera -> nera.text).collect(Collectors.toList()));
				}

				if (individual != null && internalInstance.getNamedEntityLinkingAnnotations()
						.containsIndividualAnnotations(individual)) {
					surfaceForms.addAll(
							internalInstance.getNamedEntityLinkingAnnotations().getIndividualAnnotations(individual)
									.stream().map(nera -> nera.text).collect(Collectors.toList()));
				}
			} else {
				/*
				 * If DV is not enabled add just the surface form of that individual annotation.
				 */
				surfaceForms.add(surfaceForm);
			}
		}
		return surfaceForms;
	}

	private String normalizeSurfaceForm(String textMention) {
		return textMention.replaceAll("[0-9]", "#").replaceAll("[^\\x20-\\x7E]+", "§");
	}

	@Override
	public void computeFactor(Factor<Scope> factor) {

		Vector featureVector = factor.getFeatureVector();

		final Set<String> surfaceForms = getSurfaceForms(factor.getFactorScope().instance,
				factor.getFactorScope().classType, factor.getFactorScope().individual,
				factor.getFactorScope().surfaceForm);

		for (String surfaceForm : surfaceForms) {
			getTokenNgrams(featureVector, ReflectionUtils.simpleName(factor.getFactorScope().classType), surfaceForm);
		}

		if (factor.getFactorScope().individual != null)
			for (String surfaceForm : surfaceForms) {
				getTokenNgrams(featureVector, factor.getFactorScope().individual.name, surfaceForm);
			}

	}

	private void getTokenNgrams(Vector featureVector, String name, String cleanedMention) {

		final String cM = START_SIGN + TOKEN_SPLITTER_SPACE + cleanedMention + TOKEN_SPLITTER_SPACE + END_SIGN;

		final String[] tokens = cM.split(TOKEN_SPLITTER_SPACE);

		final int maxNgramSize = tokens.length;

		featureVector.set(LEFT + name + RIGHT + TOKEN_SPLITTER_SPACE + cM, true);

		for (int ngram = 1; ngram < maxNgramSize; ngram++) {
			for (int i = 0; i < maxNgramSize - 1; i++) {

				/*
				 * Do not include start symbol.
				 */
				if (i + ngram == 1)
					continue;

				/*
				 * Break if size exceeds token length
				 */
				if (i + ngram > maxNgramSize)
					break;

				StringBuffer fBuffer = new StringBuffer();
				for (int t = i; t < i + ngram; t++) {

					if (tokens[t].isEmpty())
						continue;

					if (BasicRegExPattern.STOP_WORDS.contains(tokens[t].toLowerCase()))
						continue;

					fBuffer.append(tokens[t]).append(TOKEN_SPLITTER_SPACE);

				}

				final String featureName = fBuffer.toString().trim();

				if (featureName.length() < MIN_TOKEN_LENGTH)
					continue;

				if (featureName.isEmpty())
					continue;

				featureVector.set(LEFT + name + RIGHT + TOKEN_SPLITTER_SPACE + featureName, true);

			}
		}

	}

}
