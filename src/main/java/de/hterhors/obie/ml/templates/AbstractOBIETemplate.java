package de.hterhors.obie.ml.templates;

import java.io.Serializable;

import de.hterhors.obie.ml.run.AbstractOBIERunner;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import factors.FactorScope;
import templates.AbstractTemplate;

public abstract class AbstractOBIETemplate<Scope extends FactorScope>
		extends AbstractTemplate<OBIEInstance, OBIEState, Scope> implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	final protected AbstractOBIERunner runner;

	/**
	 * Whether distant supervision is enabled for this template or not. This effects
	 * the way of calculating the factors and features!
	 */
	protected final boolean isDistantSupervision;

	public AbstractOBIETemplate(AbstractOBIERunner runner) {
		this.runner = runner;
		this.isDistantSupervision = runner.getParameter().exploreOnOntologyLevel;
	}

}
