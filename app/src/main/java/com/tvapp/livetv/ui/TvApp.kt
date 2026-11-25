package com.tvapp.livetv.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
import com.tvapp.livetv.model.PlaylistSource
import com.tvapp.livetv.model.UserPrefs
import com.tvapp.livetv.network.LocalM3uServer
import com.tvapp.livetv.player.PlayerScreen
import com.tvapp.livetv.updater.OtaUpdater
import com.tvapp.livetv.updater.ReleaseInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSplash by remember { mutableStateOf(true) }
    var customM3uUrl by remember { mutableStateOf(UserPrefs.getCustomM3uUrl(context)) }
    val allSources = remember(customM3uUrl) {
        if (customM3uUrl != null) {
            M3uRepository.PRESET_SOURCES + PlaylistSource("custom", "My M3U", listOf(customM3uUrl!!))
        } else {
            M3uRepository.PRESET_SOURCES
        }
    }

    var activeSource by remember { mutableStateOf(M3uRepository.PRESET_SOURCES.first()) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    var focusedChannel by remember { mutableStateOf<Channel?>(null) }
    var activeChannel by remember { mutableStateOf<Channel?>(null) }
    var lastChannel by remember { mutableStateOf<Channel?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var favoriteIds by remember { mutableStateOf(UserPrefs.getFavorites(context)) }
    var recentIds by remember { mutableStateOf(UserPrefs.getRecentIds(context)) }

    var pendingUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf<Int?>(null) }

    // Muted Hero Live Preview Player
    val previewPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { previewPlayer.release() }
    }

    // Debounced live preview when user focuses a channel card
    LaunchedEffect(focusedChannel, activeChannel) {
        if (activeChannel != null) {
            previewPlayer.pause()
            return@LaunchedEffect
        }
        val target = focusedChannel ?: return@LaunchedEffect
        delay(650)
        previewPlayer.setMediaItem(MediaItem.fromUri(target.streamUrl))
        previewPlayer.prepare()
        previewPlayer.play()
    }

    LaunchedEffect(Unit) {
        delay(1500)
        showSplash = false
    }

    LaunchedEffect(activeSource) {
        isLoading = true
        loadError = null
        selectedGroup = "All"
        searchQuery = ""
        try {
            val loaded = M3uRepository.fetchPlaylists(activeSource.urls)
            channels = loaded
            focusedChannel = loaded.firstOrNull()
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

        fun switchTo(next: Channel) {
            if (next.id != currentPlaying.id) {
                lastChannel = currentPlaying
                activeChannel = next
                recentIds = UserPrefs.addRecent(context, next.id)
            }
        }

        PlayerScreen(
            channel = currentPlaying,
            isFavorite = favoriteIds.contains(currentPlaying.id),
            hasPreviousRecall = lastChannel != null,
            onToggleFavorite = {
                favoriteIds = UserPrefs.toggleFavorite(context, currentPlaying.id)
            },
            onNextChannel = {
                if (visibleList.isNotEmpty()) switchTo(visibleList[(idx + 1) % visibleList.size])
            },
            onPreviousChannel = {
                if (visibleList.isNotEmpty()) {
                    val prevIdx = if (idx - 1 < 0) visibleList.lastIndex else idx - 1
                    switchTo(visibleList[prevIdx])
                }
            },
            onRecallLastChannel = {
                lastChannel?.let { switchTo(it) }
            },
            onJumpToNumber = { num ->
                channels.find { it.number == num }?.let { switchTo(it) }
            },
            onBack = { activeChannel = null }
        )
        return
    }

    val groupCounts = remember(channels) { channels.groupingBy { it.group }.eachCount() }
    val priorityOrder = listOf("Movies", "News", "Entertainment", "Music", "Sports", "Kids", "Religious", "General")
    val groups = remember(channels, favoriteIds, recentIds) {
        val base = mutableListOf<Pair<String, Int>>()
        if (favoriteIds.isNotEmpty()) base.add("★ Favorites" to favoriteIds.size)
        if (recentIds.isNotEmpty()) base.add("🕒 Recent" to recentIds.size)
        base.add("All" to channels.size)
        val sortedGroups = groupCounts.entries.sortedWith(
            compareBy<Map.Entry<String, Int>> {
                val p = priorityOrder.indexOf(it.key)
                if (p == -1) Int.MAX_VALUE else p
            }.thenByDescending { it.value }
        )
        base.addAll(sortedGroups.map { it.key to it.value })
        base
    }
    val filteredChannels = remember(channels, selectedGroup, searchQuery, favoriteIds, recentIds) {
        filterChannels(channels, selectedGroup, searchQuery, favoriteIds, recentIds)
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            containerColor = Color(0xFF111827),
            title = { Text("Search Channels", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        androidx.compose.material3.Text("Type channel name...", color = Color(0xFF64748B))
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { showSearchDialog = false }) { Text("Done") }
            },
            dismissButton = {
                Button(onClick = { searchQuery = ""; showSearchDialog = false }) { Text("Clear") }
            }
        )
    }

    if (showQrDialog) {
        QrPlaylistDialog(
            onUrlReceived = { url ->
                UserPrefs.saveCustomM3uUrl(context, url)
                customM3uUrl = url
                activeSource = PlaylistSource("custom", "My M3U", listOf(url))
                showQrDialog = false
            },
            onDismiss = { showQrDialog = false }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(18.dp)
    ) {
        // Left Navigation Rail
        Column(
            modifier = Modifier
                .width(215.dp)
                .fillMaxHeight()
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher),
                        contentDescription = "Tarang TV",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Tarang TV",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp
                        )
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
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
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(text = "$count", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { showSearchDialog = true },
                    colors = ButtonDefaults.colors(
                        containerColor = if (searchQuery.isNotBlank()) Color(0xFF0284C7) else Color(0xFF1E293B),
                        contentColor = Color.White,
                        focusedContainerColor = Color(0xFF38BDF8),
                        focusedContentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "Filter: $searchQuery" else "Search",
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = { showQrDialog = true },
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color.White,
                        focusedContainerColor = Color(0xFF38BDF8),
                        focusedContentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Add M3U (QR)", fontSize = 13.sp)
                }

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
                            "Update v${pendingUpdate!!.versionName}"
                        } else {
                            "Check Updates"
                        },
                        fontSize = 13.sp
                    )
                }

                downloadProgress?.let { pct ->
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF22C55E)
                    )
                }

                updateStatus?.let { status ->
                    Text(text = status, color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
            }
        }

        // Right Main Area: Top Hero Live Preview + Pack Pills + Smooth Grid
        Column(modifier = Modifier.fillMaxSize()) {
            // Hero Live Preview Card
            val preview = focusedChannel
            if (preview != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF111827), Color(0xFF1E1B4B))
                            )
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "CH ${preview.number.toString().padStart(3, '0')}  •  ${preview.group.uppercase()}",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = preview.name,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Press OK for Fullscreen  •  Muted Live Preview",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = previewPlayer
                                    useController = false
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            text = "PREVIEW",
                            color = Color(0xFF22C55E),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color(0x99000000), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Playlist Pack Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                items(allSources) { src ->
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
                        Text(text = src.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

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
                            columns = GridCells.Adaptive(minSize = 205.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            items(filteredChannels, key = { it.id }) { channel ->
                                val isFav = favoriteIds.contains(channel.id)
                                Card(
                                    onClick = {
                                        if (activeChannel?.id != channel.id) {
                                            lastChannel = activeChannel
                                        }
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
                                        .height(98.dp)
                                        .onFocusChanged { state ->
                                            if (state.isFocused) {
                                                focusedChannel = channel
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (!channel.logoUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = channel.logoUrl,
                                                contentDescription = channel.name,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF0F172A))
                                                    .padding(4.dp)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF1E293B)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = channel.number.toString().padStart(2, '0'),
                                                    color = Color(0xFF38BDF8),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = channel.name,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "${channel.number.toString().padStart(3, '0')} • ${if (isFav) "★ " else ""}${channel.group}",
                                                color = if (isFav) Color(0xFFF59E0B) else Color(0xFF38BDF8),
                                                fontSize = 11.sp,
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QrPlaylistDialog(
    onUrlReceived: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val ip = remember { LocalM3uServer.getLocalIpAddress() }
    val url = remember(ip) { ip?.let { "http://$it:8765" } }
    val qrBitmap = remember(url) { url?.let { LocalM3uServer.generateQrBitmap(it) } }

    LaunchedEffect(Unit) {
        val server = LocalM3uServer(onUrlReceived = onUrlReceived)
        try {
            server.start()
        } finally {
            server.stop()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        title = { Text("Add Custom M3U from Phone", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (qrBitmap != null && url != null) {
                    Text(
                        text = "Scan with your phone (same Wi-Fi) or open:
$url",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Text(
                        text = "Connect TV to Wi-Fi to use Phone QR transfer.",
                        color = Color(0xFFF87171),
                        fontSize = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
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
