package europeana.sparql.updater;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import europeana.sparql.updater.virtuoso.VirtuosoGraphManagerCl;

/**
 * Spring boot application that runs the Updater as a cron job
 */
@SpringBootApplication
@EnableScheduling
public class Scheduler {
	
	private static final Logger LOG = LogManager.getLogger(Scheduler.class);

	// TODO: decide on the schedule
//	@Scheduled(cron = "0 0 0 * * 0")
	@Scheduled(fixedDelay = 608000, initialDelay = 10, timeUnit = TimeUnit.SECONDS) // every 7 days
	public void runUpdate() {
		// TODO: setup the configuration of the following parameters for the application
		File isqlCommand = new File("/opt/virtuoso-opensource/bin/isql");
		File ttlFolder = new File("/usr/share/proj");
		File sqlFolder = new File("/ingest/sql-scripts");
		String sparqlEndpointUrl = "http://localhost:8890/sparql";
		String slackWebhookApiAutomation = "https://hooks.slack.com/services/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";
//		String sparqlEndpointUrl="http://sparql.europeana.eu/";
//		String sparqlEndpointUrl="http://rnd-2.eanadev.org:8890/sparql";
		int virtuosoPort = 1111;
		// TODO: Set a password. The dba password is set in the docker-compose file
		String virtuosoDbaPassword = "pa";
		// TODO: end of configuration properties

		if (!ttlFolder.exists())
			ttlFolder.mkdir();
		if (!sqlFolder.exists())
			sqlFolder.mkdir();

		EuropeanaDatasetFtpServer ftpServer = EuropeanaDatasetFtpServer.GENERAL_PUBLIC;
		EuropeanaSparqlEndpoint sparqlEndpoint = new EuropeanaSparqlEndpoint(sparqlEndpointUrl);
		sparqlEndpoint.setDebug(true);
		VirtuosoGraphManagerCl graphManager = new VirtuosoGraphManagerCl(isqlCommand, virtuosoPort, "dba",
				virtuosoDbaPassword, ttlFolder, sqlFolder);

		Updater updater = new Updater(sparqlEndpoint, ftpServer, graphManager);
		// TODO: for the real deployment, remove the list os datasets in the invokation
		// of runTestUpdate().
		UpdateReport report = updater.runTestUpdate(
//				"1", "10", "2058621"
				"222", "90901", "9200357", "1021", "2058703", "97", "2059511", "394", "169", "842"
//				, "2063621"	, "2048377", "2021110", "9200517", "91650", "91699", "0943110", "2051909", "995", "09902", "1008", "2048620", "411", "9200509", "577", "1095", "753", "457", "2048401", "2024918", "2032015", "902", "05816", "724", "712", "2021723", "10621", "2063629", "2048375", "2048128", "08534", "232", "925", "9200171", "2022361", "9200503", "101", "9200121", "1051", "9200412", "641", "2021719", "916124", "2021632", "916110", "532", "2023008", "183", "0943103", "1001", "2022718", "2023804", "58", "759", "2021802", "154", "2021648", "2021662", "2026113", "130", "2064201", "50", "2058815", "30", "328", "9200435", "9200573", "1097", "10501", "91698", "308", "2059515", "9200475", "2059513", "0940418", "477", "9200215", "765", "387", "921"
		);

		publishUpdateReportToSlack(report.print(), slackWebhookApiAutomation);
	}

//	public static void main(String[] args) {
//		publishUpdateReportToSlack("testing sparql updater - please ignore", "https://hooks.slack.com/services/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");
//	}
	
	public static void main(String[] args) {
		SpringApplication.run(Scheduler.class, args);
	}

	/**
	 * Method publishes the report over the configured slack channel.
	 * 
	 */
	private static void publishUpdateReportToSlack(String message, String slackWebhookApiAutomation) {
		message=String.format("{\"text\": \"%s\"}", message.replaceAll("\n", "\\n"));
		LOG.info("Sending Slack Message : " + message);
		System.out.println("Sending Slack Message : " + message);
		try {
			HttpPost httpPost = new HttpPost(slackWebhookApiAutomation);
			StringEntity entity = new StringEntity(message);
			httpPost.setEntity(entity);
			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Content-type", "application/json");
			try (CloseableHttpClient httpClient = HttpClients.createDefault();
					CloseableHttpResponse response = httpClient.execute(httpPost)) {
				LOG.info("Received status " + response.getStatusLine().getStatusCode() + " while calling slack!");
				if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
					LOG.info(" Successfully sent slack message !");
				}
			}
		} catch (IOException e) {
			LOG.error("Exception occurred while sending slack message !! " + e.getMessage());
		}
	}

	
}
