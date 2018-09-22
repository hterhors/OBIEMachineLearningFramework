package de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.eval;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.InvestigationRestriction;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.OBIEClassFormatter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.EntityAnnotation;

public class EvaluationObject {

	public final IOBIEThing scioClass;
	private final InvestigationRestriction investigationRestriction;

	public EvaluationObject(EntityAnnotation resultEntity, InvestigationRestriction investigationRestriction) {
		this.scioClass = resultEntity.getAnnotationInstance();
		this.investigationRestriction = investigationRestriction;
	}

	@Override
	public String toString() {
		return OBIEClassFormatter.format(scioClass, false, investigationRestriction);
	}

}
