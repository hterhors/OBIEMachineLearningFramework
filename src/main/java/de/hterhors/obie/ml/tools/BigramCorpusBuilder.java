package de.hterhors.obie.ml.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.AbstractOntologyEnvironment;
import de.hterhors.obie.core.ontology.OntologyInitializer;
import de.hterhors.obie.core.projects.AbstractProjectEnvironment;
import de.hterhors.obie.core.tools.corpus.CorpusFileTools;
import de.hterhors.obie.ml.corpus.BigramCorpusProvider;
import de.hterhors.obie.ml.ner.INamedEntitityLinker;

public class BigramCorpusBuilder {

	protected static Logger log = LogManager.getFormatterLogger(BigramCorpusBuilder.class);

	public static boolean overrideCorpusFileIfExists = false;

	public BigramCorpusBuilder(AbstractProjectEnvironment env, Set<Class<? extends INamedEntitityLinker>> linker,
			final String corpusPrefix, final AbstractOntologyEnvironment ontologyEnvironment) throws Exception {

		log.info("Override-flag was set to: " + overrideCorpusFileIfExists + ", "
				+ (overrideCorpusFileIfExists ? "existing corpus might be overriden!" : "corpus might not be saved."));

		OntologyInitializer.initializeOntology(ontologyEnvironment);

		final BigramCorpusProvider corpusProvider = new BigramCorpusProvider(env.getRawCorpusFile(), linker);

		storeCorpusToFile(corpusProvider, env, corpusPrefix, ontologyEnvironment.getOntologyVersion());

	}

	/**
	 * * Writes a corpus to the file-system.
	 * 
	 * @param corpus
	 * @param environment
	 * @param corpusPrefixName
	 */
	private void storeCorpusToFile(final BigramCorpusProvider corpus, AbstractProjectEnvironment environment,
			final String corpusPrefixName, final int ontologyVersion) {

		final File corpusFile = CorpusFileTools.buildAnnotatedBigramCorpusFile(
				environment.getBigramCorpusFileDirectory(), corpusPrefixName, corpus.getRawCorpus().getRootClasses(),
				ontologyVersion);

		if (corpusFile.exists()) {
			log.warn("Corpus file already exists under name: " + corpusFile);

		}

		if (!overrideCorpusFileIfExists) {
			log.warn("Do not override, discard corpus!");
			return;
		} else {
			log.warn("Override file!");
		}

		corpusFile.getParentFile().mkdirs();

		log.info("Store corpus to " + corpusFile + "...");
		try {
			FileOutputStream fileOut;
			fileOut = new FileOutputStream(corpusFile);
			final ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(corpus);
			out.close();
			fileOut.close();
			log.info("Corpus successfully stored!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
