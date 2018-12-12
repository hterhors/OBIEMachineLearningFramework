package de.hterhors.obie.ml.templates;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.OntologyModelContent;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.core.tokenizer.Token;
import de.hterhors.obie.ml.ner.regex.BasicRegExPattern;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.templates.BOCharNGramsTemplate.Scope;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import factors.Factor;
import factors.FactorScope;
import learning.Vector;

/**
 * only this template
 * 
 * Mean(n=138): model=1.248880181258857; objective=0.33594116520360173 Mean real
 * performance: 0.31447035145447844
 * 
 * @author hterhors
 *
 * @date Nov 3, 2017
 */
public class BOCharNGramsTemplate extends AbstractOBIETemplate<Scope> implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Logger log = LogManager.getFormatterLogger(BOCharNGramsTemplate.class);

	private static final String TOKEN_SPLITTER_SPACE = " ";

	private static final String END_SIGN = "$";

	private static final String START_SIGN = "^";

	private static final int MIN_TOKEN_LENGTH = 2;

	private static final String LEFT = "<";

	private static final String RIGHT = ">";

	private static final int MAX_N_GRAM_SIZE = 7;

	public BOCharNGramsTemplate(RunParameter parameter) {
		super(parameter);
	}

	class Scope extends FactorScope {

		public Class<? extends IOBIEThing> classType;
		public final AbstractIndividual individual;
		public OBIEInstance instance;

		public Scope(OBIEInstance instance, Class<? extends IOBIEThing> entityRootClassType,
				Class<? extends IOBIEThing> classType, AbstractIndividual individual) {
			super(BOCharNGramsTemplate.this, instance, entityRootClassType, classType, individual);
			this.classType = classType;
			this.individual = individual;
			this.instance = instance;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();

		for (TemplateAnnotation entity : state.getCurrentTemplateAnnotations().getTemplateAnnotations()) {
			addFactorRecursive(factors, state.getInstance(), entity.rootClassType, entity.getThing());
		}

		return factors;
	}

	private void addFactorRecursive(List<Scope> factors, OBIEInstance internalInstance,
			Class<? extends IOBIEThing> rootClassType, IOBIEThing obieThing) {

		if (obieThing == null)
			return;

		factors.add(new Scope(internalInstance, rootClassType, obieThing.getClass(), obieThing.getIndividual()));

		/*
		 * Add factors for object type properties.
		 */
		ReflectionUtils.getSlots(obieThing.getClass()).forEach(field -> {
			try {
				if (ReflectionUtils.isAnnotationPresent(field, RelationTypeCollection.class)) {
					for (IOBIEThing element : (List<IOBIEThing>) field.get(obieThing)) {
						addFactorRecursive(factors, internalInstance, rootClassType, element);
					}
				} else {
					addFactorRecursive(factors, internalInstance, rootClassType, (IOBIEThing) field.get(obieThing));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		return;

	}

	@Override
	public void computeFactor(Factor<Scope> factor) {

		Vector featureVector = factor.getFeatureVector();

		final String context = factor.getFactorScope().individual.name;

		final List<String> tokens = Arrays.stream(factor.getFactorScope().instance.getContent().split("(?!^)"))
				.collect(Collectors.toList());

		tokens.add(0, START_SIGN);
		tokens.add(tokens.size(), END_SIGN);

		for (int n = 1; n <= MAX_N_GRAM_SIZE; n++) {
			for (int offset = 0; offset < tokens.size() - 1; offset++) {

				/*
				 * Do not include start symbol.
				 */
				if (offset + n == 1)
					continue;

				/*
				 * Break if size exceeds token length
				 */
				if (offset + n > tokens.size())
					break;

				StringBuffer fBuffer = new StringBuffer();
				for (int t = offset; t < offset + n; t++) {

					if (tokens.get(t).isEmpty()) {
						fBuffer.append("<EMPTY>").append(TOKEN_SPLITTER_SPACE);
						continue;
					}

					fBuffer.append(tokens.get(t)).append(TOKEN_SPLITTER_SPACE);

				}

				final String featureName = fBuffer.toString().trim();

				if (featureName.length() < MIN_TOKEN_LENGTH)
					continue;

				if (featureName.isEmpty())
					continue;

				featureVector.set(LEFT + context + RIGHT + TOKEN_SPLITTER_SPACE + featureName, true);

			}
		}

	}

}
