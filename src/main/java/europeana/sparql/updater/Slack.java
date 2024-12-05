package europeana.sparql.updater;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Utility class for sending data to Slack webhook
 */
public final class Slack {

    private static final Logger LOG = LogManager.getLogger(Slack.class);

    private Slack() {
        // empty constructor to prevent initialization
    }

    /**
     * Method publishes the report at the configured Slack channel.
     * @param report the report to send
     * @param slackWebhook the Slack url to send the report to
     */
    public static void publishUpdateReport(UpdateReport report, String slackWebhook) {
        String message = report.printSummary();
        message = String.format("{\"text\": \"%s\"}", message.replace("\n", "\\n"));
        LOG.info("Sending Slack message : {}", message);
        try {
            HttpPost httpPost = new HttpPost(slackWebhook);
            StringEntity entity = new StringEntity(message);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");
            try (CloseableHttpClient httpClient = HttpClients.createDefault();
                 CloseableHttpResponse response = httpClient.execute(httpPost)) {
                LOG.info("Received status {} while calling Slack!", response.getStatusLine().getStatusCode());
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    LOG.info(" Successfully sent Slack message!");
                }
            }
        } catch (IOException e) {
            LOG.error("Exception while sending Slack message!", e);
        }
    }
}