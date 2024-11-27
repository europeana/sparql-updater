package europeana.sparql.updater.virtuoso;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A client for the isql command line tool of Virtuoso, which is used to create,
 * update and remove the Europeana datasets in Virtuoso's database.
 *
 */
public class VirtuosoGraphManagerCl {

	private static final Logger LOG = LogManager.getLogger(VirtuosoGraphManagerCl.class);

	String dbaUser;
	String dbaPassword;
	int portNumber;
	File isqlCommand;
	File ttlImportFolder;
	File sqlFolder;

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

	public CommandResult removeGraph(String datasetId) throws IOException {
		return removeGraph(datasetId, false);
	}

	public CommandResult removeTmpGraph(String datasetId) throws IOException {
		return removeGraph(datasetId, true);
	}

	private CommandResult removeGraph(String datasetId, boolean isTmpGraph) throws IOException {
		String sqlString = IsqlTemplate.getRemoveGraphScript(datasetId, isTmpGraph);
		File sqlFile = new File(sqlFolder, datasetId + "_remove.sql");
		FileUtils.write(sqlFile, sqlString, StandardCharsets.UTF_8);

		Process process = null;
		try {
			process = run(sqlFile);
		} catch (InterruptedException e) {
			return CommandResult.error("Interrupted: " + e.getMessage());
		}
		int exitCode = process.exitValue();
		String output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
		LOG.debug(output);
		if (exitCode == 0)
			return CommandResult.success("Removal successful");
		else
			return CommandResult.error("Exit code: " + exitCode + " ; Output:\n" + output);
	}

	public CommandResult renameTmpGraph(String datasetId) throws IOException {
		String sqlString = IsqlTemplate.getRenameGraphScript(datasetId);
		File sqlFile = new File(sqlFolder, datasetId + "_rename.sql");
		FileUtils.write(sqlFile, sqlString, StandardCharsets.UTF_8);
		Process process = null;
		try {
			process = run(sqlFile);
		} catch (InterruptedException e) {
			return CommandResult.error("Interrupted: " + e.getMessage());
		}
		int exitCode = process.exitValue();
		String output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
		LOG.debug(output);
		if (exitCode == 0)
			return CommandResult.success("Rename successful");
		else
			return CommandResult.error("Exit code: " + exitCode + " ; Output:\n" + output);
	}

	public CommandResult ingestGraph(String datasetId) throws IOException {
		String sqlString = IsqlTemplate.getCreatUpdateScript(ttlImportFolder, datasetId);
		File sqlFile = new File(sqlFolder, datasetId + "_create_update.sql");
		FileUtils.write(sqlFile, sqlString, StandardCharsets.UTF_8);
		Process process = null;
		try {
			process = run(sqlFile);
		} catch (InterruptedException e) {
			return CommandResult.error("Interrupted: " + e.getMessage());
		}
		int exitCode = process.exitValue();
		String output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
		LOG.debug(output);
		Pattern successPattern = Pattern.compile("Result triples:\\s+(\\d+)");
		Matcher matcher = successPattern.matcher(output);
		if (matcher.find()) {
			if (matcher.group(1).equals("0"))
				return CommandResult.error("Empty dataset. Output:\n" + output);
			return CommandResult.success(matcher.group(1) + " triples");
		} else
			return CommandResult.error("Exit code: " + exitCode + " ; Output:\n" + output);
	}

	private Process run(File sqlFile) throws InterruptedException, IOException {
		LOG.debug(sqlFile.getName());
		LOG.debug(FileUtils.readFileToString(sqlFile, StandardCharsets.UTF_8));
		ProcessBuilder processBuilder = new ProcessBuilder(isqlCommand.getAbsolutePath(), String.valueOf(portNumber),
				dbaUser, dbaPassword, "VERBOSE=ON", sqlFile.getAbsolutePath());
		Process process = processBuilder.redirectErrorStream(true).start();
		process.waitFor();
//		sqlFile.delete();
		return process;
	}

	public File getTtlImportFolder() {
		return ttlImportFolder;
	};

}
