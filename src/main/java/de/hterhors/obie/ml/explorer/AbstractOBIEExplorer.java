package de.hterhors.obie.ml.explorer;

import de.hterhors.obie.core.ontology.InvestigationRestriction;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.variables.OBIEState;
import sampling.Explorer;

public abstract class AbstractOBIEExplorer implements Explorer<OBIEState> {

	final protected RunParameter parameter;

	public AbstractOBIEExplorer(RunParameter parameter) {
		this.parameter = parameter;
	}

//	public abstract void setInvestigationRestriction(InvestigationRestriction investigationRestriction);

}
