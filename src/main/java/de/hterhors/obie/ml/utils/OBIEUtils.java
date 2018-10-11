package de.hterhors.obie.ml.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

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

	/**
	 * Performs a deep clone on the given object using the serialization and
	 * de-serialization. This method can be used to clone collections of classes
	 * that implements Serialization.
	 *
	 * @deprecated Use {@link OBIEUtils#deepClone(IOBIEThing)} instead
	 */
	@Deprecated
	public static IOBIEThing deepSerializeClone(IOBIEThing scioClass) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(scioClass);

			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			return (IOBIEThing) ois.readObject();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
