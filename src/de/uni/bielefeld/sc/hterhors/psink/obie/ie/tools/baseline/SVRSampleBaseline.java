package de.uni.bielefeld.sc.hterhors.psink.obie.ie.tools.baseline;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import corpus.SampledInstance;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.BigramCorpusProvider;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.evaluation.evaluator.IEvaluator;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.explorer.TemplateExplorer;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.objfunc.REObjectiveFunction;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.eval.EvaluatePrediction;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.run.param.OBIERunParameter;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.scorer.IExternalScorer;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.scorer.InstanceCollection;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.scorer.LibSVMRegressionScorer;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.templates.scope.OBIEFactorScope;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.InstanceEntityAnnotations;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEInstance;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.variables.OBIEState;
import factors.Factor;
import factors.FactorGraph;
import factors.FactorPool;
import factors.FactorScope;
import templates.AbstractTemplate;
import utility.Utils;

/**
 * This class calculates a baseline using the lib svm regression model. First we
 * sample from all possible candidates collect them score them by an objective
 * function and then use them as training for the svm.
 * 
 * @author hterhors
 *
 * @date Dec 13, 2017
 */
public class SVRSampleBaseline {

	/**
	 * If there are more training data then this size a warning is thrown.
	 */
	private static final long WARN_AT_LIMIT_OF_TRAIN = 1000000;

	public static Logger log = LogManager.getLogger(SVRSampleBaseline.class);

	private static enum ESelectionMode {
		/**
		 * Random selection mode shuffles the generated next states and takes n first
		 * for further exploration.
		 */
		RANDOM,

		/**
		 * Best selection mode sorts generated next states and takes n best for further
		 * exploration.
		 */
		BEST;
	}

	private static enum EExplorationMode {
		/**
		 * Exponential exploration, collect all n selected states, generates for each
		 * state n new states which are collected again until a given max depth is
		 * reached.
		 */
		EXPONENTIAL,

		/**
		 * Linear exploration, collects all n selected states, generates n*n new states
		 * and collects from these n following states.
		 */
		BEAM;
	}

	/**
	 * The data collection instance. It collects all training states and provides
	 * them. Threadsafe!
	 */
	private InstanceCollection data = new InstanceCollection();

	/**
	 * The objective function which is used to score states during training. The
	 * objective score determines the quality of the state.
	 */
	private IEvaluator evaluator;

	/**
	 * The parameter that includes the templates and further information.
	 */
	private OBIERunParameter parameter;

	/**
	 * The exploration strategy. TODO: incorporate multiple strategies like
	 * cardinality explorer.
	 */
	private TemplateExplorer explorer;
	/**
	 * The list of templates, which where specified in the parameter.
	 */
	private List<AbstractTemplate<OBIEInstance, OBIEState, ? extends OBIEFactorScope>> templates = new ArrayList<>();

	/**
	 * The provider of the corpus. That includes training, development (if any) and
	 * test set.
	 */
	private BigramCorpusProvider corpusProvider;

	/**
	 * A factor pool for sharing factors. This variable is not really needed by as
	 * we use some bire internal code we need to specify this.
	 */
	private FactorPool sharedFactorPool = FactorPool.getInstance();

	/**
	 * The selection mode determines the way of select the next following states.
	 */
	private ESelectionMode selectionMode = ESelectionMode.RANDOM;

	/**
	 * The collection mode determines the way of selected states are explored.
	 * 
	 */
	private EExplorationMode explorationMode = EExplorationMode.BEAM;

	/**
	 * The random seed for shuffling the data.
	 */
	private long randomSeed = 100L;

	/**
	 * Random for state shuffling during training exploration.
	 */
	private Random rand = new Random(randomSeed);

	/**
	 * Number of states that are explored in each step
	 *
	 * Specifies how many variation for a particular slot / type should be used.
	 *
	 */
	private final int numOfExploration = 100;

	/**
	 * Number of steps
	 *
	 * Specifies basically how many slots are watch (at max). It might be, that the
	 * same slot is chosen multiple times.
	 *
	 */
	private int explortionDepth = 20;

	/**
	 * Whether the model should be rebuild if it already exists or not.
	 */
	private boolean forceBuildModel = true;

	/**
	 * The default path to where the models are stored.
	 */
	private static final String modelDirectory = "/baseline/svr/libsvm/models/";

	// static {
	// if (!new File(modelDirectory).exists()) {
	// new File(modelDirectory).mkdirs();
	// }
	// }

	/**
	 * 
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws IOException
	 */
	public SVRSampleBaseline(OBIERunParameter parameter) throws Exception {

		this.parameter = parameter;
		/*
		 * TODO: adjust parameter.
		 */
		explorer = new TemplateExplorer(parameter);
		corpusProvider = BigramCorpusProvider.loadCorpusFromFile(parameter);

		evaluator = parameter.evaluator;

		for (Class<? extends AbstractTemplate<?, ?, ?>> abstractTemplate : parameter.templates) {
			templates.add(instantiateTemplate(abstractTemplate));
		}

		IExternalScorer model;

		final File modelName = new File(parameter.rootDirectory + modelDirectory + buildModelName());

		if (!new File(parameter.rootDirectory + modelDirectory).exists()) {
			new File(parameter.rootDirectory + modelDirectory).mkdirs();
		}

		if (forceBuildModel) {
			model = trainModel(modelName);
		} else {
			try {
				model = loadModel(modelName);
			} catch (IOException e) {
				log.warn("Can not load model: " + e.getMessage());
				model = trainModel(modelName);
			}
		}

		evaluate(corpusProvider.getTestCorpus().getInternalInstances(), model);

		// Collections.sort(collectedTrainingStates, new
		// Comparator<ActiveLearningState>() {
		// @Override
		// public int compare(ActiveLearningState o1, ActiveLearningState o2) {
		// return -Double.compare(o1.getObjectiveScore(),
		// o2.getObjectiveScore());
		//
		// }
		// });
		// collectedTrainingStates.forEach(System.out::println);

	}

	/**
	 * Builds the name of the model given the parameter.
	 * 
	 * @return
	 */
	private String buildModelName() {
		return rootTypesToString() + (explorationMode + "_" + selectionMode + "_" + randomSeed + "_" + numOfExploration
				+ "_" + explortionDepth).toLowerCase();
	}

	private String rootTypesToString() {
		final StringBuffer names = new StringBuffer();
		for (Class<? extends IOBIEThing> clazz : parameter.rootSearchTypes) {
			names.append(clazz.getSimpleName());
			names.append("_");
		}

		return names.toString();
	}

	private IExternalScorer trainModel(final File modelFile) throws IOException {
		log.info("Train new model with name: " + modelFile);

		long num;
		switch (explorationMode) {
		case EXPONENTIAL:
			num = (int) Math.pow(numOfExploration, explortionDepth);
			break;
		case BEAM:
			num = numOfExploration * explortionDepth;
			break;
		default:
			throw new NotImplementedException("Exploration mode is not implemented: " + explorationMode);
		}
		/*
		 * Times number of training data.
		 */
		num *= corpusProvider.getTrainingCorpus().getInternalInstances().size();
		/*
		 * Increase by number of training data cause of initial state each.
		 */
		num += corpusProvider.getTrainingCorpus().getInternalInstances().size();

		log.info("Approximately number of training instances: " + num);

		if (num >= Integer.MAX_VALUE) {
			throw new IllegalArgumentException("The number of generated training data exceeds the limit of integer!");
		}

		if (num >= WARN_AT_LIMIT_OF_TRAIN) {
			log.warn("WARN! The number of generated training data exceeds the warn limit: " + WARN_AT_LIMIT_OF_TRAIN);
		}

		explore(numOfExploration, explortionDepth);

		final long time = System.currentTimeMillis();

		IExternalScorer model = learn();
		model.saveScorer(modelFile);

		log.info("Time needed to learn: " + (System.currentTimeMillis() - time));

		return model;
	}

	private IExternalScorer loadModel(final File modelFile) throws IOException {
		log.info("Load model from: " + modelFile + "... ");
		IExternalScorer loadedModel = newExternalScorerInstance();
		loadedModel.loadScorer(modelFile);
		log.info("done!");
		return loadedModel;
	}

	/**
	 * Uses java reflections to create instances of templates that were specified in
	 * the parameters.
	 * 
	 * @param abstractTemplate
	 * @return
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws ClassNotFoundException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 */
	@SuppressWarnings("unchecked")
	private AbstractTemplate<OBIEInstance, OBIEState, ? extends OBIEFactorScope> instantiateTemplate(
			Class<? extends AbstractTemplate<?, ?, ?>> abstractTemplate)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException {
		AbstractTemplate<OBIEInstance, OBIEState, ? extends OBIEFactorScope> t;

		/**
		 * TODO: How to instantiate special templates
		 */
//		if (abstractTemplate == SemanticNumericDataTypeTemplate.class) {
//			t = (AbstractTemplate<InternalInstance, OBIEState, ? extends OBIEFactorScope>) Class
//					.forName(abstractTemplate.getName()).getConstructor(OBIEParameter.class, List.class)
//					.newInstance(parameter, corpusProvider.getTrainingCorpus().getInternalInstances());
//		} else {
		t = (AbstractTemplate<OBIEInstance, OBIEState, ? extends OBIEFactorScope>) Class
				.forName(abstractTemplate.getName()).getConstructor(OBIERunParameter.class).newInstance(parameter);
//		}
		return t;
	}

	/**
	 * Trains a regression svm using the previously collected data.
	 * 
	 * @return the trained model
	 * @throws IOException
	 */
	private IExternalScorer learn() throws IOException {
		IExternalScorer model = newExternalScorerInstance();

		model.train(data);

		return model;
	}

	private IExternalScorer newExternalScorerInstance() {
		return new LibSVMRegressionScorer(parameter.svmParam);
		// return new LibLinearScorer();
	}

	/**
	 * Starts the exploration strategy during prediction. This method calls itself
	 * in a recursive way until the given depth is reached.
	 * 
	 * In opposite to the training exploration we do not use multiple states for
	 * further exploration but only the best state. This ensures a directed search.
	 * However, it may be stuck in a local optimum.
	 * 
	 * @param model
	 * @param previousState
	 * @param remainingCalls
	 * @return the state with the highest model score.
	 */
	private OBIEState explorePredicting(IExternalScorer model, OBIEState previousState, final double prevScore) {

		List<OBIEState> states = explorer.getNextStates(previousState);

		double maxScore = 0;

		/*
		 * build
		 */
		generateFeatures(states);
		/*
		 * score
		 */
		model.score(states, true);

		OBIEState bestState = previousState;
		/*
		 * get best
		 */
		for (OBIEState predState : states) {
			final double score = predState.getModelScore();
			if (maxScore <= score) {
				bestState = predState;
				maxScore = score;
			}
		}
		// System.out.println(bestState);
		/*
		 * Break recursive call if the score does not change significant
		 */
		if (Math.abs(prevScore - maxScore) < 0.000001)
			return bestState;

		/*
		 * Explore further
		 */
		return explorePredicting(model, bestState, maxScore);
	}

	/**
	 * Starts the exploration strategy to collect training instances. This method
	 * starts a recursive method which runs until the given depth is reached.
	 * 
	 * @param numOfExploration
	 * @param depth
	 */
	private void explore(final int numOfExploration, final int depth) {

		corpusProvider.getTrainingCorpus().getInternalInstances().parallelStream().forEach(trainInstance -> {

			OBIEState previousState = new OBIEState(trainInstance, parameter);

			List<IOBIEThing> gold = trainInstance.getGoldAnnotation().getEntityAnnotations().stream()
					.map(e -> e.getAnnotationInstance()).collect(Collectors.toList());

			List<OBIEState> previousStates = new ArrayList<>();

			previousStates.add(previousState);

			switch (explorationMode) {
			case EXPONENTIAL:
				exploreTrainingExplonential(previousState, gold, numOfExploration, depth);
				break;
			case BEAM:
				exploreTrainingBeam(previousStates, gold, numOfExploration, depth);
				break;

			default:
				throw new NotImplementedException("Exploration mode is not implemented: " + explorationMode);
			}

		});

	}

	/**
	 * The exploration during training creates n following states that are further
	 * explored. To do that, we generate all possible following states and select
	 * random the next n states. For each of this states we call this method
	 * recursively until we read the maximum depth.
	 * 
	 * All selected states are collected as training data. For that the states are
	 * scored by the objective function to determine their quality.
	 * 
	 * @param previousState
	 * @param gold
	 * @param numOfExploration
	 * @param remainingCalls
	 */
	private void exploreTrainingExplonential(OBIEState previousState, List<IOBIEThing> gold, final int numOfExploration,
			final int remainingCalls) {

		if (remainingCalls == 0)
			return;

		List<OBIEState> generatedStates = explorer.getNextStates(previousState);

		switch (selectionMode) {
		case BEST:
			scoreAndSort(gold, generatedStates);
			break;
		case RANDOM:
			Collections.shuffle(generatedStates, rand);
			break;
		default:
			throw new NotImplementedException("Selection mode is not implemented: " + selectionMode);
		}

		final List<OBIEState> selectedStates = generatedStates.stream().limit(numOfExploration)
				.collect(Collectors.toList());

		selectedStates.add(previousState);

		generateFeatures(selectedStates);

		selectedStates.forEach(newState -> {

			List<IOBIEThing> prediction = newState.getCurrentPrediction().getEntityAnnotations().stream()
					.map(e -> e.getAnnotationInstance()).collect(Collectors.toList());

			data.addFeatureDataPoint(newState.toTrainingPoint(data, true).setScore(evaluator.f1(gold, prediction)));

			if (data.getDataPoints().size() % 1000 == 0)
				log.debug("Collected #of datapoints: " + data.getDataPoints().size());
			/*
			 * Call recursive
			 */

			exploreTrainingExplonential(newState, gold, numOfExploration, remainingCalls - 1);

		});

	}

	private void exploreTrainingBeam(final List<OBIEState> previousStates, List<IOBIEThing> gold,
			final int numOfExploration, final int remainingCalls) {

		if (remainingCalls == 0)
			return;

		generateFeatures(previousStates);

		// /**
		// *
		// * ########DEBUG print learning states############
		// *
		// */
		// for (ActiveLearningState genState : previousStates) {
		// final List<IOBIEThing> prediction =
		// genState.getPredictedResult().getEntities().stream()
		// .map(e -> e.getOBIEAnnotation()).collect(Collectors.toList());
		// genState.setObjectiveScore(objectiveFunction.f1(gold, prediction));
		// }
		// collectedTrainingStates.addAll(previousStates);

		previousStates.forEach(newState -> {

			List<IOBIEThing> prediction = newState.getCurrentPrediction().getEntityAnnotations().stream()
					.map(e -> e.getAnnotationInstance()).collect(Collectors.toList());

			data.addFeatureDataPoint(newState.toTrainingPoint(data, true).setScore(evaluator.f1(gold, prediction)));

			if (data.getDataPoints().size() % 10000 == 0)
				log.info("Collected #of datapoints: " + data.getDataPoints().size());

		});

		final List<OBIEState> generatedStates = new ArrayList<>();
		for (OBIEState previousState : previousStates) {
			generatedStates.addAll(explorer.getNextStates(previousState));
		}

		switch (selectionMode) {
		case BEST:
			scoreAndSort(gold, generatedStates);
			break;
		case RANDOM:
			Collections.shuffle(generatedStates, rand);
			break;
		default:
			throw new NotImplementedException("Selection mode is not implemented: " + selectionMode);
		}
		final List<OBIEState> selectedStates = generatedStates.stream().limit(numOfExploration)
				.collect(Collectors.toList());

		/*
		 * Call recursive
		 */
		exploreTrainingBeam(selectedStates, gold, numOfExploration, remainingCalls - 1);

	}

	/**
	 * Sorts a list of states according to their objective function score.
	 * 
	 * @param gold
	 * @param generatedStates
	 */
	private void scoreAndSort(final List<IOBIEThing> gold, final List<OBIEState> generatedStates) {
		/*
		 * Score all states so we can sort them.
		 */
		for (OBIEState genState : generatedStates) {
			final List<IOBIEThing> prediction = genState.getCurrentPrediction().getEntityAnnotations().stream()
					.map(e -> e.getAnnotationInstance()).collect(Collectors.toList());
			genState.setObjectiveScore(evaluator.f1(gold, prediction));
		}
		/*
		 * Sort
		 */
		Collections.sort(generatedStates, new Comparator<OBIEState>() {
			@Override
			public int compare(OBIEState o1, OBIEState o2) {
				return -Double.compare(o1.getObjectiveScore(), o2.getObjectiveScore());

			}
		});
	}

	/**
	 * Applies all templates to the current state to generate features.
	 * 
	 * @param newState
	 * @param template
	 */
	private void generateFeatures(List<OBIEState> selectedStates) {
		for (AbstractTemplate<OBIEInstance, OBIEState, ? extends OBIEFactorScope> template : templates) {
			forGenericTemplate(selectedStates, template);
		}
	}

	/**
	 * Method to resolve generic issues. It applies a particular template.
	 * 
	 * @param newState
	 * @param template
	 */
	private <T extends OBIEFactorScope> void forGenericTemplate(List<OBIEState> selectedStates,
			AbstractTemplate<OBIEInstance, OBIEState, T> template) {

		Set<T> allGeneratedScopesForTemplate = generateScopesAndAddToStates(template, selectedStates);

		/*
		 * Select only new scopes (or all if forced) for computation.
		 */
		Set<T> scopesToCompute = null;
		/*
		 * Extract only the ones which are not already associate with a factor.
		 */
		Set<T> newFactorScopesForTemplate = sharedFactorPool.extractNewFactorScopes(allGeneratedScopesForTemplate);

		scopesToCompute = newFactorScopesForTemplate;

		/*
		 * Compute all selected factors (in parallel).
		 */
		Set<Factor<T>> newFactors = computeNewFactors(template, scopesToCompute);

		sharedFactorPool.addFactors(newFactors);
	}

	private <T extends FactorScope> Set<Factor<T>> computeNewFactors(AbstractTemplate<OBIEInstance, OBIEState, T> t,
			Set<T> scopes) {
		Stream<T> stream = Utils.getStream(scopes, true);
		Set<Factor<T>> factors = stream.map(p -> {
			Factor<T> f = new Factor<>(p);
			t.computeFactor(f);
			// f.getFeatureVector().normalize();
			return f;
		}).collect(Collectors.toSet());

		return factors;
	}

	private <T extends FactorScope> Set<T> generateScopesAndAddToStates(
			AbstractTemplate<OBIEInstance, OBIEState, T> template, List<OBIEState> selectedStates) {
		Stream<OBIEState> stream = Utils.getStream(selectedStates, true);
		Set<T> allGeneratedScopesForTemplate = ConcurrentHashMap.newKeySet();

		stream.forEach(state -> {
			List<T> generatedScopesForState = template.generateFactorScopes(state);

			FactorGraph factorGraph = state.getFactorGraph();
			factorGraph.addFactorScopes(generatedScopesForState);
			allGeneratedScopesForTemplate.addAll(generatedScopesForState);
		});
		return allGeneratedScopesForTemplate;
	}

	/**
	 * Evaluated a given model on given data.
	 * 
	 * @param model     the mdoel
	 * @param depth     the maximum exploration depth
	 * @param instances the instances to apply the model
	 */
	private void evaluate(List<OBIEInstance> instances, IExternalScorer model) {

		final double initialScore = -1D;

		List<SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>> predictions = new ArrayList<>();

		for (OBIEInstance instance : instances) {
			OBIEState previousState = new OBIEState(instance, parameter);

			/*
			 * Get best state according to the model.
			 */
			OBIEState bestState = explorePredicting(model, previousState, initialScore);
			predictions.add(new SampledInstance<OBIEInstance, InstanceEntityAnnotations, OBIEState>(instance,
					instance.getGoldAnnotation(), bestState));
			// System.out.println("######################################");
		}

		EvaluatePrediction.evaluateREPredictions(new REObjectiveFunction(parameter), predictions, parameter.evaluator);
	}
}
