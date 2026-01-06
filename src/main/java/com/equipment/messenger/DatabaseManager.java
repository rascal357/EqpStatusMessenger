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
}
