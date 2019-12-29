package com.pr0gramm.app.ui.views.viewer

import androidx.collection.LruCache
import com.google.android.exoplayer2.SimpleExoPlayer

object SeekController {
    private val seekToCache = LruCache<Long, ExpiringTimestamp>(16)

    fun store(id: Long, exo: SimpleExoPlayer) {
        seekToCache.put(id, ExpiringTimestamp(exo.currentPosition))
    }

    fun restore(id: Long, exo: SimpleExoPlayer) {
        // restore seek position if known
        val seekTo = seekToCache.get(id)
        if (seekTo != null && seekTo.valid) {
            exo.seekTo(seekTo.timestamp)
        }
    }

    /**
     * This timestamp value is only valid for 60 seconds.
     */
    private class ExpiringTimestamp(val timestamp: Long) {
        private val created: Long = System.currentTimeMillis()
        val valid: Boolean get() = (System.currentTimeMillis() - created) < 60 * 1000
    }
}