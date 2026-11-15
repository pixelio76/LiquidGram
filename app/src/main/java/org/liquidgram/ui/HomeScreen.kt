package org.liquidgram.ui

import androidx.compose.material.icons.rounded.SystemUpdate
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import org.liquidgram.data.Chat
import org.liquidgram.data.TelegramRepository

private const val TAB_CHATS = 2

@Composable
fun HomeScreen(repo: TelegramRepository, openChat: (Long) -> Unit, openAppearance: () -> Unit, open: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_CHATS) }
    val rawChats by repo.chats.collectAsState()
    val chats = remember(rawChats, Plugins.enabled, Plugins.all) { Plugins.chats(rawChats) }
    // One backdrop per page (each records its own layer). The tab bar and menus sample the shown page's: a layer over
    // all pages re-recorded the whole screen every frame and halved the frame rate.
    val backdrops = List(4) { rememberLayerBackdrop() }
    val menu = rememberGlassMenu()
    // Like Telegram: muted chats don't count towards the tab badge.
    val unread = chats.filter { !it.muted }.sumOf { it.unread }
    var chatsIcon by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    Box(Modifier.fillMaxSize().background(Tg.Bg)) {
        // Tab switch: the new page slides in from the side of its tab and pushes the old one out (opaque pages, no
        // alpha: blending two glass pages through an offscreen layer every frame was what stuttered). Same critically
        // damped spring as screen navigation. Pages stay composed once opened, so nothing is rebuilt mid-slide.
        var shownTab by remember { mutableIntStateOf(tab) }
        var fromTab by remember { mutableIntStateOf(tab) }
        val slide = remember { androidx.compose.animation.core.Animatable(1f) }
        LaunchedEffect(tab) {
            if (tab == shownTab) return@LaunchedEffect
            fromTab = shownTab; shownTab = tab
            slide.snapTo(0f)
            val k = (2f * Math.PI.toFloat() / 0.36f).let { it * it }
            slide.animateTo(1f, spring(dampingRatio = 1f, stiffness = k))
        }
        val backdrop = backdrops[shownTab]
        Box(Modifier.fillMaxSize()) {
            for (t in 0..3) androidx.compose.runtime.key(t) {
                val selected = t == tab
                var opened by remember { mutableStateOf(selected) }
                if (selected) opened = true
                if (opened) Box(
                    Modifier.fillMaxSize().zIndex(if (selected) 1f else 0f)
                        .graphicsLayer {
                            val p = slide.value
                            val dir = if (shownTab > fromTab) 1f else -1f
                            when {
                                t == shownTab -> { translationX = dir * size.width * (1f - p); alpha = 1f }
                                t == fromTab && p < 1f -> { translationX = -dir * size.width * p; alpha = 1f }
                                else -> alpha = 0f // off screen: not drawn at all
                            }
                        }
                        // Hidden pages under the shown one must not take its touches.
                        .then(if (selected) Modifier else Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }),
                ) {
                    val b = backdrops[t]
                    when (t) {
                        0 -> ContactsPage(repo, chats, b, openChat, open)
                        1 -> CallsPage(repo, b, openChat)
                        TAB_CHATS -> ChatsPage(repo, chats, b, menu, openChat, open) { tab = 3 }
                        else -> SettingsPage(repo, b, openChat, openAppearance, open) { tab = 1 }
                    }
                }
            }
        }

        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().scrollEdge(backdrop, top = false)
                .padding(start = EdgeGap, end = EdgeGap, top = 12.dp).bottomGap(EdgeGap)
        ) {
            // Glasmio's iOS tab bar: native selection lens, press and drag motion (github.com/NEORUAA/Glasmio).
            val items = remember(Look.language) {
                listOf(
                    io.github.neoruaa.glasmio.ui.GlasmioTabItem(0, Strings.tr("Contacts"), io.github.neoruaa.glasmio.icons.SfSymbol.PersonFilled),
                    io.github.neoruaa.glasmio.ui.GlasmioTabItem(1, Strings.tr("Calls"), io.github.neoruaa.glasmio.icons.SfSymbol.ClockFilled),
                    io.github.neoruaa.glasmio.ui.GlasmioTabItem(2, Strings.tr("Chats"), io.github.neoruaa.glasmio.icons.SfSymbol.HouseFilled),
                    io.github.neoruaa.glasmio.ui.GlasmioTabItem(3, Strings.tr("Settings"), io.github.neoruaa.glasmio.icons.SfSymbol.Gearshape),
                )
            }
            io.github.neoruaa.glasmio.ui.GlasmioNavigationBar(
                items = items,
                selectedKey = tab,
                onSelected = { tab = it },
                onExpand = {},
                compactProgress = remember { mutableStateOf(0f) },
                compactSize = io.github.neoruaa.glasmio.ui.CompactBottomControlSize,
                backdrop = backdrop,
                onItemIconBoundsChanged = { key, r -> if (key == TAB_CHATS) chatsIcon = r },
            )
        }
        // Unread badge on the Chats icon (the Glasmio bar has no badge slot).
        var lastUnread by remember { mutableStateOf(unread) }
        if (unread > 0) lastUnread = unread
        androidx.compose.animation.AnimatedVisibility(
            unread > 0 && chatsIcon != androidx.compose.ui.geometry.Rect.Zero,
            Modifier.offset { androidx.compose.ui.unit.IntOffset((chatsIcon.right - 10.dp.toPx()).toInt(), (chatsIcon.top - 6.dp.toPx()).toInt()) },
            enter = androidx.compose.animation.scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f)) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.scaleOut(spring(stiffness = 700f)) + androidx.compose.animation.fadeOut(),
        ) { Badge(lastUnread, Tg.Red) }

        GlassMenuHost(menu, backdrop)
    }
}

/** Page skeleton: scrolling content recorded into the backdrop, glass header floating above it. */
@Composable
private fun Page(
    backdrop: io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop,
    title: String,
    content: @Composable (PaddingValues) -> Unit,
    leading: @Composable () -> Unit = {},
    titleExtra: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    overlay: @Composable () -> Unit = {},
    titleAlpha: () -> Float = { 1f },
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).trackBackdropPosition(backdrop).background(Tg.Bg)) {
            content(PaddingValues(top = top + 60.dp, bottom = 120.dp))
        }
        Box(Modifier.fillMaxWidth().scrollEdge(backdrop).statusBarsPadding().height(60.dp).padding(horizontal = EdgeGap)) {
            Box(Modifier.align(Alignment.CenterStart)) { leading() }
            Row(Modifier.align(Alignment.Center).graphicsLayer { alpha = titleAlpha() }, verticalAlignment = Alignment.CenterVertically) {
                T(title, 17.sp, Tg.Text, FontWeight.SemiBold)
                titleExtra()
            }
            Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(10.dp)) { trailing() }
        }
        overlay()
    }
}

@Composable
private fun ChatsPage(
    repo: TelegramRepository,
    chats: List<Chat>,
    backdrop: io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop,
    menu: GlassMenuState,
    openChat: (Long) -> Unit,
    open: (String) -> Unit,
    openSettings: () -> Unit,
) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val soon = rememberSoon()
    val folders by repo.folders.collectAsState()
    // Chips: All, Unread, then the account's own folders.
    val filters = listOf("All", "Unread") + folders.drop(1)
    if (filter >= filters.size) filter = 0
    val inFilter = when (filter) {
        0 -> chats
        1 -> chats.filter { it.unread > 0 }
        else -> chats.filter { filters[filter] in it.folders }
    }
    val shown = if (query.isBlank()) inFilter else chats.filter {
        it.title.contains(query.trim(), ignoreCase = true) || it.lastMessage.contains(query.trim(), ignoreCase = true)
    }
    // Global search: public channels, groups, bots and people on the server, joined or not (debounced while typing).
    var global by remember { mutableStateOf(emptyList<Chat>()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        global = emptyList()
        if (query.trim().removePrefix("@").length < 2) { searching = false; return@LaunchedEffect }
        searching = true
        kotlinx.coroutines.delay(350)
        global = runCatching { repo.searchGlobal(query) }.getOrDefault(emptyList()).filter { g -> shown.none { it.id == g.id } }
        searching = false
    }

    // iOS large title: "Chats" scrolls away with the list, and the small title fades into the bar instead.
    val listState = rememberLazyListState()
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 60 } }
    val titleAlpha by animateFloatAsState(if (collapsed) 1f else 0f, label = "title")

    Page(
        backdrop, "Chats",
        titleAlpha = { titleAlpha },
        leading = { GlassCircle(backdrop, openSettings) { Icon(Icons.Outlined.Settings, Tg.Text, 22.dp) } },
        trailing = {
            GlassCircle(backdrop, { soon("Posting stories") }) { Icon(Icons.Outlined.AddCircleOutline, Tg.Text, 22.dp) }
            // Blue "+" with its menu: Glasmio's button-to-menu morph.
            MenuButton(
                backdrop,
                {
                    listOf(
                        MenuItem("New Group", Icons.Outlined.Group) { open("newGroup") },
                        MenuItem("New Contact", Icons.Outlined.PersonAddAlt) { open("newContact") },
                        MenuItem("New Channel", Icons.Outlined.Campaign) { open("newChannel") },
                        MenuItem("New Secret Chat", Icons.Outlined.Lock) { open("newSecret") },
                    )
                },
                tint = Tg.Accent,
            ) { Icon(Icons.Rounded.Add, Color.White, 26.dp, Modifier.align(Alignment.Center)) }
        },
        content = { pad ->
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = pad) {
                item { T("Chats", 34.sp, Tg.Text, FontWeight.Bold, Modifier.padding(start = 16.dp, top = 2.dp, bottom = 10.dp)) }
                item { FilterChips(filters, filter) { filter = it } }
                item { SearchField(query) { query = it } }
                if (query.isBlank() && Look.showStories) item { StoriesRow(repo, openChat) }
                items(shown, key = { it.id }) { chat ->
                    ChatRow(
                        chat, Modifier.animateItem(),
                        onLongClick = { rect ->
                            // The panel grows out of a point at the row's bottom-right corner, not out of the whole row
                            // (a row-sized blob looked like the row itself turning into blurry glass).
                            menu.show(
                                androidx.compose.ui.geometry.Rect(rect.right - 24f, rect.bottom - 1f, rect.right - 23f, rect.bottom),
                                listOfNotNull(
                                    MenuItem(if (chat.pinned) "Unpin" else "Pin", Icons.Outlined.PushPin) { repo.setPinned(chat.id, !chat.pinned) },
                                    MenuItem(if (chat.muted) "Unmute" else "Mute", if (chat.muted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff) {
                                        repo.setMuted(chat.id, !chat.muted)
                                    },
                                    MenuItem("Mark as Read", Icons.Outlined.MarkChatRead) { repo.readChat(chat.id) }.takeIf { chat.unread > 0 },
                                    MenuItem("Delete", Icons.Outlined.Delete, destructive = true) { repo.deleteChat(chat.id) },
                                ),
                                cover = false,
                            )
                        },
                    ) { openChat(chat.id) }
                }
                if (query.isNotBlank()) {
                    if (global.isNotEmpty() || searching) item(key = "globalTitle") {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            T("GLOBAL SEARCH", 13.sp, Tg.Secondary, modifier = Modifier.weight(1f))
                            if (searching) Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { Spinner(Tg.Secondary) }
                        }
                    }
                    // What it is (@name · subscribers / bot) where a chat shows its last message.
                    items(global, key = { "g" + it.id }) { found ->
                        ChatRow(found.copy(lastMessage = searchStatus(found.status), time = "", unread = 0), Modifier.animateItem(), onLongClick = {}) { openChat(found.id) }
                    }
                    if (!searching && shown.isEmpty() && global.isEmpty()) item(key = "noResults") {
                        T("No results", 17.sp, Tg.Secondary, modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        },
    )
}

/** Search result status in the interface language ("1.2K subscribers" → "1.2K подписчиков"). */
private fun searchStatus(s: String) = if (Look.language != 1) s else s.split(" · ").joinToString(" · ") { part ->
    when (part) {
        "bot" -> "бот"; "channel" -> "канал"; "group" -> "группа"
        else -> part.replace(" subscribers", " подписчиков").replace(" members", " участников")
    }
}

/** Filter pills; the accent pill slides between them with a spring. */
@Composable
private fun FilterChips(names: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val density = LocalDensity.current
    val rects = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val (x, w) = rects[selected] ?: (0f to 0f)
    val pillX by animateDpAsState(with(density) { x.toDp() }, spring(0.72f, 420f), label = "chipX")
    val pillW by animateDpAsState(with(density) { w.toDp() }, spring(0.72f, 420f), label = "chipW")
    val field = Tg.Field
    val accent = Tg.Accent
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
        // Chip backgrounds and the sliding accent pill, drawn under the labels.
        Box(
            Modifier.matchParentSize().drawBehind {
                val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                rects.values.forEach { (cx, cw) ->
                    drawRoundRect(field, androidx.compose.ui.geometry.Offset(cx, 0f), androidx.compose.ui.geometry.Size(cw, size.height), r)
                }
                if (pillW > 0.dp) drawRoundRect(accent, androidx.compose.ui.geometry.Offset(pillX.toPx(), 0f), androidx.compose.ui.geometry.Size(pillW.toPx(), size.height), r)
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            names.forEachIndexed { i, name ->
                val textColor by androidx.compose.animation.animateColorAsState(if (i == selected) Color.White else Tg.Text, label = "chipText")
                Box(
                    Modifier
                        .onGloballyPositioned { rects[i] = it.positionInParent().x to it.size.width.toFloat() }
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .clickable { onSelect(i) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { T(name, 15.sp, textColor, FontWeight.Medium) }
            }
        }
    }
}

@Composable
private fun StoriesRow(repo: TelegramRepository, openChat: (Long) -> Unit) {
    val stories by repo.stories.collectAsState()
    val me by repo.me.collectAsState()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(stories) { s ->
            // No circle clip here: it cut off the "+" badge that sits on the ring's corner.
            Box(Modifier.size(66.dp).clickable(interactionSource = null, indication = null) { if (!s.isMine) openChat(s.chatId) }) {
                val ring = when {
                    s.isMine -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    s.seen -> Brush.linearGradient(listOf(Tg.Muted, Tg.Muted))
                    Look.mono -> Brush.linearGradient(listOf(Tg.Accent.copy(alpha = 0.6f), Tg.Accent))
                    else -> Brush.linearGradient(listOf(Color(0xFF34C759), Color(0xFF0A84FF)))
                }
                Box(Modifier.fillMaxSize().border(2.5.dp, ring, avatarShape()).padding(5.dp)) {
                    Avatar(if (s.isMine) me.name else s.name, s.colorIndex, 56.dp, photoPath = if (s.isMine) me.photoPath else s.photoPath)
                }
                if (s.isMine) Box(
                    Modifier.align(Alignment.BottomEnd).size(22.dp).background(Tg.Bg, CircleShape).padding(2.dp)
                        .background(Tg.Accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, Color.White, 16.dp) }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: Chat, modifier: Modifier = Modifier, onLongClick: (androidx.compose.ui.geometry.Rect) -> Unit, onClick: () -> Unit) {
    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    Row(
        modifier.fillMaxWidth().menuAnchor { bounds = it }
            .combinedClickable(onClick = onClick, onLongClick = { onLongClick(bounds) })
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(chat.title, chat.colorIndex, if (Look.compactList) 46.dp else 60.dp, saved = chat.isSaved, photoPath = chat.photoPath)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Column(Modifier.padding(top = 9.dp, bottom = 9.dp, end = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Name takes all free space; the icons stick to it, time stays right.
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        T(chat.title, 17.sp, Tg.Text, FontWeight.SemiBold, Modifier.weight(1f, fill = false))
                        if (chat.verified) { Spacer(Modifier.width(3.dp)); Icon(Icons.Rounded.Verified, Tg.Accent, 17.dp) }
                        if (chat.muted) { Spacer(Modifier.width(3.dp)); Icon(Icons.Rounded.NotificationsOff, Tg.Muted, 15.dp) }
                    }
                    Spacer(Modifier.width(6.dp))
                    if (chat.outgoing || chat.outgoingRead) { Icon(if (chat.outgoingRead) Icons.Rounded.DoneAll else Icons.Rounded.Done, if (Look.mono) Tg.Accent else Tg.Read, 17.dp); Spacer(Modifier.width(3.dp)) }
                    T(chat.time, 15.sp, Tg.Secondary)
                }
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Top) {
                    val lines = if (Look.compactList) 1 else 2
                    T(chat.lastMessage, 15.sp, Tg.Secondary, maxLines = lines, modifier = Modifier.weight(1f).height((20 * lines).dp).themeTint())
                    Box(Modifier.padding(start = 8.dp, top = if (Look.compactList) 0.dp else 12.dp)) {
                        when {
                            chat.unread > 0 -> Badge(chat.unread, if (chat.muted) Tg.Muted else Tg.Badge)
                            chat.pinned -> Icon(Icons.Rounded.PushPin, Tg.Muted, 18.dp)
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Tg.Separator))
        }
    }
}

@Composable
private fun ContactsPage(repo: TelegramRepository, chats: List<Chat>, backdrop: io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop, openChat: (Long) -> Unit, open: (String) -> Unit) {
    val soon = rememberSoon()
    // shortcut: "contacts" = private chats; TDLib GetContacts would give the real address book
    val people = chats.filter { !it.isSaved && !it.verified && (it.status.startsWith("last seen") || it.status == "online") }.sortedBy { it.title }
    Page(
        backdrop, "Contacts",
        leading = { GlassPill(backdrop, { soon("Sorting") }) { T("Sort", 17.sp, Tg.Text, FontWeight.Medium) } },
        trailing = { GlassCircle(backdrop, { open("newContact") }) { Icon(Icons.Rounded.Add, Tg.Text, 24.dp) } },
        content = { pad ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
                item { SimpleRow(Icons.Outlined.PersonAddAlt, "Invite Friends", Tg.Accent) { soon("Invites") } }
                items(people, key = { it.id }) { c ->
                    PersonRow(c.title, c.colorIndex, c.status, c.status == "online", c.photoPath) { openChat(c.id) }
                }
            }
        },
    )
}

@Composable
private fun CallsPage(repo: TelegramRepository, backdrop: io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop, openChat: (Long) -> Unit) {
    val soon = rememberSoon()
    val calls by repo.calls.collectAsState()
    Page(
        backdrop, "Calls",
        leading = { GlassPill(backdrop, { soon("Editing calls") }) { T("Edit", 17.sp, Tg.Text, FontWeight.Medium) } },
        trailing = { GlassCircle(backdrop, { soon("Calls") }) { Icon(Icons.Rounded.Call, Tg.Text, 22.dp) } },
        content = { pad ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
                items(calls) { c ->
                    // As on iOS: the row calls back, the ⓘ opens the chat.
                    Row(Modifier.fillMaxWidth().clickable { repo.startCall(c.chatId, c.video) }.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.name, c.colorIndex, 44.dp, photoPath = c.photoPath)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Row(Modifier.padding(vertical = 10.dp).padding(end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    T(c.name, 17.sp, if (c.missed) Tg.Red else Tg.Text, FontWeight.Medium)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (c.outgoing) Icons.Rounded.CallMade else Icons.Rounded.CallReceived, Tg.Secondary, 14.dp)
                                        T(if (c.video) " Video" else if (c.outgoing) " Outgoing" else " Incoming", 14.sp, Tg.Secondary)
                                    }
                                }
                                T(c.time, 15.sp, Tg.Secondary)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Outlined.Info, Tg.Accent, 22.dp, Modifier.clickable { openChat(c.chatId) })
                            }
                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Tg.Separator))
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun SettingsPage(repo: TelegramRepository, backdrop: io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop, openChat: (Long) -> Unit, openAppearance: () -> Unit, open: (String) -> Unit, openCalls: () -> Unit) {
    val me by repo.me.collectAsState()
    val soon = rememberSoon()
    val context = androidx.compose.ui.platform.LocalContext.current
    val updateScope = androidx.compose.runtime.rememberCoroutineScope()
    val onRow: (String) -> Unit = { label ->
        when (label) {
            "Saved Messages" -> repo.chats.value.firstOrNull { it.isSaved }?.let { openChat(it.id) } ?: soon(label)
            "Recent Calls" -> openCalls()
            "Appearance" -> openAppearance()
            "My Profile" -> open("myProfile")
            "Devices" -> open("sessions")
            "Language" -> open("language")
            "Notifications and Sounds" -> open("notifications")
            "Privacy and Security" -> open("privacy")
            "Data and Storage" -> open("data")
            "Power Saving" -> open("power")
            "Quick Reactions" -> open("reactions")
            "Plugins" -> open("plugins")
            "App Update" -> updateScope.launch { Updates.check(context, manual = true) }
            else -> soon(label)
        }
    }
    Page(
        backdrop, "",
        leading = { ThemeButton(backdrop) },
        trailing = { GlassPill(backdrop, { open("editProfile") }) { T("Edit", 17.sp, Tg.Text, FontWeight.Medium) } },
        content = { pad ->
            LazyColumn(Modifier.fillMaxSize().background(Tg.Grouped), contentPadding = pad) {
                item {
                    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(me.name, me.colorIndex, 100.dp, photoPath = me.photoPath)
                        Spacer(Modifier.height(12.dp))
                        T(me.name, 26.sp, Tg.Text, FontWeight.SemiBold)
                        T("${me.phone} • ${me.username}", 15.sp, Tg.Secondary)
                    }
                }
                item {
                    Group(onRow,
                        Triple(Icons.Rounded.Person, "My Profile", Color(0xFFFF453A)),
                        Triple(Icons.Rounded.Bookmark, "Saved Messages", Color(0xFF0A84FF)),
                        Triple(Icons.Rounded.Call, "Recent Calls", Color(0xFF30D158)),
                        Triple(Icons.Rounded.Devices, "Devices", Color(0xFFFF9F0A)),
                        Triple(Icons.Rounded.Folder, "Chat Folders", Color(0xFF64D2FF)),
                    )
                }
                item {
                    Group(onRow,
                        Triple(Icons.Rounded.Notifications, "Notifications and Sounds", Color(0xFFFF453A)),
                        Triple(Icons.Rounded.Lock, "Privacy and Security", Color(0xFF8E8E93)),
                        Triple(Icons.Rounded.DataUsage, "Data and Storage", Color(0xFF30D158)),
                        Triple(Icons.Rounded.Palette, "Appearance", Color(0xFF0A84FF)),
                        Triple(Icons.Rounded.FavoriteBorder, "Quick Reactions", Color(0xFFFF375F)),
                        Triple(Icons.Rounded.Language, "Language", Color(0xFFBF5AF2)),
                        Triple(Icons.Rounded.Videocam, "Power Saving", Color(0xFFFF9F0A)),
                    )
                }
                item {
                    Group(onRow,
                        Triple(Icons.Rounded.Extension, "Plugins", Color(0xFF5E5CE6)),
                        Triple(Icons.Rounded.SystemUpdate, "App Update", Color(0xFF30D158)),
                    )
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).clip(RoundedCornerShape(26.dp)).background(Tg.Card)) {
                        Row(Modifier.fillMaxWidth().clickable { repo.logOut() }.padding(16.dp), horizontalArrangement = Arrangement.Center) {
                            T("Log Out", 17.sp, Tg.Red)
                        }
                    }
                }
                item { VersionFooter { open("developer") } }
            }
        },
    )
}

@Composable
private fun Group(onClick: (String) -> Unit, vararg rows: Triple<ImageVector, String, Color>) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).clip(RoundedCornerShape(26.dp)).background(Tg.Card)) {
        rows.forEachIndexed { i, (icon, label, color) ->
            Row(Modifier.fillMaxWidth().clickable { onClick(label) }.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).background(if (Look.mono) Tg.Accent else color, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, Color.White, 19.dp)
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Row(Modifier.height(50.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(label, 17.sp, Tg.Text, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ChevronRight, Tg.Muted, 22.dp)
                    }
                    if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Tg.Separator))
                }
            }
        }
    }
}

@Composable
private fun SimpleRow(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, color, 26.dp)
        Spacer(Modifier.width(22.dp))
        T(label, 17.sp, color)
    }
}

@Composable
private fun PersonRow(name: String, colorIndex: Int, status: String, online: Boolean, photoPath: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, colorIndex, 44.dp, photoPath = photoPath)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Column(Modifier.padding(vertical = 9.dp)) {
                T(name, 17.sp, Tg.Text, FontWeight.Medium)
                T(status, 14.sp, if (online) Tg.Accent else Tg.Secondary)
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Tg.Separator))
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(40.dp)
            .background(Tg.Field, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, Tg.Secondary, 20.dp)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) T("Search", 17.sp, Tg.Secondary)
            BasicTextField(
                query, onChange, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = TextStyle(color = Tg.Text, fontSize = 17.sp), cursorBrush = SolidColor(Tg.Accent),
            )
        }
        if (query.isNotEmpty()) Icon(Icons.Rounded.Cancel, Tg.Muted, 18.dp, Modifier.clickable { onChange("") })
    }
}

/** "LiquidGram for Android" + version at the bottom of Settings; ten quick taps open the developer menu. */
@Composable
private fun VersionFooter(openDeveloper: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    var last by remember { mutableStateOf(0L) }
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 24.dp)
            .clickable(interactionSource = null, indication = null) {
                val now = System.currentTimeMillis()
                taps = if (now - last < 1500) taps + 1 else 1
                last = now
                when {
                    taps >= 10 -> { taps = 0; openDeveloper() }
                    taps >= 5 -> android.widget.Toast.makeText(context, Strings.tr("Developer menu in") + " " + (10 - taps), android.widget.Toast.LENGTH_SHORT).show()
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T("LiquidGram for Android", 13.sp, Tg.Secondary, FontWeight.Medium)
        T("v${org.liquidgram.BuildConfig.VERSION_NAME} (${org.liquidgram.BuildConfig.VERSION_CODE})", 13.sp, Tg.Secondary)
    }
}
