package de.hterhors.obie.tools.ml.stopcrit.sampling;

import java.util.List;

import de.hterhors.obie.tools.ml.variables.OBIEState;
import sampling.stoppingcriterion.StoppingCriterion;

public class StopAtRepeatedModelScore implements StoppingCriterion<OBIEState> {

	private final int timesNoChange;

	final int numberOfSamplingStepsTEST;

	public StopAtRepeatedModelScore(int numberOfSamplingStepsTEST, int timesNoChange) {
		this.numberOfSamplingStepsTEST = numberOfSamplingStepsTEST;
		this.timesNoChange = timesNoChange;
	}

	@Override
	public boolean checkCondition(List<OBIEState> chain, int step) {

		if (chain.isEmpty())
			return false;

		double maxScore = chain.get(chain.size() - 1).getModelScore();
		int count = 0;
		final int maxCount = timesNoChange;

		for (int i = 0; i < chain.size(); i++) {
			if (chain.get(i).getModelScore() >= maxScore) {
				count++;
			}
		}
		if (step >= numberOfSamplingStepsTEST) {
			System.err.println("NUMBER OF SAMPLING STEPS EXCEEDED!");
		}

		return count >= maxCount || step >= numberOfSamplingStepsTEST;
	}

}
