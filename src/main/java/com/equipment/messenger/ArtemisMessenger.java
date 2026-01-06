package com.equipment.messenger;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;

/**
 * ActiveMQ Artemisメッセージング クラス
 */
public class ArtemisMessenger implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ArtemisMessenger.class);

    private final String brokerUrl;
    private final String username;
    private final String password;
    private final String queueName;

    private ActiveMQConnectionFactory connectionFactory;
    private Connection connection;
    private Session session;
    private MessageProducer producer;
    private Queue queue;

    public ArtemisMessenger(String brokerUrl, String username, String password, String queueName) {
        this.brokerUrl = brokerUrl;
        this.username = username;
        this.password = password;
        this.queueName = queueName;
    }

    /**
     * ActiveMQ Artemisへの接続を初期化
     */
    public void initialize() throws JMSException {
        logger.info("ActiveMQ Artemis接続を初期化: {}", brokerUrl);

        connectionFactory = new ActiveMQConnectionFactory(brokerUrl, username, password);
        connection = connectionFactory.createConnection();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        queue = session.createQueue(queueName);
        producer = session.createProducer(queue);

        connection.start();
        logger.info("ActiveMQ Artemis接続成功");
    }

    /**
     * 装置ステータスをE10StateChangeキューに送信
     */
    public void sendEquipmentStatus(EquipmentStatus status) throws JMSException {
        if (session == null || producer == null) {
            throw new IllegalStateException("Messenger is not initialized");
        }

        // テキストメッセージを作成
        String messageText = "currentState=\"" + status.getStatus() + "\"";
        TextMessage message = session.createTextMessage(messageText);

        // JMSReplyToヘッダーにEquipmentIdを設定
        String replyToText = "EquipmentId=" + status.getEqpId();
        Queue replyToQueue = session.createQueue(replyToText);
        message.setJMSReplyTo(replyToQueue);

        // メッセージを送信
        producer.send(message);

        logger.info("メッセージ送信 - EQPID: {}, STATUS: {}, TIME: {}",
                status.getEqpId(),
                status.getStatus(),
                status.getTimestampTime());
    }

    /**
     * 接続をテスト
     */
    public boolean testConnection() {
        try {
            initialize();
            logger.info("ActiveMQ Artemis接続テスト成功");
            return true;
        } catch (JMSException e) {
            logger.error("ActiveMQ Artemis接続テスト失敗", e);
            return false;
        }
    }

    /**
     * リソースをクローズ
     */
    @Override
    public void close() {
        try {
            if (producer != null) {
                producer.close();
            }
            if (session != null) {
                session.close();
            }
            if (connection != null) {
                connection.close();
            }
            if (connectionFactory != null) {
                connectionFactory.close();
            }
            logger.info("ActiveMQ Artemis接続をクローズしました");
        } catch (JMSException e) {
            logger.error("リソースのクローズに失敗", e);
        }
    }
}
