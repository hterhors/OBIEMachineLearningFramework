package de.hterhors.obie.ml.templates;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.ner.regex.BasicRegExPattern;
import de.hterhors.obie.ml.run.AbstractOBIERunner;
import de.hterhors.obie.ml.templates.BOWnGramsTemplate.Scope;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.IETmplateAnnotation;
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
public class BOWnGramsTemplate extends AbstractOBIETemplate<Scope> implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Logger log = LogManager.getFormatterLogger(BOWnGramsTemplate.class);

	private static final String TOKEN_SPLITTER_SPACE = " ";

	private static final String END_SIGN = "$";

	private static final String START_SIGN = "^";

	private static final int MIN_TOKEN_LENGTH = 2;

	private static final String LEFT = "<";

	private static final String RIGHT = ">";

	private static final int MAX_N_GRAM_SIZE = 3;

	public BOWnGramsTemplate(AbstractOBIERunner runner) {
		super(runner);
	}

	class Scope extends FactorScope {

		public Class<? extends IOBIEThing> classType;
		public final AbstractIndividual individual;
		public OBIEInstance instance;

		public Scope(OBIEInstance instance, Class<? extends IOBIEThing> entityRootClassType,
				Class<? extends IOBIEThing> classType, AbstractIndividual individual) {
			super(BOWnGramsTemplate.this, instance, entityRootClassType, classType, individual);
			this.classType = classType;
			this.individual = individual;
			this.instance = instance;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();

		for (IETmplateAnnotation entity : state.getCurrentIETemplateAnnotations().getAnnotations()) {
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
		ReflectionUtils.getNonDatatypeSlots(obieThing.getClass(),obieThing.getInvestigationRestriction()).forEach(field -> {
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

		final List<String> tokens = factor.getFactorScope().instance.getTokens().stream().map(t -> t.getText())
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

				boolean add = true;
				for (int t = offset; t < offset + n; t++) {

					final String token = tokens.get(t);

					if (token.isEmpty()) {
//						fBuffer.append("<EMPTY>").append(TOKEN_SPLITTER_SPACE);
						add = false;
						break;
					}

					if (BasicRegExPattern.STOP_WORDS.contains(token.toLowerCase())) {
//						fBuffer.append("<STOP>").append(TOKEN_SPLITTER_SPACE);
						add = false;
						break;
					}

					fBuffer.append(token).append(TOKEN_SPLITTER_SPACE);

				}

				if (!add)
					continue;

				final String featureName = fBuffer.toString().toLowerCase();

				if (fBuffer.length() < MIN_TOKEN_LENGTH)
					continue;

				if (fBuffer.length() == 0)
					continue;

				featureVector.set(LEFT + context + RIGHT + TOKEN_SPLITTER_SPACE + featureName, true);

			}
		}
	}
}
//p: 0.765625	r: 0.765625	f1: 0.765625
//OFF
//MICRO: Mean-Precisiion = 0.21052631578947367
//MICRO: Mean-Recall = 0.21052631578947367
//MICRO: Mean-F1 = 0.21052631578947367
//MACRO: Mean-Precisiion = 0.21052631578947367
//MACRO: Mean-Recall = 0.21052631578947367
//MACRO: Mean-F1 = 0.21052631578947367
//NOT
//MICRO: Mean-Precisiion = 1.0
//MICRO: Mean-Recall = 1.0
//MICRO: Mean-F1 = 1.0
//MACRO: Mean-Precisiion = 1.0
//MACRO: Mean-Recall = 1.0
//MACRO: Mean-F1 = 1.0

//n=4
//p: 0.75	r: 0.75	f1: 0.75
//OFF
//MICRO: Mean-Precisiion = 0.21052631578947367
//MICRO: Mean-Recall = 0.21052631578947367
//MICRO: Mean-F1 = 0.21052631578947367
//MACRO: Mean-Precisiion = 0.21052631578947367
//MACRO: Mean-Recall = 0.21052631578947367
//MACRO: Mean-F1 = 0.21052631578947367
//NOT
//MICRO: Mean-Precisiion = 0.9777777777777777
//MICRO: Mean-Recall = 0.9777777777777777
//MICRO: Mean-F1 = 0.9777777777777777
//MACRO: Mean-Precisiion = 0.9777777777777777
//MACRO: Mean-Recall = 0.9777777777777777
//MACRO: Mean-F1 = 0.9777777777777777

//n=3
//p: 0.6875	r: 0.6875	f1: 0.6875
//OFF
//MICRO: Mean-Precisiion = 0.2631578947368421
//MICRO: Mean-Recall = 0.2631578947368421
//MICRO: Mean-F1 = 0.2631578947368421
//MACRO: Mean-Precisiion = 0.2631578947368421
//MACRO: Mean-Recall = 0.2631578947368421
//MACRO: Mean-F1 = 0.2631578947368421
//NOT
//MICRO: Mean-Precisiion = 0.8666666666666667
//MICRO: Mean-Recall = 0.8666666666666667
//MICRO: Mean-F1 = 0.8666666666666667
//MACRO: Mean-Precisiion = 0.8666666666666667
//MACRO: Mean-Recall = 0.8666666666666667
//MACRO: Mean-F1 = 0.8666666666666667

//n=2
//p: 0.75	r: 0.75	f1: 0.75
//OFF
//MICRO: Mean-Precisiion = 0.3157894736842105
//MICRO: Mean-Recall = 0.3157894736842105
//MICRO: Mean-F1 = 0.3157894736842105
//MACRO: Mean-Precisiion = 0.3157894736842105
//MACRO: Mean-Recall = 0.3157894736842105
//MACRO: Mean-F1 = 0.3157894736842105
//NOT
//MICRO: Mean-Precisiion = 0.9333333333333333
//MICRO: Mean-Recall = 0.9333333333333333
//MICRO: Mean-F1 = 0.9333333333333333
//MACRO: Mean-Precisiion = 0.9333333333333333
//MACRO: Mean-Recall = 0.9333333333333333
//MACRO: Mean-F1 = 0.9333333333333333
//--------------randomRun1059004149---------------
//Total training time: 1305 ms.
//Total test time: 35 ms.
//Total time: PT1.34S
//n=1
//p: 0.703125	r: 0.703125	f1: 0.703125
//OFF
//MICRO: Mean-Precisiion = 0.3684210526315789
//MICRO: Mean-Recall = 0.3684210526315789
//MICRO: Mean-F1 = 0.3684210526315789
//MACRO: Mean-Precisiion = 0.3684210526315789
//MACRO: Mean-Recall = 0.3684210526315789
//MACRO: Mean-F1 = 0.3684210526315789
//NOT
//MICRO: Mean-Precisiion = 0.8444444444444444
//MICRO: Mean-Recall = 0.8444444444444444
//MICRO: Mean-F1 = 0.8444444444444444
//MACRO: Mean-Precisiion = 0.8444444444444444
//MACRO: Mean-Recall = 0.8444444444444444
//MACRO: Mean-F1 = 0.8444444444444444