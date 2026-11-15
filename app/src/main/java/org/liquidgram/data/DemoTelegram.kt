package org.liquidgram.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Offline demo data matching the reference screenshots. */
object DemoTelegram : TelegramRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private var nextId = 1000L

    override val auth = MutableStateFlow<AuthState>(AuthState.Ready)
    override val authError = MutableStateFlow<String?>(null)
    override val authBusy = MutableStateFlow(false)
    override val connection = MutableStateFlow<String?>(null)
    override fun submitAuth(input: String) {}
    override fun logOut() {}

    override val folders = MutableStateFlow(listOf("All Chats", "New", "Family", "Church", "Work", "People"))

    override val chats = MutableStateFlow(
        listOf(
            Chat(1, "Saved Messages", "image.jpeg", "Fri", 5, pinned = true, isSaved = true),
            Chat(13, "Demo Bot", "Как дела?", "11:05 PM", 4, status = "bot", folders = setOf("New")),
            Chat(2, "Emily Parker", "Did you see the photos? 👀 They turned out amazing!", "10:42 PM", 0, unread = 2, status = "online", folders = setOf("New", "People")),
            Chat(3, "Ryan Mitchell", "You're still meeting tonight? Can't wait", "10:23 PM", 2, outgoingRead = true, folders = setOf("Work", "People")),
            Chat(4, "Olivia Morgan", "I finished the draft 📄", "10:09 PM", 6, unread = 1, muted = true, folders = setOf("Work")),
            Chat(5, "Grace Foster", "Happy Birthday 🎉", "9:47 PM", 3, outgoingRead = true, folders = setOf("Family")),
            Chat(6, "Hannah Collins", "The tickets are booked 🎉 See you on Saturday!", "8:45 PM", 1, outgoingRead = true, folders = setOf("People")),
            Chat(7, "Charlotte Harris", "Stop, you're making me blush 🙈", "8:31 PM", 6, status = "online", folders = setOf("New", "People")),
            Chat(8, "Lily Anderson", "Sunday service starts at 10, don't be late 🙏", "8:02 PM", 4, folders = setOf("Church")),
            Chat(9, "Daily News", "Markets opened higher this morning as tech stocks rallied", "7:28 PM", 5, unread = 8, muted = true, verified = true, status = "1.2M subscribers"),
            Chat(10, "Mom", "Call me when you get home ❤️", "6:15 PM", 0, folders = setOf("Family")),
            Chat(11, "Design Team", "Noah: New mockups are in Figma", "Yesterday", 3, folders = setOf("Work"), status = "12 members", isGroup = true, lastReadInboxId = 2),
            Chat(12, "James Walker", "Sounds good, see you then!", "Yesterday", 2, outgoingRead = true, folders = setOf("People")),
        )
    )

    override val stories = MutableStateFlow(listOf(
        Story(0, "My Story", 5, seen = false, isMine = true),
        Story(0, "Emily", 0, seen = false),
        Story(0, "Ryan", 2, seen = false),
        Story(0, "Grace", 3, seen = false),
        Story(0, "Hannah", 1, seen = true),
        Story(0, "Lily", 4, seen = true),
    ))

    override val calls = MutableStateFlow(listOf(
        Call(2, "Emily Parker", 0, "10:12 PM", outgoing = false, missed = false, video = false),
        Call(10, "Mom", 0, "6:20 PM", outgoing = true, missed = false, video = true),
        Call(3, "Ryan Mitchell", 2, "Yesterday", outgoing = false, missed = true, video = false),
        Call(5, "Grace Foster", 3, "Tuesday", outgoing = true, missed = false, video = false),
        Call(7, "Charlotte Harris", 6, "Monday", outgoing = false, missed = false, video = true),
    ))

    override val me = MutableStateFlow(Profile(0, "Alex Turner", "online", "+1 555 010 2030", "@alexturner", "12 Mar 1998", null, null, null, null, colorIndex = 2))

    private val messageFlows = mutableMapOf<Long, MutableStateFlow<List<Message>>>()
    private val typingFlows = mutableMapOf<Long, MutableStateFlow<Boolean>>()

    private fun seed(chatId: Long): List<Message> = when (chatId) {
        13L -> listOf(
            Message(1, "/start", true, "11:04 PM"),
            Message(2, "Привет! Это кнопки бота 👇", false, "11:04 PM", buttons = listOf(
                listOf(BotButton("🔥 Опасная", "callback", style = "danger"), BotButton("✅ Успешная", "callback", style = "success")),
                listOf(BotButton("⭐ Основная", "callback", style = "primary"), BotButton("Обычная", "callback")),
                listOf(BotButton("Открыть telegram.org", "url", url = "https://telegram.org")),
            )),
            Message(3, "Как дела?", false, "11:05 PM", keyboard = listOf(
                listOf(BotButton("Превосходно"), BotButton("Замечательно"), BotButton("Отлично")),
                listOf(BotButton("Хорошо"), BotButton("Нормально"), BotButton("Плохо")),
            ), keyboardPlaceholder = "Выберите ответ"),
        )
        // Long history, so scrolling a busy group can be checked offline.
        11L -> List(60) { i ->
            val who = listOf("Noah" to 2, "Mia" to 6, "Liam" to 4)[i % 3]
            Message(-60L + i, listOf("Pushed the new build 🚀", "Can you check the onboarding flow?", "Looks great on my phone",
                "Meeting moved to 3 PM", "Who has the latest icons?", "Glass looks so smooth now ✨ The blur is perfect")[i % 6],
                i % 4 == 0, "Mon", senderName = if (i % 4 == 0) null else who.first, senderColor = who.second)
        } + listOf(
            Message(1, "Morning team! ☀️", false, "9:02 AM", senderName = "Noah", senderColor = 2),
            Message(2, "Standup in 10", false, "9:05 AM", senderName = "Mia", senderColor = 6),
            Message(3, "On my way", true, "9:06 AM", reactions = listOf(Reaction("👍", 2, false), Reaction("🔥", 1, false))),
            Message(4, "New mockups are in Figma", false, "9:40 AM", senderName = "Noah", senderColor = 2,
                replyName = "You", replyText = "On my way"),
        )
        7L -> listOf(
            Message(1, "Are you bringing those cookies tomorrow? 🍪", true, "8:20 PM"),
            Message(2, "Haha, deal! I'll keep a few just for you 💕", false, "8:24 PM", reactions = listOf(Reaction("❤", 1, true))),
            Message(3, "You're the best 😘", true, "8:25 PM"),
            Message(4, "Stop, you're making me blush 🙈", false, "8:31 PM", pinned = true),
            Message(5, "☺️", true, "8:31 PM", isSticker = true),
        )
        // Public chats from global search: a few posts, readable before joining.
        in 101L..105L -> directory.first { it.id == chatId }.let { c ->
            if (c.lastMessage.isBlank()) emptyList() else listOf(
                Message(1, "Welcome to ${c.title}! 👋", false, "Mon"),
                Message(2, c.lastMessage, false, c.time.ifBlank { "Mon" }),
            )
        }
        else -> {
            val chat = chats.value.firstOrNull { it.id == chatId } ?: return emptyList()
            listOf(
                Message(1, "Hey! How are you? 👋", true, "6:02 PM"),
                Message(2, "All good, busy day 😅", false, "6:05 PM"),
                Message(3, chat.lastMessage, chat.outgoingRead, chat.time),
            )
        }
    }

    // Newest first: the chat list is drawn bottom-up. Created on first use, so sending to an unopened chat (share sheet) works.
    private fun flowOf(chatId: Long) = messageFlows.getOrPut(chatId) { MutableStateFlow(seed(chatId).reversed()) }
    override fun messages(chatId: Long): StateFlow<List<Message>> = flowOf(chatId)

    /** Public chats the demo user isn't in, for global search. */
    private val directory = listOf(
        Chat(101, "Tech Daily", "Foldables are back: hands-on with the new lineup", "9:10 AM", 5, verified = true, status = "@techdaily · 1.2M subscribers", isMember = false),
        Chat(102, "Liquid Design", "Mia: anyone tried the new glass blur?", "8:41 AM", 6, isGroup = true, status = "@liquiddesign · 4.3K members", isMember = false),
        Chat(103, "Weather Bot", "Send a city name to get the forecast", "", 4, status = "@weather_bot · bot"),
        Chat(104, "Anna Smith", "", "", 1, status = "@annasmith"),
        Chat(105, "Travel Notes", "Sunrise over Lake Bled 🌄", "Yesterday", 3, status = "@travelnotes · 87.5K subscribers", isMember = false),
    )

    override suspend fun searchGlobal(query: String): List<Chat> {
        val q = query.trim().removePrefix("@")
        if (q.length < 2) return emptyList()
        delay(300) // a network round trip, so the "searching" state shows
        val listed = chats.value.map { it.id }.toSet()
        return directory.filter { it.id !in listed && (it.title.contains(q, true) || it.status.contains(q, true)) }
    }

    override fun chatInfo(chatId: Long): Chat? = chats.value.firstOrNull { it.id == chatId } ?: directory.firstOrNull { it.id == chatId }

    override fun joinChat(chatId: Long) {
        val c = directory.firstOrNull { it.id == chatId } ?: return
        chats.update { list -> if (list.any { it.id == chatId }) list else listOf(list.first()) + c.copy(isMember = true) + list.drop(1) }
    }

    override suspend fun pressBotButton(chatId: Long, messageId: Long, button: BotButton): String? =
        "✓ ${button.text}"

    override fun typing(chatId: Long): StateFlow<Boolean> =
        typingFlows.getOrPut(chatId) { MutableStateFlow(false) }

    override fun send(chatId: Long, text: String, replyTo: Long?) {
        val now = LocalTime.now().format(timeFormat)
        val flow = flowOf(chatId)
        val quoted = replyTo?.let { id -> flow.value.firstOrNull { it.id == id } }
        flow.update {
            listOf(Message(nextId++, text, true, now, canEdit = true,
                replyName = quoted?.let { q -> if (q.out) "You" else q.senderName ?: chats.value.first { c -> c.id == chatId }.title },
                replyText = quoted?.text)) + it
        }
        updateChat(chatId, text, now, read = false)
        scope.launch {
            delay(1200)
            updateChat(chatId, text, now, read = true)
            val typing = typingFlows.getOrPut(chatId) { MutableStateFlow(false) }
            typing.value = true
            delay(1800)
            typing.value = false
            val reply = listOf("Haha 😄", "Totally!", "Sounds great 👍", "Aww 🥰", "Tell me more!").random()
            val t = LocalTime.now().format(timeFormat)
            flow.update { listOf(Message(nextId++, reply, false, t)) + it }
            chats.update { list -> list.map { if (it.id == chatId) it.copy(lastMessage = reply, time = t, outgoing = false, outgoingRead = false) else it } }
            // Same notification path as the real account, so it can be tried offline.
            if (!Notifier.foreground) chats.value.firstOrNull { it.id == chatId }?.let { Notifier.post?.invoke(chatId, it.title, reply) }
        }
    }

    private fun updateChat(chatId: Long, text: String, time: String, read: Boolean) = chats.update { list ->
        // A chat from global search joins the list with its first message.
        val chat = (list.firstOrNull { it.id == chatId } ?: directory.firstOrNull { it.id == chatId }?.copy(isMember = true) ?: return@update list)
            .copy(lastMessage = text, time = time, outgoingRead = read)
        // Pinned chats stay on top, the updated one goes right below them.
        val (pinned, rest) = list.filter { it.id != chatId }.partition { it.pinned }
        if (chat.pinned) listOf(chat) + pinned + rest else pinned + chat + rest
    }

    override fun closeChat(chatId: Long) {}

    override fun editMessage(chatId: Long, messageId: Long, text: String) {
        messageFlows[chatId]?.update { list -> list.map { if (it.id == messageId) it.copy(text = text, edited = true) else it } }
    }

    override fun sendVoice(chatId: Long, path: String, durationSec: Int) {
        val now = LocalTime.now().format(timeFormat)
        flowOf(chatId).update { listOf(Message(nextId++, "", true, now, isVoice = true, voicePath = path, voiceDuration = durationSec)) + it }
        updateChat(chatId, "🎤 Voice message", now, read = false)
    }

    override fun sendTyping(chatId: Long) {}
    override fun loadMore(chatId: Long) {}

    override fun sendPhoto(chatId: Long, path: String) {
        val now = LocalTime.now().format(timeFormat)
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, opts)
        val aspect = if (opts.outHeight > 0) opts.outWidth.toFloat() / opts.outHeight else 1f
        flowOf(chatId).update { listOf(Message(nextId++, "", true, now, isPhoto = true, photoPath = path, photoAspect = aspect)) + it }
        updateChat(chatId, "🖼 Photo", now, read = false)
    }

    override fun deleteMessages(chatId: Long, ids: List<Long>) {
        messageFlows[chatId]?.update { list -> list.filter { it.id !in ids } }
    }

    override fun react(chatId: Long, messageId: Long, emoji: String) {
        messageFlows[chatId]?.update { list ->
            list.map { m ->
                if (m.id != messageId) return@map m
                val mine = m.reactions.firstOrNull { it.chosen }
                // One reaction per user, like Telegram without Premium: picking another moves ours.
                val rest = m.reactions.mapNotNull { r -> if (r.chosen) r.copy(count = r.count - 1, chosen = false).takeIf { it.count > 0 } else r }
                if (mine?.emoji == emoji) m.copy(reactions = rest)
                else m.copy(reactions = rest.firstOrNull { it.emoji == emoji }
                    ?.let { hit -> rest.map { if (it === hit) it.copy(count = it.count + 1, chosen = true) else it } }
                    ?: (rest + Reaction(emoji, 1, true)))
            }
        }
    }

    override fun forward(fromChatId: Long, messageIds: List<Long>, toChatId: Long) {
        val src = messageFlows[fromChatId]?.value.orEmpty().filter { it.id in messageIds }.reversed()
        val now = LocalTime.now().format(timeFormat)
        val from = chats.value.firstOrNull { it.id == fromChatId }?.title
        flowOf(toChatId).update { list -> src.map { it.copy(id = nextId++, out = true, time = now, read = false, reactions = emptyList(), pinned = false, forwardedFrom = it.forwardedFrom ?: if (it.out) "You" else from) }.reversed() + list }
        src.lastOrNull()?.let { updateChat(toChatId, it.text.ifBlank { "Forwarded message" }, now, read = false) }
    }

    override fun pinMessage(chatId: Long, messageId: Long, pinned: Boolean) {
        messageFlows[chatId]?.update { list -> list.map { if (it.id == messageId) it.copy(pinned = pinned) else it } }
    }

    override suspend fun editChat(chatId: Long, title: String, about: String): String? {
        chats.update { list -> list.map { if (it.id == chatId) it.copy(title = title.ifBlank { it.title }) else it } }
        aboutOf[chatId] = about
        return null
    }

    override fun setChatPhoto(chatId: Long, path: String) =
        chats.update { list -> list.map { if (it.id == chatId) it.copy(photoPath = path) else it } }

    private val aboutOf = mutableMapOf<Long, String>()

    override fun setMuted(chatId: Long, muted: Boolean) =
        chats.update { list -> list.map { if (it.id == chatId) it.copy(muted = muted) else it } }

    override fun deleteChat(chatId: Long) = chats.update { list -> list.filter { it.id != chatId } }
    override fun block(chatId: Long) {}

    override fun setPinned(chatId: Long, pinned: Boolean) = chats.update { list ->
        val updated = list.map { if (it.id == chatId) it.copy(pinned = pinned) else it }
        val (p, rest) = updated.partition { it.pinned }
        p + rest
    }

    override fun readChat(chatId: Long) = markRead(chatId)

    override fun markRead(chatId: Long) = chats.update { list -> list.map { if (it.id == chatId) it.copy(unread = 0) else it } }

    override suspend fun profile(chatId: Long): Profile {
        val chat = chats.value.firstOrNull { it.id == chatId } ?: Chat(chatId, "Chat", "", "", 0)
        return if (chatId == 7L) Profile(
            7, "Charlotte Harris", "last seen recently", "+375 33 123 45 67", "@charris", "1 Nov 2001 (23 years old)",
            "Wellness Kitchen", "2.7K subscribers", "🥗 New recipe is up! A super easy quinoa salad with fresh herbs", "Mon", colorIndex = chat.colorIndex,
        ) else if (chat.isGroup || chat.verified) Profile(
            chatId, chat.title, chat.status, "", if (chat.verified) "t.me/dailynews" else "", "", null, null, null, null,
            colorIndex = chat.colorIndex, kind = if (chat.isGroup) "group" else "channel", photoPath = chat.photoPath,
            about = aboutOf[chatId] ?: if (chat.isGroup) "Design team chat: mockups, reviews, standups" else "The day's top stories, every morning",
            // Demo: the group is ours (editable), the news channel isn't.
            canEdit = chat.isGroup,
        ) else Profile(
            chatId, chat.title, chat.status, "+1 555 01${chatId.toString().padStart(2, '0')} 4567",
            "@" + chat.title.lowercase().replace(" ", ""), "—", null, null, null, null, colorIndex = chat.colorIndex,
        )
    }

    // ---------------------------------------------------------------- own profile, sessions, new chats, stickers

    private var info = MyInfo("Alex", "Turner", "Designer. Coffee first ☕", "alexturner")
    override suspend fun myInfo() = info
    override suspend fun saveMyInfo(info: MyInfo): String? {
        this.info = info
        me.update { it.copy(name = "${info.firstName} ${info.lastName}".trim(), username = if (info.username.isBlank()) "" else "@${info.username}") }
        return null
    }
    override fun setProfilePhoto(path: String) = me.update { it.copy(photoPath = path) }

    private val demoSessions = MutableStateFlow(listOf(
        Session(1, "Pixel 7", "LiquidGram 1.0", "Minsk, Belarus", "online", current = true),
        Session(2, "MacBook Pro", "Telegram macOS 11.4", "Minsk, Belarus", "Yesterday", current = false),
        Session(3, "iPhone 16 Pro", "Telegram iOS 11.5", "Warsaw, Poland", "Mon", current = false),
    ))
    override suspend fun sessions() = demoSessions.value
    override suspend fun terminateSession(id: Long) = demoSessions.update { l -> l.filter { it.id != id } }
    override suspend fun terminateOtherSessions() = demoSessions.update { l -> l.filter { it.current } }

    override suspend fun contacts() = chats.value.filter { !it.isSaved && !it.isGroup && !it.verified }
        .map { Contact(it.id, it.title, it.status, it.colorIndex, it.photoPath) }.sortedBy { it.name }

    private fun newChat(title: String, status: String, group: Boolean, first: String): Long {
        val id = nextId++
        val now = LocalTime.now().format(timeFormat)
        val (pinned, rest) = chats.value.partition { it.pinned }
        chats.value = pinned + Chat(id, title, first, now, (id % 7).toInt(), status = status, isGroup = group) + rest
        messageFlows[id] = MutableStateFlow(emptyList())
        return id
    }

    override suspend fun createGroup(title: String, userIds: List<Long>) = Result.success(newChat(title, "${userIds.size + 1} members", true, "Group created"))
    override suspend fun createChannel(title: String, description: String) = Result.success(newChat(title, "1 subscriber", false, "Channel created"))
    override suspend fun addContact(firstName: String, lastName: String, phone: String) =
        Result.success(newChat("$firstName $lastName".trim(), "last seen recently", false, ""))
    override suspend fun createSecretChat(userId: Long): Result<Long> {
        val name = chats.value.firstOrNull { it.id == userId }?.title ?: "Secret chat"
        return Result.success(newChat("🔒 $name", "secret chat", false, "Secret chat started"))
    }

    override suspend fun stickerSets() = emptyList<StickerSet>()
    override suspend fun stickerFile(sticker: Sticker) = sticker.path

    override fun sendSticker(chatId: Long, sticker: Sticker) {
        val now = LocalTime.now().format(timeFormat)
        flowOf(chatId).update {
            listOf(Message(nextId++, sticker.emoji, true, now, isSticker = true, stickerPath = sticker.path, stickerKind = sticker.kind)) + it
        }
        updateChat(chatId, sticker.emoji + " Sticker", now, read = false)
    }

    // ---------------------------------------------------------------- settings, files, search, round videos

    private val scopes = mutableMapOf("private" to NotifyScope("private", true, true), "group" to NotifyScope("group", true, true), "channel" to NotifyScope("channel", false, true))
    override suspend fun notifyScopes() = scopes.values.toList()
    override suspend fun setNotifyScope(scope: NotifyScope) { scopes[scope.key] = scope }
    private val privacyValues = mutableMapOf("phone" to "contacts", "lastSeen" to "everybody", "photo" to "everybody", "forwards" to "everybody", "calls" to "everybody", "invites" to "contacts")
    override suspend fun privacy(key: String) = privacyValues[key] ?: "everybody"
    override suspend fun setPrivacy(key: String, value: String) { privacyValues[key] = value }
    override suspend fun blockedCount() = 2
    override suspend fun hasPassword() = true
    private var cache = 48_300_000L
    override suspend fun storageUsage() = cache
    override suspend fun clearCache() { cache = 1_200_000L }

    override fun download(chatId: Long, messageId: Long) {}

    override fun sendDocument(chatId: Long, path: String) {
        val now = LocalTime.now().format(timeFormat)
        val f = java.io.File(path)
        flowOf(chatId).update {
            listOf(Message(nextId++, "", true, now, isFile = true, fileName = f.name.substringAfter("_"), fileSize = "${(f.length() / 1024).coerceAtLeast(1)} KB", filePath = path)) + it
        }
        updateChat(chatId, "📎 " + f.name.substringAfter("_"), now, read = false)
    }

    override fun sendVideo(chatId: Long, path: String) {
        val now = LocalTime.now().format(timeFormat)
        val (w, h, sec) = videoInfo(path)
        flowOf(chatId).update {
            listOf(Message(nextId++, "", true, now, isVideo = true, videoPath = path, videoDuration = sec, photoAspect = if (w > 0 && h > 0) w.toFloat() / h else 1f)) + it
        }
        updateChat(chatId, "📹 Video", now, read = false)
    }

    override fun sendVideoNote(chatId: Long, path: String, durationSec: Int) {
        val now = LocalTime.now().format(timeFormat)
        flowOf(chatId).update { listOf(Message(nextId++, "", true, now, isRound = true, roundPath = path, roundDuration = durationSec)) + it }
        updateChat(chatId, "📹 Video message", now, read = false)
    }

    override suspend fun searchMessages(chatId: Long, query: String) =
        messages(chatId).value.filter { it.text.contains(query, ignoreCase = true) }

    override suspend fun sharedMedia(chatId: Long, kind: String) = messages(chatId).value.filter {
        when (kind) { "media" -> it.isPhoto || it.isVideo; "files" -> it.isFile; else -> it.text.contains("http") || it.text.contains("www.") }
    }

    override fun loadAround(chatId: Long, messageId: Long) {}

    // Demo calls: requesting → ringing → active on a timer, as a real call would go.
    override val activeCall = MutableStateFlow<ActiveCall?>(null)
    private var nextCallId = 1
    override fun startCall(chatId: Long, video: Boolean) {
        val id = nextCallId++
        activeCall.value = ActiveCall(id, chatId, outgoing = true, video = video, phase = CallPhase.Requesting)
        scope.launch {
            kotlinx.coroutines.delay(800)
            activeCall.update { c -> c?.takeIf { it.id == id && !it.over }?.copy(phase = CallPhase.Ringing) ?: c }
            kotlinx.coroutines.delay(2500)
            activeCall.update { c -> c?.takeIf { it.id == id && !it.over }?.copy(phase = CallPhase.Active, connectedAt = System.currentTimeMillis()) ?: c }
        }
    }
    override fun acceptCall() {
        activeCall.update { it?.copy(phase = CallPhase.Active, connectedAt = System.currentTimeMillis()) }
    }
    override fun endCall() { activeCall.update { it?.copy(phase = CallPhase.Ended) } }
    override fun dismissCall() { activeCall.value = null }
}
