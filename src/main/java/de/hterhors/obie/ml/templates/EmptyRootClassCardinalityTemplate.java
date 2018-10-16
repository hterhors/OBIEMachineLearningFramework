package de.hterhors.obie.ml.templates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.run.param.OBIERunParameter;
import de.hterhors.obie.ml.templates.EmptyRootClassCardinalityTemplate.Scope;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import factors.Factor;
import factors.FactorScope;
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

	class Scope extends FactorScope {

		public final int numberOfEmptyRootClasses;
		public final Class<? extends IOBIEThing> rootClassType;

		public Scope(Class<? extends IOBIEThing> entityRootClassType, AbstractOBIETemplate<?> template,
				Class<? extends IOBIEThing> rootClassType, final int numberOfEmptyRootClasses) {
			super(template, entityRootClassType, rootClassType, numberOfEmptyRootClasses);
			this.numberOfEmptyRootClasses = numberOfEmptyRootClasses;
			this.rootClassType = rootClassType;
		}
	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();

		final Map<Class<? extends IOBIEThing>, Integer> countEmptyClasses = new HashMap<>();

		for (TemplateAnnotation entity : state.getCurrentTemplateAnnotations().getTemplateAnnotations()) {
			if (entity.getThing().equals(entity.getInitializationClass())) {
				countEmptyClasses.put(entity.rootClassType,
						countEmptyClasses.getOrDefault(entity.rootClassType, 0) + 1);
			}
		}
		for (Entry<Class<? extends IOBIEThing>, Integer> count : countEmptyClasses.entrySet()) {
			factors.add(new Scope(count.getKey(), this, count.getKey(), count.getValue()));
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
