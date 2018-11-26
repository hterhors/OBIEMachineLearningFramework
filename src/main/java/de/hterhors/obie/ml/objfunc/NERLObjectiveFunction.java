package de.hterhors.obie.ml.objfunc;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEState;
import learning.ObjectiveFunction;

public class NERLObjectiveFunction extends ObjectiveFunction<OBIEState, InstanceTemplateAnnotations>
		implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static Logger log = LogManager.getFormatterLogger(NERLObjectiveFunction.class.getName());
	private final RunParameter parameter;

	public NERLObjectiveFunction(RunParameter parameter) {
		this.parameter = parameter;
	}

	@Override
	public double computeScore(OBIEState state, InstanceTemplateAnnotations goldResult) {

		List<IOBIEThing> predictions = state.getCurrentTemplateAnnotations().getTemplateAnnotations().stream()
				.map(s -> s.getThing()).collect(Collectors.toList());
		List<IOBIEThing> gold = goldResult.getTemplateAnnotations().stream().map(s -> s.getThing())
				.collect(Collectors.toList());

		double s = parameter.evaluator.prf1(gold, predictions).getF1();

		return s;

	}

}
