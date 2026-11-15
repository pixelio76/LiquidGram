package org.liquidgram.ui

import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.runtime.produceState
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import org.liquidgram.data.Profile
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.drawBackdrop
import io.github.neoruaa.glasmio.glass.render.effects.blur
import io.github.neoruaa.glasmio.glass.render.effects.lens
import io.github.neoruaa.glasmio.glass.render.effects.vibrancy
import org.liquidgram.data.TelegramRepository

@Composable
fun ProfileScreen(
    repo: TelegramRepository, chatId: Long, onBack: () -> Unit, onSearch: () -> Unit, onChatGone: () -> Unit,
    onEditSelf: (() -> Unit)? = null,
    onEditChat: () -> Unit = {}, // set = the user's own profile: no action buttons, "Edit" opens the profile form
) {
    val self = onEditSelf != null
    val chat = repo.chats.value.firstOrNull { it.id == chatId }
    val me by repo.me.collectAsState()
    // Show what we know at once, then fill in phone/username/birthday/channel when they arrive.
    val loaded by produceState(Profile(chatId, chat?.title ?: "", chat?.status ?: "", "…", "…", "…", null, null, null, null, chat?.photoPath, chat?.colorIndex ?: 0), chatId) {
        if (!self) value = repo.profile(chatId)
    }
    val p = if (self) me else loaded
    val photo = rememberPhoto(p.photoPath)
    val heroBackdrop = rememberLayerBackdrop()
    val scroll = rememberScrollState()
    val soon = rememberSoon()
    val context = LocalContext.current
    val muted = repo.chats.collectAsState().value.firstOrNull { it.id == chatId }?.muted == true
    val share = { text: String -> context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)) }
    val moreItems = if (p.kind != "user") listOf(
        MenuItem(if (p.kind == "channel") "Leave Channel" else "Leave Group", Icons.Outlined.Delete, destructive = true) { repo.deleteChat(chatId); onChatGone() },
    ) else listOf(
        MenuItem("Share Contact", Icons.Outlined.Share) {
            share(listOf(p.name, p.phone, p.username).filter { it.isNotBlank() && it != "—" && it != "hidden" }.joinToString("\n"))
        },
        MenuItem("Auto-Delete", Icons.Outlined.Timer) { soon("Auto-delete") },
        MenuItem("Block User", Icons.Outlined.Block, destructive = true) { repo.block(chatId); onChatGone() },
        MenuItem("Delete Chat", Icons.Outlined.Delete, destructive = true) { repo.deleteChat(chatId); onChatGone() },
    )
    // Always five buttons, as in Telegram iOS: groups and channels swap call + video for voice chat (live stream) + share.
    val actions = (if (p.kind == "user") listOf(Icons.Rounded.Call to "call", Icons.Rounded.Videocam to "video")
    else listOf(if (p.kind == "channel") Icons.Rounded.Podcasts to "live stream" else Icons.Rounded.GraphicEq to "voice chat")) +
        listOf((if (muted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications) to (if (muted) "unmute" else "mute"), Icons.Rounded.Search to "search") +
        (if (p.kind == "user") emptyList() else listOf(Icons.Rounded.IosShare to "share")) +
        listOf(Icons.Rounded.MoreHoriz to "more")

    // The whole page, for the floating header buttons and the menu (the hero layer alone ends under the photo,
    // leaving the menu without glass over the list).
    val pageBackdrop = rememberLayerBackdrop()
    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.52f).dp
    Box(Modifier.fillMaxSize().background(Tg.Grouped)) {
        Column(Modifier.fillMaxSize().layerBackdrop(pageBackdrop).trackBackdropPosition(pageBackdrop).background(Tg.Grouped).verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(heroHeight)) {
                // "Photo": the avatar blown up to full width; parallax follows the scroll.
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer { translationY = scroll.value * 0.4f }
                        .layerBackdrop(heroBackdrop).trackBackdropPosition(heroBackdrop)
                        .background(avatarBrush(p.colorIndex)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (photo != null) FadeImage(photo, Modifier.fillMaxSize())
                    else T(initials(p.name), 140.sp, Color.White.copy(alpha = 0.9f), FontWeight.Bold)
                    // Darker bottom so the white name and buttons read on any photo.
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f))))
                }
                Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    T(p.name, 28.sp, Color.White, FontWeight.Bold)
                    T(p.status, 15.sp, Color.White.copy(alpha = 0.8f))
                    if (!self) {
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            actions.forEach { (icon, label) ->
                                HeroButton(
                                    heroBackdrop, pageBackdrop, icon, label, Modifier.weight(1f),
                                    // "More" is the same button-with-menu as "+" in Chats: the button morphs into the menu.
                                    menu = if (label == "more") ({ moreItems }) else null,
                                ) {
                                    when (label) {
                                        "mute", "unmute" -> repo.setMuted(chatId, !muted)
                                        // Telegram call through TDLib (signaling; voice needs the tgcalls engine, see ActiveCall).
                                        "call" -> repo.startCall(chatId, video = false)
                                        "video" -> repo.startCall(chatId, video = true)
                                        "voice chat", "live stream" -> soon(label)
                                        "share" -> if (p.username.isNotBlank() && p.username != "—") share("https://" + p.username) else soon("Invite links")
                                        else -> onSearch()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val channelName = p.channelName
            if (channelName != null) {
                Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 8.dp)) {
                    T("Channel", 15.sp, Tg.Secondary, modifier = Modifier.weight(1f))
                    T(p.channelSubscribers.orEmpty(), 15.sp, Tg.Secondary)
                }
                Card(top = 0.dp) {
                    Row(Modifier.fillMaxWidth().clickable {}.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(channelName, 3, 56.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Row {
                                T(channelName, 16.sp, Tg.Text, FontWeight.SemiBold, Modifier.weight(1f))
                                T(p.channelTime.orEmpty(), 14.sp, Tg.Secondary)
                            }
                            T(p.channelPost.orEmpty(), 15.sp, Tg.Secondary, maxLines = 2)
                        }
                    }
                }
            }
            // Only what this chat has: users show phone/username/bio/birthday, groups and channels description/link.
            val rows = (if (p.kind == "user") listOf(
                Triple("mobile", p.phone, Tg.Accent), Triple("username", p.username, Tg.Accent),
                Triple("bio", p.about, Tg.Text), Triple("date of birth", p.birthday, Tg.Text),
            ) else listOf(Triple("description", p.about, Tg.Text), Triple("link", p.username, Tg.Accent)))
                .filter { it.second.isNotBlank() && it.second != "—" }
            if (rows.isNotEmpty()) Card {
                rows.forEachIndexed { i, (label, value, color) ->
                    InfoRow(label, value, color)
                    if (i < rows.lastIndex) Divider()
                }
            }
            SharedMedia(repo, chatId)
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }

        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = EdgeGap, vertical = 8.dp)) {
            GlassCircle(pageBackdrop, onBack, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Rounded.ArrowBackIosNew, Tg.Text, 20.dp) }
            // Edit: own profile → profile form; own group/channel → its form; anyone else's chat has nothing to edit.
            val edit = onEditSelf ?: if (p.canEdit) onEditChat else null
            if (edit != null) GlassPill(pageBackdrop, edit, Modifier.align(Alignment.CenterEnd)) { T("Edit", 17.sp, Tg.Text, FontWeight.Medium) }
        }
    }
}

/** Round glass button over the photo with its caption underneath (Telegram iOS 26). [menu] makes it a menu button. */
@Composable
private fun HeroButton(
    backdrop: LayerBackdrop, pageBackdrop: LayerBackdrop, icon: ImageVector, label: String, modifier: Modifier,
    menu: (() -> List<MenuItem>)? = null, onClick: () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val glass = Color.Black.copy(alpha = 0.12f)
            // Wide enough for the longest Russian items ("Поделиться контактом"); the menu blurs the whole page.
            if (menu != null) MenuButton(backdrop, menu, size = maxWidth, surface = glass, menuBackdrop = pageBackdrop, menuWidth = 270.dp) {
                Icon(icon, Color.White, 26.dp)
            }
            else GlassBox(backdrop, Modifier.size(maxWidth), CircleShape, tint = glass, onClick = onClick, material = null, contentAlignment = Alignment.Center) {
                Icon(icon, Color.White, 26.dp)
            }
        }
        T(label, 12.sp, Color.White, FontWeight.Medium, Modifier.padding(top = 6.dp))
    }
}

/** Shared media of the chat under the profile: Media (photo/video grid), Files and Links tabs. */
@Composable
private fun SharedMedia(repo: TelegramRepository, chatId: Long) {
    var tab by remember { mutableStateOf(0) }
    val kind = listOf("media", "files", "links")[tab]
    // Tab and its list travel together, so a frame never draws one tab's list in another tab's layout.
    val items by produceState<Pair<Int, List<org.liquidgram.data.Message>?>>(tab to null, chatId, tab) {
        value = tab to null; value = tab to repo.sharedMedia(chatId, kind)
    }
    val context = LocalContext.current
    // Glasmio's liquid glass segmented control (as in the sticker panel). It samples a layer of its own: the page
    // layer it sits in can't be its backdrop.
    val tabsBackdrop = rememberLayerBackdrop()
    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp).fillMaxWidth()) {
        Box(Modifier.matchParentSize().layerBackdrop(tabsBackdrop).background(Tg.Grouped))
        io.github.neoruaa.glasmio.ui.GlasmioSegmentedControl(
            items = listOf(0 to Strings.tr("Media"), 1 to Strings.tr("Files"), 2 to Strings.tr("Links")),
            selected = tab,
            onSelected = { tab = it },
            modifier = Modifier.fillMaxWidth(),
            backdrop = tabsBackdrop,
        )
    }
    Spacer(Modifier.height(12.dp))
    androidx.compose.animation.AnimatedContent(
        items, contentKey = { (t, l) -> t to (l == null) }, label = "sharedMedia",
        transitionSpec = {
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.spring(stiffness = 500f)) togetherWith
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.spring(stiffness = 700f))
        },
    ) { (tab, list) ->
    val kind = listOf("media", "files", "links")[tab]
    when {
        list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Spinner(Tg.Secondary) }
        list.isEmpty() -> T(
            listOf("No media yet", "No files yet", "No links yet")[tab], 15.sp, Tg.Secondary,
            modifier = Modifier.fillMaxWidth().padding(24.dp), align = androidx.compose.ui.text.style.TextAlign.Center,
        )
        kind == "media" -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            list.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { m -> MediaCell(m, Modifier.weight(1f)) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        else -> Card(top = 0.dp) {
            list.forEachIndexed { i, m ->
                val url = if (kind == "links") LinkRegex.find(m.text)?.value else null
                Column(
                    Modifier.fillMaxWidth().clickable {
                        if (url != null) runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(if (url.startsWith("http")) url else "https://$url")))
                        }
                    }.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    if (kind == "files") {
                        T(m.fileName.ifBlank { "File" }, 17.sp, Tg.Text)
                        T(listOf(m.fileSize, m.time).filter { it.isNotBlank() }.joinToString(" · "), 15.sp, Tg.Secondary)
                    } else {
                        T(url ?: m.text, 17.sp, Tg.Accent)
                        if (url != null && m.text.trim() != url) T(m.text, 15.sp, Tg.Secondary, maxLines = 2)
                    }
                }
                if (i < list.lastIndex) Divider()
            }
        }
    }
    }
}

private val LinkRegex = Regex("""(https?://|www\.)\S+|\b[\w-]+(\.[\w-]+)+/\S*""")

@Composable
private fun MediaCell(m: org.liquidgram.data.Message, modifier: Modifier) {
    val photo = rememberPhoto(m.photoPath ?: m.videoThumb)
    Box(modifier.aspectRatio(1f).background(Tg.Placeholder)) {
        FadeImage(photo, Modifier.fillMaxSize())
        if (m.isVideo) T(
            "▶ " + "%d:%02d".format(m.videoDuration / 60, m.videoDuration % 60), 13.sp, Color.White, FontWeight.SemiBold,
            Modifier.align(Alignment.BottomStart).padding(5.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun Card(top: Dp = 16.dp, content: @Composable () -> Unit) = Column(
    Modifier.padding(start = 16.dp, end = 16.dp, top = top).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Tg.Card)
) { content() }

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color) = Column(Modifier.fillMaxWidth().clickable {}.padding(horizontal = 16.dp, vertical = 10.dp)) {
    T(label, 15.sp, Tg.Text)
    T(value, 17.sp, valueColor)
}

@Composable
private fun Divider() = Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(0.5.dp).background(Tg.Separator))
