package controllers;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Provider;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import play.inject.ApplicationLifecycle;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.mvc.Controller;
import play.mvc.Result;

/**
 * This controller contains an action to handle HTTP requests to the
 * application's home page.
 */
@Singleton
public class HomeController extends Controller {

	@Inject
	WSClient ws;

	/** The application config. */
	public static final Config CONFIG = ConfigFactory.load();

	private String indexName = CONFIG.getString("index.name");
	private Settings settings = Settings.settingsBuilder()
			.put("network.host", CONFIG.getString("index.host"))
			.put("path.home", CONFIG.getString("index.location"))
			.put("http.port", CONFIG.getString("index.http_port"))
			.put("transport.tcp.port", CONFIG.getString("index.tcp_port")).build();
	private Node node =
			NodeBuilder.nodeBuilder().settings(settings).local(true).build().start();

	/** The Elasticsearch client to be used by all parts of the application. */
	Client client = node.client();

	/**
	 * @param lifecycle The injected Play lifecycle
	 */
	@Inject
	public HomeController(ApplicationLifecycle lifecycle) {
		lifecycle.addStopHook(() -> {
			node.close();
			client.close();
			return CompletableFuture.completedFuture(null);
		});
	}

	@Inject
	private Provider<play.Application> app;

	/** @return The robots.txt */
	public Result robots() {
		return ok(app.get().resourceAsStream("robots.txt")).as("text/plain");
	}

	/**
	 * @return OK with contact information
	 */
	public Result contact() {
		return ok(views.html.contact.render());
	}

	/**
	 * @return OK with imprint information
	 */
	public Result imprint() {
		return ok(views.html.imprint.render());
	}

	/**
	 * @param q The search string
	 * @param t Ther ES type (collection, title-print, title-digital)
	 * @param from From parameter for Elasticsearch query
	 * @param size Size parameter for Elasitcsearch query
	 * @param format The response format ('json' for JSON, else HTML)
	 * @return Result of search as ok() or badRequest()
	 */
	public Result search(String q, String t, int from, int size, String format) {
		try {
			String searchResponse = search(q, t, from, size);
			return format != null && format.equals("json")
					? ok(Json.parse(searchResponse))
					: ok(views.html.search.render(q, searchResponse, from, size));
		} catch (IllegalArgumentException x) {
			x.printStackTrace();
			return badRequest("Bad request: " + x.getMessage());
		}
	}

	/** Facet fields. */
	public static final String[] FACETS = { "type", "medium", "subject",
			"temporal", "spatial", "created", "isPartOf" };

	private String search(String q, String type, int from, int size) {
		client.admin().indices().refresh(new RefreshRequest()).actionGet();
		QueryBuilder simpleQuery = QueryBuilders.queryStringQuery(q);
		SearchRequestBuilder searchRequest = client.prepareSearch(indexName)
				.setSearchType(SearchType.QUERY_THEN_FETCH).setQuery(simpleQuery)
				.setFrom(from).setSize(size);
		if (type != null) {
			searchRequest = searchRequest.setTypes(type);
		}
		searchRequest = withAggregations(searchRequest, FACETS);
		SearchResponse searchResponse = searchRequest.execute().actionGet();
		return size == 1
				? Json.prettyPrint(
						Json.parse(searchResponse.getHits().getAt(0).getSourceAsString()))
				: searchResponse.toString();
	}

	private static SearchRequestBuilder withAggregations(
			final SearchRequestBuilder searchRequest, String... fields) {
		Arrays.asList(fields).forEach(field -> {
			searchRequest.addAggregation(AggregationBuilders.terms(field)
					.field(field + ".raw").size(Integer.MAX_VALUE));
		});
		return searchRequest;
	}

	/**
	 * Method to get a specific document by id, either rendered within a html view
	 * or as plain Json.
	 * 
	 * @param id id of the document that is supposed to be returned
	 * @param format specifies in which format the content shall be returned
	 * @return either render a view, return the content as Json or report
	 *         "not found"
	 */
	public Result getResource(String id, String format) {
		response().setHeader("Access-Control-Allow-Origin", "*");

		String normalizedId = id.substring(1, id.length());

		String printId =
				"http://digitalisiertedrucke.de/resources/P" + normalizedId;
		GetResponse resultPrint = client
				.prepareGet(indexName, "title-print", printId).execute().actionGet();
		JsonNode resultPrintAsJson = Json.parse(resultPrint.getSourceAsString());

		String digitalId =
				"http://digitalisiertedrucke.de/resources/D" + normalizedId;
		GetResponse resultDigital =
				client.prepareGet(indexName, "title-digital", digitalId).execute()
						.actionGet();

		JsonNode resultDigitalAsJson =
				Json.parse(resultDigital.getSourceAsString());

		// JsonNode collection = resultDigitalAsJson.get("isPartOf");

		return ok(views.html.resource.render(normalizedId, resultPrintAsJson,
				resultDigitalAsJson));
	}

	public Result getCollection(String id, String format) {
		response().setHeader("Access-Control-Allow-Origin", "*");
		String normalizedId = "http://digitalisiertedrucke.de/collections/" + id;
		GetResponse resultCollection =
				client.prepareGet(indexName, "collection", normalizedId).execute()
						.actionGet();
		JsonNode resultCollectionAsJson =
				Json.parse(resultCollection.getSourceAsString());

		return ok(views.html.collection.render(id, resultCollectionAsJson));

	}

	private static String prettyJsonOk(JsonNode jsonNode) {
		try {
			return new ObjectMapper().writerWithDefaultPrettyPrinter()
					.writeValueAsString(jsonNode);
		} catch (JsonProcessingException x) {
			x.printStackTrace();
			return null;
		}
	}

}
