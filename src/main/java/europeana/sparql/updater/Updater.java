package europeana.sparql.updater;

import europeana.sparql.updater.Dataset.State;
import europeana.sparql.updater.exception.UpdaterException;
import europeana.sparql.updater.virtuoso.CommandResult;
import europeana.sparql.updater.virtuoso.VirtuosoGraphManagerCl;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The top class that executes the SPARQL endpoint update process
 *
 */
public class Updater {

    private static final Logger LOG = LogManager.getLogger(Updater.class);

    String nodeId;
    EuropeanaSparqlEndpoint sparql;
    EuropeanaDatasetFtpServer ftpServer;
    VirtuosoGraphManagerCl sparqlGraphManager;

    public Updater(String nodeId, EuropeanaSparqlEndpoint sparql, EuropeanaDatasetFtpServer ftpServer,
                   VirtuosoGraphManagerCl sparqlGraphManager) {
        super();
        this.nodeId = nodeId;
        this.sparql = sparql;
        this.ftpServer = ftpServer;
        this.sparqlGraphManager = sparqlGraphManager;
    }

    public UpdateReport runUpdate(List<Dataset> datasets) {
        // Check if Virtuoso is up and running first, will throw error if not available in time
        sparqlGraphManager.waitUntilAvailable(60);

        List<Dataset> datasetsInFtp = ftpServer.listDatasets();
        Map<Dataset, Dataset> datasetsInSparql = sparql.listDatasets();

        // When processing only particular sets, we filter out the rest
        if (datasets != null && !datasets.isEmpty()) {
            datasetsInFtp.removeIf(e -> (!datasets.contains(e)));
            datasetsInSparql.entrySet().removeIf(e -> (!datasets.contains(e.getKey())));
        }

        // Iterate over all datasets found on the FTP server
        for (Dataset ds : datasetsInFtp) {
            ds.updateState(datasetsInSparql.get(ds));
            datasetsInSparql.remove(ds);
        }

        List<Dataset> datasetsToUpdate = new ArrayList<>(datasetsInFtp);
        if (!datasetsInSparql.isEmpty()) {
            // Found datasets in SPARQL that are not on the FTP server
            datasetsToUpdate = new ArrayList<>(datasetsInFtp);
            for (Dataset ds : datasetsInSparql.keySet()) {
                ds.setState(State.TO_REMOVE);
                datasetsToUpdate.add(ds);
            }
        }

        UpdateReport report = new UpdateReport(nodeId);
        for (Dataset ds : datasetsToUpdate) {
            try {
                CommandResult result = null;
                switch (ds.getState()) {
                    case CORRUPT -> {
                        LOG.warn("Dataset {} is corrupt and will be removed", ds.getId());
                        sparqlGraphManager.removeTmpGraph(ds.getId());
                        result = createOrUpdateDataset(ds);
                        if (result.isSuccess())
                            report.wasFixed(ds);
                        else
                            report.failed(ds, result.getErrorMessage());
                    }
                    case MISSING -> {
                        LOG.debug("Dataset {} is new and will be downloaded", ds.getId());
                        result = createOrUpdateDataset(ds);
                        if (result.isSuccess())
                            report.wasCreated(ds);
                        else
                            report.failed(ds, result.getErrorMessage());
                    }
                    case OUTDATED -> {
                        LOG.debug("Dataset {} is outdated and will be downloaded again", ds.getId());
                        result = createOrUpdateDataset(ds);
                        if (result.isSuccess())
                            report.wasUpdated(ds);
                        else
                            report.failed(ds, result.getErrorMessage());
                    }
                    case UP_TO_DATE -> {
                        report.wasUnchanged(ds);
                        LOG.trace("No changes to dataset {} ", ds.getId());
                    }
                    case TO_REMOVE -> {
                        LOG.debug("Dataset {} is no longer available and will be removed", ds.getId());
                        result = sparqlGraphManager.removeTmpGraph(ds.getId());
                        if (!result.isSuccess())
                            report.failed(ds, result.getErrorMessage());
                        else {
                            result = sparqlGraphManager.removeObsoleteGraph(ds.getId());
                            if (result.isSuccess())
                                report.wasRemoved(ds);
                            else
                                report.failed(ds, result.getErrorMessage());
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                report.failed(ds, StringUtils.isEmpty(e.getMessage()) ? "Exception " + e.getClass().getSimpleName()
                        : e.getMessage());
            }
        }
        report.setEndTime(Instant.now());
        return report;
    }

    private CommandResult createOrUpdateDataset(Dataset ds) throws UpdaterException, IOException {
        String datasetId = ds.getId();
        File outputFolder = sparqlGraphManager.getTtlImportFolder();
        File dsZipFile = new File(outputFolder, ds.getId() + ".zip");
        LOG.trace("Downloading zip file {}...", dsZipFile);
        ftpServer.download(dsZipFile, datasetId);

        prepareVirtuosoImportFiles(datasetId, dsZipFile, ds.getTimestampFtp());
        LOG.trace("Deleting zip file {}...", dsZipFile);
        dsZipFile.delete();

        Instant startTime = Instant.now();
        CommandResult res = sparqlGraphManager.ingestGraph(datasetId + "_new");
        if (res.isSuccess()) {
            res = sparqlGraphManager.removeObsoleteGraph(datasetId);
            if (res.isSuccess()) {
                res = sparqlGraphManager.renameTmpGraph(datasetId);
                Instant endTime = Instant.now();
                Duration diff = Duration.between(startTime, endTime);
                if (LOG.isInfoEnabled()) {
                    LOG.info(String.format("Dataset \"%s\" ingest duration: %d:%02d:%02d", datasetId, diff.toHours(),
                            diff.toMinutesPart(), diff.toSecondsPart()));
                }
            }
        }
        return res;
    }

    private void prepareVirtuosoImportFiles(String datasetId, File dsZipFile, Instant datasetTimestampAtFtp) throws IOException {
        File outputFolder = dsZipFile.getParentFile();
//		FileUtils.write(new File(outputFolder, datasetId+".ttl.graph"), "http://data.europeana.eu/dataset/"+datasetId, StandardCharsets.UTF_8);
        File datasetTtlFile = new File(outputFolder, datasetId + ".ttl.gz");
        if (datasetTtlFile.exists())
            datasetTtlFile.delete();

        LOG.trace("Generating TTL zip file {}...", datasetTtlFile);
        FileOutputStream datasetTtlFileStream = new FileOutputStream(datasetTtlFile);
        GZIPOutputStream gzipDatasetTtlStream = new GZIPOutputStream(datasetTtlFileStream);
        Writer writer = new OutputStreamWriter(gzipDatasetTtlStream, StandardCharsets.UTF_8);

        boolean firstRecord = true;
        int nrEntries = 0;
        final ZipInputStream zip = new ZipInputStream(new FileInputStream(dsZipFile));
        ZipEntry entry = zip.getNextEntry();
        while (entry != null) {
            String edmRdf = IOUtils.toString(zip, StandardCharsets.UTF_8);
            String[] lines = edmRdf.split("\n");
            for (String line : lines) {
                if (firstRecord || !line.startsWith("@prefix")) {
                    writer.write(line);
                    writer.write("\n");
                }
            }
            firstRecord = false;

            zip.closeEntry();
            entry = zip.getNextEntry();
            nrEntries++;
        }
        zip.close();
        LOG.trace("Adding {} entries to file {}", nrEntries, dsZipFile);

        // add the triple with the last modification timestamp from the FTP server
        writer.write("\n\n<http://data.europeana.eu/dataset/");
        writer.write(datasetId);
        writer.write("> <http://purl.org/dc/terms/modified> \"");
        writer.write(datasetTimestampAtFtp.toString());
        writer.write("\"^^<http://www.w3.org/2001/XMLSchema#dateTime> .\n");

        writer.close();
        gzipDatasetTtlStream.close();
        datasetTtlFileStream.close();
        //LOG.trace("Deleting TTL zip file {}...", datasetTtlFile);
        //Files.deleteIfExists(datasetTtlFile.toPath());
    }
}
