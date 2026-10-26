package com.conreo.couchytv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.conreo.couchytv.R
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.conreo.couchytv.data.NetStatus
import com.conreo.couchytv.data.NowPlaying
import com.conreo.couchytv.data.FeedLine

@Composable
fun StatusBar(
    net: NetStatus,
    time: String,
    date: String,
    showVpn: Boolean,
    glass: Boolean,
    onVpnClick: () -> Unit,
    onNetworkClick: () -> Unit,
    onSettingsClick: () -> Unit,
    nowPlaying: NowPlaying? = null,
    feedLine: FeedLine? = null,
    onScreensaver: () -> Unit = {},
) {
    // Optional glass panel behind the icon cluster — same look as the dock
    // panel, corners follow the roundness setting.
    // fork: the same glass as the dock (ForkLook.kt)
    val cluster = if (glass) {
        Modifier
            .glassPanel(smoothShape(LocalCornerRadius.current))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    } else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      if (feedLine != null) {
        // fork: the feed line, left edge; it takes the room the right side leaves
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            FeedLineText(feedLine, if (glass) cluster.padding(vertical = 4.dp) else Modifier)
        }
        Spacer(Modifier.width(16.dp))
      }
      if (nowPlaying != null) {
        // fork: what is playing, with its transport, left of the clock cluster
        NowPlayingChip(nowPlaying, if (glass) cluster else Modifier, onScreensaver)
        Spacer(Modifier.width(16.dp))
      }
      Row(
        modifier = cluster,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Order: VPN · network · settings · date · clock (clock far right)
        if (showVpn) {
            StatusIcon(
                icon = AppIcons.Vpn,
                active = net.vpn,
                contentDescription = stringResource(R.string.cd_vpn),
                onClick = onVpnClick,
            )
        }
        StatusIcon(
            icon = when {
                net.ethernet -> AppIcons.Ethernet
                net.wifi -> AppIcons.Wifi
                else -> AppIcons.WifiOff
            },
            active = net.connected,
            contentDescription = stringResource(R.string.cd_network),
            onClick = onNetworkClick,
        )
        StatusIcon(
            icon = AppIcons.Gear,
            active = true,
            contentDescription = stringResource(R.string.cd_settings),
            onClick = onSettingsClick,
        )
        if (date.isNotEmpty()) {
            Text(
                text = date,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Text(
            // Tabular figures: digits keep a fixed width so the clock never jitters.
            text = time,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = Color.White,
            modifier = Modifier.padding(start = 10.dp),
        )
      }
    }
}

/**
 * Fork: the feed line, the weather over the feed's top item (data/FeedLine.kt). Not focusable: it is
 * a reading, not a control. The kicker takes the item's tone: warm for something that needs a person
 * ("needs", "fault"), teal for something happening now ("live"), amber otherwise.
 */
@Composable
private fun FeedLineText(h: FeedLine, panel: Modifier) {
    Column(panel.widthIn(max = 560.dp)) {
        if (h.weather.isNotEmpty()) {
            Text(
                text = h.weather,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
            )
        }
        if (h.headline.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (h.kicker.isNotEmpty()) {
                    Text(
                        text = h.kicker,
                        style = MaterialTheme.typography.labelMedium,
                        color = when (h.tone) {
                            "needs", "fault" -> Color(0xFFF0A070)
                            "live" -> Color(0xFF7FD6CF)
                            else -> Color(0xFFE3B277)
                        },
                        maxLines = 1,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Text(
                    text = h.headline,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Fork: the playing track and prev / play-pause / next, then a button that starts the screensaver.
 * Each button is a StatusIcon, so it focuses and scales like the rest of the bar and D-pad up from the
 * apps reaches it. Skip buttons only when the playing app offers them.
 */
@Composable
private fun NowPlayingChip(np: NowPlaying, panel: Modifier, onScreensaver: () -> Unit) {
    Row(
        modifier = panel,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        np.art?.let { art ->
            Image(
                bitmap = art.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(smoothShape(6.dp)),
            )
        }
        Column(Modifier.widthIn(max = 240.dp).padding(start = 4.dp, end = 6.dp)) {
            Text(
                text = np.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (np.artist.isNotEmpty()) {
                Text(
                    text = np.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (np.canPrevious) {
            StatusIcon(icon = AppIcons.Previous, active = true, contentDescription = stringResource(R.string.fork_np_previous),
                onClick = { np.controls.skipToPrevious() })
        }
        StatusIcon(
            icon = if (np.playing) AppIcons.Pause else AppIcons.Play,
            active = true,
            contentDescription = stringResource(if (np.playing) R.string.fork_np_pause else R.string.fork_np_play),
            onClick = { if (np.playing) np.controls.pause() else np.controls.play() },
        )
        if (np.canNext) {
            StatusIcon(icon = AppIcons.Next, active = true, contentDescription = stringResource(R.string.fork_np_next),
                onClick = { np.controls.skipToNext() })
        }
        // the screensaver now, instead of waiting for it (a music visualizer, if that is the screensaver)
        StatusIcon(icon = AppIcons.Visualizer, active = true, contentDescription = stringResource(R.string.fork_screensaver),
            onClick = onScreensaver)
    }
}

@Composable
private fun StatusIcon(
    icon: ImageVector,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.22f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.18f),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = if (active) 0.95f else 0.35f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
