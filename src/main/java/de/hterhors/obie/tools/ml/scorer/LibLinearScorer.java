package de.hterhors.obie.tools.ml.scorer;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.bwaldvogel.liblinear.Feature;
import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;
import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.Problem;
import de.bwaldvogel.liblinear.SolverType;
import de.hterhors.obie.tools.ml.scorer.InstanceCollection.FeatureDataPoint;
import de.hterhors.obie.tools.ml.variables.OBIEState;
import utility.Utils;
import variables.AbstractState;

public class LibLinearScorer implements IExternalScorer {
	private static Logger log = LogManager.getFormatterLogger(LibLinearScorer.class.getName());

	private InstanceCollection trainingData = new InstanceCollection();
	final Parameter svmParameter;

	// Parameter svmParam
	public LibLinearScorer() {
		SolverType solver = SolverType.L1R_LR; // -s 0
//		double C = 5.12E-5; // cost of constraints violation
		double C = 0.0001; // cost of constraints violation
		// double eps = 0.01; // stopping criteria
		// double C = 0.0001; // cost of constraints violation
		double eps = 0.01; // stopping criteria

		Parameter svmParameter = new Parameter(solver, C, eps);
		this.svmParameter = svmParameter;
	}

	private Model model = null;

	public void train(InstanceCollection trainingData) {

		trainingData.getDataPoints().forEach(System.out::println);

		final int dataCount = trainingData.getDataPoints().size();
		final int totalFeatureCount = trainingData.numberOfTotalFeatures();

		log.info("Number of training instances " + dataCount);
		log.info("Number of features = " + totalFeatureCount);

		Problem problem = new Problem();
		problem.l = dataCount; // number of training examples
		problem.n = totalFeatureCount; // number of features
		problem.x = new Feature[dataCount][]; // feature nodes
		problem.y = new double[dataCount]; // target values

		this.trainingData = trainingData;

		int dataPointIndex = 0;
		for (FeatureDataPoint tdp : trainingData.getDataPoints()) {
			problem.x[dataPointIndex] = toLibLinearNodeArray(tdp);
			problem.y[dataPointIndex] = tdp.score;
			dataPointIndex++;
		}
		for (Feature[] nodes : problem.x) {
			int indexBefore = 0;
			for (Feature n : nodes) {
				if (n.getIndex() <= indexBefore) {
					System.out.println(n.getIndex());
					System.out.println(n);
					System.out.println(Arrays.toString(nodes));
					trainingData.sparseIndexMapping.entrySet().forEach(System.out::println);
					throw new IllegalArgumentException("feature nodes must be sorted by index in ascending order");
				}
				indexBefore = n.getIndex();
			}
		}

		log.info("Start training SVR...");
		this.model = Linear.train(problem, svmParameter);
		// ParameterSearchResult r = Linear.findParameterC(problem,
		// svmParameter, 2, 0.0000001, 100000);
		//
		// System.out.println(r.getBestC());
		// System.out.println(r.getBestRate());
		log.info("done");
	}

	public void saveScorer(final File modelDile) throws IOException {
		if (!modelDile.getParentFile().exists()) {
			modelDile.getParentFile().mkdirs();
		}
		File modelFile = new File(modelDile + ".liblinear");
		model.save(modelFile);
		trainingData.saveFeatureMapData(modelDile);
	}

	public void loadScorer(final File modelName) throws IOException {
		model = Model.load(new File(modelName, ".libsvm"));
		trainingData.loadFeatureMapData(modelName);
	}

	private double predict(FeatureDataPoint dp) {

		Feature[] nodes = toLibLinearNodeArray(dp);

		// double v1 = Linear.predict(model, nodes);
		// double[] probabilities = new double[model.getLabels().length];
		//
		// double v1 = Linear.predictProbability(model, nodes, probabilities);

		double[] functionValues = new double[model.getNrClass()];

		Linear.predictValues(model, nodes, functionValues);
		// System.out.println("(Actual:" + dp.score + " Prediction:" +
		// functionValues[0] + ")");
		// System.out.println("(Actual:" + dp.score + " Prediction:" +
		// Arrays.toString(functionValues) + ")");

		return functionValues[0];
	}

	private Feature[] toLibLinearNodeArray(FeatureDataPoint tdp) {
		Feature[] nodes = new Feature[tdp.featuresIndices.size()];

		int nonZeroFeatureIndex = 0;
		for (Integer featureIndex : tdp.featuresIndices) {
			Feature node = new FeatureNode(featureIndex, tdp.features.get(featureIndex));
			nodes[nonZeroFeatureIndex] = node;
			nonZeroFeatureIndex++;
		}
		return nodes;
	}

	/**
	 * Computes a score for each passed state given the individual factors. Scoring
	 * is done is done in parallel if flag is set and scorer implementation does not
	 * override this method.
	 * 
	 * @param states
	 * @param multiThreaded
	 */
	public void score(List<? extends AbstractState<?>> states, boolean multiThreaded) {
		Stream<? extends AbstractState<?>> stream = Utils.getStream(states, multiThreaded);
		stream.forEach(s -> {
			scoreSingleState(s);
		});
	}

	/**
	 * Computes the score of this state according to the trained model. The computed
	 * score is returned but also updated in the state objects <i>score</i> field.
	 * 
	 * @param state
	 * @return
	 */

	protected void scoreSingleState(AbstractState<?> state) {

		if (model == null) {
			return;
		}

		if (state instanceof OBIEState) {
			state.setModelScore(predict(((OBIEState) state).toTrainingPoint(trainingData, false)));
		} else {
			throw new IllegalArgumentException("Unknown State: " + state.getClass().getSimpleName());
		}
	}

}
