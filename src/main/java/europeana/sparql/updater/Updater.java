package europeana.sparql.updater;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import europeana.sparql.updater.Dataset.State;
import europeana.sparql.updater.virtuoso.CommandResult;
import europeana.sparql.updater.virtuoso.VirtuosoGraphManagerCl;

/**
 * The top class that executes the SPARQL endpoint update process
 *
 */
public class Updater {

	private static final Logger LOG = LogManager.getLogger(Updater.class);

	EuropeanaSparqlEndpoint sparql;
	EuropeanaDatasetFtpServer ftpServer;
	VirtuosoGraphManagerCl sparqlGraphManager;

	public Updater(EuropeanaSparqlEndpoint sparql, EuropeanaDatasetFtpServer ftpServer,
			VirtuosoGraphManagerCl sparqlGraphManager) {
		super();
		this.sparql = sparql;
		this.ftpServer = ftpServer;
		this.sparqlGraphManager = sparqlGraphManager;
	}

	public UpdateReport runTestUpdate(List<String> datasetIds) {
		Set<Dataset> datasets = new HashSet<Dataset>(datasetIds.size());
		for (String dsId : datasetIds) {
			datasets.add(new Dataset(dsId));
		}
		return runUpdate(datasets);
	}

	public UpdateReport runUpdate() {
		return runUpdate(null);
	}

	private UpdateReport runUpdate(Set<Dataset> forDatasets) {
		List<Dataset> datasetsInFtp = ftpServer.listDatasets();
		List<Dataset> datasetsAll = null;

		Map<Dataset, Dataset> datasetsInSparql = sparql.listDatasets();

		// this is used while under developement
		if (forDatasets != null && !forDatasets.isEmpty()) {
			datasetsInFtp.removeIf(e -> (!forDatasets.contains(e)));
			datasetsInSparql.entrySet().removeIf(e -> (!forDatasets.contains(e.getKey())));
		}

		for (Dataset ds : datasetsInFtp) {
			ds.checkState(datasetsInSparql.get(ds));
			datasetsInSparql.remove(ds);
		}

		if (!datasetsInSparql.isEmpty()) {
			datasetsAll = new ArrayList<Dataset>(datasetsInFtp);
			for (Dataset ds : datasetsInSparql.keySet()) {
				ds.setState(State.TO_REMOVE);
				datasetsAll.add(ds);
			}
		} else
			datasetsAll = datasetsInFtp;

		UpdateReport report = new UpdateReport();
		report.setStartTime(Instant.now());
		for (Dataset ds : datasetsAll) {
			try {
				CommandResult result = null;
				switch (ds.getState()) {
				case CORRUPT:
					sparqlGraphManager.removeTmpGraph(ds.getId());
					result = createOrUpdateDataset(ds);
					if (result.isSuccess())
						report.wasFixed(ds);
					else
						report.failed(ds, result.getErrorMessage());
					break;
				case MISSING:
					result = createOrUpdateDataset(ds);
					if (result.isSuccess())
						report.wasCreated(ds);
					else
						report.failed(ds, result.getErrorMessage());
					break;
				case OUTDATED:
					result = createOrUpdateDataset(ds);
					if (result.isSuccess())
						report.wasUpdated(ds);
					else
						report.failed(ds, result.getErrorMessage());
					break;
				case UP_TO_DATE:
					report.wasUnchanged(ds);
					break;
				case TO_REMOVE:
					result = sparqlGraphManager.removeTmpGraph(ds.getId());
					if (!result.isSuccess())
						report.failed(ds, result.getErrorMessage());
					else {
						result = sparqlGraphManager.removeGraph(ds.getId());
						if (result.isSuccess())
							report.wasRemoved(ds);
						else
							report.failed(ds, result.getErrorMessage());
					}
					break;
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

	private CommandResult createOrUpdateDataset(Dataset ds) throws IOException {
		String datasetId = ds.getId();
		File outputFolder = sparqlGraphManager.getTtlImportFolder();
		File dsZipFile = new File(outputFolder, ds.getId() + ".zip");
		ftpServer.download(dsZipFile, datasetId);

		prepareVirtuosoImportFiles(datasetId, dsZipFile, ds.getTimestampFtp());
		dsZipFile.delete();

		Instant startTime = Instant.now();
		CommandResult res = sparqlGraphManager.ingestGraph(datasetId + "_new");
		if (res.isSuccess()) {
			res = sparqlGraphManager.removeGraph(datasetId);
			if (res.isSuccess()) {
				res = sparqlGraphManager.renameTmpGraph(datasetId);
				Instant endTime = Instant.now();
				Duration diff = Duration.between(startTime, endTime);
				LOG.info(String.format("Dataset \"%s\" ingest duration: %d:%02d:%02d", datasetId, diff.toHours(),
						diff.toMinutesPart(), diff.toSecondsPart()));
			}
		}
		return res;
	}

	private void prepareVirtuosoImportFiles(String datasetId, File dsZipFile, Instant datasetTimestampAtFtp)
			throws IOException {
		File outputFolder = dsZipFile.getParentFile();
//		FileUtils.write(new File(outputFolder, datasetId+".ttl.graph"), "http://data.europeana.eu/dataset/"+datasetId, StandardCharsets.UTF_8);	
		File datasetTtlFile = new File(outputFolder, datasetId + ".ttl.gz");
		if (datasetTtlFile.exists())
			datasetTtlFile.delete();

		FileOutputStream datasetTtlFileStream = new FileOutputStream(datasetTtlFile);
		GZIPOutputStream gzipDatasetTtlStream = new GZIPOutputStream(datasetTtlFileStream);
		Writer writer = new OutputStreamWriter(gzipDatasetTtlStream, StandardCharsets.UTF_8);

		boolean firstRecord = true;
		int recIndex = 0;
		final ZipInputStream zip = new ZipInputStream(new FileInputStream(dsZipFile));
		ZipEntry entry = zip.getNextEntry();
		while (entry != null) {
			String edmRdf = "";
			try {
				edmRdf = IOUtils.toString(zip, StandardCharsets.UTF_8);
				String[] lines = edmRdf.split("\n");
				for (String line : lines) {
					if (firstRecord || !line.startsWith("@prefix")) {
						writer.write(line);
						writer.write("\n");
					}
				}
				firstRecord = false;
			} catch (Throwable e) {
				throw e;
			}
			zip.closeEntry();
			entry = zip.getNextEntry();
			recIndex++;
		}
		zip.close();

		// add the triple with the last modification timestamp from the FTP server
		writer.write("\n\n<http://http://data.europeana.eu/dataset/");
		writer.write(datasetId);
		writer.write("> <http://purl.org/dc/terms/modified> \"");
		writer.write(datasetTimestampAtFtp.toString());
		writer.write("\"^^<http://www.w3.org/2001/XMLSchema#dateTime> .\n");

		writer.close();
		gzipDatasetTtlStream.close();
		datasetTtlFileStream.close();
	}
}
