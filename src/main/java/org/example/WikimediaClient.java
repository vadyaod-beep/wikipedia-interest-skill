package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WikimediaClient {

    private static final String USER_AGENT = System.getenv().getOrDefault(
            "WIKIMEDIA_USER_AGENT", "wikipedia-interest-skill/1.0 (Java pageview analysis CLI)");
    private static final int MAX_ATTEMPTS = 3;
    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 502, 503, 504);

    private final ObjectMapper objectMapper;
    private final long initialRetryDelayMillis;

    public WikimediaClient() {
        this(1_000);
    }

    WikimediaClient(long initialRetryDelayMillis) {
        if (initialRetryDelayMillis < 0) {
            throw new IllegalArgumentException("Retry delay must not be negative");
        }
        this.objectMapper = new ObjectMapper();
        this.initialRetryDelayMillis = initialRetryDelayMillis;
    }

    /** Resolve the title of the same Wikipedia article in each requested language. */
    public Map<String, String> resolveArticleTitles(
            String sourceLanguage, String article, Set<String> languages) throws Exception {
        Map<String, String> titles = new LinkedHashMap<>();
        String continuation = null;
        String genericContinuation = null;

        do {
            String url = "https://" + sourceLanguage + ".wikipedia.org/w/api.php"
                    + "?action=query&prop=langlinks&format=json&formatversion=2&redirects=1&lllimit=max"
                    + "&titles=" + encode(article)
                    + (continuation == null ? "" : "&continue=" + encode(genericContinuation)
                    + "&llcontinue=" + encode(continuation));
            JsonNode root = objectMapper.readTree(get(url));
            JsonNode pages = root.path("query").path("pages");
            if (!pages.isArray() || pages.isEmpty() || pages.get(0).path("missing").asBoolean()) {
                throw new IllegalArgumentException("Article not found on " + sourceLanguage
                        + ".wikipedia.org: " + article);
            }
            JsonNode page = pages.get(0);
            if (languages.contains(sourceLanguage)) {
                titles.put(sourceLanguage, page.path("title").asText(article));
            }
            for (JsonNode link : page.path("langlinks")) {
                String language = link.path("lang").asText();
                if (languages.contains(language)) {
                    titles.put(language, link.path("title").asText());
                }
            }
            continuation = root.path("continue").path("llcontinue").asText(null);
            genericContinuation = root.path("continue").path("continue").asText(null);
        } while (continuation != null && titles.size() < languages.size());

        if (titles.size() != languages.size()) {
            Set<String> missing = new java.util.LinkedHashSet<>(languages);
            missing.removeAll(titles.keySet());
            throw new IllegalArgumentException("No linked Wikipedia article for language(s): " + missing
                    + ". Choose another source article or language set.");
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        for (String language : languages) {
            ordered.put(language, titles.get(language));
        }
        return ordered;
    }

    public List<PageView> getPageViews(
            String language,
            String article,
            String startDate,
            String endDate
    ) throws Exception {

        String url =
                "https://wikimedia.org/api/rest_v1/metrics/pageviews/" +
                        "per-article/" +
                        language + ".wikipedia.org/" +
                        "all-access/all-agents/" +
                        encode(article) + "/" +
                        "daily/" +
                        startDate + "/" +
                        endDate;

        PageViewResponse pageViewResponse =
                objectMapper.readValue(
                        get(url),
                        PageViewResponse.class
                );
        return pageViewResponse.getItems() == null ? List.of() : pageViewResponse.getItems();
    }

    String get(String url) throws Exception {
        URI uri = URI.create(url);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            HttpURLConnection connection = openConnection(uri);
            int status;
            String details;
            try {
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(30_000);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Accept", "application/json");
                status = connection.getResponseCode();
                if (status == 200) {
                    try (InputStream body = connection.getInputStream()) {
                        return new String(body.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
                try (InputStream error = connection.getErrorStream()) {
                    details = error == null ? ""
                            : new String(error.readAllBytes(), StandardCharsets.UTF_8);
                }
            } finally {
                connection.disconnect();
            }
            if (!RETRYABLE_STATUSES.contains(status) || attempt == MAX_ATTEMPTS) {
                throw new IllegalStateException("Wikimedia API returned HTTP " + status
                        + " for " + uri.getHost() + " after " + attempt + " attempt(s). "
                        + details);
            }
            waitBeforeRetry(attempt);
        }
        throw new IllegalStateException("Unreachable retry state");
    }

    HttpURLConnection openConnection(URI uri) throws IOException {
        return (HttpURLConnection) uri.toURL().openConnection();
    }

    private void waitBeforeRetry(int failedAttempt) throws IOException {
        try {
            Thread.sleep(initialRetryDelayMillis * (1L << (failedAttempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting to retry Wikimedia API", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
