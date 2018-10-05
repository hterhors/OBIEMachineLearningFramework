package de.hterhors.obie.ml.scorer;

import java.io.File;
import java.io.IOException;

import learning.scorer.Scorer;

public interface IExternalScorer extends Scorer {

	public void loadScorer(File absolutePath) throws IOException;

	public void saveScorer(File modelDile) throws IOException;

	public void train(InstanceCollection featureMapData);

}
