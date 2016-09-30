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
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import play.Logger;
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

	/** Elasticsearch types. */
	@SuppressWarnings("javadoc")
	public static enum TYPE {
		COLLECTION("collection"), //
		TITLE_PRINT("title-print"), //
		TITLE_DIGITAL("title-digital"), //
		DDC("ddc");
		public String id;

		private TYPE(String t) {
			this.id = t;
		}
	}

	private static final String COLLECTIONS_PREFIX =
			"http://digitalisiertedrucke.de/collections/";

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
					: ok(views.html.search.render(q, searchResponse, from, size, this));
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
		GetResponse resultPrint =
				client.prepareGet(indexName, TYPE.TITLE_PRINT.id, printId).execute()
						.actionGet();
		JsonNode resultPrintAsJson = Json.parse(resultPrint.getSourceAsString());

		String digitalId =
				"http://digitalisiertedrucke.de/resources/D" + normalizedId;
		GetResponse resultDigital =
				client.prepareGet(indexName, TYPE.TITLE_DIGITAL.id, digitalId).execute()
						.actionGet();

		JsonNode resultDigitalAsJson =
				Json.parse(resultDigital.getSourceAsString());

		// JsonNode collection = resultDigitalAsJson.get("isPartOf");

		return ok(views.html.resource.render(normalizedId, resultPrintAsJson,
				resultDigitalAsJson, this));
	}

	public Result getCollection(String id, String format) {
		response().setHeader("Access-Control-Allow-Origin", "*");
		String normalizedId = COLLECTIONS_PREFIX + id;
		GetResponse resultCollection =
				client.prepareGet(indexName, TYPE.COLLECTION.id, normalizedId).execute()
						.actionGet();
		JsonNode resultCollectionAsJson =
				Json.parse(resultCollection.getSourceAsString());

		return ok(views.html.collection.render(id, resultCollectionAsJson, this));

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

	/**
	 * @param key The key too look up
	 * @return The label for the given key, or the key (if nothing was found)
	 */
	public String label(String key) {
		if (key.startsWith("http://dewey.info/class")) {
			return ddcLookup(key);
		}
		if (key.startsWith(COLLECTIONS_PREFIX)) {
			return collectionLookup(key);
		}
		String fieldLabel =
				(String) CONFIG.getObject("label.field").unwrapped().get(key);
		if (fieldLabel != null)
			return fieldLabel;
		String typeLabel =
				(String) CONFIG.getObject("label.type").unwrapped().get(key);
		if (typeLabel != null)
			return typeLabel;
		String mediumLabel =
				(String) CONFIG.getObject("label.medium").unwrapped().get(key);
		if (mediumLabel != null)
			return mediumLabel;
		return key;
	}

	private String ddcLookup(String key) {
		String lookupKey = key.replace(".", "_");
		try {
			String response =
					node.client().prepareGet(indexName, TYPE.DDC.id, lookupKey).get()
							.getSourceAsString();
			if (response != null) {
				String textValue = Json.parse(response)
						.findValue("http://www_w3_org/2004/02/skos/core#prefLabel")
						.findValue("@value").textValue();
				return textValue != null ? textValue : "";
			}
		} catch (Throwable t) {
			Logger.error("Could not get data, index: {} type: {}, id: {} ({}: {})",
					indexName, TYPE.DDC.id, lookupKey, t, t);
		}
		return key;
	}

	private String collectionLookup(String key) {
		try {
			String response =
					node.client().prepareGet(indexName, TYPE.COLLECTION.id, key).get()
							.getSourceAsString();
			if (response != null) {
				String textValue = Json.parse(response).findValue("title").textValue();
				return textValue != null ? textValue : "";
			}
		} catch (Throwable t) {
			Logger.error("Could not get data, index: {} type: {}, id: {} ({}: {})",
					indexName, TYPE.COLLECTION.id, key, t, t);
		}
		return key.replace(COLLECTIONS_PREFIX, "");
	}

}
