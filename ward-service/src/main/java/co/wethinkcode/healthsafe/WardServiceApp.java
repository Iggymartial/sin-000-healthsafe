package co.wethinkcode.healthsafe;

import java.util.List;
import java.util.Optional;

import co.wethinkcode.healthsafe.mq.StaffingEventSubscriber;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

public class WardServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7031);
        IngestionClient ingestionClient = new IngestionClient();

        // connectOrNoOp(): if the broker is down, this logs a warning and
        // returns a no-op subscriber instead of throwing - /wards and
        // /wards/{id} (fully working since Stage 2) must keep working
        // with or without MQ available.
        StaffingEventSubscriber staffingEvents = StaffingEventSubscriber.connectOrNoOp();

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

        // Stage 3: the latest staffing event received for this ward via
        // staffing-events-topic, with NO direct call to staffing-service
        // involved - this is what "react without polling" actually means
        // in practice: the data just shows up here because it was pushed,
        // not fetched.
        app.get("/wards/{id}/staffing-status", ctx -> {
            String wardId = ctx.pathParam("id").toUpperCase();
            Optional<StaffingEvent> event = staffingEvents.latestFor(wardId);

            if (event.isPresent()) {
                ctx.json(event.get());
            } else {
                ctx.status(HttpStatus.NOT_FOUND)
                        .json(new ErrorResponse(
                                "no staffing event received yet for ward '" + wardId + "'"));
            }
        });

        // MQ TODO (Stage 4): publishes to ActiveMQ queue MqConfig.QUEUE when
        // an equipment failure is detected on one of this service's wards.
    }
}
