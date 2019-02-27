package de.hterhors.obie.ml.templates;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hterhors.obie.core.ontology.AbstractIndividual;
import de.hterhors.obie.core.ontology.ReflectionUtils;
import de.hterhors.obie.core.ontology.annotations.DatatypeProperty;
import de.hterhors.obie.core.ontology.annotations.RelationTypeCollection;
import de.hterhors.obie.core.ontology.interfaces.IDatatype;
import de.hterhors.obie.core.ontology.interfaces.IOBIEThing;
import de.hterhors.obie.ml.run.AbstractRunner;
import de.hterhors.obie.ml.templates.GenericMainTemplatePriorTemplate.Scope;
import de.hterhors.obie.ml.variables.OBIEState;
import de.hterhors.obie.ml.variables.TemplateAnnotation;
import factors.Factor;
import factors.FactorScope;

/**
 * This template creates a prior for each slot filler candidate given the
 * training data. This results in taking always that slot filler candidate that
 * appears most in the training data for its corresponding slot.
 * 
 * For that a feature is created for each assigned slot value class for each
 * property of the currently observed entity annotation.
 * 
 * @author hterhors
 *
 */
public class GenericMainTemplatePriorTemplate extends AbstractOBIETemplate<Scope> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Logger log = LogManager.getFormatterLogger(GenericMainTemplatePriorTemplate.class.getName());

	private static final String MAIN_CLASS_IDENTIFIER = "main_class";

	private static final String MAIN_INDIVIDUAL_IDENTIFIER = "main_individual";

	public GenericMainTemplatePriorTemplate(AbstractRunner runner) {
		super(runner);
	}

	class Scope extends FactorScope {

		final String slotName;
		final String slotValue;

		public Scope(AbstractOBIETemplate<Scope> template, String propertyName, String assignedClassesNameOrValue) {
			super(template, propertyName, assignedClassesNameOrValue);
			this.slotValue = assignedClassesNameOrValue;
			this.slotName = propertyName;
		}

	}

	@Override
	public List<Scope> generateFactorScopes(OBIEState state) {
		List<Scope> factors = new ArrayList<>();

		/*
		 * For all soccer player in the document create an individual factor scope.
		 *
		 * In the lecture corpus there is only one soccer player per document!
		 *
		 */
		for (TemplateAnnotation entityAnnotation : state.getCurrentTemplateAnnotations().getTemplateAnnotations()) {

			IOBIEThing thing = entityAnnotation.getThing();

			/*
			 * If the observed soccerPlayer annotation is null we don't need to create any
			 * features.
			 */
			if (thing == null)
				continue;

			/**
			 * If we are interested in the class type we can store this here. Usually the
			 * class type is not important if the individual was found! However, in some
			 * cases it might make sense.
			 */
			addScope(factors, getClassScope(MAIN_CLASS_IDENTIFIER, thing));

			/**
			 * If we are interested in the actual individual, we can store this here. We
			 * assume that the name is sufficient enough to distinguish between all
			 * individuals. This assumption might not hold. In that case the namespace
			 * should also be part of the feature!
			 */
			addScope(factors, getIndividualScope(MAIN_INDIVIDUAL_IDENTIFIER, thing));

			/**
			 * Given the soccer player ontology we are not interested in any classes. As no
			 * class contains any (important) sub classes.
			 */

			for (Field field : ReflectionUtils.getSlots(thing.getClass(), thing.getInvestigationRestriction())) {
				try {
					final String context = field.getName();

					if (field.isAnnotationPresent(RelationTypeCollection.class)) {

						if (ReflectionUtils.isAnnotationPresent(field, DatatypeProperty.class)) {

							List<IDatatype> slotFillers = (List<IDatatype>) field.get(thing);

							for (IDatatype slotFiller : slotFillers) {
								if (slotFiller != null) {
									addScope(factors, getDatatypeScope(context, slotFiller));
								}
							}
						} else {
							List<IOBIEThing> slotFillers = (List<IOBIEThing>) field.get(thing);

							for (IOBIEThing slotFiller : slotFillers) {
								if (slotFiller != null) {
									addScope(factors, getIndividualScope(context, slotFiller));
								}
							}
						}

					} else {

						if (ReflectionUtils.isAnnotationPresent(field, DatatypeProperty.class)) {
							IDatatype slotFiller = (IDatatype) field.get(thing);

							if (slotFiller != null)
								addScope(factors, getDatatypeScope(context, slotFiller));

						} else {

							IOBIEThing slotFiller = (IOBIEThing) field.get(thing);
							if (slotFiller != null)
								addScope(factors, getIndividualScope(context, slotFiller));
						}
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		}

		return factors;
	}

	/*
	 * Take each entry in a list slot individually instead of all together.
	 */
//	private Scope getIndividualScope(String slotIdentifier, List<? extends IOBIEThing> slotFillerValues) {
//
//		if (slotFillerValues == null)
//			return null;
//
//		final Set<String> distinctValues = new HashSet<String>(slotFillerValues.size());
//
//		for (IOBIEThing thing : slotFillerValues) {
//
//			if (thing == null) {
//				continue;
//			}
//
//			final AbstractIndividual individual = thing.getIndividual();
//
//			if (individual == null) {
//				continue;
//			}
//
//			distinctValues.add(getIndividualID(individual));
//		}
//
//		if (distinctValues.isEmpty())
//			return null;
//
//		return new Scope(this, slotIdentifier, distinctValues);
//	}

	private String getIndividualID(AbstractIndividual individual) {
		// spi.nameSpace +
		return individual.name;
	}

	private void addScope(List<Scope> factors, Scope scope) {
		if (scope != null)
			factors.add(scope);
	}

	/**
	 * If we are interested in capturing the datatype value of a datatype property
	 * slot we can add this here.
	 * 
	 * @param birthYearPropertyIdentifier the identifier to store the value
	 * @param datatypeSlotValue           the filler of the slot
	 */
	private Scope getDatatypeScope(String slotIdentifier, IDatatype datatypeSlotValue) {
		return new Scope(this, slotIdentifier, datatypeSlotValue.getInterpretedValue());
	}

	/**
	 * If we are interested in the actual individual, we can store this here. We
	 * assume that the name is sufficient enough to distinguish between all
	 * individuals. This assumption might not hold. In that case the namespace
	 * should also be part of the feature!
	 * 
	 * @param classTypeIdentifier the identifier to store the value
	 * @param soccerPlayerThing   the filler of the slot
	 */
	private Scope getIndividualScope(String slotIdentifier, IOBIEThing soccerPlayerThing) {

		if (soccerPlayerThing == null)
			return null;

		final AbstractIndividual individual = soccerPlayerThing.getIndividual();

		if (individual == null)
			return null;

		return new Scope(this, slotIdentifier, getIndividualID(individual));
	}

	/**
	 * If we are interested in the class type we can store this here. Usually the
	 * class type is not important if the individual was found! However, in some
	 * cases it might make sense.
	 * 
	 * @param identifier      the identifier to store the value
	 * @param assignedClasses the map to store the value
	 * @param thing           the filler of the slot
	 */
	private Scope getClassScope(final String identifier, IOBIEThing thing) {

		if (thing == null)
			return null;

		return new Scope(this, identifier, ReflectionUtils.simpleName(thing.getClass()));

	}

	@Override
	public void computeFactor(Factor<Scope> factor) {

		/*
		 * If the property had multiple values, as in one-to-many relations (such as
		 * hasTeams) we create a feature that takes both values into account. This might
		 * be very sparse.
		 */
//		if (factor.getFactorScope().assignedClassesNameOrValue.size() > 1)
//			factor.getFeatureVector().set("Prior towards: " + factor.getFactorScope().propertyName + "->"
//					+ factor.getFactorScope().assignedClassesNameOrValue, true);

//		for (String assignedClassesNamesOrValue : ) {

		/*
		 * For each property and for each value for that property create a single
		 * feature.
		 */
		factor.getFeatureVector()
				.set("Prior: " + factor.getFactorScope().slotName + "->" + factor.getFactorScope().slotValue, true);
//		}
	}

}
