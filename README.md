# EqpStatusMessenger

装置ステータスをOracleデータベースから取得し、ActiveMQ Artemisのキューに送信するJavaアプリケーション。

## 概要

1分ごとに以下の処理を実行します：
1. RTI_TIMESTAMPテーブルからタイムスタンプを取得（初回は現在時刻を挿入）
2. タイムスタンプ以降に更新された装置ステータスを取得
3. 取得したステータスをActiveMQ ArtemisのE10StateChangeキューに送信
4. 送信情報をログに記録
5. 1分間スリープ後、繰り返し

## 排他制御（複数インスタンス対応）

**SELECT FOR UPDATE NOWAIT** を使用した排他制御により、複数のプログラムインスタンスを同時に起動しても安全に動作します。

### 動作の仕組み

1. **トランザクション開始**: 処理の最初にトランザクションを開始
2. **排他ロック取得**: `SELECT TIMESTAMPTIME FROM RTI_TIMESTAMP FOR UPDATE NOWAIT` でロックを取得
   - ロック取得成功 → 処理を継続
   - ロック取得失敗（他のプロセスが実行中） → 30秒待って再試行
3. **装置ステータス取得**: 同じトランザクション内で装置ステータスを取得
4. **メッセージ送信**: ActiveMQ Artemisに送信
5. **コミット**: タイムスタンプを更新してコミット（ロック解放）

### 複数インスタンス起動時の挙動

- **インスタンスA**: ロック取得成功 → 処理実行
- **インスタンスB**: ロック取得失敗 → 30秒待機 → 再試行
- **インスタンスC**: ロック取得失敗 → 30秒待機 → 再試行

これにより、同時に1つのインスタンスだけが処理を実行し、データの二重送信を防止します。

### メリット

- 冗長構成が可能（フェイルオーバー対応）
- 1つのインスタンスが停止しても、他のインスタンスが処理を継続
- データベースレベルでの排他制御により、確実に二重処理を防止

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

### 設定ファイルの読み込み優先順位

アプリケーションは以下の優先順位で設定を読み込みます：

1. **./config/application.properties** (外部ファイル) ← 最優先
2. **./application.properties** (カレントディレクトリ)
3. **クラスパス内のapplication.properties** (jarファイル内)
4. **システムプロパティ** (`-Ddb.url=...` などのコマンドライン引数) ← 最終的な上書き

### 設定項目

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

### 開発環境での設定

開発時は `src/main/resources/application.properties` を直接編集してください。

```bash
vi src/main/resources/application.properties
mvn clean package
java -jar target/EqpStatusMessenger-1.0.0-jar-with-dependencies.jar
```

### 本番環境での設定（推奨）

**外部設定ファイルを使用することで、jarファイルを再ビルドせずに設定変更が可能です。**

#### 方法1: configディレクトリを使用（推奨）

```bash
# configディレクトリを作成
mkdir config

# 設定ファイルをコピーして編集
cp src/main/resources/application.properties config/
vi config/application.properties

# 実行（config/application.propertiesが自動的に読み込まれる）
java -jar target/EqpStatusMessenger-1.0.0-jar-with-dependencies.jar
```

#### 方法2: カレントディレクトリに配置

```bash
# カレントディレクトリに配置
cp src/main/resources/application.properties .
vi application.properties

# 実行
java -jar target/EqpStatusMessenger-1.0.0-jar-with-dependencies.jar
```

#### 方法3: システムプロパティで上書き

特定の設定だけをコマンドラインで上書きできます（パスワードなど機密情報に便利）。

```bash
# パスワードだけを上書き
java -Ddb.password=prod_password \
     -Dartemis.password=prod_password \
     -jar target/EqpStatusMessenger-1.0.0-jar-with-dependencies.jar

# 複数の設定を上書き
java -Ddb.url=jdbc:oracle:thin:@prod-server:1521:ORCL \
     -Ddb.username=prod_user \
     -Ddb.password=prod_pass \
     -Dartemis.url=tcp://prod-artemis:61616 \
     -jar target/EqpStatusMessenger-1.0.0-jar-with-dependencies.jar
```

### 設定変更時の運用

| 方法 | 設定変更後の手順 | 再ビルド |
|------|------------------|----------|
| jarファイル内 | `mvn clean package` で再ビルド | 必要 ✗ |
| 外部ファイル (config/) | 設定ファイルを編集してアプリ再起動 | 不要 ✓ |
| システムプロパティ | 起動コマンドを変更して再起動 | 不要 ✓ |

**推奨**: 本番環境では外部設定ファイル（config/application.properties）を使用し、機密情報はシステムプロパティで上書きする方法が最も柔軟です。

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
sudo mkdir -p /opt/eqp-status-messenger/config
sudo mkdir -p /opt/eqp-status-messenger/logs

# JARファイルをコピー
sudo cp target/EqpStatusMessenger-1.0.0-jar-with-dependencies.jar /opt/eqp-status-messenger/

# 外部設定ファイルをコピー（推奨）
sudo cp src/main/resources/application.properties /opt/eqp-status-messenger/config/

# 本番環境用に設定を編集
sudo vi /opt/eqp-status-messenger/config/application.properties

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

### 設定ファイルが読み込まれない

アプリケーション起動時のログで、どの設定ファイルが読み込まれたか確認できます：

```
設定ファイルを読み込みました: /opt/eqp-status-messenger/config/application.properties
```

または

```
設定ファイルを読み込みました (クラスパス): application.properties
```

システムプロパティで上書きした場合は以下のように表示されます：

```
システムプロパティで上書き: db.url = jdbc:oracle:thin:@prod-server:1521:ORCL
システムプロパティで上書き: db.password = ********
```

### データベース接続エラー

- 設定ファイル（`config/application.properties` または jarファイル内）のデータベース設定を確認
- Oracle JDBCドライバーが正しくインストールされているか確認
- ネットワーク接続とファイアウォール設定を確認
- システムプロパティで上書きしている場合は、コマンドライン引数を確認

### ActiveMQ Artemis接続エラー

- Artemisサーバーが起動しているか確認
- 設定ファイルまたはシステムプロパティのArtemis設定を確認
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
