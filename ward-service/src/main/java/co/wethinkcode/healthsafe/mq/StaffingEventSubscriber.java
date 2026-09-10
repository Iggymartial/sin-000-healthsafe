package co.wethinkcode.healthsafe.mq;

import java.util.Optional;

import co.wethinkcode.healthsafe.StaffingEvent;

/**
 * Reads staffing schedule updates received from staffing-events-topic.
 *
 * Same graceful-degradation pattern as StaffingEventPublisher on the
 * producer side: if the broker can't be reached at startup, this
 * service should still serve /wards and /wards/{id} normally (those
 * were already fully working in Stage 2) - it just won't have any
 * staffing events to report until the broker becomes reachable.
 */
public interface StaffingEventSubscriber {

    Optional<StaffingEvent> latestFor(String wardId);

    static StaffingEventSubscriber connectOrNoOp() {
        try {
            return new JmsStaffingEventSubscriber();
        } catch (Exception e) {
            System.err.println(
                    "Could not connect to ActiveMQ broker at " + MqConfig.BROKER_URL
                            + " - this service will not receive staffing events, but its REST "
                            + "endpoints will still work normally: " + e.getMessage());
            return wardId -> Optional.empty(); // no-op: broker unavailable, already logged
        }
    }
}
