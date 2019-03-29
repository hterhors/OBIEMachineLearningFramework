package de.hterhors.obie.ml.corpus.distributor;

public abstract class AbstractConfigBuilder<B extends AbstractConfigBuilder<B>> {
	float corpusSizeFraction = AbstractCorpusDistributor.DEFAULT_CORPUS_SIZE_FRACTION;

	public abstract AbstractCorpusDistributor build();

	public float getCorpusSizeFraction() {
		return corpusSizeFraction;
	}

	public B setCorpusSizeFraction(float corpusSizeFraction) {
		this.corpusSizeFraction = corpusSizeFraction;
		return getDistributor();
	}

	protected abstract B getDistributor();

}
