package de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.OntologyAnalyzer;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.OntologyModelContent;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.HierarchyTemplate.Scope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.scope.OBIEFactorScope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.ReflectionUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.TemplateAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;
import factors.Factor;
import learning.Vector;

public class HierarchyTemplate extends AbstractOBIETemplate<Scope> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Logger log = LogManager.getFormatterLogger(HierarchyTemplate.class.getSimpleName());

	final private static String FEATURE_GREATER_THAN_HIERARCHY = "Type_of_%s_has_hierarchy > %d";
	final private static String FEATURE_EQUAL_HIERARCHY = "Type_of_%s_has_hierarchy = %d";

	public HierarchyTemplate(OBIERunParameter parameter) {
		super(parameter);
	}

	class Scope extends OBIEFactorScope {

		final Class<? extends IOBIEThing> clazz;

		public Scope(
				Class<? extends IOBIEThing> entityRootClassType, AbstractOBIETemplate<?> template,
				Class<? extends IOBIEThing> scioClass) {
			super(template, scioClass, entityRootClassType);
			this.clazz = scioClass;
		}

		@Override
		public String toString() {
			return "Scope [scioClass=" + clazz + ", getInfluencedVariables()=" + getInfluencedVariables() + "]";
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		for (TemplateAnnotation entity : state.getCurrentPrediction().getTemplateAnnotations()) {
			factors.addAll(addFactorRecursive(entity.rootClassType, entity.getTemplateAnnotation()));
		}
		return factors;
	}

	private List<Scope> addFactorRecursive(Class<? extends IOBIEThing> entityRootClassType, IOBIEThing scioClass) {
		List<Scope> factors = new ArrayList<>();

		if (scioClass == null)
			return factors;

		final Set<Class<? extends IOBIEThing>> influencedVariables = new HashSet<>();
		influencedVariables.add(scioClass.getClass());

		if (!ReflectionUtils.isAnnotationPresent(scioClass.getClass(), DatatypeProperty.class)) {
			factors.add(new Scope(entityRootClassType, this, scioClass.getClass()));
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
							/**
							 * TODO: Integrate lists.
							 */
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

		final Set<Class<? extends IOBIEThing>> rootClassTypes = ReflectionUtils
				.getSuperRootClasses(factor.getFactorScope().clazz);

		final int hierarchy = OntologyAnalyzer.getHierarchy(parameter.projectEnvironment.getOntologyThingInterface(),
				factor.getFactorScope().clazz);

		for (Class<? extends IOBIEThing> rootClass : rootClassTypes) {

			final String className = rootClass.isInterface()
					? ReflectionUtils.getImplementationClass(rootClass).getSimpleName()
					: rootClass.getSimpleName();

			for (int hLevel = 1; hLevel < hierarchy; hLevel++) {
				featureVector.set(String.format(FEATURE_GREATER_THAN_HIERARCHY, className, hLevel), true);
			}
			featureVector.set(String.format(FEATURE_EQUAL_HIERARCHY, className, hierarchy), true);
		}
	}

}
