package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/** Talks to alert-level-service to read the current Emergency Status. */
public class AlertLevelClient {

    private static final String ALERT_LEVEL_URL = "http://localhost:7032/alert-level";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Empty means alert-level-service could not be reached, or returned
     * something unexpected - this class never throws for that; the
     * caller decides how to respond (see StaffingServiceApp).
     */
    public Optional<Integer> fetchCurrentLevel() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ALERT_LEVEL_URL))
                .timeout(TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            AlertLevel level = mapper.readValue(response.body(), AlertLevel.class);
            return Optional.ofNullable(level.level());
        } catch (IOException | InterruptedException e) {
            return Optional.empty();
        }
    }
}
