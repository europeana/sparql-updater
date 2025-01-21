package europeana.sparql.updater;

import europeana.sparql.updater.Dataset.State;
import europeana.sparql.updater.exception.UpdaterException;
import europeana.sparql.updater.exception.VirtuosoCmdLineException;
import europeana.sparql.updater.virtuoso.CommandResult;
import europeana.sparql.updater.virtuoso.EuropeanaSparqlClient;
import europeana.sparql.updater.virtuoso.VirtuosoGraphManagerCl;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service that runs the SPARQL endpoint update process
 */
public class UpdaterService {

    private static final Logger LOG = LogManager.getLogger(UpdaterService.class);

    private static final int VIRTUOSO_MAX_WAIT_TIME = 60; // seconds
    String serverId;
    EuropeanaSparqlClient sparql;
    EuropeanaDatasetFtpServer ftpServer;
    VirtuosoGraphManagerCl sparqlGraphManager;
    File storageLocation;

    /**
     * Initialize a new updater service
     * @param serverId the id of the server on which the update is done
     * @param ftpServer the Europeana FTP server that hosts the dataset sources
     * @param sparql a Europeana sparql client for doing sparql queries
     * @param virtuosoGraphManangerCl the command-line utility for interacting with Virtuoso (isql)
     * @param storageLocation optional, any file or directory located on the drive on which to report disk usage
     */
    public UpdaterService(String serverId, EuropeanaDatasetFtpServer ftpServer, EuropeanaSparqlClient sparql,
                          VirtuosoGraphManagerCl virtuosoGraphManangerCl, File storageLocation) {
        this.serverId = serverId;
        this.ftpServer = ftpServer;
        this.sparql = sparql;
        this.sparqlGraphManager = virtuosoGraphManangerCl;
        this.storageLocation = storageLocation;
    }

    /**
     * Start an update
     * @param datasets only update the provided list of datasets, if null all datasets are updated
     * @return an UpdateReport
     * @throws VirtuosoCmdLineException when Virtuoso is not available
     */
    public UpdateReport runUpdate(List<Dataset> datasets) throws VirtuosoCmdLineException {
        // Check if Virtuoso is up and running first, will throw error if not available in time
        sparqlGraphManager.waitUntilAvailable(VIRTUOSO_MAX_WAIT_TIME);

        if (storageLocation != null && LOG.isInfoEnabled()) {
            LOG.info(ServerInfoUtils.getDiskUsage(storageLocation));
        }

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

        LOG.info("Processing {} datasets...", datasetsToUpdate.size());
        UpdateReport report = new UpdateReport(serverId, storageLocation);
        for (Dataset ds : datasetsToUpdate) {
            try {
                updateSet(report, ds);
            } catch (UpdaterException | IOException  e) {
                LOG.error("Failed to update dataset {}", ds, e);
                report.addFailed(ds, StringUtils.isEmpty(e.getMessage()) ? ("Exception " + e.getClass().getSimpleName())
                        : e.getMessage());
            }
        }
        report.setEndTime(Instant.now());
        return report;
    }

    private void updateSet(UpdateReport report, Dataset ds) throws UpdaterException, IOException {
        LOG.debug("Processing dataset {}...", ds);
        CommandResult result;
        switch (ds.getState()) {
            case CORRUPT -> {
                LOG.warn("Dataset {} is corrupt and will be removed", ds.getId());
                sparqlGraphManager.removeTmpGraph(ds.getId());
                result = createOrUpdateDataset(ds);
                if (result.isSuccess()) {
                    report.addFixed(ds);
                } else {
                    report.addFailed(ds, result.getErrorMessage());
                }
            }
            case MISSING -> {
                LOG.info("Dataset {} is new and will be downloaded", ds.getId());
                result = createOrUpdateDataset(ds);
                if (result.isSuccess()) {
                    report.addCreated(ds);
                } else {
                    report.addFailed(ds, result.getErrorMessage());
                }
            }
            case OUTDATED -> {
                LOG.info("Dataset {} is outdated and will be downloaded again", ds.getId());
                result = createOrUpdateDataset(ds);
                if (result.isSuccess()) {
                    report.addUpdated(ds);
                } else {
                    report.addFailed(ds, result.getErrorMessage());
                }
            }
            case UP_TO_DATE -> {
                report.addUnchanged(ds);
                LOG.trace("No changes to dataset {} ", ds.getId());
            }
            case TO_REMOVE -> {
                LOG.info("Dataset {} is no longer available and will be removed", ds.getId());
                result = sparqlGraphManager.removeTmpGraph(ds.getId());
                if (!result.isSuccess()) {
                    report.addFailed(ds, result.getErrorMessage());
                } else {
                    result = sparqlGraphManager.removeObsoleteGraph(ds.getId());
                    if (result.isSuccess()) {
                        report.addRemoved(ds);
                    } else {
                        report.addFailed(ds, result.getErrorMessage());
                    }
                }
            }
        }
    }

    private CommandResult createOrUpdateDataset(Dataset ds) throws UpdaterException, IOException {
        Instant startTime = Instant.now();

        String datasetId = ds.getId();
        File outputFolder = sparqlGraphManager.getTtlImportFolder();
        File dsZipFile = new File(outputFolder, ds.getId() + ".zip");
        LOG.trace("Downloading zip file {}...", dsZipFile);
        ftpServer.download(dsZipFile, datasetId);

        File dsTtlFile = prepareVirtuosoImportFiles(datasetId, dsZipFile, ds.getTimestampFtp());
        LOG.trace("Deleting zip file {}...", dsZipFile);
        Files.delete(dsZipFile.toPath());

        CommandResult res = sparqlGraphManager.ingestGraph(datasetId + "_new");
        if (res.isSuccess()) {
            res = sparqlGraphManager.removeObsoleteGraph(datasetId);
            if (res.isSuccess()) {
                res = sparqlGraphManager.renameTmpGraph(datasetId);
            }
        } else {
            LOG.error("Error creating or updating dataset {}: reason: {}", ds, res.getErrorMessage());
        }
        LOG.trace("Deleting ttl.gz file {}", dsTtlFile);
        Files.delete(dsTtlFile.toPath());

        if (LOG.isInfoEnabled()) {
            Instant endTime = Instant.now();
            Duration diff = Duration.between(startTime, endTime);
            LOG.info(String.format("Ingesting dataset %s took: %d:%02d:%02d", datasetId, diff.toHours(),
                    diff.toMinutesPart(), diff.toSecondsPart()));
        }
        return res;
    }

    private File prepareVirtuosoImportFiles(String datasetId, File dsZipFile, Instant datasetTimestampAtFtp) throws IOException {
        File outputFolder = dsZipFile.getParentFile();
        File datasetTtlFile = new File(outputFolder, datasetId + ".ttl.gz");
        if (datasetTtlFile.exists()) {
            LOG.trace("Deleting old ttl.gz file {}...", datasetTtlFile);
            Files.delete(datasetTtlFile.toPath());
        }

        LOG.trace("Generating TTL zip file {}...", datasetTtlFile);
        try (FileOutputStream datasetTtlFileStream = new FileOutputStream(datasetTtlFile);
             GZIPOutputStream gzipDatasetTtlStream = new GZIPOutputStream(datasetTtlFileStream);
             Writer writer = new OutputStreamWriter(gzipDatasetTtlStream, StandardCharsets.UTF_8)) {

            processZipFile(dsZipFile, writer);

            // add the triple with the last modification timestamp from the FTP server
            writer.write("\n\n<http://data.europeana.eu/dataset/");
            writer.write(datasetId);
            writer.write("> <http://purl.org/dc/terms/modified> \"");
            writer.write(datasetTimestampAtFtp.toString());
            writer.write("\"^^<http://www.w3.org/2001/XMLSchema#dateTime> .\n");
        }
        return datasetTtlFile;
    }

    private void processZipFile(File dsZipFile, Writer writer) throws IOException {
        boolean firstRecord = true;
        int nrEntries = 0;
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(dsZipFile))) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                writeLines(writer, firstRecord, zip);
                firstRecord = false;
                zip.closeEntry();
                entry = zip.getNextEntry();
                nrEntries++;
            }
        }
        LOG.trace("Added {} entries to file {}", nrEntries, dsZipFile);
    }

    private void writeLines(Writer writer, boolean firstRecord, ZipInputStream zip) throws IOException {
        String edmRdf = IOUtils.toString(zip, StandardCharsets.UTF_8);
        String[] lines = edmRdf.split("\n");
        for (String line : lines) {
            if (firstRecord || !line.startsWith("@prefix")) {
                writer.write(line);
                writer.write("\n");
            }
        }
    }
}
