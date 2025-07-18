package europeana.sparql.updater;

import europeana.sparql.updater.exception.UpdaterException;
import europeana.sparql.updater.util.ServerInfoUtils;
import europeana.sparql.updater.virtuoso.EuropeanaSparqlClient;
import europeana.sparql.updater.virtuoso.VirtuosoGraphManagerCl;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.TimeZone;

/**
 * Spring boot application that runs the Updater as a cron job
 */

@EnableScheduling
@Service
public class UpdateScheduler {

    private static final Logger LOG = LogManager.getLogger(UpdateScheduler.class);

    private final UpdaterSettings settings;
    private ThreadPoolTaskScheduler taskScheduler;


    /**
     * Initialize a new Update scheduler
     * @param settings configuration
     */
    public UpdateScheduler(UpdaterSettings settings) {
        this.settings = settings;
    }

    @PostConstruct
    private void init() {
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

        private static boolean updateInProgress = false; // to prevent an update running multiple time simultaneously
        private final UpdaterSettings settings;

        public DoUpdate(UpdaterSettings settings) {
            this.settings = settings;
        }

        public void run()  {
            LOG.info("Starting update...");
            synchronized (this) {
                if (updateInProgress) {
                    LOG.error("There's already an update in progress! Aborting...");
                    return;
                }
                updateInProgress = true;
            }

            File isqlCommand = new File(settings.getVirtuosoIsql());
            File ttlFolder = new File(settings.getTtlFolder());
            File sqlFolder = new File(settings.getSqlFolder());
            if (!ttlFolder.exists()) {
                if (!ttlFolder.mkdir()) {
                    throw new RuntimeException("Unable to create folder " + ttlFolder.getAbsolutePath());
                }
            }
            if (!sqlFolder.exists()) {
                if (!sqlFolder.mkdir()) {
                    throw new RuntimeException("Unable to create folder " + sqlFolder.getAbsolutePath());
                }
            }

            VirtuosoGraphManagerCl graphManager = new VirtuosoGraphManagerCl(isqlCommand, settings.getVirtuosoPort(),
                    settings.getVirtuosoUser(),
                    settings.getVirtuosoPassword(),
                    ttlFolder,
                    sqlFolder);
            EuropeanaDatasetFtpServer ftpServer = new EuropeanaDatasetFtpServer(settings.getFtpHostName(), settings.getFtpPort(),
                    settings.getFtpPath(), settings.getFtpUsername(), settings.getFtpPassword(), settings.getFtpChecksum());
            EuropeanaSparqlClient sparqlEndpoint = new EuropeanaSparqlClient(settings.getVirtuosoEndpoint());
            Integer maxWaitForVirtuoso = settings.getUpdateMaxWaitForVirtuoso();

            String nodeId = ServerInfoUtils.getServerId();
            UpdateReport report;
            try {
                report = new UpdaterService(nodeId, ftpServer, sparqlEndpoint, graphManager, ttlFolder,
                        maxWaitForVirtuoso, settings.getMaxRecordsPerImport()).runUpdate(settings.getDatasetsList());
            } catch (UpdaterException ue) {
                LOG.error("Error running the update", ue);
                report = new UpdateReport(nodeId, ue);
            }

            LOG.info("Finished update.");
            updateInProgress = false;
            if (LOG.isInfoEnabled()) {
                LOG.info(report.printSummary());
            }
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

}
