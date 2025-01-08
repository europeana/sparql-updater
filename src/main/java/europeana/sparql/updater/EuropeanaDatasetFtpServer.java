package europeana.sparql.updater;

import europeana.sparql.updater.exception.DownloadException;
import europeana.sparql.updater.exception.UpdaterException;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * An FTP client for accessing the datasets files on the Europeana FTP server
 */
public class EuropeanaDatasetFtpServer {

    private static final Logger LOG = LogManager.getLogger(EuropeanaDatasetFtpServer.class);

    private static final int RETRIES = 3;

    private final String hostName;
    private final int port;
    private final String path;
    private final String username;
    private final String password;
    protected FTPClient ftpClient;
    private final boolean downloadChecksum;

    /**
     * Initialize a new FTP server
     * @param hostName the host name of the ftp server
     * @param port the port of the ftp server
     * @param path the path on the server where datasets are available
     * @param username the username to login
     * @param password the password to login
     * @param downloadChecksum if true then the checksum file is downloaded as well
     */
    public EuropeanaDatasetFtpServer(String hostName, int port, String path, String username, String password,
                                     Boolean downloadChecksum) {
        super();
        this.hostName = hostName;
        this.port = port;
        this.path = path;
        this.username = username;
        this.password = password;
        this.downloadChecksum = downloadChecksum;

        initConnection();
    }

    private void initConnection() {
        if (ftpClient == null) {
            LOG.info("Initialising connection to FTP server...");
            ftpClient = new FTPClient();
        } else {
            LOG.info("Re-establishing connection to FTP server...");
        }

        // 1. setup connection
        try {
            ftpClient.connect(hostName, port);
            logServerReply(ftpClient);
            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                LOG.error("Error connecting to ftp server {}:{}, error code: {}", hostName, port, replyCode);
                return;
            }
            boolean success = ftpClient.login(username, password);
            logServerReply(ftpClient);
            if (!success) {
                LOG.error("Could not login to FTP server");
            } else {
                LOG.info("Logged in to FTP server");
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        // 2. set properties
        try {
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        } catch (IOException io) {
            LOG.error("Error setting file type to binary", io);
        }

        // 3. go to folder with TTL files
        try {
            LOG.debug("Changing work directory to path {}...", path);
            ftpClient.changeWorkingDirectory(path);
            logServerReply(ftpClient);
        } catch (IOException io) {
            LOG.error("Error changing working directory to path {}", path, io);
        }
    }

    protected static void logServerReply(FTPClient ftpClient) {
        int replyCode = ftpClient.getReplyCode();
        if (!FTPReply.isPositiveCompletion(replyCode)) {
            LOG.error("Ftp server returned error code {}!", replyCode);
        }
        if (LOG.isDebugEnabled()) {
            String[] replies = ftpClient.getReplyStrings();
            if (replies != null && replies.length > 0) {
                for (String aReply : replies) {
                    LOG.debug("FTP server response = {}", aReply);
                }
            }
        }
    }

    /**
     * When processing a dataset takes long the server may close the connection to the ftp client.
     */
    @SuppressWarnings("java:S1166") // no need to always log exceptions when checking status
    private void reconnectIfNeeded() {
        boolean connectionOk = false;
        try {
            connectionOk = ftpClient.sendNoOp();
            LOG.debug("FTP connection ok = {}", connectionOk);
        } catch (IOException e) {
            LOG.info("FTP connection was closed!");
        }
        if (!connectionOk) {
            try {
                ftpClient.disconnect();
            } catch (IOException e) {
                LOG.warn("Error trying to disconnect FTP client", e);
            }
            initConnection();
        }
    }

    /**
     * List the datasets available on the ftp server
     * @return a list of available data sets
     */
    public List<Dataset> listDatasets() {
        LOG.info("Listing FTP server datasets...");
        List<Dataset> datasetList = new ArrayList<>();
        try {
            ftpClient.changeWorkingDirectory(path);
            logServerReply(ftpClient);

            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            FTPFile[] listFiles = ftpClient.listFiles();
            logServerReply(ftpClient);

            for (FTPFile f : listFiles) {
                if (f.getName().endsWith(".zip")) {
                    Dataset ds = new Dataset(f.getName().substring(0, f.getName().indexOf('.')));
                    ds.setTimestampFtp(f.getTimestamp().toInstant());
                    datasetList.add(ds);
                    LOG.trace("  Found FTP server dataset {} with date {}", ds, ds.timestampFtp);
                }
            }
        } catch (IOException ex) {
            LOG.error("Error listing data sets", ex);
            // we'll try to continue with what we have
        }
        return datasetList;
    }

    /**
     * Download the zip file of a particular dataset
     * @param outputFile the location and file name to store the downloaded file
     * @param datasetId the id of the dataset to download
     * @throws UpdaterException when there is a problem downloading the file
     */
    public void download(File outputFile, String datasetId) throws UpdaterException {
        reconnectIfNeeded();
        try {
            FTPFile[] listFiles = ftpClient.listFiles();
            logServerReply(ftpClient);
            for (FTPFile f : listFiles) {
                if ((downloadChecksum || f.getName().endsWith(".zip")) && (f.getName().startsWith(datasetId + "."))) {
                    downloadFile(outputFile, f);
                    break;
                }
            }
            LOG.debug("Set {} downloaded as file {}", datasetId, outputFile);
        } catch (IOException io) {
            throw new DownloadException("Error listing files", io);
        }
    }

    protected void downloadFile(File outputFile, FTPFile f) throws UpdaterException {
        try {
            FileOutputStream fos = new FileOutputStream(outputFile);
            int retry = 0;
            while (!ftpClient.retrieveFile(path + "/" + f.getName(), fos) && retry < RETRIES) {
                LOG.warn("Failed to download file {}/{} - Retrying...", path, f.getName());
                retry++;
                if (retry == RETRIES) {
                    throw new DownloadException("Failed to download " + path + "/" + f.getName());
                }
            }
            fos.close();
        } catch (IOException io) {
            throw new DownloadException("Failed to download " + path + "/" + f.getName(), io);
        }
    }

}
