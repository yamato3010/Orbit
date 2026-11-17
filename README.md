# Orbit — VRChat Tools for Android

VRChatのフレンドとワールドを身近にする、Kotlin / Jetpack Compose製の非公式Androidアプリです。Material 3 Expressiveのテーマとモーションを使用しています。

## プレビュー

<img src="docs/screenshots/home.png" alt="Orbitのホーム画面（デモモード）" width="320" />

## 使える機能

- **ホーム**：オンライン人数、フレンドのステータス、ワールド、お気に入り
- **フレンド**：名前・現在地の検索、オンライン／参加歓迎／お気に入りでの絞り込み、詳細表示
- **ワールド**：検索、詳細表示、端末内のブックマーク、VRChatのWebページを開く
- **アクティビティ**：手動更新時に検出したオンライン状態と現在地の変化を端末内に最大100件保存
- **設定**：ライト／ダーク／システム、Android 12以降のダイナミックカラー
- **ログイン**：ユーザー名とパスワード、TOTP／メールOTP／リカバリーコードによる2段階認証、セッション復元、ログアウト

初回起動は**デモモード**です。デモのフレンド・ワールドは架空で、ワールド画像はCompose Canvasによるイラストです。ホームまたは設定からログインするとVRChat APIのデータに切り替わります。フレンドとログ画面の更新ボタンで最新情報を取得します。

お気に入りはOrbit専用で、VRChatのお気に入りとは同期しません。履歴は更新ごとの差分であり、閉じている間の全イベントやPCのプレイ時間は取得しません。アバター管理・招待の送信・WebSocket常時接続・PCのローカルログ連携は未実装です。プライベートな現在地は公開せず、そのまま非公開として表示します。

## 認証と保存

通信はHTTPSでVRChat APIへ直接行います。パスワードと認証コードは永続保存しません。認証CookieをAndroid KeystoreのAES-GCM鍵で暗号化して保存し、ログアウト時に削除します。バックアップは無効です。フレンドのお気に入り、ワールドの保存、活動ログはアカウントごとに端末内に保存します。ログアウト後もこれらのローカルデータは残ります。

## ビルド

Android StudioのJBR（JDK 17以上）とAndroid SDK 37が必要です。Gradle 9.6 / AGP 9.5.1、Material 3 1.5.0-rc01を使用しています。

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

出力：`app/build/outputs/apk/debug/app-debug.apk`

デモモードの端末／エミュレーターでUIテストを実行できます。

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

実アカウントによるログイン・2段階認証・API通信は別途確認が必要です。API仕様はコミュニティによるリバースエンジニアリングに基づいており、変更される可能性があります。

## 今回の検証結果

- Debug APKのビルド成功
- フレンド検索・状態判定のユニットテスト5件成功（テンプレートのテストを含めると6件）
- Android Lint：エラー0件。依存関係の更新案・KTXの利用案などの警告は残っています。
- Android 17エミュレーターでホームの実表示を確認し、上のスクリーンショットを撮影
- UIテストは検索欄と結果を区別するセレクターへ修正済みですが、最終再実行時にエミュレーターのpackage / activityサービスが再起動し、APKの再インストールに失敗したため完了できていません。
- 実アカウントのログイン・2段階認証・API応答は未検証

## デザインと実装の参考

- [Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)：形、色、タイポグラフィ、モーション
- [ArchiveTune](https://github.com/rukamori/ArchiveTune)：大きな見出し、丸みのある操作部、カードの構成
- [Petal](https://github.com/shreyagarwal72/petal)：柔らかな色調とExpressiveな形
- [VRChat API仕様](https://github.com/vrchatapi/specification)：認証・フレンド・ワールド取得

参考アプリのコード・画像は転載していません。VRChatの非公式アプリです。
