package de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.tokenizer.Token;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.TokenContextTemplate.Scope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.scope.OBIEFactorScope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.utils.ReflectionUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.TemplateAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.NERLClassAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;
import factors.Factor;
import learning.Vector;

public class TokenContextTemplate extends AbstractOBIETemplate<Scope> {

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
		final public Class<? extends IOBIEThing> classType;
		final public int beginTokenIndex;
		final public int endTokenIndex;

		public Position(Class<? extends IOBIEThing> classType, int beginTokenIndex, int endTokenIndex) {
			super();
			this.classType = classType;
			this.beginTokenIndex = beginTokenIndex;
			this.endTokenIndex = endTokenIndex;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + beginTokenIndex;
			result = prime * result + ((classType == null) ? 0 : classType.hashCode());
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
			if (classType == null) {
				if (other.classType != null)
					return false;
			} else if (!classType.equals(other.classType))
				return false;
			if (endTokenIndex != other.endTokenIndex)
				return false;
			return true;
		}

	}

	class Scope extends OBIEFactorScope {

		final OBIEInstance instance;
		final Set<Position> positions;

		public Scope(Set<Class<? extends IOBIEThing>> influencedVariable, final Set<Position> positions,
				Class<? extends IOBIEThing> entityRootClassType, OBIEInstance internalInstance) {
			super(influencedVariable, entityRootClassType, thisTemplate, internalInstance, positions,
					entityRootClassType);
			this.instance = internalInstance;
			this.positions = positions;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();

		for (TemplateAnnotation entity : state.getCurrentPrediction().getTemplateAnnotations()) {
			addFactorRecursive(factors, state.getInstance().getInstance(), entity.rootClassType,
					entity.get());
		}
		return factors;
	}

	private void addFactorRecursive(List<Scope> factors, OBIEInstance internalInstance,
			Class<? extends IOBIEThing> rootClassType, IOBIEThing scioClass) {

		if (scioClass == null)
			return;

		final Set<Position> positions = new HashSet<>();

		try {
			if (enableDistantSupervision) {
				if (internalInstance.getNamedEntityLinkingAnnotations().containsClassAnnotations(scioClass.getClass())) {
					for (NERLClassAnnotation nera : internalInstance.getNamedEntityLinkingAnnotations()
							.getClassAnnotations(scioClass.getClass())) {
						positions.add(new Position(nera.classType,
								internalInstance.charPositionToTokenPosition(nera.onset),
								internalInstance.charPositionToTokenPosition(nera.onset + nera.text.length())));
					}
				}
			} else {
				if (scioClass.getCharacterOnset() != null && scioClass.getCharacterOffset() != null) {
					final Class<? extends IOBIEThing> classType = scioClass.getClass();
					final int beginTokenIndex = internalInstance
							.charPositionToTokenPosition(scioClass.getCharacterOnset());
					final int endTokenIndex = internalInstance
							.charPositionToTokenPosition(scioClass.getCharacterOffset());
					positions.add(new Position(classType, beginTokenIndex, endTokenIndex));
				}
			}

			final Set<Class<? extends IOBIEThing>> influencedVariables = new HashSet<>();
			// influencedVariables.add(scioClass.getClass());

			factors.add(new Scope(influencedVariables, positions, rootClassType, internalInstance));
		} catch (Exception e) {
			e.printStackTrace();
			log.warn(scioClass.getTextMention());
			log.warn(scioClass);
			log.warn(scioClass.getClass().getSimpleName() + "->" + scioClass.getTextMention());
			System.exit(1);
		}

		/*
		 * Add factors for object type properties.
		 */
		ReflectionUtils.getDeclaredOntologyFields(scioClass.getClass()).forEach(field -> {
			try {
				if (field.isAnnotationPresent(RelationTypeCollection.class)) {
					for (IOBIEThing element : (List<IOBIEThing>) field.get(scioClass)) {
						addFactorRecursive(factors, internalInstance, rootClassType, element);
					}
				} else {
					addFactorRecursive(factors, internalInstance, rootClassType, (IOBIEThing) field.get(scioClass));
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

			String className = position.classType.getSimpleName();
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
