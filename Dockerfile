FROM openlink/virtuoso-opensource-7:latest
LABEL Author="Europeana Foundation <development@europeana.eu>"

COPY ./src/docker/load-edm.sql /initdb.d/load-edm.sql
COPY ./src/docker/edm-v527-160401.owl /opt/virtuoso-opensource/vad/edm-v527-160401.owl
COPY ./target/sparql-updater.jar /opt/sparql-updater/sparql-updater.jar
COPY --chmod=777 ./src/docker/start_virtuoso_and_updater.sh /start_virtuoso_and_updater.sh
#COPY ./src/docker/virtuoso.ini /opt/sparql-updater/virtuoso.ini

RUN mkdir -p /ingest
RUN ln -s /usr/share/proj /ttl-import

ENV DEBIAN_FRONTEND=noninteractive

# Install OpenJDK-17
RUN apt-get update && \
    apt-get install -y openjdk-17-jdk && \
    apt-get clean;

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64/
RUN export JAVA_HOME

VOLUME ["/database" "/settings" "/ingest" "/ttl-import" ]
EXPOSE 8090
EXPOSE 1111
ENTRYPOINT ["/start_virtuoso_and_updater.sh"]




