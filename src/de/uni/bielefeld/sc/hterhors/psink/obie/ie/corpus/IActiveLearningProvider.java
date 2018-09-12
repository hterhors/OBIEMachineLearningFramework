package de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus;

import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.AbstractOBIERunner;

public interface IActiveLearningProvider {

	public boolean updateALTrainingInstances(AbstractOBIERunner model);

	public int getCurrentActiveLearningIteration();

}
