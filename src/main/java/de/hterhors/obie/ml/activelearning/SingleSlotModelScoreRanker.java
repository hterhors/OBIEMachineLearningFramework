package de.hterhors.obie.ml.activelearning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import corpus.SampledInstance;
import de.hterhors.obie.core.ontology.InvestigationRestriction;
import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.InvestigationRestriction.RestrictedField;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.explorer.AbstractOBIEExplorer;
import de.hterhors.obie.ml.run.AbstractOBIERunner;
import de.hterhors.obie.ml.run.DefaultSlotFillingRunner;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;

public class SingleSlotModelScoreRanker implements IActiveLearningDocumentRanker {

	RunParameter parameter;

	private final Set<InvestigationRestriction> restrictions = new HashSet<>();
	private AbstractOBIERunner runner;

	public SingleSlotModelScoreRanker(AbstractOBIERunner runner) {
		// clone
		this.parameter = runner.getParameter();
		this.runner = new DefaultSlotFillingRunner(parameter);

		for (Class<? extends IOBIEThing> classType : this.parameter.rootSearchTypes) {

			classType = ReflectionUtils.getImplementationClass(classType);

			List<Set<RestrictedField>> restrictFieldsList = InvestigationRestriction.getFieldRestrictionCombinations(
					classType, InvestigationRestriction.getMainSingleFields(classType));

			for (Set<RestrictedField> set : restrictFieldsList) {
				if (set.size() > 1) {
					/**
					 * TODO: allow more than a single field here: parameterize
					 */
					continue;
				}
				for (int i = 1; i < 3; i++) {
					restrictions.add(new InvestigationRestriction(set, i % 2 == 0));
				}
			}
		}
	}

	class RI implements Comparable<RI> {
		public final RankedInstance ri;
		public final InvestigationRestriction in;

		public RI(RankedInstance ri, InvestigationRestriction in) {
			this.ri = ri;
			this.in = in;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + ((in == null) ? 0 : in.hashCode());
			result = prime * result + ((ri == null) ? 0 : ri.hashCode());
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
			RI other = (RI) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (in == null) {
				if (other.in != null)
					return false;
			} else if (!in.equals(other.in))
				return false;
			if (ri == null) {
				if (other.ri != null)
					return false;
			} else if (!ri.equals(other.ri))
				return false;
			return true;
		}

		@Override
		public int compareTo(RI o) {
			/*
			 * Highest first
			 */
			return -Double.compare(ri.value, o.ri.value);
		}

		private SingleSlotModelScoreRanker getOuterType() {
			return SingleSlotModelScoreRanker.this;
		}

		@Override
		public String toString() {
			return "RI [ri=" + ri + ", in=" + in + "]";
		}

	}

	@Override
	public List<OBIEInstance> rank(List<OBIEInstance> remainingInstances) {

		List<RI> rankedInstances = new ArrayList<>();

//		for (InvestigationRestriction investigationRestriction : restrictions) {
//
//			this.runner.sampler.getExplorers().stream().map(e -> (AbstractOBIEExplorer) e)
//					.forEach(s -> s.setInvestigationRestriction(investigationRestriction));
//
//			List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> predictions = runner
//					.test(remainingInstances);
//
//			for (SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState> sampledInstance : predictions) {
//				rankedInstances.add(new RI(
//						new RankedInstance(sampledInstance.getState().getModelScore(), sampledInstance.getInstance()),
//						investigationRestriction));
//			}
//
//			Collections.sort(rankedInstances);
//
//			rankedInstances.forEach(System.out::println);
//			System.out.println("-----");
//		}
		Collections.sort(rankedInstances);

		rankedInstances.forEach(System.out::println);

		System.exit(1);

		return rankedInstances.stream().map(x -> x.ri.instance).collect(Collectors.toList());
	}

}
