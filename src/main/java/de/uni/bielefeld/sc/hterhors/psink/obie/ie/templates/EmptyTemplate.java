package de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.OntologyModelContent;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.EmptyTemplate.Scope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.scope.OBIEFactorScope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.ReflectionUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.TemplateAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;
import factors.Factor;
import learning.Vector;

public class EmptyTemplate extends AbstractOBIETemplate<Scope> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public EmptyTemplate(OBIERunParameter parameter) {
		super(parameter);
	}

	private static Logger log = LogManager.getFormatterLogger(EmptyTemplate.class.getName());

	class Scope extends OBIEFactorScope {

		public Scope(Class<? extends IOBIEThing> entityRootClassType, AbstractOBIETemplate<?> template) {
			super(template, entityRootClassType);
		}

		@Override
		public String toString() {
			return "FactorVariables [getInfluencedVariables()=" + getInfluencedVariables() + "]";
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

		final String className = scioClass.getClass().getSimpleName();
		final String surfaceForm = scioClass.getTextMention();

		if (surfaceForm != null) {

			factors.add(new Scope(entityRootClassType, this));
		}
		/*
		 * Add factors for object type properties.
		 */
		Arrays.stream(scioClass.getClass().getDeclaredFields())
				.filter(f -> f.isAnnotationPresent(OntologyModelContent.class)).forEach(field -> {
					field.setAccessible(true);
					try {

						if (field.isAnnotationPresent(RelationTypeCollection.class)) {
							for (IOBIEThing element : (List<IOBIEThing>) field.get(scioClass)) {
								if (ReflectionUtils.isAnnotationPresent(field, DatatypeProperty.class)) {

								} else {

								}

							}
						} else {
							if (ReflectionUtils.isAnnotationPresent(field, DatatypeProperty.class)) {

							} else {

							}
						}
						factors.addAll(addFactorRecursive(entityRootClassType, (IOBIEThing) field.get(scioClass)));
					} catch (Exception e) {
						e.printStackTrace();
					}
				});
		return factors;
	}

	@Override
	public void computeFactor(Factor<Scope> factor) {
		Vector featureVector = factor.getFeatureVector();
	}

}
