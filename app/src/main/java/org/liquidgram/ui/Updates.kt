package org.liquidgram.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.liquidgram.BuildConfig
import org.liquidgram.glass.LiquidButton
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Over-the-air updates from the GitHub releases of [REPO]: the latest release's tag (vX.Y.Z) is compared with the
 * installed version; a newer one offers its .apk, which is downloaded into the cache and handed to the system
 * installer. The release must be signed with the same key as the installed app, or Android refuses it.
 */
object Updates {
    const val REPO = "pixelio76/LiquidGram"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

    class Release(val version: String, val notes: String, val apkUrl: String, val apkSize: Long)

    /** A newer release to offer (null = none or dismissed). */
    var offer by mutableStateOf<Release?>(null)
    /** Download progress 0..1 while the .apk is coming in. */
    var progress by mutableStateOf<Float?>(null)
    var error by mutableStateOf<String?>(null)

    /** Splits "v1.2.3" into numbers; missing parts count as 0. */
    fun parts(version: String) = version.trim().removePrefix("v").removePrefix("V")
        .split('.', '-').mapNotNull { it.toIntOrNull() }

    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Whether the last [latest] call failed to reach GitHub (as opposed to finding no release). */
    private var unreachable = false

    /** Latest release with an .apk, or null (none yet, no network, GitHub rate limit; see [unreachable]). */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        unreachable = false
        runCatching {
            val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            if (c.responseCode == 404) return@runCatching null // no releases yet
            if (c.responseCode != 200) { unreachable = true; return@runCatching null }
            val json = org.json.JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk", ignoreCase = true) } ?: return@runCatching null
            Release(json.getString("tag_name"), json.optString("body"), apk.getString("browser_download_url"), apk.optLong("size"))
        }.onFailure { unreachable = true }.getOrNull()
    }

    /**
     * Looks for an update. [manual] = from Settings: ignores the 6-hour pause and a version the user put off,
     * and says so when there is nothing new.
     */
    suspend fun check(context: Context, manual: Boolean = false) {
        val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!manual && now - prefs.getLong("lastCheck", 0) < CHECK_EVERY_MS) return
        val r = latest()
        prefs.edit().putLong("lastCheck", now).apply()
        val newer = r != null && isNewer(r.version, BuildConfig.VERSION_NAME)
        if (newer && (manual || prefs.getString("skipped", null) != r!!.version)) offer = r
        else if (manual) android.widget.Toast.makeText(
            context,
            Strings.tr(if (unreachable) "Couldn't check for updates" else "You have the latest version"),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    fun later(context: Context) {
        offer?.let { context.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().putString("skipped", it.version).apply() }
        offer = null; error = null
    }

    /** Downloads the offered .apk and opens the system installer (asking for the install permission first if needed). */
    suspend fun install(context: Context) {
        val r = offer ?: return
        if (!context.packageManager.canRequestPackageInstalls()) {
            // Android asks once per app; the user comes back and taps Update again.
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        error = null; progress = 0f
        val file = File(context.cacheDir, "updates/LiquidGram-${r.version}.apk")
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs()
                val c = URL(r.apkUrl).openConnection() as HttpURLConnection // GitHub redirects to its CDN; followed
                c.connectTimeout = 15_000; c.readTimeout = 30_000
                val total = c.contentLengthLong.takeIf { it > 0 } ?: r.apkSize
                c.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); done += n
                            if (total > 0) progress = (done.toFloat() / total).coerceIn(0f, 1f)
                        }
                    }
                }
                total <= 0 || file.length() == total
            }.getOrDefault(false)
        }
        progress = null
        if (!ok) { error = Strings.tr("Download failed"); file.delete(); return }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** The update card: slides up from the bottom over everything when a newer release is offered. */
@Composable
fun UpdateOverlay() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shown by androidx.compose.runtime.remember { mutableStateOf<Updates.Release?>(null) }
    Updates.offer?.let { shown = it }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            Updates.offer != null,
            Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it } + fadeIn(),
            exit = slideOutVertically(spring(dampingRatio = 1f, stiffness = 500f)) { it } + fadeOut(),
        ) {
            val r = shown ?: return@AnimatedVisibility
            val backdrop = io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop()
            UpdateCard(r, backdrop,
                onUpdate = { scope.launch { Updates.install(context) } },
                onLater = { Updates.later(context) },
            )
        }
    }
}

@Composable
private fun UpdateCard(r: Updates.Release, backdrop: LayerBackdrop, onUpdate: () -> Unit, onLater: () -> Unit) {
    val progress = Updates.progress
    Box(Modifier.fillMaxWidth().bottomGap(EdgeGap).padding(horizontal = 12.dp)) {
        // The card's own surface is what the glass buttons on it sample.
        Box(
            Modifier.matchParentSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(30.dp))
                .layerBackdrop(backdrop).background(Tg.Card),
        )
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            T("Update available", 20.sp, Tg.Text, FontWeight.SemiBold)
            T("LiquidGram " + r.version.removePrefix("v") + "  ·  " + "%.1f MB".format(r.apkSize / 1_048_576f), 15.sp, Tg.Secondary)
            if (r.notes.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                    T(r.notes.trim(), 15.sp, Tg.Text, maxLines = Int.MAX_VALUE)
                }
            }
            Updates.error?.let { Spacer(Modifier.height(8.dp)); T(it, 14.sp, Tg.Red) }
            Spacer(Modifier.height(16.dp))
            if (progress != null) {
                // Download progress: an accent capsule filling a muted track.
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Tg.Separator)) {
                    Box(Modifier.fillMaxWidth(progress).height(8.dp).clip(CircleShape).background(Tg.Accent))
                }
                Spacer(Modifier.height(8.dp))
                T(Strings.tr("Downloading…") + " ${(progress * 100).toInt()}%", 14.sp, Tg.Secondary)
            } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiquidButton(onLater, backdrop, Modifier.weight(1f), height = 50.dp) { T("Later", 17.sp, Tg.Text, FontWeight.Medium) }
                LiquidButton(onUpdate, backdrop, Modifier.weight(1f), tint = Tg.Accent, height = 50.dp) { T("Update", 17.sp, Color.White, FontWeight.SemiBold) }
            }
        }
    }
}
