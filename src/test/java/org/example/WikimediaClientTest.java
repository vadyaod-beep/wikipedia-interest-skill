package org.example;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class WikimediaClientTest {
    private static final String URL = "https://example.test/pageviews";

    @Test
    void retriesEachTemporaryStatusWithoutWaitingInTests() throws Exception {
        for (int status : new int[]{429, 502, 503, 504}) {
            StubClient client = new StubClient(status, 200);

            assertEquals("{\"items\":[]}", client.get(URL));
            assertEquals(2, client.attempts());
        }
    }

    @Test
    void doesNotRetryOrdinaryClientErrorsOrUnlistedServerErrors() {
        for (int status : new int[]{400, 404, 500}) {
            StubClient client = new StubClient(status, 200);

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> client.get(URL));
            assertTrue(error.getMessage().contains("HTTP " + status));
            assertEquals(1, client.attempts());
        }
    }

    @Test
    void stopsAfterThreeAttemptsAndIncludesLastResponse() {
        StubClient client = new StubClient(504, 504, 504, 200);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.get(URL));

        assertEquals(3, client.attempts());
        assertTrue(error.getMessage().contains("HTTP 504"));
        assertTrue(error.getMessage().contains("after 3 attempt(s)"));
        assertTrue(error.getMessage().contains("upstream request timeout"));
    }

    private static class StubClient extends WikimediaClient {
        private final int[] statuses;
        private int attempts;

        StubClient(int... statuses) {
            super(0);
            this.statuses = statuses;
        }

        int attempts() {
            return attempts;
        }

        @Override
        HttpURLConnection openConnection(URI uri) throws IOException {
            return new StubConnection(uri, statuses[attempts++]);
        }
    }

    private static class StubConnection extends HttpURLConnection {
        private final int status;

        StubConnection(URI uri, int status) throws IOException {
            super(uri.toURL());
            this.status = status;
        }

        @Override
        public int getResponseCode() {
            return status;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream("{\"items\":[]}".getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream("upstream request timeout".getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }
    }
}
