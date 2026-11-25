package com.tvapp.livetv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tvapp.livetv.BuildConfig
import com.tvapp.livetv.R
import com.tvapp.livetv.model.Channel
import com.tvapp.livetv.model.M3uRepository
import com.tvapp.livetv.model.UserPrefs
import com.tvapp.livetv.player.PlayerScreen
import com.tvapp.livetv.updater.OtaUpdater
import com.tvapp.livetv.updater.ReleaseInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSplash by remember { mutableStateOf(true) }
    var activeSource by remember { mutableStateOf(M3uRepository.PRESET_SOURCES.first()) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var activeChannel by remember { mutableStateOf<Channel?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var favoriteIds by remember { mutableStateOf(UserPrefs.getFavorites(context)) }
    var recentIds by remember { mutableStateOf(UserPrefs.getRecentIds(context)) }

    var pendingUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        delay(1800)
        showSplash = false
    }

    LaunchedEffect(activeSource) {
        isLoading = true
        loadError = null
        selectedGroup = "All"
        try {
            channels = M3uRepository.fetchPlaylist(activeSource.url)
            isLoading = false
        } catch (e: Exception) {
            loadError = e.message ?: "Failed to load channels"
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching { OtaUpdater.checkForUpdate() }.onSuccess { release ->
            pendingUpdate = release
        }
    }

    if (showSplash) {
        SplashScreen()
        return
    }

    val currentPlaying = activeChannel
    if (currentPlaying != null) {
        val visibleList = remember(channels, selectedGroup, searchQuery, favoriteIds, recentIds) {
            filterChannels(channels, selectedGroup, searchQuery, favoriteIds, recentIds)
        }
        val idx = visibleList.indexOfFirst { it.id == currentPlaying.id }.coerceAtLeast(0)

        PlayerScreen(
            channel = currentPlaying,
            isFavorite = favoriteIds.contains(currentPlaying.id),
            onToggleFavorite = {
                favoriteIds = UserPrefs.toggleFavorite(context, currentPlaying.id)
            },
            onNextChannel = {
                if (visibleList.isNotEmpty()) {
                    val next = visibleList[(idx + 1) % visibleList.size]
                    activeChannel = next
                    recentIds = UserPrefs.addRecent(context, next.id)
                }
            },
            onPreviousChannel = {
                if (visibleList.isNotEmpty()) {
                    val prevIdx = if (idx - 1 < 0) visibleList.lastIndex else idx - 1
                    val prev = visibleList[prevIdx]
                    activeChannel = prev
                    recentIds = UserPrefs.addRecent(context, prev.id)
                }
            },
            onBack = { activeChannel = null }
        )
        return
    }

    val groupCounts = remember(channels) {
        channels.groupingBy { it.group }.eachCount()
    }
    val groups = remember(channels, favoriteIds, recentIds) {
        val base = mutableListOf<Pair<String, Int>>()
        if (favoriteIds.isNotEmpty()) base.add("★ Favorites" to favoriteIds.size)
        if (recentIds.isNotEmpty()) base.add("🕒 Recent" to recentIds.size)
        base.add("All" to channels.size)
        base.addAll(groupCounts.entries.sortedByDescending { it.value }.map { it.key to it.value })
        base
    }
    val filteredChannels = remember(channels, selectedGroup, searchQuery, favoriteIds, recentIds) {
        filterChannels(channels, selectedGroup, searchQuery, favoriteIds, recentIds)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(20.dp)
    ) {
        // Left Sidebar: Brand + Categories + OTA Update
        Column(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .padding(end = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher),
                        contentDescription = "Tarang TV",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Tarang TV",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp
                        )
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(groups) { (group, count) ->
                        val isSelected = group == selectedGroup
                        Button(
                            onClick = { selectedGroup = group },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B),
                                contentColor = Color.White,
                                focusedContainerColor = Color(0xFF38BDF8),
                                focusedContentColor = Color(0xFF090D16)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = group,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$count",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // OTA Update Control
            Column {
                Button(
                    onClick = {
                        scope.launch {
                            val release = pendingUpdate ?: OtaUpdater.checkForUpdate()
                            if (release != null) {
                                pendingUpdate = release
                                updateStatus = "Downloading v${release.versionName}..."
                                runCatching {
                                    OtaUpdater.downloadAndInstall(context, release.apkDownloadUrl) { pct ->
                                        downloadProgress = pct
                                    }
                                }.onFailure { err ->
                                    updateStatus = "Update failed: ${err.message}"
                                    downloadProgress = null
                                }
                            } else {
                                updateStatus = "Up to date (v${BuildConfig.VERSION_NAME})"
                            }
                        }
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (pendingUpdate != null) Color(0xFF16A34A) else Color(0xFF334155),
                        contentColor = Color.White,
                        focusedContainerColor = Color(0xFFFACC15),
                        focusedContentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (pendingUpdate != null) {
                            "Update to v${pendingUpdate!!.versionName}"
                        } else {
                            "Check for Updates"
                        }
                    )
                }

                downloadProgress?.let { pct ->
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF22C55E)
                    )
                }

                updateStatus?.let { status ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = status, color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }

        // Right Content: Playlist Switcher + Search + Channel Grid
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Playlist Packs (India, Hindi, News, Movies, Sports...)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                items(M3uRepository.PRESET_SOURCES) { src ->
                    val active = src.id == activeSource.id
                    Button(
                        onClick = { activeSource = src },
                        colors = ButtonDefaults.colors(
                            containerColor = if (active) Color(0xFFF59E0B) else Color(0xFF1E293B),
                            contentColor = if (active) Color.Black else Color.White,
                            focusedContainerColor = Color(0xFF38BDF8),
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Text(text = src.label, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    androidx.compose.material3.Text(
                        "Search channels (e.g. DD, Aaj Tak, Music...)",
                        color = Color(0xFF64748B)
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF111827),
                    unfocusedContainerColor = Color(0xFF111827)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    loadError != null -> {
                        Text(
                            text = loadError!!,
                            color = Color(0xFFF87171),
                            fontSize = 18.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    filteredChannels.isEmpty() -> {
                        Text(
                            text = "No channels found. Try another pack or clear search.",
                            color = Color(0xFF94A3B8),
                            fontSize = 16.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 210.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            items(filteredChannels, key = { it.id }) { channel ->
                                val isFav = favoriteIds.contains(channel.id)
                                Card(
                                    onClick = {
                                        activeChannel = channel
                                        recentIds = UserPrefs.addRecent(context, channel.id)
                                    },
                                    colors = CardDefaults.colors(
                                        containerColor = Color(0xFF111827),
                                        contentColor = Color.White,
                                        focusedContainerColor = Color(0xFF1E293B)
                                    ),
                                    border = CardDefaults.border(
                                        focusedBorder = Border(
                                            border = BorderStroke(2.dp, Color(0xFF38BDF8)),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(104.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (!channel.logoUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = channel.logoUrl,
                                                contentDescription = channel.name,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF0F172A))
                                                    .padding(4.dp)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF1E293B)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = channel.name.take(2).uppercase(),
                                                    color = Color(0xFF38BDF8),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = channel.name,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = if (isFav) "★ ${channel.group}" else channel.group,
                                                color = if (isFav) Color(0xFFF59E0B) else Color(0xFF38BDF8),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun filterChannels(
    channels: List<Channel>,
    selectedGroup: String,
    searchQuery: String,
    favoriteIds: Set<String>,
    recentIds: List<String>
): List<Channel> {
    val byGroup = when (selectedGroup) {
        "All" -> channels
        "★ Favorites" -> channels.filter { favoriteIds.contains(it.id) }
        "🕒 Recent" -> recentIds.mapNotNull { id -> channels.find { it.id == id } }
        else -> channels.filter { it.group == selectedGroup }
    }
    val q = searchQuery.trim()
    return if (q.isEmpty()) byGroup else byGroup.filter {
        it.name.contains(q, ignoreCase = true) || it.group.contains(q, ignoreCase = true)
    }
}
