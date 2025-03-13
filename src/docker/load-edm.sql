checkpoint;

delete from DB.DBA.load_list;

ld_dir ('/opt/virtuoso-opensource/vad', 'edm-v527-160401.owl', 'http://www.europeana.eu/schemas/edm/');

rdf_loader_run();

DB.DBA.XML_SET_NS_DECL ('cc', 'http://creativecommons.org/ns#', 2);
DB.DBA.XML_SET_NS_DECL ('dc', 'http://purl.org/dc/elements/1.1/', 2);
DB.DBA.XML_SET_NS_DECL ('dcterms', 'http://purl.org/dc/terms/', 2);
DB.DBA.XML_SET_NS_DECL ('dqv', 'http://www.w3.org/ns/dqv#', 2);
DB.DBA.XML_SET_NS_DECL ('ebucore', 'http://www.ebu.ch/metadata/ontologies/ebucore/ebucore#', 2);
DB.DBA.XML_SET_NS_DECL ('edm', 'http://www.europeana.eu/schemas/edm/', 2);
DB.DBA.XML_SET_NS_DECL ('foaf', 'http://xmlns.com/foaf/0.1/', 2);
DB.DBA.XML_SET_NS_DECL ('odrl', 'http://www.w3.org/ns/odrl/2/', 2);
DB.DBA.XML_SET_NS_DECL ('ore', 'http://www.openarchives.org/ore/terms/', 2);
DB.DBA.XML_SET_NS_DECL ('owl', 'http://www.w3.org/2002/07/owl#', 2);
DB.DBA.XML_SET_NS_DECL ('rdaGr2', 'http://rdvocab.info/ElementsGr2/', 2);
DB.DBA.XML_SET_NS_DECL ('rdf', 'http://www.w3.org/1999/02/22-rdf-syntax-ns#', 2);
DB.DBA.XML_SET_NS_DECL ('rdfs', 'http://www.w3.org/2000/01/rdf-schema#', 2);
DB.DBA.XML_SET_NS_DECL ('scvs',	 'http://rdfs.org/sioc/services#', 2);
DB.DBA.XML_SET_NS_DECL ('skos', 'http://www.w3.org/2004/02/skos/core#', 2);
DB.DBA.XML_SET_NS_DECL ('xsd', 'http://www.w3.org/2001', 2);
DB.DBA.XML_SET_NS_DECL ('wgs84_pos', 'http://www.w3.org/2003/01/geo/wgs84_pos#', 2);

checkpoint;
