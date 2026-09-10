package co.wethinkcode.healthsafe.mq;

import co.wethinkcode.healthsafe.EquipmentFailureEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Consumes from a QUEUE, not a topic - session.createQueue(), not
 * createTopic(). Point-to-point: this message goes to exactly one
 * consumer, not every subscriber.
 *
 * Uses Session.CLIENT_ACKNOWLEDGE, deliberately different from Stage
 * 3's AUTO_ACKNOWLEDGE on the topic subscriber. With auto-acknowledge,
 * a message counts as delivered the instant it arrives, even if
 * processing then fails. With client-acknowledge, message.acknowledge()
 * is only called AFTER the alert has been successfully processed and
 * stored - if this service crashed partway through, the unacknowledged
 * message stays on the broker and gets redelivered on reconnect. This
 * is what "guaranteed delivery" actually means mechanically, not just
 * a label attached to a queue.
 *
 * CopyOnWriteArrayList, not a plain ArrayList: onMessage() runs on the
 * JMS listener thread, while allAlerts() is read from Javalin's HTTP
 * threads - this needs to be safe for concurrent reads while a write
 * might be happening, which CopyOnWriteArrayList is built for
 * (optimised for a read-heavy, write-rare pattern, which matches this
 * use case well: alerts are read often, added occasionally).
 */
class JmsEquipmentFailureConsumer implements EquipmentFailureConsumer, MessageListener {

    private final Connection connection;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<EquipmentFailureEvent> alerts = new CopyOnWriteArrayList<>();

    JmsEquipmentFailureConsumer() throws JMSException {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Queue queue = session.createQueue(MqConfig.QUEUE);
        MessageConsumer consumer = session.createConsumer(queue);
        consumer.setMessageListener(this);
    }

    @Override
    public void onMessage(Message message) {
        try {
            String json = ((TextMessage) message).getText();
            EquipmentFailureEvent event = mapper.readValue(json, EquipmentFailureEvent.class);
            alerts.add(event);

            System.out.println("[equipment-failure-queue] ALERT received for ward " + event.wardId()
                    + ": " + event.equipment() + " - " + event.issue());

            // Only acknowledged AFTER successfully parsing and storing the
            // alert. If an exception had been thrown above, this line is
            // never reached, and the message remains unacknowledged on
            // the broker for redelivery - that's the actual guarantee.
            message.acknowledge();
        } catch (Exception e) {
            System.err.println("Failed to process equipment failure alert, leaving it "
                    + "unacknowledged for redelivery: " + e.getMessage());
        }
    }

    @Override
    public List<EquipmentFailureEvent> allAlerts() {
        return List.copyOf(alerts);
    }
}
