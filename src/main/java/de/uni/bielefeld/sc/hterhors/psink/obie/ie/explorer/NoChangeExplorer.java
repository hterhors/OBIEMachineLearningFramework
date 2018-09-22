package de.uni.bielefeld.sc.hterhors.psink.obie.ie.explorer;

import java.util.Arrays;
import java.util.List;

import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;

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

	public NoChangeExplorer(OBIERunParameter param) throws ClassNotFoundException {
	}

	@Override
	public List<OBIEState> getNextStates(OBIEState previousState) {
		return Arrays.asList(previousState);
	}

}
