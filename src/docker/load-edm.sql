checkpoint;

delete from DB.DBA.load_list;

ld_dir ('/usr/share/proj', 'edm-v527-160401.owl', 'http://www.europeana.eu/schemas/edm/');

rdf_loader_run();

checkpoint;
