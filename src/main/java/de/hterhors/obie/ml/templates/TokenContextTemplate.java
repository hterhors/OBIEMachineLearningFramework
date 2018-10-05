package de.hterhors.obie.ml.templates;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.AbstractOBIEIndividual;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.core.tokenizer.Token;
import de.hterhors.obie.ml.run.param.OBIERunParameter;
import de.hterhors.obie.ml.templates.TokenContextTemplate.Scope;
import de.hterhors.obie.ml.templates.scope.OBIEFactorScope;
import de.hterhors.obie.ml.utils.ReflectionUtils;
import de.hterhors.obie.ml.variables.NERLClassAnnotation;
import de.hterhors.obie.ml.variables.NERLIndividualAnnotation;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import factors.Factor;
import learning.Vector;

public class TokenContextTemplate extends AbstractOBIETemplate<Scope> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final String SPLITTER = " ";
	private static final String RIGHT = ">";
	private static final String LEFT = "<";

	private static Logger log = LogManager.getFormatterLogger(TokenContextTemplate.class.getName());

	private final AbstractOBIETemplate<?> thisTemplate;
	/**
	 * Whether distant supervision is enabled for this template or not. This effects
	 * the way of calculating the factors and features!
	 */
	private final boolean enableDistantSupervision;

	public TokenContextTemplate(OBIERunParameter parameter) {
		super(parameter);
		this.thisTemplate = this;
		this.enableDistantSupervision = parameter.exploreOnOntologyLevel;
	}

	static class Position {
		final public String classOrIndividualName;
		final public int beginTokenIndex;
		final public int endTokenIndex;

		public Position(final String classOrIndividualName, int beginTokenIndex, int endTokenIndex) {
			this.classOrIndividualName = classOrIndividualName;
			this.beginTokenIndex = beginTokenIndex;
			this.endTokenIndex = endTokenIndex;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + beginTokenIndex;
			result = prime * result + ((classOrIndividualName == null) ? 0 : classOrIndividualName.hashCode());
			result = prime * result + endTokenIndex;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Position other = (Position) obj;
			if (beginTokenIndex != other.beginTokenIndex)
				return false;
			if (classOrIndividualName == null) {
				if (other.classOrIndividualName != null)
					return false;
			} else if (!classOrIndividualName.equals(other.classOrIndividualName))
				return false;
			if (endTokenIndex != other.endTokenIndex)
				return false;
			return true;
		}

	}

	class Scope extends OBIEFactorScope {

		final OBIEInstance instance;
		final Set<Position> positions;

		public Scope(final Set<Position> positions, Class<? extends IOBIEThing> entityRootClassType,
				OBIEInstance internalInstance) {
			super(thisTemplate, internalInstance, positions, entityRootClassType);
			this.instance = internalInstance;
			this.positions = positions;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();

		for (TemplateAnnotation entity : state.getCurrentPrediction().getTemplateAnnotations()) {
			addFactorRecursive(factors, state.getInstance().getInstance(), entity.rootClassType,
					entity.getTemplateAnnotation());
		}
		return factors;
	}

	private void addFactorRecursive(List<Scope> factors, OBIEInstance internalInstance,
			Class<? extends IOBIEThing> rootClassType, IOBIEThing obieThing) {

		if (obieThing == null)
			return;

		final Set<Position> positions = new HashSet<>();

		try {
			if (enableDistantSupervision) {
				if (internalInstance.getNamedEntityLinkingAnnotations()
						.containsClassAnnotations(obieThing.getClass())) {
					for (NERLClassAnnotation nera : internalInstance.getNamedEntityLinkingAnnotations()
							.getClassAnnotations(obieThing.getClass())) {
						positions.add(new Position(nera.classType.getSimpleName(),
								internalInstance.charPositionToTokenPosition(nera.onset),
								internalInstance.charPositionToTokenPosition(nera.onset + nera.text.length())));
					}
				}
				if (internalInstance.getNamedEntityLinkingAnnotations()
						.containsIndividualAnnotations(obieThing.getIndividual())) {
					for (NERLIndividualAnnotation nera : internalInstance.getNamedEntityLinkingAnnotations()
							.getIndividualAnnotations(obieThing.getIndividual())) {
						positions.add(new Position(nera.relatedIndividual.name,
								internalInstance.charPositionToTokenPosition(nera.onset),
								internalInstance.charPositionToTokenPosition(nera.onset + nera.text.length())));
					}
				}
			} else {
				if (obieThing.getCharacterOnset() != null && obieThing.getCharacterOffset() != null) {

					forClass: {
						final Class<? extends IOBIEThing> classType = obieThing.getClass();
						final int beginTokenIndex = internalInstance
								.charPositionToTokenPosition(obieThing.getCharacterOnset());
						final int endTokenIndex = internalInstance
								.charPositionToTokenPosition(obieThing.getCharacterOffset());
						positions.add(new Position(classType.getSimpleName(), beginTokenIndex, endTokenIndex));
					}

					forIndividual: {
						final AbstractOBIEIndividual individual = obieThing.getIndividual();
						if (individual == null)
							break forIndividual;
						final int beginTokenIndex = internalInstance
								.charPositionToTokenPosition(obieThing.getCharacterOnset());
						final int endTokenIndex = internalInstance
								.charPositionToTokenPosition(obieThing.getCharacterOffset());
						positions.add(new Position(individual.name, beginTokenIndex, endTokenIndex));
					}
				}
			}

			factors.add(new Scope(positions, rootClassType, internalInstance));
		} catch (Exception e) {
			e.printStackTrace();
			log.warn(obieThing.getTextMention());
			log.warn(obieThing);
			log.warn(obieThing.getClass().getSimpleName() + "->" + obieThing.getTextMention());
			System.exit(1);
		}

		/*
		 * Add factors for object type properties.
		 */
		ReflectionUtils.getDeclaredOntologyFields(obieThing.getClass()).forEach(field -> {
			try {
				if (field.isAnnotationPresent(RelationTypeCollection.class)) {
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
	}

	@Override
	public void computeFactor(Factor<Scope> factor) {

		Vector featureVector = factor.getFeatureVector();

		List<Token> tokens = factor.getFactorScope().instance.getTokens();

		for (Position position : factor.getFactorScope().positions) {

			String className = position.classOrIndividualName;
			int beginTokenIndex = position.beginTokenIndex;
			int endTokenIndex = position.endTokenIndex;

			addContextFeatures(featureVector, tokens, className, beginTokenIndex, endTokenIndex);
		}

	}

	private void addContextFeatures(Vector featureVector, List<Token> tokens, String className, int beginTokenIndex,
			int endTokenIndex) {

		final String[] leftContext = extractLeftContext(tokens, beginTokenIndex);

		final String[] rightContext = extractRightContext(tokens, endTokenIndex);

		getContextFeatures(featureVector, className, leftContext, rightContext);
	}

	private String[] extractLeftContext(List<Token> tokens, int beginTokenIndex) {
		final String[] leftContext = new String[3];

		// 4
		for (int i = 1; i < 4; i++) {
			if (beginTokenIndex - i >= 0) {
				leftContext[i - 1] = tokens.get(beginTokenIndex - i).getText();
			} else {
				break;
			}
		}
		return leftContext;
	}

	private String[] extractRightContext(List<Token> tokens, int endTokenIndex) {
		final String[] rightContext = new String[3];

		// 3
		for (int i = 0; i < 3; i++) {
			if (endTokenIndex + i < tokens.size()) {
				rightContext[i] = tokens.get(endTokenIndex + i).getText();
			} else {
				break;
			}
		}
		return rightContext;

	}

	private static void getContextFeatures(Vector featureVector, final String contextClass, final String[] leftContext,
			final String[] rightContext) {

		final StringBuffer lCs = new StringBuffer();
		final StringBuffer rCs = new StringBuffer();

		for (int i = 0; i < leftContext.length; i++) {
			rCs.setLength(0);
			lCs.insert(0, leftContext[i] + SPLITTER);
			featureVector.set((lCs + LEFT + contextClass + RIGHT + rCs).trim(), true);

			for (int j = 0; j < rightContext.length; j++) {
				rCs.append(SPLITTER).append(rightContext[j]);
				featureVector.set((lCs + LEFT + contextClass + RIGHT + rCs).trim(), true);

			}
		}

		rCs.setLength(0);
		lCs.setLength(0);

		for (int i = 0; i < rightContext.length; i++) {
			lCs.setLength(0);
			rCs.append(SPLITTER).append(rightContext[i]);
			featureVector.set((lCs + LEFT + contextClass + RIGHT + rCs).trim(), true);

			for (int j = 0; j < leftContext.length; j++) {
				lCs.insert(0, leftContext[j] + SPLITTER);
				featureVector.set((lCs + LEFT + contextClass + RIGHT + rCs).trim(), true);

			}

		}
	}

}
