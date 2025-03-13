log_enable(2);

UPDATE DB.DBA.RDF_QUAD TABLE OPTION (index RDF_QUAD_GS)
   SET g = iri_to_id ('http://data.europeana.eu/dataset/##DATASET_ID##')
 WHERE g = iri_to_id ('http://data.europeana.eu/dataset/##DATASET_ID##_new', 0);

checkpoint;

log_enable(1);
