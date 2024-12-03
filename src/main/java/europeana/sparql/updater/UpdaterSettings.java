package europeana.sparql.updater;

import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.ArrayList;
import java.util.List;

/**
 * Container class for all settings that we load from properties file and optionally override from a user.properties file
 */
@Configuration
@PropertySource("classpath:sparql-updater.properties")
@PropertySource(value = "classpath:sparql-updater.user.properties", ignoreResourceNotFound = true)
public class UpdaterSettings {

    private static final Logger LOG = LogManager.getLogger(UpdaterSettings.class);

    @Value("${update.onstartup}")
    private Boolean doUpdateOnStartup;
    @Value("${update.datasets}")
    private String updateDatasets;
    private List<Dataset> datasetsList;
    @Value("${update.cron}")
    private String updateCronSchedule;

    @Value("${isql.file}")
    private String isqlFile;
    @Value("${ttl.folder}")
    private String ttlFolder;
    @Value("${sql.folder}")
    private String sqlFolder;

    @Value("${virtuoso.endpoint}")
    private String virtuosoEndpoint;
    @Value("${virtuoso.port}")
    private Integer virtuosoPort;
    @Value("${virtuoso.user}")
    private String virtuosoUser;
    @Value("${virtuoso.password}")
    private String virtuosoPassword;
    @Value("${slack.webhook}")
    private String slackWebhook;

    @PostConstruct
    private void logImportantSettings() {
        LOG.error("TEST2");
        LOG.info("Configuration:");
        if (updateDatasets == null || updateDatasets.isBlank()) {
            LOG.info("Data sets: ALL");
        } else {
            String[] datasetIds = updateDatasets.split(",");
            this.datasetsList = new ArrayList<>(datasetIds.length);
            for (String dsId : datasetIds) {
                datasetsList.add(new Dataset(dsId));
            }
            LOG.info("Data sets: {}", datasetsList);
        }
        LOG.info("Update on startup = {}", doUpdateOnStartup);
        if (slackWebhook == null || slackWebhook.isBlank()) {
            LOG.info("No reporting to Slack configured");
        } else {
            LOG.info("Reporting to Slack enabled");
        }

        LOG.info("  Virtuoso endpoint = {}", virtuosoEndpoint);
        LOG.info("  Virtuoso port = {}", virtuosoPort);
    }

    /**
     *
     * @return true if the updater should run an update directly after startup, otherwise false
     */
    public Boolean doUpdateOnStartup() {
        return doUpdateOnStartup;
    }

    public List<Dataset> getDatasetsList() {
        return datasetsList.stream().toList();
    }

    public String getUpdateCronSchedule() {
        return updateCronSchedule;
    }

    public String getIsqlFile() {
        return isqlFile;
    }

    public String getTtlFolder() {
        return ttlFolder;
    }

    public String getSqlFolder() {
        return sqlFolder;
    }

    public String getVirtuosoEndpoint() {
        return virtuosoEndpoint;
    }

    public int getVirtuosoPort() {
        return virtuosoPort;
    }

    public String getVirtuosoUser() {
        return virtuosoUser;
    }

    public String getVirtuosoPassword() {
        return virtuosoPassword;
    }

    public String getSlackWebhook() {
        return slackWebhook;
    }
}
