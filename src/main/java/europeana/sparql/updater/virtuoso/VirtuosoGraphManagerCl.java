package europeana.sparql.updater.virtuoso;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A client for the isql command line tool of Virtuoso, which is used to create,
 * update and remove the Europeana datasets in Virtuoso's database.
 */
public class VirtuosoGraphManagerCl {

    private static final Logger LOG = LogManager.getLogger(VirtuosoGraphManagerCl.class);

    private static final int TIMEOUT_VIRTUOSO_CHECK = (int) TimeUnit.SECONDS.toMillis(5);
    private static final Pattern SUCCESS_TRIPLES = Pattern.compile("Result triples:\\s+(\\d+)");

    private final String dbaUser;
    private final String dbaPassword;
    private final int portNumber;
    private final File isqlCommand;
    private final File ttlImportFolder;
    private final File sqlFolder;

    public VirtuosoGraphManagerCl(File isqlCommand, int portNumber, String dbaUser, String dbaPassword,
                                  File ttlImportFolder, File sqlFolder) {
        super();
        this.isqlCommand = isqlCommand;
        this.portNumber = portNumber;
        this.dbaUser = dbaUser;
        this.dbaPassword = dbaPassword;
        this.ttlImportFolder = ttlImportFolder;
        this.sqlFolder = sqlFolder;
    }

    public File getTtlImportFolder() {
        return ttlImportFolder;
    }

    @SuppressWarnings("java:S1166") // no need to log exceptions if Virtuoso is not ready yet.
    public boolean isAvailable() {
        LOG.info("Checking if Virtuoso is available...");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLocalHost(), portNumber), TIMEOUT_VIRTUOSO_CHECK);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Waits a certain amount of time until we ping Virtuoso on the configured port.
     * If that doesn't happen within the specified waiting time a RuntimException is thrown
     * @param maxWaitTimeSec maximum amount of time before giving up
     * @return boolean
     */
    public boolean waitUntilAvailable(int maxWaitTimeSec)  {
        long start = System.currentTimeMillis();
        boolean available = false;
        while (!available && System.currentTimeMillis() - start < TimeUnit.SECONDS.toMillis(maxWaitTimeSec)) {
            available = isAvailable();
            try {
                TimeUnit.SECONDS.sleep(4);
            } catch (InterruptedException e) {
                LOG.warn("Interruption while checking if Virtuoso is ready", e);
                Thread.currentThread().interrupt();
            }
        }
        if (available) {
            return true;
        }
        throw new RuntimeException("Virtuoso not ready after waiting " + maxWaitTimeSec + " seconds");
    }



    public CommandResult removeObsoleteGraph(String datasetId) throws IOException {
        return removeGraph(datasetId, IsqlTemplate.getRemoveObsoleteGraphScript(datasetId));
    }

    public CommandResult removeTmpGraph(String datasetId) throws IOException {
        return removeGraph(datasetId, IsqlTemplate.getRemoveTmpGraphScript(datasetId));
    }

    private CommandResult removeGraph(String datasetId, String sqlString) throws IOException {
        LOG.debug("Removing graph for dataset {}", datasetId);
        File sqlFile = new File(sqlFolder, datasetId + "_remove.sql");
        FileUtils.write(sqlFile, sqlString, StandardCharsets.UTF_8);

        SqlCommandResult result = runSqlCommand(sqlFile);
        if (result != null && result.exitCode == 0) {
            return CommandResult.success("Removal successful");
        } else {
            return CommandResult.error(result.exitCode, result.output);
        }
    }

    public CommandResult renameTmpGraph(String datasetId) throws IOException {
        LOG.debug("Renaming graph for dataset {}", datasetId);
        String sqlString = IsqlTemplate.getRenameGraphScript(datasetId);
        File sqlFile = new File(sqlFolder, datasetId + "_rename.sql");
        FileUtils.write(sqlFile, sqlString, StandardCharsets.UTF_8);

        SqlCommandResult result = runSqlCommand(sqlFile);
        if (result != null && result.exitCode == 0) {
            return CommandResult.success("Removal successful");
        } else {
            return CommandResult.error(result.exitCode, result.output);
        }
    }

    public CommandResult ingestGraph(String datasetId) throws IOException {
        LOG.debug("Ingesting graph for dataset {}", datasetId);
        String sqlString = IsqlTemplate.getCreateUpdateScript(ttlImportFolder, datasetId);
        File sqlFile = new File(sqlFolder, datasetId + "_create_update.sql");
        FileUtils.write(sqlFile, sqlString, StandardCharsets.UTF_8);

        SqlCommandResult result = runSqlCommand(sqlFile);
        if (result != null && result.exitCode == 0) {
            Matcher matcher = SUCCESS_TRIPLES.matcher(result.output);
            if (matcher.find()) {
                if (("0").equals(matcher.group(1))) {
                    return CommandResult.error("Empty dataset. Output:\n" + result.output);
                }
                return CommandResult.success(matcher.group(1) + " triples");
            }
        }
        return CommandResult.error(result.exitCode, result.output);
    }

    private SqlCommandResult runSqlCommand(File sqlFile) throws IOException {
        try {
            LOG.debug("Starting process to execute {}...", sqlFile.getName());
            ProcessBuilder processBuilder = new ProcessBuilder(isqlCommand.getAbsolutePath(), String.valueOf(portNumber),
                    dbaUser, dbaPassword,
                    (LOG.isDebugEnabled() || LOG.isTraceEnabled() ? "VERBOSE=ON" : ""),
                    sqlFile.getAbsolutePath());
            Process process = processBuilder.redirectErrorStream(true).start();
            process.waitFor();
            return new SqlCommandResult(process);
        } catch (InterruptedException e) {
            LOG.warn("Process was interrupted", e);
            Thread.currentThread().interrupt();
        } finally {
            LOG.debug("Deleting SQL file {}...", sqlFile);
            sqlFile.delete();
        }
        return null;
    }

    private static final class SqlCommandResult {
        private int exitCode = -1;
        private String output = null;

        public SqlCommandResult(Process process) throws IOException {
            if (process != null) {
                this.exitCode = process.exitValue();
                this.output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
                LOG.debug("Process exit value = {}, output = {}", exitCode, output);
            } else {
                LOG.error("Process is null");
            }
        }
    }

}
