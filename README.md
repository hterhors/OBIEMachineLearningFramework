# OBIEMachineLearningFramework

Repository of the ontology based information extraction machine learning framework.

**NOTE** This project is still under heavy development!

**Dependiecies**
You need the following dependent projects:

1)  OBIECore https://github.com/hterhors/OBIECore
2)  BIRE https://github.com/ag-sc/BIRE  (**simplified-api branch**)

**Related Projects, Implementations / Examples**
1) OWL2JavaBin https://github.com/hterhors/OWL2JavaBin is a tool taht can be used to convert ontologies written in OWL into java binaries which are used in the OBIE-ML-Framework.
2) SoccerPlayerOntology https://github.com/hterhors/SoccerPlayerOntology is an example ontology that was generated with OWL2javaBin. It contains the OWL file and the resulting java binaries. 
3) SoccerPlayerOBIEProject https://github.com/hterhors/SoccerPlayerOBIEProject is a project that works with the generated SoccerPalyerOntology. It contains example source code for
  i) the information extraction task using the OBIE MachineLearningFramework (incl. template / feature generation), 
  ii) how to convert an OWL to java binaries. 
  It further, contains an examplary annotated data set that was automatically generated from Wikipedia/dbpedia data using the DBPediaDatasetExtraction project.
4)  OBIECore https://github.com/hterhors/OBIECore contains core source code for all OBIE-related projects. 

**Description**

This project builds on the BIRE framework that implements probabilistic graphical models with factor graphs (basically Conditional Random Fields). 

The Ontology Based Information Extraction (OBIE) framework abstracts from BIRE by implementing all necessary methods in a generic way, that is specififed with any ontology that was generated using the OWL2javaBin project. 

**Usage**

The project can be used out-of-the-box. Parameter and configurations are project dependent and passed individaully for each project.


For Relation Extraction problems create a new StandardRERunner and pass all necessary parameter.
This class contains methods for loading, training, storing a model. 
How to use this project in particular, see the example project SoccerPlayerOBIEProject.


