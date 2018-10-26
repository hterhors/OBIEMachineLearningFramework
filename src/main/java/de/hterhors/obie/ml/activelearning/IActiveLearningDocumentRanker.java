package de.hterhors.obie.ml.activelearning;

import java.util.List;

import de.hterhors.obie.ml.variables.OBIEInstance;

public interface IActiveLearningDocumentRanker {

	List<OBIEInstance> rank(List<OBIEInstance> remainingInstances);

}
