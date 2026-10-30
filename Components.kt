package com.lumina.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- models & helpers ----------

data class Shelf(
    val title: String,
    val items: List<Item>,
    val landscape: Boolean = false,
    val progress: Boolean = false
)

data class AppInfo(val label: String, val pkg: String, val icon: ImageBitmap?)

class History(ctx: Context) {
    private val p = ctx.getSharedPreferences("lumina", Context.MODE_PRIVATE)
    val map = mutableStateMapOf<Int, Float>()
    val order = mutableStateListOf<Int>()

    init {
        p.getString("recent", "")!!.split(",").mapNotNull { it.toIntOrNull() }.forEach { id ->
            order.add(id)
            map[id] = p.getFloat("p_$id", 0.1f)
        }
    }

    fun play(id: Int) {
        map[id] = ((map[id] ?: 0f) + 0.15f).coerceAtMost(0.95f)
        order.remove(id)
        order.add(0, id)
        while (order.size > 12) order.removeAt(order.size - 1)
        p.edit().putFloat("p_$id", map[id]!!).putString("recent", order.joinToString(",")).apply()
    }
}

fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val tv = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
    val std = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
    return (tv + std)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            AppInfo(
                label = it.loadLabel(pm).toString(),
                pkg = it.activityInfo.packageName,
                icon = runCatching { it.loadIcon(pm).toBitmap(160, 160).asImageBitmap() }.getOrNull()
            )
        }
        .sortedBy { it.label.lowercase() }
}

fun launchApp(ctx: Context, pkg: String) {
    val pm = ctx.packageManager
    val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return
    runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun openSettings(ctx: Context, action: String) {
    runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun openTrailer(ctx: Context, item: Item) {
    val q = Uri.encode("${item.title} ${item.year} trailer")
    val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$q"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(i) }
}

fun openSearch(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent("android.search.action.GLOBAL_SEARCH").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun Modifier.tvClickable(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

// ---------- top navigation ----------

val TABS = listOf("Home", "Movies", "Series", "Apps", "Settings")

@Composable
fun TopNav(tab: Int, onTab: (Int) -> Unit, onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Pal.Accent))
        Spacer(Modifier.width(8.dp))
        Text("Lumina", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(Modifier.width(36.dp))
        TABS.forEachIndexed { i, label -> NavItem(label, tab == i) { onTab(i) } }
        Spacer(Modifier.weight(1f))
        IconBtn(onSearch) {
            Canvas(Modifier.size(18.dp)) {
                val sw = 2.dp.toPx()
                drawCircle(
                    Color.White, radius = size.minDimension * 0.32f,
                    center = Offset(size.width * 0.42f, size.height * 0.42f), style = Stroke(sw)
                )
                drawLine(
                    Color.White, Offset(size.width * 0.66f, size.height * 0.66f),
                    Offset(size.width * 0.92f, size.height * 0.92f), strokeWidth = sw, cap = StrokeCap.Round
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Clock()
        Spacer(Modifier.width(14.dp))
        IconBtn({}) {
            Box(
                Modifier.size(20.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Pal.Accent, Color(0xFF5E5CE6))))
            )
        }
    }
}

@Composable
fun NavItem(label: String, selected: Boolean, onFocus: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (selected || f) 1f else 0.62f, tween(200), label = "navAlpha")
    val w by animateDpAsState(if (selected) 18.dp else 0.dp, tween(220), label = "navBar")
    Column(
        Modifier
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .clip(RoundedCornerShape(50))
            .background(if (f) Color.White.copy(alpha = 0.14f) else Color.Transparent)
            .tvClickable(onFocus)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label, color = Color.White.copy(alpha = alpha), fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
        Spacer(Modifier.height(3.dp))
        Box(Modifier.height(2.dp).width(w).clip(RoundedCornerShape(2.dp)).background(Pal.Accent))
    }
}

@Composable
fun IconBtn(onClick: () -> Unit, content: @Composable () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (f) 1.12f else 1f, tween(150), label = "iconScale")
    Box(
        Modifier.size(34.dp)
            .onFocusChanged { f = it.isFocused }
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (f) 0.24f else 0.08f))
            .tvClickable(onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun Clock() {
    val t by produceState(initialValue = "") {
        while (true) {
            value = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            delay(20_000)
        }
    }
    Text(t, color = Pal.Sub, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

// ---------- buttons & cards ----------

@Composable
fun TvButton(label: String, primary: Boolean, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (f) 1.07f else 1f, tween(160), label = "btnScale")
    val shape = RoundedCornerShape(50)
    val bg = when {
        primary && f -> Color.White
        primary -> Color.White.copy(alpha = 0.86f)
        f -> Color.White.copy(alpha = 0.28f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    Box(
        Modifier
            .onFocusChanged { f = it.isFocused }
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(shape)
            .background(bg)
            .then(if (f) Modifier.border(2.dp, Pal.Accent, shape) else Modifier)
            .tvClickable(onClick)
            .padding(horizontal = 24.dp, vertical = 9.dp)
    ) {
        Text(label, color = if (primary) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Backdrop(item: Item) {
    Box(Modifier.fillMaxSize()) {
        if (item.backdrop != null) {
            AsyncImage(
                model = "https://image.tmdb.org/t/p/w1280${item.backdrop}",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize().background(gradientFor(item.id)))
        }
    }
}

@Composable
fun MediaCard(item: Item, landscape: Boolean, progress: Float?, onFocus: () -> Unit, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (f) 1.09f else 1f, tween(180), label = "cardScale")
    val cardShape = RoundedCornerShape(12.dp)
    val w = if (landscape) 188.dp else 104.dp
    val h = if (landscape) 106.dp else 156.dp
    val url = if (landscape) item.backdrop?.let { "https://image.tmdb.org/t/p/w780$it" }
    else item.poster?.let { "https://image.tmdb.org/t/p/w500$it" }

    Box(
        Modifier.size(w, h)
            .zIndex(if (f) 1f else 0f)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .graphicsLayer {
                scaleX = s; scaleY = s
                shadowElevation = if (f) 30f else 0f
                shape = cardShape
                clip = true
            }
            .then(if (f) Modifier.border(2.dp, Color.White.copy(alpha = 0.92f), cardShape) else Modifier)
            .background(Pal.Surface, cardShape)
            .tvClickable(onClick)
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(gradientFor(item.id)).padding(10.dp), contentAlignment = Alignment.Center) {
                Text(item.title, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (progress != null) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                    .height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.3f))
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Pal.Accent))
            }
        }
    }
}

@Composable
fun ShelfRow(s: Shelf, history: History, onFocus: (Item) -> Unit, onPlay: (Item) -> Unit) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(
            s.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 48.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(s.items.distinctBy { it.id }, key = { it.id }) { item ->
                MediaCard(
                    item, s.landscape,
                    if (s.progress) history.map[item.id] else null,
                    onFocus = { onFocus(item) },
                    onClick = { onPlay(item) }
                )
            }
        }
    }
}

// ---------- apps ----------

@Composable
fun AppTile(app: AppInfo, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (f) 1.12f else 1f, tween(170), label = "appScale")
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(88.dp)
            .onFocusChanged { f = it.isFocused }
            .graphicsLayer { scaleX = s; scaleY = s }
            .tvClickable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(72.dp).clip(shape).background(Pal.Surface)
                .then(if (f) Modifier.border(2.dp, Color.White.copy(alpha = 0.92f), shape) else Modifier.border(1.dp, Pal.Border, shape)),
            contentAlignment = Alignment.Center
        ) {
            app.icon?.let { Image(it, contentDescription = app.label, modifier = Modifier.size(46.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            app.label, color = if (f) Color.White else Pal.Sub, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AppsRow(apps: List<AppInfo>, onLaunch: (AppInfo) -> Unit) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text("Your Apps", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 48.dp))
        LazyRow(
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(apps, key = { it.pkg }) { app -> AppTile(app) { onLaunch(app) } }
        }
    }
}

@Composable
fun SettingsTile(label: String, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (f) 1.07f else 1f, tween(170), label = "setScale")
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(168.dp, 84.dp)
            .onFocusChanged { f = it.isFocused }
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(shape)
            .background(Color.White.copy(alpha = if (f) 0.16f else 0.07f))
            .then(if (f) Modifier.border(2.dp, Color.White.copy(alpha = 0.92f), shape) else Modifier.border(1.dp, Pal.Border, shape))
            .tvClickable(onClick)
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

val SETTINGS_TILES = listOf(
    "Network" to Settings.ACTION_WIFI_SETTINGS,
    "Display & Sound" to Settings.ACTION_DISPLAY_SETTINGS,
    "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
    "Apps" to Settings.ACTION_APPLICATION_SETTINGS,
    "Default Home" to Settings.ACTION_HOME_SETTINGS,
    "All Settings" to Settings.ACTION_SETTINGS
)
