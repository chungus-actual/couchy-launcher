package com.conreo.couchytv.data

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Environment
import java.io.File

/**
 * Fork: the reels wallpaper's source. Every video file in [dir] (relative to shared storage, e.g.
 * "Movies/Reels"), as file URIs ExoPlayer plays in place. Drop a file in the folder and it joins the
 * rotation; nothing is copied. Needs [PERMISSION] (asked for when Reels is picked in the wallpaper
 * settings); without it the list is empty and the launcher falls through to the next wallpaper.
 */
object Reels {
    /** READ_MEDIA_VIDEO from Android 13, the storage permission before it. */
    val PERMISSION: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private val TYPES = setOf("mp4", "mkv", "webm", "mov", "m4v")

    fun list(dir: String): List<String> {
        @Suppress("DEPRECATION")
        val root = File(Environment.getExternalStorageDirectory(), dir.trim('/'))
        return root.listFiles { f -> f.isFile && f.extension.lowercase() in TYPES && f.length() > 0 }
            ?.sortedBy { it.name }
            ?.map { Uri.fromFile(it).toString() }
            .orEmpty()
    }
}
