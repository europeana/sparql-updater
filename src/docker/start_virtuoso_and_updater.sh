#!/bin/bash
java -jar /opt/sparql-updater/sparql-updater.jar  > /ingest/output.txt 2>&1 &
/virtuoso-entrypoint.sh