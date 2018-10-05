package de.hterhors.obie.tools.ml.templates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.tools.ml.run.param.OBIERunParameter;
import de.hterhors.obie.tools.ml.templates.EmptyRootClassCardinalityTemplate.Scope;
import de.hterhors.obie.tools.ml.templates.scope.OBIEFactorScope;
import de.hterhors.obie.tools.ml.variables.OBIEState;
import de.hterhors.obie.tools.ml.variables.TemplateAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import factors.Factor;
import learning.Vector;

public class EmptyRootClassCardinalityTemplate extends AbstractOBIETemplate<Scope> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public EmptyRootClassCardinalityTemplate(OBIERunParameter parameter) {
		super(parameter);
	}

	private static Logger log = LogManager.getFormatterLogger(EmptyRootClassCardinalityTemplate.class);

	class Scope extends OBIEFactorScope {

		public final int numberOfEmptyRootClasses;
		public final Class<? extends IOBIEThing> rootClassType;

		public Scope(Set<Class<? extends IOBIEThing>> influencedVariables,
				Class<? extends IOBIEThing> entityRootClassType, AbstractOBIETemplate<?> template,
				Class<? extends IOBIEThing> rootClassType, final int numberOfEmptyRootClasses) {
			super(influencedVariables, entityRootClassType, template, entityRootClassType, rootClassType,
					numberOfEmptyRootClasses);
			this.numberOfEmptyRootClasses = numberOfEmptyRootClasses;
			this.rootClassType = rootClassType;
		}
	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();

		final Map<Class<? extends IOBIEThing>, Integer> countEmptyClasses = new HashMap<>();

		for (TemplateAnnotation entity : state.getCurrentPrediction().getTemplateAnnotations()) {
			if (entity.getTemplateAnnotation().equals(entity.getInitializationClass())) {
				countEmptyClasses.put(entity.rootClassType,
						countEmptyClasses.getOrDefault(entity.rootClassType, 0) + 1);
			}
		}
		for (Entry<Class<? extends IOBIEThing>, Integer> count : countEmptyClasses.entrySet()) {
			final Set<Class<? extends IOBIEThing>> influencedVariables = new HashSet<>();
			// influencedVariables.add(count.getKey());
			factors.add(new Scope(influencedVariables, count.getKey(), this, count.getKey(), count.getValue()));
		}

		return factors;
	}

	@Override
	public void computeFactor(Factor<Scope> factor) {
		Vector featureVector = factor.getFeatureVector();

		final int numberOfEmptyRootClasses = factor.getFactorScope().numberOfEmptyRootClasses;
		final Class<? extends IOBIEThing> rootClassType = factor.getFactorScope().rootClassType;
		featureVector.set("#OfEmpty_" + rootClassType.getSimpleName() + " <= 1", numberOfEmptyRootClasses <= 1);
		// featureVector.set("#OfEmpty_" + rootClassType.getSimpleName() + " >
		// 1", numberOfEmptyRootClasses > 1);
		// featureVector.set("#OfEmpty_" + rootClassType.getSimpleName() + " ==
		// 1", numberOfEmptyRootClasses == 1);
	}

}
