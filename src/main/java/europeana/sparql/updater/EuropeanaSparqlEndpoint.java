package europeana.sparql.updater;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Resource;

import europeana.sparql.updater.Dataset.State;
import europeana.sparql.updater.SparqlClient.Handler;

/**
 * A SPARQL client for querying the Virtuoso SPARQL endpoint. Used for getting
 * the ingest status of the datasets in Virtuoso.
 */
public class EuropeanaSparqlEndpoint {

	private static final Pattern DATASET_URI_PATTERN = Pattern
			.compile("http://data.europeana.eu/dataset/(\\d+)(_new)?");

	SparqlClient sparqlClient;
	String baseUrl;

	public int query(String queryString, Handler handler) {
		return sparqlClient.query(queryString, handler);
	}

	public EuropeanaSparqlEndpoint(String baseUrl) {
		this.baseUrl = baseUrl;

		sparqlClient = new SparqlClient(baseUrl);
	}

	public void setDebug(boolean debug) {
		sparqlClient.setDebug(debug);
	}

	public Map<Dataset, Dataset> listDatasets() {
		final Map<Dataset, Dataset> datasets = new HashMap<Dataset, Dataset>();
		final List<Dataset> datasetsInconsistent = new ArrayList<>();
		sparqlClient.query("SELECT DISTINCT ?g WHERE { GRAPH ?g {?s a ?o} }", new Handler() {
			@Override
			public boolean handleSolution(QuerySolution solution) throws Exception {
				Resource dsRes = solution.getResource("g");
				String uri = dsRes.getURI();
				Matcher matcher = DATASET_URI_PATTERN.matcher(uri);
				if (matcher.matches()) {
					Dataset ds = new Dataset(matcher.group(1));
					if (matcher.groupCount() == 2)
						datasetsInconsistent.add(ds);
					else {
						List<QuerySolution> query = sparqlClient.query("SELECT ?d WHERE { GRAPH <" + uri + "> {<" + uri
								+ "> <http://purl.org/dc/terms/issued> ?d }	}");
						if (!query.isEmpty())
							ds.setTimestampSparql(Instant.parse(query.get(0).getLiteral("d").getString()));
						else
							datasetsInconsistent.add(ds);
						datasets.put(ds, ds);
					}
				}
				return true;
			}
		});
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
		return datasets;
	}

}
