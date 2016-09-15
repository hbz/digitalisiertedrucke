package controllers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import play.inject.ApplicationLifecycle;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.index;

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

	/**
	 * An action that renders an HTML page with a welcome message. The
	 * configuration in the <code>routes</code> file means that this method will
	 * be called when the application receives a <code>GET</code> request with a
	 * path of <code>/</code>.
	 * 
	 * @return Result
	 */
	public Result index() {
		return ok(index.render(request().host().split(":")[0],
				CONFIG.getString("index.http_port"), search("collection"),
				search("title-print"), search("title-digital")));
	}

	private String search(String type) {
		client.admin().indices().refresh(new RefreshRequest()).actionGet();
		QueryBuilder simpleQuery = QueryBuilders.queryStringQuery("*");
		SearchRequestBuilder searchRequest = client.prepareSearch(indexName)
				.setTypes(type).setSearchType(SearchType.QUERY_THEN_FETCH)
				.setQuery(simpleQuery).setSize(1);
		SearchResponse searchResponse = searchRequest.execute().actionGet();
		return searchResponse.getHits().totalHits() == 0 ? searchResponse.toString()
				: Json.prettyPrint(
						Json.parse(searchResponse.getHits().getAt(0).getSourceAsString()));
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
	public CompletionStage<Result> get(String id, String format) {
		response().setHeader("Access-Control-Allow-Origin", "*");
		String server = "http://localhost:6011";
		String url = String.format("%s/%s/%s/%s/_source", server,
				"digitalisiertedrucke", "title-print", id);
		if (format != null && format.equals("html")) {
			return ws.url(url).get()
					.thenApplyAsync(response -> response.getStatus() == OK
							? ok(views.html.details.render(id, response.asJson()))
							: notFound("Not found: " + id));
		}
		return ws.url(url).get()
				.thenApplyAsync(response -> response.getStatus() == OK
						? ok(prettyJsonOk(response.asJson()))
						: notFound("Not found: " + id));
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
