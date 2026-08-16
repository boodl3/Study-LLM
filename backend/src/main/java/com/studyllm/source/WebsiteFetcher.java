package com.studyllm.source;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Fetches a web page's raw HTML for use as a source, enforcing the same timeouts/size discipline
 * as the rest of the ingestion pipeline plus a private-network guard (a user-supplied URL fetched
 * server-side is a classic SSRF vector).
 */
@Component
public class WebsiteFetcher {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

  private final HttpClient httpClient =
      HttpClient.newBuilder()
          .connectTimeout(CONNECT_TIMEOUT)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  /** Fetches {@code url}'s HTML, rejecting private/loopback targets and bodies over {@code maxBytes}. */
  public byte[] fetch(String url, long maxBytes) {
    URI uri = parseHttpUri(url);
    requirePublicHost(uri);

    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("User-Agent", "Mozilla/5.0 (compatible; StudyLLM/1.0)")
            .header("Accept", "text/html,application/xhtml+xml")
            .GET()
            .build();

    HttpResponse<byte[]> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException e) {
      throw new IllegalArgumentException("Couldn't reach that URL: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalArgumentException("Fetching that URL was interrupted");
    }

    if (response.statusCode() / 100 != 2) {
      throw new IllegalArgumentException("That URL returned HTTP " + response.statusCode());
    }
    String contentType = response.headers().firstValue("Content-Type").orElse("");
    if (!contentType.isEmpty() && !contentType.toLowerCase().contains("html")) {
      throw new IllegalArgumentException("That URL isn't a web page (content-type: " + contentType + ")");
    }
    if (response.body().length > maxBytes) {
      throw new IllegalArgumentException("That page exceeds the 50MB limit");
    }
    return response.body();
  }

  private static URI parseHttpUri(String url) {
    URI uri;
    try {
      uri = new URI(url.trim());
    } catch (Exception e) {
      throw new IllegalArgumentException("Not a valid URL");
    }
    String scheme = uri.getScheme();
    if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
      throw new IllegalArgumentException("URL must start with http:// or https://");
    }
    if (uri.getHost() == null) {
      throw new IllegalArgumentException("Not a valid URL");
    }
    return uri;
  }

  private static void requirePublicHost(URI uri) {
    InetAddress address;
    try {
      address = InetAddress.getByName(uri.getHost());
    } catch (Exception e) {
      throw new IllegalArgumentException("Couldn't resolve that host");
    }
    if (address.isLoopbackAddress()
        || address.isAnyLocalAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()) {
      throw new IllegalArgumentException("That URL points to a private network address");
    }
  }
}
