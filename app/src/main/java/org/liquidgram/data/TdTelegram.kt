package org.liquidgram.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Real Telegram through TDLib. TDLib pushes updates on its own thread; we keep the latest
 * objects in maps and republish UI models as StateFlows.
 */
class TdTelegram(context: Context, private val apiId: Int, private val apiHash: String) : TelegramRepository {
    private val dir = context.filesDir.resolve("tdlib").absolutePath
    private val appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"

    override val auth = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authError = MutableStateFlow<String?>(null)
    override val authBusy = MutableStateFlow(false)
    override val connection = MutableStateFlow<String?>("Connecting…")
    override val chats = MutableStateFlow<List<Chat>>(emptyList())
    override val stories = MutableStateFlow<List<Story>>(emptyList())
    override val calls = MutableStateFlow<List<Call>>(emptyList())
    override val me = MutableStateFlow(Profile(0, "", "online", "", "", "", null, null, null, null))
    override val folders = MutableStateFlow(listOf("All Chats"))

    private val tdChats = ConcurrentHashMap<Long, TdApi.Chat>()
    private val users = ConcurrentHashMap<Long, TdApi.User>()
    private val files = ConcurrentHashMap<Int, TdApi.File>()
    private val activeStories = ConcurrentHashMap<Long, TdApi.ChatActiveStories>()
    private val folderNames = ConcurrentHashMap<Int, String>()
    private var folderOrder = listOf<Int>()
    private val messageFlows = ConcurrentHashMap<Long, MutableStateFlow<List<Message>>>()
    private val typingFlows = ConcurrentHashMap<Long, MutableStateFlow<Boolean>>()
    private var myId = 0L

    @Volatile private lateinit var client: Client

    init {
        // TDLib's own log goes to a file (it doesn't reach logcat); 2 = warnings, enough to see network trouble.
        java.io.File(dir).mkdirs()
        Client.execute(TdApi.SetLogStream(TdApi.LogStreamFile("$dir/tdlib.log", 4L shl 20, false)))
        Client.execute(TdApi.SetLogVerbosityLevel(2))
    }

    private fun send(f: TdApi.Function<*>, onResult: (TdApi.Object) -> Unit = {}) = client.send(f) { onResult(it) }

    private suspend fun <T : TdApi.Object> call(f: TdApi.Function<T>): TdApi.Object =
        suspendCancellableCoroutine { c -> client.send(f) { c.resume(it) } }

    // ---------------------------------------------------------------- auth

    private fun onAuthState(s: TdApi.AuthorizationState) {
        when (s) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> send(TdApi.SetTdlibParameters().apply {
                databaseDirectory = dir
                filesDirectory = "$dir/files"
                useFileDatabase = true
                useChatInfoDatabase = true
                useMessageDatabase = true
                useSecretChats = true
                apiId = this@TdTelegram.apiId
                apiHash = this@TdTelegram.apiHash
                systemLanguageCode = Locale.getDefault().language
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                systemVersion = "Android ${Build.VERSION.RELEASE}"
                applicationVersion = appVersion
            }, ::reportError)
            is TdApi.AuthorizationStateWaitPhoneNumber -> auth.value = AuthState.WaitPhone
            is TdApi.AuthorizationStateWaitCode -> auth.value = AuthState.WaitCode(
                length = when (val t = s.codeInfo.type) {
                    is TdApi.AuthenticationCodeTypeTelegramMessage -> t.length
                    is TdApi.AuthenticationCodeTypeSms -> t.length
                    is TdApi.AuthenticationCodeTypeCall -> t.length
                    is TdApi.AuthenticationCodeTypeFragment -> t.length
                    else -> 5
                },
                sentTo = when (s.codeInfo.type) {
                    is TdApi.AuthenticationCodeTypeTelegramMessage -> "We've sent the code to the Telegram app on your other device."
                    is TdApi.AuthenticationCodeTypeSms -> "We've sent you an SMS with the code to ${s.codeInfo.phoneNumber}."
                    else -> "Enter the code sent to ${s.codeInfo.phoneNumber}."
                }
            )
            is TdApi.AuthorizationStateWaitPassword -> auth.value = AuthState.WaitPassword(s.passwordHint)
            is TdApi.AuthorizationStateWaitRegistration -> auth.value = AuthState.Unsupported("This number has no Telegram account yet. Sign up in the official app first.")
            is TdApi.AuthorizationStateWaitEmailAddress, is TdApi.AuthorizationStateWaitEmailCode ->
                auth.value = AuthState.Unsupported("Telegram asks for e-mail login for this account. Log in once in the official app, then try again.")
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> auth.value = AuthState.Unsupported("Confirm the login on your other device: ${s.link}")
            is TdApi.AuthorizationStateReady -> {
                auth.value = AuthState.Ready
                send(TdApi.GetMe()) { if (it is TdApi.User) { myId = it.id; users[it.id] = it; publishMe() } }
                send(TdApi.LoadChats(TdApi.ChatListMain(), 100))
                loadCalls()
            }
            is TdApi.AuthorizationStateLoggingOut, is TdApi.AuthorizationStateClosing -> auth.value = AuthState.Loading
            is TdApi.AuthorizationStateClosed -> {
                // After log-out TDLib closes this instance; start a fresh one, which leads back to the login screen.
                tdChats.clear(); users.clear(); raw.clear(); messageFlows.clear(); activeStories.clear()
                historyDone.clear(); historyLoading.clear()
                chats.value = emptyList(); stories.value = emptyList(); calls.value = emptyList()
                client = Client.create({ onUpdate(it) }, null, null)
            }
            else -> Unit
        }
    }

    override fun submitAuth(input: String) {
        // One request at a time: a second tap while TDLib is still working gets "Another authorization query has
        // started" back, even though the first request goes through.
        if (authBusy.value) return
        authError.value = null
        val f: TdApi.Function<*> = when (auth.value) {
            AuthState.WaitPhone -> TdApi.SetAuthenticationPhoneNumber(input.filter { it.isDigit() || it == '+' }, null)
            is AuthState.WaitCode -> TdApi.CheckAuthenticationCode(input.trim())
            is AuthState.WaitPassword -> TdApi.CheckAuthenticationPassword(input)
            else -> return
        }
        authBusy.value = true
        send(f) { authBusy.value = false; reportError(it) }
    }

    override fun logOut() = send(TdApi.LogOut()).let { }

    private fun reportError(o: TdApi.Object) {
        if (o is TdApi.Error && !o.message.contains("Another authorization query", ignoreCase = true)) {
            authError.value = o.message.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    // ---------------------------------------------------------------- updates

    private fun onUpdate(o: TdApi.Object) {
        when (o) {
            is TdApi.UpdateAuthorizationState -> onAuthState(o.authorizationState)
            is TdApi.UpdateConnectionState -> connection.value = when (o.state) {
                is TdApi.ConnectionStateReady -> null
                is TdApi.ConnectionStateWaitingForNetwork -> "Waiting for network…"
                is TdApi.ConnectionStateConnectingToProxy -> "Connecting to proxy…"
                is TdApi.ConnectionStateConnecting -> "Connecting to Telegram…"
                else -> "Updating…"
            }
            is TdApi.UpdateCall -> onCall(o.call)
            is TdApi.UpdateUser -> { users[o.user.id] = o.user; if (o.user.id == myId) publishMe(); publishChats() }
            is TdApi.UpdateUserStatus -> users[o.userId]?.let { it.status = o.status; publishChats() }
            is TdApi.UpdateNewChat -> { tdChats[o.chat.id] = o.chat; publishChats() }
            is TdApi.UpdateChatTitle -> chat(o.chatId) { title = o.title }
            is TdApi.UpdateChatPhoto -> chat(o.chatId) { photo = o.photo }
            is TdApi.UpdateChatLastMessage -> chat(o.chatId) { lastMessage = o.lastMessage; o.positions.forEach { setPosition(this, it) } }
            is TdApi.UpdateChatPosition -> chat(o.chatId) { setPosition(this, o.position) }
            is TdApi.UpdateChatReadInbox -> chat(o.chatId) { unreadCount = o.unreadCount; lastReadInboxMessageId = o.lastReadInboxMessageId }
            is TdApi.UpdateChatReadOutbox -> {
                chat(o.chatId) { lastReadOutboxMessageId = o.lastReadOutboxMessageId }
                publishMessages(o.chatId)
            }
            is TdApi.UpdateChatNotificationSettings -> chat(o.chatId) { notificationSettings = o.notificationSettings }
            is TdApi.UpdateFile -> {
                files[o.file.id] = o.file
                if (o.file.local.isDownloadingCompleted) {
                    fileChats[o.file.id]?.let(::publishMessages) ?: run { publishChats(); publishStories(); publishMe() }
                } else if (o.file.local.downloadedSize == 0L && o.file.local.isDownloadingActive) {
                    fileChats[o.file.id]?.let(::publishMessages)
                }
            }
            is TdApi.UpdateChatFolders -> {
                folderOrder = o.chatFolders.map { it.id }
                folderNames.clear()
                o.chatFolders.forEach { folderNames[it.id] = it.name.text.text }
                folders.value = listOf("All Chats") + folderOrder.mapNotNull { folderNames[it] }
                publishChats()
            }
            is TdApi.UpdateChatActiveStories -> {
                if (o.activeStories.list == null || o.activeStories.stories.isEmpty()) activeStories.remove(o.activeStories.chatId)
                else activeStories[o.activeStories.chatId] = o.activeStories
                publishStories()
            }
            is TdApi.UpdateNewMessage -> { addMessage(o.message); notifyIncoming(o.message) }
            is TdApi.UpdateMessageSendSucceeded -> raw[o.message.chatId]?.let { m ->
                m.remove(o.oldMessageId); m[o.message.id] = o.message; publishMessages(o.message.chatId)
            }
            is TdApi.UpdateMessageSendFailed -> raw[o.message.chatId]?.let { m ->
                m.remove(o.oldMessageId); publishMessages(o.message.chatId)
                authError.value = o.error.message
            }
            is TdApi.UpdateDeleteMessages -> if (o.isPermanent) raw[o.chatId]?.let { m -> o.messageIds.forEach { m.remove(it) }; publishMessages(o.chatId) }
            is TdApi.UpdateMessageContent -> raw[o.chatId]?.get(o.messageId)?.let { it.content = o.newContent; publishMessages(o.chatId) }
            is TdApi.UpdateMessageEdited -> raw[o.chatId]?.get(o.messageId)?.let { it.editDate = o.editDate; it.replyMarkup = o.replyMarkup; publishMessages(o.chatId) }
            is TdApi.UpdateMessageInteractionInfo -> raw[o.chatId]?.get(o.messageId)?.let { it.interactionInfo = o.interactionInfo; publishMessages(o.chatId) }
            is TdApi.UpdateMessageIsPinned -> raw[o.chatId]?.get(o.messageId)?.let { it.isPinned = o.isPinned; publishMessages(o.chatId) }
            is TdApi.UpdateChatAction -> typingFlows[o.chatId]?.value = o.action is TdApi.ChatActionTyping
            else -> Unit
        }
    }

    private inline fun chat(id: Long, block: TdApi.Chat.() -> Unit) {
        tdChats[id]?.let { synchronized(it) { it.block() }; publishChats() }
    }

    private fun setPosition(c: TdApi.Chat, p: TdApi.ChatPosition) {
        val rest = c.positions.filterNot { sameList(it.list, p.list) }
        c.positions = (if (p.order == 0L) rest else rest + p).toTypedArray()
    }

    private fun sameList(a: TdApi.ChatList, b: TdApi.ChatList) = when {
        a is TdApi.ChatListMain && b is TdApi.ChatListMain -> true
        a is TdApi.ChatListArchive && b is TdApi.ChatListArchive -> true
        a is TdApi.ChatListFolder && b is TdApi.ChatListFolder -> a.chatFolderId == b.chatFolderId
        else -> false
    }

    // ---------------------------------------------------------------- mapping

    private fun publishChats() {
        chats.value = tdChats.values
            .mapNotNull { c -> c.positions.firstOrNull { it.list is TdApi.ChatListMain }?.let { c to it } }
            .sortedByDescending { it.second.order }
            .map { (c, pos) -> toChat(c, pos.isPinned) }
    }

    private fun toChat(c: TdApi.Chat, pinned: Boolean): Chat {
        val m = c.lastMessage
        val user = (c.type as? TdApi.ChatTypePrivate)?.let { users[it.userId] }
        val sender = (m?.senderId as? TdApi.MessageSenderUser)?.let { users[it.userId] }
        val prefix = if (m != null && !m.isOutgoing && c.type !is TdApi.ChatTypePrivate && sender != null) "${sender.firstName}: " else ""
        return Chat(
            id = c.id,
            title = if (c.id == myId) "Saved Messages" else c.title,
            lastMessage = m?.let { prefix + preview(it.content) } ?: "",
            time = m?.let { formatDate(it.date) } ?: "",
            colorIndex = colorIndex(c.id),
            unread = c.unreadCount,
            muted = c.notificationSettings.muteFor > 0,
            pinned = pinned,
            outgoing = m?.isOutgoing == true,
            outgoingRead = m != null && m.isOutgoing && m.id <= c.lastReadOutboxMessageId,
            isSaved = c.id == myId,
            status = user?.let { status(it) } ?: when (val t = c.type) {
                is TdApi.ChatTypeSupergroup -> if (t.isChannel) "channel" else "group"
                is TdApi.ChatTypeBasicGroup -> "group"
                else -> ""
            },
            folders = c.positions.mapNotNull { (it.list as? TdApi.ChatListFolder)?.let { f -> folderNames[f.chatFolderId] } }.toSet(),
            photoPath = c.photo?.small?.let(::path),
            isGroup = c.type is TdApi.ChatTypeBasicGroup || (c.type as? TdApi.ChatTypeSupergroup)?.isChannel == false,
            lastReadInboxId = c.lastReadInboxMessageId,
        )
    }

    // ---------------------------------------------------------------- global search

    /** In the user's chat list (main or archive) = joined; public channels found by search aren't. */
    private fun joined(c: TdApi.Chat) = c.positions.any { it.list is TdApi.ChatListMain || it.list is TdApi.ChatListArchive } ||
        c.type is TdApi.ChatTypePrivate || c.type is TdApi.ChatTypeSecret

    private fun count(n: Int, one: String) = (if (n >= 1_000_000) "%.1fM".format(n / 1e6) else if (n >= 1000) "%.1fK".format(n / 1e3) else "$n") + " " + one

    override suspend fun searchGlobal(query: String): List<Chat> {
        val q = query.trim().removePrefix("@")
        if (q.length < 2) return emptyList()
        // Server matches among the user's own chats first, then public chats by username/title (not joined ones too).
        val own = (call(TdApi.SearchChatsOnServer(q, null, 20)) as? TdApi.Chats)?.chatIds ?: LongArray(0)
        val public = (call(TdApi.SearchPublicChats(q, null)) as? TdApi.Chats)?.chatIds ?: LongArray(0)
        return (own + public).distinct().mapNotNull { id ->
            val c = tdChats[id] ?: (call(TdApi.GetChat(id)) as? TdApi.Chat)?.also { tdChats[id] = it } ?: return@mapNotNull null
            describe(c)
        }
    }

    /** A chat for search results and for opening it outside the list: @username and how big it is as the status line. */
    private suspend fun describe(c: TdApi.Chat): Chat {
        val base = toChat(c, false).copy(isMember = joined(c))
        val status = when (val t = c.type) {
            is TdApi.ChatTypeSupergroup -> (call(TdApi.GetSupergroup(t.supergroupId)) as? TdApi.Supergroup)?.let { sg ->
                listOfNotNull(sg.usernames?.activeUsernames?.firstOrNull()?.let { "@$it" }, count(sg.memberCount, if (t.isChannel) "subscribers" else "members").takeIf { sg.memberCount > 0 })
                    .joinToString(" · ")
            }
            is TdApi.ChatTypePrivate -> users[t.userId]?.let { u ->
                listOfNotNull(u.usernames?.activeUsernames?.firstOrNull()?.let { "@$it" }, "bot".takeIf { u.type is TdApi.UserTypeBot }).joinToString(" · ")
            }
            else -> null
        }
        return base.copy(status = status?.takeIf { it.isNotBlank() } ?: base.status)
    }

    override fun chatInfo(chatId: Long): Chat? = tdChats[chatId]?.let { toChat(it, false).copy(isMember = joined(it)) }

    override fun joinChat(chatId: Long) {
        send(TdApi.JoinChat(chatId)) { if (it is TdApi.Error) authError.value = it.message }
    }

    /** Local path of a file, starting a download if we don't have it yet. */
    private fun path(f: TdApi.File): String? = downloadPath(f, 1)

    private fun downloadPath(f: TdApi.File, priority: Int): String? {
        val file = files[f.id] ?: f.also { files[it.id] = it }
        if (file.local.isDownloadingCompleted) return file.local.path
        if (file.local.canBeDownloaded && !file.local.isDownloadingActive) send(TdApi.DownloadFile(file.id, priority, 0, 0, false))
        return null
    }

    private fun preview(c: TdApi.MessageContent): String = when (c) {
        is TdApi.MessageText -> c.text.text
        is TdApi.MessagePhoto -> "🖼 " + c.caption.text.ifEmpty { "Photo" }
        is TdApi.MessageVideo -> "📹 " + c.caption.text.ifEmpty { "Video" }
        is TdApi.MessageAnimation -> "GIF"
        is TdApi.MessageSticker -> c.sticker.emoji + " Sticker"
        is TdApi.MessageAnimatedEmoji -> c.emoji
        is TdApi.MessageVoiceNote -> "🎤 Voice message"
        is TdApi.MessageVideoNote -> "📹 Video message"
        is TdApi.MessageAudio -> "🎵 " + c.audio.title.ifEmpty { "Audio" }
        is TdApi.MessageDocument -> "📎 " + c.document.fileName
        is TdApi.MessageLocation -> "📍 Location"
        is TdApi.MessageContact -> "👤 Contact"
        is TdApi.MessagePoll -> "📊 Poll"
        is TdApi.MessageCall -> "📞 Call"
        is TdApi.MessageChatAddMembers -> "joined the group"
        is TdApi.MessagePinMessage -> "pinned a message"
        else -> "Message"
    }

    private fun toMessage(m: TdApi.Message): Message {
        val c = m.content
        val emoji = (c as? TdApi.MessageSticker)?.sticker?.emoji ?: (c as? TdApi.MessageAnimatedEmoji)?.emoji
        val readOut = tdChats[m.chatId]?.lastReadOutboxMessageId ?: 0
        val photo = (c as? TdApi.MessagePhoto)?.photo?.sizes?.let { sizes -> sizes.filter { it.width <= 1280 }.maxByOrNull { it.width } ?: sizes.lastOrNull() }
        if (photo != null) fileChats[photo.photo.id] = m.chatId
        val voice = (c as? TdApi.MessageVoiceNote)?.voiceNote
        if (voice != null) fileChats[voice.voice.id] = m.chatId
        val sticker = (c as? TdApi.MessageSticker)?.sticker
        if (sticker != null) fileChats[sticker.sticker.id] = m.chatId
        val doc = (c as? TdApi.MessageDocument)?.document
        if (doc != null) fileChats[doc.document.id] = m.chatId
        val note = (c as? TdApi.MessageVideoNote)?.videoNote
        if (note != null) fileChats[note.video.id] = m.chatId
        // Video and GIF share one look: file, preview, size, length.
        val video = (c as? TdApi.MessageVideo)?.video
        val gif = (c as? TdApi.MessageAnimation)?.animation
        val clip = video?.video ?: gif?.animation
        val clipThumb = (video?.thumbnail ?: gif?.thumbnail)?.file
        clip?.let { fileChats[it.id] = m.chatId }
        clipThumb?.let { fileChats[it.id] = m.chatId }
        val clipW = video?.width ?: gif?.width ?: 0
        val clipH = video?.height ?: gif?.height ?: 0
        val chat = tdChats[m.chatId]
        val inGroup = chat?.type.let { it is TdApi.ChatTypeBasicGroup || (it as? TdApi.ChatTypeSupergroup)?.isChannel == false }
        val sender = if (inGroup && !m.isOutgoing) senderOf(m) else null
        val reply = (m.replyTo as? TdApi.MessageReplyToMessage)?.let { r -> replyTarget(r.chatId, r.messageId, m.chatId) }
        return Message(
            m.id,
            when {
                emoji != null -> emoji
                photo != null -> (c as TdApi.MessagePhoto).caption.text
                doc != null -> (c as TdApi.MessageDocument).caption.text
                note != null -> ""
                video != null -> (c as TdApi.MessageVideo).caption.text
                gif != null -> (c as TdApi.MessageAnimation).caption.text
                else -> preview(c)
            },
            m.isOutgoing, formatTime(m.date),
            isSticker = emoji != null,
            read = !m.isOutgoing || m.id <= readOut,
            isPhoto = photo != null,
            photoPath = photo?.photo?.let { if (MediaPrefs.autoPhotos) downloadPath(it, 16) else readyPath(it) },
            photoAspect = photo?.let { if (it.height > 0) it.width.toFloat() / it.height else 1f }
                ?: if (clipW > 0 && clipH > 0) clipW.toFloat() / clipH else 1f,
            isVideo = clip != null,
            isGif = gif != null,
            // GIFs are small and loop in place, so they load at once; videos load on tap (or with auto-download of photos).
            videoPath = clip?.let { if (gif != null || MediaPrefs.autoPhotos && it.size in 1..(10L shl 20)) downloadPath(it, 8) else readyPath(it) },
            videoThumb = clipThumb?.let { downloadPath(it, 16) },
            videoDuration = video?.duration ?: gif?.duration ?: 0,
            senderName = sender?.first,
            senderColor = sender?.second ?: 0,
            senderPhoto = sender?.third,
            replyName = reply?.let { if (it.isOutgoing) "You" else senderOf(it).first },
            replyText = reply?.let { preview(it.content) },
            edited = m.editDate > 0,
            canEdit = m.isOutgoing && c is TdApi.MessageText,
            isVoice = voice != null,
            voicePath = voice?.voice?.let { downloadPath(it, 16) },
            voiceDuration = voice?.duration ?: 0,
            stickerPath = sticker?.sticker?.let { downloadPath(it, 8) },
            stickerKind = sticker?.let { kindOf(it.format) } ?: StickerKind.Static,
            isFile = doc != null,
            fileName = doc?.fileName.orEmpty(),
            fileSize = doc?.document?.size?.let(::formatSize).orEmpty(),
            filePath = doc?.document?.let { if (MediaPrefs.autoFiles) downloadPath(it, 8) else readyPath(it) },
            fileLoading = (doc?.document ?: clip)?.let { files[it.id]?.local?.isDownloadingActive } == true,
            isRound = note != null,
            roundPath = note?.video?.let { downloadPath(it, 16) },
            roundDuration = note?.duration ?: 0,
            buttons = inlineButtons(m),
            keyboard = replyKeyboard(m),
            keyboardOneTime = (m.replyMarkup as? TdApi.ReplyMarkupShowKeyboard)?.oneTime == true,
            keyboardPlaceholder = (m.replyMarkup as? TdApi.ReplyMarkupShowKeyboard)?.inputFieldPlaceholder.orEmpty(),
            // shortcut: custom-emoji and paid reactions are skipped; draw them once custom emoji rendering exists.
            reactions = m.interactionInfo?.reactions?.reactions.orEmpty().mapNotNull { r ->
                (r.type as? TdApi.ReactionTypeEmoji)?.let { Reaction(it.emoji, r.totalCount, r.isChosen) }
            },
            pinned = m.isPinned,
            forwardedFrom = when (val o = m.forwardInfo?.origin) {
                is TdApi.MessageOriginUser -> users[o.senderUserId]?.let { "${it.firstName} ${it.lastName}".trim() } ?: "User"
                is TdApi.MessageOriginHiddenUser -> o.senderName
                is TdApi.MessageOriginChat -> tdChats[o.senderChatId]?.title ?: "Chat"
                is TdApi.MessageOriginChannel -> tdChats[o.chatId]?.title ?: "Channel"
                else -> null
            },
        )
    }

    /** Local path only if the file is already here (no download started). */
    private fun readyPath(f: TdApi.File): String? = (files[f.id] ?: f).local.takeIf { it.isDownloadingCompleted }?.path

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toFloat())
        bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toFloat())
        bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toFloat())
        else -> "$bytes B"
    }

    private fun kindOf(f: TdApi.StickerFormat) = when (f) {
        is TdApi.StickerFormatTgs -> StickerKind.Lottie
        is TdApi.StickerFormatWebm -> StickerKind.Video
        else -> StickerKind.Static
    }

    /** Name, colour index and small photo of whoever sent [m]. */
    private fun senderOf(m: TdApi.Message): Triple<String, Int, String?> = when (val s = m.senderId) {
        is TdApi.MessageSenderUser -> users[s.userId].let { u ->
            Triple(u?.let { "${it.firstName} ${it.lastName}".trim() } ?: "User", colorIndex(s.userId), u?.profilePhoto?.small?.let(::path))
        }
        is TdApi.MessageSenderChat -> tdChats[s.chatId].let { ch -> Triple(ch?.title ?: "Channel", colorIndex(s.chatId), ch?.photo?.small?.let(::path)) }
        else -> Triple("", 0, null)
    }

    /** Replied-to messages that aren't loaded: fetched once, then the chat is redrawn with the quote. */
    private val replyCache = ConcurrentHashMap<Pair<Long, Long>, TdApi.Message>()
    private val replyRequested = ConcurrentHashMap.newKeySet<Pair<Long, Long>>()

    private fun replyTarget(chatId: Long, messageId: Long, shownIn: Long): TdApi.Message? {
        raw[chatId]?.get(messageId)?.let { return it }
        val key = chatId to messageId
        replyCache[key]?.let { return it }
        if (replyRequested.add(key)) send(TdApi.GetMessage(chatId, messageId)) { r ->
            if (r is TdApi.Message) { replyCache[key] = r; publishMessages(shownIn) }
        }
        return null
    }

    private fun status(u: TdApi.User): String = when (val s = u.status) {
        is TdApi.UserStatusOnline -> "online"
        is TdApi.UserStatusOffline -> "last seen " + formatDate(s.wasOnline).let { if (it.contains(':')) "at $it" else it }
        is TdApi.UserStatusRecently -> "last seen recently"
        is TdApi.UserStatusLastWeek -> "last seen within a week"
        is TdApi.UserStatusLastMonth -> "last seen within a month"
        else -> if (u.type is TdApi.UserTypeBot) "bot" else "last seen a long time ago"
    }

    private fun publishMe() {
        val u = users[myId] ?: return
        me.value = Profile(
            myId, "${u.firstName} ${u.lastName}".trim(), "online", "+" + u.phoneNumber,
            u.usernames?.activeUsernames?.firstOrNull()?.let { "@$it" } ?: "", "", null, null, null, null,
            photoPath = u.profilePhoto?.small?.let(::path), colorIndex = colorIndex(myId),
        )
    }

    private fun publishStories() {
        stories.value = listOf(Story(myId, "My Story", colorIndex(myId), seen = true, isMine = true, photoPath = me.value.photoPath)) +
            activeStories.values.filter { it.chatId != myId }.sortedByDescending { it.order }.mapNotNull { s ->
                val c = tdChats[s.chatId] ?: return@mapNotNull null
                Story(c.id, c.title, colorIndex(c.id), seen = s.maxReadStoryId >= (s.stories.maxOfOrNull { it.storyId } ?: 0), photoPath = c.photo?.small?.let(::path))
            }
    }

    private fun loadCalls() = send(TdApi.SearchCallMessages("", 50, false)) { r ->
        if (r !is TdApi.FoundMessages) return@send
        calls.value = r.messages.mapNotNull { m ->
            val call = m.content as? TdApi.MessageCall ?: return@mapNotNull null
            val c = tdChats[m.chatId]
            Call(
                m.chatId, c?.title ?: "Unknown", colorIndex(m.chatId), formatDate(m.date), m.isOutgoing,
                missed = call.discardReason is TdApi.CallDiscardReasonMissed || call.discardReason is TdApi.CallDiscardReasonDeclined,
                video = call.isVideo, photoPath = c?.photo?.small?.let(::path),
            )
        }
    }

    // ---------------------------------------------------------------- messages

    /** chatId → messageId → latest TDLib message; UI lists are rebuilt from here. */
    private val raw = ConcurrentHashMap<Long, ConcurrentHashMap<Long, TdApi.Message>>()
    /** Photo file id → chat that shows it, so a finished download redraws that chat. */
    private val fileChats = ConcurrentHashMap<Int, Long>()
    /** Custom-emoji icons of bot buttons, fetched once per id. */
    private val emojiIcons = ConcurrentHashMap<Long, TdApi.Sticker>()
    private val emojiIconsAsked = ConcurrentHashMap.newKeySet<Long>()

    private fun buttonIcon(id: Long, chatId: Long): Pair<String?, StickerKind> {
        if (id == 0L) return null to StickerKind.Static
        val s = emojiIcons[id]
        if (s == null) {
            if (emojiIconsAsked.add(id)) send(TdApi.GetCustomEmojiStickers(longArrayOf(id))) { r ->
                (r as? TdApi.Stickers)?.stickers?.firstOrNull()?.let { emojiIcons[id] = it; publishMessages(chatId) }
            }
            return null to StickerKind.Static
        }
        fileChats[s.sticker.id] = chatId
        return downloadPath(s.sticker, 8) to kindOf(s.format)
    }

    /** A fresh incoming message in an unmuted chat becomes a notification while the app is in the background. */
    private fun notifyIncoming(m: TdApi.Message) {
        if (m.isOutgoing || Notifier.foreground || System.currentTimeMillis() / 1000 - m.date > 120) return
        val c = tdChats[m.chatId] ?: return
        if (c.notificationSettings.muteFor > 0) return
        val inGroup = c.type is TdApi.ChatTypeBasicGroup || (c.type as? TdApi.ChatTypeSupergroup)?.isChannel == false
        val text = preview(m.content)
        Notifier.post?.invoke(m.chatId, c.title, if (inGroup) senderOf(m).first + ": " + text else text)
    }

    private fun styleOf(s: TdApi.ButtonStyle?) = when (s) {
        is TdApi.ButtonStylePrimary -> "primary"
        is TdApi.ButtonStyleDanger -> "danger"
        is TdApi.ButtonStyleSuccess -> "success"
        else -> ""
    }

    private fun inlineButtons(m: TdApi.Message): List<List<BotButton>> =
        (m.replyMarkup as? TdApi.ReplyMarkupInlineKeyboard)?.rows?.map { row ->
            row.map { b ->
                val (icon, kind) = buttonIcon(b.iconCustomEmojiId, m.chatId)
                val t = b.type
                BotButton(
                    b.text,
                    when (t) { is TdApi.InlineKeyboardButtonTypeCallback -> "callback"; is TdApi.InlineKeyboardButtonTypeUrl -> "url"; else -> "other" },
                    url = (t as? TdApi.InlineKeyboardButtonTypeUrl)?.url.orEmpty(),
                    data = (t as? TdApi.InlineKeyboardButtonTypeCallback)?.data,
                    style = styleOf(b.style), iconPath = icon, iconKind = kind,
                )
            }
        }.orEmpty()

    private fun replyKeyboard(m: TdApi.Message): List<List<BotButton>>? = when (val k = m.replyMarkup) {
        is TdApi.ReplyMarkupShowKeyboard -> k.rows.map { row ->
            row.map { b ->
                val (icon, kind) = buttonIcon(b.iconCustomEmojiId, m.chatId)
                BotButton(b.text, if (b.type is TdApi.KeyboardButtonTypeText) "text" else "other", style = styleOf(b.style), iconPath = icon, iconKind = kind)
            }
        }
        is TdApi.ReplyMarkupRemoveKeyboard -> emptyList()
        else -> null
    }

    override suspend fun pressBotButton(chatId: Long, messageId: Long, button: BotButton): String? {
        val data = button.data ?: return null
        val answer = call(TdApi.GetCallbackQueryAnswer(chatId, messageId, TdApi.CallbackQueryPayloadData(data))) as? TdApi.CallbackQueryAnswer
        return answer?.text?.takeIf { it.isNotBlank() }
    }
    private val historyLoading = ConcurrentHashMap.newKeySet<Long>()
    private val historyDone = ConcurrentHashMap.newKeySet<Long>()

    override fun messages(chatId: Long): StateFlow<List<Message>> = messageFlows.getOrPut(chatId) {
        raw.getOrPut(chatId) { ConcurrentHashMap() }
        MutableStateFlow<List<Message>>(emptyList()).also { loadMore(chatId) }
    }

    private fun publishMessages(chatId: Long) {
        val m = raw[chatId] ?: return
        messageFlows[chatId]?.value = m.values.sortedByDescending { it.id }.map(::toMessage)
    }

    override fun loadMore(chatId: Long) {
        if (chatId in historyDone || !historyLoading.add(chatId)) return
        val oldest = raw[chatId]?.keys?.minOrNull() ?: 0L
        send(TdApi.GetChatHistory(chatId, oldest, 0, 50, false)) { r ->
            historyLoading.remove(chatId)
            if (r !is TdApi.Messages) return@send
            if (r.messages.isEmpty()) { historyDone += chatId; return@send }
            val m = raw.getValue(chatId)
            r.messages.forEach { m[it.id] = it }
            publishMessages(chatId)
            // TDLib often returns only a handful from the local cache first; keep going until we have a screenful.
            if (m.size < 30) loadMore(chatId)
        }
    }

    private fun addMessage(msg: TdApi.Message) {
        val m = raw[msg.chatId] ?: return
        m[msg.id] = msg
        publishMessages(msg.chatId)
    }

    override fun sendPhoto(chatId: Long, path: String) {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, opts)
        send(TdApi.SendMessage().apply {
            this.chatId = chatId
            inputMessageContent = TdApi.InputMessagePhoto().apply {
                photo = TdApi.InputPhoto().apply {
                    photo = TdApi.InputFileLocal(path)
                    width = opts.outWidth
                    height = opts.outHeight
                }
                caption = TdApi.FormattedText("", emptyArray())
            }
        }) { if (it is TdApi.Error) authError.value = it.message }
    }

    override fun deleteMessages(chatId: Long, ids: List<Long>) {
        send(TdApi.DeleteMessages(chatId, ids.toLongArray(), true))
    }

    override fun react(chatId: Long, messageId: Long, emoji: String) {
        val chosen = raw[chatId]?.get(messageId)?.interactionInfo?.reactions?.reactions.orEmpty()
            .any { it.isChosen && (it.type as? TdApi.ReactionTypeEmoji)?.emoji == emoji }
        val type = TdApi.ReactionTypeEmoji(emoji)
        send(if (chosen) TdApi.RemoveMessageReaction(chatId, messageId, type) else TdApi.AddMessageReaction(chatId, messageId, type, false, true)) { reportError(it) }
    }

    override fun forward(fromChatId: Long, messageIds: List<Long>, toChatId: Long) {
        send(TdApi.ForwardMessages().apply {
            chatId = toChatId; this.fromChatId = fromChatId; this.messageIds = messageIds.toLongArray()
        }) { reportError(it) }
    }

    override fun pinMessage(chatId: Long, messageId: Long, pinned: Boolean) {
        send(if (pinned) TdApi.PinChatMessage(chatId, messageId, false, false) else TdApi.UnpinChatMessage(chatId, messageId)) { reportError(it) }
    }

    override fun setMuted(chatId: Long, muted: Boolean) {
        val s = tdChats[chatId]?.notificationSettings ?: return
        s.useDefaultMuteFor = false
        s.muteFor = if (muted) Int.MAX_VALUE else 0
        send(TdApi.SetChatNotificationSettings(chatId, s))
    }

    override fun deleteChat(chatId: Long) {
        val report: (TdApi.Object) -> Unit = { if (it is TdApi.Error) authError.value = errorText(it) }
        when (val t = tdChats[chatId]?.type) {
            // Groups and channels: leave (that also removes them from the list). History removal alone fails there.
            is TdApi.ChatTypeSupergroup -> send(TdApi.LeaveChat(chatId), report)
            is TdApi.ChatTypeBasicGroup -> send(TdApi.LeaveChat(chatId)) { r -> report(r); send(TdApi.DeleteChatHistory(chatId, true, false)) }
            is TdApi.ChatTypeSecret -> send(TdApi.CloseSecretChat(t.secretChatId)) { send(TdApi.DeleteChatHistory(chatId, true, false), report) }
            else -> send(TdApi.DeleteChatHistory(chatId, true, false), report)
        }
    }

    override fun block(chatId: Long) {
        val userId = (tdChats[chatId]?.type as? TdApi.ChatTypePrivate)?.userId ?: return
        send(TdApi.SetMessageSenderBlockList(TdApi.MessageSenderUser(userId), TdApi.BlockListMain()))
    }

    override fun setPinned(chatId: Long, pinned: Boolean) {
        send(TdApi.ToggleChatIsPinned(TdApi.ChatListMain(), chatId, pinned)) { if (it is TdApi.Error) authError.value = it.message }
    }

    override fun readChat(chatId: Long) {
        val last = tdChats[chatId]?.lastMessage?.id ?: return
        send(TdApi.ViewMessages().apply { this.chatId = chatId; messageIds = longArrayOf(last); forceRead = true })
    }

    override fun closeChat(chatId: Long) {
        send(TdApi.CloseChat(chatId))
    }

    override fun typing(chatId: Long): StateFlow<Boolean> = typingFlows.getOrPut(chatId) { MutableStateFlow(false) }

    override fun send(chatId: Long, text: String, replyTo: Long?) {
        send(TdApi.SendMessage().apply {
            this.chatId = chatId
            if (replyTo != null) this.replyTo = TdApi.InputMessageReplyToMessage().apply { messageId = replyTo }
            inputMessageContent = TdApi.InputMessageText().apply { this.text = TdApi.FormattedText(text, emptyArray()) }
        }) { if (it is TdApi.Error) authError.value = it.message }
    }

    override fun editMessage(chatId: Long, messageId: Long, text: String) {
        send(TdApi.EditMessageText().apply {
            this.chatId = chatId
            this.messageId = messageId
            inputMessageContent = TdApi.InputMessageText().apply { this.text = TdApi.FormattedText(text, emptyArray()) }
        }) { if (it is TdApi.Error) authError.value = it.message }
    }

    override fun sendVoice(chatId: Long, path: String, durationSec: Int) {
        send(TdApi.SendMessage().apply {
            this.chatId = chatId
            inputMessageContent = TdApi.InputMessageVoiceNote().apply {
                voiceNote = TdApi.InputVoiceNote().apply {
                    voiceNote = TdApi.InputFileLocal(path)
                    duration = durationSec
                    waveform = ByteArray(0)
                }
                caption = TdApi.FormattedText("", emptyArray())
            }
        }) { if (it is TdApi.Error) authError.value = it.message }
    }

    override fun sendTyping(chatId: Long) {
        send(TdApi.SendChatAction().apply { this.chatId = chatId; action = TdApi.ChatActionTyping() })
    }

    override fun markRead(chatId: Long) {
        send(TdApi.OpenChat(chatId))
        val last = tdChats[chatId]?.lastMessage?.id ?: return
        send(TdApi.ViewMessages().apply { this.chatId = chatId; messageIds = longArrayOf(last); forceRead = true })
    }

    // ---------------------------------------------------------------- profile

    override suspend fun profile(chatId: Long): Profile {
        val c = tdChats[chatId]
        val base = Profile(
            chatId, c?.title ?: "", chats.value.firstOrNull { it.id == chatId }?.status ?: "", "", "", "",
            null, null, null, null, photoPath = c?.photo?.small?.let(::path), colorIndex = colorIndex(chatId),
        ).let { p ->
            // Full-size photo for the profile header (synchronous download, it's one file).
            val big = c?.photo?.big?.let { (call(TdApi.DownloadFile(it.id, 1, 0, 0, true)) as? TdApi.File)?.local?.path?.takeIf(String::isNotEmpty) }
            if (big != null) p.copy(photoPath = big) else p
        }
        when (val t = c?.type) {
            is TdApi.ChatTypeSupergroup -> {
                val sg = call(TdApi.GetSupergroup(t.supergroupId)) as? TdApi.Supergroup
                val full = call(TdApi.GetSupergroupFullInfo(t.supergroupId)) as? TdApi.SupergroupFullInfo
                val count = full?.memberCount?.takeIf { it > 0 } ?: sg?.memberCount ?: 0
                return base.copy(
                    canEdit = canChangeInfo(sg?.status),
                    kind = if (t.isChannel) "channel" else "group",
                    status = if (t.isChannel) "$count subscribers" else "$count members",
                    phone = "", birthday = "",
                    username = sg?.usernames?.activeUsernames?.firstOrNull()?.let { "t.me/$it" } ?: "",
                    about = full?.description.orEmpty(),
                )
            }
            is TdApi.ChatTypeBasicGroup -> {
                val full = call(TdApi.GetBasicGroupFullInfo(t.basicGroupId)) as? TdApi.BasicGroupFullInfo
                val bg = call(TdApi.GetBasicGroup(t.basicGroupId)) as? TdApi.BasicGroup
                return base.copy(canEdit = canChangeInfo(bg?.status), kind = "group", status = "${full?.members?.size ?: 0} members", phone = "", birthday = "", username = "", about = full?.description.orEmpty())
            }
            else -> Unit
        }
        val userId = (c?.type as? TdApi.ChatTypePrivate)?.userId ?: return base
        val user = call(TdApi.GetUser(userId)) as? TdApi.User ?: return base
        val full = call(TdApi.GetUserFullInfo(userId)) as? TdApi.UserFullInfo
        val channel = full?.personalChatId?.takeIf { it != 0L }?.let { tdChats[it] ?: (call(TdApi.GetChat(it)) as? TdApi.Chat) }
        val subs = (channel?.type as? TdApi.ChatTypeSupergroup)?.let { (call(TdApi.GetSupergroup(it.supergroupId)) as? TdApi.Supergroup)?.memberCount }
        return base.copy(
            phone = user.phoneNumber.takeIf { it.isNotEmpty() }?.let { "+$it" } ?: "hidden",
            username = user.usernames?.activeUsernames?.firstOrNull()?.let { "@$it" } ?: "—",
            birthday = full?.birthdate?.let { b ->
                val d = "${b.day} ${java.time.Month.of(b.month).getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())}"
                if (b.year > 0) "$d ${b.year} (${LocalDate.now().year - b.year - (if (LocalDate.now() < LocalDate.of(LocalDate.now().year, b.month, b.day)) 1 else 0)} years old)" else d
            } ?: "—",
            channelName = channel?.title,
            channelSubscribers = subs?.let { if (it >= 1000) "%.1fK subscribers".format(it / 1000f) else "$it subscribers" },
            channelPost = channel?.lastMessage?.let { preview(it.content) },
            channelTime = channel?.lastMessage?.let { formatDate(it.date) },
            about = full?.bio?.text.orEmpty(),
        )
    }

    private fun canChangeInfo(s: TdApi.ChatMemberStatus?) = when (s) {
        is TdApi.ChatMemberStatusCreator -> s.isMember
        is TdApi.ChatMemberStatusAdministrator -> s.rights.canChangeInfo
        else -> false
    }

    override suspend fun editChat(chatId: Long, title: String, about: String): String? {
        val c = tdChats[chatId]
        if (title.isNotBlank() && title != c?.title) errorText(call(TdApi.SetChatTitle(chatId, title.trim())))?.let { return it }
        return errorText(call(TdApi.SetChatDescription(chatId, about.trim())))
    }

    override fun setChatPhoto(chatId: Long, path: String) {
        send(TdApi.SetChatPhoto(chatId, TdApi.InputChatPhotoStatic(TdApi.InputFileLocal(path)))) { reportError(it) }
    }

    // ---------------------------------------------------------------- own profile, sessions, new chats, stickers

    private fun errorText(r: TdApi.Object) = (r as? TdApi.Error)?.message?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() }

    override suspend fun myInfo(): MyInfo {
        val u = users[myId] ?: (call(TdApi.GetMe()) as? TdApi.User)
        val bio = (call(TdApi.GetUserFullInfo(myId)) as? TdApi.UserFullInfo)?.bio?.text.orEmpty()
        return MyInfo(u?.firstName.orEmpty(), u?.lastName.orEmpty(), bio, u?.usernames?.editableUsername.orEmpty())
    }

    override suspend fun saveMyInfo(info: MyInfo): String? {
        val old = myInfo()
        if (info.firstName != old.firstName || info.lastName != old.lastName) errorText(call(TdApi.SetName(info.firstName, info.lastName)))?.let { return it }
        if (info.bio != old.bio) errorText(call(TdApi.SetBio(info.bio)))?.let { return it }
        if (info.username != old.username) errorText(call(TdApi.SetUsername(info.username)))?.let { return it }
        return null
    }

    override fun setProfilePhoto(path: String) {
        send(TdApi.SetProfilePhoto(TdApi.InputChatPhotoStatic(TdApi.InputFileLocal(path)), false)) { if (it is TdApi.Error) authError.value = it.message }
    }

    override suspend fun sessions(): List<Session> {
        val r = call(TdApi.GetActiveSessions()) as? TdApi.Sessions ?: return emptyList()
        return r.sessions.sortedByDescending { it.isCurrent }.map {
            Session(
                it.id, it.deviceModel.ifEmpty { it.platform }, "${it.applicationName} ${it.applicationVersion}".trim(),
                listOf(it.location, it.ipAddress).filter(String::isNotEmpty).joinToString(" • "),
                if (it.isCurrent) "online" else formatDate(it.lastActiveDate), it.isCurrent,
            )
        }
    }

    override suspend fun terminateSession(id: Long) { call(TdApi.TerminateSession(id)) }
    override suspend fun terminateOtherSessions() { call(TdApi.TerminateAllOtherSessions()) }

    override suspend fun contacts(): List<Contact> {
        val ids = (call(TdApi.GetContacts()) as? TdApi.Users)?.userIds ?: return emptyList()
        return ids.toList().mapNotNull { id -> (users[id] ?: (call(TdApi.GetUser(id)) as? TdApi.User))?.let { u ->
            users[id] = u
            Contact(id, "${u.firstName} ${u.lastName}".trim(), status(u), colorIndex(id), u.profilePhoto?.small?.let(::path))
        } }.sortedBy { it.name }
    }

    override suspend fun createGroup(title: String, userIds: List<Long>): Result<Long> =
        when (val r = call(TdApi.CreateNewBasicGroupChat(userIds.toLongArray(), title, 0))) {
            is TdApi.CreatedBasicGroupChat -> Result.success(r.chatId)
            else -> Result.failure(Exception(errorText(r) ?: "Couldn't create the group"))
        }

    override suspend fun createChannel(title: String, description: String): Result<Long> =
        when (val r = call(TdApi.CreateNewSupergroupChat(title, false, true, description, null, 0, false))) {
            is TdApi.Chat -> Result.success(r.id)
            else -> Result.failure(Exception(errorText(r) ?: "Couldn't create the channel"))
        }

    override suspend fun addContact(firstName: String, lastName: String, phone: String): Result<Long> {
        val r = call(TdApi.ImportContacts(arrayOf(TdApi.ImportedContact(phone.filter { it.isDigit() || it == '+' }, firstName, lastName, null))))
        val userId = (r as? TdApi.ImportedContacts)?.userIds?.firstOrNull()
            ?: return Result.failure(Exception(errorText(r) ?: "Couldn't add the contact"))
        if (userId == 0L) return Result.failure(Exception("This phone number isn't on Telegram"))
        return when (val c = call(TdApi.CreatePrivateChat(userId, false))) {
            is TdApi.Chat -> Result.success(c.id)
            else -> Result.failure(Exception(errorText(c) ?: "Couldn't open the chat"))
        }
    }

    override suspend fun createSecretChat(userId: Long): Result<Long> =
        when (val r = call(TdApi.CreateNewSecretChat(userId))) {
            is TdApi.Chat -> Result.success(r.id)
            else -> Result.failure(Exception(errorText(r) ?: "Couldn't start a secret chat"))
        }

    override suspend fun stickerSets(): List<StickerSet> {
        val infos = (call(TdApi.GetInstalledStickerSets(TdApi.StickerTypeRegular())) as? TdApi.StickerSets)?.sets ?: return emptyList()
        return infos.mapNotNull { info ->
            val set = call(TdApi.GetStickerSet(info.id)) as? TdApi.StickerSet ?: return@mapNotNull null
            StickerSet(set.id, set.title, set.stickers.map { Sticker(it.sticker.id, it.sticker.local.path.takeIf { _ -> it.sticker.local.isDownloadingCompleted }, it.emoji, kindOf(it.format)) })
        }
    }

    override suspend fun stickerFile(sticker: Sticker): String? = sticker.path
        ?: (call(TdApi.DownloadFile(sticker.fileId, 1, 0, 0, true)) as? TdApi.File)?.local?.path?.takeIf(String::isNotEmpty)

    override fun sendSticker(chatId: Long, sticker: Sticker) {
        val file: TdApi.InputFile = if (sticker.fileId != 0) TdApi.InputFileId(sticker.fileId) else TdApi.InputFileLocal(sticker.path)
        send(TdApi.SendMessage().apply {
            this.chatId = chatId
            inputMessageContent = TdApi.InputMessageSticker(TdApi.InputSticker(file, null, 512, 512), sticker.emoji)
        }) { if (it is TdApi.Error) authError.value = it.message }
    }

    // ---------------------------------------------------------------- settings, files, search, round videos

    private fun scopeOf(key: String): TdApi.NotificationSettingsScope = when (key) {
        "private" -> TdApi.NotificationSettingsScopePrivateChats()
        "group" -> TdApi.NotificationSettingsScopeGroupChats()
        else -> TdApi.NotificationSettingsScopeChannelChats()
    }

    override suspend fun notifyScopes() = listOf("private", "group", "channel").map { key ->
        val s = call(TdApi.GetScopeNotificationSettings(scopeOf(key))) as? TdApi.ScopeNotificationSettings
        NotifyScope(key, (s?.muteFor ?: 0) == 0, s?.showPreview ?: true)
    }

    override suspend fun setNotifyScope(scope: NotifyScope) {
        val s = call(TdApi.GetScopeNotificationSettings(scopeOf(scope.key))) as? TdApi.ScopeNotificationSettings ?: return
        s.muteFor = if (scope.enabled) 0 else Int.MAX_VALUE
        s.showPreview = scope.preview
        call(TdApi.SetScopeNotificationSettings(scopeOf(scope.key), s))
    }

    private fun privacySetting(key: String): TdApi.UserPrivacySetting = when (key) {
        "phone" -> TdApi.UserPrivacySettingShowPhoneNumber()
        "lastSeen" -> TdApi.UserPrivacySettingShowStatus()
        "photo" -> TdApi.UserPrivacySettingShowProfilePhoto()
        "forwards" -> TdApi.UserPrivacySettingShowLinkInForwardedMessages()
        "calls" -> TdApi.UserPrivacySettingAllowCalls()
        else -> TdApi.UserPrivacySettingAllowChatInvites()
    }

    override suspend fun privacy(key: String): String {
        val rules = (call(TdApi.GetUserPrivacySettingRules(privacySetting(key))) as? TdApi.UserPrivacySettingRules)?.rules.orEmpty()
        // Rules apply in order; the first general one decides.
        return when (rules.firstOrNull { it is TdApi.UserPrivacySettingRuleAllowAll || it is TdApi.UserPrivacySettingRuleAllowContacts || it is TdApi.UserPrivacySettingRuleRestrictAll }) {
            is TdApi.UserPrivacySettingRuleAllowAll -> "everybody"
            is TdApi.UserPrivacySettingRuleAllowContacts -> "contacts"
            else -> "nobody"
        }
    }

    override suspend fun setPrivacy(key: String, value: String) {
        val rule: TdApi.UserPrivacySettingRule = when (value) {
            "everybody" -> TdApi.UserPrivacySettingRuleAllowAll()
            "contacts" -> TdApi.UserPrivacySettingRuleAllowContacts()
            else -> TdApi.UserPrivacySettingRuleRestrictAll()
        }
        call(TdApi.SetUserPrivacySettingRules(privacySetting(key), TdApi.UserPrivacySettingRules(arrayOf(rule))))
    }

    override suspend fun blockedCount() = (call(TdApi.GetBlockedMessageSenders(TdApi.BlockListMain(), 0, 1)) as? TdApi.MessageSenders)?.totalCount ?: 0
    override suspend fun hasPassword() = (call(TdApi.GetPasswordState()) as? TdApi.PasswordState)?.hasPassword == true

    override suspend fun storageUsage() = (call(TdApi.GetStorageStatisticsFast()) as? TdApi.StorageStatisticsFast)?.let { it.filesSize + it.databaseSize } ?: 0L

    override suspend fun clearCache() {
        // Zero limits = delete every cached file (TDLib downloads them again when needed).
        call(TdApi.OptimizeStorage(0, 0, 0, 0, emptyArray(), LongArray(0), LongArray(0), false, 0))
    }

    override fun download(chatId: Long, messageId: Long) {
        val c = raw[chatId]?.get(messageId)?.content ?: return
        val file = when (c) {
            is TdApi.MessagePhoto -> c.photo.sizes.filter { it.width <= 1280 }.maxByOrNull { it.width }?.photo ?: c.photo.sizes.lastOrNull()?.photo
            is TdApi.MessageDocument -> c.document.document
            is TdApi.MessageVideoNote -> c.videoNote.video
            is TdApi.MessageVideo -> c.video.video
            is TdApi.MessageAnimation -> c.animation.animation
            else -> null
        } ?: return
        fileChats[file.id] = chatId
        send(TdApi.DownloadFile(file.id, 32, 0, 0, false))
    }

    override fun sendDocument(chatId: Long, path: String) {
        send(TdApi.SendMessage().apply {
            this.chatId = chatId
            inputMessageContent = TdApi.InputMessageDocument(TdApi.InputDocument(TdApi.InputFileLocal(path), null, false), TdApi.FormattedText("", emptyArray()))
        }) { if (it is TdApi.Error) authError.value = it.message }
    }

    override fun sendVideo(chatId: Long, path: String) {
        val (w, h, sec) = videoInfo(path)
        send(TdApi.SendMessage().apply {
            this.chatId = chatId
            inputMessageContent = TdApi.InputMessageVideo(
                TdApi.InputVideo(TdApi.InputFileLocal(path), null, null, 0, IntArray(0), sec, w, h, true),
                TdApi.FormattedText("", emptyArray()), false, null, false,
            )
        }) { if (it is TdApi.Error) authError.value = it.message }
    }

    override fun sendVideoNote(chatId: Long, path: String, durationSec: Int) {
        send(TdApi.SendMessage().apply {
            this.chatId = chatId
            inputMessageContent = TdApi.InputMessageVideoNote(TdApi.InputVideoNote(TdApi.InputFileLocal(path), null, durationSec, 384), null)
        }) { if (it is TdApi.Error) authError.value = it.message }
    }

    override suspend fun searchMessages(chatId: Long, query: String): List<Message> {
        val r = call(TdApi.SearchChatMessages(chatId, null, query, null, 0, 0, 50, null)) as? TdApi.FoundChatMessages ?: return emptyList()
        return r.messages.map(::toMessage)
    }

    override suspend fun sharedMedia(chatId: Long, kind: String): List<Message> {
        val filter = when (kind) {
            "media" -> TdApi.SearchMessagesFilterPhotoAndVideo()
            "files" -> TdApi.SearchMessagesFilterDocument()
            else -> TdApi.SearchMessagesFilterUrl()
        }
        val r = call(TdApi.SearchChatMessages(chatId, null, "", null, 0, 0, 60, filter)) as? TdApi.FoundChatMessages ?: return emptyList()
        if (kind != "media") return r.messages.map(::toMessage)
        // The grid needs a small picture at once: a ~320px photo size or the video's thumbnail, downloaded in place.
        return kotlinx.coroutines.coroutineScope {
            r.messages.map { m ->
                async {
                    val file = when (val c = m.content) {
                        is TdApi.MessagePhoto -> (c.photo.sizes.firstOrNull { it.width >= 240 } ?: c.photo.sizes.lastOrNull())?.photo
                        is TdApi.MessageVideo -> c.video.thumbnail?.file
                        else -> null
                    }
                    val path = file?.let { f ->
                        f.local.path.takeIf { f.local.isDownloadingCompleted && it.isNotEmpty() }
                            ?: (call(TdApi.DownloadFile(f.id, 1, 0, 0, true)) as? TdApi.File)?.local?.path?.takeIf(String::isNotEmpty)
                    }
                    toMessage(m).copy(photoPath = path)
                }
            }.awaitAll()
        }
    }

    // ---------------------------------------------------------------- calls (signaling only, see ActiveCall)

    override val activeCall = MutableStateFlow<ActiveCall?>(null)

    /** What Telegram's own apps announce; the peer picks a common layer. */
    private fun callProtocol() = TdApi.CallProtocol(true, true, 65, 92, arrayOf("4.0.0", "3.0.0", "2.7.7", "2.4.4"))

    // Private chat ids are the user ids in TDLib.
    override fun startCall(chatId: Long, video: Boolean) {
        activeCall.value = ActiveCall(0, chatId, outgoing = true, video = video, phase = CallPhase.Requesting)
        send(TdApi.CreateCall(chatId, callProtocol(), video)) { r ->
            if (r is TdApi.Error) {
                authError.value = r.message
                activeCall.update { it?.copy(phase = CallPhase.Failed) }
            }
        }
    }

    override fun acceptCall() {
        val c = activeCall.value ?: return
        send(TdApi.AcceptCall(c.id, callProtocol())) { r -> if (r is TdApi.Error) authError.value = r.message }
    }

    override fun endCall() {
        val c = activeCall.value ?: return
        if (c.id == 0) { activeCall.value = c.copy(phase = CallPhase.Ended); return } // still being created
        val seconds = if (c.connectedAt > 0) ((System.currentTimeMillis() - c.connectedAt) / 1000).toInt() else 0
        send(TdApi.DiscardCall(c.id, false, "", seconds, c.video, 0))
        activeCall.value = c.copy(phase = CallPhase.Ended)
    }

    override fun dismissCall() { activeCall.value = null }

    private fun onCall(call: TdApi.Call) {
        val old = activeCall.value
        // Another call while one is on screen: TDLib reports it; we only show one, so it is declined.
        if (old != null && !old.over && old.id != 0 && old.id != call.id) {
            if (call.state is TdApi.CallStatePending && !call.isOutgoing) send(TdApi.DiscardCall(call.id, false, "", 0, call.isVideo, 0))
            return
        }
        val phase = when (val s = call.state) {
            is TdApi.CallStatePending -> if (call.isOutgoing && !s.isReceived) CallPhase.Requesting else CallPhase.Ringing
            is TdApi.CallStateExchangingKeys -> CallPhase.Connecting
            is TdApi.CallStateReady -> CallPhase.Active
            is TdApi.CallStateHangingUp -> CallPhase.Ended
            is TdApi.CallStateDiscarded -> when (s.reason) {
                is TdApi.CallDiscardReasonDeclined -> CallPhase.Declined
                is TdApi.CallDiscardReasonMissed -> CallPhase.Missed
                else -> CallPhase.Ended
            }
            else -> CallPhase.Failed
        }
        // A finished call we already took off the screen stays off it.
        if (old == null && phase != CallPhase.Requesting && phase != CallPhase.Ringing) return
        val connectedAt = old?.connectedAt?.takeIf { it > 0 } ?: if (phase == CallPhase.Active) System.currentTimeMillis() else 0
        activeCall.value = ActiveCall(call.id, call.userId, call.isOutgoing, call.isVideo, phase, connectedAt)
    }

    override fun loadAround(chatId: Long, messageId: Long) {
        send(TdApi.GetChatHistory(chatId, messageId, -20, 40, false)) { r ->
            if (r !is TdApi.Messages) return@send
            val m = raw.getOrPut(chatId) { ConcurrentHashMap() }
            r.messages.forEach { m[it.id] = it }
            publishMessages(chatId)
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun colorIndex(id: Long) = (abs(id) % 7).toInt()

    private fun formatTime(unix: Int) =
        Instant.ofEpochSecond(unix.toLong()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    private fun formatDate(unix: Int): String {
        val t = Instant.ofEpochSecond(unix.toLong()).atZone(ZoneId.systemDefault())
        val days = java.time.temporal.ChronoUnit.DAYS.between(t.toLocalDate(), LocalDate.now())
        return when {
            days == 0L -> formatTime(unix)
            days < 7 -> t.format(DateTimeFormatter.ofPattern("EEE"))
            else -> t.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
        }
    }

    // Last initializer: TDLib starts calling onUpdate right away, so every map above must already exist.
    init {
        client = Client.create({ onUpdate(it) }, null, null)
    }
}
