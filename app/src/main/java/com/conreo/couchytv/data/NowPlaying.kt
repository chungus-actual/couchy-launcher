package com.conreo.couchytv.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.conreo.couchytv.MediaListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Fork: what is playing on the box, for the status bar's now-playing chip (the track and basic media
 * controls on the home screen).
 *
 * Shown while a session is PLAYING, and for PAUSED_FOR after it pauses so it can be resumed from the
 * home screen; after that a paused session (Spotify keeps one forever) is not "something playing".
 */
data class NowPlaying(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val controls: MediaController.TransportControls,
)

private const val PAUSED_FOR = 10 * 60_000L

/**
 * Emits the session worth showing, or null. Needs notification-listener access for [MediaListener]
 * (granted once over adb: cmd notification allow_listener <pkg>/com.conreo.couchytv.MediaListener);
 * without it this emits null and the chip simply never appears.
 */
fun nowPlayingFlow(context: Context): Flow<NowPlaying?> = callbackFlow {
    val watcher = Watcher(context) { trySend(it) }
    watcher.start()
    awaitClose { watcher.stop() }
}.distinctUntilChanged()

private class Watcher(context: Context, private val emit: (NowPlaying?) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MediaListener::class.java)
    private var controller: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = push()
        override fun onPlaybackStateChanged(state: PlaybackState?) = push()
        override fun onSessionDestroyed() = pick(null)
    }
    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }
    // a paused session ages out with no event to say so: look again now and then
    private val recheck = object : Runnable {
        override fun run() { push(); main.postDelayed(this, 30_000L) }
    }
    // listener access granted after the launcher started: try again rather than never
    private val retry = Runnable { start() }
    // The cover comes back as a NEW Bitmap on every metadata read (it is parceled), so equality never
    // held and every media callback recomposed the launcher; one scaled copy per track instead.
    private var artKey: String? = null
    private var artCache: Bitmap? = null

    fun start() {
        try {
            msm.addOnActiveSessionsChangedListener(sessions, component, main)
            pick(msm.getActiveSessions(component))
        } catch (_: SecurityException) {
            emit(null)          // no listener access (yet): no chip
            main.postDelayed(retry, 60_000L)
            return
        }
        main.postDelayed(recheck, 30_000L)
    }

    fun stop() {
        main.removeCallbacks(retry)
        main.removeCallbacks(recheck)
        runCatching { msm.removeOnActiveSessionsChangedListener(sessions) }
        controller?.unregisterCallback(callback)
        controller = null
    }

    private fun pick(list: List<MediaController>?) {
        val all = list ?: runCatching { msm.getActiveSessions(component) }.getOrDefault(emptyList())
        // the one that is playing, else the most recent
        val c = all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: all.firstOrNull()
        if (c?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = c
            c?.registerCallback(callback, main)
        }
        push()
    }

    private fun push() {
        val c = controller
        val m = c?.metadata
        val st = c?.playbackState
        if (c == null || m == null || st == null) { emit(null); return }
        val playing = st.state == PlaybackState.STATE_PLAYING || st.state == PlaybackState.STATE_BUFFERING
        val recentPause = st.state == PlaybackState.STATE_PAUSED &&
            SystemClock.elapsedRealtime() - st.lastPositionUpdateTime < PAUSED_FOR
        val title = m.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        if ((!playing && !recentPause) || title.isEmpty()) { emit(null); return }
        emit(NowPlaying(
            title = title,
            artist = (m.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
            art = art(m, title),
            playing = playing,
            canPrevious = st.actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canNext = st.actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            controls = c.transportControls,
        ))
    }

    /** The cover at chip size (40 dp = 80 px at density 320; 96 leaves room for the focus zoom), kept
     *  per track. */
    private fun art(m: MediaMetadata, title: String): Bitmap? {
        val src = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        // the track and the picture's size: Spotify sends the bitmap a moment after the text
        val key = src?.let { "$title|${it.width}x${it.height}" }
        if (key != artKey) {
            artKey = key
            artCache = src?.let {
                if (it.width <= 96) it
                else Bitmap.createScaledBitmap(it, 96, (96 * it.height / it.width).coerceAtLeast(1), true)
            }
        }
        return artCache
    }
}
