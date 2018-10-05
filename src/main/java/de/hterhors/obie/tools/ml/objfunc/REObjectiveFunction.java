package de.hterhors.obie.tools.ml.objfunc;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.tools.ml.evaluation.evaluator.IOBIEEvaluator;
import de.hterhors.obie.tools.ml.run.param.OBIERunParameter;
import de.hterhors.obie.tools.ml.variables.InstanceEntityAnnotations;
import de.hterhors.obie.tools.ml.variables.OBIEState;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import learning.ObjectiveFunction;

/**
 * Objective function for relation extraction
 * 
 * @author hterhors
 *
 */
public class REObjectiveFunction extends ObjectiveFunction<OBIEState, InstanceEntityAnnotations> implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static Logger log = LogManager.getFormatterLogger(REObjectiveFunction.class.getName());
	private IOBIEEvaluator evaluator;

	public REObjectiveFunction(OBIERunParameter parameter) {
		this.evaluator = parameter.evaluator;
	}

	@Override
	public double computeScore(OBIEState state, InstanceEntityAnnotations goldResult) {

		List<IOBIEThing> predictions = state.getCurrentPrediction().getTemplateAnnotations().stream()
				.map(s -> s.getTemplateAnnotation()).collect(Collectors.toList());
		List<IOBIEThing> gold = goldResult.getTemplateAnnotations().stream().map(s -> s.getTemplateAnnotation())
				.collect(Collectors.toList());

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
