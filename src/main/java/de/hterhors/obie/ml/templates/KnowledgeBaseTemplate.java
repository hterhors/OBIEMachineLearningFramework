package de.hterhors.obie.ml.templates;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.OntologyModelContent;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IDatatype;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.core.owlreader.RDFObject;
import de.hterhors.obie.core.owlreader.TripleStoreDatabase;
import de.hterhors.obie.ml.run.AbstractRunner;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import factors.Factor;
import factors.FactorScope;
import learning.Vector;

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
public class KnowledgeBaseTemplate extends AbstractOBIETemplate<FactorScope> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Logger log = LogManager.getFormatterLogger(KnowledgeBaseTemplate.class.getName());

	public static boolean useQuery1 = true, useQuery2 = true, useQuery3 = true, useQuery4 = true, useQuery5 = true;

	final private static String subjectName1 = "s1";
	final private static String propertyName1 = "p1";
	final private static String propertyName2 = "p2";

	final private static String QUERY1 = "select distinct ?" + propertyName1 + " where { { <%s> ?" + propertyName1
			+ " <%s>} UNION {<%s> ?" + propertyName1 + " <%s>} }";

	final private static String QUERY3 = "select distinct ?" + subjectName1 + " where {?" + subjectName1
			+ " <%s> <%s> .} LIMIT 1";

//	final private static String QUERY6_1 = "ask {?" + subjectName1 + " <%s> <%s>}";

	final private static String QUERY3_domain = "select distinct ?" + subjectName1 + " where {?" + subjectName1
			+ "<%s> <%s> . ?" + subjectName1 + " a <%s> }";

	final private static String QUERY2 = "select distinct ?" + propertyName1 + " ?" + propertyName2 + " where { {<%s> ?"
			+ propertyName1 + " ?o. ?o ?" + propertyName2 + " <%s>} UNION { <%s> ?" + propertyName1 + " ?o. ?o ?"
			+ propertyName2 + " <%s>} } ";

	final private static String QUERY4 = "select distinct ?" + propertyName1 + " ?" + propertyName2 + " where {<%s> ?"
			+ propertyName1 + " ?o. <%s> ?" + propertyName2 + " ?o  } ";

	private TripleStoreDatabase db;

	public KnowledgeBaseTemplate(AbstractRunner runner) {
		super(runner);

		readExternal(runner);
//		readTrainingData(runner);

	}

	private void readTrainingData(AbstractRunner runner) {
		db = new TripleStoreDatabase();

		try {

			for (OBIEInstance obieInstance : runner.corpusProvider.getTrainingCorpus().getInternalInstances()) {

				for (TemplateAnnotation ta : obieInstance.getGoldAnnotation().getTemplateAnnotations()) {

					IOBIEThing thing = ta.getThing();

					for (Field slot : ReflectionUtils.getSlots(thing.getClass(), thing.getInvestigationRestriction())) {

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
							db.add(thing, slot, filler);
						}

					}

				}

			}
		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private void readExternal(AbstractRunner runner) {
		log.info("Read external knowledgebase into triple store...");
		db = new TripleStoreDatabase(new File("/home/hterhors/git/OBIECore/ontology_properties_en.ttl"));
//		db = new TripleStoreDatabase(new File("/home/hterhors/git/OBIECore/knowledgebase_complete.ttl"));
		try {

			for (OBIEInstance obieInstance : runner.corpusProvider.getFullCorpus().getInternalInstances()) {

				for (TemplateAnnotation ta : obieInstance.getGoldAnnotation().getTemplateAnnotations()) {

					IOBIEThing thing = ta.getThing();

//					List<Map<String, RDFObject>> invertQ2Result1 = db.select("SELECT DISTINCT * WHERE { <"
//							+ thing.getIndividual().getURI() + thing.getIndividual().name + "> ?p ?o }").queryData;
//					invertQ2Result1.forEach(System.out::println);

					for (Field slot : ReflectionUtils.getSlots(thing.getClass(), thing.getInvestigationRestriction())) {

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
//					List<Map<String, RDFObject>> invertQ2Result2 = db.select(
//							"SELECT DISTINCT * WHERE { <" + thing.getIndividual().getURI() + "> ?p ?o }").queryData;
//					invertQ2Result2.forEach(System.out::println);

				}

			}
		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	class ObjectPropertyScope extends FactorScope {

		/**
		 * The parent class type of the obie-template in a parent-child relation.
		 * Otherwise the first child of the pair.
		 */
		final AbstractIndividual parentOrFirst;

		/**
		 * The class type of the investigated child-property in a parent-child relation.
		 * Otherwise the second child of the pair.
		 */
		final AbstractIndividual childOrSecond;

		/**
		 * Whether the values are from the same slot or from different slots.
		 */
		final boolean interSlotComparison;
		/**
		 * The first slot name true.
		 */
		final String slotName1;

		final String slotName2;

		public ObjectPropertyScope(Class<? extends IOBIEThing> entityRootClassType, AbstractIndividual value1,
				String slotName1, String slotName2, AbstractIndividual value2, boolean interSlotComparison) {
			super(KnowledgeBaseTemplate.this, entityRootClassType, value1, value2, slotName1, slotName2,
					interSlotComparison);
			this.parentOrFirst = value1;
			this.childOrSecond = value2;
			this.slotName1 = slotName1;
			this.slotName2 = slotName2;
			this.interSlotComparison = interSlotComparison;
		}

	}

	class DatatypePropertyScope extends FactorScope {

		/**
		 * The parent class type of the obie-template in a parent-child relation.
		 * Otherwise the first child of the pair.
		 */
		final String owlClassName;

		/**
		 * The class type of the investigated child-property in a parent-child relation.
		 * Otherwise the second child of the pair.
		 */
		final String slotValue;

		/**
		 * The slot name or slot name combination if {@link #interSlotComparison} is
		 * true.
		 */
		final String slotName;

		public DatatypePropertyScope(Class<? extends IOBIEThing> entityRootClassType, String owlClassName,
				String slotName, String slotValue) {
			super(KnowledgeBaseTemplate.this, entityRootClassType, owlClassName, slotValue, slotName);
			this.owlClassName = owlClassName;
			this.slotValue = slotValue;
			this.slotName = slotName;
		}

	}

	@Override
	public List<FactorScope> generateFactorScopes(OBIEState state) {
		List<FactorScope> factors = new ArrayList<>();
		for (TemplateAnnotation entity : state.getCurrentTemplateAnnotations().getTemplateAnnotations()) {
			addFactorRecursive(factors, entity.rootClassType, state.getInstance(), entity.getThing());
		}

		return factors;
	}

	private void addFactorRecursive(List<FactorScope> factors, Class<? extends IOBIEThing> rootClassType,
			OBIEInstance obieInstance, IOBIEThing obieClass) {
		try {
			addFactorRecursive(factors, obieInstance, rootClassType, obieClass);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}

	}

	private void addFactorRecursive(List<FactorScope> factors, OBIEInstance obieInstance,
			Class<? extends IOBIEThing> rootClassType, final IOBIEThing parentThing)
			throws IllegalArgumentException, IllegalAccessException {

		/*
		 * inter slot relation.
		 * 
		 * For every distinct slot pair do: for every distinct slotValue pair do: add
		 * factor
		 */
		final List<Field> slots = ReflectionUtils.getSlots(parentThing.getClass(),
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

					if (ReflectionUtils.isAnnotationPresent(slot, DatatypeProperty.class)) {

						final String value = ((IDatatype) slotValue1).getInterpretedValue();

						factors.add(new DatatypePropertyScope(rootClassType, parentThing.getONTOLOGY_NAME(),
								slot.getAnnotation(OntologyModelContent.class).ontologyName(), value));

					} else {

						final AbstractIndividual slotValue = slotValue1.getIndividual();

						factors.add(new ObjectPropertyScope(rootClassType, parentIndividual,
								slot.getAnnotation(OntologyModelContent.class).ontologyName(), null, slotValue, false));
					}
				}
			}
		}

		{
			for (int i = 0; i < slots.size(); i++) {

				final Field slot1 = slots.get(i);

				if (ReflectionUtils.isAnnotationPresent(slot1, DatatypeProperty.class))
					continue;

				final List<IOBIEThing> slot1Values = getFillers(parentThing, slot1);

				for (int j = i + 1; j < slots.size(); j++) {

					final Field slot2 = ReflectionUtils
							.getSlots(parentThing.getClass(), parentThing.getInvestigationRestriction()).get(j);

					if (ReflectionUtils.isAnnotationPresent(slot2, DatatypeProperty.class))
						continue;

					final List<IOBIEThing> slot2Values = getFillers(parentThing, slot2);

					for (int k = 0; k < slot1Values.size(); k++) {
						final IOBIEThing slotValue1 = slot1Values.get(k);

						if (slotValue1 == null)
							continue;

						if (ReflectionUtils.isAnnotationPresent(slot1, DatatypeProperty.class)) {
							final String value1 = ((IDatatype) slotValue1).getInterpretedValue();

							for (int l = 0; l < slot2Values.size(); l++) {
								IOBIEThing slotValue2 = slot2Values.get(l);

								if (slotValue2 == null)
									continue;

								if (ReflectionUtils.isAnnotationPresent(slot2, DatatypeProperty.class)) {

									/**
									 * Do not compare two datatype properties.
									 */
								} else {
//
//									final AbstractIndividual value2 = slotValue2.getIndividual();
//
//									factors.add(new DatatypePropertyScope(rootClassType, value2,
//											"(" + slot1.getName() + ")<->(" + slot2.getName() + ")", value1, true));
								}
							}

						} else {

							final AbstractIndividual value1 = slotValue1.getIndividual();

							for (int l = 0; l < slot2Values.size(); l++) {
								IOBIEThing slotValue2 = slot2Values.get(l);

								if (slotValue2 == null)
									continue;

								if (ReflectionUtils.isAnnotationPresent(slot2, DatatypeProperty.class)) {
//									final String value2 = ((IDatatype) slotValue2).getInterpretedValue();
//
//									factors.add(new DatatypePropertyScope(rootClassType, value1,
//											"(" + slot1.getName() + ")<->(" + slot2.getName() + ")", value2, true));
								} else {

									final AbstractIndividual value2 = slotValue2.getIndividual();

									factors.add(new ObjectPropertyScope(rootClassType, value1,
											slot1.getAnnotation(OntologyModelContent.class).ontologyName(),
											slot2.getAnnotation(OntologyModelContent.class).ontologyName(), value2,
											true));
								}
							}
						}

					}
				}
			}
		}
//		if (true)
//			return;
		/*
		 * TODO: kinda inefficient cause getFillers() is called a lot.
		 * 
		 * intra-slot relation.
		 * 
		 * For every distinct slot pair do: for every distinct slotValue pair do: add
		 * factor
		 */
		{
			/**
			 * No datatype-pair queries.
			 */
			for (int i = 0; i < slots.size(); i++) {

				final Field slot = slots.get(i);

				if (ReflectionUtils.isAnnotationPresent(slot, DatatypeProperty.class))
					continue;

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

						factors.add(new ObjectPropertyScope(rootClassType, value1, slot.getName(), slot.getName(),
								value2, false));
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

	final private static String MEAN_STD_DEVIATION_TEMPLATE = "%s of %s is within %s x std deviation(%s) of mean(%s)";
	final private static String NOT_MEAN_STD_DEVIATION_TEMPLATE = "%s of %s is NOT within %s x std deviation (%s) of mean(%s)";

	@Override
	public void computeFactor(Factor<FactorScope> factor) {

		if (factor.getFactorScope() instanceof ObjectPropertyScope) {

			ObjectPropertyScope objectScope = (ObjectPropertyScope) factor.getFactorScope();

			final String parentOrFirst = objectScope.parentOrFirst.getURI();
			final String childOrSecond = objectScope.childOrSecond.getURI();

			/*
			 * Ohne domain PRF1 [tp=2329.0, fp=525.0, fn=707.0, tn=0.0,
			 * getF1()=0.7908319185059423, getAccuracy()=0.6540297669194046,
			 * getRecall()=0.7671277997364954, getPrecision()=0.8160476524176594,
			 * getJaccard()=0.6540297669194046]
			 */

			/*
			 * Mit domain PRF1 [tp=2326.0, fp=533.0, fn=710.0, tn=0.0,
			 * getF1()=0.7891433418150976, getAccuracy()=0.6517231717567946,
			 * getRecall()=0.7661396574440053, getPrecision()=0.813571178733823,
			 * getJaccard()=0.6517231717567946]
			 * 
			 */
			if (useQuery3) {

				String query3;

				query3 = String.format(QUERY3, objectScope.slotName1, childOrSecond);

				if (!db.select(query3).queryData.isEmpty()) {
					factor.getFeatureVector().set("QUERY3: " + objectScope.slotName1 + " not empty", true);
					factor.getFeatureVector()
							.set("QUERY3: " + objectScope.slotName1 + " " + childOrSecond + " not empty", true);

				}
			}
			if (useQuery2) {

				/*
				 * PRF1 [tp=2642.0, fp=392.0, fn=394.0, tn=0.0, getF1()=0.8705107084019769,
				 * getAccuracy()=0.7707117852975496, getRecall()=0.8702239789196311,
				 * getPrecision()=0.8707976268951879, getJaccard()=0.7707117852975496]
				 */
				String query2 = String.format(QUERY2, parentOrFirst, childOrSecond, childOrSecond, parentOrFirst);

				List<Map<String, RDFObject>> result2 = db.select(query2).queryData;

				if (!result2.isEmpty()) {
					factor.getFeatureVector().set("QUERY2: not empty", true);
					factor.getFeatureVector().set(
							"QUERY2: " + objectScope.slotName1 + " & " + objectScope.slotName2 + " not empty", true);
				}

				for (Map<String, RDFObject> map : result2) {
					RDFObject property1 = map.get(propertyName1);
					RDFObject property2 = map.get(propertyName2);
					if (property1 != null && property2 != null) {
						factor.getFeatureVector().set("QUERY2: " + objectScope.slotName1 + " & " + objectScope.slotName2
								+ " * " + property1.value + " " + property2.value + " * not empty", true);
					}
				}
			}
			if (useQuery1) {

				/*
				 * Ohne limit PRF1 [tp=2406.0, fp=544.0, fn=630.0, tn=0.0,
				 * getF1()=0.8038757099899766, getAccuracy()=0.6720670391061453,
				 * getRecall()=0.7924901185770751, getPrecision()=0.8155932203389831,
				 * getJaccard()=0.6720670391061453] //
				 */
				String query1 = String.format(QUERY1, parentOrFirst, childOrSecond, childOrSecond, parentOrFirst);

				List<Map<String, RDFObject>> result1 = db.select(query1).queryData;

				if (!result1.isEmpty()) {
					factor.getFeatureVector().set("QUERY1: not empty", true);
					factor.getFeatureVector().set(
							"QUERY1: dbo:" + objectScope.slotName1 + " & dbo:" + objectScope.slotName2 + " not empty",
							true);
				}

				for (Map<String, RDFObject> map : result1) {
					RDFObject property = map.get(propertyName1);
					if (property != null) {
						factor.getFeatureVector().set("QUERY1: dbo:" + objectScope.slotName1 + " & dbo:"
								+ objectScope.slotName2 + " * " + property.value + " * not empty", true);
					}
				}
			}
			if (useQuery4) {
				String query4 = String.format(QUERY4, parentOrFirst, childOrSecond);

				List<Map<String, RDFObject>> result4 = db.select(query4).queryData;

				if (!result4.isEmpty()) {
					factor.getFeatureVector().set("QUERY4: not empty", true);
					factor.getFeatureVector().set(
							"QUERY4: " + objectScope.slotName1 + " & " + objectScope.slotName2 + " not empty", true);
				}

				for (Map<String, RDFObject> map : result4) {
					RDFObject property1 = map.get(propertyName1);
					RDFObject property2 = map.get(propertyName2);
					if (property1 != null && property2 != null) {
						factor.getFeatureVector().set("QUERY4: " + objectScope.slotName1 + " & " + objectScope.slotName2
								+ " " + property1.value + " " + property2.value + " not empty", true);
					}
				}
			}

		} else if (factor.getFactorScope() instanceof DatatypePropertyScope) {

//			if (true)
//				return;

			if (useQuery5) {

				DatatypePropertyScope datatypeScope = (DatatypePropertyScope) factor.getFactorScope();
//			

				String query = String.format(
						"select distinct ?val where {?individual <%s> ?value . ?individual a <%s> . BIND(STR(?value) AS ?val). } LIMIT 1000",
						datatypeScope.slotName, datatypeScope.owlClassName);

				List<Map<String, RDFObject>> q1Result = db.select(query).queryData;
				MeanDevPair meanDevPair;
				try {
					meanDevPair = getMeanDevPair(datatypeScope.slotName, q1Result);
				} catch (IndexOutOfBoundsException e) {
					return;
				}

				final double distToMean = Math.abs(meanDevPair.mean - Double.parseDouble(datatypeScope.slotValue));

				log.info("meanDevPair: " + meanDevPair);
				log.info("distToMean: " + distToMean);

				for (int i = 0; i < 100; i++) {

					if (distToMean >= i * 10 && distToMean < ((i + 1) * 10))
						factor.getFeatureVector().set(
								"Distance to mean " + datatypeScope.slotName + "->" + String.valueOf(i * 10), true);
				}

//			if (v == Double.valueOf(datatypeScope.slotValue))
//				factor.getFeatureVector().set("Common value for " + datatypeScope.slotName, true);

				for (int i = 10; i >= 1; i--) {

					boolean within = Math.abs(Double.parseDouble(datatypeScope.slotValue) - meanDevPair.mean) <= i
							* 0.001 * meanDevPair.dev;

					/**
					 * Add only the feature which is the farthest away from mean.
					 */
					if (!within) {
						factor.getFeatureVector()
								.set(String.format(NOT_MEAN_STD_DEVIATION_TEMPLATE, datatypeScope.slotName,
										datatypeScope.owlClassName, i, meanDevPair.dev, meanDevPair.mean), !within);
						break;
					}

				}
				for (int i = 1; i <= 10; i++) {

					boolean within = Math.abs(Double.parseDouble(datatypeScope.slotValue) - meanDevPair.mean) <= i
							* 0.001 * meanDevPair.dev;

					/**
					 * Add only the feature which is the nearest to the mean.
					 */
					if (within) {
						factor.getFeatureVector().set(String.format(MEAN_STD_DEVIATION_TEMPLATE, datatypeScope.slotName,
								datatypeScope.owlClassName, i, meanDevPair.dev, meanDevPair.mean), within);
						break;
					}
				}
			}
		}
	}

	private Map<String, MeanDevPair> cache = new HashMap<>();

	class MeanDevPair {

		public final double mean;
		public final double dev;

		public MeanDevPair(double mean, double dev) {
			this.mean = mean;
			this.dev = dev;
		}

	}

	private MeanDevPair getMeanDevPair(final String slotContex, List<Map<String, RDFObject>> qr) {
		MeanDevPair mdp = null;
		if ((mdp = cache.get(slotContex)) != null) {
			return mdp;
		}

		List<Double> values = new ArrayList<>();

		double meanOrMedian = 0;
		for (Map<String, RDFObject> r : qr) {

			RDFObject v;
			if ((v = r.get("val")) == null)
				continue;

			final double dv = Double.parseDouble(v.value);

			values.add(dv);
			meanOrMedian += dv;

		}

//		mean /= values.size();

		Collections.sort(values);
		meanOrMedian = values.get((int) (values.size() / 2));

		double variance = 0;
		for (Double value : values) {
			variance += Math.pow(meanOrMedian - value, 2);
		}

		final double stdDev = Math.sqrt(variance);
		mdp = new MeanDevPair(meanOrMedian, stdDev);
		cache.put(slotContex, mdp);

		return mdp;
	}

	private void doubleProp(Vector vector, ObjectPropertyScope factor, String query, String context,
			String value1Filler, String value2Filler, final boolean interSlotComparison) {
		List<Map<String, RDFObject>> q2Result = db.select(String.format(query, value1Filler, value2Filler)).queryData;
		for (Map<String, RDFObject> r : q2Result) {
			vector.set(context + ": " + factor.slotName1 + "->" + r.get(propertyName1).value + "->"
					+ r.get(propertyName2).value + "-interSlot =" + interSlotComparison, true);
		}

		List<Map<String, RDFObject>> invertQ2Result = db
				.select(String.format(query, value2Filler, value1Filler)).queryData;
		for (Map<String, RDFObject> r : invertQ2Result) {
			vector.set("Invert-" + context + ": " + factor.slotName1 + "->" + r.get(propertyName1).value + "->"
					+ r.get(propertyName2).value + (interSlotComparison ? " interSlot" : " intraSlot"), true);
		}
	}

	private void singleProp(Vector vector, ObjectPropertyScope factor, String query, String context,
			final String value1Filler, final String value2Filler, final boolean interSlotComparison) {
		List<Map<String, RDFObject>> q1Result = db.select(String.format(query, value1Filler, value2Filler)).queryData;
		for (Map<String, RDFObject> r : q1Result) {
			vector.set(context + ": " + factor.slotName1 + "->" + r.get(propertyName1).value + "-interSlot ="
					+ interSlotComparison, true);
		}

		List<Map<String, RDFObject>> invertQ1Result = db
				.select(String.format(query, value2Filler, value1Filler)).queryData;
		for (Map<String, RDFObject> r : invertQ1Result) {
			vector.set("Invert-" + context + ": " + factor.slotName1 + "->" + r.get(propertyName1).value
					+ (interSlotComparison ? " interSlot" : " intraSlot"), true);
		}
	}

	private void singleSingleFiller(Vector vector, ObjectPropertyScope factor, String query, String context,
			final String value1Filler, final String value2Filler, final boolean interSlotComparison) {
		List<Map<String, RDFObject>> q1Result = db.select(String.format(query, value1Filler)).queryData;
		for (Map<String, RDFObject> r : q1Result) {
			vector.set(context + ": " + factor.slotName1 + "->" + r.get(propertyName1).value + "-interSlot ="
					+ interSlotComparison, true);
		}

		List<Map<String, RDFObject>> invertQ1Result = db.select(String.format(query, value2Filler)).queryData;
		for (Map<String, RDFObject> r : invertQ1Result) {
			vector.set("Invert-" + context + ": " + factor.slotName1 + "->" + r.get(propertyName1).value
					+ (interSlotComparison ? " interSlot" : " intraSlot"), true);
		}
	}

}
