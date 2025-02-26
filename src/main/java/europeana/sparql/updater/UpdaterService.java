package europeana.sparql.updater;

import europeana.sparql.updater.Dataset.State;
import europeana.sparql.updater.exception.UpdaterException;
import europeana.sparql.updater.exception.VirtuosoCmdLineException;
import europeana.sparql.updater.util.ServerInfoUtils;
import europeana.sparql.updater.virtuoso.CommandResult;
import europeana.sparql.updater.virtuoso.EuropeanaSparqlClient;
import europeana.sparql.updater.virtuoso.VirtuosoGraphManagerCl;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    Integer updateMaxWaitForVirtuoso;
    Integer maxChunkSize;

    /**
     * Initialize a new updater service
     * @param serverId the id of the server on which the update is done
     * @param ftpServer the Europeana FTP server that hosts the dataset sources
     * @param sparql a Europeana sparql client for doing sparql queries
     * @param virtuosoGraphManangerCl the command-line utility for interacting with Virtuoso (isql)
     * @param storageLocation optional, any file or directory located on the drive on which to report disk usage
     * @param updateMaxWaitForVirtuoso maximum time in seconds how long the update should wait for Virtuoso to be ready (can be null)
     * @param maxChunkSize, maximum number of items to process in 1 go (if set to 0 then there's no limit)
     */
    public UpdaterService(String serverId, EuropeanaDatasetFtpServer ftpServer, EuropeanaSparqlClient sparql,
                          VirtuosoGraphManagerCl virtuosoGraphManangerCl, File storageLocation, Integer updateMaxWaitForVirtuoso, Integer maxChunkSize) {
        this.serverId = serverId;
        this.ftpServer = ftpServer;
        this.sparql = sparql;
        this.sparqlGraphManager = virtuosoGraphManangerCl;
        this.storageLocation = storageLocation;
        this.updateMaxWaitForVirtuoso = updateMaxWaitForVirtuoso;
        this.maxChunkSize = maxChunkSize;
    }

    /**
     * Start an update
     * @param datasets only update the provided list of datasets, if null all datasets are updated
     * @return an UpdateReport
     * @throws VirtuosoCmdLineException when Virtuoso is not available
     */
    public UpdateReport runUpdate(List<Dataset> datasets) throws VirtuosoCmdLineException {
        // Check if Virtuoso is up and running first, this will throw an error if not available in time
        if (updateMaxWaitForVirtuoso != null) {
            sparqlGraphManager.waitUntilAvailable(updateMaxWaitForVirtuoso);
        }
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

        int nrDataSetsToUpdate = 0;
        // Iterate over all datasets found on the FTP server
        for (Dataset ds : datasetsInFtp) {
            if (ds.updateState(datasetsInSparql.get(ds))) {
                nrDataSetsToUpdate++;
            }
            datasetsInSparql.remove(ds);
        }

        List<Dataset> dataSetsAll = new ArrayList<>(datasetsInFtp);
        if (!datasetsInSparql.isEmpty()) {
            // Found datasets in SPARQL that are not on the FTP server
            dataSetsAll = new ArrayList<>(datasetsInFtp);
            for (Dataset ds : datasetsInSparql.keySet()) {
                ds.setState(State.TO_REMOVE);
                nrDataSetsToUpdate++;
                dataSetsAll.add(ds);
            }
        }

        LOG.info("Found {} data sets, {} need action...", dataSetsAll.size(), nrDataSetsToUpdate);
        UpdateReport report = new UpdateReport(serverId, storageLocation, nrDataSetsToUpdate);
        for (Dataset ds : dataSetsAll) {
            try {
                updateSet(report, ds);
            } catch (UpdaterException | IOException  e) {
                LOG.error("Failed to update data set {}", ds, e);
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

        LOG.info("Download complete, generating files...");
        File dsTtlFile = new File(outputFolder, datasetId + ".ttl.gz");
        CommandResult res = null;
        try (ImportFileCreator ttlCreator = new ImportFileCreator(datasetId, dsZipFile, dsTtlFile, ds.getTimestampFtp(),
                maxChunkSize)) {
            while (ttlCreator.hasNextTtlFile() && (res == null || res.isSuccess())) {
                ttlCreator.createNextTtlFile();
                res = sparqlGraphManager.ingestGraph(datasetId + "_new");
            }
        }

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
        LOG.trace("Deleting zip file {}...", dsZipFile);
        Files.delete(dsZipFile.toPath());

        if (LOG.isInfoEnabled()) {
            Instant endTime = Instant.now();
            Duration diff = Duration.between(startTime, endTime);
            LOG.info(String.format("Ingesting dataset %s took: %d:%02d:%02d", datasetId, diff.toHours(),
                    diff.toMinutesPart(), diff.toSecondsPart()));
        }
        return res;
    }

}
