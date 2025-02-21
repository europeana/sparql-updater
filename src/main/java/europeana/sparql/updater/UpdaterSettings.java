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

    @Value("${ftp.hostname}")
    private String ftpHostName;
    @Value("${ftp.port}")
    private Integer ftpPort;
    @Value("${ftp.path}")
    private String ftpPath;
    @Value("${ftp.username}")
    private String ftpUsername;
    @Value("${ftp.password}")
    private String ftpPassword;
    @Value("${ftp.checksum:false}")
    private Boolean ftpChecksum;

    @Value("${update.onstartup:false}")
    private Boolean doUpdateOnStartup;
    @Value("${update.datasets}")
    private String updateDatasets;
    private List<Dataset> datasetsList;
    @Value("${update.cron}")
    private String updateCronSchedule;

    @Value("${ttl.folder}")
    private String ttlFolder;
    @Value("${ttl.maxRecordsPerImport}")
    private Integer maxRecordsPerImport;
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
    @Value("${virtuoso.isql.file}")
    private String virtuosoIsql;

    @Value("${slack.webhook}")
    private String slackWebhook;

    @PostConstruct
    private void logImportantSettings() {
        LOG.info("Configuration:");
        LOG.info("  FTP server = {}:{}{}", ftpHostName, ftpPort, ftpPath);
        LOG.info("  Virtuoso endpoint = {}:{}", virtuosoEndpoint, virtuosoPort);
        if (updateDatasets == null || updateDatasets.isBlank()) {
            LOG.info("  Data sets: ALL");
        } else {
            String[] datasetIds = updateDatasets.split(",");
            this.datasetsList = new ArrayList<>(datasetIds.length);
            for (String dsId : datasetIds) {
                datasetsList.add(new Dataset(dsId));
            }
            LOG.info("  Data sets: {}", datasetsList);
        }
        LOG.info("  Update on startup = {}", doUpdateOnStartup);
        if (slackWebhook == null || slackWebhook.isBlank()) {
            LOG.info("  No reporting to Slack configured");
        } else {
            LOG.info("  Reporting to Slack enabled");
        }

        if (virtuosoPassword.equals(System.getenv("DBA_PASSWORD"))) {
            LOG.info("  Environment variable ok");
       } else {
            LOG.warn("Configured password does not match value of DBA_PASSWORD environment variable!");
        }
    }

    public String getFtpHostName() {
        return ftpHostName;
    }

    public Integer getFtpPort() {
        return ftpPort;
    }

    public String getFtpPath() {
        return ftpPath;
    }

    public String getFtpUsername() {
        return ftpUsername;
    }

    public String getFtpPassword() {
        return ftpPassword;
    }

    public Boolean getFtpChecksum() {
        return ftpChecksum;
    }

    /**
     *
     * @return true if the updater should run an update directly after startup, otherwise false
     */
    public boolean doUpdateOnStartup() {
        return doUpdateOnStartup;
    }

    public List<Dataset> getDatasetsList() {
        if (datasetsList == null) {
            return null;
        }
        return datasetsList.stream().toList();
    }

    public String getUpdateCronSchedule() {
        return updateCronSchedule;
    }

    public String getVirtuosoIsql() {
        return virtuosoIsql;
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

	public int getMaxRecordsPerImport() {
		return maxRecordsPerImport;
	}
}
