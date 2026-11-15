package org.liquidgram.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.liquidgram.data.Chat
import org.liquidgram.data.Message
import java.io.File

/** A setting a plugin shows to the user; its value goes into the plugin as {{key}}. */
class PluginOption(o: JSONObject) {
    val key: String = o.getString("key")
    /** "toggle", "number", "text", "words" (comma-separated list, inserted as a regex), "choice". */
    val type: String = o.optString("type", "text")
    val title: String = o.optString("title", key)
    val min = o.optDouble("min", 0.0).toFloat()
    val max = o.optDouble("max", 100.0).toFloat()
    val choices: List<String> = o.optJSONArray("choices")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    val default: Any = when (type) {
        "toggle" -> o.optBoolean("default", false)
        "number" -> o.optDouble("default", min.toDouble())
        "choice" -> o.optString("default", choices.firstOrNull().orEmpty())
        else -> o.optString("default", "")
    }
}

/**
 * LiquidGram plugin: a JSON file that changes the app on this device only. Nothing in it is executed, so a plugin
 * can't reach the network or the account. Format: [Plugins.AI_GUIDE]. [saved] gives the user's option values by id.
 */
class Plugin(val raw: String, val builtIn: Boolean, saved: (String) -> Map<String, Any> = { emptyMap() }) {
    private val base = JSONObject(raw)
    val id: String = base.getString("id").replace(Regex("[^A-Za-z0-9_.-]"), "_")
    val options: List<PluginOption> = base.optJSONArray("options").objects().map(::PluginOption)
    val values: Map<String, Any> = saved(id).let { s -> options.associate { it.key to (s[it.key] ?: it.default) } }
    private val json = JSONObject(resolve())
    val name: String = json.getString("name")
    val author: String = json.optString("author")
    val version: String = json.optString("version", "1.0")
    val description: String = json.optString("description")
    val settings: JSONObject? = json.optJSONObject("settings")
    val strings: Map<String, String> = json.optJSONObject("strings")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty()
    val outgoing = rules("outgoing")
    val incoming = rules("incoming")
    val hideMessages = regexes("hideMessages")
    val hideChats = regexes("hideChats")

    /** What it touches, for the plugin list. */
    val hooks: List<String> = listOfNotNull(
        "Appearance".takeIf { settings != null }, "Texts".takeIf { strings.isNotEmpty() },
        "Sending".takeIf { outgoing.isNotEmpty() }, "Messages".takeIf { incoming.isNotEmpty() || json.has("hideMessages") },
        "Chat List".takeIf { json.has("hideChats") },
    )

    /** Puts option values in: a lone "{{key}}" becomes the JSON value itself (number, true/false), inside text the plain text. */
    private fun resolve(): String = options.fold(raw) { s, o ->
        val v = values.getValue(o.key)
        val text = if (o.type == "words") wordsRegex(v.toString()) else if (v is Double && v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        val literal = if (v is Boolean || v is Number) text else JSONObject.quote(text)
        s.replace("\"{{${o.key}}}\"", literal).replace("{{${o.key}}}", JSONObject.quote(text).let { it.substring(1, it.length - 1) })
    }

    /** "казино, ставки" → (?:\Qказино\E|\Qставки\E); empty matches nothing. */
    private fun wordsRegex(list: String): String = list.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }?.joinToString("|", "(?:", ")") { Regex.escape(it) } ?: "(?!)"

    /** "if": "key" (or "!key") switches a rule on/off by an option. */
    private fun on(cond: String): Boolean {
        if (cond.isBlank()) return true
        val not = cond.startsWith("!")
        val v = values[cond.removePrefix("!")]
        val truthy = when (v) { is Boolean -> v; is Number -> v.toDouble() != 0.0; null -> false; else -> v.toString().isNotBlank() }
        return truthy != not
    }

    private fun rules(key: String): List<Pair<Regex, String>> = json.optJSONArray(key).objects()
        .filter { on(it.optString("if")) }.map { Regex(it.getString("find")) to it.optString("replace") }

    private fun regexes(key: String): List<Regex> = json.optJSONArray(key)?.let { a ->
        (0 until a.length()).mapNotNull { i ->
            when (val e = a.get(i)) { is JSONObject -> e.takeIf { on(it.optString("if")) }?.getString("find"); else -> e.toString() }
        }.map(::Regex)
    }.orEmpty()
}

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

object Plugins {
    var all by mutableStateOf(emptyList<Plugin>())
        private set
    var enabled by mutableStateOf(emptySet<String>())
        private set
    private var active = emptyList<Plugin>()
    private var strings = emptyMap<String, String>()

    private class Key(val get: () -> Any, val set: (Any) -> Unit)
    private fun num(v: Any) = (v as? Number)?.toFloat() ?: v.toString().toFloat()
    private fun f(get: () -> Float, set: (Float) -> Unit) = Key(get) { set(num(it)) }
    private fun i(get: () -> Int, set: (Int) -> Unit) = Key(get) { set(num(it).toInt()) }
    private fun b(get: () -> Boolean, set: (Boolean) -> Unit) = Key(get) { set(it as? Boolean ?: it.toString().toBooleanStrict()) }

    /** Every appearance value a plugin may set (same names as in the saved preferences). Ranges are listed in [AI_GUIDE]. */
    private val keys = mapOf(
        "liquidGlass" to b({ Glass.liquid }) { Glass.liquid = it },
        "reduceTransparency" to b({ Glass.reduceTransparency }) { Glass.reduceTransparency = it },
        "followSystem" to b({ Look.followSystem }) { Look.followSystem = it },
        "darkChoice" to b({ Look.darkChoice }) { Look.darkChoice = it },
        "accent" to i({ Look.accent }) { Look.accent = it.coerceIn(0, Look.accentColors.lastIndex) },
        "wallpaper" to i({ Look.wallpaper }) { Look.wallpaper = it.coerceIn(0, Look.wallpapers.lastIndex) },
        "textScale" to f({ Look.textScale }) { Look.textScale = it.coerceIn(0.7f, 1.5f) },
        "bubbleRadius" to f({ Look.bubbleRadius }) { Look.bubbleRadius = it.coerceIn(0f, 40f) },
        "ownTint" to f({ Look.ownTint }) { Look.ownTint = it.coerceIn(0f, 1f) },
        "patternAlpha" to f({ Look.patternAlpha }) { Look.patternAlpha = it.coerceIn(0f, 2f) },
        "patternSize" to f({ Look.patternSize }) { Look.patternSize = it.coerceIn(0.3f, 3f) },
        "avatarRound" to f({ Look.avatarRound }) { Look.avatarRound = it.coerceIn(0f, 1f) },
        "avatarRim" to f({ Look.avatarRim }) { Look.avatarRim = it.coerceIn(0f, 3f) },
        "showStories" to b({ Look.showStories }) { Look.showStories = it },
        "compactList" to b({ Look.compactList }) { Look.compactList = it },
        "mono" to b({ Look.mono }) { Look.mono = it },
        "powerStickers" to b({ Look.powerStickers }) { Look.powerStickers = it },
        "powerGlass" to b({ Look.powerGlass }) { Look.powerGlass = it },
    )

    private fun prefs(c: Context) = c.getSharedPreferences("plugins", Context.MODE_PRIVATE)
    private fun dir(c: Context) = File(c.filesDir, "plugins").apply { mkdirs() }

    private fun saved(c: Context, id: String): Map<String, Any> = prefs(c).getString("opt:$id", null)
        ?.let { s -> JSONObject(s).let { o -> o.keys().asSequence().associateWith { o.get(it) } } }.orEmpty()

    fun load(c: Context) {
        val builtIn = BUILT_IN.map { Plugin(it, builtIn = true) { id -> saved(c, id) } }
        val installed = dir(c).listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
            .mapNotNull { f -> runCatching { Plugin(f.readText(), builtIn = false) { id -> saved(c, id) } }.getOrNull() }
        all = builtIn + installed.filter { p -> builtIn.none { it.id == p.id } }
        enabled = prefs(c).getStringSet("enabled", emptySet())!!.toSet()
        // Plugins that are gone (removed built-ins, deleted files): give back the look they had changed.
        enabled.filter { id -> all.none { it.id == id } }.forEach { id -> restore(c, id) }
        enabled = enabled.filter { id -> all.any { it.id == id } }.toSet()
        prefs(c).edit().putStringSet("enabled", enabled).apply()
        refresh()
    }

    private fun refresh() {
        active = all.filter { it.id in enabled }
        strings = active.fold(emptyMap()) { acc, p -> acc + p.strings }
    }

    private fun restore(c: Context, id: String) {
        prefs(c).getString("restore:$id", null)?.let { JSONObject(it) }?.let { old ->
            old.keys().forEach { k -> keys[k]?.let { key -> runCatching { key.set(old.get(k)) } } }
        }
        prefs(c).edit().remove("restore:$id").apply()
    }

    fun setEnabled(c: Context, p: Plugin, on: Boolean) {
        if (on == p.id in enabled) return
        p.settings?.let { s ->
            if (on) {
                // Remember what the plugin replaces, so turning it off restores the user's own look.
                val old = JSONObject()
                s.keys().forEach { k -> keys[k]?.let { key -> old.put(k, key.get()); runCatching { key.set(s.get(k)) } } }
                prefs(c).edit().putString("restore:" + p.id, old.toString()).apply()
            } else restore(c, p.id)
        }
        enabled = if (on) enabled + p.id else enabled - p.id
        prefs(c).edit().putStringSet("enabled", enabled).apply()
        refresh()
    }

    /** Changes one of a plugin's own settings; a running plugin picks it up at once. */
    fun setOption(c: Context, p: Plugin, key: String, value: Any) {
        val wasOn = p.id in enabled
        if (wasOn && p.settings != null) setEnabled(c, p, false)
        prefs(c).edit().putString("opt:" + p.id, JSONObject(p.values + (key to value)).toString()).apply()
        val fresh = Plugin(p.raw, p.builtIn) { saved(c, it) }
        all = all.map { if (it.id == p.id) fresh else it }
        refresh()
        if (wasOn && fresh.settings != null) setEnabled(c, fresh, true)
    }

    /** Installs a plugin file; returns an error to show, or null. */
    fun install(c: Context, text: String): String? {
        val p = try { Plugin(text, builtIn = false) } catch (e: Exception) { return "Not a LiquidGram plugin: " + (e.message ?: "bad JSON") }
        if (all.any { it.builtIn && it.id == p.id }) return "A built-in plugin already has this id"
        all.firstOrNull { it.id == p.id }?.let { setEnabled(c, it, false) }
        File(dir(c), p.id + ".json").writeText(text)
        load(c)
        return null
    }

    fun delete(c: Context, p: Plugin) {
        setEnabled(c, p, false)
        File(dir(c), p.id + ".json").delete()
        prefs(c).edit().remove("opt:" + p.id).apply()
        load(c)
    }

    // Hooks. They read [enabled] so Compose redraws when a plugin is switched.

    fun text(key: String): String? = if (enabled.isEmpty()) null else strings[key]

    fun outgoing(text: String): String = if (enabled.isEmpty()) text else
        active.fold(text) { t, p -> p.outgoing.fold(t) { acc, (re, to) -> re.replace(acc, to) } }

    private fun shown(text: String) = active.fold(text) { t, p -> p.incoming.fold(t) { acc, (re, to) -> re.replace(acc, to) } }

    fun messages(list: List<Message>): List<Message> = if (enabled.isEmpty()) list else list
        .filter { m -> m.out || active.none { p -> p.hideMessages.any { it.containsMatchIn(m.text) } } }
        .map { m -> if (m.out) m else m.copy(text = shown(m.text)) }

    fun chats(list: List<Chat>): List<Chat> = if (enabled.isEmpty()) list else list
        .filter { c -> active.none { p -> p.hideChats.any { it.containsMatchIn(c.title) } } }
        .map { c -> if (c.outgoing) c else c.copy(lastMessage = shown(c.lastMessage)) }

    /** Instructions to paste into an AI chat (Settings → Plugins → Copy Instructions for AI). */
    fun aiGuide(): String = AI_GUIDE + "\n\nAll interface texts that \"strings\" can rename (English keys):\n" +
        Strings.keys.sorted().joinToString("\n") { "- $it" }

    private val AI_GUIDE = """
You are writing a plugin for LiquidGram, an Android Telegram client. Reply with ONE valid JSON object only (no comments,
no markdown) and tell the user to save it as a .json file and open it in Settings → Plugins → Install from File.

A plugin runs no code. It can only use these fields (all optional except id and name):
- "id": unique string, letters/digits/._- only, e.g. "author.myplugin"
- "name", "author", "version", "description": strings shown in the plugin list
- "settings": object of appearance values, applied while the plugin is on; turning it off restores the user's values.
  Keys: blur 0..40 (dp, glass blur), refraction 0..3, tint 0..2 (glass tint opacity), chromatic true/false,
  depth true/false, followSystem true/false (follow system dark mode), darkChoice true/false (dark theme when
  followSystem is false), accent 0..6 (0 blue, 1 indigo, 2 purple, 3 pink, 4 orange, 5 green, 6 teal),
  wallpaper 0..1 (0 doodles, 1 plain), textScale 0.7..1.5, bubbleRadius 0..40, ownTint 0..1 (accent over own
  messages), patternAlpha 0..2, patternSize 0.3..3, avatarRound 0..1 (0 square, 1 round), avatarRim 0..3,
  showStories, compactList, mono (everything in the accent colour), powerStickers, powerGlass: true/false.
- "strings": object { "English interface text": "replacement" } — renames texts in the app (keys listed below).
- "outgoing": array of { "find": regex, "replace": text, "if": option } — rewrites text the user sends.
- "incoming": same rules for incoming messages and chat list previews (shown text only, nothing is sent).
- "hideMessages": array of regexes (or { "find": regex, "if": option }) — hides matching incoming messages.
- "hideChats": same, matched against chat titles — hides chats from the list.
Regexes are Java syntax inside JSON strings, so backslashes are doubled: "\\bword\\b". Use (?iu) for case-insensitive.
In "replace", $1 inserts a group.

User settings for the plugin:
- "options": array of { "key", "type", "title", "default", ... }, shown under the plugin in Settings → Plugins.
  type "toggle" (default true/false), "number" (with "min", "max"; shown as a slider), "text", "choice"
  (with "choices": [..]), "words" (the user types a comma-separated list; inserted as a ready regex alternation).
- Use a value anywhere as {{key}}: "blur": "{{strength}}" puts the number itself; inside text it is inserted as text.
- "if": "key" turns a rule on only when that option is on (toggle on, number not 0, text not empty); "!key" inverts.

Example:
{
  "id": "me.cleanfeed",
  "name": "Clean Feed",
  "author": "me",
  "version": "1.0",
  "description": "Hides spam and softens the glass",
  "options": [
    { "key": "words", "type": "words", "title": "Hide messages with words", "default": "casino, bet" },
    { "key": "glass", "type": "toggle", "title": "Clear glass", "default": true },
    { "key": "blur", "type": "number", "title": "Blur", "min": 0, "max": 30, "default": 12 }
  ],
  "settings": { "blur": "{{blur}}", "tint": 0.5 },
  "hideMessages": [ { "find": "(?iu){{words}}" } ],
  "incoming": [ { "find": "(?i)\\blol\\b", "replace": "😂", "if": "glass" } ],
  "strings": { "Chats": "Inbox" }
}
""".trim()

    /** Starting point for writing a plugin by hand (Settings → Plugins → Copy Template). */
    val TEMPLATE = """
{
  "id": "my.plugin",
  "name": "My Plugin",
  "author": "me",
  "version": "1.0",
  "description": "What it does",
  "options": [ { "key": "blur", "type": "number", "title": "Blur", "min": 0, "max": 30, "default": 14 } ],
  "settings": { "blur": "{{blur}}", "accent": 2 },
  "strings": { "Chats": "Messages" },
  "outgoing": [ { "find": "\\bthx\\b", "replace": "thanks" } ],
  "hideMessages": [ "(?i)#ad\\b" ]
}
""".trim()

    private val BUILT_IN = listOf(
        """{"id":"lg.noads","name":"No Ads","author":"LiquidGram","description":"Hides incoming messages marked as ads.",
           "options":[
             {"key":"marks","type":"toggle","title":"Ad marks (#реклама, #ad, «на правах рекламы»)","default":true},
             {"key":"erid","type":"toggle","title":"Ad tokens (erid)","default":true},
             {"key":"words","type":"words","title":"Also hide messages with words","default":""}],
           "hideMessages":[
             {"find":"(?iu)#реклама|#ad\\b|на правах рекламы","if":"marks"},
             {"find":"(?iu)\\berid\\b","if":"erid"},
             {"find":"(?iu){{words}}","if":"words"}]}""",
    )
}
