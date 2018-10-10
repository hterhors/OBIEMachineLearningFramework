package de.hterhors.obie.ml.templates;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.run.param.OBIERunParameter;
import de.hterhors.obie.ml.templates.LocalTemplate.Scope;
import de.hterhors.obie.ml.utils.ReflectionUtils;
import de.hterhors.obie.ml.variables.NERLClassAnnotation;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import factors.Factor;
import factors.FactorScope;
import learning.Vector;

/**
 * This template creates feature in form of an in between context. Each feature
 * contains the parent class annotations and its property slot annotation and
 * the text in between. Further we capture the
 * 
 * @author hterhors
 *
 * @date Jan 15, 2018
 */
public class LocalTemplate extends AbstractOBIETemplate<Scope> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final int MAX_TOKENS_DISTANCE = 100;

	private static Logger log = LogManager.getFormatterLogger(LocalTemplate.class.getName());

	/**
	 * Whether distant supervision is enabled for this template or not. This effects
	 * the way of calculating the factors and features!
	 */
	private final boolean enableDistantSupervision;

	public LocalTemplate(OBIERunParameter parameter) {
		super(parameter);
		this.enableDistantSupervision = parameter.exploreOnOntologyLevel;
	}

	class Scope extends FactorScope {

		final OBIEInstance instance;
		final String fromClassName;
		final String toClassName;
		final int sentenceDistance;

		public Scope(final String fromClassName, final String toClassName, int sentenceDistance,
				OBIEInstance internalInstance, Class<? extends IOBIEThing> entityRootClassType) {
			super(LocalTemplate.this, internalInstance, fromClassName, toClassName, sentenceDistance,
					entityRootClassType);
			this.instance = internalInstance;
			this.fromClassName = fromClassName;
			this.toClassName = toClassName;
			this.sentenceDistance = sentenceDistance;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		for (TemplateAnnotation entity : state.getCurrentPrediction().getTemplateAnnotations()) {
			addFactorRecursive(factors, state.getInstance(), entity.rootClassType, null,
					entity.getTemplateAnnotation());
		}

		return factors;
	}

	private void addFactorRecursive(final List<Scope> factors, OBIEInstance internalInstance,
			Class<? extends IOBIEThing> rootClassType, final IOBIEThing parent, IOBIEThing child) {

		if (child == null)
			return;
		/*
		 * Add factor parent - child relation
		 */
		if (parent != null) {

			if (enableDistantSupervision) {

				if (internalInstance.getNamedEntityLinkingAnnotations().containsClassAnnotations(child.getClass())
						&& internalInstance.getNamedEntityLinkingAnnotations()
								.containsClassAnnotations(parent.getClass())) {
					Set<NERLClassAnnotation> parentNeras = internalInstance.getNamedEntityLinkingAnnotations()
							.getClassAnnotations(parent.getClass()).stream().collect(Collectors.toSet());

					Set<NERLClassAnnotation> childNeras;
					if (ReflectionUtils.isAnnotationPresent(child.getClass(), DatatypeProperty.class)) {
						childNeras = internalInstance.getNamedEntityLinkingAnnotations()
								.getClassAnnotationsByTextMention(child.getClass(), child.getTextMention());
					} else {
						childNeras = internalInstance.getNamedEntityLinkingAnnotations()
								.getClassAnnotations(child.getClass()).stream().collect(Collectors.toSet());
					}
					if (childNeras != null) {
						for (NERLClassAnnotation parentNera : parentNeras) {
							for (NERLClassAnnotation childNera : childNeras) {

								Integer fromPosition = Integer.valueOf(parentNera.onset + parentNera.text.length());
								Class<? extends IOBIEThing> classType1 = parentNera.classType;
								Integer toPosition = Integer.valueOf(childNera.onset);
								Class<? extends IOBIEThing> classType2 = childNera.classType;
								/*
								 * Switch "from" and "to" if from is after to position.
								 */
								if (fromPosition > toPosition) {
									fromPosition = Integer.valueOf(childNera.onset + childNera.text.length());
									classType1 = childNera.classType;

									toPosition = Integer.valueOf(parentNera.onset);
									classType2 = parentNera.classType;
								}
								addFactor(factors, fromPosition, toPosition, classType1, classType2, internalInstance,
										rootClassType);

							}
						}
					} else {
						/*
						 * TODO: What to do here? print warning that
						 */
					}

				}

			} else {
				Integer fromPosition = parent.getCharacterOffset();
				Integer toPosition = child.getCharacterOnset();

				if (fromPosition != null && toPosition != null) {
					Class<? extends IOBIEThing> classType1 = parent.getClass();
					Class<? extends IOBIEThing> classType2 = child.getClass();
					/*
					 * Switch "from" and "to" if from is after to position.
					 */
					if (fromPosition > toPosition) {
						fromPosition = child.getCharacterOffset();
						toPosition = parent.getCharacterOnset();
						classType1 = child.getClass();
						classType2 = parent.getClass();
					}
					addFactor(factors, fromPosition, toPosition, classType1, classType2, internalInstance,
							rootClassType);
				}
			}
		}
		ReflectionUtils.getAccessibleOntologyFields(child.getClass()).forEach(field -> {
			try {
				if (field.isAnnotationPresent(RelationTypeCollection.class)) {
					for (IOBIEThing listObject : (List<IOBIEThing>) field.get(child)) {
						addFactorRecursive(factors, internalInstance, rootClassType, child, listObject);
					}
				} else {
					addFactorRecursive(factors, internalInstance, rootClassType, child, (IOBIEThing) field.get(child));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

	}

	private void addFactor(final List<Scope> factors, Integer fromPosition, Integer toPosition,
			Class<? extends IOBIEThing> classType1, Class<? extends IOBIEThing> classType2,
			OBIEInstance internalInstance, Class<? extends IOBIEThing> rootClassType) {
		/*
		 * Inclusive
		 */
		int fromTokenIndex = internalInstance.charPositionToTokenPosition(fromPosition);
		/*
		 * Exclusive
		 */
		int toTokenIndex = internalInstance.charPositionToTokenPosition(toPosition);

		if (toTokenIndex - fromTokenIndex <= MAX_TOKENS_DISTANCE && toTokenIndex - fromTokenIndex >= 0) {
			final int class1SentenceIndex = internalInstance.charPositionToToken(fromPosition).getSentenceIndex();
			final int class2SentenceIndex = internalInstance.charPositionToToken(toPosition).getSentenceIndex();
			final int distance = Math.abs(class1SentenceIndex - class2SentenceIndex);

			factors.add(new Scope(classType1.getSimpleName(), classType2.getSimpleName(), distance, internalInstance,
					rootClassType));

			for (Class<? extends IOBIEThing> rootClassType1 : ReflectionUtils.getSuperRootClasses(classType1)) {
				if (!ReflectionUtils.isAnnotationPresent(rootClassType1, DatatypeProperty.class))

					for (Class<? extends IOBIEThing> rootClassType2 : ReflectionUtils.getSuperRootClasses(classType2)) {

						if (!ReflectionUtils.isAnnotationPresent(rootClassType2, DatatypeProperty.class))

							/*
							 * Add features for root classes of annotations.
							 */

							factors.add(new Scope(rootClassType1.getSimpleName(), rootClassType2.getSimpleName(),
									distance, internalInstance, rootClassType));

					}
			}

		}

	}

	@Override
	public void computeFactor(Factor<Scope> factor) {

		Vector featureVector = factor.getFeatureVector();

		final String fromName = factor.getFactorScope().fromClassName;

		final String toName = factor.getFactorScope().toClassName;

		final int sentenceDistance = factor.getFactorScope().sentenceDistance;
		// for (int localityDist = 0; localityDist < sentenceDistance;
		// localityDist++) {
		//
		// featureVector.set(fromName + "->" + toName + " sentence dist < "
		// + localityDist,
		// localityDist < sentenceDistance);
		// featureVector.set(class1Name + "->" + class2Name + " sentence
		// dist > " + localityDist,
		// sentenceDistance > localityDist);
		//
		// }
		featureVector.set(fromName + "->" + toName + " sentence dist = " + sentenceDistance, true);

	}

}