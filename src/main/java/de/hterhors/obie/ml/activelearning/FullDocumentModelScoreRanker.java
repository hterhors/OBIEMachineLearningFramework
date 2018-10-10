package de.hterhors.obie.ml.activelearning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import corpus.SampledInstance;
import de.hterhors.obie.ml.corpus.distributor.ActiveLearningDistributor;
import de.hterhors.obie.ml.run.AbstractOBIERunner;
import de.hterhors.obie.ml.variables.InstanceEntityAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;

public class FullDocumentModelScoreRanker implements IActiveLearningDocumentRanker {

	@Override
	public List<OBIEInstance> rank(ActiveLearningDistributor distributor, AbstractOBIERunner runner,
			List<OBIEInstance> remainingInstances) {

		List<OBIEInstance> rankedInstances = new ArrayList<>();

		List<SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>> predictions = runner
				.test(remainingInstances);

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
