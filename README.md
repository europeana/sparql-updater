# SPARQL updater
Software to automatically fill a Virtuoso DB with Europeana datasets and update it regularly

# Steps for local testing
1. Check if the configuration in the file `/src/main/resources/sparql-updater.user.properties` is present and correct
2. Run `mvn clean install` to create the file `/target/sparql-updater.jar`.
   This file contains the code to automatically load sets from the Europeana FTP server and write it to Virtuoso.
   It will also check regularly if datasets were modified and if so will update Virtuoso.
3. Run `docker build . -t europeana/sparql-updater-virtuoso` to create a Docker image containing both
   Virtuoso and the sparql-updater.jar
4. Start everything using the file `docker-compose-development-environment.yml`. The Virtuoso GUI is available
   via http://localhost:8890/

Some things to be aware of:
* Loading all Europeana datasets will require around 500GB of disk space!
* For local testing purposes we use a hard-coded password, see the `docker-compose-development-environment.yml` file
* After startup a folder named `/volumes` is created containing 3 subfolders
    * `/database` containing the virtuoso data files
    * `/ingest` containing the sparql-updater's log file and a subfolder with generated sql files (only filled during processing)
    * `/ttl-import` containing files downloaded from the ftp-server (only filled during processing)
* You can check which datasets are loaded using this SPARQL query: `SELECT DISTINCT ?g WHERE { GRAPH ?g {?s a ?o} }`

If you are making changes to the sparql-updater don't forget to
1. Rebuild the jar
2. Rebuild the Docker image
3. Recreate the container 
