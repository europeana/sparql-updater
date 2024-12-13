package europeana.sparql.updater.virtuoso;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * General implementation of a client for doing SPARQL queries
 */
public class SparqlClient {

    private static final Logger LOG = LogManager.getLogger(SparqlClient.class);

    public static final Map<String, String> STANDARD_PREFIXES = Map.of(
                    "http://purl.org/dc/elements/1.1/", "dc",
                    "http://purl.org/dc/terms/", "dcterms",
                    "http://www.europeana.eu/schemas/edm/", "edm",
                    "http://www.openarchives.org/ore/terms/", "ore",
                    "http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf",
                    "http://www.w3.org/2000/01/rdf-schema#", "rdfs",
                    "http://www.w3.org/2004/02/skos/core#", "skos",
                    "http://www.w3.org/2002/07/owl#", "owl");

    protected final String sparqlEndpoint;
    protected final Dataset dataset;
    protected String queryPrefix;

    /**
     * Initialize a new sparql client that uses the standard list of prefixes
     * @param sparqlEndpoint the url (and port) to send sparql queries to
     */
    public SparqlClient(String sparqlEndpoint) {
        this(sparqlEndpoint, STANDARD_PREFIXES);
    }

    /**
     * Initialize a new sparql client that uses a specific set of prefixes
     * @param sparqlEndpoint the url (and port) to send sparql queries to
     * @param queryPrefix the query prefix to use
     */
    public SparqlClient(String sparqlEndpoint, String queryPrefix) {
        this.sparqlEndpoint = sparqlEndpoint;
        this.dataset = null;
        this.queryPrefix = (queryPrefix == null ? "" : queryPrefix);
    }

    /**
     * Initialize a new sparql client that uses a specific set of prefixes
     * @param sparqlEndpoint the url (and port) to send sparql queries to
     * @param queryPrefixes the query prefixes to use
     */
    public SparqlClient(String sparqlEndpoint, Map<String, String> queryPrefixes) {
        super();
        this.sparqlEndpoint = sparqlEndpoint;
        this.dataset = null;
        StringBuilder tmp = new StringBuilder();
        for (Entry<String, String> ns : queryPrefixes.entrySet()) {
            tmp.append("PREFIX ")
                    .append(ns.getValue())
                    .append(": <")
                    .append(ns.getKey())
                    .append(">\n");
        }
        queryPrefix = tmp.toString();
    }

    /**
     * Initialize a new sparql client that uses a specific set of prefixes and a specific datset
     * @param dataset the dataset to use for queries
     */
    public SparqlClient(Dataset dataset) {
        this(dataset, STANDARD_PREFIXES);
    }

    /**
     * Initialize a new sparql client that uses a specific prefix and a specific datset
     * @param dataset the dataset to use when generating queries
     * @param queryPrefix the query prefix to use
     */
    public SparqlClient(Dataset dataset, String queryPrefix) {
        super();
        this.dataset = dataset;
        this.sparqlEndpoint = null;
        this.queryPrefix = queryPrefix;
    }

    /**
     * Initialize a new sparql client that uses a specific set of prefixes and a specific dataset
     * @param dataset the dataset to use when generating queries
     * @param queryPrefixes the query prefixes to use
     */
    public SparqlClient(Dataset dataset, Map<String, String> queryPrefixes) {
        super();
        this.sparqlEndpoint = null;
        this.dataset = dataset;
        StringBuilder tmp = new StringBuilder();
        for (Entry<String, String> ns : queryPrefixes.entrySet()) {
            tmp.append("PREFIX ")
                    .append(ns.getValue())
                    .append(": <")
                    .append(ns.getKey())
                    .append(">\n");
        }
        queryPrefix = tmp.toString();
    }

    public List<QuerySolution> query(String queryString) {
        final List<QuerySolution> solutions = new ArrayList<>();
        query(queryString, new AbstractQueryResponseHandler() {
            @Override
            public boolean handleSolution(QuerySolution solution) {
                solutions.add(solution);
                return true;
            }
        });
        return solutions;
    }

    /**
     * Execute a sparl query and process the response with the provided handler
     * @param queryString the query to execute
     * @param handler a QueryResponseHandler that processes the results of the query
     * @return the number of processed query results
     */
    public int query(String queryString, AbstractQueryResponseHandler handler) {
        int wdCount = 0;
        String fullQuery = queryPrefix + queryString;
        try (QueryExecution qexec = createQueryExecution(fullQuery)) {
            ResultSet results = qexec.execSelect();
            while (results.hasNext() && callHandlerForItem(handler, results.next())) {
                wdCount++;
            }
            LOG.debug("Query finished - processed {} resources", wdCount);
        } catch (Exception ex) {
            LOG.error("Error on query: {}", fullQuery, ex);
        }
        return wdCount;
    }

    private boolean callHandlerForItem(AbstractQueryResponseHandler handler, QuerySolution hit) {
        boolean continueProcessing = true;
        try {
            continueProcessing = handler.handleSolution(hit);
            if (!continueProcessing) {
                LOG.trace("Received handler abort");
            }
        } catch (RuntimeException e) {
            LOG.warn("Error while processing result item {}. Continuing with next result item...", hit, e);
        }
        return continueProcessing;
    }

    private QueryExecution createQueryExecution(String fullQuery) {
        if (sparqlEndpoint != null) {
            return QueryExecutionFactory.sparqlService(this.sparqlEndpoint, fullQuery);
        }
        return QueryExecutionFactory.create(fullQuery, dataset);
    }

    public void createAllStatementsAboutAndReferingResource(String resourceUri, Model createInModel) {
        createAllStatementsAboutResource(resourceUri, createInModel);
        createAllStatementsReferingResource(resourceUri, createInModel);
    }

    public void createAllStatementsAboutResource(String resourceUri, Model createInModel) {
        final Resource subjRes = createInModel.createResource(resourceUri);
        query("SELECT ?p ?o WHERE {<" + resourceUri + "> ?p ?o}", new AbstractQueryResponseHandler() {
            @Override
            public boolean handleSolution(QuerySolution solution) {
                Resource pRes = solution.getResource("p");
                Resource oRes = null;
                Literal oLit = null;
                try {
                    oRes = solution.getResource("o");
                } catch (RuntimeException e) {
                    LOG.trace("Unable to get resource from solution {}. Trying literal instead...", solution, e);
                    oLit = solution.getLiteral("o");
                }
                createInModel.add(createInModel.createStatement(subjRes, createInModel.createProperty(pRes.getURI()),
                        oRes == null ? oLit : oRes));
                return true;
            }
        });
    }

    public void createAllStatementsReferingResource(String resourceUri, Model createInModel) {
        final Resource subjRes = createInModel.createResource(resourceUri);
        query("SELECT ?s ?p WHERE {?s ?p <" + resourceUri + ">}", new AbstractQueryResponseHandler() {
            @Override
            public boolean handleSolution(QuerySolution solution) {
                Resource pRes = solution.getResource("p");
                Resource sRes = solution.getResource("s");
                createInModel
                        .add(createInModel.createStatement(sRes, createInModel.createProperty(pRes.getURI()), subjRes));
                return true;
            }
        });
    }

    public Model getAllStatementsAboutAndReferingResource(String resourceUri) {
        final Model model = ModelFactory.createDefaultModel();
        createAllStatementsAboutAndReferingResource(resourceUri, model);
        return model;
    }

}
