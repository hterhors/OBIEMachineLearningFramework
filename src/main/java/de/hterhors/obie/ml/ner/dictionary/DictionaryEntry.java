package de.hterhors.obie.ml.ner.dictionary;

import de.hterhors.obie.core.ontology.AbstractIndividual;

public class DictionaryEntry {

	final public AbstractIndividual individual;

	final public String surfaceForm;

	public DictionaryEntry(AbstractIndividual individual, String surfaceForm) {
		this.surfaceForm = surfaceForm.toLowerCase();
		this.individual = individual;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((individual == null) ? 0 : individual.hashCode());
		result = prime * result + ((surfaceForm == null) ? 0 : surfaceForm.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DictionaryEntry other = (DictionaryEntry) obj;
		if (individual == null) {
			if (other.individual != null)
				return false;
		} else if (!individual.equals(other.individual))
			return false;
		if (surfaceForm == null) {
			if (other.surfaceForm != null)
				return false;
		} else if (!surfaceForm.equals(other.surfaceForm))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "DictionaryEntry [surfaceForm=" + surfaceForm + ", individual=" + individual + "]";
	}

}
