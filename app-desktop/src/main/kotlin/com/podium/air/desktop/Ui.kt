// SPDX-License-Identifier: GPL-3.0-only
// Desktop adaptation of Podium Air's Home / Library / Player layout and Theme.kt palette.
package com.podium.air.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.Song
import com.podium.air.domain.RepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Desktop
import java.io.File
import java.net.URI

val AccentRed = Color(0xFFFA2D48)
private val Dark = darkColorScheme(primary = Color.White, onPrimary = Color.Black, background = Color.Black,
    onBackground = Color.White, surface = Color(0xFF0D0D0F), onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E), onSurfaceVariant = Color(0xFF8E8E93), outline = Color(0xFF2C2C2E))
private val Light = lightColorScheme(primary = Color.Black, onPrimary = Color.White, background = Color.White,
    onBackground = Color.Black, surface = Color(0xFFF7F7F9), onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F7), onSurfaceVariant = Color(0xFF6E6E73), outline = Color(0xFFE5E5EA))
private val Typography = Typography(
    displayLarge = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-0.8).sp),
    headlineLarge = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.7).sp),
    headlineMedium = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.4).sp),
    titleLarge = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp), bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
)
private enum class Page(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home), EXPLORE("Explore", Icons.Rounded.Explore), SEARCH("Search", Icons.Rounded.Search),
    LIBRARY("Library", Icons.Rounded.LibraryMusic), PLAYLISTS("Playlists", Icons.Rounded.QueueMusic),
    ALBUMS("Albums", Icons.Rounded.Album), ARTISTS("Artists", Icons.Rounded.Person), HISTORY("History", Icons.Rounded.History),
    PLAYER("Now Playing", Icons.Rounded.MusicNote), QUEUE("Queue", Icons.Rounded.PlaylistPlay), LYRICS("Lyrics", Icons.Rounded.Lyrics),
    ACCOUNT("Account", Icons.Rounded.AccountCircle), SETTINGS("Settings", Icons.Rounded.Settings), ABOUT("About", Icons.Rounded.Info),
}
private data class Route(val page: Page = Page.HOME, val detail: String? = null, val title: String = page.title)

@Composable
fun PodiumApp(model: DesktopModel, filePicker: (Boolean) -> Unit, smoke: Boolean = false, onReady: () -> Unit = {}) {
    val state by model.state.collectAsState()
    val audio by model.engine.state.collectAsState()
    val message by model.message.collectAsState()
    val importing by model.importing.collectAsState()
    var route by remember { mutableStateOf(Route()) }
    var search by remember { mutableStateOf("") }
    var createName by remember { mutableStateOf<String?>(null) }
    val colors = if (state.preferences.dark) Dark else Light
    MaterialTheme(colorScheme = colors, typography = Typography) {
        Surface(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                event.isCtrlPressed && event.key == Key.O -> { filePicker(event.isShiftPressed); true }
                event.isCtrlPressed && event.key == Key.F -> { route = Route(Page.SEARCH); true }
                event.isCtrlPressed && event.key == Key.Spacebar -> { model.toggle(); true }
                event.isCtrlPressed && event.key == Key.DirectionRight -> { model.next(); true }
                event.isCtrlPressed && event.key == Key.DirectionLeft -> { model.previous(); true }
                event.isAltPressed && event.key == Key.DirectionRight -> { model.seek(audio.positionMs + 10000); true }
                event.isAltPressed && event.key == Key.DirectionLeft -> { model.seek(audio.positionMs - 10000); true }
                event.key == Key.Escape && route.detail != null -> { route = Route(route.page); true }
                else -> false
            }
        }) {
            Column {
                Row(Modifier.weight(1f)) {
                    BoxWithConstraints {
                        // The sidebar stays scrollable at small window heights and at 200% DPI.
                        Navigation(route, state.preferences.dark, { route = Route(it) })
                    }
                    VerticalDivider(color = colors.outline)
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (route.detail != null) Control(Icons.Rounded.ArrowBack, "Back") { route = Route(route.page) }
                            Text(if (route.page == Page.PLAYLISTS && route.detail != null) state.playlists.find { it.id == route.detail }?.name ?: route.title else route.title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (importing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            else {
                                TextButton(onClick = { filePicker(false) }) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Import music") }
                                Control(Icons.Rounded.FolderOpen, "Import folder (Ctrl+Shift+O)") { filePicker(true) }
                            }
                        }
                        if (message != null) Notice(message!!, onDismiss = { model.message.value = null })
                        if (audio.error != null) Notice(audio.error!!, error = true)
                        AnimatedContent(route, transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) }, label = "navigation", modifier = Modifier.weight(1f)) { selected ->
                            when (selected.page) {
                                Page.HOME -> Home(model, state, { route = it }, filePicker)
                                Page.EXPLORE -> Explore(state) { route = it }
                                Page.SEARCH -> Column(Modifier.padding(horizontal = 28.dp)) {
                                    OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true,
                                        placeholder = { Text("Search your music") }, leadingIcon = { Icon(Icons.Rounded.Search, null) })
                                    val results = state.library.filter { "${it.title} ${it.artist} ${it.album}".contains(search, true) }.map { it.song() }
                                    TrackList(results, model, state, Modifier.weight(1f), emptyText = "No matching tracks in your local library.")
                                }
                                Page.LIBRARY -> Column(Modifier.padding(horizontal = 28.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(selected.detail == "favorites", { route = if (selected.detail == "favorites") Route(Page.LIBRARY) else Route(Page.LIBRARY, "favorites", "Favorites") }, { Text("Favorites") })
                                        AssistChip({ route = Route(Page.PLAYLISTS) }, { Text("Playlists") })
                                        AssistChip({ route = Route(Page.ALBUMS) }, { Text("Albums") })
                                        AssistChip({ route = Route(Page.ARTISTS) }, { Text("Artists") })
                                    }
                                    val tracks = state.library.filter { selected.detail != "favorites" || it.id in state.favorites }.map { it.song() }
                                    TrackList(tracks, model, state, Modifier.weight(1f), emptyText = if (selected.detail == "favorites") "Favorite a song to find it here." else "Import music files or a folder to start your library.")
                                }
                                Page.PLAYLISTS -> if (selected.detail == null) Playlists(state, { createName = "" }) { route = Route(Page.PLAYLISTS, it.id, it.name) }
                                    else PlaylistDetail(selected.detail, state, model) { route = Route(Page.PLAYLISTS) }
                                Page.ALBUMS, Page.ARTISTS -> if (selected.detail == null) Collections(state, selected.page) { route = Route(selected.page, it, it) }
                                    else TrackList(state.library.filter { if (selected.page == Page.ALBUMS) it.album == selected.detail else it.artist == selected.detail }.map { it.song() }, model, state, Modifier.padding(horizontal = 28.dp))
                                Page.HISTORY -> TrackList(state.history.mapNotNull { id -> state.library.find { it.id == id }?.song() }, model, state, Modifier.padding(horizontal = 28.dp), emptyText = "Your recently played tracks will appear here.")
                                Page.PLAYER -> NowPlaying(model, state)
                                Page.QUEUE -> QueueView(model)
                                Page.LYRICS -> LyricsView(model, Modifier.padding(horizontal = 28.dp))
                                Page.SETTINGS -> Settings(model, state)
                                Page.ACCOUNT -> Account()
                                Page.ABOUT -> About()
                            }
                        }
                    }
                }
                HorizontalDivider(color = colors.outline)
                MiniPlayer(model, state) { route = Route(Page.PLAYER) }
            }
        }
        if (createName != null) AlertDialog(onDismissRequest = { createName = null }, title = { Text("New playlist") },
            text = { OutlinedTextField(createName!!, { createName = it }, label = { Text("Playlist name") }, singleLine = true) },
            confirmButton = { TextButton({ model.createPlaylist(createName!!); createName = null }, enabled = !createName.isNullOrBlank()) { Text("Create") } },
            dismissButton = { TextButton({ createName = null }) { Text("Cancel") } })
        LaunchedEffect(Unit) {
            if (smoke) {
                for (page in Page.entries) { route = Route(page); delay(300) }
                val playlist = state.playlists.firstOrNull()
                if (playlist != null) { route = Route(Page.PLAYLISTS, playlist.id, playlist.name); delay(300) }
                state.library.firstOrNull()?.let { route = Route(Page.ALBUMS, it.album, it.album); delay(300) }
                route = Route(Page.HOME); delay(300)
            }
            onReady()
        }
    }
}

@Composable private fun Navigation(route: Route, dark: Boolean, navigate: (Page) -> Unit) {
    Column(Modifier.width(190.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(12.dp)) {
        val logo = remember { SkiaImage.makeFromEncoded(requireNotNull(object {}.javaClass.getResourceAsStream("/brand/mono.png")).readBytes()).asImageBitmap() }
        Row(Modifier.padding(8.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(logo, "Podium Air", Modifier.size(32.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (dark) Color.White else Color.Black))
            Spacer(Modifier.width(8.dp)); Text("Podium Air", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Text("WINDOWS EDITION", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp, 0.dp, 0.dp, 12.dp))
        Page.entries.forEach { page ->
            if (page == Page.PLAYER || page == Page.ACCOUNT) HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)
            val background by animateColorAsState(if (route.page == page) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, label = "tab")
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(background).clickable { navigate(page) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(page.icon, page.title, Modifier.size(19.dp), tint = if (route.page == page) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp)); Text(page.title, fontSize = 13.sp, fontWeight = if (route.page == page) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
@Composable private fun Notice(text: String, error: Boolean = false, onDismiss: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().background(if (error) AccentRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontSize = 12.sp, color = if (error) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant)
        if (onDismiss != null) Control(Icons.Rounded.Close, "Dismiss", onClick = onDismiss)
    }
}
@Composable private fun Control(icon: ImageVector, label: String, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(38.dp)) { Icon(icon, label, Modifier.size(21.dp), tint = if (selected) AccentRed else if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
}
@Composable private fun Art(song: Song?, size: Int = 72, modifier: Modifier = Modifier) {
    var bitmap by remember(song?.thumbnailUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(song?.thumbnailUrl) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { song?.thumbnailUrl?.let { uri -> SkiaImage.makeFromEncoded(File(URI(uri)).readBytes()).asImageBitmap() } }.getOrNull()
        }
    }
    Box(modifier.size(size.dp).clip(RoundedCornerShape((size / 14).coerceAtLeast(6).dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!, "${song?.albumName ?: "Album"} artwork", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.MusicNote, "No artwork", Modifier.size((size * 0.38).dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun Home(model: DesktopModel, state: SavedState, navigate: (Route) -> Unit, picker: (Boolean) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(28.dp, 0.dp, 28.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(24.dp)) {
                Text("YOUR MUSIC. YOUR SPACE.", color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp)); Text(if (state.library.isEmpty()) "Bring your music home." else "Listen again.", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(8.dp)); Text("Native Windows preview • Local music", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button({ if (state.library.isEmpty()) picker(false) else model.play(state.library.map { it.song() }) }) {
                    Icon(if (state.library.isEmpty()) Icons.Rounded.Add else Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (state.library.isEmpty()) "Import your first tracks" else "Play your library")
                }
            }
        }
        val history = state.history.mapNotNull { id -> state.library.find { it.id == id }?.song() }
        if (history.isNotEmpty()) item { Shelf("Recently played", history) { model.play(history, it) } }
        if (state.library.isNotEmpty()) item { val songs = state.library.takeLast(20).reversed().map { it.song() }; Shelf("Recently added", songs) { model.play(songs, it) } }
        if (state.favorites.isNotEmpty()) item { val songs = state.library.filter { it.id in state.favorites }.map { it.song() }; Shelf("Favorites", songs) { model.play(songs, it) } }
        item {
            Text("Streaming & account parity", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp)); Text("YouTube Music streaming and Google sync are unavailable in this preview. The Android app's private API and extraction pipeline have not been ported. Your local music works offline.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton({ navigate(Route(Page.ACCOUNT)) }) { Text("Source availability") }
        }
    }
}
@Composable private fun Shelf(title: String, songs: List<Song>, play: (Int) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) { itemsIndexed(songs) { index, song ->
            Column(Modifier.width(148.dp).clickable { play(index) }.semantics { contentDescription = "Play ${song.title} by ${song.artist}" }) {
                Art(song, 148); Spacer(Modifier.height(8.dp)); Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
    }
}
@Composable private fun Explore(state: SavedState, navigate: (Route) -> Unit) {
    LazyColumn(Modifier.padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Explore your collection", style = MaterialTheme.typography.headlineMedium); Text("Albums and artists from your imported tracks.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { CollectionLink("Albums", "${state.library.map { it.album }.distinct().size} albums", Icons.Rounded.Album) { navigate(Route(Page.ALBUMS)) } }
        item { CollectionLink("Artists", "${state.library.map { it.artist }.distinct().size} artists", Icons.Rounded.Person) { navigate(Route(Page.ARTISTS)) } }
        item { CollectionLink("Favorites", "${state.favorites.size} tracks", Icons.Rounded.Favorite) { navigate(Route(Page.LIBRARY, "favorites", "Favorites")) } }
    }
}
@Composable private fun CollectionLink(title: String, subtitle: String, icon: ImageVector, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = click).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(30.dp), tint = AccentRed); Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Rounded.ChevronRight, "Open $title")
    }
}
@Composable private fun TrackList(songs: List<Song>, model: DesktopModel, state: SavedState, modifier: Modifier = Modifier, emptyText: String = "This collection is empty.", playlist: Playlist? = null) {
    if (songs.isEmpty()) { Empty(emptyText, modifier); return }
    LazyColumn(modifier.fillMaxSize()) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("${songs.size} tracks", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); TextButton({ model.play(songs) }) { Icon(Icons.Rounded.PlayArrow, null); Text("Play all") } } }
        itemsIndexed(songs, key = { index, song -> "${song.videoId}:$index" }) { index, song ->
            TrackRow(song, index, songs.size, model, state, { model.play(songs, index) }, playlist)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        }
    }
}
@Composable private fun TrackRow(song: Song, index: Int, count: Int, model: DesktopModel, state: SavedState, play: () -> Unit, playlist: Playlist?) {
    var menu by remember { mutableStateOf(false) }; var remove by remember { mutableStateOf(false) }
    val audio by model.engine.state.collectAsState()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = play).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(song, 46); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = if (audio.entry?.song?.videoId == song.videoId) AccentRed else MaterialTheme.colorScheme.onSurface)
            Text("${song.artist} • ${song.albumName ?: "Local music"}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(song.durationText.orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Control(if (song.videoId in state.favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Toggle favorite ${song.title}", selected = song.videoId in state.favorites) { model.favorite(song.videoId) }
        Box {
            Control(Icons.Rounded.MoreHoriz, "Actions for ${song.title}") { menu = true }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Play next") }, { model.enqueue(song, true); menu = false })
                DropdownMenuItem({ Text("Add to queue") }, { model.enqueue(song); menu = false })
                state.playlists.forEach { p -> DropdownMenuItem({ Text("Add to ${p.name}") }, { model.addToPlaylist(p.id, song.videoId); menu = false }) }
                if (playlist != null) {
                    DropdownMenuItem({ Text("Move up") }, { model.moveInPlaylist(playlist.id, index, -1); menu = false }, enabled = index > 0)
                    DropdownMenuItem({ Text("Move down") }, { model.moveInPlaylist(playlist.id, index, 1); menu = false }, enabled = index < count - 1)
                    DropdownMenuItem({ Text("Remove from playlist") }, { model.removeFromPlaylist(playlist.id, index); menu = false })
                }
                DropdownMenuItem({ Text("Show in folder") }, { runCatching { Desktop.getDesktop().open(File(requireNotNull(song.localPath)).parentFile) }.onFailure { model.message.value = it.message }; menu = false })
                DropdownMenuItem({ Text("Remove from library") }, { remove = true; menu = false })
            }
        }
    }
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("Remove ${song.title}?") }, text = { Text("This removes the track from your library, playlists and queue. The audio file stays on your computer.") },
        confirmButton = { TextButton({ model.removeTrack(song.videoId); remove = false }) { Text("Remove") } }, dismissButton = { TextButton({ remove = false }) { Text("Cancel") } })
}
@Composable private fun Playlists(state: SavedState, create: () -> Unit, open: (Playlist) -> Unit) {
    LazyColumn(Modifier.padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TextButton(create) { Icon(Icons.Rounded.Add, null); Text("New playlist") } }
        items(state.playlists, key = { it.id }) { p -> CollectionLink(p.name, "${p.tracks.size} tracks", Icons.Rounded.QueueMusic) { open(p) } }
        if (state.playlists.isEmpty()) item { Empty("Create a playlist, then add tracks using each song's action menu.") }
    }
}
@Composable private fun PlaylistDetail(id: String, state: SavedState, model: DesktopModel, back: () -> Unit) {
    val p = state.playlists.find { it.id == id }
    if (p == null) { Empty("Playlist removed."); return }
    var rename by remember(id) { mutableStateOf<String?>(null) }; var delete by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 28.dp)) {
        Row { TextButton({ rename = p.name }) { Text("Rename") }; TextButton({ delete = true }) { Text("Delete") } }
        TrackList(p.tracks.mapNotNull { trackId -> state.library.find { it.id == trackId }?.song() }, model, state, Modifier.weight(1f), playlist = p)
    }
    if (rename != null) AlertDialog(onDismissRequest = { rename = null }, title = { Text("Rename playlist") }, text = { OutlinedTextField(rename!!, { rename = it }, singleLine = true) },
        confirmButton = { TextButton({ model.renamePlaylist(id, rename!!); rename = null }, enabled = !rename.isNullOrBlank()) { Text("Save") } }, dismissButton = { TextButton({ rename = null }) { Text("Cancel") } })
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete ${p.name}?") }, text = { Text("Your music files and library stay on your computer.") }, confirmButton = { TextButton({ model.deletePlaylist(id); delete = false; back() }) { Text("Delete") } }, dismissButton = { TextButton({ delete = false }) { Text("Cancel") } })
}
@Composable private fun Collections(state: SavedState, page: Page, open: (String) -> Unit) {
    val groups = state.library.groupBy { if (page == Page.ALBUMS) it.album else it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    LazyColumn(Modifier.padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(groups.entries.toList(), key = { it.key }) { entry -> CollectionLink(entry.key, "${entry.value.size} tracks", page.icon) { open(entry.key) } }
        if (groups.isEmpty()) item { Empty("Import tracks to browse ${page.title.lowercase()}.") }
    }
}
@Composable private fun Empty(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
@Composable private fun MiniPlayer(model: DesktopModel, state: SavedState, open: () -> Unit) {
    val audio by model.engine.state.collectAsState()
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clickable(onClick = open), verticalAlignment = Alignment.CenterVertically) {
            Art(audio.entry?.song, 46); Spacer(Modifier.width(12.dp)); Column {
                Text(audio.entry?.song?.title ?: "Podium Air", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(audio.entry?.song?.artist ?: "Choose a track to start listening", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Control(Icons.Rounded.SkipPrevious, "Previous track", audio.entry != null) { model.previous() }
        Control(if (audio.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (audio.playing) "Pause" else "Play", model.queue.value.current != null) { model.toggle() }
        Control(Icons.Rounded.SkipNext, "Next track", model.queue.value.nextIndex() != null) { model.next() }
        Spacer(Modifier.width(18.dp)); Icon(Icons.Rounded.VolumeUp, "Volume", Modifier.size(18.dp))
        Slider(state.preferences.volume, { model.preferences(state.preferences.copy(volume = it)) }, Modifier.width(96.dp), valueRange = 0f..1f)
    }
}
@Composable private fun NowPlaying(model: DesktopModel, state: SavedState) {
    val audio by model.engine.state.collectAsState()
    val song = audio.entry?.song
    if (song == null) { Empty("Choose a track from your library to start listening."); return }
    BoxWithConstraints(Modifier.fillMaxSize().padding(28.dp, 0.dp, 28.dp, 24.dp)) {
        val wide = maxWidth > 720.dp
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Art(song, if (wide) 300 else 230)
                Spacer(Modifier.height(22.dp)); Text(song.title, style = MaterialTheme.typography.headlineMedium)
                Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(song.albumName.orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp)); SeekBar(model)
                PlayerControls(model)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Control(if (song.videoId in state.favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Toggle favorite", selected = song.videoId in state.favorites) { model.favorite(song.videoId) }
                    Text(if (audio.fading) "Crossfading" else "Local music • ${File(song.localPath.orEmpty()).extension.uppercase()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (wide) LyricsView(model, Modifier.weight(1f))
        }
    }
}
@Composable private fun SeekBar(model: DesktopModel) {
    val audio by model.engine.state.collectAsState()
    var dragging by remember { mutableStateOf(false) }; var position by remember(audio.entry?.key) { mutableStateOf(0f) }
    Column {
        Slider(if (dragging) position else audio.positionMs.toFloat().coerceIn(0f, audio.durationMs.coerceAtLeast(1).toFloat()),
            { dragging = true; position = it }, valueRange = 0f..audio.durationMs.coerceAtLeast(1).toFloat(),
            onValueChangeFinished = { model.seek(position.toLong()); dragging = false }, enabled = audio.durationMs > 0,
            modifier = Modifier.semantics { contentDescription = "Seek position" })
        Row(Modifier.fillMaxWidth()) { Text(formatTime(if (dragging) position.toLong() else audio.positionMs), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.weight(1f)); Text(formatTime(audio.durationMs), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun PlayerControls(model: DesktopModel) {
    val audio by model.engine.state.collectAsState(); val queue by model.queue.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        Control(Icons.Rounded.Shuffle, "Toggle shuffle", selected = queue.shuffled) { model.shuffle() }
        Control(Icons.Rounded.SkipPrevious, "Previous track", audio.entry != null) { model.previous() }
        FilledIconButton({ model.toggle() }, enabled = queue.current != null, modifier = Modifier.size(56.dp)) { Icon(if (audio.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (audio.playing) "Pause" else "Play", Modifier.size(32.dp)) }
        Control(Icons.Rounded.SkipNext, "Next track", queue.nextIndex() != null) { model.next() }
        Control(if (queue.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat: ${queue.repeat.name}", selected = queue.repeat != RepeatMode.OFF) { model.repeat() }
    }
}
@Composable private fun QueueView(model: DesktopModel) {
    val queue by model.queue.collectAsState()
    if (queue.entries.isEmpty()) { Empty("Your queue is empty. Play a collection or add a song to queue."); return }
    LazyColumn(Modifier.padding(horizontal = 28.dp)) {
        item { PlayerControls(model); Spacer(Modifier.height(16.dp)) }
        itemsIndexed(queue.entries, key = { _, it -> it.key }) { index, entry ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (index == queue.cursor) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent).clickable { model.selectQueue(index) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(entry.song, 42); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(entry.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (index == queue.cursor) AccentRed else MaterialTheme.colorScheme.onSurface); Text(entry.song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                Control(Icons.Rounded.KeyboardArrowUp, "Move ${entry.song.title} up", index > 0) { model.moveQueue(entry.key, -1) }
                Control(Icons.Rounded.KeyboardArrowDown, "Move ${entry.song.title} down", index < queue.entries.lastIndex) { model.moveQueue(entry.key, 1) }
                Control(Icons.Rounded.Close, "Remove ${entry.song.title} from queue") { model.removeQueue(entry.key) }
            }
        }
    }
}
@Composable private fun LyricsView(model: DesktopModel, modifier: Modifier = Modifier) {
    val lyrics by model.lyrics.collectAsState(); val audio by model.engine.state.collectAsState()
    val list = rememberLazyListState()
    val synced = lyrics.any { it.timeMs > 0 }
    val current = if (synced) lyrics.indexOfLast { it.timeMs <= audio.positionMs } else -1
    LaunchedEffect(audio.entry?.key, current) { if (current >= 0) list.animateScrollToItem((current - 1).coerceAtLeast(0)) }
    if (lyrics.isEmpty()) { Empty("No lyrics found. Place a .lrc file beside the audio file with the same filename, or use embedded lyrics.", modifier); return }
    LazyColumn(modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        itemsIndexed(lyrics) { index, line ->
            val alpha by animateFloatAsState(if (!synced || current == index) 1f else 0.35f, label = "lyric")
            val text = buildAnnotatedString {
                append(if (line.isGap) "♪" else line.text)
                if (line.isWordSynced && current == index) addStyle(SpanStyle(color = AccentRed), 0, line.revealedChars(audio.positionMs).toInt().coerceIn(0, length))
            }
            Text(text, Modifier.fillMaxWidth().then(if (synced) Modifier.clickable { model.seek(line.timeMs) } else Modifier),
                fontSize = 27.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            line.background?.let { Text(it.text, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
@Composable private fun Settings(model: DesktopModel, state: SavedState) {
    val prefs = state.preferences; val sleep by model.sleepRemaining.collectAsState()
    LazyColumn(Modifier.padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("Appearance", style = MaterialTheme.typography.headlineMedium); Row(verticalAlignment = Alignment.CenterVertically) { Text("Dark theme", Modifier.weight(1f)); Switch(prefs.dark, { model.preferences(prefs.copy(dark = it)) }) } }
        item { Text("Playback", style = MaterialTheme.typography.headlineMedium); Text("Crossfade • ${prefs.crossfadeSeconds}s"); Slider(prefs.crossfadeSeconds.toFloat(), { model.preferences(prefs.copy(crossfadeSeconds = it.toInt())) }, valueRange = 0f..12f, steps = 11); Text("Equal-power volume overlap. Crossfade pauses during seeking and is disabled at playback speeds other than 1×. Automix beat matching is not available.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text("Playback speed • ${"%.2f".format(prefs.speed)}×"); Slider(prefs.speed, { model.preferences(prefs.copy(speed = it)) }, valueRange = 0.5f..2f, steps = 5) }
        item { Text("Sleep timer", style = MaterialTheme.typography.titleLarge); if (sleep != null) Text("Pauses in ${formatTime(sleep!! * 1000)}"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(15, 30, 60).forEach { minutes -> AssistChip({ model.sleepTimer(minutes) }, { Text("${minutes}m") }) }; AssistChip({ model.sleepTimer(null) }, { Text("Cancel") }) } }
        item { Text("Equalizer", style = MaterialTheme.typography.headlineMedium); TextButton({ model.preferences(prefs.copy(equalizer = List(10) { 0.0 })) }) { Text("Reset to flat") } }
        items(10) { band -> Row(verticalAlignment = Alignment.CenterVertically) { Text(listOf("32 Hz", "64 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz")[band], Modifier.width(64.dp), fontSize = 12.sp); Slider(prefs.equalizer.getOrElse(band) { 0.0 }.toFloat(), { gain -> model.preferences(prefs.copy(equalizer = prefs.equalizer.toMutableList().apply { this[band] = gain.toDouble() })) }, Modifier.weight(1f), valueRange = -12f..12f); Text("${prefs.equalizer.getOrElse(band) { 0.0 }.toInt()} dB", Modifier.width(46.dp), fontSize = 12.sp) } }
        item { Text("Keyboard shortcuts", style = MaterialTheme.typography.headlineMedium); Text("Ctrl+O — import files\nCtrl+Shift+O — import folder\nCtrl+F — search\nCtrl+Space — play/pause\nCtrl+Left/Right — previous/next\nAlt+Left/Right — seek 10 seconds\nEsc — leave collection") }
        item { Text("Data", style = MaterialTheme.typography.headlineMedium); Text("Your library, playlists and preferences are stored on this computer. Music files are referenced in place; importing does not copy them. ${defaultDataDirectory().absolutePath}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun Account() {
    Column(Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Local music", style = MaterialTheme.typography.headlineMedium); Text("No account is needed to use your local collection.")
        Text("YouTube Music • Unavailable", fontWeight = FontWeight.Bold)
        Text("Google login, personalized streaming, remote playlists and bidirectional account sync have not been implemented. This preview does not collect passwords, cookies or tokens.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button({ Desktop.getDesktop().browse(URI("https://music.youtube.com")) }) { Text("Open YouTube Music") }
        Text("The service opens in your default browser, with its own sign-in and playback rules. It does not connect or synchronize this app.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Adapted from the Android application Podium Air."); Text("Made with ❤️ by Prem", color = AccentRed)
    }
}
@Composable private fun About() {
    var licenses by remember { mutableStateOf<String?>(null) }
    var showLicenseMenu by remember { mutableStateOf(false) }
    val licensePaths = remember { readResource("/licenses/INDEX.txt").lineSequence().filter { it.startsWith("licenses/") }.toList() }
    Column(Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Podium Air — Windows Edition", style = MaterialTheme.typography.headlineMedium)
        Text("0.1.0 • Native desktop preview"); Text("Adapted from the Android application Podium Air."); Text("Made with ❤️ by Prem", color = AccentRed)
        Text("This preview supports local music. Streaming, full Android feature parity, Automix and Windows system media sessions are pending.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton({ Desktop.getDesktop().browse(URI("https://github.com/kaizen-flims/Podium-Air-Windows-")) }) { Text("Corresponding source & build instructions") }
        TextButton({ licenses = readResource("/licenses/THIRD_PARTY_NOTICES.md") + "\n\n" + readResource("/licenses/LICENSE") }) { Text("Third-party licenses & legal notices") }
        Text("Free software under GNU GPL version 3. No warranty. You may redistribute it under the license terms.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (licenses != null) AlertDialog(onDismissRequest = { licenses = null }, title = { Text("Licenses & legal notices") }, text = {
        Column {
            Box {
                TextButton({ showLicenseMenu = true }) { Text("View bundled license…") }
                DropdownMenu(showLicenseMenu, { showLicenseMenu = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                    licensePaths.forEach { path -> DropdownMenuItem({ Text(path.removePrefix("licenses/"), fontSize = 11.sp) }, { licenses = readResource("/$path"); showLicenseMenu = false }) }
                }
            }
            androidx.compose.foundation.text.selection.SelectionContainer { Text(licenses!!, Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), fontSize = 11.sp) }
        }
    }, confirmButton = { TextButton({ licenses = null }) { Text("Close") } })
}
private fun readResource(path: String): String = object {}.javaClass.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() } ?: "See the corresponding source for $path."
