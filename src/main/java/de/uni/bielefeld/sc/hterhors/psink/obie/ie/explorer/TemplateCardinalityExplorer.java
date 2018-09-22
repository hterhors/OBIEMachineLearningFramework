package de.uni.bielefeld.sc.hterhors.psink.obie.ie.explorer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.explorer.utils.ExplorationUtils;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.EntityAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;

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

	/**
	 * changes the cardinality of the annotation-root class. E.g. if you search for
	 * OrganismModles the root class would be OrganismModel. Since this class is not
	 * embedded into a parent class as property with 1:n cardinality it is not
	 * explored by the PropertyCardinalityExplorer.
	 * 
	 * @throws OWLOntologyCreationException
	 * @throws ClassNotFoundException
	 */

	public TemplateCardinalityExplorer(OBIERunParameter param) {
		/*
		 * Get implementation class if input is interface.
		 */
		this.rootClassTypes = param.rootSearchTypes;
		this.maxNumberOfEntityElements = param.maxNumberOfEntityElements;
		this.maxNumberOfDataTypeElements = param.maxNumberOfDataTypeElements;
		this.exploreClassesWithoutTextualEvidence = param.exploreClassesWithoutTextualEvidence;
		this.exploreOnOntologyLevel = param.exploreOnOntologyLevel;
		this.rnd = param.rndForSampling;
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

			final int size = previousState.getCurrentPrediction().getEntityAnnotations().size();

			if (rootTemplateType.isAnnotationPresent(DatatypeProperty.class) && size >= maxNumberOfDataTypeElements) {
				continue;
			}

			if (size >= maxNumberOfEntityElements) {
				continue;
			}

			final Set<IOBIEThing> candidates;
			if (exploreOnOntologyLevel) {
				candidates = ExplorationUtils.getSlotTypeCandidates(previousState.getInstance(), rootTemplateType,
						exploreClassesWithoutTextualEvidence);
			} else {
				candidates = ExplorationUtils.getSlotFillerCandidates(previousState.getInstance(), rootTemplateType,
						exploreClassesWithoutTextualEvidence);
			}

			for (IOBIEThing candidateClass : candidates) {
				final OBIEState generatedState = new OBIEState(previousState);
				generatedState.getCurrentPrediction()
						.addAnnotation(new EntityAnnotation(rootTemplateType, candidateClass));
				generatedStates.add(generatedState);

			}

		}

		for (EntityAnnotation internalAnnotaton : previousState.getCurrentPrediction().getEntityAnnotations()) {
			final OBIEState generatedState = new OBIEState(previousState);
			generatedState.getCurrentPrediction().removeEntity(internalAnnotaton);
			generatedStates.add(generatedState);
		}

		Collections.shuffle(generatedStates, rnd);

		return generatedStates;
	}

}
