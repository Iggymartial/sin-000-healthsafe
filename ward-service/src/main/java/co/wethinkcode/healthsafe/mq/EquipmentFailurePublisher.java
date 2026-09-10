package co.wethinkcode.healthsafe.mq;

/**
 * Publishes equipment failure alerts to equipment-failure-queue.
 *
 * Same graceful-degradation pattern as StaffingEventPublisher in
 * Stage 3: if the broker is unreachable, connectOrNoOp() logs a
 * warning and falls back to a no-op rather than stopping this
 * service's REST API from working.
 */
public interface EquipmentFailurePublisher extends AutoCloseable {

    void publish(Object event);

    static EquipmentFailurePublisher connectOrNoOp() {
        try {
            return new JmsEquipmentFailurePublisher();
        } catch (Exception e) {
            System.err.println(
                    "Could not connect to ActiveMQ broker at " + MqConfig.BROKER_URL
                            + " - equipment failure alerts will NOT be published, but REST "
                            + "endpoints will still work normally: " + e.getMessage());
            return new EquipmentFailurePublisher() {
                @Override
                public void publish(Object event) {
                    // no-op: broker unavailable, already logged at startup
                }

                @Override
                public void close() {
                    // nothing to close
                }
            };
        }
    }
}
