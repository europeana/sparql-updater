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
 * A SPARQL client for querying the Virtuoso SPARQL endpoint. Used for getting the ingest status of the datasets in
 * Virtuoso.
 */
public class EuropeanaSparqlClient {

    private static final Logger LOG = LogManager.getLogger(EuropeanaSparqlClient.class);

    private static final Pattern DATASET_URI_PATTERN = Pattern
            .compile("http://data.europeana.eu/dataset/(\\d+)(_new)?");

    SparqlClient sparqlClient;
    String baseUrl;

    /**
     * Intialize a new Virtuoso client
     * @param baseUrl the url where to connect to
     */
    public EuropeanaSparqlClient(String baseUrl) {
        this.baseUrl = baseUrl;

        sparqlClient = new SparqlClient(baseUrl);
        //sparqlClient.setRetries(3);
    }

    /**
     * Run a sparl query
     * @param queryString the query to execute
     * @param handler a QueryHandler that specifies what to do with the response
     * @return the number of returned results
     */
    public int query(String queryString, AbstractQueryResponseHandler handler) {
        return sparqlClient.query(queryString, handler);
    }

    /**
     * List all datasets available in SPARQL
     * @return Map of
     */
    public Map<Dataset, Dataset> listDatasets() {
        LOG.info("Listing SPARQL datasets...");
        final Map<Dataset, Dataset> datasets = new HashMap<>();
        sparqlClient.query("SELECT DISTINCT ?g WHERE { GRAPH ?g {?s a ?o} }", new HandleQueryResult(datasets));
        return datasets;
    }

    private class HandleQueryResult extends AbstractQueryResponseHandler {
        private Map<Dataset, Dataset> datasets;
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
                    List<QuerySolution> query = sparqlClient.query("SELECT ?d WHERE { GRAPH <" + uri + "> {<" + uri
                            + "> <http://purl.org/dc/terms/modified> ?d } }");
                    if (!query.isEmpty()) {
                        String date = query.get(0).getLiteral("d").getString();
                        ds.setTimestampSparql(Instant.parse(date));
                        LOG.trace("SPARQL dataset {} has date {}", ds, ds.getTimestampFtp());
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
