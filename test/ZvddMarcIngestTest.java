
/* Copyright 2013 Fabian Steeg. Licensed under the Eclipse Public License 1.0 */

import java.io.IOException;

import org.culturegraph.mf.morph.Metamorph;
import org.culturegraph.mf.stream.converter.JsonEncoder;
import org.culturegraph.mf.stream.converter.JsonToElasticsearchBulk;
import org.culturegraph.mf.stream.reader.MarcXmlReader;
import org.culturegraph.mf.stream.sink.ObjectWriter;
import org.culturegraph.mf.stream.source.FileOpener;
import org.culturegraph.mf.util.FileCompression;
import org.junit.Test;

import util.AbstractIngestTests;
import util.PipeEncodeTriples;

/**
 * Ingest the ZVDD MARC-XML export.
 * 
 * Run as JUnit test to print some stats, transform the fields, and output
 * results as N-Triples and JSON for test data. Run as Java application for full
 * transformation.
 * 
 * @author Fabian Steeg (fsteeg)
 */
@SuppressWarnings("javadoc")
public final class ZvddMarcIngestTest extends AbstractIngestTests {

	public ZvddMarcIngestTest() {
		super("test/zvdd-collections-test-set.xml", "morph_zvdd-collection2ld.xml",
				"zvdd_morph-stats.xml", new MarcXmlReader());
	}

	public static void main(String[] args) {
		String inputFile = "conf/hbz_zvdd_resource_marc.xml.bz2";
		transform(inputFile, "title-print");
		transform(inputFile, "title-digital");
		transform(inputFile, "collection");
	}

	private static void transform(String inputFile, String type) {
		String morphDef = String.format("morph_zvdd-%s2ld.xml", type);
		String destination = String.format("test/all-%s_out.jsonl", type);
		FileOpener opener = new FileOpener();
		opener.setCompression(FileCompression.BZIP2);
		JsonEncoder encoder = new JsonEncoder();
		encoder.setPrettyPrinting(true);
		JsonToElasticsearchBulk esBulk =
				new JsonToElasticsearchBulk("id", type, "digitalisiertedrucke");
		opener//
				.setReceiver(new MarcXmlReader())//
				.setReceiver(new Metamorph(morphDef))//
				.setReceiver(encoder)//
				.setReceiver(esBulk)//
				.setReceiver(new ObjectWriter<>(destination));
		opener.process(inputFile);
	}

	@Test
	public void testJson() { // NOPMD asserts are done in the superclass
		JsonEncoder encodeJson = new JsonEncoder();
		encodeJson.setPrettyPrinting(true);
		super.triples("test/zvdd-title-digitalisation_test.json",
				"test/zvdd-title-digitalisation_out.json", encodeJson);
	}

	@Test
	public void testTriples() { // NOPMD asserts are done in the superclass
		super.triples("test/zvdd-title-digitalisation_test.nt",
				"test/zvdd-title-digitalisation_out.nt", new PipeEncodeTriples());

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
