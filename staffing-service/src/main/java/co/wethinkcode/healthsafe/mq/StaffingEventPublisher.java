package co.wethinkcode.healthsafe.mq;

/**
 * Publishes staffing schedule updates to the staffing-events-topic.
 *
 * Deliberately an interface with a static factory (connectOrNoOp),
 * rather than a single concrete class instantiated directly. Reasoning:
 * the ActiveMQ broker being down should NOT stop staffing-service's
 * REST API from working - the REST endpoints were already fully built
 * and tested in Stage 2, and publishing to the topic is a secondary
 * side effect of computing a schedule, not the primary job of that
 * endpoint. If the broker can't be reached at startup, connectOrNoOp()
 * returns a no-op implementation instead of throwing, so the rest of
 * this service never has to null-check or special-case "is MQ up".
 */
public interface StaffingEventPublisher extends AutoCloseable {

    void publish(Object event);

    static StaffingEventPublisher connectOrNoOp() {
        try {
            return new JmsStaffingEventPublisher();
        } catch (Exception e) {
            System.err.println(
                    "Could not connect to ActiveMQ broker at " + MqConfig.BROKER_URL
                            + " - staffing events will NOT be published, but REST endpoints will "
                            + "still work normally: " + e.getMessage());
            return new StaffingEventPublisher() {
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
