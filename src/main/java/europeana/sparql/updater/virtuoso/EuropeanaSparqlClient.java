package europeana.sparql.updater.virtuoso;

import com.apicatalog.jsonld.StringUtils;
import europeana.sparql.updater.Dataset;
import europeana.sparql.updater.Dataset.State;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A SPARQL client for querying the Virtuoso SPARQL endpoint. Used for getting the status of datasets in Virtuoso.
 */
public class EuropeanaSparqlClient extends SparqlClient {

    private static final Logger LOG = LogManager.getLogger(EuropeanaSparqlClient.class);

    private static final Pattern DATASET_URI_PATTERN = Pattern
            .compile("http://data.europeana.eu/dataset/(\\d+)(_new)?");

    /**
     * Initialize a new Sparql client for our purposes
     * @param sparqlEndpoint the url (and port) to send sparql queries to
     */
    public EuropeanaSparqlClient(String sparqlEndpoint) {
        super(sparqlEndpoint);
    }

    /**
     * List all datasets available in SPARQL
     * @return Map of dataset (values both as key and as value).
     */
    public Map<Dataset, Dataset> listDatasets() {
        LOG.info("Listing SPARQL data sets...");
        final Map<Dataset, Dataset> datasets = new HashMap<>();
        super.query("SELECT DISTINCT ?g WHERE { GRAPH ?g {?s a ?o} }", new HandleQueryResult(datasets));
        return datasets;
    }

    private class HandleQueryResult extends AbstractQueryResponseHandler {
        private final Map<Dataset, Dataset> datasets;
        public HandleQueryResult(Map<Dataset, Dataset> datasets) {
            this.datasets = datasets;
        }
        @Override
        public boolean handleSolution(QuerySolution solution) {
            final List<Dataset> datasetsInconsistent = new ArrayList<>();
            Resource dsRes = solution.getResource("g");
            String uri = dsRes.getURI();
            Matcher matcher = DATASET_URI_PATTERN.matcher(uri);
            if (matcher.matches()) {
                Dataset ds = new Dataset(matcher.group(1));
                if (matcher.groupCount() == 2 && !StringUtils.isBlank(matcher.group(2))) {
                    LOG.warn("SPARQL dataset {} was partially ingested!", ds);
                    datasetsInconsistent.add(ds);
                } else {
                    List<QuerySolution> query = query("SELECT ?d WHERE { GRAPH <" + uri + "> {<" + uri
                            + "> <http://purl.org/dc/terms/modified> ?d } }");
                    if (!query.isEmpty()) {
                        String date = query.get(0).getLiteral("d").getString();
                        ds.setTimestampSparql(Instant.parse(date));
                        LOG.trace("SPARQL dataset {} has date {}", ds, ds.getTimestampSparql());
                    } else {
                        LOG.warn("SPARQL dataset {} has no modified date!", ds);
                        datasetsInconsistent.add(ds);
                    }
                    datasets.put(ds, ds);
                }
            }

            // Mark inconsistent datasets as corrupt
            for (Dataset dsInconsistent : datasetsInconsistent) {
                Dataset ds = datasets.get(dsInconsistent);
                LOG.warn("SPARQL dataset {} is corrupt", ds);
                if (ds == null) {
                    dsInconsistent.setState(State.CORRUPT);
                    dsInconsistent.setTimestampSparql(null);
                    datasets.put(dsInconsistent, dsInconsistent);
                } else {
                    ds.setState(State.CORRUPT);
                    ds.setTimestampSparql(null);
                }
            }
            return true;
        }
    }

}
