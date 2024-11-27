package europeana.sparql.updater;

import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Container class for all settings that we load from properties file and optionally override from a user.properties file
 */
@Configuration
@PropertySource("classpath:sparql-updater.properties")
@PropertySource(value = "classpath:sparql-updater.user.properties", ignoreResourceNotFound = true)
public class UpdaterSettings {

    private static final Logger LOG = LogManager.getLogger(UpdaterSettings.class);

    @Value("${update.cron}")
    private String updaterCronSchedule;

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
        LOG.info("Configuration:");
        LOG.info("  Virtuoso endpoint = {}", virtuosoEndpoint);
        LOG.info("  Virtuoso port = {}", virtuosoPort);
    }

    public String getUpdaterCronSchedule() {
        return updaterCronSchedule;
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
