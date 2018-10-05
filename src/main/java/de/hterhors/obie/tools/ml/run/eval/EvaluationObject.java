package de.hterhors.obie.tools.ml.run.eval;

import de.hterhors.obie.tools.ml.run.InvestigationRestriction;
import de.hterhors.obie.tools.ml.utils.OBIEClassFormatter;
import de.hterhors.obie.tools.ml.variables.TemplateAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;

public class EvaluationObject {

	public final IOBIEThing scioClass;
	private final InvestigationRestriction investigationRestriction;

	public EvaluationObject(TemplateAnnotation resultEntity, InvestigationRestriction investigationRestriction) {
		this.scioClass = resultEntity.getTemplateAnnotation();
		this.investigationRestriction = investigationRestriction;
	}

	@Override
	public String toString() {
		return OBIEClassFormatter.format(scioClass, false, investigationRestriction);
	}

}