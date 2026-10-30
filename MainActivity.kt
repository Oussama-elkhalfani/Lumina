package com.lumina.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherApp() }
    }
}

@Composable
fun LauncherApp() {
    val ctx = LocalContext.current
    val history = remember { History(ctx) }
    val apps by produceState(initialValue = emptyList<AppInfo>()) {
        value = withContext(Dispatchers.IO) { loadApps(ctx) }
    }
    var content by remember { mutableStateOf(Sample.content) }
    var tab by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Item?>(null) }

    LaunchedEffect(Unit) { content = Repo.loadAll() }
    BackHandler { if (detail != null) detail = null else tab = 0 }

    val all = remember(content) {
        (content.trending + content.movies + content.series + content.topMovies + content.topSeries).distinctBy { it.id }
    }
    val play: (Item) -> Unit = { history.play(it.id); openTrailer(ctx, it) }

    Box(Modifier.fillMaxSize().background(Pal.Bg)) {
        Box(Modifier.fillMaxSize().focusProperties { canFocus = detail == null }) {
            Crossfade(tab, animationSpec = tween(350), label = "tab") { t ->
                when (t) {
                    0 -> {
                        val cont = history.order.mapNotNull { id -> all.firstOrNull { it.id == id } }
                        Browse(
                            hero = content.trending.first(),
                            shelves = listOf(
                                Shelf("Continue Watching", cont, landscape = true, progress = true),
                                Shelf("Recommended For You", content.trending),
                                Shelf("Popular Series", content.series, landscape = true)
                            ),
                            apps = apps, history = history, onPlay = play, onInfo = { detail = it }
                        )
                    }
                    1 -> Browse(
                        hero = content.movies.first(),
                        shelves = listOf(
                            Shelf("Popular Movies", content.movies, landscape = true),
                            Shelf("Top Rated", content.topMovies)
                        ),
                        apps = emptyList(), history = history, onPlay = play, onInfo = { detail = it }
                    )
                    2 -> Browse(
                        hero = content.series.first(),
                        shelves = listOf(
                            Shelf("Popular Series", content.series, landscape = true),
                            Shelf("Top Rated", content.topSeries)
                        ),
                        apps = emptyList(), history = history, onPlay = play, onInfo = { detail = it }
                    )
                    3 -> AppsScreen(apps) { launchApp(ctx, it.pkg) }
                    else -> SettingsScreen()
                }
            }
            Box(
                Modifier.fillMaxWidth().height(96.dp)
                    .background(Brush.verticalGradient(listOf(Pal.Bg.copy(alpha = 0.75f), Color.Transparent)))
            )
            TopNav(tab, onTab = { tab = it }, onSearch = { openSearch(ctx) })
        }
        detail?.let { d -> Detail(d, onPlay = { play(d) }, onClose = { detail = null }) }
    }
}

@Composable
fun Browse(
    hero: Item,
    shelves: List<Shelf>,
    apps: List<AppInfo>,
    history: History,
    onPlay: (Item) -> Unit,
    onInfo: (Item) -> Unit
) {
    val ctx = LocalContext.current
    var featured by remember { mutableStateOf(hero) }

    Box(Modifier.fillMaxSize()) {
        Crossfade(featured, animationSpec = tween(600), label = "bg") { Backdrop(it) }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Pal.Bg.copy(alpha = 0.92f), Pal.Bg.copy(alpha = 0.5f), Color.Transparent))
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 0.7f to Pal.Bg.copy(alpha = 0.92f), 1f to Pal.Bg)
            )
        )
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(64.dp))
            Column(
                Modifier.padding(start = 48.dp).height(158.dp).width(470.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(Modifier.height(98.dp), contentAlignment = Alignment.BottomStart) {
                    Crossfade(featured, animationSpec = tween(500), label = "info") { it ->
                        Column {
                            Text(
                                listOf(it.genre, it.year).filter { s -> s.isNotBlank() }.joinToString("  ·  "),
                                color = Pal.Sub, fontSize = 12.sp, fontWeight = FontWeight.Medium
                            )
                            Text(it.title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(it.overview, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    TvButton("Play", true) { onPlay(featured) }
                    Spacer(Modifier.width(10.dp))
                    TvButton("More Info", false) { onInfo(featured) }
                }
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(shelves.filter { it.items.isNotEmpty() }, key = { it.title }) { s ->
                    ShelfRow(s, history, onFocus = { featured = it }, onPlay = onPlay)
                }
                if (apps.isNotEmpty()) {
                    item(key = "apps") { AppsRow(apps) { launchApp(ctx, it.pkg) } }
                }
            }
        }
    }
}

@Composable
fun AppsScreen(apps: List<AppInfo>, onLaunch: (AppInfo) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 96.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(apps, key = { it.pkg }) { app -> AppTile(app) { onLaunch(app) } }
        }
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(top = 96.dp)) {
        Text("Settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 48.dp))
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(SETTINGS_TILES, key = { it.first }) { (label, action) ->
                SettingsTile(label) { openSettings(ctx, action) }
            }
        }
    }
}

@Composable
fun Detail(item: Item, onPlay: () -> Unit, onClose: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Box(Modifier.fillMaxSize()) {
        Backdrop(item)
        Box(Modifier.fillMaxSize().background(Pal.Bg.copy(alpha = 0.88f)))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 72.dp).width(520.dp)) {
            Text(
                listOf(item.genre, item.year).filter { it.isNotBlank() }.joinToString("  ·  "),
                color = Pal.Sub, fontSize = 14.sp, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(item.title, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(item.overview, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, lineHeight = 23.sp)
            Spacer(Modifier.height(26.dp))
            Row {
                Box(Modifier.focusRequester(fr)) { TvButton("Play", true, onPlay) }
                Spacer(Modifier.width(12.dp))
                TvButton("Close", false, onClose)
            }
        }
    }
}
