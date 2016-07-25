
/* Copyright 2013 Fabian Steeg. Licensed under the Eclipse Public License 1.0 */

import java.io.IOException;

import org.culturegraph.mf.stream.converter.JsonEncoder;
import org.culturegraph.mf.stream.reader.MarcXmlReader;
import org.junit.Test;

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
		encodeJson.setPrettyPrinting(true);
		super.triples("test/zvdd-title-digitalisation_test.json",
				"test/zvdd-title-digitalisation_out.json", encodeJson);
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
