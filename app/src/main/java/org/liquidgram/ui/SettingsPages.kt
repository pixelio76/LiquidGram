package org.liquidgram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import kotlinx.coroutines.launch
import org.liquidgram.data.NotifyScope
import org.liquidgram.data.TelegramRepository
import org.liquidgram.glass.LiquidToggle

// Settings → Notifications, Privacy, Data and Storage, Power Saving. Built on the form kit in Forms.kt.

@Composable
private fun SwitchRow(label: String, value: Boolean, controls: LayerBackdrop, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 17.sp, Tg.Text, modifier = Modifier.weight(1f), maxLines = 2)
        Spacer(Modifier.width(12.dp))
        LiquidToggle({ value }, onChange, controls)
    }
}

/** Label with an inline segmented choice (Everybody / My Contacts / Nobody). */
@Composable
private fun ChoiceRow(label: String, options: List<Pair<String, String>>, value: String?, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        T(label, 17.sp, Tg.Text)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Tg.Text.copy(alpha = 0.06f)).padding(2.dp)) {
            options.forEach { (key, title) ->
                val on = key == value
                Box(
                    Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(8.dp)).background(if (on) Tg.Accent.copy(alpha = 0.22f) else Color.Transparent).clickable { onPick(key) },
                    contentAlignment = Alignment.Center,
                ) { T(title, 13.sp, if (on) Tg.Accent else Tg.Secondary, if (on) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, color: Color = Tg.Secondary, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(label, 17.sp, if (onClick != null && value.isEmpty()) color else Tg.Text, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) T(value, 17.sp, color)
    }
}

@Composable
private fun Loading() = Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Spinner(Tg.Secondary) }

@Composable
fun NotificationsScreen(repo: TelegramRepository, onBack: () -> Unit) {
    var scopes by remember { mutableStateOf<List<NotifyScope>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { scopes = repo.notifyScopes() }
    fun update(s: NotifyScope) {
        scopes = scopes?.map { if (it.key == s.key) s else it }
        scope.launch { repo.setNotifyScope(s) }
    }
    FormPage("Notifications and Sounds", onBack) { controls ->
        val list = scopes ?: return@FormPage Loading()
        val titles = mapOf("private" to "Private Chats", "group" to "Group Chats", "channel" to "Channels")
        list.forEach { s ->
            SectionTitle(titles.getValue(s.key))
            FormCard {
                SwitchRow("Show Notifications", s.enabled, controls) { update(s.copy(enabled = it)) }
                Line()
                SwitchRow("Message Preview", s.preview, controls) { update(s.copy(preview = it)) }
            }
        }
        Note("Notifications arrive while LiquidGram is running; push delivery needs Firebase and isn't set up yet.")
    }
}

@Composable
fun PrivacyScreen(repo: TelegramRepository, onBack: () -> Unit) {
    val keys = listOf(
        "phone" to "Phone Number", "lastSeen" to "Last Seen & Online", "photo" to "Profile Photos",
        "forwards" to "Forwarded Messages", "calls" to "Calls", "invites" to "Groups & Channels",
    )
    var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var blocked by remember { mutableStateOf<Int?>(null) }
    var password by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        values = keys.associate { (k, _) -> k to repo.privacy(k) }
        blocked = repo.blockedCount()
        password = repo.hasPassword()
    }
    val options = listOf("everybody" to "Everybody", "contacts" to "My Contacts", "nobody" to "Nobody")
    FormPage("Privacy and Security", onBack) {
        FormCard {
            InfoRow("Blocked Users", blocked?.toString() ?: "…")
            Line()
            InfoRow("Two-Step Verification", when (password) { null -> "…"; true -> "On"; false -> "Off" })
        }
        SectionTitle("Privacy")
        FormCard {
            keys.forEachIndexed { n, (k, title) ->
                ChoiceRow(title, options, values[k]) { v ->
                    values = values + (k to v)
                    scope.launch { repo.setPrivacy(k, v) }
                }
                if (n < keys.lastIndex) Line()
            }
        }
    }
}

@Composable
fun DataScreen(repo: TelegramRepository, onBack: () -> Unit) {
    var usage by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { usage = repo.storageUsage() }
    FormPage("Data and Storage", onBack) { controls ->
        SectionTitle("Storage Usage")
        FormCard {
            InfoRow("Cache", usage?.let { "%.1f MB".format(it / 1_048_576f) } ?: "…")
            Line()
            InfoRow("Clear Cache", "", Tg.Red) { scope.launch { repo.clearCache(); usage = repo.storageUsage() } }
        }
        SectionTitle("Automatic Media Download")
        FormCard {
            SwitchRow("Photos", Look.autoPhotos, controls) { Look.autoPhotos = it }
            Line()
            SwitchRow("Files", Look.autoFiles, controls) { Look.autoFiles = it }
        }
        Note("Media that isn't downloaded automatically loads when you tap it.")
        SectionTitle("Video Messages")
        FormCard {
            ChoiceRow("Start with", listOf("front" to "Front camera", "back" to "Back camera"), if (Look.roundBackCamera) "back" else "front") {
                Look.roundBackCamera = it == "back"
            }
        }
    }
}

@Composable
fun PowerScreen(onBack: () -> Unit) = FormPage("Power Saving", onBack) { controls ->
    FormCard {
        SwitchRow("Loop Animated Stickers", !Look.powerStickers, controls) { Look.powerStickers = !it }
        Line()
        SwitchRow("Glass Refraction", !Look.powerGlass, controls) { Look.powerGlass = !it }
    }
    Note("Turning these off saves battery: stickers play once, glass keeps its blur but stops bending light.")
}

/** Settings → Plugins: switch, install and remove local plugins (see [Plugins]). */
@Composable
fun PluginsScreen(onBack: () -> Unit) = FormPage("Plugins", onBack) { controls ->
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val say = { text: String -> android.widget.Toast.makeText(context, Strings.tr(text), android.widget.Toast.LENGTH_SHORT).show() }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Plugins are small JSON files; anything bigger isn't one.
        val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().takeIf { b -> b.size < 256 * 1024 }?.decodeToString() } }.getOrNull()
        say(if (text == null) "Can't read this file" else Plugins.install(context, text) ?: "Plugin installed")
    }
    var openOptions by remember { mutableStateOf(emptySet<String>()) }
    Note("Plugins change LiquidGram on this device only: look, interface texts, how messages are sent and shown. They are JSON files and run no code.")
    SectionTitle("Installed")
    FormCard {
        Plugins.all.forEachIndexed { i, p ->
            if (i > 0) Line()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T(p.name, 17.sp, Tg.Text, FontWeight.Medium)
                    T(listOf("v" + p.version, p.author).filter { it.isNotBlank() }.joinToString(" · "), 13.sp, Tg.Secondary)
                    if (p.description.isNotBlank()) T(p.description, 14.sp, Tg.Secondary, maxLines = 3)
                    T(p.hooks.joinToString(" · ") { Strings.tr(it) }, 13.sp, Tg.Accent)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp)) {
                        if (p.options.isNotEmpty()) T(if (p.id in openOptions) "Hide Settings" else "Settings", 15.sp, Tg.Accent,
                            modifier = Modifier.clickable { openOptions = if (p.id in openOptions) openOptions - p.id else openOptions + p.id })
                        if (!p.builtIn) T("Delete", 15.sp, Tg.Red, modifier = Modifier.clickable { Plugins.delete(context, p) })
                    }
                }
                Spacer(Modifier.width(12.dp))
                LiquidToggle({ p.id in Plugins.enabled }, { Plugins.setEnabled(context, p, it) }, controls)
            }
            if (p.id in openOptions) p.options.forEach { o ->
                Line()
                PluginOptionRow(o, p.values.getValue(o.key), controls) { Plugins.setOption(context, p, o.key, it) }
            }
        }
    }
    FormCard {
        Row(Modifier.fillMaxWidth().clickable { clipboard.setText(androidx.compose.ui.text.AnnotatedString(Plugins.aiGuide())); say("Instructions copied") }.padding(16.dp)) {
            T("Copy Instructions for AI", 17.sp, Tg.Accent)
        }
    }
    Note("Paste the instructions into a chat with any AI and describe the plugin you want: it replies with a ready .json file. Save it and install it here.")
    FormCard {
        Row(Modifier.fillMaxWidth().clickable { pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }.padding(16.dp)) {
            T("Install from File", 17.sp, Tg.Accent)
        }
        Line()
        Row(Modifier.fillMaxWidth().clickable { clipboard.setText(androidx.compose.ui.text.AnnotatedString(Plugins.TEMPLATE)); say("Template copied") }.padding(16.dp)) {
            T("Copy Plugin Template", 17.sp, Tg.Accent)
        }
    }
    Note("A plugin is a .json file with an id, a name and any of: settings, strings, outgoing, incoming, hideMessages, hideChats. Start from the template.")
}

/** One of a plugin's own settings, drawn by its type. */
@Composable
private fun PluginOptionRow(o: PluginOption, value: Any, controls: LayerBackdrop, onChange: (Any) -> Unit) = when (o.type) {
    "toggle" -> SwitchRow(o.title, value == true, controls) { onChange(it) }
    "choice" -> ChoiceRow(o.title, o.choices.map { it to it }, value.toString()) { onChange(it) }
    "number" -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        val v = (value as? Number)?.toFloat() ?: o.min
        Row {
            T(o.title, 17.sp, Tg.Text, modifier = Modifier.weight(1f))
            T(if (o.max - o.min > 10f) v.toInt().toString() else "%.2f".format(v), 15.sp, Tg.Secondary)
        }
        Spacer(Modifier.height(8.dp))
        org.liquidgram.glass.LiquidSlider({ v }, { onChange(it.toDouble()) }, o.min..o.max, (o.max - o.min) / 200f, controls, Modifier.fillMaxWidth())
    }
    else -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        T(o.title, 17.sp, Tg.Text, maxLines = 2)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tg.Text.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (value.toString().isEmpty()) T(if (o.type == "words") "Words, comma-separated" else "", 16.sp, Tg.Secondary)
            androidx.compose.foundation.text.BasicTextField(
                value.toString(), { onChange(it) }, Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(color = Tg.Text, fontSize = 16.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Tg.Accent),
            )
        }
    }
}

/** Hidden developer menu (ten taps on the version in Settings). */
@Composable
fun DeveloperScreen(onBack: () -> Unit) = FormPage("Developer", onBack) { controls ->
    SectionTitle("Test account")
    FormCard {
        SwitchRow("Demo account", org.liquidgram.Repo.demo, controls) { org.liquidgram.Repo.demo = it }
    }
    Note("The offline test account used while building LiquidGram: sample chats, no network. It lasts until the app is restarted; your own account isn't touched.")
    SectionTitle("Build")
    FormCard {
        InfoRow("Version", "${org.liquidgram.BuildConfig.VERSION_NAME} (${org.liquidgram.BuildConfig.VERSION_CODE})")
        Line()
        InfoRow("Telegram API", if (org.liquidgram.BuildConfig.TG_API_ID != 0) "configured" else "demo only")
        Line()
        InfoRow("Android", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
    }
}
