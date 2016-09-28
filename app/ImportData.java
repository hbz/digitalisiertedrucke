
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.culturegraph.mf.morph.Metamorph;
import org.culturegraph.mf.stream.converter.JsonEncoder;
import org.culturegraph.mf.stream.converter.JsonToElasticsearchBulk;
import org.culturegraph.mf.stream.reader.MarcXmlReader;
import org.culturegraph.mf.stream.sink.ObjectWriter;
import org.culturegraph.mf.stream.source.FileOpener;
import org.culturegraph.mf.util.FileCompression;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.jena.JenaRDFParser;
import com.github.jsonldjava.utils.JSONUtils;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import controllers.HomeController.TYPE;
import play.Logger;
import play.libs.Json;

/**
 * Run as Java application to transform the source bzipped MARC-XML data to JSON
 * and index it into Elasticsearch. Data and config are in the `conf/` dir.
 * 
 * @author Fabian Steeg (fsteeg)
 *
 */
public class ImportData {

	private static final Config CONFIG =
			ConfigFactory.parseFile(new File("conf/application.conf"));
	private static final String INDEX_NAME = CONFIG.getString("index.name");
	private static final Settings SETTINGS = Settings.settingsBuilder()
			.put("path.home", CONFIG.getString("index.location"))
			.put("http.port", CONFIG.getString("index.http_port"))
			.put("transport.tcp.port", CONFIG.getString("index.tcp_port")).build();
	private static final Node NODE =
			NodeBuilder.nodeBuilder().settings(SETTINGS).local(true).build().start();
	private static final Client CLIENT = NODE.client();

	/**
	 * @param args unused (data and conf in the `conf/` dir)
	 * @throws IOException if data can't be read
	 */
	public static void main(String[] args) throws IOException {
		createEmptyIndex("conf/index-settings.json");
		String inputFile = "conf/hbz_zvdd_resource_marc.xml.bz2";
		indexTurtle(CONFIG.getString("index.ddc"), TYPE.DDC.id);
		importData(inputFile, TYPE.TITLE_PRINT.id);
		importData(inputFile, TYPE.TITLE_DIGITAL.id);
		importData(inputFile, TYPE.COLLECTION.id);
		NODE.close();
		CLIENT.close();
	}

	private static void importData(String inputFile, String type)
			throws IOException {
		String destination = String.format("test/all-%s_out.jsonl", type);
		transform(inputFile, type, destination);
		Logger.info("Transformed type '{}' from '{}' to file '{}'", //
				type, inputFile, destination);
		indexData(destination, type);
		Logger.info("Indexed from '{}' to index '{}', type '{}'", //
				destination, INDEX_NAME, type);
	}

	private static void indexTurtle(String dataUrl, String type) {
		Logger.debug("Indexing from dataUrl: {}, type: {}, index: {}, client {}",
				dataUrl, type, INDEX_NAME, CLIENT);
		final BulkRequestBuilder bulkRequest = CLIENT.prepareBulk();
		try {
			List<String> jsonLd = toJsonLd(new URL(dataUrl));
			for (String concept : jsonLd) {
				String id = Json.parse(concept).findValue("@id").textValue();
				IndexRequestBuilder indexRequest =
						CLIENT.prepareIndex(INDEX_NAME, type, id).setSource(concept);
				bulkRequest.add(indexRequest);
			}
		} catch (MalformedURLException e) {
			Logger.error("Could not index data", e);
		}
		BulkResponse response = bulkRequest.execute().actionGet();
		if (response.hasFailures()) {
			Logger.info("Indexing response: {}", response.buildFailureMessage());
		}
	}

	/**
	 * @param turtleUrl The URL of the RDF in TURTLE format
	 * @return The input, converted to JSON-LD, or null
	 */
	public static List<String> toJsonLd(final URL turtleUrl) {
		final Model model = ModelFactory.createDefaultModel();
		try {
			model.read(turtleUrl.openStream(), null, "TURTLE");
			final JenaRDFParser parser = new JenaRDFParser();
			Object json = JsonLdProcessor.fromRDF(model, new JsonLdOptions(), parser);
			List<Object> list = JsonLdProcessor.expand(json);
			return list.subList(1, list.size()).stream()
					.map(o -> JSONUtils.toString(o).replace(".", "_"))
					.collect(Collectors.toList());
		} catch (JsonLdError | IOException e) {
			Logger.error("Could not convert to JSON-LD", e);
		}
		return null;
	}

	private static void transform(String inputFile, String type,
			String destination) {
		new File(destination).delete();
		String morphDef = String.format("morph_zvdd-%s2ld.xml", type);
		FileOpener opener = new FileOpener();
		opener.setCompression(FileCompression.BZIP2);
		JsonEncoder encoder = new JsonEncoder();
		encoder.setPrettyPrinting(true);
		JsonToElasticsearchBulk esBulk =
				new JsonToElasticsearchBulk("id", type, INDEX_NAME);
		opener//
				.setReceiver(new MarcXmlReader())//
				.setReceiver(new Metamorph(morphDef))//
				.setReceiver(encoder)//
				.setReceiver(esBulk)//
				.setReceiver(new ObjectWriter<>(destination));
		opener.process(inputFile);
		opener.closeStream();
	}

	static void createEmptyIndex(final String indexConfig) throws IOException {
		deleteIndex();
		CreateIndexRequestBuilder cirb =
				CLIENT.admin().indices().prepareCreate(INDEX_NAME);
		if (indexConfig != null) {
			String settingsMappings =
					Files.lines(Paths.get(indexConfig)).collect(Collectors.joining());
			cirb.setSource(settingsMappings);
		}
		cirb.execute().actionGet();
		CLIENT.admin().indices().refresh(new RefreshRequest()).actionGet();
	}

	static void indexData(final String path, String type) throws IOException {
		Logger.info("Index from path '{}', type '{}'", path, type);
		final BulkRequestBuilder bulkRequest = CLIENT.prepareBulk();
		try (BufferedReader br =
				new BufferedReader(new InputStreamReader(new FileInputStream(path),
						StandardCharsets.UTF_8))) {
			readData(bulkRequest, br, CLIENT, INDEX_NAME, type);
		}
		BulkResponse bulkResponse = bulkRequest.execute().actionGet();
		if (bulkResponse.hasFailures()) {
			// System.out.println(bulkResponse.buildFailureMessage());
			Arrays.asList(bulkResponse.getItems()).stream()
					.map(i -> i.getFailureMessage()).collect(Collectors.toSet())
					.forEach(f -> {
						Logger.error(f);
					});
		}
		Logger.info("Indexed {} items", bulkResponse.getItems().length);
		CLIENT.admin().indices().refresh(new RefreshRequest()).actionGet();
	}

	private static void readData(final BulkRequestBuilder bulkRequest,
			final BufferedReader br, final Client client, final String aIndex,
			final String type) throws IOException {
		final ObjectMapper mapper = new ObjectMapper();
		String line;
		int currentLine = 1;
		String docData = null;
		String docId = null;
		// First line: index with id, second line: source
		while ((line = br.readLine()) != null) {
			JsonNode rootNode = mapper.readValue(line, JsonNode.class);
			if (currentLine % 2 != 0) {
				JsonNode index = rootNode.get("index");
				docId = index.findValue("_id").asText();
			} else {
				Map<String, Object> oldMap = Json.fromJson(Json.parse(line), Map.class);
				Map<String, Object> newMap = new HashMap<>();
				for (Map.Entry<String, Object> e : oldMap.entrySet()) {
					newMap.put(e.getKey().replace('.', '_'), e.getValue());
				}
				docData = Json.stringify(Json.toJson(newMap));
				bulkRequest
						.add(client.prepareIndex(aIndex, type, docId).setSource(docData));
			}
			currentLine++;
		}
	}

	private static void deleteIndex() {
		CLIENT.admin().cluster().prepareHealth().setWaitForYellowStatus().execute()
				.actionGet();
		if (CLIENT.admin().indices().prepareExists(INDEX_NAME).execute().actionGet()
				.isExists()) {
			CLIENT.admin().indices().delete(new DeleteIndexRequest(INDEX_NAME))
					.actionGet();
		}
	}

}
