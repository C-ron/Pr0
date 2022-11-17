package com.pr0gramm.app.util

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Parcel
import android.text.SpannableStringBuilder
import android.text.style.ClickableSpan
import android.text.style.RelativeSizeSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import com.pr0gramm.app.Settings
import com.pr0gramm.app.decodeBase64
import com.pr0gramm.app.parcel.creator
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.FilterParser
import com.pr0gramm.app.ui.MainActivity
import com.pr0gramm.app.ui.PrivateBrowserSpan
import java.io.File
import java.io.FileInputStream
import java.util.regex.Pattern
import android.text.util.Linkify as AndroidLinkify

object Linkify {
    private val MALICIOUS_COMMENT_CHARS = Pattern.compile("""([\p{Mn}\p{Mc}\p{Me}])[\p{Mn}\p{Mc}\p{Me}]+""")

    private val RE_USERNAME = Pattern.compile("""(?<=^|\s)@[A-Za-z0-9]+""")
    private val RE_GENERIC_LINK =
        Pattern.compile("""(?:https?://)?(?:www\.)?pr0gramm\.com(/(?:new|top|user)/[^\p{javaWhitespace}]*[a-z0-9])""")
    private val RE_GENERIC_SHORT_LINK =
        Pattern.compile("""(?<!reddit.com)/((?:new|top|user)/[^\p{javaWhitespace}]*[a-z0-9'"])""")
    private val RE_WEB_LINK = Pattern.compile("""\bhttps?://(?:[^<>\s]+\([^<>\s]+[^<>!,.:\s]|[^(<>\s]+[^<>)!,.:\s])""")

    private val RE_NEW_LINES = Regex("\r?\n\\s*\n")

    fun linkifyClean(view: TextView, content: String, callback: Callback? = null) {
        var cleanedContent = content.take(1024 * 64).replace(Regex("\n(\\s*?\n){2,}"), "\n\n").trim()
        cleanedContent = MALICIOUS_COMMENT_CHARS.matcher(cleanedContent).replaceAll("$1")
        cleanedContent = RE_GENERIC_LINK.matcher(cleanedContent).replaceAll("$1")

        linkify(view, SpannableStringBuilder.valueOf(cleanedContent), callback)
    }

    fun linkify(
        view: TextView,
        text: SpannableStringBuilder = SpannableStringBuilder(view.text),
        callback: Callback? = null
    ) {
        view.movementMethod = NonCrashingLinkMovementMethod
        view.setTextFuture(linkify(view.context, text, callback))
    }

    fun linkify(context: Context, originalText: CharSequence, callback: Callback? = null): CharSequence {
        val text = SpannableStringBuilder.valueOf(originalText)
        val base = UriHelper.of(context).base()
        val scheme = "https://"

        AndroidLinkify.addLinks(text, RE_WEB_LINK, null, null) { match, _ ->
            Affiliate.get(context, match.group()) ?: match.group()
        }

        AndroidLinkify.addLinks(text, RE_USERNAME, scheme, null) { match, _ ->
            val user = match.group().substring(1)
            base.buildUpon().path("/user").appendEncodedPath(user).toString()
        }

        AndroidLinkify.addLinks(text, RE_GENERIC_SHORT_LINK, scheme, null) { match, _ ->
            base.buildUpon().appendEncodedPath(match.group(1)).toString()
        }


        if (";base64," in originalText) {
            VoiceMessageSpan.addToText(text)
        }

        loop@ for (span in text.getSpans(0, text.length, URLSpan::class.java)) {
            val url = span.url

            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            val flags = text.getSpanFlags(span)

            val uri = Uri.parse(url)

            val replacement: URLSpan? = if (uri.host == "pr0gramm.com") {
                // try to parse the span as a pr0gramm link with a item/comment reference
                val parsed = FilterParser.parse(uri)
                val ref = parsed?.start?.takeIf { it.itemId > 0 }

                when {
                    // direct link to a comment
                    ref?.commentId != null ->
                        CommentSpan(callback, url, Comment(ref.itemId, ref.commentId))

                    // direct link to an item
                    ref != null ->
                        ItemSpan(callback, url, Item(ref.itemId))

                    // open it internally in the app, looks like we understand the format.
                    parsed != null ->
                        InternalURLSpan(url)

                    // nope, never seen this kind of url
                    else -> null
                }

            } else {
                if (Settings.useIncognitoBrowser) {
                    PrivateBrowserSpan(url)
                } else {
                    null
                }
            }

            // replace the current span with the new span.
            if (replacement != null) {
                text.removeSpan(span)
                text.setSpan(replacement, start, end, flags)
            }
        }

        RE_NEW_LINES.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1

            text.setSpan(RelativeSizeSpan(0.5f), start, end, 0)
        }

        return text
    }

    interface Callback {
        fun itemClicked(ref: Item): Boolean
        fun commentClicked(ref: Comment): Boolean
    }

    class Item(val item: Long)
    class Comment(val item: Long, val comment: Long)
}

private open class InternalURLSpan(url: String) : URLSpan(url) {
    val url: String get() = getURL()

    override fun onClick(widget: View) {
        widget.context.startActivity<MainActivity> { intent ->
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse(url)
        }
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(getURL())
    }

    companion object {
        @JvmField
        val CREATOR = creator { p -> InternalURLSpan(p.readString() ?: "https://example.com") }
    }
}

private class ItemSpan(val callback: Linkify.Callback?, url: String, val ref: Linkify.Item) : InternalURLSpan(url) {
    override fun onClick(widget: View) {
        if (callback?.itemClicked(ref) == true) {
            return
        }

        super.onClick(widget)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(url)
        dest.writeLong(ref.item)
    }

    companion object {
        @JvmField
        val CREATOR = creator { p ->
            val url = p.readString()!!
            val ref = Linkify.Item(p.readLong())
            ItemSpan(null, url, ref)
        }
    }
}

private class CommentSpan(val callback: Linkify.Callback?, url: String, val ref: Linkify.Comment) : InternalURLSpan(url) {
    override fun onClick(widget: View) {
        if (callback?.commentClicked(ref) == true) {
            return
        }

        super.onClick(widget)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(url)
        dest.writeLong(ref.item)
        dest.writeLong(ref.comment)
    }

    companion object {
        @JvmField
        val CREATOR = creator { p ->
            val url = p.readString()!!
            val ref = Linkify.Comment(p.readLong(), p.readLong())
            CommentSpan(null, url, ref)
        }
    }
}

private class VoiceMessageSpan(val content: ByteArray) : ClickableSpan() {
    override fun onClick(widget: View) {
        doInBackground { play() }
    }

    private fun play() {
        // clear the previous player instance if available
        catchAll {
            previousInstance?.reset()
        }

        val mp = MediaPlayer().also { previousInstance = it }
        mp.setAudioStreamType(AudioManager.STREAM_MUSIC)

        // handle events
        mp.setOnCompletionListener {
            previousInstance = null
            mp.release()
        }

        mp.setOnPreparedListener { mp.start() }

        // set input data
        val temp = File.createTempFile("audio", ".ogg").apply {
            deleteOnExit()
            writeBytes(content)
        }

        FileInputStream(temp).use { stream ->
            mp.setDataSource(stream.fd)
        }

        // and start playback
        mp.prepareAsync()
    }

    companion object {
        private val mimeTypes = listOf("audio/mpeg", "audio/mp3", "audio/mp4", "audio/ogg", "media/ogg").joinToString("|")
        private val regex = "data:(?:$mimeTypes);base64,([a-zA-Z0-9/+]+)=*".toRegex()

        private var previousInstance: MediaPlayer? by weakref(null)

        fun addToText(text: SpannableStringBuilder) {
            regex.findAll(text).toList().reversed().forEach { match ->
                val (encoded) = match.destructured
                val bytes = encoded.decodeBase64(urlSafe = false)

                val replacement = "\u25B6 Sprachnachricht"
                text.replace(match.range.start, match.range.last + 1, replacement)
                text.setSpan(VoiceMessageSpan(bytes),
                        match.range.start, match.range.start + replacement.length,
                        SpannableStringBuilder.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }

    }
}
