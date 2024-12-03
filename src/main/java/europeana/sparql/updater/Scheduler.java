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
	private ThreadPoolTaskScheduler scheduler;

	public Scheduler(UpdaterSettings settings) {
		this.settings = settings;
	}

	@PostConstruct
	public void init() {
		if (StringUtils.isEmpty(settings.getUpdateCronSchedule())) {
			LOG.warn("No cron settings specified for updating SPARQL data! Automatic update is off!");
		}
		else {
			if (scheduler == null) {
				scheduler = new ThreadPoolTaskScheduler();
				scheduler.setPoolSize(1);
				scheduler.initialize();
			}
			TimeZone timezone = TimeZone.getTimeZone("Europe/Amsterdam");
			LOG.info("SPARQL data update schedule is {}, timezone {}", settings.getUpdateCronSchedule(), timezone.getID());

			scheduler.schedule(new DoUpdate(settings), new CronTrigger(settings.getUpdateCronSchedule(), timezone));
		}

		if (settings.doUpdateOnStartup()) {
			new DoUpdate(settings).run();
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
			File sqlFolder = new File(settings.getSqlFolder());
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

			UpdateReport report = new Updater(sparqlEndpoint, ftpServer, graphManager).runUpdate(settings.getDatasetsList());

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
