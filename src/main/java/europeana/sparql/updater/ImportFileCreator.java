/**
 * 
 */
package europeana.sparql.updater;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Converts the ZIP files containing one TTL file per record into one single TTL file containing all records.
 * May be configured to split the contents of the ZIP files into files with a maximum number of records.
 * 
 * @author Nuno Freire
 * @since 19/02/2025
 */
public class ImportFileCreator implements Closeable {

	private static final Logger LOG = LogManager.getLogger(ImportFileCreator.class);

	int maxRecordsPerImport;
	String datasetId;
	File dsZipFile;
	Instant timestampFtp;

	ZipInputStream zip;
	ZipEntry entry;
	File datasetTtlFile;
	int nrEntriesInTotal = 0;

	/**
	 * @param datasetId 
	 * @param dsZipFile the input ZIP file
	 * @param datasetTtlFile the output TTL file
	 * @param timestampFtp the last modified date of the Zip file on the FTP server
	 * @param maxRecordsPerImport maximum records per TTL file
	 * @throws IOException
	 */
	public ImportFileCreator(String datasetId, File dsZipFile, File datasetTtlFile, Instant timestampFtp,
			int maxRecordsPerImport) throws IOException {
		super();
		this.datasetId = datasetId;
		this.dsZipFile = dsZipFile;
		this.timestampFtp = timestampFtp;
		this.maxRecordsPerImport = maxRecordsPerImport;
		this.datasetTtlFile = datasetTtlFile;
		zip = new ZipInputStream(new FileInputStream(dsZipFile));
		entry = zip.getNextEntry();
	}

	/**
	 * Checks if there are records to create another file. Should be invoked before invoking createNextTtlFile()
	 * 
	 * @return true if there will be another file, false otherwise
	 * @throws IOException
	 */
	public boolean hasNextTtlFile() throws IOException {
		return entry != null;
	}
	
	/**
	 * Creates the next TTL file
	 * 
	 * @throws IOException
	 */
	public void createNextTtlFile() throws IOException {
		LOG.trace("Generating TTL zip file {}...", datasetTtlFile);
		try (FileOutputStream datasetTtlFileStream = new FileOutputStream(datasetTtlFile);
				GZIPOutputStream gzipDatasetTtlStream = new GZIPOutputStream(datasetTtlFileStream);
				Writer writer = new OutputStreamWriter(gzipDatasetTtlStream, StandardCharsets.UTF_8)) {
			int nrEntries = 1;
			while (entry != null && (maxRecordsPerImport <= 0 || maxRecordsPerImport >= nrEntries)) {
				writeLines(writer, (nrEntries == 1), zip);
				zip.closeEntry();
				entry = zip.getNextEntry();
				nrEntries++;
				nrEntriesInTotal++;
			}
			LOG.trace("Added {} entries to file {}", nrEntries-1, dsZipFile);

			if (entry == null) {
				// add the triple with the last modification timestamp from the FTP server
				LOG.trace("Adding the triple with the last modification timestamp from the FTP server...");
				writer.write("\n\n<http://data.europeana.eu/dataset/");
				writer.write(datasetId);
				writer.write("> <http://purl.org/dc/terms/modified> \"");
				writer.write(timestampFtp.toString());
				writer.write("\"^^<http://www.w3.org/2001/XMLSchema#dateTime> .\n");
			}
		}
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

	@Override
	public void close() throws IOException {
		zip.close();
	}

}
