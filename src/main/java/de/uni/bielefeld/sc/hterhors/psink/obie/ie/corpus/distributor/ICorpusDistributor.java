package de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.distributor;

import java.io.Serializable;

import de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.BigramCorpusProvider;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.distributor.AbstractCorpusDistributor.Distributor;

/**
 * Classes that implement this interface contain the configuration for the
 * corpus provider.
 * 
 * @author hterhors
 *
 * @date Oct 13, 2017
 */
public interface ICorpusDistributor extends Serializable {

	Distributor distributeInstances(BigramCorpusProvider corpusProvider);

	public String getDistributorID();

}
