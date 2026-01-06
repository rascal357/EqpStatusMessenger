package com.equipment.messenger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Oracleデータベース管理クラス
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String equipmentTableName;

    public DatabaseManager(String jdbcUrl, String username, String password, String equipmentTableName) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.equipmentTableName = equipmentTableName;
    }

    /**
     * データベース接続を取得
     */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    /**
     * RTI_TIMESTAMPテーブルからTIMESTAMPTIMEを取得
     * データがない場合は現在時間を挿入して返す
     */
    public Date getOrInitializeTimestamp() throws SQLException {
        try (Connection conn = getConnection()) {
            // まず既存のタイムスタンプを取得
            String selectSql = "SELECT TIMESTAMPTIME FROM RTI_TIMESTAMP";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(selectSql)) {

                if (rs.next()) {
                    Timestamp timestamp = rs.getTimestamp("TIMESTAMPTIME");
                    if (timestamp != null) {
                        logger.info("既存のタイムスタンプを取得: {}", timestamp);
                        return new Date(timestamp.getTime());
                    }
                }
            }

            // データがない場合は現在時間を挿入
            Date currentTime = new Date();
            String insertSql = "INSERT INTO RTI_TIMESTAMP (TIMESTAMPTIME, UPDATETIME) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                Timestamp timestamp = new Timestamp(currentTime.getTime());
                pstmt.setTimestamp(1, timestamp);
                pstmt.setTimestamp(2, timestamp);
                pstmt.executeUpdate();
                logger.info("新しいタイムスタンプを挿入: {}", timestamp);
            }

            return currentTime;
        }
    }

    /**
     * 指定されたタイムスタンプ以降に更新された装置ステータスを取得
     */
    public List<EquipmentStatus> getUpdatedEquipmentStatus(Date fromTimestamp) throws SQLException {
        List<EquipmentStatus> statusList = new ArrayList<>();

        String sql = "SELECT EQPID, STATUS, TIMESTAMPTIME FROM " + equipmentTableName +
                     " WHERE TIMESTAMPTIME > ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setTimestamp(1, new Timestamp(fromTimestamp.getTime()));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    EquipmentStatus status = new EquipmentStatus();
                    status.setEqpId(rs.getString("EQPID"));
                    status.setStatus(rs.getString("STATUS"));
                    status.setTimestampTime(new Date(rs.getTimestamp("TIMESTAMPTIME").getTime()));
                    statusList.add(status);
                }
            }
        }

        logger.debug("{}件の装置ステータスを取得", statusList.size());
        return statusList;
    }

    /**
     * RTI_TIMESTAMPテーブルのTIMESTAMPTIMEとUPDATETIMEを更新
     * TIMESTAMPTIME: 指定されたタイムスタンプ
     * UPDATETIME: 現在時刻
     */
    public void updateTimestamp(Date newTimestamp) throws SQLException {
        String sql = "UPDATE RTI_TIMESTAMP SET TIMESTAMPTIME = ?, UPDATETIME = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            Timestamp timestampTime = new Timestamp(newTimestamp.getTime());
            Timestamp updateTime = new Timestamp(System.currentTimeMillis());

            pstmt.setTimestamp(1, timestampTime);
            pstmt.setTimestamp(2, updateTime);
            pstmt.executeUpdate();

            logger.debug("タイムスタンプを更新 - TIMESTAMPTIME: {}, UPDATETIME: {}",
                        timestampTime, updateTime);
        }
    }

    /**
     * データベース接続をテスト
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            logger.info("データベース接続成功");
            return true;
        } catch (SQLException e) {
            logger.error("データベース接続失敗", e);
            return false;
        }
    }

    // ========== トランザクション管理メソッド（排他制御用） ==========

    /**
     * トランザクションを開始してConnectionを返す
     * 使用後は必ずcommitまたはrollbackを呼び出すこと
     */
    public Connection beginTransaction() throws SQLException {
        Connection conn = getConnection();
        conn.setAutoCommit(false);
        logger.debug("トランザクション開始");
        return conn;
    }

    /**
     * RTI_TIMESTAMPテーブルからTIMESTAMPTIMEを排他ロック付きで取得
     * SELECT FOR UPDATE NOWAITを使用して他のプロセスとの競合を回避
     *
     * @param conn トランザクション用のConnection
     * @return TIMESTAMPTIMEの値
     * @throws SQLException ロック取得失敗時（ORA-00054）を含む
     */
    public Date getOrInitializeTimestampWithLock(Connection conn) throws SQLException {
        // まず既存のタイムスタンプを排他ロック付きで取得
        String selectSql = "SELECT TIMESTAMPTIME FROM RTI_TIMESTAMP FOR UPDATE NOWAIT";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSql)) {

            if (rs.next()) {
                Timestamp timestamp = rs.getTimestamp("TIMESTAMPTIME");
                if (timestamp != null) {
                    logger.info("既存のタイムスタンプを排他ロック付きで取得: {}", timestamp);
                    return new Date(timestamp.getTime());
                }
            }
        } catch (SQLException e) {
            // ORA-00054: resource busy and acquire with NOWAIT specified
            if (e.getErrorCode() == 54) {
                logger.warn("RTI_TIMESTAMPテーブルがロックされています（他のプロセスが実行中）");
                throw e;
            }
            throw e;
        }

        // データがない場合は現在時間を挿入（初回のみ）
        Date currentTime = new Date();
        String insertSql = "INSERT INTO RTI_TIMESTAMP (TIMESTAMPTIME, UPDATETIME) VALUES (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            Timestamp timestamp = new Timestamp(currentTime.getTime());
            pstmt.setTimestamp(1, timestamp);
            pstmt.setTimestamp(2, timestamp);
            pstmt.executeUpdate();
            logger.info("新しいタイムスタンプを挿入: {}", timestamp);
        }

        return currentTime;
    }

    /**
     * 指定されたタイムスタンプ以降に更新された装置ステータスを取得（トランザクション内）
     *
     * @param conn トランザクション用のConnection
     * @param fromTimestamp 取得開始タイムスタンプ
     * @return 装置ステータスのリスト
     */
    public List<EquipmentStatus> getUpdatedEquipmentStatus(Connection conn, Date fromTimestamp) throws SQLException {
        List<EquipmentStatus> statusList = new ArrayList<>();

        String sql = "SELECT EQPID, STATUS, TIMESTAMPTIME FROM " + equipmentTableName +
                     " WHERE TIMESTAMPTIME > ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, new Timestamp(fromTimestamp.getTime()));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    EquipmentStatus status = new EquipmentStatus();
                    status.setEqpId(rs.getString("EQPID"));
                    status.setStatus(rs.getString("STATUS"));
                    status.setTimestampTime(new Date(rs.getTimestamp("TIMESTAMPTIME").getTime()));
                    statusList.add(status);
                }
            }
        }

        logger.debug("{}件の装置ステータスを取得", statusList.size());
        return statusList;
    }

    /**
     * RTI_TIMESTAMPテーブルを更新してトランザクションをコミット
     *
     * @param conn トランザクション用のConnection
     * @param newTimestamp 新しいタイムスタンプ
     */
    public void updateTimestampAndCommit(Connection conn, Date newTimestamp) throws SQLException {
        String sql = "UPDATE RTI_TIMESTAMP SET TIMESTAMPTIME = ?, UPDATETIME = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            Timestamp timestampTime = new Timestamp(newTimestamp.getTime());
            Timestamp updateTime = new Timestamp(System.currentTimeMillis());

            pstmt.setTimestamp(1, timestampTime);
            pstmt.setTimestamp(2, updateTime);
            pstmt.executeUpdate();

            logger.debug("タイムスタンプを更新 - TIMESTAMPTIME: {}, UPDATETIME: {}",
                        timestampTime, updateTime);
        }

        // トランザクションをコミット
        conn.commit();
        logger.debug("トランザクションをコミットしました");
    }

    /**
     * トランザクションをロールバック
     *
     * @param conn トランザクション用のConnection
     */
    public void rollback(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
                logger.debug("トランザクションをロールバックしました");
            } catch (SQLException e) {
                logger.error("ロールバックに失敗しました", e);
            }
        }
    }

    /**
     * Connectionをクローズ
     *
     * @param conn クローズするConnection
     */
    public void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
                logger.debug("データベース接続をクローズしました");
            } catch (SQLException e) {
                logger.error("接続のクローズに失敗しました", e);
            }
        }
    }
}
