# LiquidGram

A Telegram client for Android in the iOS 26 style, built on Liquid Glass (Kyant0/AndroidLiquidGlass 2.0.1, the `backdrop` module).

## Build

```
gradlew :app:assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`. The SDK lives in `C:\Users\denis\Android\Sdk` (path set in `local.properties`).
Full refraction (lens) needs Android 13+; on older versions the glass falls back to blur only.

## What's inside

| File | What it does |
|---|---|
| `backdrop/` | Liquid Glass library from the archive, trimmed to Android only |
| `app/.../glass/` | Bottom tab bar, glass button and press physics, taken from the library demo |
| `ui/Kit.kt` | Colors, avatars, `GlassCircle`/`GlassPill`, `LiquidMenu` (port of the SwiftUI `Menu`: the button morphs into the panel) |
| `ui/HomeScreen.kt` | Chats (stories, folders with a sliding underline), Contacts, Calls, Settings + glass tab bar |
| `ui/ChatScreen.kt` | Chat: wallpaper, bubbles, glass header and input bar, send button with a spring |
| `ui/ProfileScreen.kt` | Profile: large header with parallax, glass buttons, cards |
| `data/Telegram.kt` | `TelegramRepository`, the single interface the UI talks to |
| `data/DemoTelegram.kt` | Demo data from the screenshots (replies "typing…" after you send) |

## Real Telegram (TDLib)

TDLib is built from the official source (`build-tdlib.sh`, run in WSL Ubuntu) and already included: `app/src/main/jniLibs` + `org/drinkless/tdlib`.

To log in to your account:
1. Get `api_id` / `api_hash` at https://my.telegram.org → API development tools.
2. Add to `local.properties`:
   ```
   telegram.apiId=12345
   telegram.apiHash=0123456789abcdef0123456789abcdef
   ```
3. `gradlew :app:assembleDebug` and install. Login: phone → code → 2FA password.

Without keys the app runs on demo data.

Works: chat list, folders, stories, search, messages (text, photos, stickers as emoji), sending text and photos,
older history, read receipts, "typing…", copy/delete messages, mute, block, delete chat, profile with birthday and personal channel, calls history, log out.
Not yet: voice messages, calls, push notifications (needs FCM), stickers/GIFs/videos as media, replies, editing.
