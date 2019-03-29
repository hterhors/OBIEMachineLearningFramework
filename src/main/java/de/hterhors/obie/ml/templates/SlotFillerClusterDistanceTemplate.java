package de.hterhors.obie.ml.templates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.ner.NERLClassAnnotation;
import de.hterhors.obie.ml.ner.NERLIndividualAnnotation;
import de.hterhors.obie.ml.run.AbstractOBIERunner;
import de.hterhors.obie.ml.templates.InBetweenContextTemplate.PositionPairContainer;
import de.hterhors.obie.ml.templates.SlotFillerClusterDistanceTemplate.Scope;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.IETmplateAnnotation;
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
public class SlotFillerClusterDistanceTemplate extends AbstractOBIETemplate<Scope> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Logger log = LogManager.getFormatterLogger(SlotFillerClusterDistanceTemplate.class.getName());

	public SlotFillerClusterDistanceTemplate(AbstractOBIERunner runner) {
		super(runner);
	}

	class Scope extends FactorScope {

		public final int dist;
		public final int avgDistance;

		public Scope(Class<? extends IOBIEThing> rootClassType, final int dist, final int avgDistance) {
			super(SlotFillerClusterDistanceTemplate.this, rootClassType, new Integer(dist), new Integer(avgDistance));
			this.dist = dist;
			this.avgDistance = avgDistance;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		for (IETmplateAnnotation entity : state.getCurrentIETemplateAnnotations().getAnnotations()) {

			if (entity == null)
				continue;

			final List<Integer> charPositions = new ArrayList<>();

			entity.getThing().getInvestigatedSlots().forEach(slot -> {
				if (slot != null)
					if (slot.isMultiValueSlot) {
						for (IOBIEThing listObject : slot.getMultiValues()) {
							charPositions.add(listObject.getCharacterOnset());
						}
					} else {
						if (slot.getSingleValue() != null)
							charPositions.add(slot.getSingleValue().getCharacterOnset());
					}
			});

			if (charPositions.isEmpty())
				continue;

			Collections.sort(charPositions);

			final int dist = charPositions.get(charPositions.size() - 1).intValue() - charPositions.get(0).intValue();
			final int avgDistance = dist / charPositions.size();

			factors.add(new Scope(entity.rootClassType, dist, avgDistance));

		}

		return factors;
	}

	@Override
	public void computeFactor(Factor<Scope> factor) {

		Vector featureVector = factor.getFeatureVector();

		final int dist = factor.getFactorScope().dist;
		final int avgDistance = factor.getFactorScope().avgDistance;

//		featureVector.set("DIST = 0-50 ", dist > 0 && dist <= 50);
//		featureVector.set("DIST = 50-100 ", dist > 50 && dist <= 100);
//		featureVector.set("DIST = 100-200 ", dist > 100 && dist <= 200);
//		featureVector.set("DIST = 200-500 ", dist > 200 && dist <= 500);
//		featureVector.set("DIST > 500 ", dist > 500);
//
//		featureVector.set("AVGDIST = 0-10 ", avgDistance > 0 && avgDistance <= 10);
//		featureVector.set("AVGDIST = 10-20 ", avgDistance > 10 && avgDistance <= 20);
//		featureVector.set("AVGDIST = 20-30 ", avgDistance > 20 && avgDistance <= 30);
//		featureVector.set("AVGDIST = 30-50 ", avgDistance > 30 && avgDistance <= 50);
//		featureVector.set("AVGDIST = 50-100 ", avgDistance > 50 && avgDistance <= 100);
//		featureVector.set("AVGDIST > 100 ", avgDistance > 100);

		featureVector.set("DIST < 0 ", dist < 0);
		featureVector.set("DIST < 50 ", dist < 50);
		featureVector.set("DIST < 100 ", dist < 100);
		featureVector.set("DIST < 200 ", dist < 200);
		featureVector.set("DIST < 500 ", dist < 500);

		featureVector.set("AVGDIST < 0 ", avgDistance < 0);
		featureVector.set("AVGDIST < 10 ", avgDistance < 10);
		featureVector.set("AVGDIST < 20 ", avgDistance < 20);
		featureVector.set("AVGDIST < 30 ", avgDistance < 30);
		featureVector.set("AVGDIST < 50 ", avgDistance < 50);
		featureVector.set("AVGDIST < 100 ", avgDistance < 100);

//		featureVector.set("DIST > 0 ", dist > 0);
//		featureVector.set("DIST > 50 ", dist > 50);
//		featureVector.set("DIST > 100 ", dist > 100);
//		featureVector.set("DIST > 200 ", dist > 200);
//		featureVector.set("DIST > 500 ", dist > 500);
//
//		featureVector.set("AVGDIST > 0 ", avgDistance > 0);
//		featureVector.set("AVGDIST > 10 ", avgDistance > 10);
//		featureVector.set("AVGDIST > 20 ", avgDistance > 20);
//		featureVector.set("AVGDIST > 30 ", avgDistance > 30);
//		featureVector.set("AVGDIST > 50 ", avgDistance > 50);
//		featureVector.set("AVGDIST > 100 ", avgDistance > 100);

	}

}