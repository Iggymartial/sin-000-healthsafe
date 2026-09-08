package co.wethinkcode.healthsafe;

import java.time.Instant;
import java.util.Optional;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

public class StaffingServiceApp {

    // Domain rule for THIS EXERCISE, not a real hospital staffing
    // standard: baseline 1 doctor on call, plus one additional doctor
    // for every 2 points the Emergency Status rises (0-8), plus 1 more
    // if the ward is a high-acuity department. An explicit, documented
    // assumption (see DECISIONS.md) - the README doesn't specify a
    // formula, so one had to be invented and its reasoning stated.
    private static final int BASE_DOCTORS = 1;

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7033);

        WardClient wardClient = new WardClient();
        AlertLevelClient alertLevelClient = new AlertLevelClient();

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/schedule/{wardId}", ctx -> {
            String wardId = ctx.pathParam("wardId");

            WardClient.Result wardResult = wardClient.fetchWard(wardId);

            // A sealed interface means these are the ONLY possible cases -
            // the compiler would flag it if a new Result type were ever
            // added here without being handled.
            if (wardResult instanceof WardClient.NotFound) {
                ctx.status(HttpStatus.NOT_FOUND)
                        .json(new ErrorResponse("no ward found with id '" + wardId + "'"));
                return;
            }
            if (wardResult instanceof WardClient.Unavailable unavailable) {
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .json(new ErrorResponse("ward-service unavailable: " + unavailable.reason()));
                return;
            }

            WardRecord ward = ((WardClient.Found) wardResult).ward();

            Optional<Integer> level = alertLevelClient.fetchCurrentLevel();
            if (level.isEmpty()) {
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .json(new ErrorResponse("alert-level-service unavailable"));
                return;
            }

            int alertLevel = level.get();
            int doctorsOnCall = computeDoctorsOnCall(alertLevel, ward.department());

            ctx.json(new ScheduleResponse(
                    ward.wardId(),
                    ward.department(),
                    alertLevel,
                    doctorsOnCall,
                    "Computed from current Emergency Status and ward department.",
                    Instant.now().toString()
            ));
        });
    }

    private static int computeDoctorsOnCall(int alertLevel, String department) {
        int doctors = BASE_DOCTORS + (int) Math.ceil(alertLevel / 2.0);

        boolean highAcuity = department != null
                && (department.equalsIgnoreCase("ICU") || department.equalsIgnoreCase("Cardiology"));
        if (highAcuity) {
            doctors += 1;
        }

        return doctors;
    }
}

// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
