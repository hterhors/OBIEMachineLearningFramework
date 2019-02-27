package de.hterhors.obie.ml.corpus;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import de.hterhors.obie.core.ontology.InvestigationRestriction;
import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.core.tools.corpus.CorpusFileTools;
import de.hterhors.obie.core.tools.corpus.OBIECorpus;
import de.hterhors.obie.core.tools.corpus.OBIECorpus.Instance;
import de.hterhors.obie.ml.activelearning.IActiveLearningDocumentRanker;
import de.hterhors.obie.ml.corpus.distributor.AbstractCorpusDistributor;
import de.hterhors.obie.ml.corpus.distributor.ActiveLearningDistributor;
import de.hterhors.obie.ml.corpus.distributor.FoldCrossCorpusDistributor;
import de.hterhors.obie.ml.ner.INamedEntitityLinker;
import de.hterhors.obie.ml.ner.NamedEntityLinkingAnnotations;
import de.hterhors.obie.ml.run.AbstractRunner;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import de.hterhors.obie.ml.variables.OBIEInstance.EInstanceType;
import learning.Trainer;

/**
 * This class provide the corpora for training, development and test data.
 * 
 * @author hterhors
 *
 * @date Oct 16, 2017
 */
public class BigramCorpusProvider implements IFoldCrossProvider, IActiveLearningProvider, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final int COULD_NOT_LOAD_MODEL_ERROR = -1293856;

	protected static Logger log = LogManager.getFormatterLogger(BigramCorpusProvider.class);

	/**
	 * The configuration for the corpus.
	 */
	transient private AbstractCorpusDistributor distributer;

	private final Set<String> originalTrainingInstances;
	private final Set<String> originalDevelopmentInstances;
	private final Set<String> originalTestInstances;

	/**
	 * Training, development, test corpus.
	 */
	transient public BigramInternalCorpus trainingCorpus;
	transient public BigramInternalCorpus developmentCorpus;
	transient public BigramInternalCorpus testCorpus;

	protected final Set<String> errors = new HashSet<>();

	/**
	 * This corpus contains all instances from training, development and test data.
	 */
	transient protected BigramInternalCorpus remainingFullCorpus;

	/**
	 * All internal instances that were converted from the raw corpus.
	 */
	public final List<OBIEInstance> allExistingInternalInstances = new ArrayList<>();

	public transient int currentFold = -1;

	public Set<String> getOriginalTrainingInstances() {
		return Collections.unmodifiableSet(originalTrainingInstances);
	}

	public Set<String> getOriginalDevelopInstances() {
		return Collections.unmodifiableSet(originalDevelopmentInstances);
	}

	public Set<String> getOriginalTestInstances() {
		return Collections.unmodifiableSet(originalTestInstances);
	}

	transient public int currentActiveLearningItertion = 0;

	private final Set<Class<? extends IOBIEThing>> originalRootClasses;

	public Set<Class<? extends IOBIEThing>> getOriginalRootClasses() {
		return Collections.unmodifiableSet(originalRootClasses);
	}

	public BigramCorpusProvider(final File rawCorpusFile, Set<INamedEntitityLinker> entityLinker) {
		log.info("Start creating corpus...");

		log.info("Read raw corpus from file system: " + rawCorpusFile);
		OBIECorpus rawCorpus;
		try {
			rawCorpus = OBIECorpus.readRawCorpusData(rawCorpusFile);

			originalTrainingInstances = new HashSet<>(rawCorpus.getTrainingInstances().keySet());
			originalDevelopmentInstances = new HashSet<>(rawCorpus.getDevelopInstances().keySet());
			originalTestInstances = new HashSet<>(rawCorpus.getTestInstances().keySet());
			originalRootClasses = new HashSet<>(rawCorpus.getRootClasses());
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Could not load corpus: " + e.getMessage());
		}

		log.info("Provided Named Enitity Recognition and Linking tools: ");
		entityLinker.forEach(log::info);

		log.info("Apply " + entityLinker.size() + " NEL-Tools to  " + rawCorpus.getAllInstanceNames().size()
				+ " instances...");

		AtomicInteger countEntities = new AtomicInteger();
		AtomicInteger progress = new AtomicInteger();
		final int numOfInstances = rawCorpus.getInstances().size();

		final Double totalLength = rawCorpus.getInstances().values().stream()
				.map(i -> Double.valueOf(i.content.length())).reduce(new Double(0), Double::sum);

		AtomicLong timeConsumed = new AtomicLong(0);
		AtomicLong lengthConsumed = new AtomicLong(0);

		for (Instance instance : rawCorpus.getInstances().values()) {

			OBIEInstance internalInstance = convertToInternalInstances(instance);

			allExistingInternalInstances.add(internalInstance);

			NamedEntityLinkingAnnotations.Builder annotationbuilder = new NamedEntityLinkingAnnotations.Builder();

			log.info("Annotate " + internalInstance.getName() + ", length: " + internalInstance.getContent().length());
			long t = System.currentTimeMillis();
			for (INamedEntitityLinker l : entityLinker) {
				log.info("Apply: " + l.getClass().getSimpleName() + " to: " + internalInstance.getName());
				annotationbuilder.addClassAnnotations(
						l.annotateClasses(internalInstance.getName(), internalInstance.getContent()));
				annotationbuilder.addIndividualAnnotations(
						l.annotateIndividuals(internalInstance.getName(), internalInstance.getContent()));

			}
			internalInstance.setAnnotations(annotationbuilder.build());

			final long tc = (System.currentTimeMillis() - t);

			log.info("Found " + internalInstance.getNamedEntityLinkingAnnotations().numberOfTotalAnnotations()
					+ " in instance: " + internalInstance.getName() + " in " + tc + " ms.");

			if (log.isDebugEnabled()) {
				log.debug(internalInstance.getNamedEntityLinkingAnnotations());
			}

			timeConsumed.addAndGet(tc);
			lengthConsumed.addAndGet(internalInstance.getContent().length());
			countEntities.addAndGet(internalInstance.getNamedEntityLinkingAnnotations().numberOfTotalAnnotations());

			final long estimedRemainignTime = (long) (((totalLength.doubleValue() - (double) lengthConsumed.get())
					/ (double) lengthConsumed.get()) * (double) timeConsumed.get());

			log.info("Progress " + progress.addAndGet(1) + "/" + numOfInstances + ", estimated remaining time: "
					+ estimedRemainignTime + " ms.");
			log.info("___");
		}

		log.info("Sucessfully applied all NEL-tools. Found " + countEntities.get() + " entities in "
				+ rawCorpus.getAllInstanceNames().size() + " instances");

		log.info("Check for errors...");

		checkAnnotationConsistencies(allExistingInternalInstances);

		if (errors.isEmpty())
			log.info("No errors found");
		else {
			log.warn(errors.size() + " erros found:");
			errors.forEach(log::warn);
		}
		{
			entityLinker = null;
			rawCorpus = null;
			System.gc();
		}
	}

	/**
	 * Loops over all annotations and checks their consistency with different
	 * strategies.
	 * 
	 * @param trainingDocuments
	 */
	private void checkAnnotationConsistencies(final List<OBIEInstance> trainingDocuments) {
		for (OBIEInstance internalInstance : trainingDocuments) {
			for (TemplateAnnotation internalAnnotation : internalInstance.getGoldAnnotation()
					.getTemplateAnnotations()) {
				checkForTextualAnnotations(internalAnnotation.getThing(), internalInstance.getName(),
						internalInstance.getContent());
			}
		}
	}

	/**
	 * Checks if all textual annotations can be found in the document.
	 * 
	 * @param annotation
	 * @param instanceName
	 * @param content
	 */
	@SuppressWarnings("unchecked")
	private void checkForTextualAnnotations(IOBIEThing annotation, final String instanceName, final String content) {
		if (annotation == null)
			return;

		if (ReflectionUtils.isAnnotationPresent(annotation.getClass(), DatatypeProperty.class)) {

			String surfaceForm = annotation.getTextMention();
			if (surfaceForm == null)
				log.warn("Text mention of annotation is null!");
			else if (!content.toLowerCase().contains(surfaceForm.toLowerCase())) {
				final String error = "Could not find: " + surfaceForm + " in " + instanceName;
				errors.add(error);
				log.error(error + "\nContent: " + content + "\n");
			}

		}

		ReflectionUtils.getSlots(annotation.getClass()).forEach(field -> {
			try {
				if (ReflectionUtils.isAnnotationPresent(field, RelationTypeCollection.class)) {
					for (IOBIEThing t : (List<IOBIEThing>) field.get(annotation)) {
						checkForTextualAnnotations(t, instanceName, content);
					}
				} else {
					checkForTextualAnnotations((IOBIEThing) field.get(annotation), instanceName, content);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	private BigramCorpusProvider applyParameterToCorpus(RunParameter parameter) {
		log.info("Apply parameter to corpus...");

		if (this.distributer != null)
			throw new IllegalStateException("Can not override corpus distribution configuration once it is set!");

		log.info("Set corpus distributor to: " + parameter.corpusDistributor.getDistributorID());
		this.distributer = parameter.corpusDistributor;

		log.info("\"remove empty documents\"-flag was set to: " + parameter.excludeEmptyInstancesFromCorpus);
		log.info("\"maximum number of annotations\" was set to: " + parameter.maxNumberOfEntityElements);

		final int totalNumberOfInstances = this.allExistingInternalInstances.size();
		int count = 0;
		log.info("Apply filter...");
		for (Iterator<OBIEInstance> it = this.allExistingInternalInstances.iterator(); it.hasNext();) {
			OBIEInstance internalInstance = (OBIEInstance) it.next();

			count += internalInstance.getTokens().size();

			if (parameter.excludeEmptyInstancesFromCorpus
					&& internalInstance.getGoldAnnotation().getTemplateAnnotations().isEmpty()) {
				log.debug("No annotation data found!" + " Remove empty document " + internalInstance.getName()
						+ " from corpus.");
				it.remove();
				continue;
			}

			if (internalInstance.getGoldAnnotation().getTemplateAnnotations()
					.size() > parameter.maxNumberOfEntityElements) {
				log.debug("Number of annotations = "
						+ internalInstance.getGoldAnnotation().getTemplateAnnotations().size() + " exceeds limit of: "
						+ parameter.maxNumberOfEntityElements + "! Remove document " + internalInstance.getName()
						+ " from corpus.");
				it.remove();
				continue;
			}

			for (TemplateAnnotation annotation : internalInstance.getGoldAnnotation().getTemplateAnnotations()) {

				if (!testLimitToAnnnotationElementsRecursively(annotation.getThing(),
						parameter.maxNumberOfEntityElements, parameter.maxNumberOfDataTypeElements)) {
					log.debug("Number of elements in annotation exceeds limit of: "
							+ parameter.maxNumberOfEntityElements + " for object property OR "
							+ parameter.maxNumberOfDataTypeElements + " for datatype property" + "!Remove annotation "
							+ internalInstance.getName() + " from corpus.");
					it.remove();
					continue;
				}
			}
		}

		log.info("Apply investigation restriction from parameter to gold data...");
		for (Iterator<OBIEInstance> it = this.allExistingInternalInstances.iterator(); it.hasNext();) {
			for (TemplateAnnotation annotation : it.next().getGoldAnnotation().getTemplateAnnotations()) {
				setRestrictionRec(annotation.getThing(), parameter.defaultTestInvestigationRestriction);
			}
		}

		if (totalNumberOfInstances != this.allExistingInternalInstances.size()) {
			log.warn(
					"Found instances that violates given restrictions. Set log-level to debug for more details. Remove affected instances from corpus!");
		}

		log.info("Remaining number of instances: " + this.allExistingInternalInstances.size() + "/"
				+ totalNumberOfInstances);

		log.info("Distribute instances...");

		final List<OBIEInstance> trainingDocuments = new ArrayList<>();
		final List<OBIEInstance> developmentDocuments = new ArrayList<>();
		final List<OBIEInstance> testDocuments = new ArrayList<>();

		distributer.distributeInstances(this).distributeTrainingInstances(trainingDocuments)
				.distributeDevelopmentInstances(developmentDocuments).distributeTestInstances(testDocuments);

		trainingDocuments.forEach(d -> d.setInstanceType(EInstanceType.TRAIN));
		developmentDocuments.forEach(d -> d.setInstanceType(EInstanceType.DEV));
		testDocuments.forEach(d -> d.setInstanceType(EInstanceType.TEST));

		this.currentFold = -1;
		this.currentActiveLearningItertion = 0;

		this.trainingCorpus = new BigramInternalCorpus(trainingDocuments);
		this.developmentCorpus = new BigramInternalCorpus(developmentDocuments);
		this.testCorpus = new BigramInternalCorpus(testDocuments);

		this.remainingFullCorpus = new BigramInternalCorpus(trainingCorpus, developmentCorpus, testCorpus);

		log.info("Distributed instances: ~"
				+ (Math.round((float) this.remainingFullCorpus.getInternalInstances().size()
						/ (float) totalNumberOfInstances * 100))
				+ "% (" + this.remainingFullCorpus.getInternalInstances().size() + "/" + totalNumberOfInstances + ")");

		if (distributer instanceof FoldCrossCorpusDistributor) {
			final AtomicInteger i = new AtomicInteger(0);
			log.debug("Instances (" + this.remainingFullCorpus.getInternalInstances().size() + "):\n"
					+ this.remainingFullCorpus.getInternalInstances().stream().map(d -> {
						return d.getName() + " " + (i.incrementAndGet() % 10 == 0 ? "\n" : "");
					}).reduce("", String::concat));
		} else {
			final AtomicInteger i = new AtomicInteger(0);
			log.info("Training instances: ~"
					+ (Math.round((float) trainingDocuments.size()
							/ (float) this.remainingFullCorpus.getInternalInstances().size() * 100))
					+ "% (" + trainingDocuments.size() + "/" + this.remainingFullCorpus.getInternalInstances().size()
					+ ")");

			if (log.isDebugEnabled()) {
				log.debug(trainingDocuments.stream().map(d -> {
					return d.getName() + " " + (i.incrementAndGet() % 10 == 0 ? "\n" : "");
				}).reduce("", String::concat));
				i.set(0);
			}
			log.info("Development instances: ~"
					+ (Math.round((float) developmentDocuments.size()
							/ (float) this.remainingFullCorpus.getInternalInstances().size() * 100))
					+ "% (" + developmentDocuments.size() + "/" + this.remainingFullCorpus.getInternalInstances().size()
					+ ")");
			if (log.isDebugEnabled()) {
				log.debug(developmentDocuments.stream().map(d -> {
					return d.getName() + " " + (i.incrementAndGet() % 10 == 0 ? "\n" : "");
				}).reduce("", String::concat));
				i.set(0);
			}
			log.info("Test instances: ~"
					+ (Math.round((float) testDocuments.size()
							/ (float) this.remainingFullCorpus.getInternalInstances().size() * 100))
					+ "% (" + testDocuments.size() + "/" + this.remainingFullCorpus.getInternalInstances().size()
					+ ")");
			if (log.isDebugEnabled()) {
				log.debug(testDocuments.stream().map(d -> {
					return d.getName() + " " + (i.incrementAndGet() % 10 == 0 ? "\n" : "");
				}).reduce("", String::concat));
			}
		}
		log.info("Corpus successfully distributed!");

		return this;
	}

	/**
	 * Converts a input document into the internal annotation instance
	 * representation.
	 * 
	 * @param developmentDocuments
	 * @param castedConfig
	 * @param defaultTrainInvestigationRestriction
	 * @param rootClassTypes
	 * @param documents
	 * 
	 */
	private OBIEInstance convertToInternalInstances(Instance instance) {

		final InstanceTemplateAnnotations internalAnnotation = new InstanceTemplateAnnotations();

		for (Entry<Class<? extends IOBIEThing>, List<IOBIEThing>> annotations : instance.annotations.entrySet()) {

			for (IOBIEThing a : annotations.getValue()) {

				Objects.requireNonNull(a);

				internalAnnotation.addAnnotation(new TemplateAnnotation(annotations.getKey(), a));

			}
		}

		return new OBIEInstance(instance.name, instance.content, internalAnnotation, instance.annotations.keySet());

	}

	public BigramInternalCorpus getTrainingCorpus() {
		return trainingCorpus;
	}

	public BigramInternalCorpus getDevelopCorpus() {
		if (distributer instanceof FoldCrossCorpusDistributor) {
			throw new RuntimeException("The development corpus is not available on fold cross validation.");
		}
		return developmentCorpus;
	}

	public BigramInternalCorpus getTestCorpus() {
		return testCorpus;
	}

	public BigramInternalCorpus getFullCorpus() {
		return remainingFullCorpus;
	}

	/**
	 * Reads a binary SCIOCopus.
	 * 
	 * @param filename fileName of the corpus.
	 * @return the SCIOCorpus.
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static BigramCorpusProvider loadCorpusFromFile(final RunParameter param) {

		final File file = CorpusFileTools.buildAnnotatedBigramCorpusFile(
				param.projectEnvironment.getBigramCorpusFileDirectory(), param.corpusNamePrefix, param.rootSearchTypes,
				param.ontologyEnvironment.getOntologyVersion());

		log.info("Load corpus from " + file + "...");
		try {
			Object data = null;
			FileInputStream fileIn;
			fileIn = new FileInputStream(file);
			ObjectInputStream in;
			in = new ObjectInputStream(fileIn);
			data = in.readObject();
			in.close();
			fileIn.close();
			log.info("Successfully loaded!");

			return ((BigramCorpusProvider) data).applyParameterToCorpus(param);
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
			log.warn(
					"Run ie.corpus.BigramCorpusCreator.java first to convert the raw-corpus into an OBIE-machineLearningFramework-readable format.");
			System.exit(COULD_NOT_LOAD_MODEL_ERROR);
		}
		throw new RuntimeException();
	}

	/**
	 * Adds investigationRestriction to all slot values of the parent value.
	 * 
	 * @param thing
	 * @param r
	 */
	@SuppressWarnings("unchecked")
	private static void setRestrictionRec(IOBIEThing thing, InvestigationRestriction r) {

		if (thing == null)
			return;

		try {

			if (ReflectionUtils.isAnnotationPresent(thing.getClass(), DatatypeProperty.class))
				return;

			thing.setInvestigationRestriction(r);

			for (Field slot : ReflectionUtils.getNonDatatypeSlots(thing.getClass(), r)) {

				if (ReflectionUtils.isAnnotationPresent(slot, RelationTypeCollection.class)) {

					for (IOBIEThing sv : (List<IOBIEThing>) slot.get(thing)) {
						setRestrictionRec(sv, r);
					}
				} else {
					setRestrictionRec((IOBIEThing) slot.get(thing), r);
				}

			}

		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	@Override
	public boolean nextFold() {
		if (!(distributer instanceof FoldCrossCorpusDistributor))
			throw new IllegalStateException("Provided corpus distributor does not support fold cross validation: "
					+ distributer.getDistributorID());
		// init with -1
		this.currentFold++;

		if (((FoldCrossCorpusDistributor) distributer).n == (this.currentFold))
			return false;

		updateFold(((FoldCrossCorpusDistributor) distributer).n == (this.currentFold + 1));

		return true;
	}

	private void updateFold(boolean last) {
		log.info("Update fold: " + this.currentFold);

		final int foldSize = this.remainingFullCorpus.getInternalInstances().size()
				/ ((FoldCrossCorpusDistributor) distributer).n;

		final List<OBIEInstance> newTrainingInstances = new ArrayList<>();

		newTrainingInstances
				.addAll(this.remainingFullCorpus.getInternalInstances().subList(0, this.currentFold * foldSize));
		newTrainingInstances.addAll(this.remainingFullCorpus.getInternalInstances()
				.subList((this.currentFold + 1) * foldSize, this.remainingFullCorpus.getInternalInstances().size()));

		newTrainingInstances.forEach(d -> d.setInstanceType(EInstanceType.TRAIN));

		this.trainingCorpus = new BigramInternalCorpus(newTrainingInstances);

		this.testCorpus = new BigramInternalCorpus(this.remainingFullCorpus.getInternalInstances().subList(
				this.currentFold * foldSize,
				last ? this.remainingFullCorpus.getInternalInstances().size() : (this.currentFold + 1) * foldSize));

		this.testCorpus.getInternalInstances().forEach(d -> d.setInstanceType(EInstanceType.TEST));
	}

	@Override
	public int getCurrentFoldIndex() {
		if (!(distributer instanceof FoldCrossCorpusDistributor))
			throw new IllegalStateException(
					"Selected corpus distributor configuration does not support fold corss validation: "
							+ distributer.getDistributorID());

		return this.currentFold;

	}

	/**
	 * Function for updating training data within active learning life cycle.
	 */
	@Override
	public List<OBIEInstance> updateActiveLearning(AbstractRunner runner, IActiveLearningDocumentRanker ranker) {

		if (!(distributer instanceof ActiveLearningDistributor))
			throw new IllegalArgumentException("Configuration does not support active learning validation: "
					+ distributer.getClass().getSimpleName());

		this.currentActiveLearningItertion++;

		final int remaining = getDevelopCorpus().getInternalInstances().size();

		if (remaining == 0) {
			return Collections.emptyList();
		} else {

			final List<OBIEInstance> trainingInstances = new ArrayList<>(this.trainingCorpus.getInternalInstances());

			Level trainerLevel = LogManager.getFormatterLogger(Trainer.class.getName()).getLevel();
			Level runnerLevel = LogManager.getFormatterLogger(AbstractRunner.class).getLevel();

			Configurator.setLevel(Trainer.class.getName(), Level.FATAL);
			Configurator.setLevel(AbstractRunner.class.getName(), Level.FATAL);

			log.info("Rank remaining training instances (" + getDevelopCorpus().getInternalInstances().size()
					+ ") using " + ranker.getClass().getSimpleName() + "...");
			List<OBIEInstance> rankedInstances = ranker.rank(getDevelopCorpus().getInternalInstances());
			log.info("done!");

			Configurator.setLevel(Trainer.class.getName(), trainerLevel);
			Configurator.setLevel(AbstractRunner.class.getName(), runnerLevel);

			List<OBIEInstance> newInstances;
			List<OBIEInstance> remainingInstances;

			if (remaining <= ((ActiveLearningDistributor) distributer).getB()) {
				newInstances = rankedInstances;
				remainingInstances = Collections.emptyList();
			} else {
				newInstances = rankedInstances.subList(0, ((ActiveLearningDistributor) distributer).getB());
				remainingInstances = new ArrayList<>(rankedInstances
						.subList(((ActiveLearningDistributor) distributer).getB(), rankedInstances.size()));
			}

			trainingInstances.addAll(newInstances);

			this.trainingCorpus = new BigramInternalCorpus(trainingInstances);

			this.developmentCorpus = new BigramInternalCorpus(remainingInstances);

			this.trainingCorpus.getInternalInstances().forEach(d -> d.setInstanceType(EInstanceType.TRAIN));

			return newInstances;
		}
	}

	public int getCurrentActiveLearningIteration() {
		return this.currentActiveLearningItertion;
	}

	/**
	 * Applies the limit to properties of type OneToManyRelation. If a list has more
	 * elements than the given limit, the rest will be removed.
	 * 
	 * @param annotation  the annotation.
	 * @param objectLimit the given limit to apply.
	 */
	private boolean testLimitToAnnnotationElementsRecursively(IOBIEThing annotation, final int objectLimit,
			final int datatypeLimit) {

		if (annotation == null)
			return true;

		final List<Field> fields = ReflectionUtils.getNonDatatypeSlots(annotation.getClass());

		for (Field field : fields) {
			try {
				if (ReflectionUtils.isAnnotationPresent(field, RelationTypeCollection.class)) {

					@SuppressWarnings("unchecked")
					List<IOBIEThing> elements = (List<IOBIEThing>) field.get(annotation);

					if (elements.size() > (ReflectionUtils.isAnnotationPresent(field, DatatypeProperty.class)
							? datatypeLimit
							: objectLimit)) {
						log.debug("Found property that elements in field: " + field.getName() + " exceeds given limit: "
								+ elements.size() + " > " + objectLimit);
						return false;
					}
					for (IOBIEThing element : elements) {
						final boolean m = testLimitToAnnnotationElementsRecursively(element, objectLimit,
								datatypeLimit);
						if (!m)
							return false;
					}
				} else {
					final boolean m = testLimitToAnnnotationElementsRecursively((IOBIEThing) field.get(annotation),
							objectLimit, datatypeLimit);
					if (!m)
						return false;
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		return true;
	}

}
