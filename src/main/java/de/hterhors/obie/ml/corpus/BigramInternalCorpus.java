package de.hterhors.obie.ml.corpus;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hterhors.obie.ml.variables.OBIEInstance;

/**
 * Container to store BiGram related documents. A BiGram document contains
 * annotation information in form of training or test instance.
 * 
 * @author hterhors
 *
 *         Mar 23, 2017
 */
final public class BigramInternalCorpus implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private final List<OBIEInstance> instances;

	/**
	 * InstanceName to index in list.
	 */
	private Map<String, Integer> instanceIndexMap = new HashMap<>();

	public BigramInternalCorpus(List<OBIEInstance> documents) {

		for (OBIEInstance internalInstance : documents) {
			instanceIndexMap.put(internalInstance.getName(), instanceIndexMap.size());
		}
		this.instanceIndexMap = Collections.unmodifiableMap(instanceIndexMap);
		this.instances = Collections.unmodifiableList(documents);
	}

	public BigramInternalCorpus(BigramInternalCorpus trainingCorpus, BigramInternalCorpus developmentCorpus,
			BigramInternalCorpus testCorpus) {
		this(collect(trainingCorpus, developmentCorpus, testCorpus));
	}

	private static List<OBIEInstance> collect(BigramInternalCorpus trainingCorpus,
			BigramInternalCorpus developmentCorpus, BigramInternalCorpus testCorpus) {
		List<OBIEInstance> full = new ArrayList<>();
		full.addAll(trainingCorpus.getInternalInstances());
		full.addAll(developmentCorpus.getInternalInstances());
		full.addAll(testCorpus.getInternalInstances());
		return full;
	}

	public List<OBIEInstance> getInternalInstances() {
		return instances;
	}

	public OBIEInstance getInternalInstanceByName(final String name) throws IllegalArgumentException {

		if (!instanceIndexMap.containsKey(name)) {
			throw new IllegalArgumentException(
					"Instance does not exist: " + name + ". Existing instances: " + instanceIndexMap);
		}

		return instances.get(instanceIndexMap.get(name));
	}

	@Override
	public String toString() {
		return "InternalCorpus [instances=" + instances + "]";
	}

}
