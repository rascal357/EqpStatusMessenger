# EqpStatusMessenger

装置ステータスをOracleデータベースから取得し、ActiveMQ Artemisのキューに送信するJavaアプリケーション。

## 概要

1分ごとに以下の処理を実行します：
1. RTI_TIMESTAMPテーブルからタイムスタンプを取得（初回は現在時刻を挿入）
2. タイムスタンプ以降に更新された装置ステータスを取得
3. 取得したステータスをActiveMQ ArtemisのE10StateChangeキューに送信
4. 送信情報をログに記録
5. 1分間スリープ後、繰り返し

## 必要な環境

- Java 11以降
- Maven 3.6以降
- Oracle Database
- ActiveMQ Artemis

## データベース設定

### 必要なテーブル

#### 1. RTI_TIMESTAMP テーブル

```sql
CREATE TABLE RTI_TIMESTAMP (
    TIMESTAMPTIME DATE,
    UPDATETIME DATE
);
```

#### 2. 装置ステータステーブル（例：EQUIPMENT_STATUS）

```sql
CREATE TABLE EQUIPMENT_STATUS (
    EQPID VARCHAR2(50),
    STATUS VARCHAR2(50),
    TIMESTAMPTIME DATE
);
```

**注意**: 装置ステータステーブル名は `application.properties` で設定可能です。

## ビルド方法

```bash
# プロジェクトディレクトリに移動
cd EqpStatusMessenger

# Mavenでビルド
mvn clean package

# 実行可能JARファイルが生成されます
# target/EqpStatusMessenger-1.0.0-jar-with-dependencies.jar
```

## 設定

`src/main/resources/application.properties` を編集してください。

```properties
# データベース設定
db.url=jdbc:oracle:thin:@hostname:1521:SID
db.username=your_username
db.password=your_password
db.equipment.table=EQUIPMENT_STATUS

# ActiveMQ Artemis設定
artemis.url=tcp://hostname:61616
artemis.username=admin
artemis.password=admin
artemis.queue=E10StateChange

# アプリケーション設定
app.interval.seconds=60
```

## ローカルでの実行

```bash
# ビルド後、以下のコマンドで実行
java -jar target/EqpStatusMessenger-1.0.0-jar-with-dependencies.jar
```

## Linuxサーバーでの常駐設定（systemd）

### 1. アプリケーションの配置

```bash
# アプリケーション用ディレクトリを作成
sudo mkdir -p /opt/eqp-status-messenger
sudo mkdir -p /opt/eqp-status-messenger/logs

# JARファイルをコピー
sudo cp target/EqpStatusMessenger-1.0.0-jar-with-dependencies.jar /opt/eqp-status-messenger/

# 設定ファイルをコピー（JARに含まれていない場合）
sudo cp src/main/resources/application.properties /opt/eqp-status-messenger/

# 権限設定
sudo chown -R your_user:your_group /opt/eqp-status-messenger
```

### 2. systemdサービスファイルの設定

```bash
# サービスファイルを編集
# eqp-status-messenger.service の User と Group を実際のユーザー名に変更

# サービスファイルをコピー
sudo cp eqp-status-messenger.service /etc/systemd/system/

# systemdをリロード
sudo systemctl daemon-reload
```

### 3. サービスの起動

```bash
# サービスを有効化（自動起動設定）
sudo systemctl enable eqp-status-messenger

# サービスを起動
sudo systemctl start eqp-status-messenger

# ステータス確認
sudo systemctl status eqp-status-messenger

# ログ確認
sudo journalctl -u eqp-status-messenger -f
```

### 4. サービスの操作

```bash
# サービスを停止
sudo systemctl stop eqp-status-messenger

# サービスを再起動
sudo systemctl restart eqp-status-messenger

# 自動起動を無効化
sudo systemctl disable eqp-status-messenger
```

## ログ

ログは以下の場所に出力されます：

- `logs/eqp-status-messenger.log` - アプリケーション全体のログ（日付ごとにローテーション）
- `logs/equipment-status.log` - 装置ステータス送信専用ログ（日付ごとにローテーション）
- systemd journal - `journalctl -u eqp-status-messenger`で確認可能

ログフォーマット例：
```
2026-01-06 10:30:15 - メッセージ送信 - EQPID: EQP001, STATUS: RUNNING, TIME: 2026-01-06 10:30:00
```

## メッセージフォーマット

ActiveMQ Artemisに送信されるメッセージ：

- **Queue**: E10StateChange
- **Message Body**: `currentState="<ステータス>"`
- **JMSReplyTo**: `EquipmentId=<装置ID>`

## トラブルシューティング

### データベース接続エラー

- `application.properties` のデータベース設定を確認
- Oracle JDBCドライバーが正しくインストールされているか確認
- ネットワーク接続とファイアウォール設定を確認

### ActiveMQ Artemis接続エラー

- Artemisサーバーが起動しているか確認
- `application.properties` のArtemis設定を確認
- ユーザー名とパスワードが正しいか確認

### サービスが起動しない

```bash
# 詳細なログを確認
sudo journalctl -u eqp-status-messenger -n 100

# サービスファイルの構文チェック
sudo systemd-analyze verify eqp-status-messenger.service
```

## ライセンス

プロプライエタリ

## 作成者

[あなたの名前]
