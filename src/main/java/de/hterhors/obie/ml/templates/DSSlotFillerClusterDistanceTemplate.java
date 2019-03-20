package de.hterhors.obie.ml.templates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.ner.NERLIndividualAnnotation;
import de.hterhors.obie.ml.run.AbstractOBIERunner;
import de.hterhors.obie.ml.templates.DSSlotFillerClusterDistanceTemplate.Scope;
import de.hterhors.obie.ml.variables.IETmplateAnnotation;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import factors.Factor;
import factors.FactorScope;
import learning.Vector;

/**
 * This template creates features capturing clusters of annotations.
 *
 * While distant supervision is required:
 * 
 * computes the minimal, avg, max cluster-distance among all variations of
 * filled entity types.
 * 
 * if not:
 * 
 * computes the cluster distance of the actual filled entity annotations.
 * 
 * @author hterhors
 *
 */
public class DSSlotFillerClusterDistanceTemplate extends AbstractOBIETemplate<Scope> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Logger log = LogManager.getFormatterLogger(DSSlotFillerClusterDistanceTemplate.class.getName());

	public DSSlotFillerClusterDistanceTemplate(AbstractOBIERunner runner) {
		super(runner);
	}

	class Scope extends FactorScope {

		OBIEInstance instance;

		List<AbstractIndividual> slotFillerEntitiyTypes;

		public Scope(Class<? extends IOBIEThing> rootClassType, List<AbstractIndividual> slotFillerEntitiyTypes,
				OBIEInstance instance) {
			super(DSSlotFillerClusterDistanceTemplate.this, rootClassType, slotFillerEntitiyTypes, instance);
			this.slotFillerEntitiyTypes = slotFillerEntitiyTypes;
			this.instance = instance;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		for (IETmplateAnnotation entity : state.getCurrentIETemplateAnnotations().getAnnotations()) {

			if (entity == null)
				continue;

			final List<AbstractIndividual> slotFillerEntities = new ArrayList<>();

			entity.getThing().getInvestigatedSlots().forEach(slot -> {
				if (slot != null)
					if (slot.isMultiValueSlot) {
						for (IOBIEThing listObject : slot.getMultiValues()) {
							slotFillerEntities.add(listObject.getIndividual());
						}
					} else {
						if (slot.getSingleValue() != null)
							slotFillerEntities.add(slot.getSingleValue().getIndividual());
					}
			});

			if (slotFillerEntities.isEmpty())
				continue;

			factors.add(new Scope(entity.rootClassType, slotFillerEntities, state.getInstance()));

		}

		return factors;
	}

	@Override
	public void computeFactor(Factor<Scope> factor) {

		Vector featureVector = factor.getFeatureVector();

		List<AbstractIndividual> slotFillerEntitiyTypes = factor.getFactorScope().slotFillerEntitiyTypes;

		Map<AbstractIndividual, List<Integer>> positions = new HashMap<>();

		for (AbstractIndividual individual : slotFillerEntitiyTypes) {
			if (individual == null)
				continue;
			positions.putIfAbsent(individual, new ArrayList<>());
			for (NERLIndividualAnnotation entityAnnotation : factor.getFactorScope().instance.getEntityAnnotations()
					.getIndividualAnnotations(individual)) {
				positions.get(individual).add(new Integer(entityAnnotation.onset));
			}
		}

		if (positions.size() <= 1)
			return;

		List<List<Integer>> all_Lists = new ArrayList<>();

		for (Entry<AbstractIndividual, List<Integer>> position : positions.entrySet()) {
			all_Lists.add(position.getValue());
		}

		List<List<Integer>> perm = permute(null, all_Lists);
		int minDist = Integer.MAX_VALUE;
		int minAvgDist = Integer.MAX_VALUE;
		for (List<Integer> charPositions : perm) {

			Collections.sort(charPositions);

			final int dist = charPositions.get(charPositions.size() - 1).intValue() - charPositions.get(0).intValue();
			final int avgDistance = dist / charPositions.size();

			minDist = Math.min(minDist, dist);
			minAvgDist = Math.min(minAvgDist, avgDistance);

		}

//		featureVector.set("MIN DIST < 0 ", minDist < 0);
//		featureVector.set("MIN DIST < 50 ", minDist < 50);
//		featureVector.set("MIN DIST < 100 ", minDist < 100);
//		featureVector.set("MIN DIST < 200 ", minDist < 200);
//		featureVector.set("MIN DIST < 500 ", minDist < 500);
//
//		featureVector.set("MIN AVGDIST < 0 ", minAvgDist < 0);
//		featureVector.set("MIN AVGDIST < 10 ", minAvgDist < 10);
//		featureVector.set("MIN AVGDIST < 20 ", minAvgDist < 20);
//		featureVector.set("MIN AVGDIST < 30 ", minAvgDist < 30);
//		featureVector.set("MIN AVGDIST < 50 ", minAvgDist < 50);
//		featureVector.set("MIN AVGDIST < 100 ", minAvgDist < 100);

//		featureVector.set("MIN DIST = 0-50 ", minDist > 0 && minDist <= 50);
//		featureVector.set("MIN DIST = 50-100 ", minDist > 50 && minDist <= 100);
//		featureVector.set("MIN DIST = 100-200 ", minDist > 100 && minDist <= 200);
//		featureVector.set("MIN DIST = 200-500 ", minDist > 200 && minDist <= 500);
//		featureVector.set("MIN DIST > 500 ", minDist > 500);
//
//		featureVector.set("MIN AVGDIST = 0-10 ", minAvgDist > 0 && minAvgDist <= 10);
//		featureVector.set("MIN AVGDIST = 10-20 ", minAvgDist > 10 && minAvgDist <= 20);
//		featureVector.set("MIN AVGDIST = 20-30 ", minAvgDist > 20 && minAvgDist <= 30);
//		featureVector.set("MIN AVGDIST = 30-50 ", minAvgDist > 30 && minAvgDist <= 50);
//		featureVector.set("MIN AVGDIST = 50-100 ", minAvgDist > 50 && minAvgDist <= 100);
//		featureVector.set("MIN AVGDIST > 100 ", minAvgDist > 100);
	
		featureVector.set("MIN DIST > 0 ", minDist > 0);
		featureVector.set("MIN DIST > 50 ", minDist > 50);
		featureVector.set("MIN DIST > 100 ", minDist > 100);
		featureVector.set("MIN DIST > 200 ", minDist > 200);
		featureVector.set("MIN DIST > 500 ", minDist > 500);

		featureVector.set("MIN AVGDIST > 0 ", minAvgDist > 0);
		featureVector.set("MIN AVGDIST > 10 ", minAvgDist > 10);
		featureVector.set("MIN AVGDIST > 20 ", minAvgDist > 20);
		featureVector.set("MIN AVGDIST > 30 ", minAvgDist > 30);
		featureVector.set("MIN AVGDIST > 50 ", minAvgDist > 50);
		featureVector.set("MIN AVGDIST > 100 ", minAvgDist > 100);
		
	}

	static List<List<Integer>> permute(Integer val, List<List<Integer>> all_Lists) {

		List<List<Integer>> permuteOneList;
		List<List<Integer>> permuteSeveralLists = new ArrayList<>();

		if (all_Lists.size() != 1) {
			for (int i = 0; i < all_Lists.get(0).size(); i++) {
				permuteOneList = permute(all_Lists.get(0).get(i),
						new ArrayList<>(all_Lists.subList(1, all_Lists.size())));
				if (val != null) {
					List<Integer> comb;
					for (int j = 0; j < permuteOneList.size(); j++) {
						comb = permuteOneList.get(j);
						comb.add(0, val);
						permuteSeveralLists.add(comb);
					}
				} else {
					permuteSeveralLists.addAll(permuteOneList);
				}
			}
			return permuteSeveralLists;
		} else {
			permuteOneList = new ArrayList<>();
			for (int i = 0; i < all_Lists.get(0).size(); i++) {
				List<Integer> comb = new ArrayList<>();
				if (val == null) {
					comb.add(all_Lists.get(0).get(i));
					permuteOneList.add(comb);
				} else {
					comb.add(val);
					comb.add(all_Lists.get(0).get(i));
					permuteOneList.add(comb);
				}
			}
			return permuteOneList;
		}

	}

}
