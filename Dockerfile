FROM openlink/virtuoso-opensource-7:7.2
LABEL org.opencontainers.image.vendor="Europeana Foundation" \
      org.opencontainers.image.authors="api@europeana.eu" \
      org.opencontainers.image.documentation="https://pro.europeana.eu/page/apis" \
      org.opencontainers.image.source="https://github.com/europeana/sparql-updater" \
      org.opencontainers.image.licenses="EUPL-1.2"

# Install OpenJDK-17
ENV DEBIAN_FRONTEND=noninteractive
USER root
RUN apt-get update
RUN apt-get install wget -y
RUN apt-get install -V -y openjdk-17-jdk
RUN apt-get clean

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64/
RUN export JAVA_HOME

# Configure APM and add APM agent
ENV ELASTIC_APM_VERSION=1.48.1
ADD https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/$ELASTIC_APM_VERSION/elastic-apm-agent-$ELASTIC_APM_VERSION.jar /usr/local/elastic-apm-agent.jar

# Install Virtuoso config files
COPY ./src/docker/load-edm.sql /initdb.d/load-edm.sql
COPY ./src/docker/edm-v527-160401.owl /opt/virtuoso-opensource/vad/edm-v527-160401.owl
# Folder /database/tmp-ingest is used to temporarily store files for ingestion and Virtuoso should be
# configured (when first started) to have access to that folder
ENV VIRT_Parameters_DirsAllowed=.,../vad,/database/tmp-ingest
# Optimization assuming we have at least 4GB RAM, see also https://vos.openlinksw.com/owiki/wiki/VOS/VirtRDFPerformanceTuning
ENV VIRT_Parameters_NumberOfBuffers=340000
ENV VIRT_Parameters_MaxDirtyBuffers=250000

#ENV DELETE_VIRTUOSO_DB=true

# Install SPARQL updater
COPY ./target/sparql-updater.jar /opt/sparql-updater/sparql-updater.jar

COPY --chmod=777 ./src/docker/start_virtuoso_and_updater.sh /start_virtuoso_and_updater.sh

EXPOSE 8890
EXPOSE 1111
ENTRYPOINT ["/start_virtuoso_and_updater.sh"]




