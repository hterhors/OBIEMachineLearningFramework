package de.hterhors.obie.ml.templates;

import java.util.ArrayList;
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
import de.hterhors.obie.ml.ner.NERLClassAnnotation;
import de.hterhors.obie.ml.ner.NERLIndividualAnnotation;
import de.hterhors.obie.ml.run.AbstractRunner;
import de.hterhors.obie.ml.templates.InBetweenContextTemplate.PositionPairContainer;
import de.hterhors.obie.ml.templates.LocalTemplate.Scope;
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

	public LocalTemplate(AbstractRunner runner) {
		super(runner);
		this.enableDistantSupervision = runner.getParameter().exploreOnOntologyLevel;
	}

	class Scope extends FactorScope {

		public final OBIEInstance internalInstance;
		public final Class<? extends IOBIEThing> parentClass;
		public final AbstractIndividual parentIndividual;
		public final Integer parentCharacterOnset;
		public final Integer parentCharacterOffset;
		public final Class<? extends IOBIEThing> childClass;
		public final AbstractIndividual childIndividual;
		public final Integer childCharacterOnset;
		public final Integer childCharacterOffset;
		public final String childTextMention;

		public Scope(OBIEInstance internalInstance, Class<? extends IOBIEThing> rootClassType,
				Class<? extends IOBIEThing> parentClass, AbstractIndividual parentIndividual,
				Integer parentCharacterOnset, Integer parentCharacterOffset, Class<? extends IOBIEThing> childClass,
				AbstractIndividual childIndividual, Integer childCharacterOnset, Integer childCharacterOffset,
				String childTextMention) {
			super(LocalTemplate.this, internalInstance, rootClassType, parentClass, parentIndividual,
					parentCharacterOnset, parentCharacterOffset, childClass, childIndividual, childCharacterOnset,
					childCharacterOffset, childTextMention);
			this.parentClass = parentClass;
			this.parentCharacterOnset = parentCharacterOnset;
			this.parentCharacterOffset = parentCharacterOffset;
			this.parentIndividual = parentIndividual;
			this.childClass = childClass;
			this.childIndividual = childIndividual;
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
			callRecursive(factors, state.getInstance(), entity.rootClassType, entity.getThing());
		}

		return factors;
	}

	private void callRecursive(final List<Scope> factors, OBIEInstance internalInstance,
			Class<? extends IOBIEThing> rootClassType, final IOBIEThing parent) {

		if (parent == null)
			return;

		/*
		 * Add factor parent - child relation
		 */
		ReflectionUtils.getSlots(parent.getClass(),parent.getInvestigationRestriction()).forEach(field -> {
			try {
				if (ReflectionUtils.isAnnotationPresent(field, RelationTypeCollection.class)) {
					for (IOBIEThing listObject : (List<IOBIEThing>) field.get(parent)) {

						addFactor(factors, internalInstance, rootClassType, parent, listObject);

						callRecursive(factors, internalInstance, rootClassType, listObject);
					}
				} else {

					IOBIEThing child = (IOBIEThing) field.get(parent);

					addFactor(factors, internalInstance, rootClassType, parent, child);

					callRecursive(factors, internalInstance, rootClassType, child);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

	}

	private void addFactor(final List<Scope> factors, OBIEInstance internalInstance,
			Class<? extends IOBIEThing> rootClassType, final IOBIEThing obieThing, IOBIEThing slotValue) {

		if (slotValue == null)
			return;

		if (enableDistantSupervision) {

			factors.add(new Scope(internalInstance, rootClassType, obieThing.getClass(), obieThing.getIndividual(),
					null, null, slotValue.getClass(), slotValue.getIndividual(), null, null,
					slotValue.getTextMention()));
		} else {

			factors.add(new Scope(internalInstance, rootClassType, obieThing.getClass(), obieThing.getIndividual(),
					obieThing.getCharacterOnset(), obieThing.getCharacterOffset(), slotValue.getClass(),
					slotValue.getIndividual(), slotValue.getCharacterOnset(), slotValue.getCharacterOffset(),
					slotValue.getTextMention()));
		}
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
			AbstractIndividual parentIndividual, Integer parentCharOnset, Integer parentCharOffset,
			Class<? extends IOBIEThing> childClass, AbstractIndividual childIndividual, Integer childCharOnset,
			Integer childCharOffset, final String childSurfaceForm) {
		List<Distance> positionsPairs = new ArrayList<>();

		if (enableDistantSupervision) {

			if (internalInstance.getNamedEntityLinkingAnnotations().containsIndividualAnnotations(parentIndividual)) {

				Set<NERLIndividualAnnotation> parentNerls = internalInstance.getNamedEntityLinkingAnnotations()
						.getIndividualAnnotations(parentIndividual);

				if (ReflectionUtils.isAnnotationPresent(childClass, DatatypeProperty.class)) {

					Set<NERLClassAnnotation> childNerls = internalInstance.getNamedEntityLinkingAnnotations()
							.getClassAnnotationsByTextMention(childClass, childSurfaceForm);
					if (childNerls != null) {

						for (NERLIndividualAnnotation parentNerl : parentNerls) {
							for (NERLClassAnnotation childNerl : childNerls) {

								Integer fromPosition = Integer.valueOf(parentNerl.onset + parentNerl.text.length());
								Class<? extends IOBIEThing> classType1 = parentClass;
								Integer toPosition = Integer.valueOf(childNerl.onset);
								Class<? extends IOBIEThing> classType2 = childNerl.classType;
								/*
								 * Switch "from" and "to" if from is after to position.
								 */
								if (fromPosition > toPosition) {
									fromPosition = Integer.valueOf(childNerl.onset + childNerl.text.length());
									classType1 = childNerl.classType;

									toPosition = Integer.valueOf(parentNerl.onset);
									classType2 = parentClass;
								}
								addDistances(positionsPairs, fromPosition, toPosition, classType1, classType2,
										internalInstance);

							}
						}
					} else {
						/*
						 * TODO: no child nerls found. do nothing? This happens if the ner for data type
						 * properties failed. e.g. the string we search is fifteen to 25 ml. This can
						 * not be parsed by the semantic interpretation. Thus the child nerls are empty.
						 */
					}

				} else {

					Set<NERLIndividualAnnotation> childNerls = internalInstance.getNamedEntityLinkingAnnotations()
							.getIndividualAnnotations(childIndividual);

					if (childNerls != null) {

						Class<? extends IOBIEThing> classType1 = parentClass;
						Class<? extends IOBIEThing> classType2 = childClass;

						for (NERLIndividualAnnotation parentNerl : parentNerls) {
							for (NERLIndividualAnnotation childNerl : childNerls) {

								Integer fromPosition = Integer.valueOf(parentNerl.onset + parentNerl.text.length());
								Integer toPosition = Integer.valueOf(childNerl.onset);
								/*
								 * Switch "from" and "to" if from is after to position.
								 */
								if (fromPosition > toPosition) {
									fromPosition = Integer.valueOf(childNerl.onset + childNerl.text.length());
									classType1 = childClass;

									toPosition = Integer.valueOf(parentNerl.onset);
									classType2 = parentClass;
								}
								addDistances(positionsPairs, fromPosition, toPosition, classType1, classType2,
										internalInstance);

							}
						}
					} else {
						/*
						 * TODO: no child nerls found. do nothing? This happens if the ner for data type
						 * properties failed. e.g. the string we search is fifteen to 25 ml. This can
						 * not be parsed by the semantic interpretation. Thus the child nerls are empty.
						 */
					}
				}
			} else if (parentIndividual == null) {

				/**
				 * In case the parent class has no individual take class annotations.
				 */

				Set<NERLClassAnnotation> parentNerls = internalInstance.getNamedEntityLinkingAnnotations()
						.getClassAnnotations(parentClass);

				if (parentNerls == null)
					return positionsPairs;

				if (ReflectionUtils.isAnnotationPresent(childClass, DatatypeProperty.class)) {

					Set<NERLClassAnnotation> childNerls = internalInstance.getNamedEntityLinkingAnnotations()
							.getClassAnnotationsByTextMention(childClass, childSurfaceForm);
					if (childNerls != null) {

						for (NERLClassAnnotation parentNerl : parentNerls) {
							for (NERLClassAnnotation childNerl : childNerls) {

								Integer fromPosition = Integer.valueOf(parentNerl.onset + parentNerl.text.length());
								Class<? extends IOBIEThing> classType1 = parentClass;
								Integer toPosition = Integer.valueOf(childNerl.onset);
								Class<? extends IOBIEThing> classType2 = childNerl.classType;
								/*
								 * Switch "from" and "to" if from is after to position.
								 */
								if (fromPosition > toPosition) {
									fromPosition = Integer.valueOf(childNerl.onset + childNerl.text.length());
									classType1 = childNerl.classType;

									toPosition = Integer.valueOf(parentNerl.onset);
									classType2 = parentClass;
								}
								addDistances(positionsPairs, fromPosition, toPosition, classType1, classType2,
										internalInstance);

							}
						}
					} else {
						/*
						 * TODO: no child nerls found. do nothing? This happens if the ner for data type
						 * properties failed. e.g. the string we search is fifteen to 25 ml. This can
						 * not be parsed by the semantic interpretation. Thus the child nerls are empty.
						 */
					}

				} else {

					if (childIndividual == null) {

						Set<NERLClassAnnotation> childNerls = internalInstance.getNamedEntityLinkingAnnotations()
								.getClassAnnotations(childClass);

						if (childNerls != null) {

							Class<? extends IOBIEThing> classType1 = parentClass;
							Class<? extends IOBIEThing> classType2 = childClass;

							for (NERLClassAnnotation parentNerl : parentNerls) {
								for (NERLClassAnnotation childNerl : childNerls) {

									Integer fromPosition = Integer.valueOf(parentNerl.onset + parentNerl.text.length());
									Integer toPosition = Integer.valueOf(childNerl.onset);
									/*
									 * Switch "from" and "to" if from is after to position.
									 */
									if (fromPosition > toPosition) {
										fromPosition = Integer.valueOf(childNerl.onset + childNerl.text.length());
										classType1 = childClass;

										toPosition = Integer.valueOf(parentNerl.onset);
										classType2 = parentClass;
									}
									addDistances(positionsPairs, fromPosition, toPosition, classType1, classType2,
											internalInstance);

								}
							}
						} else {
							/*
							 * TODO: no child nerls found. do nothing? This happens if the ner for data type
							 * properties failed. e.g. the string we search is fifteen to 25 ml. This can
							 * not be parsed by the semantic interpretation. Thus the child nerls are empty.
							 */
						}

					} else {

						Set<NERLIndividualAnnotation> childNerls = internalInstance.getNamedEntityLinkingAnnotations()
								.getIndividualAnnotations(childIndividual);

						if (childNerls != null) {

							Class<? extends IOBIEThing> classType1 = parentClass;
							Class<? extends IOBIEThing> classType2 = childClass;

							for (NERLClassAnnotation parentNerl : parentNerls) {
								for (NERLIndividualAnnotation childNerl : childNerls) {

									Integer fromPosition = Integer.valueOf(parentNerl.onset + parentNerl.text.length());
									Integer toPosition = Integer.valueOf(childNerl.onset);
									/*
									 * Switch "from" and "to" if from is after to position.
									 */
									if (fromPosition > toPosition) {
										fromPosition = Integer.valueOf(childNerl.onset + childNerl.text.length());
										classType1 = childClass;

										toPosition = Integer.valueOf(parentNerl.onset);
										classType2 = parentClass;
									}
									addDistances(positionsPairs, fromPosition, toPosition, classType1, classType2,
											internalInstance);

								}
							}
						} else {
							/*
							 * TODO: no child nerls found. do nothing? This happens if the ner for data type
							 * properties failed. e.g. the string we search is fifteen to 25 ml. This can
							 * not be parsed by the semantic interpretation. Thus the child nerls are empty.
							 */
						}
					}
				}
			}
		} else {
			Integer fromPosition = parentCharOffset;
			Integer toPosition = childCharOnset;

			if (fromPosition != null && toPosition != null) {
				Class<? extends IOBIEThing> fromClassType = parentClass;
				Class<? extends IOBIEThing> toClassType = childClass;
				/*
				 * Switch "from" and "to" if from is after to position.
				 */
				if (fromPosition > toPosition) {
					fromPosition = childCharOffset;
					toPosition = parentCharOnset;
					fromClassType = childClass;
					toClassType = parentClass;
				}
				addDistances(positionsPairs, fromPosition, toPosition, fromClassType, toClassType, internalInstance);
			}
		}

		return positionsPairs;
//		List<Distance> positionsPairs = new ArrayList<>();
//
//		if (enableDistantSupervision) {
//
//			if (!internalInstance.getNamedEntityLinkingAnnotations().containsIndividualAnnotations(parentIndividual))
//				return positionsPairs;
//
//			Set<NERLIndividualAnnotation> parentNerls = internalInstance.getNamedEntityLinkingAnnotations()
//					.getIndividualAnnotations(parentIndividual).stream().collect(Collectors.toSet());
//
//			if (ReflectionUtils.isAnnotationPresent(childClass, DatatypeProperty.class)) {
//
//				Set<NERLClassAnnotation> childNerls = internalInstance.getNamedEntityLinkingAnnotations()
//						.getClassAnnotationsByTextMention(childClass, childSurfaceForm);
//				if (childNerls != null) {
//
//					for (NERLIndividualAnnotation parentNerl : parentNerls) {
//						for (NERLClassAnnotation childNerl : childNerls) {
//
//							Integer fromPosition = Integer.valueOf(parentNerl.onset + parentNerl.text.length());
//							Class<? extends IOBIEThing> classType1 = parentClass;
//							Integer toPosition = Integer.valueOf(childNerl.onset);
//							Class<? extends IOBIEThing> classType2 = childNerl.classType;
//							/*
//							 * Switch "from" and "to" if from is after to position.
//							 */
//							if (fromPosition > toPosition) {
//								fromPosition = Integer.valueOf(childNerl.onset + childNerl.text.length());
//								classType1 = childNerl.classType;
//
//								toPosition = Integer.valueOf(parentNerl.onset);
//								classType2 = parentClass;
//							}
//							addDistances(positionsPairs, fromPosition, toPosition, classType1, classType2,
//									internalInstance);
//
//						}
//					}
//				} else {
//					/*
//					 * TODO: no child nerls found. do nothing? This happens if the ner for data type
//					 * properties failed. e.g. the string we search is fifteen to 25 ml. This can
//					 * not be parsed by the semantic interpretation. Thus the child nerls are empty.
//					 */
//				}
//
//			} else {
//
//				Set<NERLIndividualAnnotation> childNerls = internalInstance.getNamedEntityLinkingAnnotations()
//						.getIndividualAnnotations(childIndividual).stream().collect(Collectors.toSet());
//				if (childNerls != null) {
//
//					Class<? extends IOBIEThing> toClassType = parentClass;
//					Class<? extends IOBIEThing> classType2 = childClass;
//
//					for (NERLIndividualAnnotation parentNerl : parentNerls) {
//						for (NERLIndividualAnnotation childNerl : childNerls) {
//
//							Integer fromPosition = Integer.valueOf(parentNerl.onset + parentNerl.text.length());
//							Integer toPosition = Integer.valueOf(childNerl.onset);
//							/*
//							 * Switch "from" and "to" if from is after to position.
//							 */
//							if (fromPosition > toPosition) {
//								fromPosition = Integer.valueOf(childNerl.onset + childNerl.text.length());
//								toClassType = childClass;
//
//								toPosition = Integer.valueOf(parentNerl.onset);
//								classType2 = parentClass;
//							}
//							addDistances(positionsPairs, fromPosition, toPosition, toClassType, classType2,
//									internalInstance);
//
//						}
//					}
//				} else {
//					/*
//					 * TODO: no child nerls found. do nothing? This happens if the ner for data type
//					 * properties failed. e.g. the string we search is fifteen to 25 ml. This can
//					 * not be parsed by the semantic interpretation. Thus the child nerls are empty.
//					 */
//				}
//
//			}
//
//		} else {
//			Integer fromPosition = parentCharOffset;
//			Integer toPosition = childCharOnset;
//
//			if (fromPosition != null && toPosition != null) {
//				Class<? extends IOBIEThing> fromClassType = parentClass;
//				Class<? extends IOBIEThing> toClassType = childClass;
//				/*
//				 * Switch "from" and "to" if from is after to position.
//				 */
//				if (fromPosition > toPosition) {
//					fromPosition = childCharOffset;
//					toPosition = parentCharOnset;
//					fromClassType = childClass;
//					toClassType = parentClass;
//				}
//				addDistances(positionsPairs, fromPosition, toPosition, fromClassType, toClassType, internalInstance);
//			}
//		}
//		return positionsPairs;
	}

	private void addDistances(List<Distance> positionPairs, Integer fromPosition, Integer toPosition,
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
				factor.getFactorScope().parentClass, factor.getFactorScope().parentIndividual,
				factor.getFactorScope().parentCharacterOnset, factor.getFactorScope().parentCharacterOffset,
				factor.getFactorScope().childClass, factor.getFactorScope().childIndividual,
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
			featureVector.set(fromName + "->" + toName + " sentence dist >= 4" , sentenceDistance>=4);

		}
	}

}