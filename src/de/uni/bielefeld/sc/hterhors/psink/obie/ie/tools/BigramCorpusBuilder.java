package de.uni.bielefeld.sc.hterhors.psink.obie.ie.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni.bielefeld.sc.hterhors.psink.obie.core.projects.AbstractOBIEProjectEnvironment;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.tools.corpus.CorpusFileTools;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.corpus.BigramCorpusProvider;
import de.uni.bielefeld.sc.hterhors.psink.obie.ie.ner.INamedEntitityLinker;

public class BigramCorpusBuilder {

	protected static Logger log = LogManager.getFormatterLogger(BigramCorpusBuilder.class);

	public BigramCorpusBuilder(AbstractOBIEProjectEnvironment env, Set<Class<? extends INamedEntitityLinker>> linker,
			final String corpusPrefix) throws Exception {
		storeCorpusToFile(new BigramCorpusProvider(env.getRawCorpusFile(), linker), env, corpusPrefix);
	}

	/**
	 * Writes a corpus to the file-system.
	 * 
	 * @param config2
	 * 
	 * @param filename the name of the corpora.
	 * @param data     the actual data.
	 * @param env
	 */
	private void storeCorpusToFile(final BigramCorpusProvider data, AbstractOBIEProjectEnvironment env,
			final String corpusPrefix) {

		final File corpusFile = CorpusFileTools.buildAnnotatedBigramCorpusFile(env.getBigramCorpusFileDirectory(),
				corpusPrefix, data.getRawCorpus().getRootClasses(), env.getOntologyVersion());

		if (corpusFile.exists()) {
			log.warn("WARN!!! File to store corpusProvider already exists. for name: " + corpusFile
					+ ". Override file!");
		}

		log.info("Store corpus to " + corpusFile + "...");
		try {
			FileOutputStream fileOut;
			fileOut = new FileOutputStream(corpusFile);
			final ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(data);
			out.close();
			fileOut.close();
			log.info("Corpus successfully stored!");
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}
}
