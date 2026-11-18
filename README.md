# Orbit — VRChat Tools for Android

VRChatのフレンドとワールドを身近にする、Kotlin / Jetpack Compose製の非公式Androidアプリです。Material 3 Expressiveのテーマとモーションを使用しています。

## プレビュー

<img src="docs/screenshots/home.png" alt="Orbitのホーム画面（デモモード）" width="280" />
<img src="docs/screenshots/instances.png" alt="同じインスタンスにいるフレンドをまとめたカード" width="280" />

## 使える機能

- **ホーム**：オンライン人数、フレンドの滞在インスタンス、ワールド、お気に入り
- **フレンド**：個人表示／インスタンス別表示、名前・現在地の検索、オンライン／参加歓迎／お気に入りでの絞り込み、トラストランクに応じた控えめな背景色、詳細表示
- **ワールド**：検索、詳細表示、端末内のブックマーク、VRChatのWebページを開く
- **アクティビティ**：手動更新時に検出したオンライン状態と現在地の変化を端末内に最大100件保存
- **設定**：ライト／ダーク／システム、Android 12以降のMaterial You動的カラー、現在の配色プレビュー
- **ログイン**：ユーザー名とパスワード、TOTP／メールOTP／リカバリーコードによる2段階認証、セッション復元、ログアウト

初回起動は**デモモード**です。デモのフレンド・ワールドは架空で、ワールド画像はCompose Canvasによるイラストです。ホームまたは設定からログインするとVRChat APIのデータに切り替わります。フレンドとログ画面の更新ボタンで最新情報を取得します。

お気に入りはOrbit専用で、VRChatのお気に入りとは同期しません。履歴は更新ごとの差分であり、閉じている間の全イベントやPCのプレイ時間は取得しません。アバター管理・招待の送信・WebSocket常時接続・PCのローカルログ連携は未実装です。プライベートな現在地は公開せず、そのまま非公開として表示します。

## フレンドのアイコンとワールド画像

ログイン後はVRChat APIの画像URLを使って、フレンドのアイコン、ワールド一覧・詳細・インスタンスカードのサムネイルを読み込みます。画像リクエストにもAPIと同じセッションとUser-Agentを使います。CDNへのリダイレクトや外部画像ホストには認証Cookieを送信しません。

- フレンド：`userIcon` → `iconUrl` → `profilePicOverride` → アバターのサムネイル → アバターの元画像の順に選択
- ワールド：`thumbnailImageUrl` → `imageUrl` の順に選択
- 未設定・JSONのnull・不正なURLは除外し、読み込めないURLは次の候補へ切り替え
- 読み込み中は代替表示と進捗表示、全候補で失敗した場合は再読み込みボタンを表示
- 画像がないフレンドにはイニシャル、実ワールドには汎用アイコンを表示。デモ用イラストはデモのワールドだけに使用
- Coilの共通ImageLoaderで非同期取得・メモリ／ディスクキャッシュを利用。画像キャッシュは端末内のアプリ用キャッシュ領域に保存

デモのフレンドとワールドには実在の画像URLを設定していません。実アカウントによる画像取得の確認は別途必要です。

## Material Youとトラストランクの色

Android 12以降では、初期状態で壁紙由来のMaterial Youカラーを使います。設定の「壁紙の色を使う」で切り替えられ、ライト／ダーク／システムの各モードに対応します。明示的に保存したオン／オフ設定は引き継ぎます。配色変更中も表示画面と設定のスクロール位置を維持します。Android 11以前はOrbit標準の配色を使います。

フレンド画面の「個人表示」では、テーマの背景へランク色を薄く重ね、ランク名も表示します。

| ランク | 色 | VRChat APIタグ |
| --- | --- | --- |
| Trusted | 紫 | `system_trust_veteran` |
| Known | オレンジ | `system_trust_trusted` |
| User | 緑 | `system_trust_known` |
| New User | 青 | `system_trust_basic` |
| Visitor | 灰色 | ランクタグのない有効なタグ一覧 |

<img src="docs/screenshots/friends-ranks-light.png" alt="ライトテーマのトラストランク別背景色" width="280" />
<img src="docs/screenshots/friends-ranks-dark.png" alt="ダークテーマのトラストランク別背景色" width="280" />
<img src="docs/screenshots/material-you-settings.png" alt="Material Youの設定と配色プレビュー" width="280" />

ランク情報を取得できない場合は「ランク不明」とし、通常の背景色にします。背景色はライトで8%、ダークで10%の薄い重ね色で、名前・現在地の文字色はテーマに従います。オンライン状況のドットとは色の役割を分けています。

## インスタンス別表示

ホームの「フレンドのいる場所」に、フレンドが滞在するインスタンスをカードで表示します。フレンド画面でも「インスタンス別」に切り替えられます。

- ワールド画像・名前、インスタンス番号、公開範囲、リージョン、フレンドのアバターを表示
- ワールドIDだけでなく、インスタンス番号・所有者・nonceなどを含めてグループ化。同じワールドの別インスタンスは別カードに表示
- フレンドの多い順に並べ、カードからインスタンス詳細・フレンド詳細・ワールド詳細を開く
- 「フレンド人数」とAPIから取得した「そのインスタンスの滞在人数／定員」を区別。ワールド全体の人数をインスタンス人数として表示しない
- 非公開・移動中・Web／アプリ・現在地不明・オフラインを分けて表示。現在地が `private` のフレンド同士を、同じ部屋にいると推測しない
- 詳細の取得に失敗してもフレンドを表示し、インスタンス詳細から再試行可能
- 実データの詳細画面からVRChatのインスタンスURLを開く。参加権限や空き状況はVRChat側の判断に従う

デモでは「こもれびの庭 #789」にしろとsoraが同席し、「雲の上の喫茶店」は #123 と #456 の2部屋に分かれています。

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
- フレンド検索・状態判定5件、インスタンスの識別・グルーピング6件、トラストランク判定4件、画像URL選択3件、認証Cookieのホスト制限2件のユニットテスト成功（テンプレートのテストを含めると21件）
- Android Lint：エラー0件。依存関係の更新案・KTXの利用案などの警告は残っています。
- Android 17エミュレーターでホームとインスタンスカードの実表示を確認し、上のスクリーンショットを撮影
- Android 17の独立したテスト用エミュレーターで端末テスト9件成功：個人表示の検索・詳細遷移、インスタンス別表示、動的テーマの保持、APIレスポンスのランク・画像URL解析、画像の実取得と再試行、アプリコンテキスト。テストAPKは `cmd package compile -m speed -f` で事前コンパイルして実行しました。
- ローカルHTTPSサーバーとテスト用認証Cookieで、保護画像のPNG取得・デコード、404から代替画像への切り替え、CDN転送時のCookie非送信、503からの手動再読み込みを確認。実アカウントの画像取得とは別の検証です。
- Material Youのライト／ダーク両方で、ランク別背景色と設定画面の表示を確認
- 実アカウントのログイン・2段階認証・API応答は未検証

## デザインと実装の参考

- [vrc-tool](https://github.com/yamato3010/vrc-tool)：ホームのインスタンス単位のカード表示
- [Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)：形、色、タイポグラフィ、モーション
- [ArchiveTune](https://github.com/rukamori/ArchiveTune)：大きな見出し、丸みのある操作部、カードの構成
- [Petal](https://github.com/shreyagarwal72/petal)：柔らかな色調とExpressiveな形
- [VRChatユーザータグ](https://vrchat.community/tags/user)：トラストランクのタグ対応
- [VRChat API仕様](https://github.com/vrchatapi/specification)：認証・フレンド・ワールド取得

参考アプリのコード・画像は転載していません。VRChatの非公式アプリです。
