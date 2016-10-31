
/* Copyright 2013 Fabian Steeg. Licensed under the Eclipse Public License 1.0 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.culturegraph.mf.stream.converter.JsonEncoder;
import org.culturegraph.mf.stream.converter.JsonToElasticsearchBulk;
import org.culturegraph.mf.stream.reader.MarcXmlReader;
import org.junit.Assert;
import org.junit.Test;

import controllers.HomeController.TYPE;
import util.AbstractIngestTests;

/**
 * Ingest the ZVDD MARC-XML export.
 * 
 * Run as JUnit test to print some stats, transform the fields, and output
 * results as N-Triples and JSON for test data.
 * 
 * @author Fabian Steeg (fsteeg)
 */
@SuppressWarnings("javadoc")
public final class ZvddMarcIngestTest extends AbstractIngestTests {

	public ZvddMarcIngestTest() {
		super("test/zvdd-collections-test-set.xml", "morph_zvdd-collection2ld.xml",
				"zvdd_morph-stats.xml", new MarcXmlReader());
	}

	@Test
	public void testJson() { // NOPMD asserts are done in the superclass
		JsonEncoder encodeJson = new JsonEncoder();
		JsonToElasticsearchBulk esBulk =
				new JsonToElasticsearchBulk("id", "type", "index");
		encodeJson.setPrettyPrinting(true);
		super.jsonLines("test/zvdd-title-digitalisation_test.jsonl",
				"test/zvdd-title-digitalisation_out.jsonl", encodeJson, esBulk);
	}

	@SuppressWarnings("static-method") // JUnit test
	@Test
	public void testMissingCollections() throws IOException {
		String destination = "test/test_missing-collection_out.jsonl";
		ImportData.importMissing("conf/missing_collections.json",
				TYPE.COLLECTION.id, destination);
		Path expected = Paths.get("test/test_missing-collection_test.jsonl");
		Assert.assertEquals(//
				new String(Files.readAllBytes(expected)),
				new String(Files.readAllBytes(Paths.get(destination))));
	}

	@Test
	public void testStatistics() throws IOException { // NOPMD
		super.stats("test/mapping.textile");

	}

	@Test
	public void testDot() { // NOPMD
		super.dot("zvdd-title-digitalisation_test.dot");
	}
}
