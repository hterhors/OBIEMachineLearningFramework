package de.hterhors.obie.tools.ml.stopcrit.sampling;

import java.util.List;

import de.hterhors.obie.tools.ml.variables.OBIEState;
import sampling.stoppingcriterion.StoppingCriterion;

public class StopAtMaxObjectiveScore implements StoppingCriterion<OBIEState> {
	final int numberOfSamplingStepsTRAIN;

	public StopAtMaxObjectiveScore(int numberOfSamplingStepsTEST) {
		this.numberOfSamplingStepsTRAIN = numberOfSamplingStepsTEST;
	}

	@Override
	public boolean checkCondition(List<OBIEState> chain, int step) {
		if (chain.isEmpty())
			return false;

		double maxScore = chain.get(chain.size() - 1).getObjectiveScore();
		int count = 0;
		final int maxCount = numberOfSamplingStepsTRAIN / 10;

		for (int i = 0; i < chain.size(); i++) {
			if (chain.get(i).getObjectiveScore() >= maxScore) {
				count++;
			}
		}

		if (step >= numberOfSamplingStepsTRAIN)
			System.err.println("NUMBER OF SAMPLING STEPS EXCEEDED!");

		return count >= maxCount || step >= numberOfSamplingStepsTRAIN
				|| chain.get(chain.size() - 1).getObjectiveScore() == 1;
	}

}
