package com.tvapp.livetv.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val group: String = "General",
    val logoUrl: String? = null
)

object M3uRepository {
    // Public free-to-air India playlist from iptv-org
    const val DEFAULT_PLAYLIST_URL = "https://iptv-org.github.io/iptv/countries/in.m3u"

    private val client = OkHttpClient()

    suspend fun fetchPlaylist(url: String = DEFAULT_PLAYLIST_URL): List<Channel> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Playlist fetch failed: HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                parseM3u(body)
            }
        }

    fun parseM3u(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()
        var currentName = ""
        var currentGroup = "General"
        var currentLogo: String? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF")) {
                currentGroup = extractAttr(line, "group-title")
                    ?.takeIf { it.isNotBlank() } ?: "General"
                currentLogo = extractAttr(line, "tvg-logo")
                currentName = line.substringAfterLast(",", "").trim()
                    .ifBlank { "Channel ${channels.size + 1}" }
            } else if (!line.startsWith("#")) {
                if (line.startsWith("http://") || line.startsWith("https://")) {
                    channels.add(
                        Channel(
                            id = "${channels.size}_${currentName.hashCode()}",
                            name = currentName,
                            streamUrl = line,
                            group = currentGroup,
                            logoUrl = currentLogo
                        )
                    )
                }
                currentName = ""
                currentGroup = "General"
                currentLogo = null
            }
        }
        return channels
    }

    private fun extractAttr(line: String, key: String): String? {
        val regex = Regex("$key=\"([^\"]*)\"")
        return regex.find(line)?.groupValues?.getOrNull(1)
    }
}
