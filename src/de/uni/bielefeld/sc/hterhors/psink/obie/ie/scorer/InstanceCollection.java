package de.uni.bielefeld.sc.hterhors.psink.obie.ie.scorer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import de.uni.bielefeld.sc.hterhors.psink.obie.ie.tools.baseline.SVRSampleBaseline;

public class InstanceCollection {
	final private Collection<FeatureDataPoint> dataPoints;
	public Map<String, Integer> sparseIndexMapping;

	public InstanceCollection() {
		this.dataPoints = Collections.synchronizedList(new ArrayList<>());
		this.sparseIndexMapping = new ConcurrentHashMap<>();
	}

	public void addFeatureDataPoint(final FeatureDataPoint fdp) {
		this.dataPoints.add(fdp);
	}

	static public class FeatureDataPoint {

		final public Map<Integer, Double> features;
		final public List<Integer> featuresIndices = new ArrayList<>();
		public double score;

		public FeatureDataPoint(InstanceCollection data, Map<String, Double> features, double score, boolean training) {

			this.features = new HashMap<>();

			for (Entry<String, Double> feature : features.entrySet()) {
				final Integer featureIndex = data.getFeatureIndex(feature.getKey(), training);

				/*
				 * Do not include features that are not present in the training feature set.
				 */
				if (featureIndex == null)
					continue;

				this.features.put(featureIndex, feature.getValue());

				this.featuresIndices.add(featureIndex);
			}
			Collections.sort(featuresIndices);
			this.score = score;
		}

		/**
		 * This method was used t change the score after the instance was created. Use
		 * this method only in non-bire environment like the SVR-Baseline
		 * {@link SVRSampleBaseline}.
		 * 
		 * @param newObjectiveScore
		 * @return this.
		 */
		public FeatureDataPoint setScore(double newObjectiveScore) {
			this.score = newObjectiveScore;
			return this;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((features == null) ? 0 : features.hashCode());
			long temp;
			temp = Double.doubleToLongBits(score);
			result = prime * result + (int) (temp ^ (temp >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			FeatureDataPoint other = (FeatureDataPoint) obj;
			if (features == null) {
				if (other.features != null)
					return false;
			} else if (!features.equals(other.features))
				return false;
			if (Double.doubleToLongBits(score) != Double.doubleToLongBits(other.score))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "FeatureDataPoint [features=" + features + ", score=" + score + "]";
		}

	}

	/**
	 * Returns the index of the given feature. If the feature is not presented in
	 * the feature map the index is automatically increased if the data point
	 * belongs to the training data.
	 * 
	 * @param feature  the feature
	 * @param training if the data point belongs to training
	 * @param offset   the index offset, libLinear starts counting at 1...
	 * @return the index of the feature, or null if mode is prediction and the
	 *         feature is not present.
	 */
	private synchronized Integer getFeatureIndex(final String feature, final boolean training) {
		if (training)
			if (!sparseIndexMapping.containsKey(feature))
				/**
				 * CHECKME: !!!!NOTE!!!!
				 *
				 * LibLinear feature index starts at 1; Put +1 here.
				 *
				 */

				sparseIndexMapping.put(feature, sparseIndexMapping.size() + 0);
		return sparseIndexMapping.get(feature);
	}

	public Collection<FeatureDataPoint> getDataPoints() {
		return dataPoints;
	}

	public int numberOfTotalFeatures() {
		return sparseIndexMapping.size();
	}

	@SuppressWarnings("unchecked")
	protected void loadFeatureMapData(final File fileName) {
		try {
			FileInputStream fileIn = new FileInputStream(new File(fileName, ".feature.index.ser"));
			ObjectInputStream in = new ObjectInputStream(fileIn);
			sparseIndexMapping = (Map<String, Integer>) in.readObject();
			in.close();
			fileIn.close();
		} catch (IOException i) {
			i.printStackTrace();
			return;
		} catch (ClassNotFoundException c) {
			c.printStackTrace();
			return;
		}
	}

	protected void saveFeatureMapData(final File fileName) {
		try {
			FileOutputStream fileOut = new FileOutputStream(new File(fileName, ".feature.index.ser"));
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(sparseIndexMapping);
			out.close();
			fileOut.close();
		} catch (IOException i) {
			i.printStackTrace();
		}
	}

}
