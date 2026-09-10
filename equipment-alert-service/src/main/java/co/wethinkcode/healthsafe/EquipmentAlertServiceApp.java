package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.EquipmentFailureConsumer;
import io.javalin.Javalin;

public class EquipmentAlertServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7034);

        // connectOrNoOp(): if the broker is down, this logs a warning and
        // falls back to a no-op instead of throwing - /health and /alerts
        // must keep working (returning an empty list) with or without MQ
        // available.
        EquipmentFailureConsumer alerts = EquipmentFailureConsumer.connectOrNoOp();

        app.get("/health", ctx -> ctx.result("OK"));

        // Every equipment failure alert received so far, oldest first -
        // makes the queue's guaranteed delivery independently verifiable
        // with a plain curl call, the same way every other async flow in
        // this project has been checked, not just by reading log lines.
        app.get("/alerts", ctx -> ctx.json(alerts.allAlerts()));
    }
}
    