package europeana.sparql.updater;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EuropeanaDatasetFtpServer {
	
    private static final Logger LOG = LogManager.getLogger(EuropeanaDatasetFtpServer.class);
    
	public enum FileFormat{ XML, TTL }

	public static final EuropeanaDatasetFtpServer GENERAL_PUBLIC = new EuropeanaDatasetFtpServer("download.europeana.eu", 21, FileFormat.TTL);
	
	FileFormat fileFormat = FileFormat.XML;

	String server ;
    int port = 21;
    String user = "anonymous";
    String pass = "";
    protected FTPClient ftpClient ;
    protected String pathname;
    boolean downloadChecksum=false;
    
    public EuropeanaDatasetFtpServer(String server, int port, FileFormat fileFormat) {
		super();
		this.server = server;
		this.port = port;
		this.fileFormat = fileFormat;
        this.pathname = "/dataset/"+fileFormat;
	}

	protected static void showServerReply(FTPClient ftpClient) {
        String[] replies = ftpClient.getReplyStrings();
        if (replies != null && replies.length > 0) {
            for (String aReply : replies) {
        		LOG.info("SERVER: " + aReply);
            }
        }
    }
	

	public void enableDownloadChecksumFiles() {
		downloadChecksum=true;
	}
	
	public void download(File outputFile, String datasetId) throws IOException {
    	initConnection();
		ftpClient.changeWorkingDirectory(pathname);
        showServerReply(ftpClient);
        
        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

        FTPFile[] listFiles = ftpClient.listFiles();
        showServerReply(ftpClient);
        LIST: for(FTPFile f: listFiles) {
//        	System.out.println(f.getName());
        	if(downloadChecksum || f.getName().endsWith(".zip")) {
    			if(f.getName().startsWith(datasetId+".")) {
					downloadFile(outputFile, f);       					
    				break;
    			}
        	}
        }
		LOG.info("download finished");
	}
	
	
	public List<Dataset> listDatasets() {
		List<Dataset> datasetList=new ArrayList<Dataset>();
        try {
        	initConnection();
			ftpClient.changeWorkingDirectory(pathname);
            showServerReply(ftpClient);
            
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            FTPFile[] listFiles = ftpClient.listFiles();
            showServerReply(ftpClient);
            LIST: for(FTPFile f: listFiles) {
//            	System.out.println(f.getName());
            	if(f.getName().endsWith(".zip")) {
            		Dataset ds=new Dataset(f.getName().substring(0, f.getName().indexOf('.')));
            		ds.setTimestampFtp(f.getTimestamp().toInstant());
            		datasetList.add(ds);
            	}
            }
//    		System.out.println("downloaded "+listFiles.length+" files");
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
		return datasetList;
	}
	
	
	
	protected void initConnection() {
		ftpClient = new FTPClient();
        try {
            ftpClient.connect(server, port);
            showServerReply(ftpClient);
            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
            	LOG.error("Operation failed. Server reply code: " + replyCode);
                return;
            }
            boolean success = ftpClient.login(user, pass);
            showServerReply(ftpClient);
            if (!success) {
            	LOG.error("Could not login to the server");
                return;
            } else {
            	LOG.debug("LOGGED IN SERVER");
            }
            
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
	}
	
	
//	public void downloadAllDatasets(File outputFolder) {
//        try {
//        	initConnection();
//			ftpClient.changeWorkingDirectory(pathname);
//            showServerReply(ftpClient);
////            FTP.
//            
//            ftpClient.enterLocalPassiveMode();
//            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
//            
//            FTPFile[] listFiles = ftpClient.listFiles();
//            showServerReply(ftpClient);
//            for(FTPFile f: listFiles) {
//            	if(downloadChecksum || f.getName().endsWith(".zip")) {
//            		downloadFile(outputFolder, f);
//            	}
//            }
//    		System.out.println("downloaded "+listFiles.length+" files");
//            
//        } catch (IOException ex) {
//            System.out.println("Oops! Something wrong happened");
//            ex.printStackTrace();
//        }
//	}
	
	protected void downloadFile(File outputFile, FTPFile f) throws IOException {
		System.out.println("downloading "+f.getName());
		
//		InputStream is = ftpClient.retrieveFileStream("/dataset/TTL/"+f.getName());
		FileOutputStream fos=new FileOutputStream(outputFile);
		int retry=0;
		while (!ftpClient.retrieveFile(pathname+"/"+f.getName(), fos) && retry<3) {
			System.out.println( "Failed to download "+ pathname+"/"+f.getName()+" - Retrying...");
			retry++;
			if(retry==3)
				throw new RuntimeException("Failed to download "+ pathname+"/"+f.getName());
		}
//		IOUtils.copy(is, fos);
//		is.close();
		fos.close();
	}
	
	
	
	
	

	public static void main(String[] args) throws Exception {
		String outputFolder = null;
		String fileFormat = "XML";//XML|TTL

		if (args != null && args.length >= 1) {
				outputFolder = args[0];
				if (args.length >= 2) 
					fileFormat = args[1];
		}else {
			outputFolder = "c://users/nfrei/desktop/data/europeana_dataset";
		}

		EuropeanaDatasetFtpServer ftpDown=EuropeanaDatasetFtpServer.GENERAL_PUBLIC;
		ftpDown.download(new File(outputFolder), "123");
	}
}
