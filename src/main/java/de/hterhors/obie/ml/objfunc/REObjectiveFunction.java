package de.hterhors.obie.ml.objfunc;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.InvestigationRestriction;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.evaluation.evaluator.IOBIEEvaluator;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEState;
import learning.ObjectiveFunction;

/**
 * Objective function for relation extraction
 * 
 * @author hterhors
 *
 */
public class REObjectiveFunction extends ObjectiveFunction<OBIEState, InstanceTemplateAnnotations>
		implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static Logger log = LogManager.getFormatterLogger(REObjectiveFunction.class.getName());
	private IOBIEEvaluator evaluator;

	public REObjectiveFunction(RunParameter parameter) {
		this.evaluator = parameter.evaluator;
	}

	@Override
	public double computeScore(OBIEState state, InstanceTemplateAnnotations goldResult) {

		List<IOBIEThing> predictions = state.getCurrentTemplateAnnotations().getTemplateAnnotations().stream()
				.map(s -> s.getThing()).collect(Collectors.toList());
		List<IOBIEThing> gold = goldResult.getTemplateAnnotations().stream().map(s -> s.getThing())
				.collect(Collectors.toList());
		
//		System.out.println("obj");
//		InvestigationRestriction investigationRestriction = ;

//		evaluator.setInvestigationRestrictions(investigationRestriction);

		// System.out.println("predictions = ");
		// predictions.forEach(p ->
		// System.out.println(OBIEFormatter.format(p)));
		// System.out.println("gold = ");
		// gold.forEach(p -> System.out.println(OBIEFormatter.format(p)));
		double s = evaluator.f1(gold, predictions);
		// System.out.println("OBIEFormatter.f1(gold, predictions) = " + s);

		return s;

	}

}
