package de.hterhors.obie.ml.run.param;

public enum EInstantiationType {

	/**
	 * Do not create any initial objects.
	 */
	NONE,

	/**
	 * Initialize with an entity which is provided.
	 */
	SPECIFIED,

	/**
	 * Initialize with an empty object. This is basically just a new instance of the
	 * given searchType.
	 */
	EMPTY,

	/**
	 * Initialize with a random filled object. The randomness factor is defined in
	 * the {@link InitializerHelper}. All fields are filled by possible candidates
	 * from the {@link SamplerHelper}, if there are any.
	 */
	RANDOM,

	/**
	 * Initialize with values that are supposed to be wrong. All fields are set by
	 * there implementation class of the field type, generic field type for lists.
	 * 
	 * TODO: Ensure wrongness by observing the gold annotations.
	 */
	WRONG,

	/**
	 * Initialize with ALL correct annotations based on the given gold annotations.
	 */
	FULL_CORRECT;

}
