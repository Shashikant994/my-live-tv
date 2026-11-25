package com.tvapp.livetv.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvapp.livetv.BuildConfig
import com.tvapp.livetv.model.Channel
import com.tvapp.livetv.model.M3uRepository
import com.tvapp.livetv.player.PlayerScreen
import com.tvapp.livetv.updater.OtaUpdater
import com.tvapp.livetv.updater.ReleaseInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf("All") }
    var activeChannel by remember { mutableStateOf<Channel?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var pendingUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        try {
            channels = M3uRepository.fetchPlaylist()
            isLoading = false
        } catch (e: Exception) {
            loadError = e.message ?: "Failed to load channels"
            isLoading = false
        }

        // Silent startup OTA check
        runCatching { OtaUpdater.checkForUpdate() }.onSuccess { release ->
            pendingUpdate = release
        }
    }

    val currentPlaying = activeChannel
    if (currentPlaying != null) {
        val visibleList = remember(channels, selectedGroup) {
            if (selectedGroup == "All") channels else channels.filter { it.group == selectedGroup }
        }
        val idx = visibleList.indexOfFirst { it.id == currentPlaying.id }.coerceAtLeast(0)

        PlayerScreen(
            channel = currentPlaying,
            onNextChannel = {
                if (visibleList.isNotEmpty()) {
                    activeChannel = visibleList[(idx + 1) % visibleList.size]
                }
            },
            onPreviousChannel = {
                if (visibleList.isNotEmpty()) {
                    val prev = if (idx - 1 < 0) visibleList.lastIndex else idx - 1
                    activeChannel = visibleList[prev]
                }
            },
            onBack = { activeChannel = null }
        )
        return
    }

    val groups = remember(channels) {
        listOf("All") + channels.map { it.group }.distinct().sorted()
    }
    val filteredChannels = remember(channels, selectedGroup) {
        if (selectedGroup == "All") channels else channels.filter { it.group == selectedGroup }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(24.dp)
    ) {
        // Left Sidebar: Categories + OTA Update Button
        Column(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .padding(end = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LiveTV",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    color = Color(0xFF64748B),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(groups) { group ->
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
                            Text(
                                text = group,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                                updateStatus = "App is up to date (v${BuildConfig.VERSION_NAME})"
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

        // Right Content: Channel Grid
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
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 200.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(filteredChannels, key = { it.id }) { channel ->
                            Card(
                                onClick = { activeChannel = channel },
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
                                    .height(110.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = channel.name,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = channel.group,
                                        color = Color(0xFF38BDF8),
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
