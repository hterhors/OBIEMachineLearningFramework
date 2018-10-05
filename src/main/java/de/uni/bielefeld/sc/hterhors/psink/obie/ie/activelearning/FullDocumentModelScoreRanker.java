package de.uni.bielefeld.sc.hterhors.psink.obie.ie.activelearning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import corpus.SampledInstance;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.distributor.ActiveLearningDistributor;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.AbstractOBIERunner;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.InstanceEntityAnnotations;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;

public class FullDocumentModelScoreRanker implements IActiveLearningDocumentRanker {

	@Override
	public List<OBIEInstance> rank(ActiveLearningDistributor distributor, AbstractOBIERunner runner,
			List<OBIEInstance> remainingInstances) {

		List<OBIEInstance> rankedInstances = new ArrayList<>();

		List<SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>> predictions = runner
				.applyModelTo(remainingInstances);

		Collections.sort(predictions,
				new Comparator<SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>>() {

					@Override
					public int compare(SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState> o1,
							SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState> o2) {
						return Double.compare(o1.getState().getModelScore(), o2.getState().getModelScore());
					}
				});

		for (SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState> sampledInstance : predictions) {
			rankedInstances.add(sampledInstance.getInstance());
		}

		return rankedInstances;
	}

}
