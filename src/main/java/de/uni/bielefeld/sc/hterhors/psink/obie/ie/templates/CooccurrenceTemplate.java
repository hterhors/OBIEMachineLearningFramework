package de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.OntologyModelContent;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IDataType;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.CooccurrenceTemplate.Scope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.scope.OBIEFactorScope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.TemplateAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;
import factors.Factor;
import learning.Vector;

/**
 * This template measures the co-occurrence between the surface form and the
 * annotated class of each individual annotation as well as co-occurrences
 * between classes within the same obie-template for parent-child and
 * child-child relations.
 * 
 * Each co-occurrence is enriched with the property-chain information. The
 * property chain describes the way through the complex structure of the
 * obie-template.
 * 
 * In case of distant supervision is enabled, we add all surface forms of that
 * type of class with the annotated class at once.
 * 
 * @author hterhors
 *
 * @date Jan 11, 2018
 * 
 *       Probably a bad template!
 * 
 */
@Deprecated
public class CooccurrenceTemplate extends AbstractOBIETemplate<Scope> {

	public CooccurrenceTemplate(OBIERunParameter parameter) {
		super(parameter);
		this.enableDistantSupervision = parameter.exploreOnOntologyLevel;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static Logger log = LogManager.getFormatterLogger(CooccurrenceTemplate.class.getName());

	/**
	 * Whether distant supervision is enabled for this template or not. This effects
	 * the way of calculating the factors and features!
	 */
	private final boolean enableDistantSupervision;

	/**
	 * The current entity root class type of the investigated annotation.
	 */
	private Class<? extends IOBIEThing> entityRootClassType;

	/**
	 * The current internal instance object where the current annotation comes from.
	 */
	private OBIEInstance internalInstance;

	class Scope extends OBIEFactorScope {

		/**
		 * The parent class type of the obie-template in a parent-child relation.
		 * Otherwise the first child of the pair.
		 */
		final Class<? extends IOBIEThing> classType1;

		/**
		 * The class type of the investigated child-property in a parent-child relation.
		 * Otherwise the second child of the pair.
		 */
		final Class<? extends IOBIEThing> classType2;

		/**
		 * The surface forms of the child-property. If distant supervision is enabled
		 * this set contains surface forms of all annotations that have the class type
		 * of the child-property. Else the set contains just a single surface form of
		 * that respective annotation.
		 */
		final Set<String> type1SurfaceForms;

		/**
		 * The surface forms of the parent. If distant supervision is enabled this set
		 * contains surface forms of all annotations that have the class type of the
		 * child-property. Else the set contains just a single surface form of that
		 * respective annotation.
		 */
		final Set<String> type2SurfaceForms;

		/**
		 * This describes the property chain which connects the parent class with the
		 * investigated child property. If it is not an parent-child relation this is
		 * null.
		 */
		final String propertyNameChain;

		public Scope(Set<Class<? extends IOBIEThing>> influencedVariable, Class<? extends IOBIEThing> classType1,
				Class<? extends IOBIEThing> classType2, Set<String> surfaceFormsType1, Set<String> surfaceFormsType2,
				String propertyNameChain) {
			super(influencedVariable, entityRootClassType, CooccurrenceTemplate.this, classType1, classType2,
					surfaceFormsType1, surfaceFormsType2, propertyNameChain, entityRootClassType);
			this.classType1 = classType1;
			this.classType2 = classType2;
			this.type1SurfaceForms = surfaceFormsType1;
			this.type2SurfaceForms = surfaceFormsType2;
			this.propertyNameChain = propertyNameChain;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		internalInstance = state.getInstance();
		for (TemplateAnnotation entity : state.getCurrentPrediction().getTemplateAnnotations()) {
			entityRootClassType = entity.rootClassType;
			addFactorRecursive(factors, entity.get());
		}

		return factors;
	}

	private List<Scope> addFactorRecursive(List<Scope> factors, IOBIEThing obieClass) {
		return addFactorRecursive(factors, null, obieClass, "");
	}

	/**
	 * 
	 * childClass might be null e.g. if the field is not set in the parent. The type
	 * of the child Class is NOT null as we then pass the fields type parameter, to
	 * allow null-capturing.
	 * 
	 * 
	 * @param parentClass       the parent can only be null in the initial call.
	 *                          since the root class does not have a parent.
	 * @param childClass
	 * @param propertyNameChain
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private List<Scope> addFactorRecursive(List<Scope> factors, final IOBIEThing parentClass,
			final IOBIEThing childClass, final String propertyNameChain) {

		Set<String> parentSurfaceForms = getSurfaceForms(parentClass);
		Set<String> childSurfaceForms = getSurfaceForms(childClass);

		final Class<? extends IOBIEThing> childClassType = childClass == null ? null : childClass.getClass();
		final Set<Class<? extends IOBIEThing>> influencedVariables = new HashSet<>();
		/*
		 * Add factor for parent-child relation.
		 */
		if (parentClass == null) {
			// influencedVariables.add(childClassType);
			factors.add(new Scope(influencedVariables, null, childClassType, null, childSurfaceForms, null));
		} else {
			// influencedVariables.add(parentClass.getClass());
			// influencedVariables.add(childClassType);
			factors.add(new Scope(influencedVariables, parentClass.getClass(), childClassType, parentSurfaceForms,
					childSurfaceForms, propertyNameChain));
		}

		if (childClass == null)
			return factors;

		/*
		 * TODO: inefficient as surface forms are multiple times collected.
		 * 
		 * Child-Child relation. if the child property is of type OneToMany we create
		 * pairwise co-occurrences between all elements.
		 */
		for (int i = 0; i < childClass.getClass().getDeclaredFields().length; i++) {
			final Field f1 = childClass.getClass().getDeclaredFields()[i];

			if ((f1.isAnnotationPresent(OntologyModelContent.class))) {
				f1.setAccessible(true);

				final List<IOBIEThing> child1Fillers = getFillers(childClass, f1);

				for (int k = 0; k < child1Fillers.size(); k++) {
					final Set<String> child1SurfaceForms;
					/*
					 * The class type is either the type of the object in the field if any. Else the
					 * type of the field.
					 */
					final Class<? extends IOBIEThing> child1ClassType;

					if (child1Fillers.get(k) == null) {
						child1SurfaceForms = null;
						child1ClassType = null;
					} else {
						child1SurfaceForms = getSurfaceForms(child1Fillers.get(k));
						child1ClassType = (Class<IOBIEThing>) child1Fillers.get(k).getClass();
					}

					for (int j = i + 1; j < childClass.getClass().getDeclaredFields().length; j++) {
						final Field f2 = childClass.getClass().getDeclaredFields()[j];

						if ((f2.isAnnotationPresent(OntologyModelContent.class))) {
							f2.setAccessible(true);

							final List<IOBIEThing> child2Fillers = getFillers(childClass, f2);

							for (int l = 0; l < child2Fillers.size(); l++) {
								final Set<String> child2SurfaceForms;

								/*
								 * The class type is either the type of the object in the field if any. Else the
								 * type of the field.
								 */
								final Class<? extends IOBIEThing> child2ClassType;

								if (child2Fillers.get(k) == null) {
									child2SurfaceForms = null;
									child2ClassType = null;
								} else {
									child2SurfaceForms = getSurfaceForms(child2Fillers.get(l));
									child2ClassType = (Class<IOBIEThing>) child2Fillers.get(l).getClass();
								}

								final Set<Class<? extends IOBIEThing>> iV = new HashSet<>();
								// iV.add(child1ClassType);
								// iV.add(child2ClassType);
								/*
								 * Add factor for parent-child relation.
								 */
								factors.add(new Scope(iV, child1ClassType, child2ClassType, child1SurfaceForms,
										child2SurfaceForms,
										propertyNameChain + "->" + "{" + f1.getName() + ", " + f2.getName() + "}"));

							}
						}

					}
				}
			}
		}

		/*
		 * Parent-Child relation
		 */
		Arrays.stream(childClass.getClass().getDeclaredFields())
				.filter(f -> f.isAnnotationPresent(OntologyModelContent.class)).forEach(field -> {
					field.setAccessible(true);
					try {
						if (field.isAnnotationPresent(RelationTypeCollection.class)) {
							for (IOBIEThing listObject : (List<IOBIEThing>) field.get(childClass)) {
								factors.addAll(addFactorRecursive(factors, childClass, listObject,
										propertyNameChain + "->" + field.getName()));
							}
						} else {
							factors.addAll(addFactorRecursive(factors, childClass, (IOBIEThing) field.get(childClass),
									propertyNameChain + "->" + field.getName()));
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				});

		return factors;
	}

	/**
	 * Returns the surface forms of the given object. If distant supervision is
	 * enabled all surface forms are returned that belongs to the class type of the
	 * given object.If DV is not enabled the returned set contains only the
	 * annotated surface form of the given object.
	 * 
	 * @param filler
	 * @return null if there are no annotations for that class
	 */
	private Set<String> getSurfaceForms(final IOBIEThing filler) {

		if (filler == null)
			return null;

		final Set<String> surfaceForms;
		if (enableDistantSupervision && !filler.getClass().isAnnotationPresent(DatatypeProperty.class)) {
			/*
			 * If DV is enabled add all surface forms of that class.
			 */
			if (internalInstance.getNamedEntityLinkingAnnotations().containsClassAnnotations(filler.getClass())) {
				surfaceForms = internalInstance.getNamedEntityLinkingAnnotations().getClassAnnotations(filler.getClass())
						.stream().map(nera -> nera.getDTValueIfAnyElseTextMention()).collect(Collectors.toSet());
			} else {
				return null;
			}
		} else {
			/*
			 * If DV is not enabled add just the surface form of that individual annotation.
			 */
			surfaceForms = new HashSet<>();
			if (filler.getClass().isAnnotationPresent(DatatypeProperty.class)) {
				surfaceForms.add(((IDataType) filler).getSemanticValue());
			} else {
				surfaceForms.add(filler.getTextMention());
			}

		}
		return surfaceForms;
	}

	/**
	 * Returns the values of the field of the given object. If the field is not of
	 * type list, a list with one element is created. The returned list can include
	 * null objects.
	 * 
	 * @param childClass
	 * @param field
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private List<IOBIEThing> getFillers(IOBIEThing childClass, final Field field) {
		final List<IOBIEThing> fillers;

		try {
			if (field.isAnnotationPresent(RelationTypeCollection.class)) {
				fillers = (List<IOBIEThing>) field.get(childClass);
			} else {
				fillers = new ArrayList<>();
				fillers.add((IOBIEThing) field.get(childClass));
			}
			return fillers;
		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}
	}

	/**
	 * Returns the type of the field. If the field is of type list, the generic type
	 * is returned.
	 * 
	 * @param field
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Class<? extends IOBIEThing> getFieldType(final Field field) {
		try {
			if (field.isAnnotationPresent(RelationTypeCollection.class)) {
				return (Class<? extends IOBIEThing>) ((ParameterizedType) field.getGenericType())
						.getActualTypeArguments()[0];
			} else {
				return (Class<? extends IOBIEThing>) field.getType();
			}
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}
	}

	@Override
	public void computeFactor(Factor<Scope> factor) {
		Vector featureVector = factor.getFeatureVector();

		final String classType1Name = factor.getFactorScope().classType1 == null ? null
				: factor.getFactorScope().classType1.getSimpleName();

		final Set<String> type1SurfaceForms = factor.getFactorScope().type1SurfaceForms;

		// if (classType1Name != null && type1SurfaceForms != null) {
		// /*
		// * Add features for classType1 co-occ with surface form
		// */
		// for (String sf : type1SurfaceForms) {
		// featureVector.set(classType1Name + " (" + sf + ")", true);
		// }
		// }

		final String classType2Name = factor.getFactorScope().classType2 == null ? null
				: factor.getFactorScope().classType2.getSimpleName();

		final Set<String> type2SurfaceForms = factor.getFactorScope().type2SurfaceForms == null ? null
				: factor.getFactorScope().type2SurfaceForms;

		final String propertyNameChain = factor.getFactorScope().propertyNameChain;

		// if (type2SurfaceForms != null && classType2Name != null) {
		// for (String sf2 : type2SurfaceForms) {
		// /*
		// * 1) Add features for classType2 co-occ with surface form
		// */
		// if (propertyNameChain == null)
		// featureVector.set(classType2Name + " (" + sf2 + ")", true);
		// }
		// }

		/*
		 * Add classType1 co-occ classType2
		 */
		if (propertyNameChain != null)
			featureVector.set(classType1Name + propertyNameChain + "->" + classType2Name, true);

		/*
		 * TESTME: Features to sparse? Exclude.
		 */
		// if (type1SurfaceForms != null) {
		// for (String sf1 : type1SurfaceForms) {
		//
		// /*
		// * Add features classtype1 co-occ with classType2 incl. surface
		// * forms if any.
		// */
		// if (type2SurfaceForms != null) {
		// for (String sf2 : type2SurfaceForms) {
		// featureVector.set(classType1Name + " (" + sf1 + ")" +
		// propertyNameChain + "->" + classType2Name
		// + " (" + sf2 + ")", true);
		// }
		// }
		// }
		// }

	}

}
