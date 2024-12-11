package europeana.sparql.updater.virtuoso;

import org.apache.jena.query.QuerySolution;

/**
 * Clients implementing the SparqlClient should define a query handler.
 */
public abstract class AbstractQueryResponseHandler {

    /**
     * Handler of the QuerySolution returned by the Sparql client
     * @param solution
     * @return true to continue processing the next URI, false to abort
     */
    public boolean handleSolution(QuerySolution solution) {
        return true;
    }
}
