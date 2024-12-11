package europeana.sparql.updater.virtuoso;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * General implementation of a client for querying SPARQL endpoints
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

    protected final String baseUrl;
    protected final Dataset dataset;
    protected String queryPrefix;

    public SparqlClient(String baseUrl) {
        this(baseUrl, STANDARD_PREFIXES);
    }

    public SparqlClient(String baseUrl, String queryPrefix) {
        super();
        this.baseUrl = baseUrl;
        this.dataset = null;
        this.queryPrefix = queryPrefix == null ? "" : queryPrefix;
    }

    public SparqlClient(String baseUrl, Map<String, String> queryPrefixes) {
        super();
        this.baseUrl = baseUrl;
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

    public SparqlClient(Dataset dataset) {
        this(dataset, STANDARD_PREFIXES);
    }

    public SparqlClient(Dataset dataset, String queryPrefix) {
        super();
        this.dataset = dataset;
        this.baseUrl = null;
        this.queryPrefix = queryPrefix;
    }

    public SparqlClient(Dataset dataset, Map<String, String> queryPrefixes) {
        super();
        this.baseUrl = null;
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

    public int query(String queryString, AbstractQueryResponseHandler handler) {
        int wdCount = 0;
        String fullQuery = queryPrefix + queryString;
        try (QueryExecution qexec = createQueryExecution(fullQuery)) {
            ResultSet results = qexec.execSelect();
//            ResultSetFormatter.out(System.out, results, query);
            while (results.hasNext() && !callHandlerForItem(handler, results.next())) {
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
        if (baseUrl != null)
            return QueryExecutionFactory.sparqlService(this.baseUrl, fullQuery);
        return QueryExecutionFactory.create(fullQuery, dataset);
    }

//	public int queryWithPaging(String queryString, int resultsPerPage, String orderVariableName, Handler handler) throws Exception {
//		int[] offsett=new int[] {0};
//		boolean concluded=false;
//		while (!concluded) {
//			String fullQuery = String.format("%s %s\n%s" + 
//					"LIMIT %d\n" + 
//					"OFFSET %d ", queryPrefix, queryString, (orderVariableName ==null ? "" : "ORDER BY ("+orderVariableName+")\n"), resultsPerPage, offsett[0]);
//			if(debug)
//				System.out.println(fullQuery);
//			RetryExec<Boolean, Exception> exec=new RetryExec<Boolean, Exception>(retries) {
//				@Override
//				protected Boolean doRun() throws Exception {
//					QueryExecution qexec = createQueryExecution(fullQuery);
//					try {
//						ResultSet results = qexec.execSelect();
//			//            ResultSetFormatter.out(System.out, results, query);
//						if(!results.hasNext())
//							return true;
//						while(results.hasNext()) {
//							Resource resource = null;
//							QuerySolution hit = results.next();
//							try {
//								if (!handler.handleSolution(hit)) {
//									System.err.println("RECEIVED HANDLER ABORT");
//									return true;
////									break;
//								}
//							} catch (Exception e) {
//								System.err.println("Error on record handler: "+(resource==null ? "?" : resource.getURI()));
//								e.printStackTrace();
//								System.err.println("PROCEEDING TO NEXT URI");
//							}
//							offsett[0]++;
//						}
//						if(debug)
//							System.out.printf("QUERY FINISHED - %d resources\n", offsett);            
//						return false;
//					} catch (Exception ex) {
//						System.err.println("WARN: (will retry 3x) Error on query: "+fullQuery);
//						throw ex;
//					} finally {
//						qexec.close();
//					}
//				}
//			};
//			concluded=exec.run();
//		}
//		return offsett[0];
//	}

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
