package de.hterhors.obie.tools.ml.explorer;

import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;

public interface IExplorationCondition {

	/**
	 * Checks if the conditions for that specific exploration are fulfilled.
	 * 
	 * @param baseClass
	 *            the parent class to which modification is done.
	 * @param baseClassFieldName
	 *            the name of the field of the parent class that should be
	 *            changed.
	 * @param candidateClass
	 *            the value for that field of the parent class.
	 * @return true if all exploration conditions are fulfilled.
	 */
	public boolean matchesExplorationContitions(IOBIEThing baseClass, String baseClassFieldName,
			IOBIEThing candidateClass);

}
