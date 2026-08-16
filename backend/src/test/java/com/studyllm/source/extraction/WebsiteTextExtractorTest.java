package com.studyllm.source.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebsiteTextExtractorTest {

  private final WebsiteTextExtractor extractor = new WebsiteTextExtractor();

  @Test
  void stripsBoilerplateAndKeepsReadableContent() throws IOException {
    String html =
        """
        <html>
          <head><script>trackPageView();</script><style>body{color:red}</style></head>
          <body>
            <nav>Home | About</nav>
            <header>Site Header</header>
            <article>
              <h1>Photosynthesis</h1>
              <p>Plants convert light energy into chemical energy.</p>
            </article>
            <footer>Copyright 2026</footer>
          </body>
        </html>
        """;

    List<ExtractedSection> sections = extractor.extract(new ByteArrayInputStream(html.getBytes()));

    assertThat(sections).hasSize(1);
    String text = sections.get(0).text();
    assertThat(text).contains("Photosynthesis", "Plants convert light energy into chemical energy.");
    assertThat(text).doesNotContain("trackPageView", "color:red", "Home | About", "Site Header", "Copyright 2026");
  }

  @Test
  void fallsBackToBodyTextWhenNoSemanticTagsArePresent() throws IOException {
    String html = "<html><body><div>Just some text in a div, no p or h tags.</div></body></html>";

    List<ExtractedSection> sections = extractor.extract(new ByteArrayInputStream(html.getBytes()));

    assertThat(sections).hasSize(1);
    assertThat(sections.get(0).text()).contains("Just some text in a div, no p or h tags.");
  }
}
