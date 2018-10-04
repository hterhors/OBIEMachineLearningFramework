package de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.eval;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.InvestigationRestriction;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.OBIEClassFormatter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.TemplateAnnotation;

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
