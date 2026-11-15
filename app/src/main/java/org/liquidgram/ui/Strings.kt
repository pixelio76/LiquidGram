package org.liquidgram.ui

/**
 * UI language. Every text goes through [T], which looks it up here, so screens keep plain English literals.
 * shortcut: lookup by the English text itself, so a chat named exactly like a UI word ("Settings") is translated
 * too; move to keyed string resources if that ever matters.
 */
object Strings {
    val languages = listOf("English", "Русский")

    private val ru = mapOf(
        // tabs, pages, headers
        "Contacts" to "Контакты", "Calls" to "Звонки", "Chats" to "Чаты", "Settings" to "Настройки",
        "Edit" to "Изм.", "Sort" to "Сорт.", "Search" to "Поиск", "All" to "Все", "Unread" to "Непрочитанные",
        "All Chats" to "Все чаты", "Invite Friends" to "Пригласить друзей", "Done" to "Готово", "Cancel" to "Отмена",
        "Next" to "Далее", "Create" to "Создать", "Save" to "Сохранить", "Back" to "Назад",
        // chat list / chat
        "Saved Messages" to "Избранное", "My Story" to "Моя история", "Message" to "Сообщение",
        "Unread Messages" to "Непрочитанные сообщения", "Reply" to "Ответить", "Copy" to "Копировать",
        "Delete" to "Удалить", "Edit Message" to "Редактирование", "Pin" to "Закрепить", "Unpin" to "Открепить",
        "Mute" to "Без звука", "Unmute" to "Со звуком", "Mark as Read" to "Прочитано", "You" to "Вы",
        "online" to "в сети", "typing…" to "печатает…", "last seen recently" to "был(а) недавно",
        "last seen within a week" to "был(а) на этой неделе", "last seen within a month" to "был(а) в этом месяце",
        "last seen a long time ago" to "был(а) давно", "bot" to "бот", "group" to "группа", "channel" to "канал",
        "‹ Slide to cancel" to "‹ Влево — отмена", "Hold the mic to record" to "Удерживайте микрофон для записи",
        "Microphone access is needed for voice messages" to "Для голосовых нужен доступ к микрофону",
        "Photo" to "Фото", "Video" to "Видео", "Sticker" to "Стикер", "Emoji" to "Эмодзи", "Stickers" to "Стикеры",
        "joined the group" to "вступил(а) в группу", "pinned a message" to "закрепил(а) сообщение",
        "Yesterday" to "Вчера", "Incoming" to "Входящий", "Outgoing" to "Исходящий",
        // menus
        "New Group" to "Создать группу", "New Contact" to "Новый контакт", "New Channel" to "Создать канал",
        "New Secret Chat" to "Новый секретный чат", "Share Contact" to "Поделиться контактом", "Auto-Delete" to "Автоудаление",
        "Block User" to "Заблокировать", "Delete Chat" to "Удалить чат",
        // profile
        "Hides incoming messages marked as ads." to "Скрывает входящие сообщения с пометкой рекламы.",
        "Ad marks (#реклама, #ad, «на правах рекламы»)" to "Пометки (#реклама, #ad, «на правах рекламы»)", "Ad tokens (erid)" to "Рекламные токены (erid)",
        "Also hide messages with words" to "Скрывать также сообщения со словами", "Words, comma-separated" to "Слова через запятую",
        "Copy Instructions for AI" to "Скопировать инструкцию для ИИ", "Instructions copied" to "Инструкция скопирована",
        "Hide Settings" to "Скрыть настройки",
        "Paste the instructions into a chat with any AI and describe the plugin you want: it replies with a ready .json file. Save it and install it here." to "Вставьте инструкцию в чат с любым ИИ и опишите, какой плагин нужен: он ответит готовым .json-файлом. Сохраните его и установите здесь.",
        "Send to…" to "Отправить в…", "Blur and opacity change all the glass: buttons, panels, the tab bar, menus and switches." to "Размытие и плотность меняют всё стекло: кнопки, плашки, таб-бар, меню и переключатели.", "GLOBAL SEARCH" to "ГЛОБАЛЬНЫЙ ПОИСК", "Join Channel" to "Подписаться", "Join Group" to "Вступить в группу", "Plugins" to "Плагины", "Installed" to "Установленные", "Install from File" to "Установить из файла", "Copy Plugin Template" to "Скопировать шаблон плагина",
        "Texts" to "Тексты", "Sending" to "Отправка", "Chat List" to "Список чатов", "Plugin installed" to "Плагин установлен", "Template copied" to "Шаблон скопирован",
        "Can't read this file" to "Не удалось прочитать файл", "A built-in plugin already has this id" to "Такой id уже у встроенного плагина",
        "Plugins change LiquidGram on this device only: look, interface texts, how messages are sent and shown. They are JSON files and run no code." to "Плагины меняют LiquidGram только на этом устройстве: вид, тексты интерфейса, отправку и показ сообщений. Это JSON-файлы, они не запускают код.",
        "A plugin is a .json file with an id, a name and any of: settings, strings, outgoing, incoming, hideMessages, hideChats. Start from the template." to "Плагин — это .json-файл с id, name и любыми из полей: settings, strings, outgoing, incoming, hideMessages, hideChats. Начните с шаблона.",
        "Crystal Glass" to "Кристальное стекло", "Stronger refraction, more blur and clearer glass everywhere." to "Сильнее преломление, больше размытия и прозрачнее стекло везде.",
        "Polite Mode" to "Вежливый режим", "Expands short words in what you send: спс → спасибо, пж → пожалуйста, thx → thanks." to "Раскрывает сокращения в отправленном: спс → спасибо, пж → пожалуйста, thx → thanks.",
        "No Ads" to "Без рекламы", "Hides incoming messages marked as ads (#реклама, #ad, «на правах рекламы»)." to "Скрывает входящие сообщения с пометкой рекламы (#реклама, #ad, «на правах рекламы»).",
        "Retro Labels" to "Ретро-подписи", "Old-school names for the tabs." to "Старомодные названия вкладок.",
        "call" to "звонок", "This button" to "Эта кнопка", "Demo Bot" to "Демо-бот", "video" to "видео", "voice chat" to "видеочат", "live stream" to "трансляция", "share" to "поделиться", "Invite links" to "Ссылки-приглашения", "Channel" to "Канал", "mute" to "звук", "unmute" to "звук", "search" to "поиск", "more" to "ещё",
        "mobile" to "мобильный", "username" to "имя пользователя", "date of birth" to "день рождения", "hidden" to "скрыт",
        // settings
        "My Profile" to "Мой профиль", "Recent Calls" to "Недавние звонки", "Devices" to "Устройства",
        "Chat Folders" to "Папки с чатами", "Notifications and Sounds" to "Уведомления и звуки",
        "Privacy and Security" to "Конфиденциальность", "Data and Storage" to "Данные и память",
        "Appearance" to "Оформление", "Power Saving" to "Энергосбережение", "Quick Reactions" to "Быстрые реакции", "Liquid refraction" to "Жидкое преломление", "Channel name" to "Название канала", "Edit Channel" to "Редактировать канал", "Edit Group" to "Редактировать группу", "Group name" to "Название группы", "Description" to "Описание", "You can provide an optional description for your channel." to "Можно добавить описание канала.", "You can provide an optional description for your group." to "Можно добавить описание группы.", "Glass opacity" to "Плотность стекла", "Glass blur" to "Размытие стекла", "Reduce transparency" to "Понизить прозрачность", "Reactions in Menu" to "Реакции в меню", "Double Tap" to "Двойное нажатие", "Preview" to "Предпросмотр", "Off" to "Выкл.",
        "Pick up to 7, in the order they appear above the message menu. Tap again to remove." to "Выберите до 7 реакций в том порядке, в котором они появятся над меню сообщения. Нажмите ещё раз, чтобы убрать.",
        "Double tap a message to react with this emoji." to "Двойное нажатие на сообщение ставит эту реакцию.",
        "Forward" to "Переслать", "Forward to…" to "Переслать в…", "Pinned Message" to "Закреплённое сообщение", "Forwarded from" to "Переслано от",
        "Save to Gallery" to "Сохранить в галерею", "Share" to "Поделиться", "Saved to Gallery" to "Сохранено в галерею", "Show in Chat" to "Показать в чате", "Language" to "Язык", "Log Out" to "Выйти",
        // appearance
        "Theme" to "Тема", "Light" to "Светлая", "Dark" to "Тёмная", "Follow device theme" to "Как в системе",
        "Accent colour" to "Цвет акцента", "Chat Background" to "Фон чата", "Doodles" to "Узоры", "Plain" to "Однотонный",
        "Text size" to "Размер текста", "Bubble corners" to "Скругление сообщений", "Liquid Glass" to "Liquid Glass",
        "Blur" to "Размытие", "Refraction" to "Преломление", "Tint" to "Плотность стекла", "Chromatic aberration" to "Хроматическая аберрация",
        "Depth effect" to "Эффект глубины", "Reset to Defaults" to "Сбросить настройки", "Tap to switch" to "Нажмите, чтобы сменить",
        "Messages" to "Сообщения", "Own message tint" to "Оттенок своих сообщений", "Pattern intensity" to "Яркость узора",
        "Pattern size" to "Размер узора", "Avatar roundness" to "Скругление аватарок", "Show stories" to "Показывать истории",
        "Chat list" to "Список чатов", "Compact chat list" to "Компактный список", "Message preview lines" to "Строк в превью",
        "Wallpaper" to "Обои", "Interface" to "Интерфейс", "Animations" to "Анимации", "Reduce motion" to "Меньше движения",
        // profile editing
        "Edit Profile" to "Редактировать профиль", "First name" to "Имя", "Last name" to "Фамилия", "Bio" to "О себе",
        "Username" to "Имя пользователя", "Set New Photo" to "Выбрать фото",
        "Any details such as age, occupation or city." to "Например, возраст, род занятий или город.",
        "Others can find you by this username." to "По этому имени вас смогут найти.",
        // sessions
        "This Device" to "Это устройство", "Active Sessions" to "Активные сеансы", "Terminate All Other Sessions" to "Завершить другие сеансы",
        "Terminate" to "Завершить", "No other sessions" to "Других сеансов нет",
        // new chats
        "Group Name" to "Название группы", "Channel Name" to "Название канала", "Description" to "Описание",
        "Phone number" to "Номер телефона", "Choose members" to "Выберите участников", "Choose a contact" to "Выберите контакт",
        "No contacts yet" to "Контактов пока нет", "Secret chats are end-to-end encrypted and live only on this device." to
            "Секретные чаты зашифрованы сквозным шифрованием и хранятся только на этом устройстве.",
        "You can provide an optional description for your channel." to "Можно добавить описание канала.",
        // login
        "Your Phone" to "Ваш телефон", "Country" to "Страна", "Continue" to "Продолжить", "Enter Code" to "Введите код",
        "Password" to "Пароль", "Two-Step Verification" to "Двухэтапная аутентификация", "Sync Contacts" to "Синхронизировать контакты",
        "Enter your phone number" to "Введите номер телефона", "Enter your cloud password" to "Введите облачный пароль",
        "Connecting…" to "Соединение…", "Connecting to Telegram…" to "Подключение к Telegram…", "Updating…" to "Обновление…",
        "Waiting for network…" to "Ожидание сети…", "Connecting to proxy…" to "Подключение к прокси…",
        // toasts
        "Posting stories" to "Публикация историй", "Sorting" to "Сортировка", "Invites" to "Приглашения", "Editing calls" to "Редактирование звонков",
        "Editing contacts" to "Редактирование контактов", "Search in chat" to "Поиск в чате", "Video calls" to "Видеозвонки",
        "Auto-delete" to "Автоудаление", "Secret chats" to "Секретные чаты",
        "Private Chats" to "Личные чаты", "Group Chats" to "Группы", "Channels" to "Каналы",
        "Show Notifications" to "Показывать уведомления", "Message Preview" to "Предпросмотр сообщений",
        "Notifications arrive while LiquidGram is running; push delivery needs Firebase and isn't set up yet." to
            "Уведомления приходят, пока LiquidGram запущен; push-уведомлениям нужен Firebase, он пока не подключён.",
        "Blocked Users" to "Заблокированные", "On" to "Вкл.", "Off" to "Выкл.", "Privacy" to "Конфиденциальность",
        "Phone Number" to "Номер телефона", "Last Seen & Online" to "Время захода", "Profile Photos" to "Фотографии профиля",
        "Forwarded Messages" to "Пересылка сообщений", "Groups & Channels" to "Группы и каналы",
        "Everybody" to "Все", "My Contacts" to "Мои контакты", "Nobody" to "Никто",
        "Storage Usage" to "Использование памяти", "Cache" to "Кэш", "Clear Cache" to "Очистить кэш",
        "Automatic Media Download" to "Автозагрузка медиа", "Photos" to "Фото", "App Update" to "Обновление приложения", "Update available" to "Доступно обновление", "Update" to "Обновить", "Later" to "Позже", "Downloading…" to "Загрузка…", "Download failed" to "Не удалось скачать", "You have the latest version" to "У вас последняя версия", "Couldn't check for updates" to "Не удалось проверить обновления", "speaker" to "динамик", "mute call" to "выкл. звук", "end" to "завершить", "Decline" to "Отклонить", "Accept" to "Принять", "Speaker" to "Динамик", "Requesting…" to "Запрос…", "Ringing…" to "Вызов…", "Telegram Audio Call" to "Аудиозвонок Telegram", "Telegram Video Call" to "Видеозвонок Telegram", "Connecting…" to "Соединение…", "Call Ended" to "Звонок завершён", "Call Declined" to "Звонок отклонён", "No Answer" to "Нет ответа", "Call Failed" to "Не удалось позвонить", "Voice isn't supported yet" to "Голос пока не поддерживается", "Files" to "Файлы", "Media" to "Медиа", "Links" to "Ссылки", "No media yet" to "Медиа пока нет", "No files yet" to "Файлов пока нет", "No links yet" to "Ссылок пока нет",
        "Media that isn't downloaded automatically loads when you tap it." to "Медиа без автозагрузки загружается по нажатию.",
        "Loop Animated Stickers" to "Зацикливать анимированные стикеры", "Glass Refraction" to "Преломление стекла",
        "Turning these off saves battery: stickers play once, glass keeps its blur but stops bending light." to
            "Отключение экономит заряд: стикеры проигрываются один раз, стекло остаётся размытым, но без преломления.",
        "Everything in theme colour" to "Всё в цвете темы", "Emoji font" to "Шрифт эмодзи", "System" to "Системный",
        "Choose file…" to "Выбрать файл…", "Photo or Video" to "Фото или видео", "File" to "Файл",
        "Search messages" to "Поиск сообщений", "No results" to "Ничего не найдено", "Search for stickers" to "Поиск стикеров",
        "Video message" to "Видеосообщение", "Release to send" to "Отпустите, чтобы отправить",
        "Tap to download" to "Нажмите, чтобы загрузить", "Camera access is needed for video messages" to "Для видеосообщений нужен доступ к камере",
        "Custom font" to "Свой шрифт", "Avatar glass" to "Блик и тень аватарок",
        "Developer menu in" to "До меню разработчика:", "Developer" to "Разработчик", "Test account" to "Тестовая учётка",
        "Demo account" to "Демо-учётка", "Build" to "Сборка", "Version" to "Версия", "configured" to "настроен", "demo only" to "только демо",
        "The offline test account used while building LiquidGram: sample chats, no network. It lasts until the app is restarted; your own account isn't touched." to
            "Офлайн-учётка, на которой тестировался LiquidGram: примерные чаты, без сети. Действует до перезапуска приложения; твой аккаунт не трогается.",
        "Share Link" to "Поделиться ссылкой", "Leave Channel" to "Покинуть канал", "Leave Group" to "Покинуть группу",
        "bio" to "о себе", "description" to "описание", "link" to "ссылка",
        "Video Messages" to "Видеосообщения", "Start with" to "Начинать с камеры", "Front camera" to "Фронтальная", "Back camera" to "Основная",
    )

    /** Leading words of composed texts ("last seen 10:42", "Reply to Anna", "edited 9:01"). */
    private val ruPrefixes = listOf(
        "last seen at " to "был(а) в ", "last seen " to "был(а) ", "Reply to yourself" to "Ответ себе", "Reply to " to "Ответ ",
        "edited " to "изм. ", "🖼 Photo" to "🖼 Фото", "🎤 Voice message" to "🎤 Голосовое сообщение",
    )

    /** English texts that have a translation: the interface texts plugins can rename. */
    val keys: Set<String> get() = ru.keys

    fun tr(text: String): String {
        Plugins.text(text)?.let { return it }
        if (Look.language != 1) return text
        ru[text]?.let { return it }
        for ((en, r) in ruPrefixes) if (text.startsWith(en)) return r + text.removePrefix(en)
        if (text.endsWith(" Sticker")) return text.removeSuffix(" Sticker") + " Стикер"
        if (text.endsWith(" members")) return text.removeSuffix(" members") + " участников"
        if (text.endsWith(" subscribers")) return text.removeSuffix(" subscribers") + " подписчиков"
        return text
    }
}
