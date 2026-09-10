package co.wethinkcode.healthsafe.mq;

import co.wethinkcode.healthsafe.StaffingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Subscribes to staffing-events-topic and keeps the most recent event
 * per ward in memory, so a REST caller can ask "what's the latest
 * staffing update for this ward" without ever polling staffing-service
 * directly - that's the actual point of this stage.
 *
 * Deliberately a NON-durable subscription: if ward-service is down
 * when an event is published, that event is simply missed, not queued
 * up for later delivery. That's the correct semantic for a broadcast
 * topic in this project's own design (see common/README.md) -
 * guaranteed delivery is what the QUEUE pattern in Stage 4 is
 * specifically for. Using a durable subscription here would blur that
 * distinction rather than demonstrate understanding of it.
 *
 * ConcurrentHashMap, not a plain HashMap: onMessage() runs on the JMS
 * client's own listener thread, completely separate from the threads
 * Javalin uses to handle HTTP requests - reads and writes to this map
 * happen from different threads concurrently, and need to be safe for
 * that, not just usually fine.
 */
class JmsStaffingEventSubscriber implements StaffingEventSubscriber, MessageListener {

    private final Connection connection;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, StaffingEvent> latestByWard = new ConcurrentHashMap<>();

    JmsStaffingEventSubscriber() throws JMSException {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(MqConfig.TOPIC);
        MessageConsumer consumer = session.createConsumer(topic);
        consumer.setMessageListener(this);
    }

    @Override
    public void onMessage(Message message) {
        try {
            String json = ((TextMessage) message).getText();
            StaffingEvent event = mapper.readValue(json, StaffingEvent.class);
            latestByWard.put(event.wardId(), event);

            // Logged clearly so this is verifiable just by watching this
            // service's terminal - matches common/README.md's own
            // suggested verification method (logs, or the broker's web
            // console) without needing any extra tooling.
            System.out.println("[staffing-events-topic] received update for ward " + event.wardId()
                    + ": alertLevel=" + event.alertLevel() + ", doctorsOnCall=" + event.doctorsOnCall());
        } catch (Exception e) {
            System.err.println("Failed to process staffing event: " + e.getMessage());
        }
    }

    @Override
    public Optional<StaffingEvent> latestFor(String wardId) {
        return Optional.ofNullable(latestByWard.get(wardId));
    }
}
