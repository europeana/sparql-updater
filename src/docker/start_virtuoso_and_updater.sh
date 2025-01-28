#!/bin/bash

# Start the updater in the background
echo "Starting SPARQL updater..."
java -jar /opt/sparql-updater/sparql-updater.jar &

# Virtuoso will be started last because it needs to be kept running
echo "Starting Virtuoso..."
/virtuoso-entrypoint.sh
