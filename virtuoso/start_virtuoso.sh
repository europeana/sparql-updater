#!/bin/bash

if [ -z "${COPY_VIRTUOSO_DB_FROM}" ]; then
    echo "No source server defined for database files"
    if [ -f /database/virtuoso.db ]; then
        echo "Using existing database"
    else
        echo "Starting new database..."
    fi
else
    echo "Deleting Virtuoso database files..."
    rm -f /database/virtuoso.*
    echo "Copying Virtuoso database files from ${COPY_VIRTUOSO_DB_FROM}..."
    wget ${COPY_VIRTUOSO_DB_FROM}/virtuoso.pxa -P /database/
    wget ${COPY_VIRTUOSO_DB_FROM}/virtuoso.lck -P /database/
    wget ${COPY_VIRTUOSO_DB_FROM}/virtuoso.trx -P /database/
    wget ${COPY_VIRTUOSO_DB_FROM}/virtuoso.ini -P /database/
    wget ${COPY_VIRTUOSO_DB_FROM}/virtuoso.log -P /database/
    wget ${COPY_VIRTUOSO_DB_FROM}/virtuoso-temp.db -P /database/
    wget ${COPY_VIRTUOSO_DB_FROM}/virtuoso.db -P /database/
    if [ $? = 0 ]; then
        echo "Copying finished."
    else
        echo "Error copying database!"
        exit 1;
    fi
fi

echo "Starting Virtuoso..."
/virtuoso-entrypoint.sh
