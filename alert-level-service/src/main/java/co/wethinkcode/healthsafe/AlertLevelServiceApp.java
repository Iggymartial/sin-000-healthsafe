package co.wethinkcode.healthsafe;

import java.util.concurrent.atomic.AtomicInteger;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

public class AlertLevelServiceApp {
    
    private static final int MIN_LEVEL = 0;
    private static final int MAX_LEVEL = 8;

    public static void main(String[] args) {
         Javalin app = Javalin.create().start(7032);

        // AtomicInteger, not a plain int: Javalin handles each incoming
        // request on its own thread by default, so reads and writes to
        // the current alert level need to be genuinely thread-safe, not
        // just "usually fine in practice".
        AtomicInteger currentLevel = new AtomicInteger(0);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/alert-level", ctx -> ctx.json(new AlertLevel(currentLevel.get())));

        // The root README's integration contract only shows GET being
        // called (by staffing-service). Adding POST here is a deliberate
        // addition beyond that stated contract: something has to be able
        // to CHANGE the Emergency Status, or "tracks the hospital
        // Emergency Status" (this service's own stated job, per its
        // README) would be impossible to actually demonstrate.
        app.post("/alert-level", ctx -> {
            AlertLevel requested = ctx.bodyAsClass(AlertLevel.class);

            if (requested.level() == null || requested.level() < MIN_LEVEL || requested.level() > MAX_LEVEL) {
                ctx.status(HttpStatus.BAD_REQUEST)
                        .json(new ErrorResponse(
                                "level must be an integer between " + MIN_LEVEL + " and " + MAX_LEVEL));
                return;
            }

            currentLevel.set(requested.level());
            ctx.json(new AlertLevel(currentLevel.get()));
        });
    }
}
