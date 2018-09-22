package de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates;

import java.io.Serializable;

import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.scope.OBIEFactorScope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;
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
