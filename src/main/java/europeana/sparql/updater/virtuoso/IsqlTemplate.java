package europeana.sparql.updater.virtuoso;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

/**
 * Generates .sql script files that are provided to the isql tool of Virtuoso
 */
public final class IsqlTemplate {

    private static final String DATASET_ID    = "##DATASET_ID##";
    private static final String TTL_FILENAME  = "##TTL_FILENAME##";
    private static final String IMPORT_FOLDER = "##IMPORT_FOLDER##";

    private static final String SQL_FILE_UPDATE = "isql/create_update_graph.sql";
    private static final String SQL_FILE_RENAME = "isql/rename_graph.sql";
    private static final String SQL_FILE_REMOVE = "isql/remove_graph.sql";
    private static final String SUFFIX_NEW = "_new";

    private IsqlTemplate() {
        // empty constructor to avoid initialization
    }

    /**
     * Loads the generic sql script for doing an update and fills in the proper data
     * @param ttlImportFolder folder where TTL file is located
     * @param datasetId id of the data set to load
     * @return string containing the generated sql script
     * @throws IOException when there's a problem reading the generic sql script
     */
    public static String getCreateUpdateScript(File ttlImportFolder, String datasetId) throws IOException {
        try (InputStream is = IsqlTemplate.class.getClassLoader().getResourceAsStream(SQL_FILE_UPDATE)) {
            String templateSource = IOUtils.toString(is, StandardCharsets.UTF_8);
            templateSource = templateSource.replace(TTL_FILENAME,
                    datasetId.endsWith(SUFFIX_NEW) ? datasetId.substring(0, datasetId.length() - SUFFIX_NEW.length()) : datasetId);
            templateSource = templateSource.replace(DATASET_ID, datasetId);
            templateSource = templateSource.replace(IMPORT_FOLDER, ttlImportFolder.getAbsolutePath());
            return templateSource;
        }
    }

    /**
     * Loads the generic sql script for renaming a SPARQL graph
     * @param datasetId id of the data set to rename
     * @return string containing the generated sql script
     * @throws IOException when there's a problem reading the generic sql script
     */
    public static String getRenameGraphScript(String datasetId) throws IOException {
        try (InputStream is = IsqlTemplate.class.getClassLoader().getResourceAsStream(SQL_FILE_RENAME)) {
            String templateSource = IOUtils.toString(is, StandardCharsets.UTF_8);
            templateSource = templateSource.replace(DATASET_ID, datasetId);
            return templateSource;
        }
    }

    /**
     * Loads the generic sql script for removing an obsolete SPARQL graph
     * @param datasetId id of the data set to delete
     * @return string containing the generated sql script
     * @throws IOException when there's a problem reading the generic sql script
     */
    public static String getRemoveObsoleteGraphScript(String datasetId) throws IOException {
        return getRemoveGraphScript(datasetId, false);
    }

    /**
     * Loads the generic sql script for removing a temporary (new) SPARQL graph
     * @param datasetId id of the data set to delete
     * @return string containing the generated sql script
     * @throws IOException when there's a problem reading the generic sql script
     */
    public static String getRemoveTmpGraphScript(String datasetId) throws IOException {
        return getRemoveGraphScript(datasetId, true);
    }

    private static String getRemoveGraphScript(String datasetId, boolean isTmpGraph) throws IOException {
        try (InputStream is = IsqlTemplate.class.getClassLoader().getResourceAsStream(SQL_FILE_REMOVE)) {
            String templateSource = IOUtils.toString(is, StandardCharsets.UTF_8);
            templateSource = templateSource.replace(DATASET_ID, datasetId + (isTmpGraph ? SUFFIX_NEW : ""));
            return templateSource;
        }
    }

}
