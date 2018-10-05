package de.hterhors.obie.tools.ml.templates;

import java.io.Serializable;

import de.hterhors.obie.tools.ml.run.param.OBIERunParameter;
import de.hterhors.obie.tools.ml.templates.scope.OBIEFactorScope;
import de.hterhors.obie.tools.ml.variables.OBIEInstance;
import de.hterhors.obie.tools.ml.variables.OBIEState;
import templates.AbstractTemplate;

public abstract class AbstractOBIETemplate<Scope extends OBIEFactorScope>
		extends AbstractTemplate<OBIEInstance, OBIEState, Scope> implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	final protected OBIERunParameter parameter;

	public AbstractOBIETemplate(OBIERunParameter parameter) {
		this.parameter = parameter;
	}

}
