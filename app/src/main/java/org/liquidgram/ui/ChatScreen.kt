package org.liquidgram.ui

import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ime
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.drawBackdrop
import androidx.compose.ui.composed
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.automirrored.rounded.Forward
import io.github.neoruaa.glasmio.glass.render.effects.blur
import io.github.neoruaa.glasmio.glass.render.effects.lens
import io.github.neoruaa.glasmio.glass.render.effects.vibrancy
import io.github.neoruaa.glasmio.glass.render.highlight.Highlight
import com.kyant.shapes.Capsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.liquidgram.data.Message
import org.liquidgram.data.TelegramRepository
import java.io.File
import kotlin.random.Random

@Composable
fun ChatScreen(repo: TelegramRepository, chatId: Long, onBack: () -> Unit, startSearch: Boolean = false, openProfile: () -> Unit) {
    // A chat outside the main list (archived, opened from calls/stories) still opens, with a generic header.
    val chat = repo.chats.collectAsState().value.firstOrNull { it.id == chatId }
        ?: repo.chatInfo(chatId) // found by global search, not in the list
        ?: org.liquidgram.data.Chat(chatId, "Chat", "", "", 0, status = "")
    val rawMessages by repo.messages(chatId).collectAsState()
    val messages = remember(rawMessages, Plugins.enabled, Plugins.all) { Plugins.messages(rawMessages) }
    val typing by repo.typing(chatId).collectAsState()
    // [backdrop]: wallpaper + messages, for the floating header, input and menus.
    // [wallBackdrop]: wallpaper only, for the glass bubbles (they can't refract themselves).
    val backdrop = rememberLayerBackdrop()
    val wallBackdrop = rememberLayerBackdrop()
    val menu = rememberGlassMenu()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val touches = remember(scope) { WallpaperTouches(scope) }

    var draft by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    var editing by remember { mutableStateOf<Message?>(null) }
    // Media viewer: id of the photo/video open full screen.
    var viewer by remember { mutableStateOf<Long?>(null) }
    var forwarding by remember { mutableStateOf<Message?>(null) }
    var panel by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(startSearch) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Message>?>(null) }
    var jumpTo by remember { mutableStateOf<Long?>(null) }
    val round = remember { RoundRecorder(context) }
    LaunchedEffect(query, searching) {
        if (!searching || query.isBlank()) { results = null; return@LaunchedEffect }
        delay(300)
        results = repo.searchMessages(chatId, query.trim())
    }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var lastTyping by remember { mutableLongStateOf(0L) }
    // The divider marks what was unread when the chat was opened, and stays put while reading.
    val unreadFrom = remember(chatId) { chat.lastReadInboxId }

    // Bot reply keyboard: the newest loaded message that sets or removes one decides.
    // shortcut: only loaded messages count; use TDLib's chat.replyMarkupMessageId if old keyboards go missing.
    val keyboardMsg = messages.firstOrNull { it.keyboard != null }
    var usedOneTime by remember { mutableStateOf<Long?>(null) }
    val botRows = keyboardMsg?.keyboard?.takeIf { it.isNotEmpty() && keyboardMsg.id != usedOneTime }
    var botOpen by rememberSaveable { mutableStateOf(true) }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val botShown = botRows != null && botOpen && !panel && !imeOpen
    val soon = rememberSoon()
    fun onBotButton(m: Message, b: org.liquidgram.data.BotButton) {
        when (b.kind) {
            "text" -> { repo.send(chatId, b.text); if (m.keyboardOneTime) usedOneTime = m.id }
            "url" -> runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(b.url))) }
            "callback" -> scope.launch { repo.pressBotButton(chatId, m.id, b)?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
            else -> soon("This button")
        }
    }

    LaunchedEffect(chatId, messages.firstOrNull()?.id) { repo.markRead(chatId) }
    // New message at the bottom: always follow your own; follow incoming ones only if you were at the bottom
    // (the new item pushed the old last one to index 1). Scrolled up to read history = stay put.
    LaunchedEffect(messages.firstOrNull()?.id) {
        val newest = messages.firstOrNull() ?: return@LaunchedEffect
        if (newest.out || listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }
    DisposableEffect(chatId) { onDispose { repo.closeChat(chatId); VoicePlayer.stop() } }
    LaunchedEffect(listState, messages.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { if (it >= messages.size - 10) repo.loadMore(chatId) }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            // TDLib needs a real file path, so copy the picked image into our cache first.
            val file = withContext(Dispatchers.IO) {
                File(context.cacheDir, "send_${System.currentTimeMillis()}.jpg").also { out ->
                    context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                }
            }
            repo.sendPhoto(chatId, file.absolutePath)
        }
    }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            // Copied into our cache like photos (TDLib needs a file path); reading its size and length happens off the UI thread too.
            withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "send_${System.currentTimeMillis()}.mp4").also { out ->
                    context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                }
                repo.sendVideo(chatId, file.absolutePath)
            }
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            // Keep the real file name: TDLib sends it as the document's name.
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "file"
            val file = withContext(Dispatchers.IO) {
                File(File(context.cacheDir, "docs_${System.currentTimeMillis()}").apply { mkdirs() }, name).also { out ->
                    context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                }
            }
            repo.sendDocument(chatId, file.absolutePath)
        }
    }

    fun submit() {
        val text = draft.trim()
        if (text.isEmpty()) return
        val e = editing
        val out = Plugins.outgoing(text)
        if (e != null) repo.editMessage(chatId, e.id, out) else repo.send(chatId, out, replyTo?.id)
        draft = ""; replyTo = null; editing = null
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BackHandler(viewer != null) { viewer = null }
    BackHandler(viewer == null && panel) { panel = false }
    BackHandler(viewer == null && !panel && searching) { searching = false; query = "" }
    // Search jump: once the target is in the loaded history, scroll to it (the unread divider shifts indexes by one).
    LaunchedEffect(jumpTo, messages) {
        val target = jumpTo ?: return@LaunchedEffect
        val i = messages.indexOfFirst { it.id == target }
        if (i < 0) { repo.loadAround(chatId, target); return@LaunchedEffect }
        val divider = messages.indexOfFirst { !it.out && it.id > unreadFrom && unreadFrom > 0 && (messages.getOrNull(messages.indexOf(it) + 1)?.id ?: 0) <= unreadFrom }
        listState.animateScrollToItem(i + if (divider in 0 until i) 1 else 0)
        jumpTo = null
    }
    BackHandler(viewer == null && (replyTo != null || editing != null)) { replyTo = null; editing = null; draft = "" }

    Box(
        Modifier.fillMaxSize().background(Tg.Bg).imePadding()
            // A touch parts the doodles and drags the gap along. Initial pass, nothing consumed: scrolling and taps work as usual.
            .pointerInput(touches) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val push = touches.press(down.position)
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        touches.move(push, change.position)
                    }
                    touches.release(push)
                }
            }
    ) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).trackBackdropPosition(backdrop)) {
            Box(Modifier.fillMaxSize().layerBackdrop(wallBackdrop).trackBackdropPosition(wallBackdrop)) { Wallpaper(touches = touches) }
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = top + 64.dp + (if (!searching && messages.any { it.pinned }) 54.dp else 0.dp), bottom = bottom + 80.dp + (if (panel) 306.dp else 0.dp) + (if (botShown) (botRows!!.size * 50 + 6).dp else 0.dp)),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
            ) {
                messages.forEachIndexed { i, m ->
                    item(key = m.id) {
                        MessageRow(
                            m, chat.isGroup, wallBackdrop,
                            Modifier.animateItem(fadeInSpec = spring(stiffness = 400f)),
                            onLongPress = { rect, preview ->
                                menu.show(
                                    rect,
                                    listOfNotNull(
                                        MenuItem("Reply", Icons.AutoMirrored.Rounded.Reply) { editing = null; replyTo = m },
                                        MenuItem("Edit", Icons.Rounded.Edit) { replyTo = null; editing = m; draft = m.text }.takeIf { m.canEdit },
                                        MenuItem("Copy", Icons.Rounded.ContentCopy) { clipboard.setText(AnnotatedString(m.text)) }.takeIf { m.text.isNotBlank() && !m.isSticker },
                                        MenuItem(if (m.pinned) "Unpin" else "Pin", Icons.Rounded.PushPin) { repo.pinMessage(chatId, m.id, !m.pinned) },
                                        MenuItem("Forward", Icons.AutoMirrored.Rounded.Forward) { forwarding = m },
                                        MenuItem("Delete", Icons.Rounded.Delete, destructive = true) { repo.deleteMessages(chatId, listOf(m.id)) },
                                    ),
                                    cover = false,
                                    onReact = { repo.react(chatId, m.id, it) },
                                    reacted = m.reactions.filter { it.chosen }.map { it.emoji }.toSet(),
                                    preview = preview,
                                )
                            },
                            onPhoto = { viewer = m.id },
                            onDownload = { repo.download(chatId, m.id) },
                            onButton = { onBotButton(m, it) },
                            onVideo = { viewer = m.id },
                            onReact = { repo.react(chatId, m.id, it) },
                        )
                    }
                    // Reverse layout: emitting the divider after the oldest unread message puts it right above it.
                    val older = messages.getOrNull(i + 1)
                    if (unreadFrom > 0 && !m.out && m.id > unreadFrom && (older == null || older.id <= unreadFrom)) {
                        item(key = "unread") { UnreadDivider() }
                    }
                }
            }
        }

        // Header: back · name capsule · avatar (or the search field while searching)
        AnimatedContent(
            searching, label = "header",
            transitionSpec = {
                (fadeIn(spring(stiffness = 500f)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 500f)) { -it / 4 }) togetherWith
                    fadeOut(spring(stiffness = 700f))
            },
        ) { searchMode ->
        if (searchMode) Column(Modifier.fillMaxWidth().scrollEdge(backdrop).statusBarsPadding()) {
            Box(Modifier.padding(horizontal = EdgeGap, vertical = 8.dp)) {
                ChatSearchBar(backdrop, query, { query = it }) { searching = false; query = "" }
            }
            ChatSearchResults(backdrop, results, chat.title) { jumpTo = it.id; results = null; keyboard?.hide() }
        } else Box(Modifier.fillMaxWidth().scrollEdge(backdrop).statusBarsPadding().padding(horizontal = EdgeGap, vertical = 8.dp)) {
            GlassCircle(backdrop, onBack, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Rounded.ArrowBackIosNew, Tg.Text, 20.dp) }
            GlassBox(backdrop, Modifier.align(Alignment.Center).widthIn(max = 230.dp), shape = com.kyant.shapes.Capsule(), onClick = openProfile) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T(chat.title, 15.sp, Tg.Text, FontWeight.SemiBold)
                    AnimatedContent(if (typing) "typing…" else chat.status, label = "status",
                        transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }) { s ->
                        T(s, 12.sp, if (s == "online" || s == "typing…") Tg.Accent else Tg.Secondary)
                    }
                }
            }
            // Avatar alone (it has its own glass rim): a round glass ring around a squarer avatar looked broken.
            Avatar(
                chat.title, chat.colorIndex, 44.dp, saved = chat.isSaved, photoPath = chat.photoPath,
                modifier = Modifier.align(Alignment.CenterEnd).clickable(interactionSource = null, indication = null, onClick = openProfile),
            )
        }
        }

        val pinnedNow = messages.firstOrNull { it.pinned }
        var lastPinned by remember { mutableStateOf(pinnedNow) }
        if (pinnedNow != null) lastPinned = pinnedNow
        AnimatedVisibility(
            !searching && pinnedNow != null,
            Modifier.statusBarsPadding().padding(top = 64.dp, start = EdgeGap, end = EdgeGap),
            enter = fadeIn(spring(stiffness = 500f)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 500f)) { -it / 2 },
            exit = fadeOut(spring(stiffness = 700f)) + slideOutVertically(spring(stiffness = 700f)) { -it / 2 },
        ) {
            val pinned = lastPinned ?: return@AnimatedVisibility
            PinnedBar(
                backdrop, pinned, Modifier,
                onOpen = { jumpTo = pinned.id },
                onUnpin = { repo.pinMessage(chatId, pinned.id, false) },
            )
        }

        // Input area: reply/edit panel above the input row.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().scrollEdge(backdrop, top = false).padding(start = EdgeGap, end = EdgeGap, top = 8.dp).bottomGap(EdgeGap)) {
            val quoted = editing ?: replyTo
            var shown by remember { mutableStateOf(quoted) }
            if (quoted != null) shown = quoted
            AnimatedVisibility(quoted != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                val target = shown ?: return@AnimatedVisibility
                GlassBox(backdrop, Modifier.padding(bottom = 6.dp).fillMaxWidth()) { Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (editing != null) Icons.Rounded.Edit else Icons.AutoMirrored.Rounded.Reply, Tg.Accent, 20.dp)
                    Box(Modifier.padding(horizontal = 10.dp).width(2.dp).height(32.dp).background(Tg.Accent, RoundedCornerShape(1.dp)))
                    Column(Modifier.weight(1f)) {
                        T(if (editing != null) "Edit Message" else "Reply to ${if (target.out) "yourself" else target.senderName ?: chat.title}", 14.sp, Tg.Accent, FontWeight.SemiBold)
                        T(previewOf(target), 14.sp, Tg.Secondary)
                    }
                    Box(Modifier.size(32.dp).clip(CircleShape).clickable { replyTo = null; if (editing != null) draft = ""; editing = null }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, Tg.Secondary, 20.dp)
                    }
                } }
            }
            // A public channel or group opened from global search: read it, then join instead of typing.
            if (!chat.isMember) org.liquidgram.glass.LiquidButton(
                { repo.joinChat(chatId) }, backdrop, Modifier.fillMaxWidth(), tint = Tg.Accent, height = 48.dp,
            ) { T(if (chat.isGroup) "Join Group" else "Join Channel", 17.sp, Color.White, FontWeight.SemiBold) }
            else InputRow(
                backdrop = backdrop,
                draft = draft,
                onDraft = {
                    draft = it
                    val now = System.currentTimeMillis()
                    if (it.isNotBlank() && now - lastTyping > 4000) { lastTyping = now; repo.sendTyping(chatId) }
                },
                editing = editing != null,
                onAttach = {
                    listOf(
                        MenuItem("Photo", Icons.Rounded.Image) { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        MenuItem("Video", Icons.Rounded.Videocam) { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                        MenuItem("File", Icons.Rounded.Description) { pickFile.launch(arrayOf("*/*")) },
                    )
                },
                menu = menu,
                round = round,
                onRound = { path, sec -> repo.sendVideoNote(chatId, path, sec) },
                onSend = ::submit,
                onVoice = { path, sec -> repo.sendVoice(chatId, path, sec) },
                panelOpen = panel,
                onPanel = { panel = !panel; if (panel) keyboard?.hide() else keyboard?.show() },
                placeholder = keyboardMsg?.keyboardPlaceholder?.takeIf { botRows != null && it.isNotBlank() } ?: "Message",
                botKeyboard = if (botRows == null) null else botShown,
                onBotKeyboard = {
                    if (botShown) { botOpen = false; keyboard?.show() } else { botOpen = true; panel = false; keyboard?.hide() }
                },
            )
            AnimatedVisibility(botShown, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                BotKeyboard(botRows.orEmpty(), backdrop, Modifier.padding(top = 6.dp)) { onBotButton(keyboardMsg!!, it) }
            }
            AnimatedVisibility(panel, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Box(Modifier.padding(top = 6.dp)) {
                    EmojiPanel(repo, backdrop, onEmoji = { draft += it }, onSticker = { repo.sendSticker(chatId, it) })
                }
            }
        }

        GlassMenuHost(menu, backdrop)
        RoundRecordingOverlay(round)
        MediaViewer(
            remember(messages) { messages.filter { it.isPhoto || it.isVideo }.reversed() }, viewer, chat.title, onClose = { viewer = null },
            onReact = { m, e -> repo.react(chatId, m.id, e) },
            onReply = { viewer = null; editing = null; replyTo = it },
            onForward = { forwarding = it },
            onDelete = { repo.deleteMessages(chatId, listOf(it.id)); viewer = null },
            onShowInChat = { viewer = null; jumpTo = it.id },
        )
        forwarding?.let { f ->
            BackHandler { forwarding = null }
            ForwardPicker(repo, onPick = { to -> repo.forward(chatId, listOf(f.id), to); forwarding = null }) { forwarding = null }
        }
    }
}

private fun previewOf(m: Message) = when {
    m.isVoice -> "🎤 Voice message"
    m.isPhoto && m.text.isBlank() -> "🖼 Photo"
    m.isVideo && m.text.isBlank() -> if (m.isGif) "GIF" else "📹 Video"
    else -> m.text
}

/** Own messages: the glass is tinted with the accent (strength from Appearance). */
private fun ownTint(out: Boolean) = if (out) Tg.Accent.copy(alpha = Look.ownTint) else null

/**
 * One message. Long press keeps the message where it is: everything around it blurs (the screen menu draws a copy
 * of it on top), the reaction bar sits above it and the actions below. Double tap reacts.
 */
@Composable
private fun MessageRow(
    m: Message, isGroup: Boolean, wallBackdrop: Backdrop, modifier: Modifier,
    onLongPress: (Rect, @Composable () -> Unit) -> Unit, onPhoto: (String) -> Unit, onDownload: () -> Unit,
    onButton: (org.liquidgram.data.BotButton) -> Unit,
    onVideo: (Message) -> Unit,
    onReact: (String) -> Unit,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var lastTap by remember { mutableLongStateOf(0L) }
    val tap = {
        val now = System.currentTimeMillis()
        if (now - lastTap < 300 && Look.doubleTap.isNotEmpty()) { onReact(Look.doubleTap); lastTap = 0L } else lastTap = now
    }
    val body: @Composable () -> Unit = {
        MessageBody(m, isGroup, wallBackdrop, onPhoto, onDownload, onButton, onVideo, onReact, onTap = tap, onLongPress = { onLongPress(bounds) { MessageBody(m, isGroup, wallBackdrop, {}, {}, {}, {}, {}) } })
    }
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (m.out) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (isGroup && !m.out) {
            Avatar(m.senderName ?: "?", m.senderColor, 34.dp, photoPath = m.senderPhoto)
            Spacer(Modifier.width(6.dp))
        }
        Box(
            Modifier.menuAnchor { bounds = it }
                // Stickers and round videos have no glass of their own: they get the gestures here.
                .then(if (m.isSticker || m.isRound) Modifier.combinedClickable(interactionSource = null, indication = null, onClick = tap, onLongClick = { onLongPress(bounds) { MessageBody(m, isGroup, wallBackdrop, {}, {}, {}, {}, {}) } }) else Modifier),
        ) { body() }
    }
}

@Composable
private fun MessageBody(
    m: Message, isGroup: Boolean, wallBackdrop: Backdrop,
    onPhoto: (String) -> Unit, onDownload: () -> Unit, onButton: (org.liquidgram.data.BotButton) -> Unit,
    onVideo: (Message) -> Unit, onReact: (String) -> Unit,
    onTap: () -> Unit = {}, onLongPress: (() -> Unit)? = null,
) {
    if (m.isRound) {
        Column(horizontalAlignment = Alignment.End) {
            RoundVideo(m.roundPath, 220.dp, onDownload)
            Box(Modifier.padding(top = 4.dp).background(Color.Black.copy(alpha = 0.25f), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp)) {
                T("%d:%02d  %s".format(m.roundDuration / 60, m.roundDuration % 60, m.time), 11.sp, Color.White)
            }
        }
        return
    }
    if (m.isSticker) {
        Column(horizontalAlignment = Alignment.End) {
            StickerView(m.stickerPath, m.stickerKind, m.text, 170.dp)
            Box(Modifier.background(Color.Black.copy(alpha = 0.25f), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp)) { T(m.time, 11.sp, Color.White) }
            ReactionPills(m, onReact)
        }
        return
    }
    val maxWidth = if (isGroup && !m.out) 270.dp else 300.dp
    // Glass bubble without a tail; Glasmio's press physics (lift, drag, glow) on touch.
    val balloon: @Composable (Modifier) -> Unit = { mod ->
        GlassBox(
            wallBackdrop, mod, RoundedCornerShape(Look.bubbleRadius.dp), tint = ownTint(m.out),
            onClick = onTap, onLongClick = onLongPress,
            // Clear: Glasmio's lightest material (thin frost, no lift shadow) — many bubbles scroll at once.
            material = io.github.neoruaa.glasmio.glass.NavigationGlassMaterial.Clear,
        ) { Bubble(m, Modifier, onPhoto, onDownload, onVideo, onReact) }
    }
    // With an inline keyboard the bubble and its buttons share one width (the wider of the two), as in Telegram.
    if (m.buttons.isNotEmpty()) Column(Modifier.widthIn(max = maxWidth).width(IntrinsicSize.Max)) {
        balloon(Modifier.fillMaxWidth())
        BotKeyboard(m.buttons, wallBackdrop, Modifier.padding(top = 4.dp).fillMaxWidth(), onButton)
    } else balloon(Modifier.widthIn(max = maxWidth))
}

@Composable
private fun Bubble(m: Message, modifier: Modifier, onPhoto: (String) -> Unit, onDownload: () -> Unit, onVideo: (Message) -> Unit, onReact: (String) -> Unit) {
        val text = Tg.Text
        val meta = Tg.Secondary
        Column(modifier.padding(3.dp)) {
            m.senderName?.let { T(it, 14.sp, Tg.avatarColors[m.senderColor % Tg.avatarColors.size].second, FontWeight.SemiBold, Modifier.padding(start = 9.dp, top = 4.dp, end = 9.dp)) }
            m.forwardedFrom?.let { ForwardedLabel(it) }
            if (m.replyName != null) ReplyQuote(m.replyName, m.replyText.orEmpty())
            when {
                m.isPhoto -> PhotoContent(m, meta, onPhoto, onDownload)
                m.isVideo -> {
                    VideoContent(m, onVideo, onDownload) { mod ->
                        Box(mod.background(Color.Black.copy(alpha = 0.35f), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp)) { Meta(m, Color.White, Modifier) }
                    }
                    if (m.text.isNotBlank()) Box(Modifier.width(260.dp).padding(start = 9.dp, end = 7.dp, top = 4.dp, bottom = 4.dp)) {
                        T(m.text + " ".repeat(if (m.out) 16 else 11), 17.sp, Tg.Text, maxLines = Int.MAX_VALUE)
                        Meta(m, meta, Modifier.align(Alignment.BottomEnd))
                    }
                }
                m.isFile -> DocumentContent(m, meta, onDownload)
                m.isVoice -> VoiceContent(m, text, meta)
                else -> Box(Modifier.padding(start = 9.dp, end = 7.dp, top = 4.dp, bottom = 4.dp).themeTint()) {
                    // Trailing no-break spaces reserve room so the time sits inline, Telegram-style.
                    T(m.text + " ".repeat((if (m.out) 16 else 11) + if (m.edited) 8 else 0), 17.sp, text, maxLines = Int.MAX_VALUE)
                    Meta(m, meta, Modifier.align(Alignment.BottomEnd))
                }
            }
            ReactionPills(m, onReact)
        }
}

@Composable
private fun Meta(m: Message, color: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        T((if (m.edited) "edited " else "") + m.time, 11.sp, color)
        if (m.out) { Spacer(Modifier.width(2.dp)); Icon(if (m.read) Icons.Rounded.DoneAll else Icons.Rounded.Done, color, 14.dp) }
    }
}

@Composable
private fun ReplyQuote(name: String, text: String) {
    val bar = Tg.Accent
    Row(
        Modifier.padding(start = 6.dp, end = 6.dp, top = 5.dp).widthIn(min = 120.dp)
            .background(bar.copy(alpha = 0.14f), RoundedCornerShape(6.dp)).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(36.dp).background(bar, RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)))
        Column(Modifier.padding(start = 7.dp, top = 3.dp, bottom = 3.dp)) {
            T(name, 13.sp, bar, FontWeight.SemiBold)
            T(text.ifBlank { "Message" }, 13.sp, Tg.Text)
        }
    }
}

@Composable
private fun PhotoContent(m: Message, meta: Color, onPhoto: (String) -> Unit, onDownload: () -> Unit) {
    val photo = rememberPhoto(m.photoPath)
    Box(
        Modifier.width(260.dp).aspectRatio(m.photoAspect.coerceIn(0.6f, 1.8f))
            .clip(RoundedCornerShape((Look.bubbleRadius - 3f).coerceAtLeast(4f).dp))
            .background(Tg.Placeholder)
            .clickable { m.photoPath?.let(onPhoto) ?: onDownload() },
    ) {
        FadeImage(photo, Modifier.fillMaxSize())
        if (m.text.isBlank()) Box(
            Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp)
        ) { Meta(m, Color.White, Modifier) }
    }
    if (m.text.isNotBlank()) Box(Modifier.width(260.dp).padding(start = 9.dp, end = 7.dp, top = 4.dp, bottom = 4.dp)) {
        T(m.text + " ".repeat(if (m.out) 16 else 11), 17.sp, Tg.Text, maxLines = Int.MAX_VALUE)
        Meta(m, meta, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun VoiceContent(m: Message, text: Color, meta: Color) {
    val playing = VoicePlayer.playingId == m.id
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) { progress = 0f; return@LaunchedEffect }
        while (true) { progress = VoicePlayer.position(); delay(40) }
    }
    val bars = remember(m.id) { val rnd = Random(m.id); List(32) { 0.2f + rnd.nextFloat() * 0.8f } }
    Row(Modifier.padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(42.dp).background(Tg.Accent, CircleShape).clip(CircleShape)
                .clickable { VoicePlayer.toggle(m.id, m.voicePath) },
            contentAlignment = Alignment.Center,
        ) {
            if (m.voicePath == null) Spinner(Color.White)
            else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, Color.White, 26.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Row(Modifier.height(22.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                bars.forEachIndexed { i, h ->
                    val done = i < (progress * bars.size)
                    Box(Modifier.width(3.dp).height((22 * h).dp).background(if (done) text else text.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("%d:%02d".format(m.voiceDuration / 60, m.voiceDuration % 60), 12.sp, meta)
                Spacer(Modifier.width(10.dp))
                Meta(m, meta, Modifier)
            }
        }
    }
}

@Composable
private fun UnreadDivider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(Tg.Bg.copy(alpha = 0.55f)).padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
        T("Unread Messages", 14.sp, Tg.Secondary, FontWeight.Medium)
    }
}

/** Text field + attach + send / hold-to-record mic. */
@Composable
private fun InputRow(
    backdrop: Backdrop, draft: String, onDraft: (String) -> Unit, editing: Boolean,
    onAttach: () -> List<MenuItem>, onSend: () -> Unit, onVoice: (String, Int) -> Unit,
    panelOpen: Boolean, onPanel: () -> Unit,
    menu: GlassMenuState, round: RoundRecorder, onRound: (String, Int) -> Unit,
    placeholder: String = "Message",
    botKeyboard: Boolean? = null, // null: the bot has no keyboard; true: it is showing
    onBotKeyboard: () -> Unit = {},
) {
    // Tap on the mic switches between voice and round video (Telegram); holding records the current one.
    var videoMode by rememberSaveable { mutableStateOf(false) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val context = LocalContext.current
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { !it }) Toast.makeText(context, Strings.tr("Camera access is needed for video messages"), Toast.LENGTH_SHORT).show()
    }
    val density = LocalDensity.current
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var recordStart by remember { mutableLongStateOf(0L) }
    var slide by remember { mutableFloatStateOf(0f) }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(recording) { while (recording) { elapsed = System.currentTimeMillis() - recordStart; delay(100) } }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Toast.makeText(context, if (ok) "Hold the mic to record" else "Microphone access is needed for voice messages", Toast.LENGTH_SHORT).show()
    }
    val cancelDistance = with(density) { 110.dp.toPx() }

    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(!recording, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            MenuButton(backdrop, onAttach) { Icon(Icons.Rounded.AttachFile, Tg.Text, 22.dp, Modifier.align(Alignment.Center)) }
        }
        GlassBox(backdrop, Modifier.weight(1f).heightIn(min = 44.dp)) { Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (recording) {
                // Recording: pulsing dot, timer, and the "slide to cancel" hint that follows the finger.
                val pulse by animateFloatAsState(if ((elapsed / 500) % 2 == 0L) 1f else 0.3f, label = "pulse")
                Box(Modifier.size(10.dp).graphicsLayer { alpha = pulse }.background(Tg.Red, CircleShape))
                Spacer(Modifier.width(8.dp))
                T("%d:%02d".format(elapsed / 60000, (elapsed / 1000) % 60), 17.sp, Tg.Text)
                Spacer(Modifier.weight(1f))
                T("‹ Slide to cancel", 15.sp, Tg.Secondary, modifier = Modifier.offset { IntOffset((slide * 0.6f).toInt(), 0) }.padding(end = 8.dp))
            } else {
                Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    if (draft.isEmpty()) T(placeholder, 17.sp, Tg.Secondary)
                    BasicTextField(
                        draft, onDraft, Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = Tg.Text, fontSize = 17.sp * Look.textScale, fontFamily = appFont()), cursorBrush = SolidColor(Tg.Accent), maxLines = 5,
                    )
                }
                if (botKeyboard != null) Icon(
                    if (botKeyboard) Icons.Rounded.Keyboard else Icons.Rounded.GridView, Tg.Secondary, 24.dp,
                    Modifier.clip(CircleShape).clickable(onClick = onBotKeyboard).padding(horizontal = 4.dp),
                )
                Icon(
                    if (panelOpen) Icons.Rounded.Keyboard else Icons.Outlined.EmojiEmotions, Tg.Secondary, 24.dp,
                    Modifier.clip(CircleShape).clickable(onClick = onPanel).padding(horizontal = 4.dp),
                )
                AnimatedVisibility(draft.isNotBlank(), enter = scaleIn(spring(0.5f, 500f)) + fadeIn(), exit = scaleOut(spring(stiffness = 800f)) + fadeOut()) {
                    Box(
                        Modifier.padding(start = 4.dp).size(32.dp).background(Tg.Accent, CircleShape).clip(CircleShape).clickable(onClick = onSend),
                        contentAlignment = Alignment.Center,
                    ) { Icon(if (editing) Icons.Rounded.Check else Icons.Rounded.ArrowUpward, Color.White, 20.dp) }
                }
            }
        } }
        AnimatedVisibility(draft.isBlank() && !editing, enter = scaleIn(spring(0.5f, 500f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
            val grow by animateFloatAsState(if (recording) 1.35f else 1f, spring(0.5f, 400f), label = "mic")
            GlassBox(
                backdrop,
                Modifier.size(44.dp).graphicsLayer { scaleX = grow; scaleY = grow }
                    .pointerInput(Unit) {
                        // Hold to record, release to send, slide left to cancel.
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            // A short tap only flips voice ↔ video.
                            // Released within 220 ms = a tap. (Not waitForUpOrCancellation: it gives up on any consumed
                            // event from the other handlers here and turned every hold into a 0-second recording.)
                            var released = false
                            withTimeoutOrNull(220) {
                                while (true) {
                                    val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                    if (c == null || !c.pressed) { released = true; break }
                                }
                            }
                            if (released) { videoMode = !videoMode; return@awaitEachGesture }
                            if (videoMode) {
                                if (!hasPermission(context, Manifest.permission.CAMERA) || !hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                    askCamera.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                                    return@awaitEachGesture
                                }
                                round.start(owner) { file, sec -> if (file != null) onRound(file.absolutePath, sec) }
                                // Letting go keeps recording (Send / Cancel are in the overlay); sliding left cancels.
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (change.position.x - down.position.x < -cancelDistance) { round.stop(cancel = true); break }
                                    if (change.changedToUpIgnoreConsumed()) break
                                }
                                return@awaitEachGesture
                            }
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                askMic.launch(Manifest.permission.RECORD_AUDIO)
                                return@awaitEachGesture
                            }
                            if (!recorder.start()) {
                                Toast.makeText(context, "Voice messages need Android 10 or newer", Toast.LENGTH_SHORT).show()
                                return@awaitEachGesture
                            }
                            recordStart = System.currentTimeMillis(); elapsed = 0; slide = 0f; recording = true
                            var cancelled = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                slide = (change.position.x - down.position.x).coerceAtMost(0f)
                                change.consume()
                                if (slide < -cancelDistance) cancelled = true
                                if (change.changedToUpIgnoreConsumed() || cancelled) break
                            }
                            recording = false
                            recorder.stop(cancelled)?.let { (file, sec) -> onVoice(file.absolutePath, sec) }
                        }
                    }
                    ,
                shape = CircleShape,
                contentAlignment = Alignment.Center,
            ) {
                if (recording) Box(Modifier.fillMaxSize().background(Tg.Accent, CircleShape))
                Icon(if (videoMode && !recording) Icons.Rounded.Videocam else Icons.Rounded.Mic, if (recording) Color.White else Tg.Text, 22.dp)
            }
        }
    }
}

/** One voice note playing at a time, app-wide. */
object VoicePlayer {
    var playingId by mutableStateOf<Long?>(null)
        private set
    private var player: MediaPlayer? = null

    fun toggle(id: Long, path: String?) {
        if (playingId == id) { stop(); return }
        stop()
        if (path == null) return
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            playingId = id
        }.onFailure { stop() }
    }

    fun stop() {
        player?.runCatching { release() }
        player = null
        playingId = null
    }

    fun position(): Float = player?.let { if (it.duration > 0) it.currentPosition.toFloat() / it.duration else 0f } ?: 0f
}

/** Records Ogg/Opus, the format Telegram expects for voice notes (MediaRecorder supports it from Android 10). */
private class VoiceRecorder(private val context: android.content.Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    fun start(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return runCatching {
            val f = File(context.cacheDir, "voice_${System.currentTimeMillis()}.ogg")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            r.setAudioSamplingRate(48000)
            r.setAudioEncodingBitRate(32000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r; file = f; startedAt = System.currentTimeMillis()
        }.isSuccess
    }

    /** Stops; returns the file and its length unless cancelled or shorter than a second. */
    fun stop(cancel: Boolean): Pair<File, Int>? {
        val r = recorder ?: return null
        recorder = null
        runCatching { r.stop() }
        r.release()
        val f = file ?: return null
        val sec = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        if (cancel || sec < 1) { f.delete(); return null }
        return f to sec
    }
}
