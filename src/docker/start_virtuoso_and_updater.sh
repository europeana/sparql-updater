#!/bin/bash
#cp -n /opt/sparql-updater/virtuoso.ini /database/virtuoso.ini

# Start the updater and write logs to file /ingest/output.txt
echo "Starting SPARQL updater..."
java -jar /opt/sparql-updater/sparql-updater.jar  > /ingest/sparql-updater.log 2>&1 &

# Virtuoso will be started last because it needs to be kept running
echo "Starting Virtuoso..."
/virtuoso-entrypoint.sh
