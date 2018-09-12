package de.uni.bielefeld.sc.hterhors.psink.obie.ie.objfunc;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation.evaluator.IEvaluator;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.InstanceEntityAnnotations;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;
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
	private IEvaluator evaluator;

	public REObjectiveFunction(OBIERunParameter parameter) {
		this.evaluator = parameter.evaluator;
	}

	@Override
	public double computeScore(OBIEState state, InstanceEntityAnnotations goldResult) {

		List<IOBIEThing> predictions = state.getCurrentPrediction().getEntityAnnotations().stream()
				.map(s -> s.getAnnotationInstance()).collect(Collectors.toList());
		List<IOBIEThing> gold = goldResult.getEntityAnnotations().stream().map(s -> s.getAnnotationInstance())
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
