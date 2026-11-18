package com.yamato3010.vrchattools.data

internal enum class InstanceResource(val label: String) { World("ワールド"), Instance("インスタンス") }

/** セッション切れ、アクセスできないルーム、通信エラーは区別して表示する。 */
internal fun instanceLoadError(resource: InstanceResource, error: Exception?): String {
    val label = resource.label
    return when {
        error == null || (error is ApiException && error.code == 404) ->
            "${label}情報が見つかりません。フレンド情報を更新して再確認してください。"
        error is ApiException -> when (error.code) {
            401 -> "${label}情報の認証に失敗しました（HTTP 401）。設定からログインし直してください。"
            403 -> "${label}情報へのアクセスが許可されていません（HTTP 403）。"
            429 -> "${label}情報の取得が一時的に制限されています（HTTP 429）。少し待ってから再試行してください。"
            else -> "${label}情報を取得できませんでした（HTTP ${error.code}）。"
        }
        else -> "${label}情報の取得に失敗しました。接続を確認して再試行してください。"
    }
}
