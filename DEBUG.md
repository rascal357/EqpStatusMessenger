# EqpStatusMessenger デバッグガイド

## 目次
1. [Eclipseでのデバッグ](#eclipseでのデバッグ)
2. [IntelliJ IDEAでのデバッグ](#intellij-ideaでのデバッグ)
3. [VS Codeでのデバッグ](#vs-codeでのデバッグ)
4. [ログレベルの変更](#ログレベルの変更)
5. [トラブルシューティング](#トラブルシューティング)

---

## Eclipseでのデバッグ

### 1. プロジェクトのインポート

```
File → Import → Maven → Existing Maven Projects
→ Browse で EqpStatusMessenger フォルダを選択
→ Finish
```

インポート後、自動的に依存関係（Oracle JDBC、ActiveMQ Artemisなど）がダウンロードされます。

### 2. 設定ファイルの編集

`src/main/resources/application.properties`を編集：

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

### 3. デバッグ実行の方法

#### 方法A: メインクラスから直接デバッグ

1. `src/main/java/com/equipment/messenger/EqpStatusMessenger.java`を開く
2. Package Explorerで`EqpStatusMessenger.java`を右クリック
3. `Debug As → Java Application`を選択

#### 方法B: デバッグ設定を作成（推奨）

1. メニューバー: `Run → Debug Configurations...`
2. 左側のツリーで`Java Application`を右クリック → `New Configuration`
3. 以下の項目を設定：
   - **Name**: EqpStatusMessenger Debug
   - **Project**: EqpStatusMessenger（Browseボタンから選択）
   - **Main class**: `com.equipment.messenger.EqpStatusMessenger`
     （Searchボタンから選択可能）
4. **Arguments**タブ（オプション）:
   - VM arguments: `-Xms256m -Xmx512m`（メモリ設定）
5. `Apply`ボタンをクリック
6. `Debug`ボタンをクリックして実行

### 4. ブレークポイントの設定

デバッグしたい行番号の**左側の余白（行番号の左）をダブルクリック**
- 青い丸●が表示されればブレークポイント設定完了
- もう一度ダブルクリックで削除

#### 推奨ブレークポイント

| ファイル | 行番号 | 説明 |
|---------|--------|------|
| EqpStatusMessenger.java | 79 | タイムスタンプ取得後 |
| EqpStatusMessenger.java | 82 | 装置ステータス取得後 |
| EqpStatusMessenger.java | 89 | メッセージ送信ループ内 |
| ArtemisMessenger.java | 51 | メッセージ送信前 |
| DatabaseManager.java | 83 | SQL実行前 |
| DatabaseManager.java | 114 | タイムスタンプ更新時 |

### 5. デバッグ操作（ショートカットキー）

デバッグ実行中に使用できる操作：

| キー | 操作 | 説明 |
|------|------|------|
| **F5** | Step Into | メソッドの中に入る |
| **F6** | Step Over | 次の行へ（メソッドは実行するが中には入らない） |
| **F7** | Step Return | 現在のメソッドから抜ける |
| **F8** | Resume | 次のブレークポイントまで実行 |
| **Ctrl+Shift+B** | Toggle Breakpoint | ブレークポイントの設定/解除 |
| **Ctrl+Shift+I** | Inspect | 選択した変数の値を確認 |

### 6. 変数の確認

デバッグ中、**Variables**ビューで以下の情報を確認できます：

- `config` - 設定情報
- `lastTimestamp` - 前回のタイムスタンプ
- `statusList` - 取得した装置ステータスのリスト
  - サイズ（size）を展開して確認
  - 各要素を展開して詳細を確認
- `status.eqpId` - 装置ID
- `status.status` - 装置ステータス
- `status.timestampTime` - タイムスタンプ

### 7. Expressionsビューの活用

1. **Window → Show View → Expressions**でExpressionsビューを表示
2. 右クリック → `Add Watch Expression`
3. 監視したい式を入力（例：`statusList.size()`）

### 8. Consoleでのログ確認

デバッグ実行すると、**Console**ビューにログが表示されます：

```
2026-01-06 10:30:00 [main] INFO  c.e.messenger.EqpStatusMessenger - ===== EqpStatusMessenger 初期化開始 =====
2026-01-06 10:30:01 [main] INFO  c.e.messenger.DatabaseManager - データベース接続成功
2026-01-06 10:30:02 [main] INFO  c.e.messenger.ArtemisMessenger - ActiveMQ Artemis接続成功
2026-01-06 10:30:03 [main] INFO  c.e.messenger.EqpStatusMessenger - ===== 初期化完了 =====
2026-01-06 10:30:03 [main] INFO  c.e.messenger.EqpStatusMessenger - ===== EqpStatusMessenger 実行開始 =====
```

---

## IntelliJ IDEAでのデバッグ

### 1. プロジェクトのインポート

```
File → Open → EqpStatusMessengerフォルダを選択 → OK
```

IntelliJ IDEAが自動的にMavenプロジェクトとして認識します。

### 2. デバッグ実行

1. `EqpStatusMessenger.java`を開く
2. メインメソッドの左側の**緑の矢印▶**をクリック
3. `Debug 'EqpStatusMessenger.main()'`を選択

### 3. ブレークポイントの設定

- 行番号をクリック（赤い丸●が表示される）
- **Ctrl+F8**でも設定可能

### 4. デバッグ操作（ショートカットキー）

| キー | 操作 | 説明 |
|------|------|------|
| **F7** | Step Into | メソッドの中に入る |
| **F8** | Step Over | 次の行へ |
| **Shift+F8** | Step Out | メソッドから抜ける |
| **F9** | Resume | 次のブレークポイントまで実行 |
| **Alt+F8** | Evaluate Expression | 式の評価 |

---

## VS Codeでのデバッグ

### 1. 拡張機能のインストール

以下の拡張機能をインストール：
- **Extension Pack for Java**（Microsoft）
- **Debugger for Java**（Microsoft）

### 2. launch.json設定

`.vscode/launch.json`を作成：

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Debug EqpStatusMessenger",
            "request": "launch",
            "mainClass": "com.equipment.messenger.EqpStatusMessenger",
            "projectName": "EqpStatusMessenger"
        }
    ]
}
```

### 3. デバッグ実行

1. **F5**キーを押す、または
2. 左側の「Run and Debug」アイコンをクリック → 「Debug EqpStatusMessenger」を選択

### 4. ブレークポイントの設定

- 行番号の左側をクリック（赤い丸●が表示される）

---

## ログレベルの変更

### デバッグモードでの詳細ログ出力

`src/main/resources/logback.xml`を編集：

```xml
<!-- 通常モード -->
<root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="FILE"/>
</root>

<!-- デバッグモード（より詳細なログ） -->
<root level="DEBUG">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="FILE"/>
</root>
```

### 特定のクラスだけDEBUGレベルにする

```xml
<!-- DatabaseManagerクラスのみDEBUGレベル -->
<logger name="com.equipment.messenger.DatabaseManager" level="DEBUG"/>

<!-- ArtemisMessengerクラスのみDEBUGレベル -->
<logger name="com.equipment.messenger.ArtemisMessenger" level="DEBUG"/>

<root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="FILE"/>
</root>
```

### DEBUGレベルで出力される追加情報

- `DatabaseManager` - 取得した装置ステータスの件数、タイムスタンプ更新の詳細
- SQL実行の詳細
- 各処理のタイミング情報

---

## トラブルシューティング

### データベース接続エラー

**エラーメッセージ:**
```
java.sql.SQLException: IO Error: The Network Adapter could not establish the connection
```

**確認事項:**
1. `application.properties`のDB接続情報を確認
   ```properties
   db.url=jdbc:oracle:thin:@hostname:1521:SID
   db.username=your_username
   db.password=your_password
   ```
2. Oracleサーバーが起動しているか確認
   ```bash
   # Oracleサーバーで確認
   lsnrctl status
   ```
3. ネットワーク接続を確認
   ```bash
   ping hostname
   telnet hostname 1521
   ```
4. ファイアウォール設定を確認

### ActiveMQ Artemis接続エラー

**エラーメッセージ:**
```
javax.jms.JMSException: Failed to create session factory
```

**確認事項:**
1. Artemisサーバーが起動しているか確認
   ```bash
   # Artemisサーバーで確認
   ps aux | grep artemis
   ```
2. `application.properties`のArtemis接続情報を確認
   ```properties
   artemis.url=tcp://hostname:61616
   artemis.username=admin
   artemis.password=admin
   ```
3. ポート61616が開いているか確認
   ```bash
   telnet hostname 61616
   ```

### テーブルが存在しないエラー

**エラーメッセージ:**
```
ORA-00942: table or view does not exist
```

**確認事項:**
1. RTI_TIMESTAMPテーブルが存在するか確認
   ```sql
   SELECT * FROM RTI_TIMESTAMP;
   ```
2. 装置ステータステーブルが存在するか確認
   ```sql
   SELECT * FROM EQUIPMENT_STATUS;
   ```
3. テーブル名が正しいか確認（大文字/小文字、スペルミス）
4. データベースユーザーに適切な権限があるか確認

### 依存関係の問題（Eclipse/IntelliJ）

**症状:** クラスが見つからない、import文がエラー

**解決方法:**

**Eclipse:**
```
プロジェクトを右クリック → Maven → Update Project
→ Force Update of Snapshots/Releases にチェック → OK
```

**IntelliJ IDEA:**
```
右側の Maven ビュー → Reload All Maven Projects（循環矢印アイコン）
```

### メモリ不足エラー

**エラーメッセージ:**
```
java.lang.OutOfMemoryError: Java heap space
```

**解決方法:**

Eclipse/IntelliJ のRun Configurationで VM arguments を設定：
```
-Xms512m -Xmx1024m
```

---

## デバッグのベストプラクティス

### 1. 段階的なデバッグ

最初は以下の順序で動作確認：

1. **設定の読み込み** - `Config`クラス
2. **データベース接続** - `DatabaseManager.testConnection()`
3. **Artemis接続** - `ArtemisMessenger.testConnection()`
4. **タイムスタンプ取得** - `getOrInitializeTimestamp()`
5. **装置ステータス取得** - `getUpdatedEquipmentStatus()`
6. **メッセージ送信** - `sendEquipmentStatus()`

### 2. ログの活用

デバッグ実行時は必ずConsoleビューでログを確認：
- エラーメッセージ
- スタックトレース
- SQL実行の詳細

### 3. 条件付きブレークポイント

特定の条件でのみ停止したい場合：

**Eclipse:**
1. ブレークポイントを右クリック → `Breakpoint Properties`
2. `Conditional`にチェック
3. 条件を入力（例：`statusList.size() > 0`）

**IntelliJ IDEA:**
1. ブレークポイントを右クリック
2. 条件を入力（例：`status.getEqpId().equals("EQP001")`）

### 4. データの準備

デバッグ前に、テスト用のデータを準備：

```sql
-- RTI_TIMESTAMPの初期化
DELETE FROM RTI_TIMESTAMP;

-- テスト用の装置ステータスを挿入
INSERT INTO EQUIPMENT_STATUS (EQPID, STATUS, TIMESTAMPTIME)
VALUES ('EQP001', 'RUNNING', SYSDATE);

INSERT INTO EQUIPMENT_STATUS (EQPID, STATUS, TIMESTAMPTIME)
VALUES ('EQP002', 'IDLE', SYSDATE);

COMMIT;
```

---

## 参考情報

### ログファイルの場所

- `logs/eqp-status-messenger.log` - アプリケーション全体のログ
- `logs/equipment-status.log` - 装置ステータス送信専用ログ

### 主要なクラスと役割

| クラス | 役割 |
|--------|------|
| EqpStatusMessenger | メインクラス、1分ごとのループ処理 |
| Config | 設定ファイル（application.properties）の読み込み |
| DatabaseManager | Oracle接続、タイムスタンプ・装置ステータス取得 |
| ArtemisMessenger | ActiveMQ Artemisへのメッセージ送信 |
| EquipmentStatus | 装置ステータスのデータモデル |

### 問い合わせ先

技術的な問題が発生した場合は、以下の情報を添えて問い合わせてください：

1. エラーメッセージ（完全なスタックトレース）
2. ログファイル（`logs/eqp-status-messenger.log`）
3. 設定ファイル（`application.properties`）※パスワードは削除
4. 実行環境（Java バージョン、OSなど）
