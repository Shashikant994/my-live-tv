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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border as TvBorder
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text as TvText
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
@Composable
fun TvApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isTv = remember { isTvDevice(context) }

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
    var refreshTick by remember { mutableIntStateOf(0) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showM3uDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    var focusedChannel by remember { mutableStateOf<Channel?>(null) }
    var activeChannel by remember { mutableStateOf<Channel?>(null) }
    var lastChannel by remember { mutableStateOf<Channel?>(null) }
    var lastPlayedId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var favoriteIds by remember { mutableStateOf(UserPrefs.getFavorites(context)) }
    var recentIds by remember { mutableStateOf(UserPrefs.getRecentIds(context)) }
    var livePreviewEnabled by remember { mutableStateOf(UserPrefs.isLivePreviewEnabled(context)) }

    var pendingUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        delay(1200)
        showSplash = false
    }

    LaunchedEffect(activeSource, refreshTick) {
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

    val filteredChannels = remember(channels, selectedGroup, searchQuery, favoriteIds, recentIds) {
        filterChannels(channels, selectedGroup, searchQuery, favoriteIds, recentIds)
    }

    val currentPlaying = activeChannel
    if (currentPlaying != null) {
        val visibleList = filteredChannels.ifEmpty { channels }
        val idx = visibleList.indexOfFirst { it.id == currentPlaying.id }.coerceAtLeast(0)

        fun switchTo(next: Channel) {
            if (next.id != currentPlaying.id) {
                lastChannel = currentPlaying
                activeChannel = next
                lastPlayedId = next.id
                recentIds = UserPrefs.addRecent(context, next.id)
            }
        }

        PlayerScreen(
            channel = currentPlaying,
            visibleChannels = visibleList,
            isTv = isTv,
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
            onSelectChannel = { next -> switchTo(next) },
            onRecallLastChannel = {
                lastChannel?.let { switchTo(it) }
            },
            onJumpToNumber = { num ->
                channels.find { it.number == num }?.let { switchTo(it) }
            },
            onBack = {
                lastPlayedId = currentPlaying.id
                activeChannel = null
            }
        )
        return
    }

    val groups = remember(channels, favoriteIds, recentIds) {
        buildChannelGroups(channels, favoriteIds, recentIds)
    }

    fun startDownloadUpdate(release: ReleaseInfo) {
        if (!OtaUpdater.canInstallApk(context)) {
            updateStatus = "Allow 'Install unknown apps' for Tarang TV, then tap Update again."
            OtaUpdater.openUnknownSourcesSettings(context)
            return
        }
        scope.launch {
            updateStatus = "Downloading v${release.versionName}..."
            runCatching {
                OtaUpdater.downloadAndInstall(context, release.apkDownloadUrl) { pct ->
                    downloadProgress = pct
                }
            }.onSuccess {
                updateStatus = "Installer opened for v${release.versionName}"
                downloadProgress = null
            }.onFailure { err ->
                updateStatus = "Update failed: ${err.message}"
                downloadProgress = null
            }
        }
    }

    fun onCheckOrOpenUpdate() {
        val existing = pendingUpdate
        if (existing != null) {
            showUpdateDialog = true
            return
        }
        scope.launch {
            updateStatus = "Checking for updates..."
            val release = runCatching { OtaUpdater.checkForUpdate() }.getOrNull()
            if (release != null) {
                pendingUpdate = release
                updateStatus = null
                showUpdateDialog = true
            } else {
                updateStatus = "Up to date (v${BuildConfig.VERSION_NAME})"
            }
        }
    }

    fun openChannel(channel: Channel) {
        lastChannel = activeChannel
        activeChannel = channel
        lastPlayedId = channel.id
        recentIds = UserPrefs.addRecent(context, channel.id)
    }

    if (showSearchDialog) {
        SearchDialog(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onClear = { searchQuery = ""; showSearchDialog = false },
            onDismiss = { showSearchDialog = false }
        )
    }

    if (showM3uDialog) {
        M3uManagerDialog(
            currentUrl = customM3uUrl,
            isTv = isTv,
            onUrlSaved = { url ->
                UserPrefs.saveCustomM3uUrl(context, url)
                customM3uUrl = url
                activeSource = PlaylistSource("custom", "My M3U", listOf(url))
                showM3uDialog = false
            },
            onUrlCleared = {
                UserPrefs.clearCustomM3uUrl(context)
                customM3uUrl = null
                if (activeSource.id == "custom") {
                    activeSource = M3uRepository.PRESET_SOURCES.first()
                }
                showM3uDialog = false
            },
            onDismiss = { showM3uDialog = false }
        )
    }

    if (showUpdateDialog && pendingUpdate != null) {
        UpdateDialog(
            release = pendingUpdate!!,
            onInstall = {
                showUpdateDialog = false
                startDownloadUpdate(pendingUpdate!!)
            },
            onDismiss = { showUpdateDialog = false }
        )
    }

    if (isTv) {
        BrowseTvContent(
            allSources = allSources,
            activeSource = activeSource,
            groups = groups,
            selectedGroup = selectedGroup,
            searchQuery = searchQuery,
            filteredChannels = filteredChannels,
            favoriteIds = favoriteIds,
            focusedChannel = focusedChannel,
            lastPlayedId = lastPlayedId,
            isLoading = isLoading,
            loadError = loadError,
            livePreviewEnabled = livePreviewEnabled,
            pendingUpdate = pendingUpdate,
            downloadProgress = downloadProgress,
            updateStatus = updateStatus,
            onSelectSource = { activeSource = it },
            onSelectGroup = { selectedGroup = it },
            onFocusChannel = { focusedChannel = it },
            onOpenChannel = ::openChannel,
            onOpenSearch = { showSearchDialog = true },
            onRefresh = { refreshTick++ },
            onOpenM3u = { showM3uDialog = true },
            onToggleLivePreview = {
                livePreviewEnabled = !livePreviewEnabled
                UserPrefs.setLivePreviewEnabled(context, livePreviewEnabled)
            },
            onCheckUpdate = ::onCheckOrOpenUpdate
        )
    } else {
        BrowseMobileContent(
            allSources = allSources,
            activeSource = activeSource,
            groups = groups,
            selectedGroup = selectedGroup,
            searchQuery = searchQuery,
            filteredChannels = filteredChannels,
            favoriteIds = favoriteIds,
            isLoading = isLoading,
            loadError = loadError,
            pendingUpdate = pendingUpdate,
            downloadProgress = downloadProgress,
            updateStatus = updateStatus,
            onSelectSource = { activeSource = it },
            onSelectGroup = { selectedGroup = it },
            onOpenChannel = ::openChannel,
            onToggleFavorite = { ch ->
                favoriteIds = UserPrefs.toggleFavorite(context, ch.id)
            },
            onOpenSearch = { showSearchDialog = true },
            onClearSearch = { searchQuery = "" },
            onRefresh = { refreshTick++ },
            onOpenM3u = { showM3uDialog = true },
            onCheckUpdate = ::onCheckOrOpenUpdate
        )
    }
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BrowseTvContent(
    allSources: List<PlaylistSource>,
    activeSource: PlaylistSource,
    groups: List<Pair<String, Int>>,
    selectedGroup: String,
    searchQuery: String,
    filteredChannels: List<Channel>,
    favoriteIds: Set<String>,
    focusedChannel: Channel?,
    lastPlayedId: String?,
    isLoading: Boolean,
    loadError: String?,
    livePreviewEnabled: Boolean,
    pendingUpdate: ReleaseInfo?,
    downloadProgress: Int?,
    updateStatus: String?,
    onSelectSource: (PlaylistSource) -> Unit,
    onSelectGroup: (String) -> Unit,
    onFocusChannel: (Channel) -> Unit,
    onOpenChannel: (Channel) -> Unit,
    onOpenSearch: () -> Unit,
    onRefresh: () -> Unit,
    onOpenM3u: () -> Unit,
    onToggleLivePreview: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    var previewError by remember { mutableStateOf(false) }

    // Preview player lives inside BrowseTvContent so it releases automatically when entering PlayerScreen.
    val previewPlayer = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("TarangTV/${BuildConfig.VERSION_NAME} (Android TV)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(12_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build().apply {
                volume = 0f
                playWhenReady = true
            }
    }

    DisposableEffect(previewPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                previewError = true
            }
        }
        previewPlayer.addListener(listener)
        onDispose {
            previewPlayer.removeListener(listener)
            previewPlayer.release()
        }
    }

    LaunchedEffect(focusedChannel, livePreviewEnabled) {
        previewError = false
        if (!livePreviewEnabled) {
            previewPlayer.stop()
            previewPlayer.clearMediaItems()
            return@LaunchedEffect
        }
        val target = focusedChannel ?: return@LaunchedEffect
        delay(650)
        previewPlayer.setMediaItem(MediaItem.fromUri(target.streamUrl))
        previewPlayer.prepare()
        previewPlayer.play()
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
                .width(220.dp)
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
                        TvText(
                            text = "Tarang TV",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TvText(
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
                        TvButton(
                            onClick = { onSelectGroup(group) },
                            colors = TvButtonDefaults.colors(
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
                                TvText(
                                    text = group,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TvText(text = "$count", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TvButton(
                        onClick = onOpenSearch,
                        colors = TvButtonDefaults.colors(
                            containerColor = if (searchQuery.isNotBlank()) Color(0xFF0284C7) else Color(0xFF1E293B),
                            contentColor = Color.White,
                            focusedContainerColor = Color(0xFF38BDF8),
                            focusedContentColor = Color.Black
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        TvText(
                            text = if (searchQuery.isNotBlank()) "Filter" else "Search",
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    TvButton(
                        onClick = onRefresh,
                        colors = TvButtonDefaults.colors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color.White,
                            focusedContainerColor = Color(0xFF38BDF8),
                            focusedContentColor = Color.Black
                        )
                    ) {
                        TvText(text = "↻", fontSize = 14.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TvButton(
                        onClick = onOpenM3u,
                        colors = TvButtonDefaults.colors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color.White,
                            focusedContainerColor = Color(0xFF38BDF8),
                            focusedContentColor = Color.Black
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        TvText(text = "M3U / QR", fontSize = 12.sp, maxLines = 1)
                    }
                    TvButton(
                        onClick = onToggleLivePreview,
                        colors = TvButtonDefaults.colors(
                            containerColor = if (livePreviewEnabled) Color(0xFF1E293B) else Color(0xFF334155),
                            contentColor = Color.White,
                            focusedContainerColor = Color(0xFF38BDF8),
                            focusedContentColor = Color.Black
                        )
                    ) {
                        TvText(text = if (livePreviewEnabled) "Prev:On" else "Prev:Off", fontSize = 11.sp)
                    }
                }

                TvButton(
                    onClick = onCheckUpdate,
                    colors = TvButtonDefaults.colors(
                        containerColor = if (pendingUpdate != null) Color(0xFF16A34A) else Color(0xFF334155),
                        contentColor = Color.White,
                        focusedContainerColor = Color(0xFFFACC15),
                        focusedContentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TvText(
                        text = if (pendingUpdate != null) {
                            "Update v${pendingUpdate.versionName}"
                        } else {
                            "Check Updates"
                        },
                        fontSize = 12.sp
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
                    TvText(text = status, color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 2)
                }
            }
        }

        // Right Main Area
        Column(modifier = Modifier.fillMaxSize()) {
            val preview = focusedChannel
            if (preview != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(165.dp)
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
                        TvText(
                            text = "CH ${preview.number.toString().padStart(3, '0')}  •  ${preview.group.uppercase()}",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TvText(
                            text = preview.name,
                            color = Color.White,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TvText(
                            text = if (livePreviewEnabled) {
                                "Press OK for Fullscreen  •  Muted Live Preview"
                            } else {
                                "Press OK for Fullscreen  •  Preview Paused (Low Bandwidth)"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }

                    if (livePreviewEnabled) {
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
                            TvText(
                                text = if (previewError) "OFFLINE" else "PREVIEW",
                                color = if (previewError) Color(0xFFF87171) else Color(0xFF22C55E),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color(0x99000000), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        ChannelLogoBox(channel = preview, size = 76.dp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                items(allSources) { src ->
                    val active = src.id == activeSource.id
                    TvButton(
                        onClick = { onSelectSource(src) },
                        colors = TvButtonDefaults.colors(
                            containerColor = if (active) Color(0xFFF59E0B) else Color(0xFF1E293B),
                            contentColor = if (active) Color.Black else Color.White,
                            focusedContainerColor = Color(0xFF38BDF8),
                            focusedContentColor = Color.Black
                        )
                    ) {
                        TvText(text = src.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            TvText(text = loadError, color = Color(0xFFF87171), fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            TvButton(onClick = onRefresh) {
                                TvText("Retry")
                            }
                        }
                    }
                    filteredChannels.isEmpty() -> {
                        TvText(
                            text = "No channels found. Try another pack or clear search.",
                            color = Color(0xFF94A3B8),
                            fontSize = 16.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 205.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            items(filteredChannels, key = { it.id }) { channel ->
                                val isFav = favoriteIds.contains(channel.id)
                                val cardFocusRequester = remember(channel.id) { FocusRequester() }
                                LaunchedEffect(lastPlayedId) {
                                    if (lastPlayedId == channel.id) {
                                        runCatching { cardFocusRequester.requestFocus() }
                                    }
                                }
                                TvCard(
                                    onClick = { onOpenChannel(channel) },
                                    colors = TvCardDefaults.colors(
                                        containerColor = Color(0xFF111827),
                                        contentColor = Color.White,
                                        focusedContainerColor = Color(0xFF1E293B)
                                    ),
                                    border = TvCardDefaults.border(
                                        focusedBorder = TvBorder(
                                            border = BorderStroke(2.dp, Color(0xFF38BDF8)),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(96.dp)
                                        .focusRequester(cardFocusRequester)
                                        .onFocusChanged { state ->
                                            if (state.isFocused) {
                                                onFocusChannel(channel)
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ChannelLogoBox(channel = channel, size = 44.dp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            TvText(
                                                text = channel.name,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            TvText(
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

@Composable
private fun BrowseMobileContent(
    allSources: List<PlaylistSource>,
    activeSource: PlaylistSource,
    groups: List<Pair<String, Int>>,
    selectedGroup: String,
    searchQuery: String,
    filteredChannels: List<Channel>,
    favoriteIds: Set<String>,
    isLoading: Boolean,
    loadError: String?,
    pendingUpdate: ReleaseInfo?,
    downloadProgress: Int?,
    updateStatus: String?,
    onSelectSource: (PlaylistSource) -> Unit,
    onSelectGroup: (String) -> Unit,
    onOpenChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onOpenSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onRefresh: () -> Unit,
    onOpenM3u: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .safeDrawingPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Mobile Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher),
                    contentDescription = "Tarang TV",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Tarang TV",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} • Live TV",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (searchQuery.isNotBlank()) Color(0xFF38BDF8) else Color.White
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onOpenM3u) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Custom M3U",
                        tint = Color.White
                    )
                }
                Button(
                    onClick = onCheckUpdate,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pendingUpdate != null) Color(0xFF16A34A) else Color(0xFF1E293B),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = if (pendingUpdate != null) "v${pendingUpdate.versionName}" else "Update",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (downloadProgress != null) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { downloadProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF22C55E)
            )
        }
        if (updateStatus != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = updateStatus, color = Color(0xFF94A3B8), fontSize = 11.sp)
        }

        if (searchQuery.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = true,
                    onClick = onClearSearch,
                    label = { Text("Search: $searchQuery", fontSize = 12.sp) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White,
                        selectedTrailingIconColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Playlist Source Pills
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(allSources) { src ->
                val active = src.id == activeSource.id
                FilterChip(
                    selected = active,
                    onClick = { onSelectSource(src) },
                    label = {
                        Text(
                            text = src.label,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color.White,
                        selectedContainerColor = Color(0xFFF59E0B),
                        selectedLabelColor = Color.Black
                    ),
                    border = null
                )
            }
        }

        // Category Group Pills
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            items(groups) { (group, count) ->
                val selected = group == selectedGroup
                FilterChip(
                    selected = selected,
                    onClick = { onSelectGroup(group) },
                    label = { Text("$group ($count)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF111827),
                        labelColor = Color(0xFFCBD5E1),
                        selectedContainerColor = Color(0xFF2563EB),
                        selectedLabelColor = Color.White
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = Color(0xFF1E293B)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                loadError != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = loadError, color = Color(0xFFF87171), fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(onClick = onRefresh) {
                            Text("Retry")
                        }
                    }
                }
                filteredChannels.isEmpty() -> {
                    Text(
                        text = "No channels found. Try another pack or clear search.",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 280.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(filteredChannels, key = { it.id }) { channel ->
                            val isFav = favoriteIds.contains(channel.id)
                            Card(
                                onClick = { onOpenChannel(channel) },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF111827),
                                    contentColor = Color.White
                                ),
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ChannelLogoBox(channel = channel, size = 46.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = channel.name,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "CH ${channel.number.toString().padStart(3, '0')} • ${channel.group}",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { onToggleFavorite(channel) }) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = if (isFav) "Remove favorite" else "Add favorite",
                                            tint = if (isFav) Color(0xFFF59E0B) else Color(0xFF475569)
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

@Composable
fun ChannelLogoBox(channel: Channel, size: Dp) {
    if (!channel.logoUrl.isNullOrBlank()) {
        AsyncImage(
            model = channel.logoUrl,
            contentDescription = channel.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F172A))
                .padding(4.dp)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
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
}

@Composable
private fun SearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        title = { Text("Search Channels", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text("Type channel name...", color = Color(0xFF64748B))
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
            Button(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            OutlinedButton(onClick = onClear) { Text("Clear") }
        }
    )
}

@Composable
private fun M3uManagerDialog(
    currentUrl: String?,
    isTv: Boolean,
    onUrlSaved: (String) -> Unit,
    onUrlCleared: () -> Unit,
    onDismiss: () -> Unit
) {
    var draftUrl by remember(currentUrl) { mutableStateOf(currentUrl.orEmpty()) }
    var inputError by remember { mutableStateOf<String?>(null) }

    val ip = remember { LocalM3uServer.getLocalIpAddress() }
    val serverUrl = remember(ip) { ip?.let { "http://$it:8765" } }
    val qrBitmap = remember(serverUrl) { serverUrl?.let { LocalM3uServer.generateQrBitmap(it) } }

    LaunchedEffect(Unit) {
        val server = LocalM3uServer(onUrlReceived = onUrlSaved)
        try {
            server.start()
        } finally {
            server.stop()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        title = { Text("Custom M3U Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = draftUrl,
                    onValueChange = {
                        draftUrl = it
                        inputError = null
                    },
                    label = { Text("Playlist URL (.m3u / .m3u8)", color = Color(0xFF94A3B8)) },
                    placeholder = { Text("https://example.com/playlist.m3u", color = Color(0xFF64748B)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                inputError?.let { err ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = err, color = Color(0xFFF87171), fontSize = 12.sp)
                }

                if (qrBitmap != null && serverUrl != null && isTv) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Or scan with your phone (same Wi-Fi):\n$serverUrl",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else if (serverUrl != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "LAN Upload URL: $serverUrl",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = draftUrl.trim()
                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        onUrlSaved(trimmed)
                    } else {
                        inputError = "Enter a valid http:// or https:// URL"
                    }
                }
            ) {
                Text("Load")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentUrl != null) {
                    OutlinedButton(onClick = onUrlCleared) {
                        Text("Remove", color = Color(0xFFF87171))
                    }
                }
                OutlinedButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun UpdateDialog(
    release: ReleaseInfo,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        title = {
            Text(
                text = "Update Available: v${release.versionName}",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = release.releaseNotes.ifBlank { "Bug fixes and playback improvements." },
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onInstall,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
            ) {
                Text("Download & Install")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}

private fun buildChannelGroups(
    channels: List<Channel>,
    favoriteIds: Set<String>,
    recentIds: List<String>
): List<Pair<String, Int>> {
    val groupCounts = channels.groupingBy { it.group }.eachCount()
    val priorityOrder = listOf("Movies", "News", "Entertainment", "Music", "Sports", "Kids", "Religious", "General")
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
    return base
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
