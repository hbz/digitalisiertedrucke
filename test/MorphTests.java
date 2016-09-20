
/* Copyright 2013 Fabian Steeg. Licensed under the Eclipse Public License 1.0 */

import org.culturegraph.mf.test.MetamorphTestSuite;
import org.culturegraph.mf.test.MetamorphTestSuite.TestDefinitions;
import org.junit.runner.RunWith;

/**
 * @author Fabian Steeg
 */
@RunWith(MetamorphTestSuite.class)
@TestDefinitions({ "TransformationZvdd-title-print.xml",
		"TransformationZvdd-title-digital.xml",
		"TransformationZvdd-collection.xml" })
public final class MorphTests {
	/* Suite class, groups tests via annotation above */
}
