package com.equipment.messenger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 設定管理クラス
 */
public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    private final Properties properties;

    public Config() throws IOException {
        this("application.properties");
    }

    public Config(String filename) throws IOException {
        properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(filename)) {
            if (input == null) {
                throw new IOException("設定ファイルが見つかりません: " + filename);
            }
            properties.load(input);
            logger.info("設定ファイルを読み込みました: {}", filename);
        }
    }

    public String getDatabaseUrl() {
        return properties.getProperty("db.url");
    }

    public String getDatabaseUsername() {
        return properties.getProperty("db.username");
    }

    public String getDatabasePassword() {
        return properties.getProperty("db.password");
    }

    public String getEquipmentTableName() {
        return properties.getProperty("db.equipment.table", "EQUIPMENT_STATUS");
    }

    public String getArtemisUrl() {
        return properties.getProperty("artemis.url");
    }

    public String getArtemisUsername() {
        return properties.getProperty("artemis.username");
    }

    public String getArtemisPassword() {
        return properties.getProperty("artemis.password");
    }

    public String getArtemisQueue() {
        return properties.getProperty("artemis.queue", "E10StateChange");
    }

    public int getIntervalSeconds() {
        return Integer.parseInt(properties.getProperty("app.interval.seconds", "60"));
    }
}
