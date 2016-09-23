import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import play.twirl.api.Content;
import play.twirl.api.Html;

/**
 *
 * Simple (JUnit) tests that can call all parts of a play app. If you are
 * interested in mocking a whole application, see the wiki for more details.
 *
 */
@SuppressWarnings("javadoc")
public class ApplicationTest {

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertEquals(2, a);
	}

	@Test
	public void renderTemplate() {
		Content html =
				views.html.main.render("Your new application is ready.", new Html(""));
		assertEquals("text/html", html.contentType());
		assertTrue(html.body().contains("Your new application is ready."));
	}

}
