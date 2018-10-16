package de.hterhors.obie.ml.templates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.OntologyModelContent;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.run.param.OBIERunParameter;
import de.hterhors.obie.ml.templates.LocalityTemplate.Scope;
import de.hterhors.obie.ml.templates.utils.ClassTypePositionPair;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import factors.Factor;
import factors.FactorScope;
import learning.Vector;

@Deprecated
public class LocalityTemplate extends AbstractOBIETemplate<Scope> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public LocalityTemplate(OBIERunParameter parameter) {
		super(parameter);
	}

	private static Logger log = LogManager.getFormatterLogger(LocalityTemplate.class.getName());

	private static final List<Integer> localityDistances = Arrays.asList(1, 2, 3, 4, 5, 7, 10, 12, 15, 18, 20, 30, 40,
			50, 65, 80, 90, 100, 150, 200, 300, 400, 500, 700, 1000, 1500, 2000);

	static class Scope extends FactorScope {

		private final ClassTypePositionPair context;
		private final ClassTypePositionPair class1;
		private final ClassTypePositionPair class2;

		public Scope(Class<? extends IOBIEThing> entityRootClassType, AbstractOBIETemplate<?> template,
				ClassTypePositionPair parentContext, ClassTypePositionPair class1, ClassTypePositionPair class2) {
			super(template, parentContext, class1, class2, entityRootClassType);
			this.context = parentContext;
			this.class1 = class1;
			this.class2 = class2;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		for (TemplateAnnotation entity : state.getCurrentTemplateAnnotations().getTemplateAnnotations()) {
			try {
				factors.addAll(
						addFactorRecursive(entity.rootClassType, state.getInstance(), entity.getThing()));
			} catch (IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		return factors;
	}

	private List<Scope> addFactorRecursive(Class<? extends IOBIEThing> entityRootClassType, OBIEInstance document,
			final IOBIEThing scioClass) throws IllegalArgumentException, IllegalAccessException, SecurityException {
		List<Scope> factors = new ArrayList<>();

		if (scioClass == null)
			return factors;

		List<IOBIEThing> properties = convertFieldPropertiesToClasses(scioClass);

		Collections.sort(properties, new Comparator<IOBIEThing>() {

			@Override
			public int compare(IOBIEThing arg0, IOBIEThing arg1) {
				return (arg0 == null ? "null" : arg0.getClass().getSimpleName())
						.compareTo((arg1 == null ? "null" : arg1.getClass().getSimpleName()));
			}
		});

		/*
		 * Add factors for object type properties.
		 */

		for (int i = 0; i < properties.size(); i++) {
			IOBIEThing newClass1 = properties.get(i);
			for (int j = i + 1; j < properties.size(); j++) {
				IOBIEThing newClass2 = properties.get(j);
				/*
				 * Add with context
				 */
				addFactor(entityRootClassType, document, scioClass, newClass1, newClass2, factors);
				/*
				 * Add without context
				 */
				addFactor(entityRootClassType, document, null, newClass1, newClass2, factors);
			}
			addFactor(entityRootClassType, document, null, scioClass, newClass1, factors);
			factors.addAll(addFactorRecursive(entityRootClassType, document, newClass1));
		}

		return factors;

	}

	private List<IOBIEThing> convertFieldPropertiesToClasses(final IOBIEThing scioClass) {
		List<IOBIEThing> properties = Arrays.stream(scioClass.getClass().getDeclaredFields())
				.filter(f -> (!f.isAnnotationPresent(DatatypeProperty.class)
						&& f.isAnnotationPresent(OntologyModelContent.class)))
				.map(f -> {
					try {
						f.setAccessible(true);
						return (IOBIEThing) f.get(scioClass);
					} catch (Exception e) {
						e.printStackTrace();
					}
					return null;
				}).collect(Collectors.toList());
		return properties;
	}

	private void addFactor(Class<? extends IOBIEThing> entityRootClassType, OBIEInstance document, IOBIEThing context,
			IOBIEThing class1, IOBIEThing class2, List<Scope> factors) {

		if (class1 == null)
			return;

		if (class2 == null)
			return;

		if (class1.getCharacterOnset() == null)
			return;

		if (class1.getTextMention() == null)
			return;

		if (class2.getCharacterOnset() == null)
			return;

		if (class2.getTextMention() == null)
			return;

		final ClassTypePositionPair class1Pair;
		final ClassTypePositionPair class2Pair;

		ClassTypePositionPair contextPair = null;
		final long class1Onset = document.charPositionToTokenPosition(class1.getCharacterOnset());

		class1Pair = new ClassTypePositionPair(class1.getClass(), class1Onset);

		final long class2Onset = document.charPositionToTokenPosition(class2.getCharacterOnset());
		class2Pair = new ClassTypePositionPair(class2.getClass(), class2Onset);

		if (context != null && context.getCharacterOnset() != null && context.getTextMention() != null) {

			final long contextOnset = document.charPositionToTokenPosition(context.getCharacterOnset());
			contextPair = new ClassTypePositionPair(context.getClass(), contextOnset);
		}

		final Set<Class<? extends IOBIEThing>> influencedVariables = new HashSet<>();
		influencedVariables.add(class2.getClass());

		factors.add(new Scope(entityRootClassType, this, contextPair, class1Pair, class2Pair));
	}

	@Override
	public void computeFactor(Factor<Scope> factor) {
		Vector featureVector = factor.getFeatureVector();

		final String class1Name = factor.getFactorScope().class1.classType.getSimpleName();
		final long position1 = factor.getFactorScope().class1.position;
		final String class2Name = factor.getFactorScope().class2.classType.getSimpleName();
		final long position2 = factor.getFactorScope().class2.position;

		for (final int dist : localityDistances) {

			boolean local = Math.abs(position1 - position2) <= dist;

			featureVector.set("[" + class1Name + "_" + class2Name + "] dist <= " + dist, local);
			featureVector.set("[" + class1Name + "_" + class2Name + "] dist > " + dist, !local);

			if (factor.getFactorScope().context == null)
				continue;

			final String contextName = factor.getFactorScope().context.classType.getSimpleName();
			final long contextPosition = factor.getFactorScope().context.position;

			boolean context1Local = Math.abs(position1 - contextPosition) <= dist;

			boolean context2Local = Math.abs(contextPosition - position2) <= dist;

			featureVector.set("[" + contextName + "_" + class1Name + "] dist <= " + dist, context1Local);
			featureVector.set("[" + contextName + "_" + class2Name + "] dist <= " + dist, context2Local);

			featureVector.set("[" + contextName + "_" + class1Name + "] dist > " + dist, !context1Local);
			featureVector.set("[" + contextName + "_" + class2Name + "] dist > " + dist, !context2Local);

		}

	}

}
