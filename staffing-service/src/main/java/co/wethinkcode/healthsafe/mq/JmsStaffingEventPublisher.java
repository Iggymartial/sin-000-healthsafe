package co.wethinkcode.healthsafe.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;

/**
 * Real ActiveMQ-backed publisher. Connection, session, and producer are
 * created ONCE when this class is constructed and reused for every
 * publish() call - opening a fresh JMS connection per HTTP request
 * would be needless overhead on every single call to /schedule/{wardId}.
 *
 * Uses javax.jms, not jakarta.jms: confirmed this project's pom.xml
 * depends on plain `activemq-client` (not `activemq-client-jakarta`),
 * which uses the javax namespace - checked this before writing any
 * code, since guessing wrong here would mean nothing compiles.
 */
class JmsStaffingEventPublisher implements StaffingEventPublisher {

    private final Connection connection;
    private final Session session;
    private final MessageProducer producer;
    private final ObjectMapper mapper = new ObjectMapper();

    JmsStaffingEventPublisher() throws JMSException {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        connection = factory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(MqConfig.TOPIC);
        producer = session.createProducer(topic);
    }

    @Override
    public void publish(Object event) {
        try {
            String json = mapper.writeValueAsString(event);
            TextMessage message = session.createTextMessage(json);
            producer.send(message);
        } catch (Exception e) {
            // A failure here means the broadcast didn't go out - logged,
            // not thrown. The REST response to the caller has already
            // been computed correctly and should not fail just because
            // this secondary side effect couldn't complete.
            System.err.println("Failed to publish staffing event: " + e.getMessage());
        }
    }

    @Override
    public void close() throws JMSException {
        connection.close();
    }
}
