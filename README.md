# PositiveFinger Android / Kotlin

現在の iOS 版を基準にした Android 移植版。Mac は VS Code で編集し、APK は GitHub Actions または Android 担当者の Windows 環境でビルドします。Mac への Android Studio / Android SDK インストールは不要です。

## 実装

- iOS 版と同じ5タブ：ホーム・あそぶ・ずかん・交換所・設定。
- クリーム色の背景、ピンク・黄・紫のカード、輪郭線、指モチーフの相棒6種を Android の View / Canvas で実装。
- メール登録・ログイン、ニックネーム、ランキング参加設定、背景・称号、ログアウト。
- iOS と同じ Supabase の `pf_bootstrap` / `pf_action` / `pf_update_profile` / `pf_leaderboard` を使用。
- ことばポイント、チェックイン、ミッション、25 pt のガチャ、相棒選択、交換確認、イベント参加・報酬、お知らせ・ランキング。
- サーバー残高を正とし、通信失敗時にローカル残高を増やさない。ガチャ等は再試行用 UUID を保存し二重消費を防止。
- 日本語かなフリック、濁点・半濁点・小文字、候補確定、英字QWERTY、数字・記号、削除、改行、IME切替。
- 日本語変換：**スミレの MIT 辞書処理コード＋同プロジェクトの Mozc 由来システム辞書**を利用。PositiveFinger 用の上限付きビーム探索で文章候補を生成。
- 入力文はメモリ内だけで処理。永続化は集計・設定・重複判定用ハッシュ・イベント UUID。パスワード／学習禁止フィールドは検出対象外。
- 認証トークンは Android Keystore の AES-GCM で暗号化。アプリバックアップ無効。

## まず VS Code で開く

「ファイル → フォルダーを開く」で `Documents/GitHub/positivefinfger-android` を選択します。

| 編集対象 | ファイル |
| --- | --- |
| 5タブ・ログイン・画面の文言 | `app/src/main/java/com/positivefinger/android/MainActivity.kt` |
| 色・相棒のベクター描画 | `app/src/main/java/com/positivefinger/android/Pop.kt` |
| キー配置・フリック・入力確定 | `app/src/main/java/com/positivefinger/android/PositiveFingerKeyboardService.kt` |
| スミレ辞書との接続・文章候補 | `app/src/main/java/com/positivefinger/android/SumireConverter.kt` |
| iOS と同じ表現検出ルール | `app/src/main/java/com/positivefinger/android/PositiveEngine.kt` |
| 集計・クールダウン・同期キュー | `app/src/main/java/com/positivefinger/android/ActivityStore.kt` |
| サーバー API・認証 | `app/src/main/java/com/positivefinger/android/CloudClient.kt` |
| APK ビルド | `.github/workflows/android.yml` |

`thirdparty/sumire` 以下は出典を保持した外部コードです。変更時は `docs/THIRD_PARTY.md` の差分記録も更新します。

## Mac に SDK を入れずに APK を作る

1. VS Code のソース管理で変更を確認し、作業ブランチをコミット・発行します。
2. GitHub の Actions → **Android APK** を開きます。PR・main・`feature/**` の push で自動実行、または Run workflow で手動実行します。
3. 成功した実行の Artifacts → **PositiveFinger-Android-debug** をダウンロードします。
4. ZIP 内の `app-debug.apk` を Android 端末へ渡してインストールします。これは開発用署名の APK です。
5. アプリでログインし、「キーボードの追加・使い方」から PositiveFinger を有効化・選択します。

SDK・JDK・Gradle の準備、単体テスト、Lint、APK ビルドは GitHub の Linux runner 上で実行します。初回の push や Actions の実行はまだ行っていません。

## iOS との差・未確認項目

- SwiftUI のコードをそのまま使うのではなく、Android の UI で構成・配色・文言を再実装。完全に同じピクセル配置ではありません。
- スミレ全体のフォークではありません。辞書読込・検索部分を利用し、変換探索・キーUIは本アプリ側で実装。スミレ／azooKey と同じ候補順や精度を保証しません。ユーザー辞書、入力履歴の学習、高度な予測、絵文字一覧、ライブ変換、ローマ字かな変換は未移植です。
- かな入力は、中央タップ＝あ段、左＝い段、上＝う段、右＝え段、下＝お段。連打によるトグル入力は未対応です。
- スミレの標準システム辞書だけを使用。追加カテゴリ辞書・AIモデルは同梱しません。長文は64文字まで文章変換、超過時もかな／カタカナで確定可能です。
- iOS の WebSocket リアルタイム購読の代わりに、アプリ表示中は15秒ごとに同期。キーボードで獲得イベントができた時も同期します。
- 外部ログインはサーバーで有効な Apple / Google / LINE を表示し、ブラウザ＋PKCEで接続。実サービスでのログイン・メール確認・Android callback 動作は未確認。サーバーのリダイレクト許可に `positivefinger://auth/callback` が必要です。
- 開発用隠しポイント操作は未移植です。クラウド残高はサーバーの処理だけで更新します。
- APK ビルド、実機での見た目・フリック操作、クラウドの実アカウント操作は未確認です。設定した Actions が初回の統合ビルド確認になります。

## 検証済み

MacにSDKをインストールせず、既存JDK/Kotlinコンパイラと作業フォルダ内の Android テスト用フレームワークで全 Kotlin ソースをコンパイル。
単体テスト11件に合格：否定表現、長い語句の優先、正規化、再起動後の重複抑制、30秒クールダウン、日次100 ptと日付更新、アカウント切替、同期キューの確認応答、入力文を保存しないこと、相棒の所有確認、辞書による単語・文章変換等。
これは Android Gradle による APK ビルド成功を意味しません。

Androidビルド環境がある Windows 等では：

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

AGP 8.7.3 / Gradle 8.9 / JDK17 / compileSdk35 / minSdk26。互換性は [Android公式のAGP8.7資料](https://developer.android.com/build/releases/agp-8-7-0-release-notes) に合わせています。

ライセンスと出典：[docs/THIRD_PARTY.md](docs/THIRD_PARTY.md)。設定からオフラインで全文のライセンスを読めます。
