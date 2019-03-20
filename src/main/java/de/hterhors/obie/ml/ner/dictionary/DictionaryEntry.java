package de.hterhors.obie.ml.ner.dictionary;

public class DictionaryEntry {

	final public String type;

	final public String surfaceForm;

	public DictionaryEntry(String type, String surfaceForm) {
		this.surfaceForm = surfaceForm.toLowerCase();
		this.type = type;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((type == null) ? 0 : type.hashCode());
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
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
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
		return "DictionaryEntry [surfaceForm=" + surfaceForm + ", individual=" + type + "]";
	}

}
