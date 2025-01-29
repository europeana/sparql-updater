#!/bin/bash

if [ "$DELETE_VIRTUOSO_DB" == "true" ]; then
  echo "Deleting Virtuoso database files..."
  rm -f /database/virtuoso.*
fi

# Start the updater in the background
echo "Starting SPARQL updater..."
java -jar /opt/sparql-updater/sparql-updater.jar &

# Virtuoso will be started last because it needs to be kept running
echo "Starting Virtuoso..."
/virtuoso-entrypoint.sh
