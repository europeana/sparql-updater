package europeana.sparql.updater;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTPFile;

import europeana.sparql.updater.virtuoso.VirtuosoGraphManagerCl;

public class ScriptTest  {

	public static void main(String[] args) throws Exception {
		File isqlCommand=new File("/data/virtuoso/bin/isql");
		File ttlFolder=new File("/data/virtuoso/import-europeana/ttl-import");
		File sqlFolder=new File("/data/virtuoso/import-europeana/sql-scripts");
//		File ttlFolder=new File("C:\\Users\\nfrei\\Desktop\\testSPARQL\\ttl-import");
//		File sqlFolder=new File("C:\\Users\\nfrei\\Desktop\\testSPARQL\\sql-scripts");

		if(!ttlFolder.exists())
			ttlFolder.mkdir();
		if(!sqlFolder.exists())
			sqlFolder.mkdir();
		
		EuropeanaDatasetFtpServer ftpServer=EuropeanaDatasetFtpServer.GENERAL_PUBLIC;
		EuropeanaSparqlEndpoint sparqlEndpoint=EuropeanaSparqlEndpoint.TEST_RND2;
		sparqlEndpoint.setDebug(true);
		VirtuosoGraphManagerCl graphManager=new VirtuosoGraphManagerCl(isqlCommand, 1111, "dba", "dba", ttlFolder, sqlFolder);
		
		Updater updater=new Updater(sparqlEndpoint, ftpServer, graphManager);
		UpdateReport report = updater.runTestUpdate(
				"1", "10"
				, "2058621"
				);
		
		System.out.println(report.print());
	}
	
		
}
	