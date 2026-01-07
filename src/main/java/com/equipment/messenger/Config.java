package com.equipment.messenger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 設定管理クラス
 *
 * 設定ファイルの読み込み優先順位:
 * 1. ./config/application.properties (外部ファイル)
 * 2. ./application.properties (カレントディレクトリ)
 * 3. クラスパス内のapplication.properties (jarファイル内)
 * 4. システムプロパティ (-Ddb.url=... など)
 */
public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    private final Properties properties;

    public Config() throws IOException {
        this("application.properties");
    }

    public Config(String filename) throws IOException {
        properties = new Properties();

        // 1. ./config/application.properties を試す
        File configDir = new File("config", filename);
        if (configDir.exists() && configDir.isFile()) {
            try (InputStream input = new FileInputStream(configDir)) {
                properties.load(input);
                logger.info("設定ファイルを読み込みました: {}", configDir.getAbsolutePath());
            }
        }
        // 2. ./application.properties を試す
        else {
            File currentDir = new File(filename);
            if (currentDir.exists() && currentDir.isFile()) {
                try (InputStream input = new FileInputStream(currentDir)) {
                    properties.load(input);
                    logger.info("設定ファイルを読み込みました: {}", currentDir.getAbsolutePath());
                }
            }
            // 3. クラスパス（jarファイル内）から読み込む
            else {
                try (InputStream input = getClass().getClassLoader().getResourceAsStream(filename)) {
                    if (input == null) {
                        throw new IOException("設定ファイルが見つかりません: " + filename);
                    }
                    properties.load(input);
                    logger.info("設定ファイルを読み込みました (クラスパス): {}", filename);
                }
            }
        }

        // 4. システムプロパティで上書き
        overrideWithSystemProperties();
    }

    /**
     * システムプロパティで設定を上書き
     * コマンドライン引数 -Ddb.url=... などで指定された値を優先
     */
    private void overrideWithSystemProperties() {
        String[] keys = {
            "db.url", "db.username", "db.password", "db.equipment.table",
            "artemis.url", "artemis.username", "artemis.password", "artemis.queue",
            "app.interval.seconds"
        };

        for (String key : keys) {
            String systemValue = System.getProperty(key);
            if (systemValue != null) {
                properties.setProperty(key, systemValue);
                logger.info("システムプロパティで上書き: {} = {}", key,
                    key.contains("password") ? "********" : systemValue);
            }
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
