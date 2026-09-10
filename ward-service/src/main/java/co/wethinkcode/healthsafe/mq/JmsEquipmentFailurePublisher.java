package co.wethinkcode.healthsafe.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;

/**
 * Publishes to a QUEUE, not a topic - session.createQueue(), not
 * createTopic(). A queue is point-to-point: exactly one consumer
 * receives each message, versus a topic where every subscriber gets
 * every message.
 *
 * Explicitly sets PERSISTENT delivery mode. ActiveMQ actually defaults
 * to persistent already, but stating it explicitly makes the intent
 * visible in the code itself rather than relying on a default someone
 * reading this later would have to go look up - this queue exists
 * specifically FOR guaranteed delivery, so the code should say so.
 */
class JmsEquipmentFailurePublisher implements EquipmentFailurePublisher {

    private final Connection connection;
    private final Session session;
    private final MessageProducer producer;
    private final ObjectMapper mapper = new ObjectMapper();

    JmsEquipmentFailurePublisher() throws JMSException {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        connection = factory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(MqConfig.QUEUE);
        producer = session.createProducer(queue);
        producer.setDeliveryMode(DeliveryMode.PERSISTENT);
    }

    @Override
    public void publish(Object event) {
        try {
            String json = mapper.writeValueAsString(event);
            TextMessage message = session.createTextMessage(json);
            producer.send(message);
        } catch (Exception e) {
            System.err.println("Failed to publish equipment failure event: " + e.getMessage());
        }
    }

    @Override
    public void close() throws JMSException {
        connection.close();
    }
}
