package co.wethinkcode.healthsafe.mq;

import co.wethinkcode.healthsafe.EquipmentFailureEvent;

import java.util.List;

/**
 * Consumes equipment failure alerts from equipment-failure-queue.
 *
 * Returns a LIST of every alert received, not just the latest one -
 * unlike Stage 3's staffing status (a live snapshot, where "latest"
 * genuinely is all that matters), each equipment failure is a
 * discrete event that needs to be individually accounted for. Two
 * separate failures on the same ward are two separate facts, not one
 * overwriting the other.
 */
public interface EquipmentFailureConsumer {

    List<EquipmentFailureEvent> allAlerts();

    static EquipmentFailureConsumer connectOrNoOp() {
        try {
            return new JmsEquipmentFailureConsumer();
        } catch (Exception e) {
            System.err.println(
                    "Could not connect to ActiveMQ broker at " + MqConfig.BROKER_URL
                            + " - this service will not receive equipment failure alerts, but "
                            + "its REST endpoints will still work normally: " + e.getMessage());
            return List::of; // no-op: broker unavailable, already logged
        }
    }
}
