// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object PlaylistFiles {
    /** M3U/M3U8 local files only. Network URLs and external service entries are reported. */
    fun read(file: File): Pair<List<File>, List<String>> {
        require(file.isFile && file.length() <= 2_000_000) { "Choose a local M3U/M3U8 playlist smaller than 2 MB." }
        val errors = mutableListOf<String>()
        val tracks = file.readText(Charsets.UTF_8).removePrefix("\uFEFF").lineSequence().map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }.take(10000).mapNotNull { path ->
                when {
                    path.startsWith("file:", true) -> runCatching { File(URI(path)) }.getOrElse { errors += "Invalid file URI: $path"; null }
                    Regex("^[a-zA-Z][a-zA-Z0-9+.-]+://").containsMatchIn(path) -> { errors += "Online entry skipped: $path"; null }
                    else -> File(path).let { if (it.isAbsolute) it else File(file.absoluteFile.parentFile, path) }
                }
            }.toList()
        return tracks to errors
    }
    fun write(file: File, tracks: List<StoredTrack>) {
        val parent = file.absoluteFile.parentFile
        require(parent.isDirectory) { "Choose an existing folder for the playlist." }
        val text = buildString {
            appendLine("#EXTM3U")
            tracks.forEach { track ->
                appendLine("#EXTINF:${track.duration / 1000},${track.artist} - ${track.title}".replace('\n', ' ').replace('\r', ' '))
                val absolute = File(track.path).absoluteFile.toPath()
                val path = runCatching { parent.toPath().relativize(absolute).toString() }.getOrElse { absolute.toString() }
                require('\n' !in path && '\r' !in path) { "A track path contains a line break and cannot be exported to M3U." }
                appendLine(path)
            }
        }
        val temp = Files.createTempFile(parent.toPath(), "playlist-", ".tmp")
        try {
            Files.writeString(temp, text, Charsets.UTF_8)
            Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }
}
