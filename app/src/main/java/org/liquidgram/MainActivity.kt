package org.liquidgram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.liquidgram.data.AuthState
import org.liquidgram.data.DemoTelegram
import org.liquidgram.data.TdTelegram
import org.liquidgram.data.TelegramRepository
import org.liquidgram.ui.LoginScreen
import androidx.compose.runtime.collectAsState
import android.content.Context
import org.liquidgram.ui.ChatScreen
import org.liquidgram.ui.AppearanceScreen
import org.liquidgram.ui.Look
import org.liquidgram.ui.Prefs
import org.liquidgram.ui.ThemeWaveHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.LocalOverscrollFactory
import org.liquidgram.ui.Tg
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.drop
import org.liquidgram.ui.HomeScreen
import org.liquidgram.ui.ProfileScreen
import org.liquidgram.ui.EditProfileScreen
import org.liquidgram.ui.SessionsScreen
import org.liquidgram.ui.LanguageScreen
import org.liquidgram.ui.NotificationsScreen
import org.liquidgram.ui.PrivacyScreen
import org.liquidgram.ui.DataScreen
import org.liquidgram.ui.PowerScreen
import org.liquidgram.ui.PluginsScreen
import org.liquidgram.ui.DeveloperScreen
import org.liquidgram.ui.NewGroupScreen
import org.liquidgram.ui.NewChannelScreen
import org.liquidgram.ui.NewContactScreen
import org.liquidgram.ui.NewSecretChatScreen

sealed interface Route {
    data object Home : Route
    /** [search]: open with the in-chat search field (from the profile's search button). */
    data class Chat(val id: Long, val search: Boolean = false) : Route
    data class Profile(val id: Long) : Route
    data object Appearance : Route
    /** Simple pages without arguments: editProfile, sessions, language, newGroup, newChannel, newContact, newSecret. */
    data class Screen(val name: String) : Route
}

/** Saves the screen stack as strings, so rotation or process death keeps the user where they were. */
private val RouteStackSaver = listSaver<List<Route>, String>(
    save = { stack ->
        stack.map {
            when (it) {
                Route.Home -> "home"; Route.Appearance -> "appearance"
                is Route.Chat -> "chat:${it.id}"; is Route.Profile -> "profile:${it.id}"
                is Route.Screen -> "screen:${it.name}"
            }
        }
    },
    restore = { saved ->
        saved.map {
            when {
                it.startsWith("chat:") -> Route.Chat(it.removePrefix("chat:").toLong())
                it.startsWith("profile:") -> Route.Profile(it.removePrefix("profile:").toLong())
                it == "appearance" -> Route.Appearance
                it.startsWith("screen:") -> Route.Screen(it.removePrefix("screen:"))
                else -> Route.Home
            }
        }
    },
)

/** One repository per process, so rotation doesn't spin up a second TDLib client. */
object Repo {
    @Volatile private var instance: TelegramRepository? = null
    /** Developer menu: the offline test account, for this run only; every fresh launch goes back to the real one. */
    var demo by mutableStateOf(false)
    fun get(context: Context): TelegramRepository = instance ?: synchronized(this) {
        instance ?: (if (BuildConfig.TG_API_ID != 0) TdTelegram(context.applicationContext, BuildConfig.TG_API_ID, BuildConfig.TG_API_HASH) else DemoTelegram)
            .also { instance = it }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The screen's fastest mode (120 Hz where there is one): some vendors (ColorOS) keep apps at 60 Hz unless the
        // window asks. Idle screens still drop the rate where the panel supports it; animations get every frame.
        display?.let { d ->
            val current = d.mode
            d.supportedModes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate }
                ?.let { window.attributes = window.attributes.apply { preferredDisplayModeId = it.modeId } }
        }
        if (android.os.Build.VERSION.SDK_INT >= 35) window.decorView.requestedFrameRate = android.view.View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        Prefs.load(this)
        org.liquidgram.ui.Plugins.load(this)
        org.liquidgram.ui.ClawdPack.install(this)
        if (savedInstanceState == null) { Repo.demo = BuildConfig.DEBUG && intent.getBooleanExtra("demo", false); // debug builds: `am start --ez demo true` opens the demo account (screenshots)
            org.liquidgram.ui.Incoming.handle(intent) }
        val app = applicationContext
        org.liquidgram.data.Notifier.post = { chatId, title, text -> postMessage(app, chatId, title, text) }
        setContent {
            val repo = if (Repo.demo) DemoTelegram else Repo.get(this)
            val systemDark = isSystemInDarkTheme()
            val dark = if (Look.followSystem) systemDark else Look.darkChoice
            Tg.dark = dark
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            // Persist glass settings whenever one of them changes.
            LaunchedEffect(Unit) {
                snapshotFlow { Prefs.state() }
                    .drop(1)
                    .collect { Prefs.save(this@MainActivity) }
            }

            // No Android stretch overscroll: iOS doesn't stretch, and the stretch is a render-time effect on the list
            // layer that the glass then shows unblurred and smeared (vertical bands under the tab bar).
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                // Keyed: switching accounts starts from a fresh screen stack (old chat ids mean nothing there).
                // Glasmio (iOS glass materials, controls, motion) reads its theme and icon resolver from here.
                io.github.neoruaa.glasmio.core.GlasmioTheme(isDark = dark, appearance = org.liquidgram.ui.Glass.appearance()) {
                    io.github.neoruaa.glasmio.icons.GlasmioIconProvider(resolver = org.liquidgram.ui.TelegramIcons) {
                        ThemeWaveHost { androidx.compose.runtime.key(repo) { App(repo) } }
                    }
                }
            }
        }
    }

    // singleTask: shares and notification taps reach the running app here.
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        org.liquidgram.ui.Incoming.handle(intent)
    }

    override fun onResume() { super.onResume(); org.liquidgram.data.Notifier.foreground = true }
    override fun onPause() { super.onPause(); org.liquidgram.data.Notifier.foreground = false }
}

/** System notification for a new message; tapping it opens that chat. */
private fun postMessage(context: Context, chatId: Long, title: String, text: String) {
    if (android.os.Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
    val nm = context.getSystemService(android.app.NotificationManager::class.java)
    nm.createNotificationChannel(android.app.NotificationChannel("messages", org.liquidgram.ui.Strings.tr("Messages"), android.app.NotificationManager.IMPORTANCE_HIGH))
    val open = android.app.PendingIntent.getActivity(
        context, chatId.hashCode(),
        android.content.Intent(context, MainActivity::class.java).putExtra("chatId", chatId),
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
    )
    nm.notify(
        chatId.hashCode(),
        android.app.Notification.Builder(context, "messages")
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build(),
    )
}

@Composable
private fun App(repo: TelegramRepository) {
            val auth by repo.auth.collectAsState()
            val ready = auth == AuthState.Ready
            androidx.compose.animation.Crossfade(ready, animationSpec = spring(dampingRatio = 1f, stiffness = 300f), label = "auth") { r ->
                if (r) LoggedIn(repo) else LoginScreen(repo)
            }
}

@Composable
private fun LoggedIn(repo: TelegramRepository) {
            // Failures of actions (delete, leave, send…) land in authError; after login nothing else shows them.
            val context = androidx.compose.ui.platform.LocalContext.current
            LaunchedEffect(repo) {
                repo.authError.drop(1).collect { e -> if (e != null) android.widget.Toast.makeText(context, e, android.widget.Toast.LENGTH_LONG).show() }
            }
            var stack by rememberSaveable(stateSaver = RouteStackSaver) { mutableStateOf(listOf<Route>(Route.Home)) }
            fun push(r: Route) { stack = stack + r }
            fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }  // a double back tap must not empty the stack
            // A finished "new chat" form is replaced by the chat it created.
            fun replaceWithChat(id: Long) { stack = stack.dropLast(1) + Route.Chat(id) }
            BackHandler(stack.size > 1) { pop() }
            // Ask once for notifications (Android 13+); the system remembers the answer.
            val askNotify = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(Unit) {
                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            // Outside requests: a notification tap opens its chat, a share opens the chat picker.
            val incoming = org.liquidgram.ui.Incoming
            LaunchedEffect(incoming.openChat) {
                incoming.openChat?.let { stack = listOf(Route.Home, Route.Chat(it)); incoming.openChat = null }
            }
            LaunchedEffect(incoming.share) {
                if (incoming.share != null && stack.lastOrNull() != Route.Screen("share")) stack = stack + Route.Screen("share")
                // Restored after the app was killed: the shared data is gone, leave the empty picker.
                if (incoming.share == null && stack.lastOrNull() == Route.Screen("share")) pop()
            }
            // Keeps each screen's saved state (home tab, scroll positions) while another screen is on top.
            val saved = androidx.compose.runtime.saveable.rememberSaveableStateHolder()

            AnimatedContent(
                targetState = stack,
                contentKey = { it.last() },
                // iOS navigation push/pop as Glasmio measured it (docs/ios-navigation-motion.md): critically damped
                // spring, response 0.3 s; the page on top travels the full width, the one under it 30% and dims by 10%.
                transitionSpec = {
                    val k = (2f * Math.PI.toFloat() / 0.3f).let { it * it }
                    val spec = spring<IntOffset>(dampingRatio = 1f, stiffness = k)
                    val fade = spring<Float>(dampingRatio = 1f, stiffness = k)
                    if (targetState.size >= initialState.size)
                        (slideInHorizontally(spec) { it } togetherWith (slideOutHorizontally(spec) { -(it * 0.3f).toInt() } + fadeOut(fade, targetAlpha = 0.9f)))
                            .apply { targetContentZIndex = 1f }
                    else
                        ((slideInHorizontally(spec) { -(it * 0.3f).toInt() } + fadeIn(fade, initialAlpha = 0.9f)) togetherWith slideOutHorizontally(spec) { it })
                            .apply { targetContentZIndex = -1f }
                },
                label = "nav",
            ) { s ->
                val r = s.last()
                saved.SaveableStateProvider(r.toString()) {
                    when (r) {
                        Route.Home -> HomeScreen(repo, { push(Route.Chat(it)) }, { push(Route.Appearance) }) { push(Route.Screen(it)) }
                        is Route.Chat -> ChatScreen(repo, r.id, ::pop, r.search) { push(Route.Profile(r.id)) }
                        is Route.Profile -> ProfileScreen(repo, r.id, ::pop, { stack = stack.dropLast(1).dropLastWhile { it is Route.Chat && it.id == r.id } + Route.Chat(r.id, search = true) }, onChatGone = { stack = listOf(Route.Home) }, onEditChat = { push(Route.Screen("editChat:${r.id}")) })
                        Route.Appearance -> AppearanceScreen(::pop)
                        is Route.Screen -> when (r.name) {
                            "editProfile" -> EditProfileScreen(repo, ::pop)
                            "myProfile" -> ProfileScreen(repo, repo.me.value.chatId, ::pop, {}, {}) { push(Route.Screen("editProfile")) }
                            "sessions" -> SessionsScreen(repo, ::pop)
                            "language" -> LanguageScreen(::pop)
                            "notifications" -> NotificationsScreen(repo, ::pop)
                            "privacy" -> PrivacyScreen(repo, ::pop)
                            "data" -> DataScreen(repo, ::pop)
                            "power" -> PowerScreen(::pop)
                            "reactions" -> org.liquidgram.ui.QuickReactionsScreen(::pop)
                            "plugins" -> PluginsScreen(::pop)
                            // Null while the screen animates away after sending (or after the app was killed): draw nothing.
                            "share" -> org.liquidgram.ui.Incoming.share?.let { shared ->
                                org.liquidgram.ui.ShareScreen(repo, shared, onSent = { id -> stack = listOf(Route.Home, Route.Chat(id)); org.liquidgram.ui.Incoming.share = null }) {
                                    pop(); org.liquidgram.ui.Incoming.share = null
                                }
                            }
                            "developer" -> DeveloperScreen(::pop)
                            "newGroup" -> NewGroupScreen(repo, ::pop, ::replaceWithChat)
                            "newChannel" -> NewChannelScreen(repo, ::pop, ::replaceWithChat)
                            "newContact" -> NewContactScreen(repo, ::pop, ::replaceWithChat)
                            else -> if (r.name.startsWith("editChat:")) org.liquidgram.ui.EditChatScreen(repo, r.name.removePrefix("editChat:").toLong(), ::pop)
                                else NewSecretChatScreen(repo, ::pop, ::replaceWithChat)
                        }
                    }
                }
            }
            // A call slides up over every screen (signaling only for now, see ActiveCall).
            org.liquidgram.ui.CallOverlay(repo)
}
