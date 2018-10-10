package de.hterhors.obie.ml.run;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import corpus.SampledInstance;
import de.hterhors.obie.core.evaluation.PRF1;
import de.hterhors.obie.core.evaluation.PRF1Container;
import de.hterhors.obie.core.ontology.OntologyInitializer;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.corpus.BigramCorpusProvider;
import de.hterhors.obie.ml.evaluation.evaluator.CartesianSearchEvaluator;
import de.hterhors.obie.ml.evaluation.evaluator.IOBIEEvaluator;
import de.hterhors.obie.ml.exceptions.NotSupportedException;
import de.hterhors.obie.ml.ner.INamedEntitityLinker;
import de.hterhors.obie.ml.run.eval.EvaluatePrediction;
import de.hterhors.obie.ml.run.param.EScorerType;
import de.hterhors.obie.ml.run.param.OBIERunParameter;
import de.hterhors.obie.ml.scorer.IExternalScorer;
import de.hterhors.obie.ml.scorer.InstanceCollection;
import de.hterhors.obie.ml.scorer.LibLinearScorer;
import de.hterhors.obie.ml.scorer.LibSVMRegressionScorer;
import de.hterhors.obie.ml.scorer.OBIEScorer;
import de.hterhors.obie.ml.stopcrit.sampling.StopAtMaxObjectiveScore;
import de.hterhors.obie.ml.stopcrit.sampling.StopAtRepeatedModelScore;
import de.hterhors.obie.ml.templates.AbstractOBIETemplate;
import de.hterhors.obie.ml.utils.ModelFileNameUtils;
import de.hterhors.obie.ml.variables.InstanceEntityAnnotations;
import de.hterhors.obie.ml.variables.NamedEntityLinkingAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import exceptions.UnkownTemplateRequestedException;
import learning.AdvancedLearner;
import learning.AdvancedLearner.TrainingTriple;
import learning.DefaultLearner;
import learning.Learner;
import learning.Model;
import learning.ObjectiveFunction;
import learning.Trainer;
import learning.Trainer.EpochCallback;
import learning.Trainer.InstanceCallback;
import learning.scorer.LinearScorer;
import learning.scorer.Scorer;
import learning.scorer.SoftplusScorer;
import sampling.DefaultSampler;
import sampling.DefaultSampler.SamplingCallback;
import sampling.Explorer;
import sampling.Initializer;
import sampling.stoppingcriterion.StoppingCriterion;
import templates.AbstractTemplate;
import templates.TemplateFactory;
import variables.AbstractState;

public abstract class AbstractOBIERunner {

	public static Logger log = LogManager.getFormatterLogger(AbstractOBIERunner.class);

	public final OBIERunParameter parameter;

	public final ObjectiveFunction<OBIEState, InstanceEntityAnnotations> objectiveFunction;

	private Initializer<OBIEInstance, OBIEState> initializer;

	private StoppingCriterion<OBIEState> maxObjectiveScore;

	private StoppingCriterion<OBIEState> maxModelScoreStoppingCriteria;

	public final BigramCorpusProvider corpusProvider;

	private List<Explorer<OBIEState>> explorers;

	private Scorer scorer;

	protected boolean trainWithObjective = false;

	private final InstanceCollection featureMapData = new InstanceCollection();

	protected Model<OBIEInstance, OBIEState> model;

	private Learner<OBIEState> learner;

	private Trainer trainer;

	public DefaultSampler<OBIEInstance, OBIEState, InstanceEntityAnnotations> sampler;

	public AbstractOBIERunner(OBIERunParameter parameter) {

		log.info("Initialize OBIE runner...");

		this.parameter = parameter;
		log.debug("Parameter: " + this.parameter.toInfoString());

		log.info("Initialize ontological classes for individual-factories...");

		OntologyInitializer.initializeOntology(parameter.ontologyEnvironment);

		this.corpusProvider = BigramCorpusProvider.loadCorpusFromFile(parameter);

		/**
		 * TODO: parameterizes
		 */
		this.maxObjectiveScore = new StopAtMaxObjectiveScore(parameter.maxNumberOfSamplingSteps);
		this.maxModelScoreStoppingCriteria = new StopAtRepeatedModelScore(parameter.maxNumberOfSamplingSteps,
				3 * parameter.explorers.size());

		this.initializer = d -> new OBIEState(d, parameter);

		this.scorer = getScorer(parameter.scorerType);
		this.explorers = getExplorer(parameter);
		this.objectiveFunction = getObjectiveFunction();

		try {
			File infoFile = ModelFileNameUtils.getModelInfoFile(this.parameter, this.corpusProvider);
			PrintStream infoPrinter = new PrintStream(infoFile);
			infoPrinter.println(parameter.toInfoString());
			infoPrinter.close();
			log.info("Create info-file of current run in:" + infoFile.getAbsolutePath());
		} catch (IOException e) {
			e.printStackTrace();
			log.warn("Could not log info file!");
		}
	}

	/**
	 * Continues training for a given set of documents using the previous loaded or
	 * traind model.
	 * 
	 * @param trainingInstances
	 * @throws Exception
	 */
	public void continueTraining(List<OBIEInstance> trainingInstances) throws Exception {

		trainer.train(sampler, initializer, learner, trainingInstances, parameter.epochs);

	}

	public void train() throws Exception {
		train(new ArrayList<>(corpusProvider.getTrainingCorpus().getInternalInstances()));
	}

	public void train(List<OBIEInstance> trainingInstances) throws Exception {

		List<AbstractTemplate<OBIEInstance, OBIEState, ?>> templates = buildEmptyTemplates();

		model = new Model<>(scorer, templates);
		model.setMultiThreaded(parameter.multiThreading);

		sampler = buildTrainingDefaulSampler(model);

		trainer = buildDefaultTrainer();

		learner = getLearner();

		List<EpochCallback> epochCallbacks = addEpochCallback(sampler);

		for (EpochCallback epochCallback : epochCallbacks) {
			trainer.addEpochCallback(epochCallback);
		}

		// trainer.train(sampler, initializer, learner,
		// new ArrayList<>(
		// Arrays.asList(corpusProvider.getTestCorpus().getInstanceByName("Waver
		// et al 2005.txt"))),
		// parameter.epochs);
		trainer.train(sampler, initializer, learner, trainingInstances, parameter.epochs);
//		return model;
	}

	private Learner<OBIEState> getLearner() {
		final Learner<OBIEState> learner;

		if (scorer instanceof IExternalScorer) {

			// trainer.addInstanceCallback(new InstanceCallback() {
			//
			// @Override
			// public <InstanceT, StateT extends AbstractState<InstanceT>> void
			// onEndInstance(Trainer caller,
			// InstanceT instance, int indexOfInstance, StateT finalState, int
			// numberOfInstances, int epoch,
			// int numberOfEpochs) {
			//
			// if (scorer instanceof LibSVMRegressionScorer)
			// ((LibSVMRegressionScorer) scorer).train(featureMapData);
			// }
			// });
			learner = new Learner<OBIEState>() {

				@Override
				public void update(OBIEState currentState, OBIEState possibleNextState) {

				}

				@Override
				public void update(OBIEState currentState, List<OBIEState> possibleNextStates) {

				}

				@Override
				public void update(List<TrainingTriple<OBIEState>> triples) {

				}
			};
		} else {
//			learner = new DefaultLearner<>(model, parameter.optimizer.getCurrentAlphaValue());
			learner = new AdvancedLearner<>(model, parameter.optimizer, parameter.regularizer);
		}
		return learner;
	}

	void saveModel(int epoch) {
		File modelFile = ModelFileNameUtils.getModelFile(parameter, corpusProvider, epoch);

		try {
			if (scorer instanceof IExternalScorer) {
				((IExternalScorer) scorer).train(featureMapData);
				((IExternalScorer) scorer).saveScorer(modelFile);
			}
			model.saveModelToFile(modelFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected abstract List<EpochCallback> addEpochCallback(
			DefaultSampler<OBIEInstance, OBIEState, InstanceEntityAnnotations> sampler);

	private DefaultSampler<OBIEInstance, OBIEState, InstanceEntityAnnotations> buildTrainingDefaulSampler(
			Model<OBIEInstance, OBIEState> model) {

		sampler = new DefaultSampler<>(model, objectiveFunction, explorers, maxObjectiveScore);
		sampler.setTrainSamplingStrategy(OBIERunParameter.trainSamplingStrategyModelScore);
		sampler.setTrainAcceptStrategy(OBIERunParameter.trainAcceptanceStrategyModelScore);

		if (scorer instanceof IExternalScorer) {

			sampler.addSamplingCallback(new SamplingCallback() {

				@Override
				public <InstanceT, StateT extends AbstractState<InstanceT>> void onScoredProposalStates(
						List<StateT> bestStates) {

					// Collections.shuffle(bestStates, new Random(100L));

					for (final StateT stateT : bestStates) {
						if ((trainWithObjective && stateT.getObjectiveScore() < 0)
								|| (!trainWithObjective && stateT.getModelScore() < 0))
							continue;

						// for (int i = 0; i < 10; i++) {
						//
						// if ((float) stateT.getObjectiveScore() >= i / 10f
						// && (float) stateT.getObjectiveScore() < (i + 1) /
						// 10f) {

						// if ((int) counter.getOrDefault(i, 0) < 100) {
						// counter.put(i, counter.getOrDefault(i, 0) + 1);
						featureMapData.addFeatureDataPoint(((OBIEState) stateT).toTrainingPoint(featureMapData, true));
						// }
						// }
						//
						// }

					}
				}
			});
		}
		return sampler;
	}

	private List<AbstractTemplate<OBIEInstance, OBIEState, ?>> buildEmptyTemplates()
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException {
		List<AbstractTemplate<OBIEInstance, OBIEState, ?>> templates = new ArrayList<>();

		for (Class<? extends AbstractOBIETemplate<?>> abstractTemplate : parameter.templates) {

			AbstractTemplate<OBIEInstance, OBIEState, ?> t;
			t = instantiateTemplate(abstractTemplate);
			templates.add(t);
		}
		return templates;
	}

	private AbstractTemplate<OBIEInstance, OBIEState, ?> instantiateTemplate(
			Class<? extends AbstractTemplate<?, ?, ?>> abstractTemplate) {
		AbstractOBIETemplate<?> t = null;

		/**
		 * 
		 * TODO: how to instantiate special templates; Maybe pair of templateType and
		 * parameter? but then on loading? Specify params and how to load in in template
		 * maybe?
		 * 
		 */
		try {
			t = (AbstractOBIETemplate<?>) Class.forName(abstractTemplate.getName())
					.getConstructor(OBIERunParameter.class).newInstance(this.parameter);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		return t;
	}

	public List<SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>> testOnTest() throws IOException,
			FileNotFoundException, ClassNotFoundException, UnkownTemplateRequestedException, Exception {
		return test(corpusProvider.getTestCorpus().getInternalInstances());
	}

	public List<SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>> testOnTrain() throws IOException,
			FileNotFoundException, ClassNotFoundException, UnkownTemplateRequestedException, Exception {
		return test(corpusProvider.getTrainingCorpus().getInternalInstances());
	}

	public List<SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>> testInstance(OBIEInstance instance)
			throws IOException, FileNotFoundException, ClassNotFoundException, UnkownTemplateRequestedException,
			Exception {
		final List<OBIEInstance> instances = new ArrayList<>();
		instances.add(instance);
		return test(instances);
	}

	public List<SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>> testInstances(
			List<OBIEInstance> instances) throws IOException, FileNotFoundException, ClassNotFoundException,
			UnkownTemplateRequestedException, Exception {
		return test(instances);
	}

	public List<SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>> testOnDev() {
		return test(corpusProvider.getDevelopCorpus().getInternalInstances());
	}

	public List<OBIEState> predictInstance(final OBIEInstance instance,
			Set<Class<? extends INamedEntitityLinker>> entityLinker) {

		DefaultSampler<OBIEInstance, OBIEState, InstanceEntityAnnotations> sampler = buildTestDefaultSampler(model);

		Trainer trainer = buildDefaultTrainer();

		NamedEntityLinkingAnnotations.Builder annotationbuilder = new NamedEntityLinkingAnnotations.Builder();

		final Set<INamedEntitityLinker> linker = entityLinker.stream().map(linkerClass -> {
			try {
				return linkerClass.getConstructor(Set.class).newInstance(instance.rootClassTypes);
			} catch (Exception e) {
				e.printStackTrace();
			}
			throw new RuntimeException("Can not instantiate entity linker with name: " + linkerClass.getSimpleName());
		}).collect(Collectors.toSet());

		for (INamedEntitityLinker l : linker) {
			log.info("Apply: " + l.getClass().getSimpleName() + " to: " + instance.getName());
			annotationbuilder.addClassAnnotations(l.annotateClasses(instance.getContent()));
			annotationbuilder.addIndividualAnnotations(l.annotateIndividuals(instance.getContent()));
		}
		instance.setAnnotations(annotationbuilder.build());

		return trainer.predict(sampler, initializer, Arrays.asList(instance));

	}

	public List<OBIEState> predictInstancesBatch(final List<OBIEInstance> instances,
			Set<Class<? extends INamedEntitityLinker>> entityLinker) {

		DefaultSampler<OBIEInstance, OBIEState, InstanceEntityAnnotations> sampler = buildTestDefaultSampler(model);

		Trainer trainer = buildDefaultTrainer();

		NamedEntityLinkingAnnotations.Builder annotationbuilder = new NamedEntityLinkingAnnotations.Builder();

		for (OBIEInstance instance : instances) {

			final Set<INamedEntitityLinker> linker = entityLinker.stream().map(linkerClass -> {
				try {
					return linkerClass.getConstructor(Set.class).newInstance(instance.rootClassTypes);
				} catch (Exception e) {
					e.printStackTrace();
				}
				throw new RuntimeException(
						"Can not instantiate entity linker with name: " + linkerClass.getSimpleName());
			}).collect(Collectors.toSet());

			for (INamedEntitityLinker l : linker) {
				log.info("Apply: " + l.getClass().getSimpleName() + " to: " + instance.getName());
				annotationbuilder.addClassAnnotations(l.annotateClasses(instance.getContent()));
				annotationbuilder.addIndividualAnnotations(l.annotateIndividuals(instance.getContent()));
			}
			instance.setAnnotations(annotationbuilder.build());

		}
		return trainer.predict(sampler, initializer, instances);

	}

	public List<SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>> test(
			final List<OBIEInstance> instances) {

//		DefaultSampler<OBIEInstance, OBIEState, InstanceEntityAnnotations> sampler = buildTestDefaultSampler(model);
//
//		Trainer trainer = buildDefaultTrainer();

		return trainer.test(sampler, initializer, instances);

	}

	private DefaultSampler<OBIEInstance, OBIEState, InstanceEntityAnnotations> buildTestDefaultSampler(
			Model<OBIEInstance, OBIEState> model) {
		DefaultSampler<OBIEInstance, OBIEState, InstanceEntityAnnotations> sampler = new DefaultSampler<>(model,
				objectiveFunction, explorers, maxModelScoreStoppingCriteria);
		sampler.setTestSamplingStrategy(OBIERunParameter.testSamplingStrategy);
		sampler.setTestAcceptStrategy(OBIERunParameter.testAcceptanceStrategy);
		return sampler;
	}

	public void loadModel() throws Exception {

		final File modelFile = ModelFileNameUtils.getModelFile(this.parameter, this.corpusProvider,
				this.parameter.epochs);

		if (!modelFile.exists()) {
			throw new IOException("Model does not exists: " + modelFile);
		}

		this.model = new Model<>(scorer);
		model.setMultiThreaded(parameter.multiThreading);
		if (scorer instanceof IExternalScorer) {
			((IExternalScorer) scorer).loadScorer(modelFile);
		}
		model.loadModelFromDir(modelFile.getAbsoluteFile(), new TemplateFactory<OBIEInstance, OBIEState>() {

			@Override
			public AbstractTemplate<OBIEInstance, OBIEState, ?> newInstance(String templateName)
					throws UnkownTemplateRequestedException {

				for (Class<? extends AbstractTemplate<?, ?, ?>> template : parameter.templates) {

					if (template.getSimpleName().equals(templateName)) {
						return instantiateTemplate(template);
					}
				}

				throw new UnkownTemplateRequestedException("Can not find template for name: " + templateName);

			}
		});
	}

	private Trainer buildDefaultTrainer() {
		Trainer trainer = new Trainer();

		trainer.addInstanceCallback(new Trainer.InstanceCallback() {

			@Override
			public <InstanceT, StateT extends AbstractState<InstanceT>> void onEndInstance(Trainer caller,
					InstanceT instance, int indexOfInstance, StateT finalState, int numberOfInstances, int epoch,
					int numberOfEpochs) {
				final OBIEState state = (OBIEState) finalState;
				List<IOBIEThing> predictions = state.getCurrentPrediction().getTemplateAnnotations().stream()
						.map(s -> s.getTemplateAnnotation()).collect(Collectors.toList());
				List<IOBIEThing> gold = state.getInstance().getGoldAnnotation().getTemplateAnnotations().stream()
						.map(s -> s.getTemplateAnnotation()).collect(Collectors.toList());

				try {
					PRF1 s = parameter.evaluator.prf1(gold, predictions);
					log.info("PFR1 : " + s);
				} catch (NotSupportedException e) {
					double s = parameter.evaluator.f1(gold, predictions);
					log.info("F1 (purity): " + s);
				}
			}
		});
		trainer.addInstanceCallback(new Trainer.InstanceCallback() {

			@Override
			public <InstanceT, StateT extends AbstractState<InstanceT>> void onEndInstance(Trainer caller,
					InstanceT instance, int indexOfInstance, StateT finalState, int numberOfInstances, int epoch,
					int numberOfEpochs) {

				if (trainWithObjective && finalState.getObjectiveScore() != 1.0) {
					log.warn("###############################################");
					log.warn("####Could not reach objective score of 1.0!####");
					log.warn("###############################################");
				}

			}
		});
		if (objectiveFunction.getClass().equals(CartesianSearchEvaluator.class)) {

			trainer.addInstanceCallback(new InstanceCallback() {

				@Override
				public <InstanceT, StateT extends AbstractState<InstanceT>> void onEndInstance(Trainer caller,
						InstanceT instance, int indexOfInstance, StateT finalState, int numberOfInstances, int epoch,
						int numberOfEpochs) {
					parameter.evaluator.clearCache();
				}
			});
		}

		return trainer;
	}

	private Scorer getScorer(EScorerType scorerType) {
		Scorer scorer;
		switch (scorerType) {
		case LIB_LINEAR:
			scorer = new LibLinearScorer();
			break;
		case LIB_SVM:
			scorer = new LibSVMRegressionScorer(parameter.svmParam);
			break;
		case EXP:
			scorer = new OBIEScorer();
			break;
		case LINEAR:
			scorer = new LinearScorer();
			break;
		case SOFTPLUS:
			scorer = new SoftplusScorer();
			break;
		default:
			scorer = null;
		}
		return scorer;
	}

	private List<Explorer<OBIEState>> getExplorer(OBIERunParameter parameter) {
		final List<Explorer<OBIEState>> explorers = new ArrayList<>();

		for (Class<? extends Explorer<OBIEState>> explorerType : parameter.explorers) {
			try {
				explorers.add(explorerType.getConstructor(OBIERunParameter.class).newInstance(parameter));
			} catch (Exception e) {
			}
		}

		return explorers;
	}

	public abstract ObjectiveFunction<OBIEState, InstanceEntityAnnotations> getObjectiveFunction();

	public PRF1Container evaluateOnTest() throws Exception {

		List<SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>> predictions = testOnTest();

		/**
		 * Train with purity evaluate finally with cartesian
		 */
		IOBIEEvaluator evaluator = new CartesianSearchEvaluator(parameter.evaluator.isEnableCaching(),
				parameter.evaluator.getMaxEvaluationDepth(), parameter.evaluator.isPenalizeCardinality(),
				parameter.evaluator.getInvestigationRestrictions(), parameter.evaluator.getMaxNumberOfAnnotations(),
				parameter.evaluator.isIgnoreEmptyInstancesOnEvaluation());

		return EvaluatePrediction.evaluateREPredictions(getObjectiveFunction(), predictions, evaluator);
	}

	public void evaluatePerSlotOnTest(boolean detailedOutput) throws Exception {

		List<SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>> predictions = testOnTest();

		/**
		 * Train with purity evaluate finally with cartesian
		 */
		IOBIEEvaluator evaluator = new CartesianSearchEvaluator(parameter.evaluator.isEnableCaching(),
				parameter.evaluator.getMaxEvaluationDepth(), parameter.evaluator.isPenalizeCardinality(),
				parameter.evaluator.getInvestigationRestrictions(), parameter.evaluator.getMaxNumberOfAnnotations(),
				parameter.evaluator.isIgnoreEmptyInstancesOnEvaluation());

		EvaluatePrediction.evaluatePerSlotPredictions(getObjectiveFunction(), predictions, evaluator, detailedOutput);
	}

	public boolean modelExists() {

		final File modelFile = ModelFileNameUtils.getModelFile(this.parameter, this.corpusProvider,
				this.parameter.epochs);

		log.info("Search for model: " + modelFile);

		return modelFile.exists();
	}

	public void scoreWithModel(List<OBIEState> states) {
		model.score(states, null);
	}

}
