package de.hterhors.obie.ml.explorer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.InvestigationRestriction;
import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.explorer.utils.ExplorationUtils;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.IETmplateAnnotation;

/**
 * Explores the cardinality of the main template classes.
 * 
 * @author hterhors
 *
 */
public class TemplateCardinalityExplorer extends AbstractOBIEExplorer {

	private static Logger log = LogManager.getFormatterLogger(TemplateCardinalityExplorer.class.getName());

	private final Set<Class<? extends IOBIEThing>> rootClassTypes;

	private final Set<Class<? extends IOBIEThing>> exploreClassesWithoutTextualEvidence;

	private final int maxNumberOfEntityElements;
	private final int maxNumberOfDataTypeElements;

	private final boolean exploreOnOntologyLevel;

	private final Random rnd;
	private final boolean restrictExplorationOnConceptsInInstance;

	/**
	 * changes the cardinality of the annotation-root class. E.g. if you search for
	 * OrganismModles the root class would be OrganismModel. Since this class is not
	 * embedded into a parent class as property with 1:n cardinality it is not
	 * explored by the PropertyCardinalityExplorer.
	 * 
	 * @throws OWLOntologyCreationException
	 * @throws ClassNotFoundException
	 */

	public TemplateCardinalityExplorer(RunParameter param) {
		super(param);
		/*
		 * Get implementation class if input is interface.
		 */
		this.rootClassTypes = param.rootSearchTypes;
		this.maxNumberOfEntityElements = param.maxNumberOfEntityElements;
		this.maxNumberOfDataTypeElements = param.maxNumberOfDataTypeElements;
		this.exploreClassesWithoutTextualEvidence = param.exploreClassesWithoutTextualEvidence;
		this.exploreOnOntologyLevel = param.exploreOnOntologyLevel;
		this.rnd = param.rndForSampling;

		this.restrictExplorationOnConceptsInInstance = param.restrictExplorationToFoundConcepts;
	}

	@Override
	public List<OBIEState> getNextStates(OBIEState previousState) {
		List<OBIEState> generatedStates = new ArrayList<OBIEState>();

		generatedStates.add(previousState);

		/**
		 * TODO: Implement mixed root types. It is not possible at the moment to have
		 * data type classes and non-data type classes as root classes.
		 */
		for (Class<? extends IOBIEThing> rootTemplateType : rootClassTypes) {

			final int size = previousState.getCurrentIETemplateAnnotations().getAnnotations().size();

			if (ReflectionUtils.isAnnotationPresent(rootTemplateType, DatatypeProperty.class)
					&& size >= maxNumberOfDataTypeElements) {
				continue;
			} else if (size >= maxNumberOfEntityElements) {
				continue;
			}

			final Set<IOBIEThing> candidates = ExplorationUtils.getCandidates(previousState.getInstance(),
					rootTemplateType, exploreClassesWithoutTextualEvidence, exploreOnOntologyLevel,
					restrictExplorationOnConceptsInInstance,
					/**
					 * TODO: Test this! Take restriction from gold if exists if not (as in
					 * prediction) get the default of the state / instance.
					 */
					previousState.getInstance().getGoldAnnotation().getAnnotations().iterator().hasNext()
							? previousState.getInstance().getGoldAnnotation().getAnnotations().iterator().next()
									.getThing().getInvestigationRestriction()
							: previousState.getDefaultInvestigationRestriction());

			for (IOBIEThing candidateClass : candidates) {
				final OBIEState generatedState = new OBIEState(previousState);
				generatedState.getCurrentIETemplateAnnotations()
						.addAnnotation(new IETmplateAnnotation(rootTemplateType, candidateClass));
				generatedStates.add(generatedState);

			}

		}

		for (IETmplateAnnotation internalAnnotaton : previousState.getCurrentIETemplateAnnotations()
				.getAnnotations()) {
			final OBIEState generatedState = new OBIEState(previousState);
			generatedState.getCurrentIETemplateAnnotations().removeEntity(internalAnnotaton);
			generatedStates.add(generatedState);
		}

		Collections.shuffle(generatedStates, rnd);

		return generatedStates;
	}

}
