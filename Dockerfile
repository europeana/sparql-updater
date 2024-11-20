FROM openlink/virtuoso-opensource-7:latest
LABEL Author="Europeana Foundation <development@europeana.eu>"

COPY ./target/sparql-updater.jar /opt/sparql-updater/sparql-updater.jar
COPY ./src/docker/start_virtuoso_and_updater.sh /start_virtuoso_and_updater.sh


VOLUME [/database /settings]
EXPOSE 8090
EXPOSE 1111
ENTRYPOINT ["/start_virtuoso_and_updater.sh"]




