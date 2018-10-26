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
import de.hterhors.obie.ml.ner.NERLClassAnnotation;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.templates.InBetweenContextTemplate.PositionPairContainer;
import de.hterhors.obie.ml.templates.LocalTemplate.Scope;
import de.hterhors.obie.ml.utils.ReflectionUtils;
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

	public LocalTemplate(RunParameter parameter) {
		super(parameter);
		this.enableDistantSupervision = parameter.exploreOnOntologyLevel;
	}

	class Scope extends FactorScope {

		public final OBIEInstance internalInstance;
		public final Class<? extends IOBIEThing> parentClass;
		public final Integer parentCharacterOnset;
		public final Integer parentCharacterOffset;
		public final Class<? extends IOBIEThing> childClass;
		public final Integer childCharacterOnset;
		public final Integer childCharacterOffset;
		public final String childTextMention;

		public Scope(OBIEInstance internalInstance, Class<? extends IOBIEThing> rootClassType,
				Class<? extends IOBIEThing> parentClass, Integer parentCharacterOnset, Integer parentCharacterOffset,
				Class<? extends IOBIEThing> childClass, Integer childCharacterOnset, Integer childCharacterOffset,
				String childTextMention) {
			super(LocalTemplate.this, internalInstance, parentClass, parentCharacterOnset, parentCharacterOffset,
					childClass, childCharacterOnset, childCharacterOffset, childTextMention);
			this.parentClass = parentClass;
			this.parentCharacterOnset = parentCharacterOnset;
			this.parentCharacterOffset = parentCharacterOffset;
			this.childClass = childClass;
			this.childCharacterOnset = childCharacterOnset;
			this.childCharacterOffset = childCharacterOffset;
			this.childTextMention = childTextMention;
			this.internalInstance = internalInstance;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		for (TemplateAnnotation entity : state.getCurrentTemplateAnnotations().getTemplateAnnotations()) {
			addFactorRecursive(factors, state.getInstance(), entity.rootClassType, null, entity.getThing());
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
			factors.add(new Scope(internalInstance, rootClassType, parent.getClass(), parent.getCharacterOnset(),
					parent.getCharacterOffset(), child.getClass(), child.getCharacterOnset(),
					child.getCharacterOffset(), child.getTextMention()));
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

	static class Distance {

		public final String fromName;
		public final String toName;
		public final int distance;

		public Distance(String fromClassNameType, String toClassNameType, int distance) {
			this.fromName = fromClassNameType;
			this.toName = toClassNameType;
			this.distance = distance;
		}

	}

	private List<Distance> computeDistances(OBIEInstance internalInstance, Class<? extends IOBIEThing> parentClass,
			Integer parentCharOnset, Integer parentCharOffset, Class<? extends IOBIEThing> childClass,
			Integer childCharOnset, Integer childCharOffset, final String childSurfaceForm) {

		List<Distance> positionPairs = new ArrayList<>();

		if (enableDistantSupervision) {

			if (internalInstance.getNamedEntityLinkingAnnotations().containsClassAnnotations(childClass)
					&& internalInstance.getNamedEntityLinkingAnnotations().containsClassAnnotations(parentClass)) {

				Set<NERLClassAnnotation> parentNeras = internalInstance.getNamedEntityLinkingAnnotations()
						.getClassAnnotations(parentClass).stream().collect(Collectors.toSet());

				Set<NERLClassAnnotation> childNeras;
				if (ReflectionUtils.isAnnotationPresent(childClass, DatatypeProperty.class)) {
					childNeras = internalInstance.getNamedEntityLinkingAnnotations()
							.getClassAnnotationsByTextMention(childClass, childSurfaceForm);
				} else {
					childNeras = internalInstance.getNamedEntityLinkingAnnotations().getClassAnnotations(childClass)
							.stream().collect(Collectors.toSet());
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
							addPositionPairs(positionPairs, fromPosition, toPosition, classType1, classType2,
									internalInstance);

						}
					}
				} else {
					/*
					 * TODO: What to do here? print warning
					 */
				}

			}

		} else {
			Integer fromPosition = parentCharOffset;
			Integer toPosition = childCharOnset;

			if (fromPosition != null && toPosition != null) {
				Class<? extends IOBIEThing> classType1 = parentClass;
				Class<? extends IOBIEThing> classType2 = childClass;
				/*
				 * Switch "from" and "to" if from is after to position.
				 */
				if (fromPosition > toPosition) {
					fromPosition = childCharOffset;
					toPosition = parentCharOnset;
					classType1 = childClass;
					classType2 = parentClass;
				}
				addPositionPairs(positionPairs, fromPosition, toPosition, classType1, classType2, internalInstance);
			}
		}
		return positionPairs;
	}

	private void addPositionPairs(List<Distance> positionPairs, Integer fromPosition, Integer toPosition,
			Class<? extends IOBIEThing> classType1, Class<? extends IOBIEThing> classType2,
			OBIEInstance internalInstance) {
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

			positionPairs.add(new Distance(ReflectionUtils.simpleName(classType1),
					ReflectionUtils.simpleName(classType2), distance));

			for (Class<? extends IOBIEThing> rootClassType1 : ReflectionUtils.getSuperRootClasses(classType1)) {
				if (!ReflectionUtils.isAnnotationPresent(rootClassType1, DatatypeProperty.class))

					for (Class<? extends IOBIEThing> rootClassType2 : ReflectionUtils.getSuperRootClasses(classType2)) {

						if (!ReflectionUtils.isAnnotationPresent(rootClassType2, DatatypeProperty.class))

							/*
							 * Add features for root classes of annotations.
							 */

							positionPairs.add(new Distance(ReflectionUtils.simpleName(rootClassType1),
									ReflectionUtils.simpleName(rootClassType2), distance));

					}
			}

		}

	}

	@Override
	public void computeFactor(Factor<Scope> factor) {

		Vector featureVector = factor.getFeatureVector();

		final List<Distance> positionPairs = computeDistances(factor.getFactorScope().internalInstance,
				factor.getFactorScope().parentClass, factor.getFactorScope().parentCharacterOnset,
				factor.getFactorScope().parentCharacterOffset, factor.getFactorScope().childClass,
				factor.getFactorScope().childCharacterOnset, factor.getFactorScope().childCharacterOffset,
				factor.getFactorScope().childTextMention);

		for (Distance positionPair : positionPairs) {

			final String fromName = positionPair.fromName;

			final String toName = positionPair.toName;

			final int sentenceDistance = positionPair.distance;
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

}