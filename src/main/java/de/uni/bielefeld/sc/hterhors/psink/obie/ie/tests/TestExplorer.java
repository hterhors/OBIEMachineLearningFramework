package de.uni.bielefeld.sc.hterhors.psink.obie.ie.tests;

public class TestExplorer {
/**
 * TODO: Write reasonable tests!
 */

//	public static void main(String[] args) throws Exception {
//
//		// testOntologyExplorer();
//
//		// testClassAndPropertyExplorer(args);
//
//		// testPropertyCardinatlityExplorerOnIncident(args);
//
//		// testRootClassCardinatlityExplorerOnAnimalModel(args);
//
//	}
//
//	private static void testRootClassCardinatlityExplorerOnAnimalModel(String[] args) {
//
//		OBIEParameterBuilder paramBuilder = SCIOParameterBuilder.getDevelopmentREParameter();
//
//		paramBuilder.setInitializer(EInitializer.WRONG);
//
//		OBIEParameter param = paramBuilder.build();
//
//		BigramCorpusProvider corpusProvider = BigramCorpusProvider.loadCorpusFromFile(param);
//
//		InternalInstance d = corpusProvider.getTrainingCorpus().getInternalInstances().get(1);
//
//		TemplateCardinalityExplorer pc = new TemplateCardinalityExplorer(param);
//
//		OBIEState state = new OBIEState(d, param);
//		System.out.println(OBIEFormatter
//				.format(state.getCurrentPrediction().getEntityAnnotations().iterator().next().getAnnotationInstance()));
//		System.out.println("=========");
//		System.out.println("=========");
//		System.out.println("=========");
//		int size = 0;
//		List<OBIEState> generatedClasses = pc.getNextStates(state);
//
//		for (OBIEState scioClass : generatedClasses) {
//			System.out.println(scioClass);
//			System.out.println("=========");
//			size++;
//		}
//		System.out.println(size);
//	}
//
//	public static void testClassAndPropertyExplorer(String[] args)
//			throws OWLOntologyCreationException, ClassNotFoundException, NoSuchFieldException, FileNotFoundException {
//
//		// investigateIncidents();
//		investigateOrganismModel();
//		// investigateExperimentalGroup();
//	}
//
//	private static void investigateIncidents()
//			throws ClassNotFoundException, NoSuchFieldException, FileNotFoundException {
//
//		Incident goldClass = new Incident();
//		goldClass.setIncidentLocation(
//				new IncidentLocation().setCountryForeignNation(new ElSalvador()).addFirstCityPlace(new CityPlace()));
//
//		Incident baseClass = new Incident();
//		baseClass.setIncidentType(new Bombing());
//
//		OBIEParameter param = MUC34Parameter.getDevelopmentParameter().build();
//
//		BigramCorpusProvider corpusProvider = BigramCorpusProvider.loadCorpusFromFile(param);
//
//		InternalInstance d = corpusProvider.getTrainingCorpus().getInternalInstances().get(0);
//
//		TemplateExplorer cape = new TemplateExplorer(param);
//
//		printNextStates(cape, goldClass, baseClass, d, param.evaluator);
//	}
//
//	private static void investigateOrganismModel()
//			throws ClassNotFoundException, NoSuchFieldException, FileNotFoundException {
//
//		AnimalModel goldClass = new AnimalModel();
//		goldClass.setGender(new Male());
//		goldClass.setAgeCategory(new Adult());
//
//		OBIEParameterBuilder paramBuilder = SCIOParameterBuilder.getDevelopmentREParameter();
//
//		OBIEParameter param = paramBuilder.build();
//
//		BigramCorpusProvider corpusProvider = BigramCorpusProvider.loadCorpusFromFile(param);
//		InternalInstance d = corpusProvider.getTrainingCorpus().getInternalInstances().get(16);
//
//		TemplateExplorer cape = new TemplateExplorer(param);
//
//		OBIEState state = new OBIEState(d, param);
//
//		System.out.println(state.getCurrentPrediction());
//
//		List<OBIEState> generatedClasses = cape.getNextStates(state);
//		int size = 0;
//		for (OBIEState scioClass : generatedClasses) {
//			System.out.println(scioClass);
//			System.out.println("=========");
//			size++;
//		}
//		System.out.println(size);
//
//		// printNextStates(cape, goldClass, baseClass, d);
//	}
//
//	private static void investigateExperimentalGroup()
//			throws ClassNotFoundException, NoSuchFieldException, FileNotFoundException {
//
//		ExperimentalGroup goldClass = new ExperimentalGroup();
//
//		IOrganismModel organismModel = new RatModel();
//		organismModel.setGender(new Male());
//		organismModel.setAgeCategory(new Adult());
//		organismModel.setOrganismSpecies(new WistarRat());
//		goldClass.setOrganismModel(organismModel);
//
//		OBIEParameterBuilder paramBuilder = SCIOParameterBuilder.getDevelopmentREParameter();
//
//		paramBuilder.setInitializer(EInitializer.EMPTY);
//		paramBuilder.setExploreExistingTemplates(true);
//
//		OBIEParameter param = paramBuilder.build();
//
//		BigramCorpusProvider corpusProvider = BigramCorpusProvider.loadCorpusFromFile(param);
//		InternalInstance d = corpusProvider.getTrainingCorpus().getInternalInstances().get(2);
//		System.out.println(d);
//
//		OBIEState state = new OBIEState(d, param);
//
//		TemplateExplorer cape = new TemplateExplorer(param);
//
//		List<OBIEState> generatedClasses = cape.getNextStates(state);
//		int size = 0;
//		for (OBIEState scioClass : generatedClasses) {
//			System.out.println(scioClass);
//			System.out.println("=========");
//			size++;
//		}
//		System.out.println(size);
//	}
//
//	private static void testOntologyExplorer()
//			throws ClassNotFoundException, NoSuchFieldException, FileNotFoundException {
//
//		AnimalModel goldClass = new AnimalModel();
//		goldClass.setGender(new Male());
//		goldClass.setAgeCategory(new Adult());
//
//		AnimalModel baseClass = new AnimalModel();
//		baseClass.setAgeCategory(new AgeCategory());
//
//		// .setAge(new Age("---")).setAgeCategory(new AgeCategory())
//		// .setGender(new Gender()).setWeight(new
//		// Weight("--")).setOrganismSpecies(new OrganismSpecies());
//		OBIEParameterBuilder paramBuilder = SCIOParameterBuilder.getDevelopmentREParameter();
//
//		paramBuilder.setExploreOnOntologyLevel(true);
//
//		OBIEParameter param = paramBuilder.build();
//
//		BigramCorpusProvider corpusProvider = BigramCorpusProvider.loadCorpusFromFile(param);
//
//		InternalInstance d = corpusProvider.getTrainingCorpus().getInternalInstances().get(16);
//
//		TemplateExplorer cape = new TemplateExplorer(param);
//
//		printNextStates(cape, goldClass, baseClass, d, param.evaluator);
//	}
//
//	private static void printNextStates(TemplateExplorer recusiveClassExplorer, IOBIEThing goldClass,
//			IOBIEThing baseClass, InternalInstance d, IEvaluator evaluator) throws NoSuchFieldException {
//		System.out.println("Search in document = " + d);
//		System.out.println("Gold class:\n" + OBIEFormatter.format(goldClass));
//		System.out.println("___________________________________");
//		try {
//			int size = 0;
//			System.out.println("Base class:\n" + OBIEFormatter.format(baseClass));
//			System.out.println("Score from gold to base class = " + evaluator.f1(goldClass, baseClass));
//
//			System.out.println("___________________");
//
//			for (StateInstancePair scioClass : recusiveClassExplorer.topDownRecursiveFieldFilling(d, baseClass,
//					baseClass.getClass().getAnnotation(DirectInterface.class).get(),
//					baseClass.getClass().getAnnotation(DirectInterface.class).get(), 0, true)) {
//				System.out.println(OBIEFormatter.format(scioClass.instance, true));
//				System.out.println("Kartesian score: " + evaluator.f1(goldClass, scioClass.instance));
//				System.out.println("+++++++++++++++++++");
//				size++;
//			}
//			System.out.println(size);
//
//		} catch (InstantiationException | IllegalAccessException e) {
//			e.printStackTrace();
//		}
//	}

}
