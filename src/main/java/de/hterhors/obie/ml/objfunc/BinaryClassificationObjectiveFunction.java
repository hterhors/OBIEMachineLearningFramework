package de.hterhors.obie.ml.objfunc;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

import de.hterhors.obie.core.ontology.AbstractIndividual;
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
public class BinaryClassificationObjectiveFunction extends ObjectiveFunction<OBIEState, InstanceTemplateAnnotations>
		implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public BinaryClassificationObjectiveFunction(RunParameter parameter) {
	}

	@Override
	public double computeScore(OBIEState state, InstanceTemplateAnnotations goldResult) {

		List<AbstractIndividual> predictions = state.getCurrentTemplateAnnotations().getTemplateAnnotations().stream()
				.map(s -> s.getThing().getIndividual()).collect(Collectors.toList());
		List<AbstractIndividual> gold = goldResult.getTemplateAnnotations().stream()
				.map(s -> s.getThing().getIndividual()).collect(Collectors.toList());

		return predictions.equals(gold) ? 1D : 0D;
	}

}
