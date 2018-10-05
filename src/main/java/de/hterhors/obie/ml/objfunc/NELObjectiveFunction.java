package de.hterhors.obie.ml.objfunc;

import java.io.Serializable;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.interfaces.IDatatype;
import de.hterhors.obie.ml.evaluation.evaluator.NamedEntityLinkingEvaluator;
import de.hterhors.obie.ml.utils.ReflectionUtils;
import de.hterhors.obie.ml.variables.InstanceEntityAnnotations;
import de.hterhors.obie.ml.variables.OBIEState;
import learning.ObjectiveFunction;

public class NELObjectiveFunction extends ObjectiveFunction<OBIEState, InstanceEntityAnnotations>
		implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static Logger log = LogManager.getFormatterLogger(NELObjectiveFunction.class.getName());

	@Override
	public double computeScore(OBIEState state, InstanceEntityAnnotations goldResult) {

		Set<String> gold = goldResult.getTemplateAnnotations().stream().map(s -> {
			if (ReflectionUtils.isAnnotationPresent(s.getTemplateAnnotation().getClass(), DatatypeProperty.class) ) {
				return ((IDatatype) s.getTemplateAnnotation()).getSemanticValue();
			} else {
				return s.getTemplateAnnotation().getTextMention();
			}
		}).collect(Collectors.toSet());

		Set<String> predictions = state.getCurrentPrediction().getTemplateAnnotations().stream().map(s -> {
			if (ReflectionUtils.isAnnotationPresent(s.getTemplateAnnotation().getClass(), DatatypeProperty.class) ) {
				return ((IDatatype) s.getTemplateAnnotation()).getSemanticValue();
			} else {
				return s.getTemplateAnnotation().getTextMention();
			}
		}).collect(Collectors.toSet());

		// System.out.println("gold = ");
		// gold.forEach(g -> System.out.println(g));
		// System.out.println("predictions = ");
//		predictions.forEach(p -> System.out.println(p));
		double s = NamedEntityLinkingEvaluator.f1(gold, predictions);
		// System.out.println("SCORE= " + s);

		return s;

	}

}
