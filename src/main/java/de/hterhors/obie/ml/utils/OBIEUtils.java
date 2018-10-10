package de.hterhors.obie.ml.utils;

import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;

public class OBIEUtils {

	/**
	 * TODO: put in each OBIE class. Override clone() function. Performs a deep
	 * clone on the given object using the constructor.
	 */
	public static IOBIEThing deepClone(IOBIEThing obieThing) {

		if (obieThing == null)
			return null;

		try {
			return IOBIEThing.getCloneConstructor(obieThing.getClass()).newInstance(obieThing);
		} catch (Exception e) {
			System.err.println(obieThing);
			e.printStackTrace();
			System.exit(1);
			throw new IllegalArgumentException(e.getMessage());
		}

	}

}
