package de.hterhors.obie.ml.run;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import corpus.SampledInstance;
import de.hterhors.obie.core.evaluation.PRF1;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.corpus.BigramCorpusProvider;
import de.hterhors.obie.ml.evaluation.evaluator.CartesianSearchEvaluator;
import de.hterhors.obie.ml.evaluation.evaluator.IOBIEEvaluator;
import de.hterhors.obie.ml.evaluation.evaluator.StrictNamedEntityLinkingEvaluator;
import de.hterhors.obie.ml.exceptions.NotSupportedException;
import de.hterhors.obie.ml.ner.INamedEntitityLinker;
import de.hterhors.obie.ml.ner.NamedEntityLinkingAnnotations;
import de.hterhors.obie.ml.run.eval.EvaluatePrediction;
import de.hterhors.obie.ml.run.param.RunParameter;
import de.hterhors.obie.ml.scorer.IExternalScorer;
import de.hterhors.obie.ml.scorer.InstanceCollection;
import de.hterhors.obie.ml.scorer.LibLinearScorer;
import de.hterhors.obie.ml.scorer.LibSVMRegressionScorer;
import de.hterhors.obie.ml.scorer.OBIEScorer;
import de.hterhors.obie.ml.stopcrit.sampling.StopAtMaxObjectiveScore;
import de.hterhors.obie.ml.stopcrit.sampling.StopAtRepeatedModelScore;
import de.hterhors.obie.ml.templates.AbstractOBIETemplate;
import de.hterhors.obie.ml.utils.ModelFileNameUtils;
import de.hterhors.obie.ml.variables.InstanceTemplateAnnotations;
import de.hterhors.obie.ml.variables.OBIEInstance;
import de.hterhors.obie.ml.variables.OBIEState;
import exceptions.UnkownTemplateRequestedException;
import learning.AdvancedLearner;
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

	/**
	 * Final set of parameter that are shared across the system.
	 */
	private RunParameter parameter;

	/**
	 * Objective function specified by the implementing runner class. E.g. in the
	 * StandardRERunner -> REObjectiveFunction
	 */
	final public ObjectiveFunction<OBIEState, InstanceTemplateAnnotations> objectiveFunction;

	/**
	 * The initializer for creating a state.
	 */
	final private Initializer<OBIEInstance, OBIEState> initializer;

	final private StoppingCriterion<OBIEState> maxObjectiveScore;

	final private StoppingCriterion<OBIEState> maxModelScoreStoppingCriteria;

	public BigramCorpusProvider corpusProvider;

	private List<Explorer<OBIEState>> explorers;

	private Scorer scorer;

	protected boolean trainWithObjective = false;
	protected boolean sampleGreedy = false;

	private final InstanceCollection featureMapData = new InstanceCollection();

	protected Model<OBIEInstance, OBIEState> model;

	private Learner<OBIEState> learner;

	private Trainer trainer;

	private List<AbstractTemplate<OBIEInstance, OBIEState, ?>> templates;

	public DefaultSampler<OBIEInstance, OBIEState, InstanceTemplateAnnotations> sampler;

	public AbstractOBIERunner(RunParameter parameter) {
		log.info("Initialize OBIE runner...");
		this.parameter = parameter;
		log.debug("Parameter: " + this.parameter.toInfoString());
		this.initializer = d -> new OBIEState(d, parameter);
		this.objectiveFunction = getObjectiveFunction();

		this.corpusProvider = BigramCorpusProvider.loadCorpusFromFile(parameter);

		this.maxObjectiveScore = new StopAtMaxObjectiveScore(parameter.maxNumberOfSamplingSteps);
		this.maxModelScoreStoppingCriteria = new StopAtRepeatedModelScore(parameter.maxNumberOfSamplingSteps,
				3 * parameter.explorers.size());

		clean(parameter);

		try {
			File infoFile = ModelFileNameUtils.getModelInfoFile(this.parameter);
			PrintStream infoPrinter = new PrintStream(infoFile);
			infoPrinter.println(parameter.toInfoString());
			infoPrinter.close();
			log.info("Create info-file of current run in:" + infoFile.getAbsolutePath());
		} catch (IOException e) {
			e.printStackTrace();
			log.warn("Could not log info file!");
		}
	}

	public RunParameter getParameter() {
		return parameter;
	}

	public void clean(RunParameter cleanParameter) {
		this.parameter = cleanParameter;
		try {
			this.templates = newTemplates();

			this.scorer = newScorer();

			this.model = newModel(this.scorer, this.templates);

			this.explorers = newExplorer();

			this.sampler = newSampler(this.model, this.explorers, this.scorer);

			this.learner = newLearner(this.model);

			this.trainer = newTrainer();
		} catch (Exception e1) {
			e1.printStackTrace();
		}

	}

	/**
	 * Continues training for a given set of documents using the previous loaded or
	 * traind model.
	 * 
	 * @param trainingInstances
	 * @throws Exception
	 */
	private void continueTraining(List<OBIEInstance> trainingInstances) throws Exception {

		/*
		 * Sort to ensure same order before shuffling.
		 */

		Collections.sort(trainingInstances, new Comparator<OBIEInstance>() {

			@Override
			public int compare(OBIEInstance o1, OBIEInstance o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});

		trainer.train(sampler, initializer, learner, trainingInstances, parameter.epochs);

	}

	public void train() throws Exception {
		train(new ArrayList<>(corpusProvider.getTrainingCorpus().getInternalInstances()));
	}

	/**
	 * Same as calling first reset() and then continueTrain()
	 * 
	 * @param trainingInstances
	 * @throws Exception
	 */
	public void train(List<OBIEInstance> trainingInstances) throws Exception {

		List<EpochCallback> epochCallbacks = addEpochCallbackOnTrain(sampler);

		for (EpochCallback epochCallback : epochCallbacks) {
			trainer.addEpochCallback(epochCallback);
		}

		continueTraining(trainingInstances);

//		trainer.train(sampler, initializer, learner, trainingInstances, parameter.epochs);
	}

	private Model<OBIEInstance, OBIEState> newModel(Scorer scorer,
			List<AbstractTemplate<OBIEInstance, OBIEState, ?>> templates) {
		Model<OBIEInstance, OBIEState> model = new Model<>(scorer, templates);
		model.setMultiThreaded(parameter.multiThreading);
		return model;
	}

	private Learner<OBIEState> newLearner(Model<OBIEInstance, OBIEState> model) {
		final Learner<OBIEState> learner;

		if (model.getScorer() instanceof IExternalScorer) {

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

	protected abstract List<EpochCallback> addEpochCallbackOnTrain(
			DefaultSampler<OBIEInstance, OBIEState, InstanceTemplateAnnotations> sampler);

	private DefaultSampler<OBIEInstance, OBIEState, InstanceTemplateAnnotations> newSampler(
			Model<OBIEInstance, OBIEState> model, List<Explorer<OBIEState>> explorers, Scorer scorer) {

		DefaultSampler<OBIEInstance, OBIEState, InstanceTemplateAnnotations> sampler = new DefaultSampler<>(model,
				objectiveFunction, explorers, maxObjectiveScore);

		sampler.setTrainSamplingStrategy(RunParameter.linearTrainSamplingStrategyModelScore);
		sampler.setTrainAcceptStrategy(RunParameter.trainAcceptanceStrategyModelScore);

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

	private List<AbstractTemplate<OBIEInstance, OBIEState, ?>> newTemplates()
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
					.getConstructor(AbstractOBIERunner.class).newInstance(this);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		return t;
	}

	public List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> testOnTest() throws IOException,
			FileNotFoundException, ClassNotFoundException, UnkownTemplateRequestedException, Exception {
		return test(corpusProvider.getTestCorpus().getInternalInstances());
	}

	public List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> testOnTrain() throws IOException,
			FileNotFoundException, ClassNotFoundException, UnkownTemplateRequestedException, Exception {
		return test(corpusProvider.getTrainingCorpus().getInternalInstances());
	}

	public List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> testInstance(
			OBIEInstance instance) throws IOException, FileNotFoundException, ClassNotFoundException,
			UnkownTemplateRequestedException, Exception {
		final List<OBIEInstance> instances = new ArrayList<>();
		instances.add(instance);
		return test(instances);
	}

	public List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> testInstances(
			List<OBIEInstance> instances) throws IOException, FileNotFoundException, ClassNotFoundException,
			UnkownTemplateRequestedException, Exception {
		return test(instances);
	}

	public List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> testOnDev() {
		return test(corpusProvider.getDevelopCorpus().getInternalInstances());
	}

	public List<OBIEState> predictInstance(final OBIEInstance instance,
			Set<Class<? extends INamedEntitityLinker>> entityLinker) {

		DefaultSampler<OBIEInstance, OBIEState, InstanceTemplateAnnotations> sampler = buildTestDefaultSampler(model);

		Trainer trainer = newTrainer();

		NamedEntityLinkingAnnotations.Collector annotationbuilder = new NamedEntityLinkingAnnotations.Collector();

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
			annotationbuilder.addClassAnnotations(l.annotateClasses(instance.getName(), instance.getContent()));
			annotationbuilder
					.addIndividualAnnotations(l.annotateIndividuals(instance.getName(), instance.getContent()));
		}
		instance.setNERLAnnotations(annotationbuilder.collect());

		return trainer.predict(sampler, initializer, Arrays.asList(instance));

	}

	public List<OBIEState> predictInstancesBatch(final List<OBIEInstance> instances,
			Set<INamedEntitityLinker> entityLinker) {

		DefaultSampler<OBIEInstance, OBIEState, InstanceTemplateAnnotations> sampler = buildTestDefaultSampler(model);

		Trainer trainer = newTrainer();


		for (OBIEInstance instance : instances) {

			NamedEntityLinkingAnnotations.Collector annotationCollector = new NamedEntityLinkingAnnotations.Collector();

			for (INamedEntitityLinker l : entityLinker) {
				log.info("Apply: " + l.getClass().getSimpleName() + " to: " + instance.getName());
				annotationCollector.addClassAnnotations(l.annotateClasses(instance.getName(), instance.getContent()));
				annotationCollector
						.addIndividualAnnotations(l.annotateIndividuals(instance.getName(), instance.getContent()));
			}
			instance.setNERLAnnotations(annotationCollector.collect());

		}
		return trainer.predict(sampler, initializer, instances);

	}

	
	public List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> test(
			final List<OBIEInstance> instances) {

		DefaultSampler<OBIEInstance, OBIEState, InstanceTemplateAnnotations> sampler = buildTestDefaultSampler(model);

		return trainer.test(sampler, initializer, instances);

	}

	/**
	 * Set N <= 0 to collect all generated states.
	 * 
	 * @param instances
	 * @param N
	 * @return
	 */
	public Map<OBIEInstance, List<OBIEState>> collectBestNStates(final List<OBIEInstance> instances, final int N) {

		DefaultSampler<OBIEInstance, OBIEState, InstanceTemplateAnnotations> sampler = buildTestDefaultSampler(model);

		return trainer.collectBestNStates(sampler, initializer, instances, N);

	}

	private DefaultSampler<OBIEInstance, OBIEState, InstanceTemplateAnnotations> buildTestDefaultSampler(
			Model<OBIEInstance, OBIEState> model) {
		DefaultSampler<OBIEInstance, OBIEState, InstanceTemplateAnnotations> sampler = new DefaultSampler<>(model,
				objectiveFunction, explorers, maxModelScoreStoppingCriteria);
		sampler.setTestSamplingStrategy(RunParameter.testSamplingStrategy);
		sampler.setTestAcceptStrategy(RunParameter.testAcceptanceStrategy);
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

	private Trainer newTrainer() {
		Trainer trainer = new Trainer();

		trainer.addInstanceCallback(new Trainer.InstanceCallback() {

			@Override
			public <InstanceT, StateT extends AbstractState<InstanceT>> void onEndInstance(Trainer caller,
					InstanceT instance, int indexOfInstance, StateT finalState, int numberOfInstances, int epoch,
					int numberOfEpochs) {
				final OBIEState state = (OBIEState) finalState;
				List<IOBIEThing> predictions = state.getCurrentIETemplateAnnotations().getAnnotations().stream()
						.map(s -> s.getThing()).collect(Collectors.toList());
				List<IOBIEThing> gold = state.getInstance().getGoldAnnotation().getAnnotations().stream()
						.map(s -> s.getThing()).collect(Collectors.toList());

				try {
					PRF1 s = parameter.evaluator.prf1(gold, predictions);
					log.info("PFR1 : " + s);
					parameter.evaluator.clearCache();
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

	private Scorer newScorer() {
		Scorer scorer;
		switch (parameter.scorerType) {
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

	private List<Explorer<OBIEState>> newExplorer() {

		final List<Explorer<OBIEState>> explorers = new ArrayList<>();

		for (Class<? extends Explorer<OBIEState>> explorerType : parameter.explorers) {
			try {
				explorers.add(explorerType.getConstructor(RunParameter.class).newInstance(parameter));
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}

		return explorers;
	}

	public abstract ObjectiveFunction<OBIEState, InstanceTemplateAnnotations> getObjectiveFunction();

	public PRF1 evaluateOnTest() throws Exception {

		List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> predictions = testOnTest();

		/**
		 * Final evaluation with Cartesian
		 */
		IOBIEEvaluator evaluator = new CartesianSearchEvaluator(parameter.evaluator.isEnableCaching(),
				parameter.evaluator.getMaxEvaluationDepth(), parameter.evaluator.isPenalizeCardinality(),
				parameter.evaluator.getMaxNumberOfAnnotations(),
				parameter.evaluator.isIgnoreEmptyInstancesOnEvaluation());
		return EvaluatePrediction.evaluateSlotFillingPredictions(getObjectiveFunction(), predictions, evaluator);
	}

	public PRF1 evaluateNERLOnTest() throws Exception {
		List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> predictions = testOnTest();

		return EvaluatePrediction.evaluateEntityRecognitionPredictions(getObjectiveFunction(), predictions,
				new StrictNamedEntityLinkingEvaluator());
	}

	public PRF1 evaluateOnTrain() throws Exception {

		List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> predictions = testOnTrain();

		/**
		 * Final evaluation with Cartesian
		 */
		IOBIEEvaluator evaluator = new CartesianSearchEvaluator(parameter.evaluator.isEnableCaching(),
				parameter.evaluator.getMaxEvaluationDepth(), parameter.evaluator.isPenalizeCardinality(),
				parameter.evaluator.getMaxNumberOfAnnotations(),
				parameter.evaluator.isIgnoreEmptyInstancesOnEvaluation());

		return EvaluatePrediction.evaluateSlotFillingPredictions(getObjectiveFunction(), predictions, evaluator);
	}

	public void evaluatePerSlotOnTest(boolean detailedOutput) throws Exception {

		List<SampledInstance<OBIEInstance, InstanceTemplateAnnotations, OBIEState>> predictions = testOnTest();

		/**
		 * Final evaluation with Cartesian
		 */
		IOBIEEvaluator evaluator = new CartesianSearchEvaluator(parameter.evaluator.isEnableCaching(),
				parameter.evaluator.getMaxEvaluationDepth(), parameter.evaluator.isPenalizeCardinality(),
				parameter.evaluator.getMaxNumberOfAnnotations(),
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
		model.score(states);
	}

	public void scoreWithObjectiveFunction(OBIEState state) {
		objectiveFunction.score(state, state.getInstance().getGoldAnnotation());
	}

}
