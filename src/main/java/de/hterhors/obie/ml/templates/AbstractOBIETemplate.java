package de.hterhors.obie.ml.templates;

import java.io.Serializable;

import de.hterhors.obie.ml.run.param.RunParameter;
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

	final protected RunParameter parameter;

	public AbstractOBIETemplate(RunParameter parameter) {
		this.parameter = parameter;
	}

}
