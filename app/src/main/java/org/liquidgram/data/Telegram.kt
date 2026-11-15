package org.liquidgram.data

import kotlinx.coroutines.flow.StateFlow

data class Chat(
    val id: Long,
    val title: String,
    val lastMessage: String,
    val time: String,
    val colorIndex: Int,
    val unread: Int = 0,
    val muted: Boolean = false,
    val pinned: Boolean = false,
    val verified: Boolean = false,
    val outgoing: Boolean = false,
    val outgoingRead: Boolean = false,
    val isSaved: Boolean = false,
    val status: String = "last seen recently",
    val folders: Set<String> = emptySet(),
    val photoPath: String? = null,
    /** Group or channel discussion (shows sender names/avatars), not a 1-on-1 chat. */
    val isGroup: Boolean = false,
    /** Last incoming message the user has read; newer ones get the "Unread Messages" divider. */
    val lastReadInboxId: Long = 0,
    /** false: a public channel or group found by global search that the user hasn't joined. */
    val isMember: Boolean = true,
)

data class Message(
    val id: Long,
    val text: String,
    val out: Boolean,
    val time: String,
    val isSticker: Boolean = false,
    val read: Boolean = true,
    /** Photo message: local file once downloaded (null while loading) and its aspect ratio. */
    val isPhoto: Boolean = false,
    val photoPath: String? = null,
    val photoAspect: Float = 1f,
    /** Sender shown above incoming messages in groups. */
    val senderName: String? = null,
    val senderColor: Int = 0,
    val senderPhoto: String? = null,
    /** Quoted message this one replies to. */
    val replyName: String? = null,
    val replyText: String? = null,
    val edited: Boolean = false,
    val canEdit: Boolean = false,
    val isVoice: Boolean = false,
    val voicePath: String? = null,
    val voiceDuration: Int = 0,
    /** Real sticker artwork (null while downloading or for formats we can't draw, then [text] shows its emoji). */
    val stickerPath: String? = null,
    val stickerKind: StickerKind = StickerKind.Static,
    /** Document: name, readable size, local file once downloaded, download running. */
    val isFile: Boolean = false,
    val fileName: String = "",
    val fileSize: String = "",
    val filePath: String? = null,
    val fileLoading: Boolean = false,
    /** Round video message ("кружок"). */
    val isRound: Boolean = false,
    val roundPath: String? = null,
    val roundDuration: Int = 0,
    /** Video or GIF: file once downloaded (tap downloads), preview frame, length; size ratio is [photoAspect]. */
    val isVideo: Boolean = false,
    val isGif: Boolean = false,
    val videoPath: String? = null,
    val videoThumb: String? = null,
    val videoDuration: Int = 0,
    /** Bot inline keyboard under the message. */
    val buttons: List<List<BotButton>> = emptyList(),
    /** Bot reply keyboard this message shows under the input: null = none, empty = the bot removes it. */
    val keyboard: List<List<BotButton>>? = null,
    val keyboardOneTime: Boolean = false,
    val keyboardPlaceholder: String = "",
    /** Emoji reactions under the message; [Reaction.chosen] = ours. */
    val reactions: List<Reaction> = emptyList(),
    val pinned: Boolean = false,
    /** "Forwarded from …" name, null for original messages. */
    val forwardedFrom: String? = null,
)

data class Reaction(val emoji: String, val count: Int, val chosen: Boolean)

/** Stage of a call, from TDLib's call state. */
enum class CallPhase { Requesting, Ringing, Connecting, Active, Ended, Declined, Missed, Failed }

/**
 * The call on screen. Signaling only: TDLib places, accepts and ends calls, but voice and video need the tgcalls
 * media engine, which LiquidGram doesn't have yet. [connectedAt] = when it became active (ms), 0 before.
 */
data class ActiveCall(
    val id: Int, val chatId: Long, val outgoing: Boolean, val video: Boolean, val phase: CallPhase, val connectedAt: Long = 0,
) {
    val over get() = phase == CallPhase.Ended || phase == CallPhase.Declined || phase == CallPhase.Missed || phase == CallPhase.Failed
}

/**
 * Bot button. [kind]: "text" (reply keyboard, sends [text]), "callback", "url", "other" (not supported yet).
 * [style]: "", "primary", "danger", "success" (Bot API colours); [iconPath] is its custom-emoji icon.
 */
data class BotButton(
    val text: String,
    val kind: String = "text",
    val url: String = "",
    val data: ByteArray? = null,
    val style: String = "",
    val iconPath: String? = null,
    val iconKind: StickerKind = StickerKind.Static,
)

/** Width, height (as shown, rotation applied) and whole seconds of a local video; zeros if it can't be read. */
fun videoInfo(path: String): Triple<Int, Int, Int> = runCatching {
    val r = android.media.MediaMetadataRetriever()
    try {
        r.setDataSource(path)
        fun int(key: Int) = r.extractMetadata(key)?.toIntOrNull() ?: 0
        val w = int(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val h = int(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val sec = int(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION) / 1000
        if (int(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 180 == 90) Triple(h, w, sec) else Triple(w, h, sec)
    } finally { r.release() }
}.getOrDefault(Triple(0, 0, 0))

/** Media auto-download choices (Settings → Data and Storage); read by the TDLib layer. */
object MediaPrefs {
    @Volatile var autoPhotos = true
    @Volatile var autoFiles = false
}

/** Notification switches for one chat type: private, group or channel. */
data class NotifyScope(val key: String, val enabled: Boolean, val preview: Boolean)

enum class StickerKind { Static, Lottie, Video }

/** A sticker as the picker shows it. [fileId] 0 = a local file (our bundled pack), sent by [path]. */
data class Sticker(val fileId: Int, val path: String?, val emoji: String, val kind: StickerKind)
data class StickerSet(val id: Long, val title: String, val stickers: List<Sticker>)

/** One logged-in device (Settings → Devices). */
data class Session(val id: Long, val device: String, val app: String, val place: String, val lastActive: String, val current: Boolean)

/** Someone from the address book, for new group / secret chat pickers. */
data class Contact(val userId: Long, val name: String, val status: String, val colorIndex: Int, val photoPath: String? = null)

/** Editable parts of the own profile. */
data class MyInfo(val firstName: String, val lastName: String, val bio: String, val username: String)

data class Story(val chatId: Long, val name: String, val colorIndex: Int, val seen: Boolean, val isMine: Boolean = false, val photoPath: String? = null)

data class Profile(
    val chatId: Long,
    val name: String,
    val status: String,
    val phone: String,
    val username: String,
    val birthday: String,
    val channelName: String?,
    val channelSubscribers: String?,
    val channelPost: String?,
    val channelTime: String?,
    val photoPath: String? = null,
    val colorIndex: Int = 0,
    /** "user", "group" or "channel": groups and channels show description and link instead of phone. */
    val kind: String = "user",
    /** Bio of a user, description of a group/channel. */
    val about: String = "",
    /** Our own group/channel (creator, or admin allowed to change its info): title, description and photo are editable. */
    val canEdit: Boolean = false,
)

data class Call(val chatId: Long, val name: String, val colorIndex: Int, val time: String, val outgoing: Boolean, val missed: Boolean, val video: Boolean, val photoPath: String? = null)

sealed interface AuthState {
    data object Loading : AuthState
    data object WaitPhone : AuthState
    data class WaitCode(val sentTo: String, val length: Int = 5) : AuthState
    data class WaitPassword(val hint: String) : AuthState
    /** A step this client does not implement (e.g. sign-up, e-mail login); shown as text. */
    data class Unsupported(val what: String) : AuthState
    data object Ready : AuthState
}

/** Everything the UI needs from Telegram. DemoTelegram fakes it; TdTelegram talks to the real thing via TDLib. */
interface TelegramRepository {
    val auth: StateFlow<AuthState>
    val authError: StateFlow<String?>
    /** An auth request (phone/code/password) is in flight; the login button waits for it. */
    val authBusy: StateFlow<Boolean>
    /** Link to Telegram's servers: null when connected, otherwise a short status ("Connecting…"). */
    val connection: StateFlow<String?>
    val chats: StateFlow<List<Chat>>
    val stories: StateFlow<List<Story>>
    val calls: StateFlow<List<Call>>
    val me: StateFlow<Profile>
    val folders: StateFlow<List<String>>
    fun submitAuth(input: String)
    fun logOut()
    fun messages(chatId: Long): StateFlow<List<Message>>
    fun typing(chatId: Long): StateFlow<Boolean>
    fun send(chatId: Long, text: String, replyTo: Long? = null)
    fun editMessage(chatId: Long, messageId: Long, text: String)
    /** Ogg/Opus voice note recorded to [path]. */
    fun sendVoice(chatId: Long, path: String, durationSec: Int)
    /** Tell the other side we're typing (TDLib keeps it alive ~5 s). */
    fun sendTyping(chatId: Long)
    fun markRead(chatId: Long)
    fun closeChat(chatId: Long)
    /** Asks for older messages; they arrive through messages(chatId). */
    fun loadMore(chatId: Long)
    fun sendPhoto(chatId: Long, path: String)
    fun deleteMessages(chatId: Long, ids: List<Long>)
    /** Adds our [emoji] reaction, or removes it if it is already ours. */
    fun react(chatId: Long, messageId: Long, emoji: String)
    fun forward(fromChatId: Long, messageIds: List<Long>, toChatId: Long)
    fun pinMessage(chatId: Long, messageId: Long, pinned: Boolean)
    fun setMuted(chatId: Long, muted: Boolean)
    fun deleteChat(chatId: Long)
    fun block(chatId: Long)
    fun setPinned(chatId: Long, pinned: Boolean)
    /** Marks everything in the chat as read without opening it. */
    fun readChat(chatId: Long)
    suspend fun profile(chatId: Long): Profile

    suspend fun myInfo(): MyInfo
    /** Saves name, bio and username; returns an error text or null. */
    suspend fun saveMyInfo(info: MyInfo): String?
    fun setProfilePhoto(path: String)
    /** Renames a group/channel and sets its description; returns an error text or null. */
    suspend fun editChat(chatId: Long, title: String, about: String): String?
    fun setChatPhoto(chatId: Long, path: String)
    suspend fun sessions(): List<Session>
    suspend fun terminateSession(id: Long)
    suspend fun terminateOtherSessions()
    suspend fun contacts(): List<Contact>
    /** Each returns the new chat's id, or fails with a readable message. */
    suspend fun createGroup(title: String, userIds: List<Long>): Result<Long>
    suspend fun createChannel(title: String, description: String): Result<Long>
    suspend fun addContact(firstName: String, lastName: String, phone: String): Result<Long>
    suspend fun createSecretChat(userId: Long): Result<Long>
    /** Installed sticker packs (our bundled pack is added by the UI). */
    suspend fun stickerSets(): List<StickerSet>
    /** Local file of a picker sticker, downloading it first if needed. */
    suspend fun stickerFile(sticker: Sticker): String?
    fun sendSticker(chatId: Long, sticker: Sticker)

    suspend fun notifyScopes(): List<NotifyScope>
    suspend fun setNotifyScope(scope: NotifyScope)
    /** Privacy keys: phone, lastSeen, photo, forwards, calls, invites; values: everybody, contacts, nobody. */
    suspend fun privacy(key: String): String
    suspend fun setPrivacy(key: String, value: String)
    suspend fun blockedCount(): Int
    suspend fun hasPassword(): Boolean
    /** Bytes of cached media and database. */
    suspend fun storageUsage(): Long
    suspend fun clearCache()

    /** Starts downloading a message's photo, file or round video. */
    fun download(chatId: Long, messageId: Long)
    fun sendDocument(chatId: Long, path: String)
    fun sendVideo(chatId: Long, path: String)
    fun sendVideoNote(chatId: Long, path: String, durationSec: Int)
    suspend fun searchMessages(chatId: Long, query: String): List<Message>
    /** The call in progress or just finished (null = none); [startCall] places one to a private chat's user. */
    val activeCall: StateFlow<ActiveCall?>
    fun startCall(chatId: Long, video: Boolean)
    fun acceptCall()
    /** Hangs up, declines or cancels the current call. */
    fun endCall()
    /** Takes a finished call off the screen. */
    fun dismissCall()
    /** Chat's shared media for the profile tabs, newest first: [kind] "media" (photos and videos), "files" or "links". */
    suspend fun sharedMedia(chatId: Long, kind: String): List<Message>
    /** Loads history around a message (search jumps to it). */
    fun loadAround(chatId: Long, messageId: Long)
    /** Public channels, groups, bots and people matching [query], including ones the user isn't in ([Chat.status] = what it is). */
    suspend fun searchGlobal(query: String): List<Chat>
    /** A chat that isn't in the chat list (opened from global search), or null. */
    fun chatInfo(chatId: Long): Chat?
    /** Join a public channel or group. */
    fun joinChat(chatId: Long)
    /** Callback button press; returns the bot's answer text to show, if any. */
    suspend fun pressBotButton(chatId: Long, messageId: Long, button: BotButton): String?
}
