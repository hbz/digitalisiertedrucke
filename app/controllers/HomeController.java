package controllers;

import java.util.concurrent.CompletableFuture;

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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import play.inject.ApplicationLifecycle;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.index;

/**
 * This controller contains an action to handle HTTP requests to the
 * application's home page.
 */
@Singleton
public class HomeController extends Controller {

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

	/**
	 * @param q The search string
	 * @param t Ther ES type (collection, title-print, title-digital)
	 * @param from From parameter for Elasticsearch query
	 * @param size Size parameter for Elasitcsearch query
	 * @param format The response format ('html' for HTML, else JSON)
	 * @return Result of search as ok() or badRequest()
	 */
	public Result search(String q, String t, int from, int size, String format) {
		try {
			// TODO: implement html format support (result list)
			return ok(Json.parse(search(q, t, from, size)));
		} catch (IllegalArgumentException x) {
			x.printStackTrace();
			return badRequest("Bad request: " + x.getMessage());
		}
	}

	private String search(String type) {
		return search("*", type, 0, 1);
	}

	private String search(String q, String type, int from, int size) {
		client.admin().indices().refresh(new RefreshRequest()).actionGet();
		QueryBuilder simpleQuery = QueryBuilders.queryStringQuery(q);
		SearchRequestBuilder searchRequest = client.prepareSearch(indexName)
				.setSearchType(SearchType.QUERY_THEN_FETCH).setQuery(simpleQuery)
				.setFrom(from).setSize(size);
		if (type != null) {
			searchRequest = searchRequest.setTypes(type);
		}
		SearchResponse searchResponse = searchRequest.execute().actionGet();
		return size == 1
				? Json.prettyPrint(
						Json.parse(searchResponse.getHits().getAt(0).getSourceAsString()))
				: searchResponse.toString();
	}

}
