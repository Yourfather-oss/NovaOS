package com.nova.os.data

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import com.nova.os.NovaNotificationListener

class NowPlaying(
    val pkg: String,
    val title: String,
    val artist: String,
    val playing: Boolean,
    val art: ImageBitmap?,
    val controller: MediaController
) {
    fun sameAs(other: NowPlaying?): Boolean =
        other != null && pkg == other.pkg && title == other.title && artist == other.artist && playing == other.playing
}

fun hasListenerAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** The active media session, if any. Needs notification access for NOVA. */
fun readNowPlaying(context: Context): NowPlaying? = try {
    val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    val sessions = msm.getActiveSessions(ComponentName(context, NovaNotificationListener::class.java))
    val chosen = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        ?: sessions.firstOrNull { it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) != null }
    chosen?.let { c ->
        val meta = c.metadata
        val art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        NowPlaying(
            pkg = c.packageName,
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Playing",
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            art = art?.asImageBitmap(),
            controller = c
        )
    }
} catch (e: SecurityException) {
    null
} catch (e: Exception) {
    null
}
