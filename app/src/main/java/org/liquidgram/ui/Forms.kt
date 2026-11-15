package org.liquidgram.ui

import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.liquidgram.data.Contact
import org.liquidgram.data.MyInfo
import org.liquidgram.data.Session
import org.liquidgram.data.TelegramRepository
import java.io.File

/** Grouped iOS form page: scrolling content under a floating glass header (back · title · action). */
@Composable
internal fun FormPage(
    title: String, onBack: () -> Unit,
    action: String? = null, actionEnabled: Boolean = true, onAction: () -> Unit = {},
    content: @Composable ColumnScope.(controls: LayerBackdrop) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    // Glass controls inside the page sample this card-coloured layer (they can't sample the page they're on).
    val controls = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(Tg.Grouped)) {
        Box(Modifier.fillMaxSize().layerBackdrop(controls).trackBackdropPosition(controls).background(Tg.Card))
        Column(
            Modifier.fillMaxSize().layerBackdrop(backdrop).trackBackdropPosition(backdrop).background(Tg.Grouped).verticalScroll(rememberScrollState())
                .statusBarsPadding().padding(top = 64.dp).navigationBarsPadding().padding(bottom = 24.dp),
        ) { content(controls) }
        // back · title · action in a row: the title takes what is left and ends with "…", never under the buttons.
        Row(
            Modifier.fillMaxWidth().scrollEdge(backdrop).statusBarsPadding().height(60.dp).padding(horizontal = EdgeGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassCircle(backdrop, onBack) { Icon(Icons.Rounded.ArrowBackIosNew, Tg.Text, 20.dp) }
            T(title, 17.sp, Tg.Text, FontWeight.SemiBold, Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.Center)
            if (action != null) GlassPill(backdrop, { if (actionEnabled) onAction() }) {
                T(action, 17.sp, if (actionEnabled) Tg.Accent else Tg.Muted, FontWeight.SemiBold)
            } else Spacer(Modifier.width(44.dp))
        }
    }
}

@Composable
internal fun FormCard(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Tg.Card), content = content)

@Composable
internal fun Note(text: String) = T(text, 13.sp, Tg.Secondary, modifier = Modifier.padding(horizontal = 32.dp), maxLines = 4)

@Composable
internal fun Line() = Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(Tg.Separator))

@Composable
private fun Field(hint: String, value: String, onChange: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text, lines: Int = 1) {
    Box(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 16.dp, vertical = 14.dp)) {
        if (value.isEmpty()) T(hint, 17.sp, Tg.Muted)
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth(), singleLine = lines == 1, maxLines = lines,
            textStyle = TextStyle(color = Tg.Text, fontSize = 17.sp * Look.textScale), cursorBrush = SolidColor(Tg.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        )
    }
}

@Composable
private fun toast(): (String) -> Unit {
    val context = LocalContext.current
    return { Toast.makeText(context, Strings.tr(it), Toast.LENGTH_LONG).show() }
}

// ------------------------------------------------------------------ profile

@Composable
fun EditProfileScreen(repo: TelegramRepository, onBack: () -> Unit) {
    val me by repo.me.collectAsState()
    var info by remember { mutableStateOf<MyInfo?>(null) }
    LaunchedEffect(Unit) { info = repo.myInfo() }
    val scope = rememberCoroutineScope()
    val show = toast()
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val file = withContext(Dispatchers.IO) {
                File(context.cacheDir, "avatar_${System.currentTimeMillis()}.jpg").also { out ->
                    context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                }
            }
            repo.setProfilePhoto(file.absolutePath)
        }
    }
    val i = info
    FormPage("Edit Profile", onBack, "Done", actionEnabled = i != null && i.firstName.isNotBlank(), onAction = {
        scope.launch { repo.saveMyInfo(i!!.copy(username = i.username.removePrefix("@")))?.let(show) ?: onBack() }
    }) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(me.name, me.colorIndex, 100.dp, photoPath = me.photoPath)
            T("Set New Photo", 17.sp, Tg.Accent, modifier = Modifier.padding(top = 10.dp).clickable {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
        }
        if (i == null) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Spinner(Tg.Secondary) }; return@FormPage }
        FormCard {
            Field("First name", i.firstName, { info = i.copy(firstName = it) })
            Line()
            Field("Last name", i.lastName, { info = i.copy(lastName = it) })
        }
        FormCard { Field("Bio", i.bio, { if (it.length <= 70) info = i.copy(bio = it) }, lines = 3) }
        Note("Any details such as age, occupation or city.")
        Spacer(Modifier.height(12.dp))
        FormCard { Field("Username", i.username, { info = i.copy(username = it.filter { c -> c.isLetterOrDigit() || c == '_' }) }) }
        Note("Others can find you by this username.")
    }
}

/** Our own group or channel: photo, name and description (Telegram's "Edit" on a chat you manage). */
@Composable
fun EditChatScreen(repo: TelegramRepository, chatId: Long, onBack: () -> Unit) {
    val chat = repo.chats.collectAsState().value.firstOrNull { it.id == chatId }
    var profile by remember { mutableStateOf<org.liquidgram.data.Profile?>(null) }
    var title by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    LaunchedEffect(chatId) { repo.profile(chatId).also { profile = it; title = it.name; about = it.about } }
    val scope = rememberCoroutineScope()
    val show = toast()
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val file = withContext(Dispatchers.IO) {
                File(context.cacheDir, "chat_${System.currentTimeMillis()}.jpg").also { out ->
                    context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                }
            }
            repo.setChatPhoto(chatId, file.absolutePath)
        }
    }
    val p = profile
    val channel = p?.kind == "channel"
    FormPage(if (channel) "Edit Channel" else "Edit Group", onBack, "Done", actionEnabled = p != null && title.isNotBlank(), onAction = {
        scope.launch { repo.editChat(chatId, title, about)?.let(show) ?: onBack() }
    }) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(title.ifBlank { chat?.title ?: "" }, chat?.colorIndex ?: 0, 100.dp, photoPath = chat?.photoPath)
            T("Set New Photo", 17.sp, Tg.Accent, modifier = Modifier.padding(top = 10.dp).clickable {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
        }
        if (p == null) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Spinner(Tg.Secondary) }; return@FormPage }
        FormCard { Field(if (channel) "Channel name" else "Group name", title, { if (it.length <= 128) title = it }) }
        FormCard { Field("Description", about, { if (it.length <= 255) about = it }, lines = 4) }
        Note(if (channel) "You can provide an optional description for your channel." else "You can provide an optional description for your group.")
    }
}

// ------------------------------------------------------------------ sessions

@Composable
fun SessionsScreen(repo: TelegramRepository, onBack: () -> Unit) {
    var sessions by remember { mutableStateOf<List<Session>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { sessions = repo.sessions() }
    fun reload() = scope.launch { sessions = repo.sessions() }
    FormPage("Devices", onBack) {
        val list = sessions
        if (list == null) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Spinner(Tg.Secondary) }; return@FormPage }
        val (current, others) = list.partition { it.current }
        SectionTitle("This Device")
        FormCard { current.forEach { SessionRow(it, null) } }
        if (others.isNotEmpty()) FormCard {
            Row(Modifier.fillMaxWidth().clickable { scope.launch { repo.terminateOtherSessions(); reload() } }.padding(16.dp)) {
                T("Terminate All Other Sessions", 17.sp, Tg.Red)
            }
        }
        SectionTitle("Active Sessions")
        FormCard {
            if (others.isEmpty()) T("No other sessions", 15.sp, Tg.Secondary, modifier = Modifier.padding(16.dp))
            others.forEachIndexed { n, s ->
                SessionRow(s) { scope.launch { repo.terminateSession(s.id); reload() } }
                if (n < others.lastIndex) Line()
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) =
    T(Strings.tr(text).uppercase(), 13.sp, Tg.Secondary, modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 2.dp))

@Composable
private fun SessionRow(s: Session, onTerminate: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        val desktop = listOf("mac", "windows", "linux", "desktop", "pc").any { s.device.lowercase().contains(it) }
        Box(Modifier.size(36.dp).background(if (desktop) Color(0xFF5E5CE6) else Color(0xFF30D158), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(if (desktop) Icons.Rounded.Computer else Icons.Rounded.PhoneAndroid, Color.White, 22.dp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            T(s.device, 16.sp, Tg.Text, FontWeight.SemiBold)
            T(s.app, 14.sp, Tg.Text)
            T(listOf(s.place, s.lastActive).filter(String::isNotEmpty).joinToString(" • "), 13.sp, Tg.Secondary)
        }
        if (onTerminate != null) T("Terminate", 15.sp, Tg.Red, modifier = Modifier.clickable(onClick = onTerminate).padding(8.dp))
    }
}

// ------------------------------------------------------------------ language

@Composable
fun LanguageScreen(onBack: () -> Unit) = FormPage("Language", onBack) {
    FormCard {
        Strings.languages.forEachIndexed { i, name ->
            Row(Modifier.fillMaxWidth().clickable { Look.language = i }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                T(name, 17.sp, Tg.Text, modifier = Modifier.weight(1f))
                if (Look.language == i) Icon(Icons.Rounded.Check, Tg.Accent, 22.dp)
            }
            if (i < Strings.languages.lastIndex) Line()
        }
    }
}

// ------------------------------------------------------------------ new chats

/** Contacts with a tap action; [selected] shows check marks (multi-select). */
@Composable
private fun ContactPicker(contacts: List<Contact>?, selected: Set<Long>?, onTap: (Contact) -> Unit) {
    if (contacts == null) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Spinner(Tg.Secondary) }; return }
    FormCard {
        if (contacts.isEmpty()) T("No contacts yet", 15.sp, Tg.Secondary, modifier = Modifier.padding(16.dp))
        contacts.forEachIndexed { n, c ->
            Row(Modifier.fillMaxWidth().clickable { onTap(c) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selected != null) {
                    val on = c.userId in selected
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(if (on) Tg.Accent else Color.Transparent)
                            .then(if (on) Modifier else Modifier.background(Tg.Separator, CircleShape).padding(1.5.dp).background(Tg.Card, CircleShape)),
                        contentAlignment = Alignment.Center,
                    ) { if (on) Icon(Icons.Rounded.Check, Color.White, 16.dp) }
                    Spacer(Modifier.width(12.dp))
                }
                Avatar(c.name, c.colorIndex, 40.dp, photoPath = c.photoPath)
                Column(Modifier.padding(start = 12.dp)) {
                    T(c.name, 17.sp, Tg.Text, FontWeight.Medium)
                    T(c.status, 14.sp, if (c.status == "online") Tg.Accent else Tg.Secondary)
                }
            }
            if (n < contacts.lastIndex) Line()
        }
    }
}

@Composable
private fun rememberContacts(repo: TelegramRepository): List<Contact>? {
    var contacts by remember { mutableStateOf<List<Contact>?>(null) }
    LaunchedEffect(Unit) { contacts = repo.contacts() }
    return contacts
}

/** Runs a create call; on success opens the new chat, otherwise shows the error. */
@Composable
private fun creator(openChat: (Long) -> Unit): (suspend () -> Result<Long>) -> Unit {
    val scope = rememberCoroutineScope()
    val show = toast()
    var busy by remember { mutableStateOf(false) }
    return { block ->
        if (!busy) {
            busy = true
            scope.launch { block().onSuccess(openChat).onFailure { show(it.message ?: "Error") }; busy = false }
        }
    }
}

@Composable
fun NewGroupScreen(repo: TelegramRepository, onBack: () -> Unit, openChat: (Long) -> Unit) {
    val contacts = rememberContacts(repo)
    val selected = remember { mutableStateListOf<Long>() }
    var naming by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    val create = creator(openChat)
    if (!naming) FormPage("New Group", onBack, "Next", actionEnabled = selected.isNotEmpty(), onAction = { naming = true }) {
        SectionTitle("Choose members")
        ContactPicker(contacts, selected.toSet()) { if (it.userId in selected) selected.remove(it.userId) else selected.add(it.userId) }
    } else FormPage("New Group", { naming = false }, "Create", actionEnabled = title.isNotBlank(), onAction = {
        create { repo.createGroup(title.trim(), selected.toList()) }
    }) {
        FormCard { Field("Group Name", title, { title = it }) }
        SectionTitle("${selected.size} members")
        ContactPicker(contacts?.filter { it.userId in selected }, null) {}
    }
}

@Composable
fun NewChannelScreen(repo: TelegramRepository, onBack: () -> Unit, openChat: (Long) -> Unit) {
    var title by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    val create = creator(openChat)
    FormPage("New Channel", onBack, "Create", actionEnabled = title.isNotBlank(), onAction = { create { repo.createChannel(title.trim(), about.trim()) } }) {
        FormCard {
            Field("Channel Name", title, { title = it })
            Line()
            Field("Description", about, { about = it }, lines = 4)
        }
        Note("You can provide an optional description for your channel.")
    }
}

@Composable
fun NewContactScreen(repo: TelegramRepository, onBack: () -> Unit, openChat: (Long) -> Unit) {
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+") }
    val create = creator(openChat)
    FormPage("New Contact", onBack, "Create", actionEnabled = first.isNotBlank() && phone.count { it.isDigit() } >= 7, onAction = {
        create { repo.addContact(first.trim(), last.trim(), phone) }
    }) {
        FormCard {
            Field("First name", first, { first = it })
            Line()
            Field("Last name", last, { last = it })
        }
        FormCard { Field("Phone number", phone, { phone = "+" + it.filter(Char::isDigit) }, KeyboardType.Phone) }
    }
}

@Composable
fun NewSecretChatScreen(repo: TelegramRepository, onBack: () -> Unit, openChat: (Long) -> Unit) {
    val contacts = rememberContacts(repo)
    val create = creator(openChat)
    FormPage("New Secret Chat", onBack) {
        Row(Modifier.padding(horizontal = 32.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Lock, Tg.Secondary, 16.dp)
            T("Secret chats are end-to-end encrypted and live only on this device.", 13.sp, Tg.Secondary, maxLines = 4)
        }
        SectionTitle("Choose a contact")
        ContactPicker(contacts, null) { c -> create { repo.createSecretChat(c.userId) } }
    }
}
