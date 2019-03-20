package de.hterhors.obie.ml.templates;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.run.AbstractOBIERunner;
import de.hterhors.obie.ml.templates.NERTemplate.Scope;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.IETmplateAnnotation;
import factors.Factor;
import factors.FactorScope;
import learning.Vector;

/**
 * Checks whether the annotated surface form was found by the NER-module. As
 * annotations for classes are only set by the NER findings, this template makes
 * no sense for the linking task. This template makes only sense for the NER
 * tasks in combination with the MultipleTokenBoundaryExplorer.
 * 
 * @author hterhors
 *
 * @date Nov 15, 2017
 */
public class NERTemplate extends AbstractOBIETemplate<Scope> {

	public NERTemplate(AbstractOBIERunner runner) {
		super(runner);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Logger log = LogManager.getFormatterLogger(NERTemplate.class.getName());

	class Scope extends FactorScope {

		public Class<? extends IOBIEThing> classType;
		public String surfaceForm;
		public OBIEInstance instance;
		public Class<? extends IOBIEThing> entityRootClassType;

		public Scope(Class<? extends IOBIEThing> annotationRootClassType, AbstractOBIETemplate<?> template,
				OBIEInstance instance, Class<? extends IOBIEThing> classType, String surfaceForm) {
			super(template, annotationRootClassType, instance, classType, surfaceForm);
			this.instance = instance;
			this.classType = classType;
			this.surfaceForm = surfaceForm;
			this.entityRootClassType = annotationRootClassType;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		for (IETmplateAnnotation entity : state.getCurrentIETemplateAnnotations().getAnnotations()) {
			factors.addAll(addFactorRecursive(state.getInstance(), entity.rootClassType, entity.getThing()));
		}
		return factors;
	}

	private List<Scope> addFactorRecursive(OBIEInstance internalInstance,
			Class<? extends IOBIEThing> entityRootClassType, IOBIEThing scioClass) {
		List<Scope> factors = new ArrayList<>();

		if (scioClass == null)
			return factors;

		final String surfaceForm = scioClass.getTextMention();
		// System.out.println("--> in template: " + surfaceForm);

		if (surfaceForm != null) {
			factors.add(new Scope(entityRootClassType, this, internalInstance, scioClass.getClass(), surfaceForm));
		}

		/*
		 * Add factors for object type properties.
		 */
		if (ReflectionUtils.isAnnotationPresent(scioClass.getClass(), DatatypeProperty.class))
			return factors;

		ReflectionUtils.getNonDatatypeSlots(scioClass.getClass(), scioClass.getInvestigationRestriction())
				.forEach(field -> {
					try {
						if (ReflectionUtils.isAnnotationPresent(field, RelationTypeCollection.class)) {
							for (IOBIEThing element : (List<IOBIEThing>) field.get(scioClass)) {
								factors.addAll(addFactorRecursive(internalInstance, entityRootClassType, element));
							}
						} else {
							factors.addAll(addFactorRecursive(internalInstance, entityRootClassType,
									(IOBIEThing) field.get(scioClass)));
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

		if (!factor.getFactorScope().instance.getEntityAnnotations()
				.containsClassAnnotations(factor.getFactorScope().classType))
			return;
		boolean foundByNER = factor.getFactorScope().instance.getEntityAnnotations()
				.getClassAnnotations(factor.getFactorScope().classType).stream()
				.map(e -> e.getDTValueIfAnyElseTextMention()).collect(Collectors.toSet())
				.contains(factor.getFactorScope().surfaceForm);

		featureVector.set(factor.getFactorScope().entityRootClassType.getSimpleName() + " - FoundByNER", foundByNER);
		featureVector.set(factor.getFactorScope().entityRootClassType.getSimpleName() + " - NotFoundByNER",
				!foundByNER);
	}

}
