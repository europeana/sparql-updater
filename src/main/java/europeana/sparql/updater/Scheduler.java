package europeana.sparql.updater;

import europeana.sparql.updater.virtuoso.VirtuosoGraphManagerCl;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.io.File;
import java.util.TimeZone;

/**
 * Spring boot application that runs the Updater as a cron job
 */
@SpringBootApplication
@EnableScheduling
public class Scheduler {

    private static final Logger LOG = LogManager.getLogger(Scheduler.class);

    private UpdaterSettings settings;
    private ThreadPoolTaskScheduler taskScheduler;

    public Scheduler(UpdaterSettings settings) {
        this.settings = settings;
    }

    @PostConstruct
    public void init() {
        if (StringUtils.isEmpty(settings.getUpdateCronSchedule())) {
            LOG.warn("No cron settings specified for updating SPARQL data! Automatic update is off!");
        } else {
            if (taskScheduler == null) {
                taskScheduler = new ThreadPoolTaskScheduler();
                taskScheduler.setPoolSize(1);
                taskScheduler.initialize();
            }
            TimeZone timezone = TimeZone.getTimeZone("Europe/Amsterdam");
            LOG.info("SPARQL data update schedule is {}, timezone {}", settings.getUpdateCronSchedule(), timezone.getID());

            taskScheduler.schedule(new DoUpdate(settings), new CronTrigger(settings.getUpdateCronSchedule(), timezone));
        }

        if (settings.doUpdateOnStartup()) {
            new DoUpdate(settings).run();
        }
    }

    private static class DoUpdate implements Runnable{
        private UpdaterSettings settings;

        public DoUpdate(UpdaterSettings settings) {
            this.settings = settings;
        }

        public void run()  {
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

            // TODO in k8s get nodeId
            String nodeId = settings.getVirtuosoEndpoint();
            EuropeanaDatasetFtpServer ftpServer = EuropeanaDatasetFtpServer.GENERAL_PUBLIC;
            EuropeanaSparqlEndpoint sparqlEndpoint = new EuropeanaSparqlEndpoint(settings.getVirtuosoEndpoint());
            sparqlEndpoint.setDebug(true); // TODO disable for production?
            VirtuosoGraphManagerCl graphManager = new VirtuosoGraphManagerCl(isqlCommand, settings.getVirtuosoPort(),
                    settings.getVirtuosoUser(),
                    settings.getVirtuosoPassword(),
                    ttlFolder,
                    sqlFolder);

            UpdateReport report;
            try {
                report = new Updater(nodeId, sparqlEndpoint, ftpServer, graphManager).runUpdate(settings.getDatasetsList());
            } catch (RuntimeException rte) {
                LOG.error("Error running the update", rte);
                report = new UpdateReport(nodeId, rte);
            }

            LOG.info("Finished update.");
            if (settings.getSlackWebhook() == null || settings.getSlackWebhook().isBlank()) {
                LOG.info("No report sent");
            } else {
                LOG.info("Sending report to Slack...");
                Slack.publishUpdateReport(report, settings.getSlackWebhook());
            }
        }
    }

    /**
     * Clean up when the application is shutting down
     */
    @PreDestroy
    public void shutdown() {
        if (taskScheduler != null) {
            LOG.info("Shutting down update scheduler...");
            taskScheduler.shutdown();
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(Scheduler.class, args);
    }

}
