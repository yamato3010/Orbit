package com.yamato3010.vrchattools.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.yamato3010.vrchattools.data.*
import kotlin.math.*

@Composable
fun Presence.color(): Color = when (this) {
    Presence.JoinMe -> Color(0xFF43A7DB)
    Presence.Online -> Color(0xFF54A879)
    Presence.AskMe -> Color(0xFFD29337)
    Presence.Busy -> Color(0xFFD96C81)
    Presence.Offline -> MaterialTheme.colorScheme.outline
}

private val avatarColors = listOf(0xFFEAD8F5, 0xFFF9DCC7, 0xFFD3E8E2, 0xFFD6E2FA, 0xFFF4D9E5, 0xFFE6E7C5)
@Composable
fun Avatar(friend: Friend, size: Dp = 52.dp, showStatus: Boolean = true) {
    val index = (friend.id.hashCode() and Int.MAX_VALUE) % avatarColors.size
    Box(Modifier.size(size)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(size * .36f)).background(Color(avatarColors[index])), contentAlignment = Alignment.Center) {
            if (friend.image.isNotBlank()) AsyncImage(friend.image, friend.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else {
                Canvas(Modifier.fillMaxSize()) {
                    val ink = Color(0xFF665779).copy(alpha = .18f)
                    drawCircle(ink, this.size.width * .23f, androidx.compose.ui.geometry.Offset(this.size.width * .5f, this.size.height * .43f))
                    drawOval(ink, androidx.compose.ui.geometry.Offset(this.size.width * .16f, this.size.height * .65f), androidx.compose.ui.geometry.Size(this.size.width * .68f, this.size.height * .55f))
                }
                Text(friend.name.take(1), fontSize = (size.value * .33f).sp, color = Color(0xFF584267), fontWeight = FontWeight.Bold)
            }
        }
        if (showStatus) Box(Modifier.align(Alignment.BottomEnd).size(15.dp).background(MaterialTheme.colorScheme.surface, CircleShape).padding(3.dp).background(friend.status.color(), CircleShape))
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (action != null) TextButton(onClick = onAction, contentPadding = PaddingValues(start = 12.dp)) {
            Text(action, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TrustRank.accentColor(): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (this) {
        TrustRank.Trusted -> if (dark) Color(0xFFD2ACFF) else Color(0xFF7442AE)
        TrustRank.Known -> if (dark) Color(0xFFFFC178) else Color(0xFFA85B00)
        TrustRank.User -> if (dark) Color(0xFF8BD6AE) else Color(0xFF237454)
        TrustRank.NewUser -> if (dark) Color(0xFF9DCAFF) else Color(0xFF2161A8)
        TrustRank.Visitor -> if (dark) Color(0xFFB9BDC8) else Color(0xFF656973)
        TrustRank.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun FriendRow(friend: Friend, saved: Boolean, onClick: () -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier, showTrustRank: Boolean = false) {
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    val rankColor = friend.trustRank.accentColor()
    val background = if (showTrustRank && friend.trustRank != TrustRank.Unknown) {
        rankColor.copy(alpha = if (base.luminance() < 0.5f) 0.10f else 0.08f).compositeOver(base)
    } else base
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(background)
        .clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Avatar(friend)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(friend.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                if (showTrustRank) Text(friend.trustRank.label, style = MaterialTheme.typography.labelSmall, color = rankColor, maxLines = 1)
                if (friend.status == Presence.JoinMe) Icon(Icons.Rounded.WavingHand, "参加歓迎", Modifier.size(15.dp), tint = friend.status.color())
            }
            Text(friend.locationLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onSave) { Icon(if (saved) Icons.Rounded.Star else Icons.Rounded.StarOutline, if (saved) "お気に入りを解除" else "お気に入りに追加", tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) }
    }
}

/** サンプルワールドにはオフライン用のイラストを、実際のワールドには VRChat のサムネイルを使う。 */
@Composable
fun WorldArt(world: World, modifier: Modifier = Modifier) {
    val palettes = listOf(
        listOf(Color(0xFFB8A9DE), Color(0xFFEFD9E5), Color(0xFFFCEDDA)),
        listOf(Color(0xFF212C57), Color(0xFF79689B), Color(0xFFBCA4C5)),
        listOf(Color(0xFF76A599), Color(0xFFB8D6B0), Color(0xFFE9EAC6)),
        listOf(Color(0xFFD9A6BE), Color(0xFFF1CFD6), Color(0xFFFCE5CD)),
        listOf(Color(0xFF6D9EBA), Color(0xFFABC9D9), Color(0xFFE2DBE7))
    )
    val colors = palettes[world.palette % palettes.size]
    Box(modifier.background(colors[0])) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRect(Brush.verticalGradient(colors))
            drawCircle(Color(0xFFFFF5DC).copy(alpha = .88f), w * .095f, androidx.compose.ui.geometry.Offset(w * .76f, h * .29f))
            if (world.palette == 1) {
                for (i in 0..16) {
                    val x = w * i / 16f
                    val bh = h * (.14f + ((i * 7) % 9) * .035f)
                    drawRect(Color(0xFF343753).copy(alpha = .85f), androidx.compose.ui.geometry.Offset(x, h - bh), androidx.compose.ui.geometry.Size(w / 15f, bh))
                    for (j in 1..3) drawRect(Color(0xFFF1D3AD).copy(alpha = .55f), androidx.compose.ui.geometry.Offset(x + 5, h - bh + j * 14), androidx.compose.ui.geometry.Size(3f, 5f))
                }
            } else {
                val hill = Path().apply { moveTo(0f, h * .72f); cubicTo(w * .2f, h * .3f, w * .4f, h * .95f, w * .67f, h * .65f); cubicTo(w * .8f, h * .47f, w * .95f, h * .5f, w, h * .63f); lineTo(w, h); lineTo(0f, h); close() }
                drawPath(hill, colors[0].copy(alpha = .38f))
                val hill2 = Path().apply { moveTo(0f, h * .87f); cubicTo(w * .3f, h * .68f, w * .5f, h * .96f, w, h * .75f); lineTo(w, h); lineTo(0f, h); close() }
                drawPath(hill2, colors[0].copy(alpha = .42f))
                for (i in 0..3) {
                    val cx = w * (.12f + i * .27f); val cy = h * (.32f + (i % 2) * .15f)
                    drawOval(Color.White.copy(alpha = .4f), androidx.compose.ui.geometry.Offset(cx - w * .1f, cy), androidx.compose.ui.geometry.Size(w * .29f, h * .1f))
                }
                if (world.palette == 0) {
                    drawRoundRect(Color(0xFFFFF4E9), androidx.compose.ui.geometry.Offset(w * .38f, h * .54f), androidx.compose.ui.geometry.Size(w * .26f, h * .28f), androidx.compose.ui.geometry.CornerRadius(8f))
                    val roof = Path().apply { moveTo(w * .34f, h * .55f); lineTo(w * .51f, h * .35f); lineTo(w * .68f, h * .55f); close() }
                    drawPath(roof, Color(0xFF8C779C))
                    drawRect(Color(0xFFB6A4CA), androidx.compose.ui.geometry.Offset(w * .47f, h * .65f), androidx.compose.ui.geometry.Size(w * .07f, h * .17f))
                }
            }
        }
        if (world.image.isNotBlank()) AsyncImage(world.image, world.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun WorldCard(world: World, saved: Boolean, onClick: () -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick)) {
        Box {
            WorldArt(world, Modifier.fillMaxWidth().height(132.dp))
            IconButton(onClick = onSave, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(36.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = .9f), CircleShape)) {
                Icon(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (saved) "保存を解除" else "ワールドを保存", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(world.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("by ${world.author}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.People, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(" ${world.occupants}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun OrbitFlower(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondaryContainer
    Canvas(modifier) {
        val w = size.width; val h = size.height
        rotate(-18f) {
            for (i in 0 until 8) rotate(i * 45f) {
                drawOval(primary.copy(alpha = .2f), androidx.compose.ui.geometry.Offset(w * .34f, h * .01f), androidx.compose.ui.geometry.Size(w * .32f, h * .62f))
            }
        }
        drawCircle(secondary, w * .24f)
        drawCircle(primary, w * .028f, androidx.compose.ui.geometry.Offset(w * .43f, h * .47f))
        drawCircle(primary, w * .028f, androidx.compose.ui.geometry.Offset(w * .57f, h * .47f))
        drawArc(primary, 25f, 130f, false, androidx.compose.ui.geometry.Offset(w * .43f, h * .50f), androidx.compose.ui.geometry.Size(w * .14f, h * .10f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * .018f, cap = StrokeCap.Round))
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, icon: ImageVector = Icons.Outlined.SearchOff) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

fun relativeTime(time: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - time) / 60_000).coerceAtLeast(0)
    return when { minutes < 1 -> "たった今"; minutes < 60 -> "${minutes}分前"; minutes < 1440 -> "${minutes / 60}時間前"; else -> "${minutes / 1440}日前" }
}
