package org.liquidgram.data

/**
 * Bridge from the Telegram layer to system notifications: the repository reports new incoming messages, the
 * activity posts them. Only while the app is in the background and its process is alive.
 * shortcut: no push service (FCM) yet, so nothing arrives after Android stops the app; add push for that.
 */
object Notifier {
    @Volatile var foreground = false
    @Volatile var post: ((chatId: Long, title: String, text: String) -> Unit)? = null
}
