package de.hterhors.obie.ml.corpus;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.collections.set.SynchronizedSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import corpus.SampledInstance;
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
import de.hterhors.obie.ml.run.AbstractOBIERunner;
import de.hterhors.obie.ml.run.param.OBIERunParameter;
import de.hterhors.obie.ml.utils.ReflectionUtils;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;

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

	/**
	 * The raw corpus that was loaded from binaries.
	 */
	public final OBIECorpus rawCorpus;

	/**
	 * Training, development, test corpus.
	 */
	private BigramInternalCorpus trainingCorpus;
	private BigramInternalCorpus developmentCorpus;
	private BigramInternalCorpus testCorpus;

	protected final Set<String> errors = new HashSet<>();

	/**
	 * This corpus contains all instances from training, development and test data.
	 */
	protected BigramInternalCorpus remainingFullCorpus;

	/**
	 * All internal instances that were converted from the raw corpus.
	 */
	public final List<OBIEInstance> allExistingInternalInstances = new ArrayList<>();

	private int currentFold = -1;

	public OBIECorpus getRawCorpus() {
		return rawCorpus;
	}

	private int currentActiveLearningItertion = 0;

	private final Set<Class<? extends INamedEntitityLinker>> entityLinker;

	public Set<Class<? extends INamedEntitityLinker>> getEntityLinker() {
		return entityLinker;
	}

	public BigramCorpusProvider(final File rawCorpusFile, Set<Class<? extends INamedEntitityLinker>> entityLinker) {
		log.info("Start creating corpus...");

		log.info("Read raw corpus from file system: " + rawCorpusFile);
		try {
			this.rawCorpus = OBIECorpus.readRawCorpusData(rawCorpusFile);
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Could not load corpus: " + e.getMessage());
		}

		this.entityLinker = Collections.unmodifiableSet(entityLinker);

		log.info("Provided Named Enitity Recognition and Linking tools: ");
		this.entityLinker.forEach(log::info);

		log.info("Apply NEL-tools to " + rawCorpus.getAllInstanceNames().size() + " instances...");
		log.info("Instantiate NEL-tools...");
		final Set<INamedEntitityLinker> linker = entityLinker.stream().map(linkerClass -> {
			try {
				return linkerClass.getConstructor(Set.class).newInstance(this.rawCorpus.getRootClasses());
			} catch (Exception e) {
				e.printStackTrace();
			}
			throw new RuntimeException("Can not instantiate entity linker with name: " + linkerClass.getSimpleName());
		}).collect(Collectors.toSet());

		log.info("Apply " + linker.size() + " NEL-tools to instances...");

		AtomicInteger countEntities = new AtomicInteger();

		rawCorpus.getAllInstanceNames().parallelStream().forEach(docName -> {
			try {
				OBIEInstance internalInstance = convertToInternalInstances(docName);

				allExistingInternalInstances.add(internalInstance);

				NamedEntityLinkingAnnotations.Builder annotationbuilder = new NamedEntityLinkingAnnotations.Builder();

				for (INamedEntitityLinker l : linker) {
					log.info("Apply: " + l.getClass().getSimpleName() + " to: " + internalInstance.getName());
					annotationbuilder.addClassAnnotations(l.annotateClasses(internalInstance.getContent()));
					annotationbuilder.addIndividualAnnotations(l.annotateIndividuals(internalInstance.getContent()));
				}

				internalInstance.setAnnotations(annotationbuilder.build());

				log.info("Found " + internalInstance.getNamedEntityLinkingAnnotations().numberOfTotalAnnotations()
						+ " in instance: " + internalInstance.getName());

				countEntities.addAndGet(internalInstance.getNamedEntityLinkingAnnotations().numberOfTotalAnnotations());
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

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
				checkForTextualAnnotations(internalAnnotation.get(), internalInstance.getName(),
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
			else if (!content.contains(surfaceForm)) {
				final String error = "Could not find: " + surfaceForm + " in " + instanceName;
				errors.add(error);
				log.error(error + "\nContent: " + content + "\n");
			}

		}
		/*
		 * Add factors for object type properties.
		 */
		ReflectionUtils.getAccessibleOntologyFields(annotation.getClass()).forEach(field -> {
			try {
				if (field.isAnnotationPresent(RelationTypeCollection.class)) {
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

	private BigramCorpusProvider applyParameterToCorpus(OBIERunParameter parameter) {
		log.info("Apply parameter to corpus...");
		log.info("\"remove empty documents\"-flag was set to: " + parameter.excludeEmptyInstancesFromCorpus);
		log.info("\"maximum number of annotations\" was set to: " + parameter.maxNumberOfEntityElements);

		final int totalNumberOfInstances = this.allExistingInternalInstances.size();

		log.info("Apply filter...");
		for (Iterator<OBIEInstance> it = this.allExistingInternalInstances.iterator(); it.hasNext();) {
			OBIEInstance internalInstance = (OBIEInstance) it.next();

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

				if (!testLimitToAnnnotationElementsRecursively(annotation.get(), parameter.maxNumberOfEntityElements,
						parameter.maxNumberOfDataTypeElements)) {
					log.debug("Number of elements in annotation exceeds limit of: "
							+ parameter.maxNumberOfEntityElements + " for object property OR "
							+ parameter.maxNumberOfDataTypeElements + " for datatype property" + "!Remove annotation "
							+ internalInstance.getName() + " from corpus.");
					it.remove();
					continue;
				}
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

		this.currentFold = -1;
		this.currentActiveLearningItertion = 0;

		this.trainingCorpus = new BigramInternalCorpus(trainingDocuments);
		this.developmentCorpus = new BigramInternalCorpus(developmentDocuments);
		this.testCorpus = new BigramInternalCorpus(testDocuments);

		this.remainingFullCorpus = new BigramInternalCorpus(trainingCorpus, developmentCorpus, testCorpus);

		log.info("Distributed instances: ~"
				+ (Math.round((float) this.remainingFullCorpus.getInternalInstances().size()
						/ (float) this.allExistingInternalInstances.size() * 100))
				+ "% (" + this.remainingFullCorpus.getInternalInstances().size() + "/"
				+ this.allExistingInternalInstances.size() + ")");

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
	 * @param investigationRestriction
	 * @param rootClassTypes
	 * @param documents
	 * 
	 */
	private OBIEInstance convertToInternalInstances(String docName) throws Exception {

		Instance instance = rawCorpus.getInstances().get(docName);

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
	public static BigramCorpusProvider loadCorpusFromFile(final OBIERunParameter param) {

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

			return ((BigramCorpusProvider) data).setConfiguration(param.corpusDistributor)
					.applyParameterToCorpus(param);
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
			log.warn(
					"Run ie.corpus.BigramCorpusCreator.java first to convert the raw-corpus into an OBIE-machineLearningFramework-readable format.");
			System.exit(COULD_NOT_LOAD_MODEL_ERROR);
		}
		throw new RuntimeException();
	}

	private BigramCorpusProvider setConfiguration(AbstractCorpusDistributor corpus) {

		if (this.distributer != null)
			throw new IllegalStateException("Can not override corpus distribution configuration once it is set!");

		log.info("Set corpus distributor to: " + corpus.getClass().getSimpleName());
		this.distributer = corpus;
		return this;
	}

	@Override
	public boolean nextFold() {
		if (!(distributer instanceof FoldCrossCorpusDistributor))
			throw new IllegalStateException("Provided corpus distributor does not support fold cross validation: "
					+ distributer.getDistributorID());
		this.currentFold++;

		if (((FoldCrossCorpusDistributor) distributer).n == (this.currentFold))
			return false;

		updateFold();

		return true;
	}

	private void updateFold() {
		log.info("Update fold: " + this.currentFold);

		final int foldSize = this.remainingFullCorpus.getInternalInstances().size()
				/ ((FoldCrossCorpusDistributor) distributer).n;

		final List<OBIEInstance> newTrainingInstances = new ArrayList<>();

		newTrainingInstances
				.addAll(this.remainingFullCorpus.getInternalInstances().subList(0, this.currentFold * foldSize));
		newTrainingInstances.addAll(this.remainingFullCorpus.getInternalInstances()
				.subList((this.currentFold + 1) * foldSize, this.remainingFullCorpus.getInternalInstances().size()));

		this.trainingCorpus = new BigramInternalCorpus(newTrainingInstances);

		this.testCorpus = new BigramInternalCorpus(this.remainingFullCorpus.getInternalInstances()
				.subList(this.currentFold * foldSize, (this.currentFold + 1) * foldSize));

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
	public List<OBIEInstance> updateActiveLearning(AbstractOBIERunner runner, IActiveLearningDocumentRanker ranker) {

		if (!(distributer instanceof ActiveLearningDistributor))
			throw new IllegalArgumentException("Configuration does not support active learning validation: "
					+ distributer.getClass().getSimpleName());

		this.currentActiveLearningItertion++;

		final int remaining = getDevelopCorpus().getInternalInstances().size();

		final List<OBIEInstance> trainingInstances = new ArrayList<>(this.trainingCorpus.getInternalInstances());

		if (remaining <= ((ActiveLearningDistributor) distributer).b) {
			trainingInstances.addAll(getDevelopCorpus().getInternalInstances());

			this.developmentCorpus = new BigramInternalCorpus(Collections.emptyList());

			this.trainingCorpus = new BigramInternalCorpus(trainingInstances);

			return Collections.emptyList();
		} else {

			log.info("Rank remaining training instances (" + getDevelopCorpus().getInternalInstances().size()
					+ ") using " + ranker.getClass().getSimpleName() + "...");
			List<OBIEInstance> remainingInstances = ranker.rank((ActiveLearningDistributor) distributer, runner,
					getDevelopCorpus().getInternalInstances());
			log.info("done!");

			List<OBIEInstance> newInstances = remainingInstances.subList(0,
					((ActiveLearningDistributor) distributer).b);

			trainingInstances.addAll(newInstances);

			this.trainingCorpus = new BigramInternalCorpus(trainingInstances);

			this.developmentCorpus = new BigramInternalCorpus(new ArrayList<>(remainingInstances
					.subList(((ActiveLearningDistributor) distributer).b, remainingInstances.size())));

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

		final List<Field> fields = ReflectionUtils.getAccessibleOntologyFields(annotation.getClass());

		for (Field field : fields) {
			try {
				if (field.isAnnotationPresent(RelationTypeCollection.class)) {

					@SuppressWarnings("unchecked")
					List<IOBIEThing> elements = (List<IOBIEThing>) field.get(annotation);

					if (elements.size() > (field.isAnnotationPresent(DatatypeProperty.class) ? datatypeLimit
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
