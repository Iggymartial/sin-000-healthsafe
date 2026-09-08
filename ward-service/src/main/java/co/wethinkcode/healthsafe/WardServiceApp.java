package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

import java.util.List;
import java.util.Optional;

public class WardServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7031);
        IngestionClient ingestionClient = new IngestionClient();

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/wards", ctx -> {
            try {
                ctx.json(ingestionClient.fetchWards());
            } catch (IngestionClient.IngestionUnavailableException e) {
                // ingestion-service being down is a real, expected failure
                // mode - 503 tells the caller "try again later", which is
                // a different situation from "your request was wrong" (400)
                // or "that ward doesn't exist" (404).
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .json(new ErrorResponse("ingestion-service unavailable: " + e.getMessage()));
            }
        });

        app.get("/wards/{id}", ctx -> {
            // Normalised the same way ingestion-service normalises ward
            // IDs, so a caller can request "w-05" or "W-05" and get the
            // same answer either way.
            String wardId = ctx.pathParam("id").toUpperCase();

            try {
                List<WardRecord> wards = ingestionClient.fetchWards();
                Optional<WardRecord> match = wards.stream()
                        .filter(w -> w.wardId().equals(wardId))
                        .findFirst();

                if (match.isPresent()) {
                    ctx.json(match.get());
                } else {
                    ctx.status(HttpStatus.NOT_FOUND)
                            .json(new ErrorResponse("no ward found with id '" + wardId + "'"));
                }
            } catch (IngestionClient.IngestionUnavailableException e) {
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .json(new ErrorResponse("ingestion-service unavailable: " + e.getMessage()));
            }
        });
    }
}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
// MQ TODO: publishes to ActiveMQ queue MqConfig.QUEUE when it detects an equipment failure on one of its wards.
