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
     * 失敗時は自動的に再接続を試みる
     */
    public void sendEquipmentStatus(EquipmentStatus status) throws JMSException {
        int maxRetries = 3;
        JMSException lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // 接続が初期化されていない場合は再接続
                if (session == null || producer == null) {
                    logger.warn("接続が初期化されていません。再接続を試みます... (試行 {}/{})", attempt, maxRetries);
                    reconnect();
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

                return; // 成功したら終了

            } catch (JMSException e) {
                lastException = e;
                logger.warn("メッセージ送信失敗 (試行 {}/{}): {}", attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        // 古い接続をクリーンアップ
                        closeQuietly();

                        // バックオフ（指数関数的に待機時間を増やす）
                        long backoffMs = 1000L * attempt;
                        logger.info("{}ms後に再接続を試みます...", backoffMs);
                        Thread.sleep(backoffMs);

                        // 再接続
                        reconnect();

                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("再接続待機中に中断されました", ie);
                        throw e;
                    } catch (Exception re) {
                        logger.error("再接続に失敗しました", re);
                    }
                }
            }
        }

        // すべてのリトライが失敗した場合
        logger.error("{}回の試行後もメッセージ送信に失敗しました", maxRetries);
        throw lastException;
    }

    /**
     * 再接続を試みる
     */
    private void reconnect() throws JMSException {
        logger.info("ActiveMQ Artemis再接続を試みます: {}", brokerUrl);
        closeQuietly();
        initialize();
        logger.info("ActiveMQ Artemis再接続成功");
    }

    /**
     * 例外を握りつぶしてクローズ（再接続時に使用）
     */
    private void closeQuietly() {
        try {
            if (producer != null) {
                producer.close();
                producer = null;
            }
        } catch (Exception e) {
            logger.debug("Producer クローズ時のエラー（無視）: {}", e.getMessage());
        }

        try {
            if (session != null) {
                session.close();
                session = null;
            }
        } catch (Exception e) {
            logger.debug("Session クローズ時のエラー（無視）: {}", e.getMessage());
        }

        try {
            if (connection != null) {
                connection.close();
                connection = null;
            }
        } catch (Exception e) {
            logger.debug("Connection クローズ時のエラー（無視）: {}", e.getMessage());
        }

        try {
            if (connectionFactory != null) {
                connectionFactory.close();
                connectionFactory = null;
            }
        } catch (Exception e) {
            logger.debug("ConnectionFactory クローズ時のエラー（無視）: {}", e.getMessage());
        }
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
