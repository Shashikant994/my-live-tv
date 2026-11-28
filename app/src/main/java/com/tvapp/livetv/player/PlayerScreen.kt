package com.tvapp.livetv.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border as TvBorder
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text as TvText
import com.tvapp.livetv.BuildConfig
import com.tvapp.livetv.model.Channel
import com.tvapp.livetv.ui.ChannelLogoBox
import kotlinx.coroutines.delay

enum class QualityPreset(val label: String, val maxHeight: Int) {
    AUTO("Auto", Int.MAX_VALUE),
    HD_720("720p", 720),
    SD_480("480p", 480),
    LOW_360("360p", 360)
}

enum class AspectPreset(val label: String, val resizeMode: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channel: Channel,
    visibleChannels: List<Channel>,
    isTv: Boolean,
    isFavorite: Boolean,
    hasPreviousRecall: Boolean,
    onToggleFavorite: () -> Unit,
    onNextChannel: () -> Unit,
    onPreviousChannel: () -> Unit,
    onSelectChannel: (Channel) -> Unit,
    onRecallLastChannel: () -> Unit,
    onJumpToNumber: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var isBuffering by remember { mutableStateOf(true) }
    var isPlayingState by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    var showChannelDrawer by remember { mutableStateOf(false) }
    var qualityPreset by remember { mutableStateOf(QualityPreset.AUTO) }
    var aspectPreset by remember { mutableStateOf(AspectPreset.FIT) }
    var numberBuffer by remember { mutableStateOf("") }
    var audioTrackIndex by remember { mutableIntStateOf(0) }
    var audioTrackCount by remember { mutableIntStateOf(1) }
    var videoResolution by remember { mutableStateOf<String?>(null) }
    var forceLandscape by remember { mutableStateOf(false) }

    val rootFocusRequester = remember { FocusRequester() }
    val trackSelector = remember { DefaultTrackSelector(context) }

    val exoPlayer = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("TarangTV/${BuildConfig.VERSION_NAME} (Android)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(10_000, 30_000, 1_500, 3_000)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build().apply {
                playWhenReady = true
            }
    }

    // Keep screen awake + immersive fullscreen bars on mobile
    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (!isTv) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    LaunchedEffect(forceLandscape) {
        if (!isTv && activity != null) {
            activity.requestedOrientation = if (forceLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    LaunchedEffect(channel.streamUrl) {
        retryCount = 0
        audioTrackIndex = 0
        videoResolution = null
        trackSelector.parameters = trackSelector.buildUponParameters()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .build()
    }

    LaunchedEffect(channel.streamUrl, retryTrigger) {
        isBuffering = true
        errorMessage = null
        showControls = true
        exoPlayer.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Auto-retry transient stream failures up to 2 times before showing error
    LaunchedEffect(errorMessage, retryCount) {
        if (errorMessage != null && retryCount < 2) {
            delay(1500)
            retryCount++
            retryTrigger++
        }
    }

    LaunchedEffect(qualityPreset) {
        val builder = trackSelector.buildUponParameters()
        if (qualityPreset == QualityPreset.AUTO) {
            builder.clearVideoSizeConstraints()
        } else {
            builder.setMaxVideoSize(Int.MAX_VALUE, qualityPreset.maxHeight)
        }
        trackSelector.parameters = builder.build()
    }

    LaunchedEffect(numberBuffer) {
        if (numberBuffer.isNotEmpty()) {
            delay(1300)
            numberBuffer.toIntOrNull()?.let { onJumpToNumber(it) }
            numberBuffer = ""
        }
    }

    LaunchedEffect(showControls, showChannelDrawer) {
        if (showControls && !showChannelDrawer) {
            delay(5000)
            showControls = false
            if (isTv) runCatching { rootFocusRequester.requestFocus() }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    errorMessage = null
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) {
                    videoResolution = "${videoSize.height}p"
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
                audioTrackCount = audioGroups.size.coerceAtLeast(1)
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                errorMessage = "Stream unavailable (${error.errorCodeName})"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(Unit) {
        if (isTv) runCatching { rootFocusRequester.requestFocus() }
    }

    BackHandler {
        when {
            showChannelDrawer -> {
                showChannelDrawer = false
                if (isTv) runCatching { rootFocusRequester.requestFocus() }
            }
            showControls -> {
                showControls = false
                if (isTv) runCatching { rootFocusRequester.requestFocus() }
            }
            else -> onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (showChannelDrawer) {
                            showChannelDrawer = false
                        } else {
                            showControls = !showControls
                        }
                    }
                )
            }
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val code = event.nativeKeyEvent.keyCode
                    if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                        val digit = (code - KeyEvent.KEYCODE_0).toString()
                        if (numberBuffer.length < 4) numberBuffer += digit
                        return@onPreviewKeyEvent true
                    }
                    when (code) {
                        KeyEvent.KEYCODE_CHANNEL_UP -> {
                            onNextChannel(); true
                        }
                        KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            onPreviousChannel(); true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (!showControls && !showChannelDrawer) { onNextChannel(); true } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!showControls && !showChannelDrawer) { onPreviousChannel(); true } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!showControls && !showChannelDrawer) {
                                showChannelDrawer = true
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                            showControls = !showControls
                            if (showControls) showChannelDrawer = false
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (!showControls && !showChannelDrawer) {
                                showControls = true
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = true
                }
            },
            update = { pv ->
                pv.resizeMode = aspectPreset.resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering) {
            CircularProgressIndicator(
                color = Color(0xFF38BDF8),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (numberBuffer.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp)
                    .background(Color(0xE60F172A), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "CH ${numberBuffer.padStart(3, '0')}",
                    color = Color(0xFF38BDF8),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        errorMessage?.let { msg ->
            if (retryCount >= 2) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xE60F172A), RoundedCornerShape(14.dp))
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = msg,
                        color = Color(0xFFF87171),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlayerActionButton(
                            label = "Retry",
                            isTv = isTv,
                            onClick = {
                                retryCount = 0
                                retryTrigger++
                            }
                        )
                        PlayerActionButton(
                            label = "Next Channel",
                            isTv = isTv,
                            onClick = onNextChannel
                        )
                    }
                }
            }
        }

        if (showChannelDrawer) {
            QuickChannelDrawer(
                channels = visibleChannels,
                currentChannelId = channel.id,
                isTv = isTv,
                onSelect = { ch ->
                    showChannelDrawer = false
                    onSelectChannel(ch)
                },
                onClose = { showChannelDrawer = false },
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }

        if (showControls && !showChannelDrawer) {
            // Top bar for touch Back + Live info
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xCC090D16), Color.Transparent)
                        )
                    )
                    .safeDrawingPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isTv) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "CH ${channel.number.toString().padStart(3, '0')}  •  ${channel.name}",
                            color = Color.White,
                            fontSize = if (isTv) 22.sp else 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isTv) {
                                "${channel.group}  •  0-9: Jump  •  Left: Channels  •  Up/Down: Zap"
                            } else {
                                "${channel.group}  •  Tap video to hide controls"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    videoResolution?.let { res ->
                        Text(
                            text = res,
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color(0x991E293B), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = "● LIVE",
                        color = Color(0xFF22C55E),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom Action Bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xF2090D16))
                        )
                    )
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    PlayerActionButton(
                        label = "Prev CH",
                        isTv = isTv,
                        onClick = { onPreviousChannel(); showControls = true }
                    )
                    PlayerActionButton(
                        label = "Next CH",
                        isTv = isTv,
                        onClick = { onNextChannel(); showControls = true }
                    )
                    PlayerActionButton(
                        label = "Channels",
                        isTv = isTv,
                        onClick = {
                            showControls = false
                            showChannelDrawer = true
                        }
                    )
                    PlayerActionButton(
                        label = if (isPlayingState) "Pause" else "Play",
                        isTv = isTv,
                        onClick = {
                            if (isPlayingState) exoPlayer.pause() else exoPlayer.play()
                            showControls = true
                        }
                    )
                    PlayerActionButton(
                        label = "Quality: ${qualityPreset.label}",
                        isTv = isTv,
                        onClick = {
                            val vals = QualityPreset.entries
                            qualityPreset = vals[(vals.indexOf(qualityPreset) + 1) % vals.size]
                            showControls = true
                        }
                    )
                    PlayerActionButton(
                        label = "Screen: ${aspectPreset.label}",
                        isTv = isTv,
                        onClick = {
                            val vals = AspectPreset.entries
                            aspectPreset = vals[(vals.indexOf(aspectPreset) + 1) % vals.size]
                            showControls = true
                        }
                    )
                    if (audioTrackCount > 1) {
                        PlayerActionButton(
                            label = "Audio ${audioTrackIndex + 1}/$audioTrackCount",
                            isTv = isTv,
                            onClick = {
                                val groups = exoPlayer.currentTracks.groups
                                    .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
                                if (groups.isNotEmpty()) {
                                    audioTrackIndex = (audioTrackIndex + 1) % groups.size
                                    val override = TrackSelectionOverride(groups[audioTrackIndex].mediaTrackGroup, 0)
                                    trackSelector.parameters = trackSelector.buildUponParameters()
                                        .setOverrideForType(override)
                                        .build()
                                }
                                showControls = true
                            }
                        )
                    }
                    PlayerActionButton(
                        label = if (isFavorite) "★ Saved" else "☆ Favorite",
                        isTv = isTv,
                        onClick = { onToggleFavorite(); showControls = true }
                    )
                    if (hasPreviousRecall) {
                        PlayerActionButton(
                            label = "Last CH",
                            isTv = isTv,
                            onClick = { onRecallLastChannel(); showControls = true }
                        )
                    }
                    if (!isTv) {
                        PlayerActionButton(
                            label = if (forceLandscape) "Auto Rotate" else "Landscape",
                            isTv = false,
                            onClick = {
                                forceLandscape = !forceLandscape
                                showControls = true
                            }
                        )
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickChannelDrawer(
    channels: List<Channel>,
    currentChannelId: String,
    isTv: Boolean,
    onSelect: (Channel) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(Color(0xEB090D16))
            .safeDrawingPadding()
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Channels (${channels.size})",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            PlayerActionButton(label = "Close", isTv = isTv, onClick = onClose)
        }
        Spacer(modifier = Modifier.height(10.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(channels, key = { it.id }) { ch ->
                val isCurrent = ch.id == currentChannelId
                if (isTv) {
                    TvCard(
                        onClick = { onSelect(ch) },
                        colors = TvCardDefaults.colors(
                            containerColor = if (isCurrent) Color(0xFF1D4ED8) else Color(0xFF111827),
                            contentColor = Color.White,
                            focusedContainerColor = Color(0xFF1E293B)
                        ),
                        border = TvCardDefaults.border(
                            focusedBorder = TvBorder(
                                border = BorderStroke(2.dp, Color(0xFF38BDF8)),
                                shape = RoundedCornerShape(10.dp)
                            )
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        QuickChannelRowContent(ch = ch, isCurrent = isCurrent)
                    }
                } else {
                    Card(
                        onClick = { onSelect(ch) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) Color(0xFF1D4ED8) else Color(0xFF111827),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        QuickChannelRowContent(ch = ch, isCurrent = isCurrent)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickChannelRowContent(ch: Channel, isCurrent: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLogoBox(channel = ch, size = 36.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ch.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "CH ${ch.number.toString().padStart(3, '0')} • ${ch.group}",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerActionButton(
    label: String,
    isTv: Boolean,
    onClick: () -> Unit
) {
    if (isTv) {
        TvButton(
            onClick = onClick,
            colors = TvButtonDefaults.colors(
                containerColor = Color(0xFF1E293B),
                contentColor = Color.White,
                focusedContainerColor = Color(0xFF38BDF8),
                focusedContentColor = Color.Black
            )
        ) {
            TvText(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xCC1E293B),
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
