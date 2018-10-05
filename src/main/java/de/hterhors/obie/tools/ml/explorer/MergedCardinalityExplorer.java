package de.hterhors.obie.tools.ml.explorer;

import java.util.ArrayList;
import java.util.List;

import de.hterhors.obie.tools.ml.run.param.OBIERunParameter;
import de.hterhors.obie.tools.ml.variables.OBIEState;
import sampling.Explorer;

/**
 * This is an implementation of the Explorer interface that merges the
 * individual results of a given list of explorers into a single list of
 * possible successor states.
 * 
 * TODO: parameterize
 * 
 */
public class MergedCardinalityExplorer extends AbstractOBIEExplorer {

	final private Explorer<OBIEState> templateCardinalityExplorer;
	final private Explorer<OBIEState> slotCardinalityExplorer;

	public MergedCardinalityExplorer(OBIERunParameter parameter) {
		this.templateCardinalityExplorer = new TemplateCardinalityExplorer(parameter);

		this.slotCardinalityExplorer = new SlotCardinalityExplorer(parameter);
	}

	@Override
	public List<OBIEState> getNextStates(OBIEState currentState) {
		List<OBIEState> nextStates = new ArrayList<>();
		nextStates.add(currentState);
		for (OBIEState state : templateCardinalityExplorer.getNextStates(currentState)) {
			nextStates.addAll(slotCardinalityExplorer.getNextStates(state));

		}

		return nextStates;
	}

}