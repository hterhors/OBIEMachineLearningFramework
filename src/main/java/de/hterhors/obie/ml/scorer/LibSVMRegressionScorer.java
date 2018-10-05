package de.hterhors.obie.ml.scorer;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.ml.scorer.InstanceCollection.FeatureDataPoint;
import de.hterhors.obie.ml.variables.OBIEState;
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;
import utility.Utils;
import variables.AbstractState;

public class LibSVMRegressionScorer implements IExternalScorer {
	private static Logger log = LogManager.getFormatterLogger(LibSVMRegressionScorer.class.getName());

	private InstanceCollection trainingData = new InstanceCollection();
	final svm_parameter svmParam;

	public LibSVMRegressionScorer(svm_parameter svmParam) {
		this.svmParam = svmParam;
	}

	private svm_model model = null;

	public void train(InstanceCollection trainingData) {
		this.trainingData = trainingData;

		svm_problem prob = new svm_problem();
		final int dataCount = trainingData.getDataPoints().size();
		final int totalFeatureCount = trainingData.numberOfTotalFeatures();

		log.info("Number of training instances " + dataCount);
		log.info("Number of features = " + totalFeatureCount);
		prob.y = new double[dataCount];
		prob.l = dataCount;
		prob.x = new svm_node[dataCount][];
		int dataPointIndex = 0;
		for (FeatureDataPoint tdp : trainingData.getDataPoints()) {
			prob.x[dataPointIndex] = toLibSVMNodeArray(tdp.features);
			prob.y[dataPointIndex] = tdp.score;
			dataPointIndex++;
		}

		log.info("Start training SVR...");
		this.model = svm.svm_train(prob, svmParam);
		log.info("done");
	}

	public void saveScorer(final File modelDile) throws IOException {
		if (!modelDile.getParentFile().exists()) {
			modelDile.getParentFile().mkdirs();
		}
		svm.svm_save_model(modelDile + ".libsvm", model);
		trainingData.saveFeatureMapData(modelDile);
	}

	public void loadScorer(final File modelName) throws IOException {
		model = svm.svm_load_model(new File(modelName, ".libsvm").getAbsolutePath());
		trainingData.loadFeatureMapData(modelName);
	}

	private double predict(FeatureDataPoint dp) {
		Map<Integer, Double> features = dp.features;

		svm_node[] nodes = toLibSVMNodeArray(features);

		double v1 = svm.svm_predict(model, nodes);

//		System.out.println("(Actual:" + dp.score + " Prediction:" + v1 + ")");

		return v1;
	}

	private svm_node[] toLibSVMNodeArray(Map<Integer, Double> features) {
		svm_node[] nodes = new svm_node[features.size()];

		int nonZeroFeatureIndex = 0;
		for (Entry<Integer, Double> feature : features.entrySet()) {
			svm_node node = new svm_node();
			node.index = feature.getKey();
			node.value = feature.getValue();

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
