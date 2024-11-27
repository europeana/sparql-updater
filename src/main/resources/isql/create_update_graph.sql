SPARQL CLEAR GRAPH  <http://data.europeana.eu/dataset/##DATASET_ID##_new>; 

checkpoint;

delete from DB.DBA.load_list;

ld_dir ('##IMPORT_FOLDER##', '##TTL_FILENAME##.ttl.gz', 'http://data.europeana.eu/dataset/##DATASET_ID##');

rdf_loader_run();

checkpoint;

sparql select 'Result triples: ', count(*) FROM <http://data.europeana.eu/dataset/##DATASET_ID##> WHERE {?s ?p ?o};







