package de.hterhors.obie.ml.run.eval;

import de.hterhors.obie.core.ontology.InvestigationRestriction;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.utils.OBIEClassFormatter;
import de.hterhors.obie.ml.variables.TemplateAnnotation;

public class EvaluationObject {

	public final IOBIEThing scioClass;
//	private final InvestigationRestriction investigationRestriction;

	public EvaluationObject(TemplateAnnotation resultEntity
//			, InvestigationRestriction investigationRestriction
			) {
		this.scioClass = resultEntity.getThing();
//		this.investigationRestriction = investigationRestriction;
	}

	@Override
	public String toString() {
		return OBIEClassFormatter.format(scioClass, false
//				, investigationRestriction
				);
	}

}
