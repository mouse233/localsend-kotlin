package io.github.mouse233.localsendkotlin.sharing

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** Extracts file and text content sent to the app through the Android Sharesheet. */
internal object ShareIntentParser {
    data class SharedContent(
        val uris: List<Uri>,
        val text: String?
    )

    fun supportsAction(action: String?): Boolean = action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE

    fun parse(intent: Intent): SharedContent? {
        if (!supportsAction(intent.action)) return null

        val uris = LinkedHashSet<Uri>()
        intent.clipData?.let { clipData ->
            for (index in 0 until clipData.itemCount) {
                clipData.getItemAt(index).uri?.let(uris::add)
            }
        }
        IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?.forEach(uris::add)
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(uris::add)

        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
            ?.takeIf(String::isNotBlank)
        if (uris.isEmpty() && text == null) return null
        return SharedContent(uris.toList(), text)
    }
}
