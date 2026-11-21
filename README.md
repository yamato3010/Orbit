# Orbit — VRChat Tools for Android

Orbitは、VRChatのフレンドがどこにいるかを確認したり、気になるワールドを探したりできる非公式Androidアプリです。KotlinとJetpack Composeで開発しています。

<img src="docs/screenshots/home.png" alt="Orbitのダッシュボード" width="240" /> <img src="docs/screenshots/instances.png" alt="インスタンス別にまとまったフレンド一覧" width="240" />

## 入手方法

Android 7.0以降に対応しています。[GitHub Releases](https://github.com/yamato3010/Orbit/releases) に公開された最新版から `Orbit-<versionCode>.apk` をダウンロードしてインストールできます。[ソースからのビルド手順](#ビルド)もあります。

配布版をインストールした後は、アプリの「設定 → アプリの更新 → 更新を確認」から更新できます。Android 8以降で初めてOrbitから更新する際は、Androidの設定でOrbitからのインストールを許可し、Orbitに戻って「ダウンロードしてインストール」を押してください。最後にAndroidの画面でインストールを確認します。

## 主な機能

- フレンドのオンライン状況と現在地を確認し、同じインスタンスにいるフレンドをまとめて表示
- ワールドを検索し、気になるワールドを端末にブックマーク
- アプリ内で通知を確認し、招待や招待リクエストを送信
- 自分のステータスを変更し、よく使う設定をプリセットに保存
- フレンドのオンライン状態や現在地の変化をアクティビティで確認

## 使い始める

初回起動時のセットアップ画面で「VRChatにログイン」または「デモで始める」を選べます。デモではサンプルデータで操作を試せます。あとからホームまたは設定でログインできます。

フレンドの状態や通知はアプリを表示している間に更新されます。通知はアプリ内で確認できます。

## ログインとデータの扱い

VRChat APIにHTTPSで直接接続します。パスワードと2段階認証コードは永続保存しません。ログイン状態を維持する認証Cookieは、Android Keystoreの鍵で暗号化して端末に保存し、ログアウト時に削除します。

お気に入り、ワールドのブックマーク、アクティビティ履歴はアカウントごとに端末内に保存され、ログアウト後も残ります。

## ビルド

JDK 25とAndroid SDK 37を用意し、リポジトリのルートで実行します。

```sh
./gradlew :app:assembleDebug
```

APKは `app/build/outputs/apk/debug/app-debug.apk` に生成されます。

コミュニティが文書化したVRChat APIを使用しています。実アカウントでのログインとAPI通信は未検証です。[VRChat API仕様](https://github.com/vrchatapi/specification)

## ライセンス

OrbitのソースコードはMITライセンスで公開します。[ライセンス全文](LICENSE)
