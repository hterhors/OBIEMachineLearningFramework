package de.hterhors.obie.ml.utils;

import java.io.File;
import java.util.Set;

import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.corpus.BigramCorpusProvider;
import de.hterhors.obie.ml.corpus.distributor.ActiveLearningDistributor;
import de.hterhors.obie.ml.corpus.distributor.FoldCrossCorpusDistributor;
import de.hterhors.obie.ml.run.InvestigationRestriction;
import de.hterhors.obie.ml.run.InvestigationRestriction.RestrictedField;
import de.hterhors.obie.ml.run.param.OBIERunParameter;

public class ModelFileNameUtils {

	private static final String NO_INVESTIGATION_RESTRICTION = "full";
	private static final String TYPE_INVESTIGATION = "_type";
	private static final String ALL_SLOTS = "allSlots";
	private static final String NO_SLOTS = "noSlots";

	private static String restrictionsToString(InvestigationRestriction investigationRestriction) {

		if (investigationRestriction.investigateClassType && investigationRestriction.noRestrictionOnFields())
			return NO_INVESTIGATION_RESTRICTION;

		final StringBuffer n = new StringBuffer();

		if (investigationRestriction.investigateClassType) {
			n.append(TYPE_INVESTIGATION);
		}

		n.append(restrictionsToString(investigationRestriction.getFieldNamesRestrictions()));

		return n.toString();
	}

	private static String clazzSetToString(Set<Class<? extends IOBIEThing>> rootClassTypes) {

		final StringBuffer s = new StringBuffer();

		for (Class<? extends IOBIEThing> class1 : rootClassTypes) {
			s.append(class1.getSimpleName()).append(" ");
		}

		return s.substring(0, s.length() - 1);
	}

	private static String restrictionsToString(Set<RestrictedField> slots) {

		if (slots == null)
			return ALL_SLOTS;

		if (slots.isEmpty())
			return NO_SLOTS;

		final StringBuffer s = new StringBuffer();

		for (RestrictedField restrictedField : slots) {
			if (restrictedField.isMainField)
				s.append(restrictedField.fieldName).append("_");
		}

		return s.substring(0, s.length() - 1);
	}

	private static File buildModelDirectory(final OBIERunParameter parameter) {

		final StringBuffer modelDir = new StringBuffer();

		modelDir.append("bigram/models/").append("/development/")
				.append(parameter.corpusDistributor.getDistributorID() + "/")
				.append(clazzSetToString(parameter.rootSearchTypes)).append("/")
				.append(restrictionsToString(parameter.investigationRestriction)).append("/").append(parameter.runID)
				.append("/");

		return new File(parameter.rootDirectory, modelDir.toString());
	}

	private static String buildModelName(final OBIERunParameter parameter, final BigramCorpusProvider corpusProvider,
			final int epoch) {
		final StringBuffer modelName = new StringBuffer();
		modelName.append(parameter.runID);
		modelName.append("_");
		modelName.append(getConfigurationTypePrefix(parameter, corpusProvider));
		modelName.append("_epoch_");
		modelName.append(epoch);
		return modelName.toString();
	}

	public static File getModelInfoFile(final OBIERunParameter parameter) {

		final File modelDir = buildModelDirectory(parameter);

		if (!modelDir.exists()) {
			modelDir.mkdirs();
		}

		return new File(modelDir, parameter.runID + ".info");
	}

	private static String getConfigurationTypePrefix(OBIERunParameter parameter, BigramCorpusProvider corpusProvider) {

		if (parameter.corpusDistributor instanceof FoldCrossCorpusDistributor) {
			return parameter.corpusDistributor.getDistributorID() + corpusProvider.getCurrentFoldIndex();
		} else if (parameter.corpusDistributor instanceof ActiveLearningDistributor) {
			return parameter.corpusDistributor.getDistributorID() + corpusProvider.getCurrentActiveLearningIteration();
		} else {
			return parameter.corpusDistributor.getDistributorID();
		}
	}

	public static File getModelFile(OBIERunParameter parameter, BigramCorpusProvider corpusProvider, final int epoch) {
		return new File(buildModelDirectory(parameter), buildModelName(parameter, corpusProvider, epoch));
	}
}
