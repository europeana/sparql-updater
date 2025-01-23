#!/bin/bash

# If a container restarts transactions may not be committed yet. Virtuoso will try to roll forward these transactions on
# the next startup which can take a long time during which Virtuoso is not available (in some cases 10 minutes). This is
# problematic in k8s. As a workaround we always delete the transaction file (see EA-4034).
if test -f /database/virtuoso.trx; then
  echo "Deleting Virtuoso transaction file..."
  rm -f /database/virtuoso.trx
fi


# Start the updater in the background
echo "Starting SPARQL updater..."
java -jar /opt/sparql-updater/sparql-updater.jar &

# Virtuoso will be started last because it needs to be kept running
echo "Starting Virtuoso..."
/virtuoso-entrypoint.sh
