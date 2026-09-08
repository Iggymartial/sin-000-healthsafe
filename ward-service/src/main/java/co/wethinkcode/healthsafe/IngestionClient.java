package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Talks to ingestion-service over plain synchronous HTTP (this is
 * Stage 2 - synchronous REST calls; Stage 3 replaces the equivalent
 * staffing-service -> ward-service call with a topic instead).
 *
 * Deliberately NOT cached: this service should stay independently
 * runnable even if ingestion-service restarts (a rubric requirement),
 * so every /wards request here fetches fresh data rather than risking
 * a stale in-memory copy silently going out of sync.
 */
public class IngestionClient {

    private static final String INGESTION_URL = "http://localhost:7030/wards";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public List<WardRecord> fetchWards() throws IngestionUnavailableException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(INGESTION_URL))
                .timeout(TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IngestionUnavailableException(
                        "ingestion-service responded with status " + response.statusCode());
            }
            return mapper.readValue(
                    response.body(),
                    mapper.getTypeFactory().constructCollectionType(List.class, WardRecord.class)
            );
        } catch (IOException | InterruptedException e) {
            throw new IngestionUnavailableException(
                    "could not reach ingestion-service at " + INGESTION_URL, e);
        }
    }

    /**
     * A deliberate, catchable failure mode - ingestion-service being
     * down is an EXPECTED possibility for this service, not something
     * that should crash it or bubble up as a raw, unhandled exception.
     */
    public static class IngestionUnavailableException extends Exception {
        public IngestionUnavailableException(String message) {
            super(message);
        }

        public IngestionUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
