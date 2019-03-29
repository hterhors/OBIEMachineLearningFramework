package de.hterhors.obie.ml.explorer;

import java.util.Arrays;
import java.util.List;

import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.variables.OBIEState;

/**
 * This explorer is used to apply no changes at all to the states.
 * 
 * This explorer is only used during probability calculation
 * 
 * @author hterhors
 *
 * @date Dec 20, 2017
 */
public class NoChangeExplorer extends AbstractOBIEExplorer {

	public NoChangeExplorer(RunParameter param) throws ClassNotFoundException {
	super(param);
	}

	@Override
	public List<OBIEState> getNextStates(OBIEState previousState) {
		return Arrays.asList(previousState);
	}

}
