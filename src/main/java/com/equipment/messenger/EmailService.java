package com.equipment.messenger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * メール通知サービスクラス
 * 接続失敗時にメール通知を送信する
 */
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // 同じエラーの通知を送信する最小間隔（ミリ秒）- デフォルト30分
    private static final long MIN_NOTIFICATION_INTERVAL_MS = 30 * 60 * 1000;

    private final Config config;
    private final Map<String, Long> lastNotificationTime = new HashMap<>();

    public EmailService(Config config) {
        this.config = config;
    }

    /**
     * Oracle接続失敗通知を送信
     */
    public void sendDatabaseConnectionFailureNotification(Exception exception) {
        if (!config.isMailNotificationEnabled()) {
            logger.debug("メール通知が無効です");
            return;
        }

        String errorKey = "database_connection_failure";
        if (!shouldSendNotification(errorKey)) {
            logger.info("最近通知を送信したため、データベース接続失敗通知をスキップします");
            return;
        }

        String subject = "[警告] データベース接続失敗 - EqpStatusMessenger";
        String body = buildDatabaseFailureMessage(exception);

        if (sendEmail(subject, body)) {
            updateLastNotificationTime(errorKey);
        }
    }

    /**
     * Artemis接続失敗通知を送信
     */
    public void sendArtemisConnectionFailureNotification(Exception exception) {
        if (!config.isMailNotificationEnabled()) {
            logger.debug("メール通知が無効です");
            return;
        }

        String errorKey = "artemis_connection_failure";
        if (!shouldSendNotification(errorKey)) {
            logger.info("最近通知を送信したため、Artemis接続失敗通知をスキップします");
            return;
        }

        String subject = "[警告] Artemis接続失敗 - EqpStatusMessenger";
        String body = buildArtemisFailureMessage(exception);

        if (sendEmail(subject, body)) {
            updateLastNotificationTime(errorKey);
        }
    }

    /**
     * 通知を送信すべきかチェック（重複通知を防ぐ）
     */
    private boolean shouldSendNotification(String errorKey) {
        Long lastTime = lastNotificationTime.get(errorKey);
        if (lastTime == null) {
            return true;
        }

        long currentTime = System.currentTimeMillis();
        return (currentTime - lastTime) >= MIN_NOTIFICATION_INTERVAL_MS;
    }

    /**
     * 最後の通知時刻を更新
     */
    private void updateLastNotificationTime(String errorKey) {
        lastNotificationTime.put(errorKey, System.currentTimeMillis());
    }

    /**
     * データベース接続失敗メッセージを作成
     */
    private String buildDatabaseFailureMessage(Exception exception) {
        StringBuilder sb = new StringBuilder();
        sb.append("データベース接続に失敗しました。\n\n");
        sb.append("発生時刻: ").append(DATE_FORMAT.format(new Date())).append("\n");
        sb.append("データベースURL: ").append(config.getDatabaseUrl()).append("\n");
        sb.append("ユーザー名: ").append(config.getDatabaseUsername()).append("\n\n");
        sb.append("エラー詳細:\n");
        sb.append(exception.getClass().getName()).append(": ").append(exception.getMessage()).append("\n");

        if (exception.getCause() != null) {
            sb.append("\n原因:\n");
            sb.append(exception.getCause().getClass().getName()).append(": ")
              .append(exception.getCause().getMessage()).append("\n");
        }

        sb.append("\n対応方法:\n");
        sb.append("1. データベースサーバーが起動しているか確認してください\n");
        sb.append("2. ネットワーク接続を確認してください\n");
        sb.append("3. 接続情報（URL、ユーザー名、パスワード）が正しいか確認してください\n");
        sb.append("4. データベースのリスナーが正常に動作しているか確認してください\n");

        return sb.toString();
    }

    /**
     * Artemis接続失敗メッセージを作成
     */
    private String buildArtemisFailureMessage(Exception exception) {
        StringBuilder sb = new StringBuilder();
        sb.append("ActiveMQ Artemis接続に失敗しました。\n\n");
        sb.append("発生時刻: ").append(DATE_FORMAT.format(new Date())).append("\n");
        sb.append("ブローカーURL: ").append(config.getArtemisUrl()).append("\n");
        sb.append("ユーザー名: ").append(config.getArtemisUsername()).append("\n");
        sb.append("キュー名: ").append(config.getArtemisQueue()).append("\n\n");
        sb.append("エラー詳細:\n");
        sb.append(exception.getClass().getName()).append(": ").append(exception.getMessage()).append("\n");

        if (exception.getCause() != null) {
            sb.append("\n原因:\n");
            sb.append(exception.getCause().getClass().getName()).append(": ")
              .append(exception.getCause().getMessage()).append("\n");
        }

        sb.append("\n対応方法:\n");
        sb.append("1. ActiveMQ Artemisサーバーが起動しているか確認してください\n");
        sb.append("2. ネットワーク接続を確認してください\n");
        sb.append("3. 接続情報（URL、ユーザー名、パスワード）が正しいか確認してください\n");
        sb.append("4. 指定されたキューが存在するか確認してください\n");

        return sb.toString();
    }

    /**
     * メールを送信
     */
    private boolean sendEmail(String subject, String body) {
        try {
            // SMTPサーバーの設定
            Properties props = new Properties();
            props.put("mail.smtp.host", config.getMailSmtpHost());
            props.put("mail.smtp.port", String.valueOf(config.getMailSmtpPort()));
            props.put("mail.smtp.auth", String.valueOf(config.isMailSmtpAuth()));
            props.put("mail.smtp.starttls.enable", String.valueOf(config.isMailSmtpStartTlsEnable()));

            // セッションの作成
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                        config.getMailUsername(),
                        config.getMailPassword()
                    );
                }
            });

            // メッセージの作成
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(config.getMailFrom()));

            // 複数の宛先をサポート（カンマ区切り）
            String[] recipients = config.getMailTo().split(",");
            InternetAddress[] addresses = new InternetAddress[recipients.length];
            for (int i = 0; i < recipients.length; i++) {
                addresses[i] = new InternetAddress(recipients[i].trim());
            }
            message.setRecipients(Message.RecipientType.TO, addresses);

            message.setSubject(subject);
            message.setText(body);
            message.setSentDate(new Date());

            // メール送信
            Transport.send(message);

            logger.info("メール通知を送信しました - 件名: {}", subject);
            return true;

        } catch (MessagingException e) {
            logger.error("メール送信に失敗しました", e);
            return false;
        }
    }

    /**
     * メール設定をテスト
     */
    public boolean testEmailConfiguration() {
        if (!config.isMailNotificationEnabled()) {
            logger.info("メール通知が無効です");
            return true;
        }

        try {
            String subject = "[テスト] EqpStatusMessenger メール通知テスト";
            String body = "これはメール通知のテストメッセージです。\n\n" +
                         "送信時刻: " + DATE_FORMAT.format(new Date()) + "\n\n" +
                         "このメッセージを受信できた場合、メール通知の設定は正しく動作しています。";

            return sendEmail(subject, body);
        } catch (Exception e) {
            logger.error("メール設定テストに失敗しました", e);
            return false;
        }
    }
}
