package europeana.sparql.updater;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import europeana.sparql.updater.virtuoso.VirtuosoGraphManagerCl;

@SpringBootApplication
@EnableScheduling
public class Scheduler {

//	@Scheduled(cron = "0 0 0 * * 0")
	@Scheduled(fixedDelay = 10080, initialDelay = 1, timeUnit = TimeUnit.MINUTES)//every 7 days
	public void runUpdate() {
		File isqlCommand=new File("/opt/virtuoso-opensource/bin/isql");
		File ttlFolder=new File("/ingest/ttl-import");
		File sqlFolder=new File("/ingest/sql-scripts");
	//	File ttlFolder=new File("C:\\Users\\nfrei\\Desktop\\testSPARQL\\ttl-import");
	//	File sqlFolder=new File("C:\\Users\\nfrei\\Desktop\\testSPARQL\\sql-scripts");
	
		if(!ttlFolder.exists())
			ttlFolder.mkdir();
		if(!sqlFolder.exists())
			sqlFolder.mkdir();
		
		EuropeanaDatasetFtpServer ftpServer=EuropeanaDatasetFtpServer.GENERAL_PUBLIC;
		EuropeanaSparqlEndpoint sparqlEndpoint=EuropeanaSparqlEndpoint.TEST_RND2;
		sparqlEndpoint.setDebug(true);
		VirtuosoGraphManagerCl graphManager=new VirtuosoGraphManagerCl(isqlCommand, 1111, "dba", "pa", ttlFolder, sqlFolder);
		
		Updater updater=new Updater(sparqlEndpoint, ftpServer, graphManager);
		UpdateReport report = updater.runTestUpdate(
				"1", "10"
				, "2058621"
				);
		
		System.out.println(report.print());
	}
	

	public static void main(String[] args) {
		SpringApplication.run(Scheduler.class, args);
	}
}
