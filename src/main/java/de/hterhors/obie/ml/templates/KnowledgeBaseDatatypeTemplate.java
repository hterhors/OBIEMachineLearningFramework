package de.hterhors.obie.ml.templates;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.core.owlreader.RDFObject;
import de.hterhors.obie.core.owlreader.TripleStoreDatabase;
import de.hterhors.obie.ml.run.AbstractOBIERunner;
import de.hterhors.obie.ml.templates.KnowledgeBaseDatatypeTemplate.Scope;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.IETmplateAnnotation;
import factors.Factor;
import factors.FactorScope;

/**
 * This template creates a prior for each slot filler candidate given the
 * training data. This results in taking always that slot filler candidate that
 * appears most in the training data for its corresponding slot.
 * 
 * For that a feature is created for each assigned slot value class for each
 * property of the currently observed entity annotation.
 * 
 * @author hterhors
 *
 * @see Same as {@link GenericPriorTemplate} but specific for SoccerPlayer
 */
public class KnowledgeBaseDatatypeTemplate extends AbstractOBIETemplate<Scope> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Logger log = LogManager.getFormatterLogger(KnowledgeBaseDatatypeTemplate.class.getName());

	final private static String propertyName1 = "p1";
	final private static String propertyName2 = "p2";

	final private static String query1 = "select distinct ?" + propertyName1 + " where {<%s> ?" + propertyName1
			+ " <%s> . FILTER(STRSTARTS(STR(?" + propertyName1 + "), \"http://dbpedia.org/ontology/\"))}";

	final private static String query2 = "select distinct ?" + propertyName1 + " where {?s ?" + propertyName1
			+ " <%s> . FILTER(STRSTARTS(STR(?" + propertyName1 + "), \"http://dbpedia.org/ontology/\"))}";

	final private static String query3 = "select distinct ?" + propertyName1 + " where {<%s> ?" + propertyName1
			+ " ?o . FILTER(STRSTARTS(STR(?" + propertyName1 + "), \"http://dbpedia.org/ontology/\"))}";

	final private static String query4 = "select distinct ?" + propertyName1 + " ?" + propertyName2 + " where {<%s> ?"
			+ propertyName1 + " ?o. ?o ?" + propertyName2 + " <%s> . FILTER(STRSTARTS(STR(?" + propertyName1
			+ "), \"http://dbpedia.org/ontology/\")) . FILTER(STRSTARTS(STR(?" + propertyName2
			+ "), \"http://dbpedia.org/ontology/\"))} ";

	final private static String query5 = "select distinct ?" + propertyName1 + " ?" + propertyName2 + " where {<%s> ?"
			+ propertyName1 + " ?o. <%s> ?" + propertyName2 + " ?o  . FILTER(STRSTARTS(STR(?" + propertyName1
			+ "), \"http://dbpedia.org/ontology/\")) . FILTER(STRSTARTS(STR(?" + propertyName2
			+ "), \"http://dbpedia.org/ontology/\"))} ";

	private final TripleStoreDatabase db;

	public KnowledgeBaseDatatypeTemplate(AbstractOBIERunner runner) {
		super(runner);

		log.info("Read external knowledgebase into triple store...");
		db = new TripleStoreDatabase(new File("/home/hterhors/git/OBIECore/mappingbased_objects_en.ttl"));

		try {

			for (OBIEInstance obieInstance : runner.corpusProvider.getTestCorpus().getInternalInstances()) {

				for (IETmplateAnnotation ta : obieInstance.getGoldAnnotation().getAnnotations()) {

					IOBIEThing thing = ta.getThing();

//					List<Map<String, RDFObject>> invertQ2Result1 = db.select("SELECT DISTINCT * WHERE { <"
//							+ thing.getIndividual().nameSpace + thing.getIndividual().name + "> ?p ?o }").queryData;
//					invertQ2Result1.forEach(System.out::println);

					for (Field slot : ReflectionUtils.getFields(thing.getClass(), thing.getInvestigationRestriction())) {

						List<IOBIEThing> fillers;
						if (ReflectionUtils.isAnnotationPresent(slot, RelationTypeCollection.class)) {
							fillers = (List<IOBIEThing>) slot.get(thing);
						} else {
							fillers = new ArrayList<>(1);
							IOBIEThing filler = (IOBIEThing) slot.get(thing);
							if (filler != null) {
								fillers.add(filler);
							}
						}

						for (IOBIEThing filler : fillers) {

							db.delete(thing, slot, filler);
						}

					}
//					List<Map<String, RDFObject>> invertQ2Result2 = db.select("SELECT DISTINCT * WHERE { <"
//							+ thing.getIndividual().nameSpace + thing.getIndividual().name + "> ?p ?o }").queryData;
//					invertQ2Result2.forEach(System.out::println);

				}

			}
		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
			System.exit(1);
		}

	}

	class Scope extends FactorScope {

		/**
		 * The parent class type of the obie-template in a parent-child relation.
		 * Otherwise the first child of the pair.
		 */
		final AbstractIndividual value1;

		/**
		 * The class type of the investigated child-property in a parent-child relation.
		 * Otherwise the second child of the pair.
		 */
		final AbstractIndividual value2;

		/**
		 * Whether the values are from the same slot or from different slots.
		 */
		final boolean interSlotComparison;
		/**
		 * The slot name or slot name combination if {@link #interSlotComparison} is
		 * true.
		 */
		final String slotName;

		public Scope(Class<? extends IOBIEThing> entityRootClassType, AbstractIndividual value1, String slotName,
				AbstractIndividual value2, boolean interSlotComparison) {
			super(KnowledgeBaseDatatypeTemplate.this, entityRootClassType, value1, value2, slotName, interSlotComparison);
			this.value1 = value1;
			this.value2 = value2;
			this.slotName = slotName;
			this.interSlotComparison = interSlotComparison;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();
		for (IETmplateAnnotation entity : state.getCurrentIETemplateAnnotations().getAnnotations()) {
			addFactorRecursive(factors, entity.rootClassType, state.getInstance(), entity.getThing());
		}

		return factors;
	}

	private void addFactorRecursive(List<Scope> factors, Class<? extends IOBIEThing> rootClassType,
			OBIEInstance obieInstance, IOBIEThing obieClass) {
		try {
			addFactorRecursive(factors, obieInstance, rootClassType, obieClass);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}

	}

	private void addFactorRecursive(List<Scope> factors, OBIEInstance obieInstance,
			Class<? extends IOBIEThing> rootClassType, final IOBIEThing parentThing)
			throws IllegalArgumentException, IllegalAccessException {

		/*
		 * inter slot relation.
		 * 
		 * For every distinct slot pair do: for every distinct slotValue pair do: add
		 * factor
		 */
		final List<Field> slots = ReflectionUtils.getNonDatatypeSlots(parentThing.getClass(),
				parentThing.getInvestigationRestriction());

		final AbstractIndividual parentIndividual = parentThing.getIndividual();

		if (parentIndividual != null) {
			for (int i = 0; i < slots.size(); i++) {

				final Field slot = slots.get(i);

				final List<IOBIEThing> slot1Values = getFillers(parentThing, slot);

				for (int k = 0; k < slot1Values.size(); k++) {

					final IOBIEThing slotValue1 = slot1Values.get(k);

					if (slotValue1 == null)
						continue;

					final AbstractIndividual slotValue = slotValue1.getIndividual();

					factors.add(
							new Scope(rootClassType, parentIndividual, "<(" + slot.getName() + ")>", slotValue, false));

				}
			}
		}

		{
			for (int i = 0; i < slots.size(); i++) {

				final Field slot1 = slots.get(i);

				final List<IOBIEThing> slot1Values = getFillers(parentThing, slot1);

				for (int j = i + 1; j < slots.size(); j++) {

					final Field slot2 = ReflectionUtils
							.getNonDatatypeSlots(parentThing.getClass(), parentThing.getInvestigationRestriction())
							.get(j);

					final List<IOBIEThing> slot2Values = getFillers(parentThing, slot2);

					for (int k = 0; k < slot1Values.size(); k++) {
						final IOBIEThing slotValue1 = slot1Values.get(k);

						if (slotValue1 == null)
							continue;

						final AbstractIndividual value1 = slotValue1.getIndividual();

						for (int l = 0; l < slot2Values.size(); l++) {
							IOBIEThing slotValue2 = slot2Values.get(l);

							if (slotValue2 == null)
								continue;

							final AbstractIndividual value2 = slotValue2.getIndividual();

							factors.add(new Scope(rootClassType, value1,
									"(" + slot1.getName() + ")<->(" + slot2.getName() + ")", value2, true));
						}
					}
				}
			}
		}

		/*
		 * TODO: kinda inefficient
		 * 
		 * intra-slot relation.
		 * 
		 * For every distinct slot pair do: for every distinct slotValue pair do: add
		 * factor
		 */
		{
			for (int i = 0; i < slots.size(); i++) {

				final Field slot = slots.get(i);

				final List<IOBIEThing> slotValues = getFillers(parentThing, slot);

				if (slotValues.size() < 2)
					continue;

				for (int k = 0; k < slotValues.size() - 1; k++) {
					final IOBIEThing slotValue1 = slotValues.get(k);

					if (slotValue1 == null)
						continue;

					final AbstractIndividual value1 = slotValue1.getIndividual();

					for (int l = k + 1; l < slotValues.size(); l++) {
						IOBIEThing slotValue2 = slotValues.get(l);

						if (slotValue2 == null)
							continue;

						final AbstractIndividual value2 = slotValue2.getIndividual();

						factors.add(new Scope(rootClassType, value1, "<(" + slot.getName() + ")>", value2, false));
					}
				}
			}
		}
	}

	/**
	 * Returns the values of the field of the given object. If the field is not of
	 * type list, a list with one element is created. The returned list can include
	 * null objects.
	 * 
	 * @param childClass
	 * @param field
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private List<IOBIEThing> getFillers(IOBIEThing childClass, final Field field) {
		final List<IOBIEThing> fillers;

		try {
			if (ReflectionUtils.isAnnotationPresent(field, RelationTypeCollection.class)) {
				fillers = (List<IOBIEThing>) field.get(childClass);
			} else {
				fillers = new ArrayList<>(1);
				IOBIEThing filler = (IOBIEThing) field.get(childClass);
				if (filler != null) {
					fillers.add(filler);
				}
			}
			return fillers;
		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}
	}

	@Override
	public void computeFactor(Factor<Scope> factor) {

		final String value1Filler = factor.getFactorScope().value1.nameSpace + factor.getFactorScope().value1.name;
		final String value2Filler = factor.getFactorScope().value2.nameSpace + factor.getFactorScope().value2.name;

		singleProp(factor, query1, "Q1", value1Filler, value2Filler, factor.getFactorScope().interSlotComparison);

		singlePropSingleFiller(factor, query2, "Q2", value1Filler, value2Filler,
				factor.getFactorScope().interSlotComparison);

		singlePropSingleFiller(factor, query3, "Q3", value1Filler, value2Filler,
				factor.getFactorScope().interSlotComparison);

		doubleProp(factor, query4, "Q4", value1Filler, value2Filler, factor.getFactorScope().interSlotComparison);

		doubleProp(factor, query5, "Q5", value1Filler, value2Filler, factor.getFactorScope().interSlotComparison);

	}

	private void doubleProp(Factor<Scope> factor, String query, String context, String value1Filler,
			String value2Filler, final boolean interSlotComparison) {
		List<Map<String, RDFObject>> q2Result = db.select(String.format(query, value1Filler, value2Filler)).queryData;
		for (Map<String, RDFObject> r : q2Result) {
			factor.getFeatureVector()
					.set(context + ": " + factor.getFactorScope().slotName + "->" + r.get(propertyName1).value + "->"
							+ r.get(propertyName2).value + "-interSlotComparison =" + interSlotComparison, true);
		}

		List<Map<String, RDFObject>> invertQ2Result = db
				.select(String.format(query, value2Filler, value1Filler)).queryData;
		for (Map<String, RDFObject> r : invertQ2Result) {
			factor.getFeatureVector().set(
					"Invert-" + context + ": " + factor.getFactorScope().slotName + "->" + r.get(propertyName1).value
							+ "->" + r.get(propertyName2).value + (interSlotComparison ? " interSlot" : " intraSlot"),
					true);
		}
	}

	private void singleProp(Factor<Scope> factor, String query, String context, final String value1Filler,
			final String value2Filler, final boolean interSlotComparison) {
		List<Map<String, RDFObject>> q1Result = db.select(String.format(query, value1Filler, value2Filler)).queryData;
		for (Map<String, RDFObject> r : q1Result) {
			factor.getFeatureVector().set(context + ": " + factor.getFactorScope().slotName + "->"
					+ r.get(propertyName1).value + "-interSlotComparison =" + interSlotComparison, true);
		}

		List<Map<String, RDFObject>> invertQ1Result = db
				.select(String.format(query, value2Filler, value1Filler)).queryData;
		for (Map<String, RDFObject> r : invertQ1Result) {
			factor.getFeatureVector().set("Invert-" + context + ": " + factor.getFactorScope().slotName + "->"
					+ r.get(propertyName1).value + (interSlotComparison ? " interSlot" : " intraSlot"), true);
		}
	}

	private void singlePropSingleFiller(Factor<Scope> factor, String query, String context, final String value1Filler,
			final String value2Filler, final boolean interSlotComparison) {
		List<Map<String, RDFObject>> q1Result = db.select(String.format(query, value1Filler)).queryData;
		for (Map<String, RDFObject> r : q1Result) {
			factor.getFeatureVector().set(context + ": " + factor.getFactorScope().slotName + "->"
					+ r.get(propertyName1).value + "-interSlotComparison =" + interSlotComparison, true);
		}

		List<Map<String, RDFObject>> invertQ1Result = db.select(String.format(query, value2Filler)).queryData;
		for (Map<String, RDFObject> r : invertQ1Result) {
			factor.getFeatureVector().set("Invert-" + context + ": " + factor.getFactorScope().slotName + "->"
					+ r.get(propertyName1).value + (interSlotComparison ? " interSlot" : " intraSlot"), true);
		}
	}

}
