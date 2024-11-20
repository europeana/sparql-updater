FROM openlink/virtuoso-opensource-7:latest
LABEL Author="Europeana Foundation <development@europeana.eu>"

COPY ./target/sparql-updater.jar /opt/sparql-updater/sparql-updater.jar
COPY --chmod=777 ./src/docker/start_virtuoso_and_updater.sh /start_virtuoso_and_updater.sh

ENV DEBIAN_FRONTEND=noninteractive

# Install OpenJDK-17
RUN apt-get update && \
    apt-get install -y openjdk-17-jdk && \
    apt-get clean;

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64/
RUN export JAVA_HOME


VOLUME [/database /settings]
EXPOSE 8090
EXPOSE 1111
ENTRYPOINT ["/start_virtuoso_and_updater.sh"]
#ENTRYPOINT ["/virtuoso-entrypoint.sh"]




