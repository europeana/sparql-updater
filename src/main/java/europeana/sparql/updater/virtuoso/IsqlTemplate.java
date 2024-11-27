package europeana.sparql.updater.virtuoso;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

/**
 * Generates .sql script files that are provided to the isql tool from Virtuoso
 */
public class IsqlTemplate {

	public static String getCreatUpdateScript(File ttlImportFolder, String datasetId) throws IOException {
		InputStream is = IsqlTemplate.class.getClassLoader().getResourceAsStream("isql/create_update_graph.sql");
		String templateSource = IOUtils.toString(is, StandardCharsets.UTF_8);
		templateSource = templateSource.replaceAll("##TTL_FILENAME##",
				datasetId.endsWith("_new") ? datasetId.substring(0, datasetId.length() - 4) : datasetId);
		templateSource = templateSource.replaceAll("##DATASET_ID##", datasetId);
		templateSource = templateSource.replaceAll("##IMPORT_FOLDER##", ttlImportFolder.getAbsolutePath());
		return templateSource;
	}

	public static String getRenameGraphScript(String datasetId) throws IOException {
		InputStream is = IsqlTemplate.class.getClassLoader().getResourceAsStream("isql/rename_graph.sql");
		String templateSource = IOUtils.toString(is, StandardCharsets.UTF_8);
		templateSource = templateSource.replaceAll("##DATASET_ID##", datasetId);
		return templateSource;
	}

	public static String getRemoveGraphScript(String datasetId, boolean isTmpGraph) throws IOException {
		InputStream is = IsqlTemplate.class.getClassLoader().getResourceAsStream("isql/remove_graph.sql");
		String templateSource = IOUtils.toString(is, StandardCharsets.UTF_8);
		templateSource = templateSource.replaceAll("##DATASET_ID##", datasetId + (isTmpGraph ? "_new" : ""));
		return templateSource;
	}

}
