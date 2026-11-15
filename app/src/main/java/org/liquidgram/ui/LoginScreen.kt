package org.liquidgram.ui

import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import org.liquidgram.data.AuthState
import org.liquidgram.data.TelegramRepository
import org.liquidgram.glass.LiquidButton
import org.liquidgram.glass.LiquidToggle

/** iOS-style login: phone (with country) → code → two-step password. */
@Composable
fun LoginScreen(repo: TelegramRepository) {
    val state by repo.auth.collectAsState()
    val error by repo.authError.collectAsState()
    val busy by repo.authBusy.collectAsState()
    val connection by repo.connection.collectAsState()
    val context = LocalContext.current
    // Glass controls on this plain page sample this flat layer underneath (they can't sample the page itself).
    val bg = rememberLayerBackdrop()
    var picking by remember { mutableStateOf(false) }
    var country by remember { mutableStateOf(defaultCountry(context)) }

    Box(Modifier.fillMaxSize().background(Tg.Bg)) {
        Box(Modifier.fillMaxSize().layerBackdrop(bg).trackBackdropPosition(bg).background(Tg.Bg))
        AnimatedContent(
            state,
            contentKey = { it::class },
            transitionSpec = {
                (slideInHorizontally(spring(1f, 400f)) { it } + fadeIn()) togetherWith (slideOutHorizontally(spring(1f, 400f)) { -it / 3 } + fadeOut())
            },
            label = "auth",
        ) { s ->
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()) {
                when (s) {
                    AuthState.WaitPhone -> PhoneStep(repo, country, { picking = true }, { country = it }, busy, bg)
                    is AuthState.WaitCode -> CodeStep(repo, s, busy, bg)
                    is AuthState.WaitPassword -> PasswordStep(repo, s.hint, busy, bg)
                    is AuthState.Unsupported -> Header("✈️", "Telegram", s.what)
                    else -> Header("✈️", "Telegram", "Connecting…")
                }
                error?.let {
                    T(it, 14.sp, Tg.Red, maxLines = 3, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }
                connection?.let {
                    T(it, 14.sp, Tg.Secondary, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
                }
            }
        }
        // Close: on the first step leaves the app; later it starts over with another number.
        GlassCircle(
            bg,
            { if (state == AuthState.WaitPhone) (context as? Activity)?.finish() else repo.logOut() },
            modifier = Modifier.statusBarsPadding().padding(16.dp),
        ) { Icon(Icons.Rounded.Close, Tg.Text, 22.dp) }

        CountryPicker(picking, { picking = false }) { country = it; picking = false }
    }
}

@Composable
private fun Header(emoji: String, title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 64.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(emoji, style = TextStyle(fontSize = 80.sp))
        Spacer(Modifier.height(14.dp))
        T(title, 28.sp, Tg.Text, FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        BasicText(subtitle, style = TextStyle(color = Tg.Secondary, fontSize = 16.sp * Look.textScale, textAlign = TextAlign.Center))
    }
}

@Composable
private fun Separator(start: Int = 0) = Box(Modifier.padding(start = start.dp).fillMaxWidth().height(0.5.dp).background(Tg.Separator))

@Composable
private fun ColumnScope.PhoneStep(
    repo: TelegramRepository, country: Country, onPick: () -> Unit, onCountry: (Country) -> Unit, busy: Boolean, bg: LayerBackdrop,
) {
    var code by rememberSaveable { mutableStateOf(country.code) }
    var number by rememberSaveable { mutableStateOf("") }
    var sync by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(country) { if (countryForCode(code, country) != country) code = country.code }

    Header("☎️", "Your Phone", "Enter your phone number")
    Spacer(Modifier.height(28.dp))
    Separator()
    Row(Modifier.fillMaxWidth().clickable(onClick = onPick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(country.flag, style = TextStyle(fontSize = 20.sp))
        Spacer(Modifier.width(10.dp))
        T(country.name, 17.sp, Tg.Accent, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, Tg.Muted, 22.dp)
    }
    Separator(20)
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        T("+", 20.sp, Tg.Text)
        BasicTextField(
            code,
            { v -> code = v.filter(Char::isDigit).take(4); countryForCode(code, country)?.let(onCountry) },
            Modifier.width(52.dp).padding(vertical = 14.dp),
            singleLine = true,
            textStyle = TextStyle(color = Tg.Text, fontSize = 20.sp),
            cursorBrush = SolidColor(Tg.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        Box(Modifier.width(0.5.dp).height(28.dp).background(Tg.Separator))
        Box(Modifier.weight(1f).padding(start = 14.dp, top = 14.dp, bottom = 14.dp)) {
            if (number.isEmpty()) T("000 000 0000", 20.sp, Tg.Muted)
            BasicTextField(
                number, { v -> number = v.filter { it.isDigit() || it == ' ' }.take(18) }, Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(color = Tg.Text, fontSize = 20.sp),
                cursorBrush = SolidColor(Tg.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
        }
    }
    Separator()
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        T("Sync Contacts", 17.sp, Tg.Text, modifier = Modifier.weight(1f))
        LiquidToggle({ sync }, { sync = it }, bg)
    }
    Spacer(Modifier.weight(1f))
    val digits = number.filter(Char::isDigit)
    ContinueButton(enabled = code.isNotEmpty() && digits.length >= 6, busy = busy, bg = bg) { repo.submitAuth("+$code$digits") }
}

@Composable
private fun ColumnScope.CodeStep(repo: TelegramRepository, s: AuthState.WaitCode, busy: Boolean, bg: LayerBackdrop) {
    var code by rememberSaveable { mutableStateOf("") }
    Header("💬", "Enter Code", s.sentTo)
    Spacer(Modifier.height(32.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (code.isEmpty()) T("—".repeat(s.length.coerceIn(4, 8)), 30.sp, Tg.Muted)
        BasicTextField(
            code,
            { v ->
                code = v.filter(Char::isDigit).take(s.length.coerceAtLeast(4))
                // Telegram codes have a known length: send as soon as it's complete.
                if (code.length == s.length && !busy) repo.submitAuth(code)
            },
            Modifier.width(220.dp),
            singleLine = true,
            textStyle = TextStyle(color = Tg.Text, fontSize = 30.sp, letterSpacing = 10.sp, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(Tg.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
    }
    Spacer(Modifier.weight(1f))
    ContinueButton(enabled = code.length >= 4, busy = busy, bg = bg) { repo.submitAuth(code) }
}

@Composable
private fun ColumnScope.PasswordStep(repo: TelegramRepository, hint: String, busy: Boolean, bg: LayerBackdrop) {
    var password by rememberSaveable { mutableStateOf("") }
    Header("🔐", "Two-Step Verification", "Enter your cloud password" + if (hint.isNotEmpty()) ".\nHint: $hint" else ".")
    Spacer(Modifier.height(28.dp))
    Separator()
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        if (password.isEmpty()) T("Password", 20.sp, Tg.Muted)
        BasicTextField(
            password, { password = it }, Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(color = Tg.Text, fontSize = 20.sp),
            cursorBrush = SolidColor(Tg.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
    }
    Separator()
    Spacer(Modifier.weight(1f))
    ContinueButton(enabled = password.isNotEmpty(), busy = busy, bg = bg) { repo.submitAuth(password) }
}

/** Grey until the input is complete, then a blue glass button; shows a spinner while the request runs. */
@Composable
private fun ContinueButton(enabled: Boolean, busy: Boolean, bg: LayerBackdrop, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(16.dp)) {
        if (enabled) LiquidButton({ if (!busy) onClick() }, bg, Modifier.fillMaxWidth(), tint = Tg.Accent, height = 54.dp) {
            if (busy) Spinner(Color.White) else T("Continue", 18.sp, Color.White, FontWeight.SemiBold)
        } else Box(Modifier.fillMaxWidth().height(54.dp).background(Tg.Field, RoundedCornerShape(27.dp)), contentAlignment = Alignment.Center) {
            T("Continue", 18.sp, Tg.Muted, FontWeight.SemiBold)
        }
    }
}

/** Full-screen country list with search, sliding up over the login screen. */
@Composable
private fun CountryPicker(open: Boolean, onClose: () -> Unit, onPick: (Country) -> Unit) {
    BackHandler(open, onClose)
    AnimatedVisibility(open, enter = slideInVertically(spring(1f, 400f)) { it } + fadeIn(), exit = slideOutVertically(spring(1f, 500f)) { it } + fadeOut()) {
        var query by remember { mutableStateOf("") }
        val shown = Countries.filter { query.isBlank() || it.name.contains(query, true) || it.code.startsWith(query.trimStart('+')) }
        Column(Modifier.fillMaxSize().background(Tg.Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) {
                T("Country", 17.sp, Tg.Text, FontWeight.SemiBold, Modifier.align(Alignment.Center))
                Box(Modifier.align(Alignment.CenterEnd).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClose).padding(8.dp)) {
                    T("Cancel", 17.sp, Tg.Accent)
                }
            }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().height(40.dp)
                    .background(Tg.Field, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, Tg.Secondary, 20.dp)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) T("Search", 17.sp, Tg.Secondary)
                    BasicTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = TextStyle(color = Tg.Text, fontSize = 17.sp), cursorBrush = SolidColor(Tg.Accent))
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.iso }) { c ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(c) }.padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(c.flag, style = TextStyle(fontSize = 22.sp))
                        Spacer(Modifier.width(14.dp))
                        T(c.name, 17.sp, Tg.Text, modifier = Modifier.weight(1f))
                        T("+${c.code}", 17.sp, Tg.Secondary)
                    }
                    Separator(56)
                }
            }
        }
    }
}
