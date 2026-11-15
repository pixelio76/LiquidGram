package org.liquidgram.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.liquidgram.data.TelegramRepository
import java.io.File

/** What another app handed us through the share sheet. */
class Shared(val text: String, val uris: List<Uri>)

/** Things that arrive from outside the app (share sheet, notification taps), picked up by the navigation. */
object Incoming {
    var share by mutableStateOf<Shared?>(null)
    var openChat by mutableStateOf<Long?>(null)

    fun handle(intent: Intent?) {
        intent ?: return
        intent.getLongExtra("chatId", 0L).takeIf { it != 0L }?.let { openChat = it; return }
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelable(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableList(Intent.EXTRA_STREAM)
            else -> return
        }
        // Links usually come with a subject (video title); keep both, like Telegram does.
        val text = listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
            .filter { it.isNotBlank() }.distinct().joinToString("\n")
        if (text.isNotBlank() || uris.isNotEmpty()) share = Shared(text, uris)
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelable(key: String): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, Uri::class.java) else getParcelableExtra(key)

    @Suppress("DEPRECATION")
    private fun Intent.parcelableList(key: String): List<Uri> =
        (if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(key, Uri::class.java) else getParcelableArrayListExtra(key)).orEmpty()
}

/** Share sheet target: pick a chat, everything shared goes there, then that chat opens. */
@Composable
fun ShareScreen(repo: TelegramRepository, shared: Shared, onSent: (Long) -> Unit, onBack: () -> Unit) = FormPage("Send to…", onBack) { _ ->
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chats = Plugins.chats(repo.chats.collectAsState().value)
    var sending by androidx.compose.runtime.remember { mutableStateOf(false) }
    val what = listOfNotNull(
        shared.text.takeIf { it.isNotBlank() }?.let { if (it.length > 120) it.take(120) + "…" else it },
        shared.uris.size.takeIf { it > 0 }?.let { n -> if (Look.language == 1) "Файлов: $n" else "$n file" + if (n > 1) "s" else "" },
    ).joinToString("\n")
    Note(what)
    FormCard {
        chats.forEachIndexed { i, c ->
            if (i > 0) Line()
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !sending) {
                    sending = true
                    scope.launch { sendShared(context, repo, c.id, shared); onSent(c.id) }
                }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(c.title, c.colorIndex, 40.dp, saved = c.isSaved, photoPath = c.photoPath)
                Spacer(Modifier.width(12.dp))
                T(if (c.isSaved) "Saved Messages" else c.title, 17.sp, Tg.Text)
            }
        }
    }
}

/** Text as a message; images as photos, videos as videos, anything else as a file (with its own name). */
private suspend fun sendShared(context: Context, repo: TelegramRepository, chatId: Long, shared: Shared) = withContext(Dispatchers.IO) {
    if (shared.text.isNotBlank()) repo.send(chatId, Plugins.outgoing(shared.text))
    shared.uris.forEach { uri ->
        val cr = context.contentResolver
        val type = cr.getType(uri).orEmpty()
        val name = runCatching {
            cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        // TDLib needs a real path: copy into our cache under the original name.
        val file = File(File(context.cacheDir, "share_${System.nanoTime()}").apply { mkdirs() }, name.replace('/', '_'))
        val ok = runCatching { cr.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } != null }
            .onFailure { android.util.Log.w("LiquidGram", "Can't read shared $uri", it) }.getOrDefault(false)
        if (!ok) return@forEach
        when {
            type.startsWith("image/") && type != "image/gif" -> repo.sendPhoto(chatId, file.absolutePath)
            type.startsWith("video/") -> repo.sendVideo(chatId, file.absolutePath)
            else -> repo.sendDocument(chatId, file.absolutePath)
        }
    }
}
