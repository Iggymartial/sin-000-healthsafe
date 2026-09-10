package co.wethinkcode.healthsafe;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import co.wethinkcode.healthsafe.mq.EquipmentFailurePublisher;
import co.wethinkcode.healthsafe.mq.StaffingEventSubscriber;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

public class WardServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7031);
        IngestionClient ingestionClient = new IngestionClient();

        // connectOrNoOp(): if the broker is down, these log a warning and
        // fall back to no-ops instead of throwing - /wards and /wards/{id}
        // (fully working since Stage 2) must keep working with or without
        // MQ available.
        StaffingEventSubscriber staffingEvents = StaffingEventSubscriber.connectOrNoOp();
        EquipmentFailurePublisher equipmentFailures = EquipmentFailurePublisher.connectOrNoOp();

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/wards", ctx -> {
            try {
                ctx.json(ingestionClient.fetchWards());
            } catch (IngestionClient.IngestionUnavailableException e) {
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .json(new ErrorResponse("ingestion-service unavailable: " + e.getMessage()));
            }
        });

        app.get("/wards/{id}", ctx -> {
            String wardId = ctx.pathParam("id").toUpperCase();

            try {
                Optional<WardRecord> match = findWard(ingestionClient, wardId);
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
        // involved - the data just shows up here because it was pushed.
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

        // Stage 4: report an equipment failure on a ward, which publishes
        // to equipment-failure-queue (guaranteed delivery to
        // equipment-alert-service, unlike the fire-and-forget topic above).
        // Not in the original integration contract - added because
        // nothing else in this project has a way to trigger a failure in
        // the first place, so there'd be no way to demonstrate the queue
        // working without it.
        app.post("/wards/{id}/equipment-failure", ctx -> {
            String wardId = ctx.pathParam("id").toUpperCase();

            try {
                Optional<WardRecord> match = findWard(ingestionClient, wardId);
                if (match.isEmpty()) {
                    ctx.status(HttpStatus.NOT_FOUND)
                            .json(new ErrorResponse("no ward found with id '" + wardId + "'"));
                    return;
                }
            } catch (IngestionClient.IngestionUnavailableException e) {
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .json(new ErrorResponse("ingestion-service unavailable: " + e.getMessage()));
                return;
            }

            EquipmentFailureRequest request = ctx.bodyAsClass(EquipmentFailureRequest.class);

            if (request.equipment() == null || request.equipment().isBlank()) {
                ctx.status(HttpStatus.BAD_REQUEST)
                        .json(new ErrorResponse("equipment is required"));
                return;
            }

            EquipmentFailureEvent event = new EquipmentFailureEvent(
                    wardId,
                    request.equipment(),
                    request.issue() == null ? "unspecified issue" : request.issue(),
                    Instant.now().toString()
            );

            equipmentFailures.publish(event);

            ctx.status(HttpStatus.ACCEPTED).json(event);
        });
    }

    private static Optional<WardRecord> findWard(IngestionClient ingestionClient, String wardId)
            throws IngestionClient.IngestionUnavailableException {
        List<WardRecord> wards = ingestionClient.fetchWards();
        return wards.stream().filter(w -> w.wardId().equals(wardId)).findFirst();
    }
}
