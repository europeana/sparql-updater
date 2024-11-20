#!/bin/bash
#cp -n /opt/sparql-updater/virtuoso.ini /database/virtuoso.ini
java -jar /opt/sparql-updater/sparql-updater.jar  > /ingest/output.txt 2>&1 &
/virtuoso-entrypoint.sh