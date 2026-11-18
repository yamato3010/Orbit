package com.yamato3010.vrchattools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest

val LocalOrbitImageLoader = staticCompositionLocalOf<ImageLoader> { error("Orbit image loader is missing") }
private enum class ImageState { Empty, Loading, Ready, Failed }

/** 優先するアイコンやサムネイルを先に試し、続いて API が返した残りの画像 URL を試す。 */
@Composable
fun RemoteImage(
    urls: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    placeholder: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val loader = LocalOrbitImageLoader.current
    var candidate by remember(urls) { mutableIntStateOf(0) }
    var attempt by remember(urls) { mutableIntStateOf(0) }
    var state by remember(urls, attempt, candidate) { mutableStateOf(if (urls.isEmpty()) ImageState.Empty else ImageState.Loading) }
    val currentCandidate = candidate
    val currentAttempt = attempt
    Box(modifier.semantics {
        stateDescription = when (state) {
            ImageState.Loading -> "画像を読み込み中"
            ImageState.Failed -> "画像を読み込めませんでした"
            ImageState.Empty -> "画像なし"
            ImageState.Ready -> "画像を表示中"
        }
    }, contentAlignment = Alignment.Center) {
        if (state != ImageState.Ready) placeholder()
        urls.getOrNull(candidate)?.let { url ->
            val request = remember(context, url, attempt) { ImageRequest.Builder(context).data(url).build() }
            key(url, attempt) {
                AsyncImage(
                    model = request,
                    imageLoader = loader,
                    contentDescription = if (state == ImageState.Ready) description else null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onLoading = { if (candidate == currentCandidate && attempt == currentAttempt) state = ImageState.Loading },
                    onSuccess = { if (candidate == currentCandidate && attempt == currentAttempt) state = ImageState.Ready },
                    onError = {
                        if (candidate == currentCandidate && attempt == currentAttempt) {
                            if (candidate < urls.lastIndex) candidate++ else state = ImageState.Failed
                        }
                    }
                )
            }
        }
        if (state == ImageState.Loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        if (state == ImageState.Failed) IconButton(
            onClick = { candidate = 0; attempt++ },
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .92f), CircleShape)
        ) { Icon(Icons.Rounded.Refresh, "$description を再読み込み", Modifier.size(17.dp)) }
    }
}
