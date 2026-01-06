package com.equipment.messenger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

/**
 * 装置ステータスメッセンジャー メインクラス
 * 1分ごとに装置ステータスをOracleから取得しActiveMQ Artemisに送信
 */
public class EqpStatusMessenger {
    private static final Logger logger = LoggerFactory.getLogger(EqpStatusMessenger.class);

    private final Config config;
    private final DatabaseManager dbManager;
    private final ArtemisMessenger artemisMessenger;
    private volatile boolean running = true;

    public EqpStatusMessenger(Config config) {
        this.config = config;

        this.dbManager = new DatabaseManager(
                config.getDatabaseUrl(),
                config.getDatabaseUsername(),
                config.getDatabasePassword(),
                config.getEquipmentTableName()
        );

        this.artemisMessenger = new ArtemisMessenger(
                config.getArtemisUrl(),
                config.getArtemisUsername(),
                config.getArtemisPassword(),
                config.getArtemisQueue()
        );
    }

    /**
     * アプリケーションを初期化
     */
    public void initialize() throws Exception {
        logger.info("===== EqpStatusMessenger 初期化開始 =====");

        // データベース接続テスト
        if (!dbManager.testConnection()) {
            throw new Exception("データベース接続に失敗しました");
        }

        // ActiveMQ Artemis接続初期化
        artemisMessenger.initialize();

        logger.info("===== 初期化完了 =====");
    }

    /**
     * メインループを実行
     */
    public void run() {
        logger.info("===== EqpStatusMessenger 実行開始 =====");
        logger.info("処理間隔: {}秒", config.getIntervalSeconds());

        // シャットダウンフックを登録
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("シャットダウンシグナルを受信しました");
            running = false;
        }));

        Date lastTimestamp = null;

        while (running) {
            try {
                // RTI_TIMESTAMPテーブルからタイムスタンプを取得（初回は初期化）
                if (lastTimestamp == null) {
                    lastTimestamp = dbManager.getOrInitializeTimestamp();
                }

                // 前回のタイムスタンプ以降に更新された装置ステータスを取得
                List<EquipmentStatus> statusList = dbManager.getUpdatedEquipmentStatus(lastTimestamp);

                if (statusList.isEmpty()) {
                    logger.debug("更新された装置ステータスはありません");
                } else {
                    logger.info("{}件の装置ステータスを処理します", statusList.size());

                    // 最新のタイムスタンプを記録
                    Date maxTimestamp = lastTimestamp;

                    // 各装置ステータスをActiveMQ Artemisに送信
                    for (EquipmentStatus status : statusList) {
                        artemisMessenger.sendEquipmentStatus(status);

                        // 最新のタイムスタンプを更新
                        if (status.getTimestampTime().after(maxTimestamp)) {
                            maxTimestamp = status.getTimestampTime();
                        }
                    }

                    // RTI_TIMESTAMPテーブルを更新
                    dbManager.updateTimestamp(maxTimestamp);
                    lastTimestamp = maxTimestamp;

                    logger.info("処理完了 - 次回チェックタイムスタンプ: {}", lastTimestamp);
                }

                // 指定秒数スリープ
                Thread.sleep(config.getIntervalSeconds() * 1000L);

            } catch (SQLException e) {
                logger.error("データベースエラーが発生しました", e);
                try {
                    Thread.sleep(config.getIntervalSeconds() * 1000L);
                } catch (InterruptedException ie) {
                    logger.warn("スリープが中断されました", ie);
                    running = false;
                }
            } catch (JMSException e) {
                logger.error("メッセージング エラーが発生しました", e);
                try {
                    Thread.sleep(config.getIntervalSeconds() * 1000L);
                } catch (InterruptedException ie) {
                    logger.warn("スリープが中断されました", ie);
                    running = false;
                }
            } catch (InterruptedException e) {
                logger.warn("スリープが中断されました", e);
                running = false;
            } catch (Exception e) {
                logger.error("予期しないエラーが発生しました", e);
                try {
                    Thread.sleep(config.getIntervalSeconds() * 1000L);
                } catch (InterruptedException ie) {
                    logger.warn("スリープが中断されました", ie);
                    running = false;
                }
            }
        }

        logger.info("===== EqpStatusMessenger 終了 =====");
    }

    /**
     * リソースをクリーンアップ
     */
    public void shutdown() {
        logger.info("リソースをクリーンアップしています...");
        artemisMessenger.close();
    }

    /**
     * メインメソッド
     */
    public static void main(String[] args) {
        EqpStatusMessenger messenger = null;

        try {
            // 設定をロード
            Config config = new Config();

            // メッセンジャーを作成
            messenger = new EqpStatusMessenger(config);

            // 初期化
            messenger.initialize();

            // 実行
            messenger.run();

        } catch (Exception e) {
            logger.error("アプリケーションの起動に失敗しました", e);
            System.exit(1);
        } finally {
            if (messenger != null) {
                messenger.shutdown();
            }
        }
    }
}
