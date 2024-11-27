package europeana.sparql.updater;

import europeana.sparql.updater.virtuoso.VirtuosoGraphManagerCl;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.TimeZone;

/**
 * Spring boot application that runs the Updater as a cron job
 */
@SpringBootApplication
@EnableScheduling
public class Scheduler {
	
	private static final Logger LOG = LogManager.getLogger(Scheduler.class);

	private UpdaterSettings settings;
	private static ThreadPoolTaskScheduler scheduler;

	public Scheduler(UpdaterSettings settings) {
		this.settings = settings;
	}

	@PostConstruct
	public void init() {
		if (StringUtils.isEmpty(settings.getUpdaterCronSchedule())) {
			LOG.warn("No cron settings specified for updating SPARQL data! Automatic update is off!");
		}
		else {
			if (scheduler == null) {
				scheduler = new ThreadPoolTaskScheduler();
				scheduler.setPoolSize(1);
				scheduler.initialize();
			}
			TimeZone timezone = TimeZone.getTimeZone("Europe/Amsterdam");
			LOG.info("SPARQL data update schedule is {}, timezone {}", settings.getUpdaterCronSchedule(), timezone.getID());

			scheduler.schedule(new DoUpdate(settings), new CronTrigger(settings.getUpdaterCronSchedule(), timezone));
		}
	}

	private static class DoUpdate implements Runnable {
		private UpdaterSettings settings;

		public DoUpdate(UpdaterSettings settings) {
			this.settings = settings;
		}

		public void run() {
			LOG.info("Starting update...");
			File isqlCommand = new File(settings.getIsqlFile());
			File ttlFolder = new File(settings.getTtlFolder());
			File sqlFolder = new File(settings.getTtlFolder());
			if (!ttlFolder.exists()) {
				ttlFolder.mkdir();
			}
			if (!sqlFolder.exists()) {
				sqlFolder.mkdir();
			}

			EuropeanaDatasetFtpServer ftpServer = EuropeanaDatasetFtpServer.GENERAL_PUBLIC;
			EuropeanaSparqlEndpoint sparqlEndpoint = new EuropeanaSparqlEndpoint(settings.getVirtuosoEndpoint());
			sparqlEndpoint.setDebug(true); // TODO disable for production?
			VirtuosoGraphManagerCl graphManager = new VirtuosoGraphManagerCl(isqlCommand, settings.getVirtuosoPort(),
					settings.getVirtuosoUser(),
					settings.getVirtuosoPassword(),
					ttlFolder,
					sqlFolder);

			// TODO for testing purposes we define only a few sets to update!
			List<String> datasetsToUpdate = List.of("1", "10", "2058621");
			////				"1", "10", "2058621"
			//				"222", "90901", "9200357", "1021", "2058703", "97", "2059511", "394", "169", "842"
			////				, "2063621"	, "2048377", "2021110", "9200517", "91650", "91699", "0943110", "2051909", "995", "09902", "1008", "2048620", "411", "9200509", "577", "1095", "753", "457", "2048401", "2024918", "2032015", "902", "05816", "724", "712", "2021723", "10621", "2063629", "2048375", "2048128", "08534", "232", "925", "9200171", "2022361", "9200503", "101", "9200121", "1051", "9200412", "641", "2021719", "916124", "2021632", "916110", "532", "2023008", "183", "0943103", "1001", "2022718", "2023804", "58", "759", "2021802", "154", "2021648", "2021662", "2026113", "130", "2064201", "50", "2058815", "30", "328", "9200435", "9200573", "1097", "10501", "91698", "308", "2059515", "9200475", "2059513", "0940418", "477", "9200215", "765", "387", "921"

			UpdateReport report = new Updater(sparqlEndpoint, ftpServer, graphManager).runTestUpdate(datasetsToUpdate);

			LOG.info("Finished update.");
			if (settings.getSlackWebhook() == null || settings.getSlackWebhook().isBlank()) {
				LOG.info("No report sent");
			} else {
				LOG.info("Sending report to Slack...");
				publishUpdateReportToSlack(report.printSummary(settings.getVirtuosoEndpoint()), settings.getSlackWebhook());
			}
		}

		/**
		 * Method publishes the report over the configured slack channel.
		 *
		 */
		private void publishUpdateReportToSlack(String message, String slackWebhookApiAutomation) {
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

	/**
	 * Clean up when the application is shutting down
	 */
	@PreDestroy
	public void shutdown() {
		if (scheduler != null) {
			LOG.info("Shutting down update scheduler...");
			scheduler.shutdown();
		}
	}

	public static void main(String[] args) {
		SpringApplication.run(Scheduler.class, args);
	}

}
