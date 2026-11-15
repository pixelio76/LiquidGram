package org.liquidgram.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Palette. Every colour reads [dark], a snapshot state, so the whole UI recomposes when the theme flips. */
object Tg {
    var dark by mutableStateOf(false)

    private fun c(light: Long, dark: Long) = if (this.dark) Color(dark) else Color(light)

    val Accent get() = Look.accentColors[Look.accent.coerceIn(0, Look.accentColors.lastIndex)]
    val Text get() = c(0xFF000000, 0xFFFFFFFF)
    val Secondary get() = c(0xFF8E8E93, 0xFF98989F)
    val Separator get() = c(0xFFE2E2E6, 0xFF38383A)
    /** Screen background. */
    val Bg get() = c(0xFFFFFFFF, 0xFF000000)
    /** Background of grouped (settings-style) screens and the cards on them. */
    val Grouped get() = c(0xFFF2F2F7, 0xFF000000)
    val Card get() = c(0xFFFFFFFF, 0xFF1C1C1E)
    val Field get() = c(0xFFEFEFF0, 0xFF1C1C1E)
    val Badge get() = Accent
    val Muted get() = c(0xFFB3B3B8, 0xFF5A5A5E)
    val Read get() = c(0xFF30B94D, 0xFF30D158)
    val Red get() = c(0xFFFF3B30, 0xFFFF453A)
    val InBubble get() = c(0xFFFFFFFF, 0xFF262628)
    val OutBubble get() = c(0xFFE2F8CF, 0xFF2B5278)
    val OutMeta get() = c(0xFF4CA35A, 0xFF8CB8E6)
    val Placeholder get() = c(0xFFDDE3EA, 0xFF2C2C2E)
    // Telegram avatar gradients (top, bottom)
    val avatarColors = listOf(
        Color(0xFFFF885E) to Color(0xFFFF516A),
        Color(0xFFFFCD6A) to Color(0xFFFFA85C),
        Color(0xFF82B1FF) to Color(0xFF665FFF),
        Color(0xFFA0DE7E) to Color(0xFF54CB68),
        Color(0xFF53EDD6) to Color(0xFF28C9B7),
        Color(0xFF72D5FD) to Color(0xFF2A9EF1),
        Color(0xFFE0A2F3) to Color(0xFFD669ED),
    )
}

/** Glass options; Glasmio draws the glass itself (github.com/NEORUAA/Glasmio), these map onto its [GlassAppearance]. */
object Glass {
    /** Liquid refraction on; off keeps the frosted blur only. */
    var liquid by mutableStateOf(true)
    /** Opaque, readable surfaces instead of glass (iOS Reduce Transparency). */
    var reduceTransparency by mutableStateOf(false)
    /** Milkiness of the glass; [DEFAULT_OPACITY] = Glasmio's own, 0 = clear, twice the default = twice as milky. */
    var opacity by mutableFloatStateOf(DEFAULT_OPACITY)
    /** Frost radius in dp (Glasmio's materials use 5). */
    var blur by mutableFloatStateOf(DEFAULT_BLUR)

    const val DEFAULT_OPACITY = 1.25f
    const val DEFAULT_BLUR = 5f

    /** Glasmio (vendored in glasmio/) applies blur and opacity to every glass element itself: buttons, tab bar, menus, controls. */
    fun appearance() = io.github.neoruaa.glasmio.core.GlassAppearance(
        liquidGlassEnabled = liquid && !Look.powerGlass,
        reduceTransparency = reduceTransparency,
        frostRadius = if (blur == DEFAULT_BLUR) null else blur.dp,
        density = opacity / DEFAULT_OPACITY,
    )

    fun reset() { liquid = true; reduceTransparency = false; opacity = DEFAULT_OPACITY; blur = DEFAULT_BLUR }
}

/** Everything else the user can customise. */
object Look {
    var followSystem by mutableStateOf(true)
    /** Theme chosen with the button; used when [followSystem] is off. */
    var darkChoice by mutableStateOf(false)
    var accent by mutableIntStateOf(0)
    var wallpaper by mutableIntStateOf(0)
    var textScale by mutableFloatStateOf(1f)
    /** Corner radius of message bubbles, dp. */
    var bubbleRadius by mutableFloatStateOf(18f)
    /** 0 = English, 1 = Russian (see [Strings]). */
    var language by mutableIntStateOf(0)
    /** Accent tint over own message glass. */
    var ownTint by mutableFloatStateOf(0.22f)
    /** Wallpaper doodles: opacity and size multipliers. */
    var patternAlpha by mutableFloatStateOf(1f)
    var patternSize by mutableFloatStateOf(1f)
    /** 1 = round avatars, 0 = square. */
    var avatarRound by mutableFloatStateOf(1f)
    /** Width of the avatars' glass rim, gloss and shadow (0 = off). */
    var avatarRim by mutableFloatStateOf(1f)
    var showStories by mutableStateOf(true)
    var compactList by mutableStateOf(false)
    /** Paint avatars, settings icons, emoji and stickers in the accent colour (photos and videos stay as they are). */
    var mono by mutableStateOf(false)
    /** User-chosen emoji font file (.ttf), copied into app storage; null = system emoji. */
    var emojiFont by mutableStateOf<String?>(null)
    /** Power saving: stickers play once instead of looping; glass without refraction. */
    var powerStickers by mutableStateOf(false)
    var powerGlass by mutableStateOf(false)
    /** Auto-download (Data and Storage); copied into [org.liquidgram.data.MediaPrefs] on save. */
    var autoPhotos by mutableStateOf(true)
    var autoFiles by mutableStateOf(false)
    /** Round videos start on the back camera instead of the front one. */
    var roundBackCamera by mutableStateOf(false)
    /** Reactions offered above the message menu (Settings → Quick Reactions), in order; [doubleTap] reacts on double tap ("" = off). */
    val DEFAULT_REACTIONS = listOf("❤", "👍", "👎", "🔥", "🥰", "👏", "😁")
    var quickReactions by mutableStateOf(DEFAULT_REACTIONS)
    var doubleTap by mutableStateOf("❤")
    /** Free emoji reactions every chat allows by default. */
    val ALL_REACTIONS = listOf(
        "👍", "👎", "❤", "🔥", "🥰", "👏", "😁", "🤔", "🤯", "😱", "🤬", "😢", "🎉", "🤩", "🤮", "💩", "🙏", "👌", "🕊", "🤡",
        "🥱", "🥴", "😍", "🐳", "❤‍🔥", "🌚", "🌭", "💯", "🤣", "⚡", "🍌", "🏆", "💔", "🤨", "😐", "🍓", "🍾", "💋", "🖕", "😈",
        "😴", "😭", "🤓", "👻", "👨‍💻", "👀", "🎃", "🙈", "😇", "😨", "🤝", "✍", "🤗", "🫡", "🎅", "🎄", "☃", "💅", "🤪", "🗿",
        "🆒", "💘", "🙉", "🦄", "😘", "💊", "🙊", "😎", "👾", "🤷‍♂", "🤷", "🤷‍♀", "😡",
    )

    val accentColors = listOf(
        Color(0xFF0A84FF), Color(0xFF5E5CE6), Color(0xFFAF52DE), Color(0xFFFF2D55),
        Color(0xFFFF9500), Color(0xFF34C759), Color(0xFF30B0C7),
    )

    /** Chat backgrounds: [plain] is a solid colour, otherwise accent-tinted doodles. */
    class Wallpaper(val name: String, val plain: Boolean = false)

    val wallpapers = listOf(Wallpaper("Doodles"), Wallpaper("Plain", plain = true))

    fun resetAll() {
        accent = 0; wallpaper = 0; textScale = 1f; bubbleRadius = 18f; followSystem = true
        ownTint = 0.22f; patternAlpha = 1f; patternSize = 1f; avatarRound = 1f; avatarRim = 1f; showStories = true; compactList = false
        Glass.reset()
    }
}

/** Saves and restores [Glass] and [Look]. */
object Prefs {
    private const val NAME = "appearance"

    fun load(context: Context) {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        Glass.liquid = p.getBoolean("glassLiquid", true)
        Glass.reduceTransparency = p.getBoolean("glassReduce", false)
        Glass.opacity = p.getFloat("glassOpacity", Glass.DEFAULT_OPACITY)
        Glass.blur = p.getFloat("glassBlur", Glass.DEFAULT_BLUR)
        Look.followSystem = p.getBoolean("followSystem", true)
        Look.darkChoice = p.getBoolean("darkChoice", false)
        Look.accent = p.getInt("accent", 0)
        // Indexes of the removed gradient wallpapers fall back to Doodles.
        Look.wallpaper = p.getInt("wallpaper", 0).takeIf { it in Look.wallpapers.indices } ?: 0
        Look.textScale = p.getFloat("textScale", 1f)
        Look.bubbleRadius = p.getFloat("bubbleRadius", 18f)
        Look.language = p.getInt("language", if (java.util.Locale.getDefault().language == "ru") 1 else 0)
        Look.ownTint = p.getFloat("ownTint", 0.22f)
        Look.patternAlpha = p.getFloat("patternAlpha", 1f)
        Look.patternSize = p.getFloat("patternSize", 1f)
        Look.avatarRound = p.getFloat("avatarRound", 1f)
        Look.avatarRim = p.getFloat("avatarRim", 1f)
        Look.showStories = p.getBoolean("showStories", true)
        Look.compactList = p.getBoolean("compactList", false)
        Look.mono = p.getBoolean("mono", false)
        Look.emojiFont = p.getString("emojiFont", null)?.takeIf { java.io.File(it).exists() }
        Look.powerStickers = p.getBoolean("powerStickers", false)
        Look.powerGlass = p.getBoolean("powerGlass", false)
        Look.autoPhotos = p.getBoolean("autoPhotos", true)
        Look.autoFiles = p.getBoolean("autoFiles", false)
        Look.roundBackCamera = p.getBoolean("roundBackCamera", false)
        Look.quickReactions = p.getString("quickReactions", null)?.split('|')?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() } ?: Look.DEFAULT_REACTIONS
        Look.doubleTap = p.getString("doubleTap", "❤") ?: "❤"
        org.liquidgram.data.MediaPrefs.autoPhotos = Look.autoPhotos
        org.liquidgram.data.MediaPrefs.autoFiles = Look.autoFiles
    }

    /** Snapshot of every saved value; collecting it with snapshotFlow tells when to save. */
    fun state(): List<Any> = listOf(
        Glass.liquid, Glass.reduceTransparency, Glass.opacity, Glass.blur,
        Look.followSystem, Look.darkChoice, Look.accent, Look.wallpaper, Look.textScale, Look.bubbleRadius,
        Look.language, Look.ownTint, Look.patternAlpha, Look.patternSize, Look.avatarRound, Look.avatarRim, Look.showStories, Look.compactList,
        Look.mono, Look.emojiFont ?: "", Look.powerStickers, Look.powerGlass, Look.autoPhotos, Look.autoFiles, Look.roundBackCamera,
        Look.quickReactions, Look.doubleTap,
    )

    fun save(context: Context) {
        org.liquidgram.data.MediaPrefs.autoPhotos = Look.autoPhotos
        org.liquidgram.data.MediaPrefs.autoFiles = Look.autoFiles
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
        .putBoolean("glassLiquid", Glass.liquid).putBoolean("glassReduce", Glass.reduceTransparency)
        .putFloat("glassOpacity", Glass.opacity).putFloat("glassBlur", Glass.blur)
        .putBoolean("followSystem", Look.followSystem).putBoolean("darkChoice", Look.darkChoice)
        .putInt("accent", Look.accent).putInt("wallpaper", Look.wallpaper)
        .putFloat("textScale", Look.textScale).putFloat("bubbleRadius", Look.bubbleRadius)
        .putInt("language", Look.language).putFloat("ownTint", Look.ownTint).putFloat("patternAlpha", Look.patternAlpha)
        .putFloat("patternSize", Look.patternSize).putFloat("avatarRound", Look.avatarRound).putFloat("avatarRim", Look.avatarRim)
        .putBoolean("showStories", Look.showStories).putBoolean("compactList", Look.compactList)
        .putBoolean("mono", Look.mono).putString("emojiFont", Look.emojiFont)
        .putBoolean("powerStickers", Look.powerStickers).putBoolean("powerGlass", Look.powerGlass)
        .putBoolean("autoPhotos", Look.autoPhotos).putBoolean("autoFiles", Look.autoFiles).putBoolean("roundBackCamera", Look.roundBackCamera)
        .putString("quickReactions", Look.quickReactions.joinToString("|")).putString("doubleTap", Look.doubleTap)
        .apply()
    }
}
