package com.tvapp.livetv.player

import android.app.Activity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.tvapp.livetv.model.Channel
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
@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    channel: Channel,
    isFavorite: Boolean,
    hasPreviousRecall: Boolean,
    onToggleFavorite: () -> Unit,
    onNextChannel: () -> Unit,
    onPreviousChannel: () -> Unit,
    onRecallLastChannel: () -> Unit,
    onJumpToNumber: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var qualityPreset by remember { mutableStateOf(QualityPreset.AUTO) }
    var aspectPreset by remember { mutableStateOf(AspectPreset.FIT) }
    var numberBuffer by remember { mutableStateOf("") }
    var audioTrackIndex by remember { mutableIntStateOf(0) }
    var audioTrackCount by remember { mutableIntStateOf(1) }

    val rootFocusRequester = remember { FocusRequester() }
    val trackSelector = remember { DefaultTrackSelector(context) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build().apply {
                playWhenReady = true
            }
    }

    // Keep TV screen awake during playback
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(channel.streamUrl) {
        isBuffering = true
        errorMessage = null
        showControls = true
        exoPlayer.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        exoPlayer.prepare()
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

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(5000)
            showControls = false
            rootFocusRequester.requestFocus()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
            }

            override fun onTracksChanged(tracks: Tracks) {
                val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
                audioTrackCount = audioGroups.size.coerceAtLeast(1)
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                errorMessage = "Channel offline or unavailable (${error.errorCodeName}). Press Up/Down for next."
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    BackHandler {
        if (showControls) {
            showControls = false
            rootFocusRequester.requestFocus()
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
                            if (!showControls) { onNextChannel(); true } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!showControls) { onPreviousChannel(); true } else false
                        }
                        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                            showControls = !showControls; true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (!showControls) {
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
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xD90F172A), RoundedCornerShape(12.dp))
                    .padding(horizontal = 28.dp, vertical = 18.dp)
            ) {
                Text(text = msg, color = Color(0xFFF87171), fontSize = 18.sp)
            }
        }

        if (showControls) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xF2090D16))
                        )
                    )
                    .padding(horizontal = 36.dp, vertical = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CH ${channel.number.toString().padStart(3, '0')}  •  ${channel.name}",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${channel.group}  •  0-9: Jump Channel  •  Up/Down: Next/Prev",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        text = "● LIVE",
                        color = Color(0xFF22C55E),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlayerActionButton(
                        label = if (exoPlayer.isPlaying) "Pause" else "Play",
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            showControls = true
                        }
                    )
                    PlayerActionButton(
                        label = "Quality: ${qualityPreset.label}",
                        onClick = {
                            val vals = QualityPreset.entries
                            qualityPreset = vals[(vals.indexOf(qualityPreset) + 1) % vals.size]
                            showControls = true
                        }
                    )
                    PlayerActionButton(
                        label = "Screen: ${aspectPreset.label}",
                        onClick = {
                            val vals = AspectPreset.entries
                            aspectPreset = vals[(vals.indexOf(aspectPreset) + 1) % vals.size]
                            showControls = true
                        }
                    )
                    if (audioTrackCount > 1) {
                        PlayerActionButton(
                            label = "Audio ${audioTrackIndex + 1}/$audioTrackCount",
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
                        onClick = { onToggleFavorite(); showControls = true }
                    )
                    if (hasPreviousRecall) {
                        PlayerActionButton(
                            label = "Last CH",
                            onClick = { onRecallLastChannel(); showControls = true }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerActionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF1E293B),
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF38BDF8),
            focusedContentColor = Color.Black
        )
    ) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
