log_enable(2);

SPARQL CLEAR GRAPH  <http://data.europeana.eu/dataset/##DATASET_ID##_new>;

delete from DB.DBA.load_list;

ld_dir ('##IMPORT_FOLDER##', '##TTL_FILENAME##.ttl.gz', 'http://data.europeana.eu/dataset/##DATASET_ID##');

rdf_loader_run();

log_enable(1);

sparql select 'Result triples: ', count(*) FROM <http://data.europeana.eu/dataset/##DATASET_ID##> WHERE {?s ?p ?o};
