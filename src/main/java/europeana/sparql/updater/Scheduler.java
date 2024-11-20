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
		EuropeanaSparqlEndpoint sparqlEndpoint=EuropeanaSparqlEndpoint.TEST_LOCAL;
		sparqlEndpoint.setDebug(true);
		VirtuosoGraphManagerCl graphManager=new VirtuosoGraphManagerCl(isqlCommand, 1111, "dba", "pa", ttlFolder, sqlFolder);
		
		Updater updater=new Updater(sparqlEndpoint, ftpServer, graphManager);
		UpdateReport report = updater.runTestUpdate(
//				"1", "10", "2058621"
				"222", "90901", "9200357", "1021", "2058703", "97", "2059511", "394", "169", "842", "2063621", "2048377", "2021110", "9200517", "91650", "91699", "0943110", "2051909", "995", "09902", "1008", "2048620", "411", "9200509", "577", "1095", "753", "457", "2048401", "2024918", "2032015", "902", "05816", "724", "712", "2021723", "10621", "2063629", "2048375", "2048128", "08534", "232", "925", "9200171", "2022361", "9200503", "101", "9200121", "1051", "9200412", "641", "2021719", "916124", "2021632", "916110", "532", "2023008", "183", "0943103", "1001", "2022718", "2023804", "58", "759", "2021802", "154", "2021648", "2021662", "2026113", "130", "2064201", "50", "2058815", "30", "328", "9200435", "9200573", "1097", "10501", "91698", "308", "2059515", "9200475", "2059513", "0940418", "477", "9200215", "765", "387", "921"
				);
		
		System.out.println(report.print());
	}
	

	public static void main(String[] args) {
		SpringApplication.run(Scheduler.class, args);
	}
}
