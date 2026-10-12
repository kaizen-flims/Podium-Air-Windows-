// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Desktop
import java.net.URI

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun GoogleAccountPanel(model: DesktopModel, chooseClient: () -> Unit) {
    val account by model.googleAccount.collectAsState()
    Text("Google account", style = MaterialTheme.typography.headlineMedium)
    Text("Connect through Google's sign-in page in your default browser. Podium Air requests profile and read-only YouTube playlist access. Google passwords and browser cookies stay outside this app.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(account.status, fontSize = 13.sp)
    account.identity?.let { person ->
        Text(person.name, fontWeight = FontWeight.Bold)
        if (person.email.isNotBlank()) Text(person.email, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(model::signInGoogle, enabled = account.configured && !account.busy) { Text("Sign in with Google") }
        if (account.saved || account.identity != null) {
            OutlinedButton(model::refreshGooglePlaylists, enabled = !account.busy) { Text("Refresh playlists") }
            TextButton(model::disconnectGoogle, enabled = !account.busy) { Text("Disconnect") }
        }
        if (account.busy) TextButton(model::cancelGoogle) { Text("Cancel") }
    }
    var setup by remember { mutableStateOf(!account.configured) }
    LaunchedEffect(account.configured) { if (account.configured) setup = false }
    TextButton({ setup = !setup }) { Text(if (setup) "Hide connection setup" else "Connection setup") }
    if (setup) {
        Text("A registered Google Desktop OAuth client is required. Enable YouTube Data API v3 in its project and configure the consent screen/test users. Import the downloaded Desktop client JSON here. Configuration and refresh tokens are kept in Windows Credential Manager.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(chooseClient, enabled = !account.busy) { Text("Import Desktop client JSON") }
            TextButton({ Desktop.getDesktop().browse(URI("https://console.cloud.google.com/auth/clients")) }, enabled = !account.busy) { Text("Google Cloud setup") }
            if (account.configured) TextButton(model::forgetGoogleClient, enabled = !account.busy) { Text("Remove configuration") }
        }
    }
    Text("YouTube Data API playlist metadata only. Native YouTube audio, downloads, playlist edits and full YouTube Music synchronization are unavailable. Some YouTube Music-only lists are not exposed by this API.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (account.identity != null && !account.playlistAccess) Text("Read-only playlist permission was not granted. Sign in again to request it.", fontSize = 12.sp)
    if (account.playlist != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(model::googlePlaylistBack, enabled = !account.busy) { Text("← Playlists") }
            Text(account.playlist!!.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
        }
        if (account.items.isEmpty() && !account.busy) Text("No accessible video items in this playlist.", fontSize = 13.sp)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) { items(account.items, key = { "${it.position}:${it.videoId}" }) { item ->
            ListItem(headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(item.channel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = { TextButton({ Desktop.getDesktop().browse(URI("https://www.youtube.com/watch?v=${item.videoId}&list=${account.playlist!!.id}")) }, enabled = !account.busy) { Text("Open on YouTube") } })
        } }
    } else {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) { items(account.playlists, key = { it.id }) { playlist ->
            OutlinedButton({ model.selectGooglePlaylist(playlist) }, enabled = !account.busy, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(playlist.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${playlist.count} items", fontSize = 12.sp)
                }
            }
        } }
    }
    if (account.truncated) Text("This view is limited to 500 entries. Further entries remain on YouTube.", fontSize = 12.sp)
}
