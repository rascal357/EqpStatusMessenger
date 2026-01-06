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
     * SELECT FOR UPDATE NOWAITによる排他制御を使用
     */
    public void run() {
        logger.info("===== EqpStatusMessenger 実行開始 =====");
        logger.info("処理間隔: {}秒", config.getIntervalSeconds());
        logger.info("排他制御: SELECT FOR UPDATE NOWAIT使用");

        // シャットダウンフックを登録
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("シャットダウンシグナルを受信しました");
            running = false;
        }));

        while (running) {
            Connection conn = null;
            try {
                // トランザクション開始
                conn = dbManager.beginTransaction();

                // RTI_TIMESTAMPテーブルからタイムスタンプを排他ロック付きで取得
                Date lastTimestamp = dbManager.getOrInitializeTimestampWithLock(conn);

                // 前回のタイムスタンプ以降に更新された装置ステータスを取得
                List<EquipmentStatus> statusList = dbManager.getUpdatedEquipmentStatus(conn, lastTimestamp);

                // 最新のタイムスタンプを記録
                Date maxTimestamp = lastTimestamp;

                if (statusList.isEmpty()) {
                    logger.debug("更新された装置ステータスはありません");
                    // データがない場合はlastTimestampのまま
                } else {
                    logger.info("{}件の装置ステータスを処理します", statusList.size());

                    // 各装置ステータスをActiveMQ Artemisに送信
                    for (EquipmentStatus status : statusList) {
                        artemisMessenger.sendEquipmentStatus(status);

                        // 最新のタイムスタンプを更新
                        if (status.getTimestampTime().after(maxTimestamp)) {
                            maxTimestamp = status.getTimestampTime();
                        }
                    }
                }

                // RTI_TIMESTAMPテーブルを更新してコミット（データがない場合もlastTimestampで更新）
                dbManager.updateTimestampAndCommit(conn, maxTimestamp);
                dbManager.closeConnection(conn);
                conn = null;

                logger.info("処理完了 - 次回チェックタイムスタンプ: {}", maxTimestamp);

                // 指定秒数スリープ
                Thread.sleep(config.getIntervalSeconds() * 1000L);

            } catch (SQLException e) {
                // ORA-00054: resource busy and acquire with NOWAIT specified
                if (e.getErrorCode() == 54) {
                    logger.warn("他のプロセスが実行中のため、ロックを取得できませんでした。30秒後に再試行します。");
                    dbManager.rollback(conn);
                    dbManager.closeConnection(conn);
                    conn = null;

                    try {
                        // 30秒待って再試行
                        Thread.sleep(30 * 1000L);
                    } catch (InterruptedException ie) {
                        logger.warn("スリープが中断されました", ie);
                        running = false;
                    }
                } else {
                    logger.error("データベースエラーが発生しました", e);
                    dbManager.rollback(conn);
                    dbManager.closeConnection(conn);
                    conn = null;

                    try {
                        Thread.sleep(config.getIntervalSeconds() * 1000L);
                    } catch (InterruptedException ie) {
                        logger.warn("スリープが中断されました", ie);
                        running = false;
                    }
                }
            } catch (JMSException e) {
                logger.error("メッセージング エラーが発生しました", e);
                dbManager.rollback(conn);
                dbManager.closeConnection(conn);
                conn = null;

                try {
                    Thread.sleep(config.getIntervalSeconds() * 1000L);
                } catch (InterruptedException ie) {
                    logger.warn("スリープが中断されました", ie);
                    running = false;
                }
            } catch (InterruptedException e) {
                logger.warn("スリープが中断されました", e);
                dbManager.rollback(conn);
                dbManager.closeConnection(conn);
                running = false;
            } catch (Exception e) {
                logger.error("予期しないエラーが発生しました", e);
                dbManager.rollback(conn);
                dbManager.closeConnection(conn);
                conn = null;

                try {
                    Thread.sleep(config.getIntervalSeconds() * 1000L);
                } catch (InterruptedException ie) {
                    logger.warn("スリープが中断されました", ie);
                    running = false;
                }
            } finally {
                // 念のため、Connectionが残っていればクローズ
                if (conn != null) {
                    dbManager.rollback(conn);
                    dbManager.closeConnection(conn);
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
