package de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.DatatypeProperty;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.OntologyModelContent;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.annotations.RelationTypeCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.tools.corpus.CorpusFileTools;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.tools.corpus.OBIECorpus;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.tools.corpus.OBIECorpus.Instance;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.distributor.AbstractCorpusDistributor;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.distributor.ActiveLearningDistributor;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.distributor.FoldCrossCorpusDistributor;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.utils.AnnotationExtractorHelper;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.ner.INamedEntitityLinker;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.AbstractOBIERunner;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.EntityAnnotation;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.InstanceEntityAnnotations;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.NamedEntityLinkingAnnotations;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;

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
	private AbstractCorpusDistributor config;

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
	protected BigramInternalCorpus fullCorpus;

	/**
	 * All internal instances that were converted from the raw corpus.
	 */
	public final List<OBIEInstance> internalInstances = new ArrayList<>();

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
		this.entityLinker = Collections.unmodifiableSet(entityLinker);
		try {
			this.rawCorpus = OBIECorpus.readRawCorpusData(rawCorpusFile);
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Could not load corpus: " + e.getMessage());
		}
		log.info("Convert " + rawCorpus.getAllDocumentNames().size() + " instances to interal representations...");

		final Set<INamedEntitityLinker> linker = entityLinker.stream().map(linkerClass -> {
			try {
				return linkerClass.getConstructor(Set.class).newInstance(this.rawCorpus.getRootClasses());
			} catch (Exception e) {
				e.printStackTrace();
			}
			throw new RuntimeException("Can not instantiate entity linker with name: " + linkerClass.getSimpleName());
		}).collect(Collectors.toSet());

		log.info("Apply named entity linking with " + linker.size() + " linker...");
		rawCorpus.getAllDocumentNames().parallelStream().forEach(docName -> {
			try {
				OBIEInstance internalInstance = convertToInternalInstances(docName);

				internalInstances.add(internalInstance);

				NamedEntityLinkingAnnotations.Builder annotationbuilder = new NamedEntityLinkingAnnotations.Builder();

				for (INamedEntitityLinker l : linker) {
					log.info("Apply: " + l.getClass().getSimpleName() + " to: " + internalInstance.getName());
					annotationbuilder.addAnnotations(l.annotate(internalInstance.getContent()));
				}
				internalInstance.setAnnotations(annotationbuilder.build());
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		checkAnnotationConsistencies(internalInstances);

	}

	/**
	 * Loops over all annotations and checks their consistency with different
	 * strategies.
	 * 
	 * @param trainingDocuments
	 */
	private void checkAnnotationConsistencies(final List<OBIEInstance> trainingDocuments) {
		for (OBIEInstance internalInstance : trainingDocuments) {
			for (EntityAnnotation internalAnnotation : internalInstance.getGoldAnnotation().getEntityAnnotations()) {
				checkForTextualAnnotations(internalAnnotation.getAnnotationInstance(), internalInstance.getName(),
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

		if (annotation.getClass().isAnnotationPresent(DatatypeProperty.class)) {

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
		Arrays.stream(annotation.getClass().getDeclaredFields())
				.filter(f -> f.isAnnotationPresent(OntologyModelContent.class)).forEach(field -> {
					field.setAccessible(true);
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

	private BigramCorpusProvider applyParameter(OBIERunParameter parameter) {
		log.info("Apply parameter to raw corpus... ");
		log.info("Remove empty documents while loading was set to: " + parameter.excludeEmptyInstancesFromCorpus);

		for (Iterator<OBIEInstance> it = this.internalInstances.iterator(); it.hasNext();) {
			OBIEInstance internalInstance = (OBIEInstance) it.next();

			if (parameter.excludeEmptyInstancesFromCorpus
					&& internalInstance.getGoldAnnotation().getEntityAnnotations().isEmpty()) {
				log.warn("WARN!!! No annotation data found!" + " Remove empty document " + internalInstance.getName()
						+ " from internal corpus.");

				it.remove();
				continue;
			}

			if (internalInstance.getGoldAnnotation().getEntityAnnotations()
					.size() > parameter.maxNumberOfEntityElements) {
				log.warn("WARN!!! Number of annotations = "
						+ internalInstance.getGoldAnnotation().getEntityAnnotations().size()
						+ " exceeds given limit of: " + parameter.maxNumberOfEntityElements + "! Remove document "
						+ internalInstance.getName() + " from internal corpus.");
				it.remove();
				continue;
			}

			for (EntityAnnotation annotation : internalInstance.getGoldAnnotation().getEntityAnnotations()) {

				if (!AnnotationExtractorHelper.testLimitToAnnnotationElementsRecursively(
						annotation.getAnnotationInstance(), parameter.maxNumberOfEntityElements)) {
					log.warn("WARN!!! Remove annotation " + internalInstance.getName() + " from internal instance.");
					it.remove();
					continue;
				}
			}
		}

		this.fullCorpus = new BigramInternalCorpus(this.internalInstances);

		log.info("Configuration is of type: " + config.getClass().getSimpleName());

		log.info("Convert " + String.valueOf(this.internalInstances.size())
				+ " instances to interal representations...");

		final List<OBIEInstance> trainingDocuments = new ArrayList<>();
		final List<OBIEInstance> developmentDocuments = new ArrayList<>();
		final List<OBIEInstance> testDocuments = new ArrayList<>();

		config.distributeInstances(this).distributeTrainingInstances(trainingDocuments)
				.distributeDevelopmentInstances(developmentDocuments).distributeTestInstances(testDocuments);

		this.currentFold = -1;
		this.currentActiveLearningItertion = 0;

		System.out.println("-----");
		System.out.println(trainingDocuments);
		System.out.println("-----");
		System.out.println(developmentDocuments);
		System.out.println("-----");
		System.out.println(testDocuments);

		this.trainingCorpus = new BigramInternalCorpus(trainingDocuments);
		this.developmentCorpus = new BigramInternalCorpus(developmentDocuments);
		this.testCorpus = new BigramInternalCorpus(testDocuments);

		log.info(
				"Remaining documents: (" + this.internalInstances.size() + "/" + rawCorpus.getInstances().size() + ")");
		if (config instanceof FoldCrossCorpusDistributor) {
			final AtomicInteger i = new AtomicInteger(0);
			log.info("Documents (" + this.internalInstances.size() + "):\n" + this.internalInstances.stream().map(d -> {
				return d.getName() + " " + (i.incrementAndGet() % 10 == 0 ? "\n" : "");
			}).reduce("", String::concat));
		} else {
			final AtomicInteger i = new AtomicInteger(0);
			log.info("Training documents (" + trainingDocuments.size() + "):\n" + trainingDocuments.stream().map(d -> {
				return d.getName() + " " + (i.incrementAndGet() % 10 == 0 ? "\n" : "");
			}).reduce("", String::concat));
			i.set(0);
			log.info("Development documents (" + developmentDocuments.size() + "):\n"
					+ developmentDocuments.stream().map(d -> {
						return d.getName() + " " + (i.incrementAndGet() % 10 == 0 ? "\n" : "");
					}).reduce("", String::concat));
			i.set(0);
			log.info("Test documents (" + testDocuments.size() + "):\n" + testDocuments.stream().map(d -> {
				return d.getName() + " " + (i.incrementAndGet() % 10 == 0 ? "\n" : "");
			}).reduce("", String::concat));
		}
		log.info("Corpus distributed!");

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

		final InstanceEntityAnnotations internalAnnotation = new InstanceEntityAnnotations();
		for (Entry<Class<? extends IOBIEThing>, List<IOBIEThing>> annotations : instance.annotations.entrySet()) {

			for (IOBIEThing a : annotations.getValue()) {

				Objects.requireNonNull(a);

				internalAnnotation.addAnnotation(new EntityAnnotation(annotations.getKey(), a));

			}
		}
		return new OBIEInstance(instance.documentName, instance.documentContent, internalAnnotation,
				instance.annotations.keySet());

	}

	public BigramInternalCorpus getTrainingCorpus() {
		return trainingCorpus;
	}

	public BigramInternalCorpus getDevelopCorpus() {
		if (config instanceof FoldCrossCorpusDistributor) {
			throw new RuntimeException("The development corpus is not available on fold cross validation.");
		}
		return developmentCorpus;
	}

	public BigramInternalCorpus getTestCorpus() {
		return testCorpus;
	}

	public BigramInternalCorpus getFullCorpus() {
		return fullCorpus;
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
		File file = CorpusFileTools.buildAnnotatedBigramCorpusFile(param.environment.getBigramCorpusFileDirectory(),
				param.corpusNamePrefix, param.rootSearchTypes, param.environment.getOntologyVersion());

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

			return ((BigramCorpusProvider) data).setConfiguration(param.corpusDistributor).applyParameter(param);
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
			log.warn(e.getMessage());
			System.exit(COULD_NOT_LOAD_MODEL_ERROR);
		}
		throw new RuntimeException();
	}

	private BigramCorpusProvider setConfiguration(AbstractCorpusDistributor config) {
		if (this.config != null)
			throw new IllegalStateException("Can not override corpus distribution configuration once it is set!");

		log.info("Set corpus configuration to: " + config.getClass().getSimpleName());
		this.config = config;
		return this;
	}

	@Override
	public boolean nextFold() {
		if (!(config instanceof FoldCrossCorpusDistributor))
			throw new IllegalStateException(
					"Selected corpus distributor configuration does not support fold corss validation: "
							+ config.getDistributorID());
		this.currentFold++;

		if (((FoldCrossCorpusDistributor) config).n == (this.currentFold))
			return false;

		updateFold();

		return true;
	}

	private void updateFold() {
		log.info("Update fold: " + this.currentFold);

		final int foldSize = this.internalInstances.size() / ((FoldCrossCorpusDistributor) config).n;

		final List<OBIEInstance> newTrainingInstances = new ArrayList<>();

		newTrainingInstances.addAll(this.internalInstances.subList(0, this.currentFold * foldSize));
		newTrainingInstances.addAll(
				this.internalInstances.subList((this.currentFold + 1) * foldSize, this.internalInstances.size()));

		this.trainingCorpus = new BigramInternalCorpus(newTrainingInstances);

		this.testCorpus = new BigramInternalCorpus(
				this.internalInstances.subList(this.currentFold * foldSize, (this.currentFold + 1) * foldSize));

	}

	@Override
	public int getCurrentFoldIndex() {
		if (!(config instanceof FoldCrossCorpusDistributor))
			throw new IllegalStateException(
					"Selected corpus distributor configuration does not support fold corss validation: "
							+ config.getDistributorID());

		return this.currentFold;

	}

	/**
	 * Function for updating training data within active learning life cycle.
	 */
	@Override
	public boolean updateALTrainingInstances(AbstractOBIERunner model) {

		if (!(config instanceof ActiveLearningDistributor))
			throw new IllegalArgumentException(
					"Configuration does not support active learning validation: " + config.getClass().getSimpleName());

		this.currentActiveLearningItertion++;

		List<OBIEInstance> remainingInstances = new ArrayList<>(
				model.corpusProvider.getDevelopCorpus().getInternalInstances());

		Collections.shuffle(remainingInstances, ((ActiveLearningDistributor) config).random);

		// List<SampledInstance<InternalInstance, InstanceAnnotations,
		// OBIEState>> remainingInstances = model
		// .predictOnDev();
		//
		// Collections.sort(remainingInstances,
		// new Comparator<SampledInstance<InternalInstance, InstanceAnnotations,
		// OBIEState>>() {
		//
		// @Override
		// public int compare(SampledInstance<InternalInstance,
		// InstanceAnnotations, OBIEState> o1,
		// SampledInstance<InternalInstance, InstanceAnnotations, OBIEState> o2)
		// {
		// return Double.compare(o1.getState().getModelScore(),
		// o2.getState().getModelScore());
		// }
		// });
		List<OBIEInstance> newTrainingInstances = new ArrayList<>(this.trainingCorpus.getInternalInstances());

		for (int i = 0; i < ((ActiveLearningDistributor) config).b; i++) {
			newTrainingInstances.add(remainingInstances.get(i).getInstance());
		}
		List<OBIEInstance> newDevelopmentInstances = new ArrayList<>();

		for (int i = ((ActiveLearningDistributor) config).b; i < this.developmentCorpus.getInternalInstances()
				.size(); i++) {
			newDevelopmentInstances.add(remainingInstances.get(i).getInstance());
		}

		this.trainingCorpus = new BigramInternalCorpus(newTrainingInstances);
		this.developmentCorpus = new BigramInternalCorpus(newDevelopmentInstances);

		return newDevelopmentInstances.isEmpty();
	}

	public int getCurrentActiveLearningIteration() {
		return this.currentActiveLearningItertion;
	}

	public Set<Class<? extends INamedEntitityLinker>> getNamedEntityLinkerClasses() {
		// TODO Auto-generated method stub
		return null;
	}

}
