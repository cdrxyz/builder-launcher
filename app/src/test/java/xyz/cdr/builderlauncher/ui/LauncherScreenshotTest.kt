package xyz.cdr.builderlauncher.ui

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.data.KeyboardMode
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.podcasts.HomePodcastMark
import xyz.cdr.builderlauncher.podcasts.Podcasts
import xyz.cdr.builderlauncher.stocks.StockCagr
import xyz.cdr.builderlauncher.stocks.StockPoint
import xyz.cdr.builderlauncher.stocks.StockQuote
import xyz.cdr.builderlauncher.stocks.Stocks
import xyz.cdr.builderlauncher.ui.theme.BuilderTheme
import xyz.cdr.builderlauncher.ui.theme.accentColor
import xyz.cdr.builderlauncher.usage.PinUsageMark
import xyz.cdr.builderlauncher.data.UiTheme

class LauncherScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar",
        showSystemUi = false,
    )

    @Test
    fun home() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "summarize this PR",
                    prompt = "?",
                    todos = listOf("buy milk", "ship builder-launcher CI", "call dentist"),
                    event = "dentist · 09:00",
                )
            }
        }
    }

    @Test
    fun homePlaying() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "summarize this PR",
                    prompt = "?",
                    todos = listOf("buy milk", "ship builder-launcher CI", "call dentist"),
                    event = "dentist · 09:00",
                    podcastMark = HomePodcastMark.PAUSE,
                )
            }
        }
    }

    @Test
    fun homeTimer() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "4:12",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "summarize this PR",
                    prompt = "?",
                    todos = listOf("buy milk", "ship builder-launcher CI", "call dentist"),
                    analog = false,
                )
            }
        }
    }

    @Test
    fun homeTicker() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "summarize this PR",
                    prompt = "?",
                    todos = listOf("buy milk", "ship builder-launcher CI", "call dentist"),
                    ticker = "AAPL",
                    tickerChange = "-2.51%",
                    tickerUp = false,
                )
            }
        }
    }

    @Test
    fun homeCalculator() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "15*37",
                    prompt = ">",
                    todos = listOf("buy milk", "ship builder-launcher CI", "call dentist"),
                )
            }
        }
    }

    @Test
    fun homeCalculatorAsk() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "15*37",
                    prompt = "?",
                    todos = listOf("buy milk", "ship builder-launcher CI", "call dentist"),
                )
            }
        }
    }

    @Test
    fun homeAskSingleLine() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "weather tomorrow",
                    prompt = "?",
                    todos = listOf("buy milk", "ship builder-launcher CI", "call dentist"),
                )
            }
        }
    }

    @Test
    fun homeAskWrap() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "write a longer follow-up that wraps instead of scrolling sideways on this screen",
                    prompt = "?",
                    todos = listOf("buy milk", "ship builder-launcher CI", "call dentist"),
                )
            }
        }
    }

    @Test
    fun homeTodoWrap() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "pack a charger, review the PR after lunch, and pick up oat milk on the way home",
                    prompt = "-",
                    todos = listOf("buy milk", "ship builder-launcher CI", "call dentist"),
                )
            }
        }
    }

    @Test
    fun homeTodoSingleLine() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "buy milk",
                    prompt = "-",
                    todos = listOf("buy milk", "ship builder-launcher CI", "call dentist"),
                )
            }
        }
    }

    @Test
    fun todos() {
        paparazzi.snapshot {
            BuilderTheme {
                TodosChrome(
                    todos = listOf(
                        "buy milk",
                        "ship builder-launcher CI",
                        "call dentist",
                        "pack charger",
                        "review PR after lunch",
                    ),
                    doneTodos = listOf("mail keys"),
                    input = "",
                )
            }
        }
    }

    @Test
    fun todosEdit() {
        paparazzi.snapshot {
            BuilderTheme {
                TodosChrome(
                    todos = listOf(
                        "buy milk",
                        "ship builder-launcher CI",
                        "call dentist",
                    ),
                    input = "buy oat milk, pack a charger, and review the PR after lunch",
                    confirm = true,
                )
            }
        }
    }

    @Test
    fun homeCommandMenu() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "",
                    todos = listOf("buy milk", "ship builder-launcher CI"),
                    pins = listOf("Termux", "Signal"),
                    commandsOpen = true,
                )
            }
        }
    }

    @Test
    fun homeSlashCommands() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "",
                    prompt = "/",
                    todos = listOf("buy milk", "ship builder-launcher CI"),
                    pins = listOf("Termux", "Signal"),
                    slashOpen = true,
                )
            }
        }
    }

    @Test
    fun notes() {
        paparazzi.snapshot {
            BuilderTheme {
                NotesChrome(
                    rows = listOf(
                        NoteListRow("Ship notes", "7 Sep 15:42"),
                        NoteListRow("PR review", "6 Sep 09:18"),
                    ),
                )
            }
        }
    }

    @Test
    fun noteEditor() {
        paparazzi.snapshot {
            BuilderTheme {
                NoteEditorChrome(
                    body = "# Ship notes\n\nWrite the markdown here.\n\n- first\n- second",
                )
            }
        }
    }

    @Test
    fun noteEditorH1() {
        paparazzi.snapshot {
            BuilderTheme {
                NoteEditorChrome(body = "# ")
            }
        }
    }

    @Test
    fun homePinned() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "",
                    todos = listOf("buy milk", "ship builder-launcher CI"),
                    pins = listOf("Phone", "Messages", "Maps", "Camera"),
                )
            }
        }
    }

    @Test
    fun homePinnedIcons() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "",
                    todos = listOf("buy milk", "ship builder-launcher CI"),
                    pins = listOf("Phone", "Messages", "Maps", "Camera"),
                    appIcons = true,
                )
            }
        }
    }

    @Test
    fun homePinnedUsage() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "",
                    todos = listOf("buy milk", "ship builder-launcher CI"),
                    pins = listOf("Phone", "Messages", "Maps", "Camera"),
                    pinUsage = listOf(
                        PinUsageMark("12m", "7%", false),
                        PinUsageMark("45m", "25%", false),
                        PinUsageMark("30m", "17%", true),
                        PinUsageMark("8m", "4%", true),
                    ),
                )
            }
        }
    }

    @Test
    fun homePinnedIconsUsage() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "",
                    todos = listOf("buy milk", "ship builder-launcher CI"),
                    pins = listOf("Phone", "Messages", "Maps", "Camera"),
                    appIcons = true,
                    pinUsage = listOf(
                        PinUsageMark("12m", "7%", false),
                        PinUsageMark("45m", "25%", false),
                        PinUsageMark("30m", "17%", true),
                        PinUsageMark("8m", "4%", true),
                    ),
                )
            }
        }
    }

    @Test
    fun homeNotesShortcut() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "notes",
                    todos = listOf("buy milk", "ship builder-launcher CI"),
                    apps = listOf("… all notes >", "Notes", "… all apps >"),
                )
            }
        }
    }

    @Test
    fun homeAppsFilter() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "c",
                    todos = listOf("buy milk", "ship builder-launcher CI"),
                    apps = listOf("Calendar", "Camera", "Clock", "Contacts", "… all apps >"),
                )
            }
        }
    }

    @Test
    fun allApps() {
        paparazzi.snapshot {
            BuilderTheme {
                AllAppsChrome(
                    rows = listOf(
                        AppListRow("Calendar"),
                        AppListRow("Camera"),
                        AppListRow("Clock"),
                        AppListRow("Contacts"),
                        AppListRow("Maps"),
                        AppListRow("Messages"),
                        AppListRow("Phone"),
                        AppListRow("Settings"),
                    ),
                )
            }
        }
    }

    @Test
    fun allAppsFilter() {
        paparazzi.snapshot {
            BuilderTheme {
                AllAppsChrome(
                    rows = listOf(
                        AppListRow("Calendar"),
                        AppListRow("Camera"),
                        AppListRow("Clock"),
                        AppListRow("Contacts"),
                    ),
                    input = "c",
                )
            }
        }
    }

    @Test
    fun homeStocksShortcut() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "stocks",
                    todos = listOf("buy milk", "ship builder-launcher CI"),
                    apps = listOf("… all stocks >"),
                )
            }
        }
    }

    @Test
    fun homePodcastsShortcut() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "podcasts",
                    todos = listOf("buy milk", "ship builder-launcher CI"),
                    apps = listOf("… all podcasts >"),
                )
            }
        }
    }

    @Test
    fun podcasts() {
        paparazzi.snapshot {
            BuilderTheme {
                PodcastsChrome(
                    nowPlayingTitle = "Playing analog with a title long enough to wrap past three lines on the home list so the rest is cut",
                    nowPlayingShow = "The Talk Show",
                    nowPlaying = true,
                    continueRows = listOf(
                        PodcastListRow(
                            "Playing analog with a title long enough to wrap past three lines on the home list so the rest is cut",
                            "The Talk Show",
                            "12:00 of 45:00",
                            highlight = true,
                            maxTitleLines = Podcasts.TITLE_LINES,
                            deletable = true,
                            downloadable = true,
                            downloaded = true,
                        ),
                    ),
                    newRows = listOf(
                        PodcastListRow(
                            "Newest",
                            "Accidental Tech Podcast with a show name long enough that it must stay on one line",
                            "1:02:03",
                            maxTitleLines = Podcasts.TITLE_LINES,
                            maxSubtitleLines = Podcasts.SHOW_LINES,
                            deletable = true,
                            downloadable = true,
                        ),
                        PodcastListRow("New two", "The Talk Show", "45:00", maxTitleLines = Podcasts.TITLE_LINES, maxSubtitleLines = Podcasts.SHOW_LINES, deletable = true, downloadable = true),
                    ),
                    shows = listOf(
                        PodcastListRow("Accidental Tech Podcast", "Marco Arment", deletable = true),
                        PodcastListRow("The Talk Show", "John Gruber", deletable = true),
                    ),
                )
            }
        }
    }

    @Test
    fun podcastsSearch() {
        paparazzi.snapshot {
            BuilderTheme {
                PodcastsChrome(
                    nowPlayingTitle = "Playing analog",
                    nowPlayingShow = "The Talk Show",
                    nowPlaying = true,
                    hits = listOf(
                        PodcastListRow("Accidental Tech Podcast", "Marco Arment", art = true),
                        PodcastListRow("The Talk Show", "John Gruber", art = true),
                    ),
                    input = "tech",
                )
            }
        }
    }

    @Test
    fun podcastsSettings() {
        paparazzi.snapshot {
            BuilderTheme {
                PodcastsSettingsChrome(cache = "5 GB", used = "1.2 GB", count = 2, speed = "1.4×", speedProgress = 0.2f, skipSilence = true)
            }
        }
    }

    @Test
    fun podcastShow() {
        paparazzi.snapshot {
            BuilderTheme {
                PodcastShowChrome(
                    show = "Accidental Tech Podcast",
                    author = "Marco Arment",
                    order = "oldest first",
                    episodes = listOf(
                        PodcastListRow(
                            "Episode 1: Hello with the full title shown on the show screen even when it is long",
                            "12:00 of 1:02:03",
                            meta = "15 Dec 2023",
                            metaBelow = true,
                            deletable = true,
                            downloadable = true,
                        ),
                        PodcastListRow(
                            "Episode 2",
                            "45:00",
                            meta = "1 Jan 2024",
                            metaBelow = true,
                            deletable = true,
                            downloadable = true,
                            dimmed = true,
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun podcastEpisode() {
        paparazzi.snapshot {
            BuilderTheme {
                PodcastEpisodeChrome(
                    show = "Accidental Tech Podcast",
                    title = "Episode 1: Hello",
                    position = "12:00 of 1:02:03",
                    playing = true,
                    downloaded = true,
                    progress = 0.19f,
                    speed = "1.4×",
                    notes = "0:00 Intro\n12:34 Deep cut\n1:02:03 Credits",
                )
            }
        }
    }

    @Test
    fun stocks() {
        paparazzi.snapshot {
            BuilderTheme {
                StocksChrome(
                    rows = listOf(
                        StockListRow("AAPL", "Apple Inc.", "$319.97", "-2.51%", up = false),
                        StockListRow("MSFT", "Microsoft Corporation", "$428.10", "+1.24%", up = true),
                    ),
                    input = "",
                )
            }
        }
    }

    @Test
    fun stocksSettings() {
        paparazzi.snapshot {
            BuilderTheme {
                StocksSettingsChrome(insert = xyz.cdr.builderlauncher.data.StockInsert.TOP, count = 2)
            }
        }
    }

    @Test
    fun stockDetail() {
        val points = listOf(
            328.0, 326.4, 324.1, 325.8, 323.0, 321.2, 322.5, 320.1, 319.97,
        ).mapIndexed { i, close -> StockPoint(time = i.toLong(), close = close) }
        paparazzi.snapshot {
            BuilderTheme {
                StockDetailChrome(
                    symbol = "AAPL",
                    name = "Apple Inc.",
                    price = "$319.97",
                    changeLine = "-8.24 (-2.51%)",
                    up = false,
                    extendedLine = "Pre-Market $318.55 -1.42 (-0.44%)",
                    extendedUp = false,
                    points = points,
                    range = xyz.cdr.builderlauncher.stocks.StockRange.D1,
                    stats = Stocks.quoteStats(
                        StockQuote(
                            symbol = "AAPL",
                            name = "Apple Inc.",
                            price = 319.97,
                            previousClose = 328.21,
                            change = -8.24,
                            changePercent = -2.51,
                            open = 328.00,
                            high = 328.93,
                            low = 317.86,
                            volume = 39_600_000,
                            week52High = 344.57,
                            week52Low = 225.95,
                            pe = 36.61,
                            marketCap = 4.67e12,
                            dividendYield = 0.0034,
                            eps = 8.74,
                            beta = 1.09,
                            avgVolume = 53_800_000,
                        ),
                    ),
                    cagr = Stocks.cagrStats(StockCagr(y1 = 12.40, y3 = 18.20, y5 = 16.10, y10 = 27.50)),
                )
            }
        }
    }

    @Test
    fun stockDetailScrub() {
        val points = listOf(
            328.0, 326.4, 324.1, 325.8, 323.0, 321.2, 322.5, 320.1, 319.97,
        ).mapIndexed { i, close -> StockPoint(time = 1_700_000_000L + i * 300L, close = close) }
        paparazzi.snapshot {
            BuilderTheme {
                StockDetailChrome(
                    symbol = "AAPL",
                    name = "Apple Inc.",
                    price = "$325.80",
                    changeLine = "-2.41 (-0.73%)",
                    up = false,
                    dateLine = "10:28 PM",
                    selectedIndex = 3,
                    points = points,
                    range = xyz.cdr.builderlauncher.stocks.StockRange.D1,
                )
            }
        }
    }

    @Test
    fun chat() {
        paparazzi.snapshot {
            BuilderTheme {
                ChatChrome(
                    messages = listOf(
                        ChatBubble(user = true, body = "compare kotlin and rust"),
                        ChatBubble(
                            user = false,
                            body = """
                                # Quick take

                                | Lang | GC |
                                | --- | --- |
                                | Kotlin | yes |
                                | Rust | no |

                                Use **Kotlin** on Android.
                            """.trimIndent(),
                        ),
                    ),
                    input = "write a longer follow-up that wraps instead of scrolling sideways on this screen",
                )
            }
        }
    }

    @Test
    fun chatProviderMenu() {
        paparazzi.snapshot {
            BuilderTheme {
                ChatChrome(
                    messages = listOf(
                        ChatBubble(user = true, body = "compare kotlin and rust"),
                        ChatBubble(user = false, body = "Use **Kotlin** on Android."),
                    ),
                    input = "write a longer follow-up",
                    providerMenu = true,
                )
            }
        }
    }

    @Test
    fun chatHistory() {
        paparazzi.snapshot {
            BuilderTheme {
                ChatHistoryChrome(
                    rows = listOf(
                        ChatListRow("compare kotlin and rust", "7 Sep 15:42"),
                        ChatListRow("weather tomorrow", "6 Sep 09:18"),
                    ),
                )
            }
        }
    }

    @Test
    fun hub() {
        paparazzi.snapshot {
            BuilderTheme {
                HubChrome(
                    rows = listOf(
                        HubRow("Messages", "Jason", "on my way"),
                        HubRow(
                            "Signal",
                            "Lauren",
                            "running late — also can you grab milk, eggs, and the parcel " +
                                "from the porch after you pick up the kids? the meeting ran over " +
                                "and traffic downtown is a mess so I will be later than I said",
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun hubReply() {
        paparazzi.snapshot {
            BuilderTheme {
                HubChrome(
                    rows = listOf(
                        HubRow("Messages", "Jason", "on my way", reply = "sounds good"),
                        HubRow("Signal", "Lauren", "running late"),
                    ),
                )
            }
        }
    }

    @Test
    fun settings() {
        paparazzi.snapshot {
            BuilderTheme {
                SettingsChrome(
                    settings = BuilderSettings(
                        provider = LlmProvider.HERMES,
                        hermesBaseUrl = "http://192.168.1.10:8642",
                        keyboardMode = KeyboardMode.AUTO,
                    ),
                    hardware = true,
                    hermes = "http://192.168.1.10:8642",
                    apiKey = "",
                    model = "",
                    weatherPlace = "Kitchener, Ontario, Canada",
                    weatherSuggestions = listOf(
                        "Kitchener, Ontario, Canada",
                        "Kitchener, British Columbia, Canada",
                    ),
                )
            }
        }
    }

    @Test
    fun settingsBackup() {
        paparazzi.snapshot {
            BuilderTheme {
                BackupChrome()
            }
        }
    }

    @Test
    fun settingsCalendar() {
        paparazzi.snapshot {
            BuilderTheme {
                CalendarChrome()
            }
        }
    }

    @Test
    fun settingsCalendarDenied() {
        paparazzi.snapshot {
            BuilderTheme {
                CalendarChrome(
                    accessGranted = false,
                    homeLine = "Home next event stays hidden until access is granted.",
                    rows = emptyList(),
                )
            }
        }
    }

    @Test
    fun aiProviders() {
        paparazzi.snapshot {
            BuilderTheme {
                AiProvidersChrome()
            }
        }
    }

    @Test
    fun clock() {
        paparazzi.snapshot {
            BuilderTheme {
                ClockChrome(tab = "Timer", timer = "5:00")
            }
        }
    }

    @Test
    fun clockTimerAlert() {
        paparazzi.snapshot {
            BuilderTheme {
                ClockAlertChrome()
            }
        }
    }

    @Test
    fun clockAlarmAlert() {
        paparazzi.snapshot {
            BuilderTheme {
                ClockAlertChrome(kind = "alarm", label = "Alarm")
            }
        }
    }

    @Test
    fun clockAlarm() {
        paparazzi.snapshot {
            BuilderTheme {
                ClockChrome(
                    tab = "Alarm",
                    alarms = listOf(
                        xyz.cdr.builderlauncher.clock.ClockAlarm(
                            id = "1",
                            hour = 22,
                            minute = 30,
                            enabled = true,
                            label = "Take out garbage",
                            days = setOf(3),
                        ),
                        xyz.cdr.builderlauncher.clock.ClockAlarm(id = "2", hour = 7, minute = 15, enabled = false),
                    ),
                )
            }
        }
    }

    @Test
    fun weather() {
        paparazzi.snapshot {
            BuilderTheme {
                WeatherChrome()
            }
        }
    }

    @Test
    fun usage() {
        paparazzi.snapshot {
            BuilderTheme {
                UsageChrome()
            }
        }
    }

    @Test
    fun usageScrub() {
        paparazzi.snapshot {
            BuilderTheme {
                UsageChrome(selectedIndex = 3)
            }
        }
    }

    @Test
    fun homeThemePlain() {
        paparazzi.snapshot {
            BuilderTheme(theme = UiTheme.PLAIN, accent = accentColor(UiTheme.PLAIN.defaultAccentHex)) {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "summarize this PR",
                    prompt = "?",
                    todos = listOf("buy milk", "ship builder-launcher CI", "call dentist"),
                    event = "dentist · 09:00",
                )
            }
        }
    }

    @Test
    fun homeThemeMaterial() {
        paparazzi.snapshot {
            BuilderTheme(theme = UiTheme.MATERIAL, accent = accentColor(UiTheme.MATERIAL.defaultAccentHex)) {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "summarize this PR",
                    prompt = "?",
                    todos = listOf("buy milk", "ship builder-launcher CI", "call dentist"),
                    event = "dentist · 09:00",
                )
            }
        }
    }

    @Test
    fun homeThemeIos() {
        paparazzi.snapshot {
            BuilderTheme(theme = UiTheme.IOS, accent = accentColor(UiTheme.IOS.defaultAccentHex)) {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "summarize this PR",
                    prompt = "?",
                    todos = listOf("buy milk", "ship builder-launcher CI", "call dentist"),
                    event = "dentist · 09:00",
                )
            }
        }
    }

    @Test
    fun settingsThemePlain() {
        paparazzi.snapshot {
            BuilderTheme(theme = UiTheme.PLAIN, accent = accentColor(UiTheme.PLAIN.defaultAccentHex)) {
                SettingsChrome(
                    settings = BuilderSettings(
                        provider = LlmProvider.HERMES,
                        hermesBaseUrl = "http://192.168.1.10:8642",
                        keyboardMode = KeyboardMode.AUTO,
                        uiTheme = UiTheme.PLAIN,
                        accentHex = UiTheme.PLAIN.defaultAccentHex,
                    ),
                    hardware = true,
                    hermes = "http://192.168.1.10:8642",
                    apiKey = "",
                    model = "",
                    weatherPlace = "Kitchener, Ontario, Canada",
                )
            }
        }
    }

    @Test
    fun settingsThemeMaterial() {
        paparazzi.snapshot {
            BuilderTheme(theme = UiTheme.MATERIAL, accent = accentColor(UiTheme.MATERIAL.defaultAccentHex)) {
                SettingsChrome(
                    settings = BuilderSettings(
                        provider = LlmProvider.HERMES,
                        hermesBaseUrl = "http://192.168.1.10:8642",
                        keyboardMode = KeyboardMode.AUTO,
                        uiTheme = UiTheme.MATERIAL,
                        accentHex = UiTheme.MATERIAL.defaultAccentHex,
                    ),
                    hardware = true,
                    hermes = "http://192.168.1.10:8642",
                    apiKey = "",
                    model = "",
                    weatherPlace = "Kitchener, Ontario, Canada",
                )
            }
        }
    }

    @Test
    fun stocksThemePlain() { snap(UiTheme.PLAIN) { sampleStocks() } }

    @Test
    fun stocksThemeMaterial() { snap(UiTheme.MATERIAL) { sampleStocks() } }

    @Test
    fun stocksThemeIos() { snap(UiTheme.IOS) { sampleStocks() } }

    @Test
    fun usageThemePlain() { snap(UiTheme.PLAIN) { UsageChrome() } }

    @Test
    fun usageThemeMaterial() { snap(UiTheme.MATERIAL) { UsageChrome() } }

    @Test
    fun usageThemeIos() { snap(UiTheme.IOS) { UsageChrome() } }

    @Test
    fun podcastsThemePlain() { snap(UiTheme.PLAIN) { samplePodcasts() } }

    @Test
    fun podcastsThemeMaterial() { snap(UiTheme.MATERIAL) { samplePodcasts() } }

    @Test
    fun podcastsThemeIos() { snap(UiTheme.IOS) { samplePodcasts() } }

    private fun snap(theme: UiTheme, content: @Composable () -> Unit) {
        paparazzi.snapshot {
            BuilderTheme(theme = theme, accent = accentColor(theme.defaultAccentHex), content = content)
        }
    }

    @Composable
    private fun sampleStocks() {
        StocksChrome(
            rows = listOf(
                StockListRow("AAPL", "Apple Inc.", "$319.97", "-2.51%", up = false),
                StockListRow("MSFT", "Microsoft Corporation", "$428.10", "+1.24%", up = true),
            ),
            input = "",
        )
    }

    @Composable
    private fun samplePodcasts() {
        PodcastsChrome(
            nowPlayingTitle = "Playing analog with a title long enough to wrap past three lines on the home list so the rest is cut",
            nowPlayingShow = "The Talk Show",
            nowPlaying = true,
            continueRows = listOf(
                PodcastListRow(
                    "Playing analog with a title long enough to wrap past three lines on the home list so the rest is cut",
                    "The Talk Show",
                    "12:00 of 45:00",
                    highlight = true,
                    maxTitleLines = Podcasts.TITLE_LINES,
                    deletable = true,
                    downloadable = true,
                    downloaded = true,
                ),
            ),
            newRows = listOf(
                PodcastListRow(
                    "Newest",
                    "Accidental Tech Podcast with a show name long enough that it must stay on one line",
                    "1:02:03",
                    maxTitleLines = Podcasts.TITLE_LINES,
                    maxSubtitleLines = Podcasts.SHOW_LINES,
                    deletable = true,
                    downloadable = true,
                ),
                PodcastListRow("New two", "The Talk Show", "45:00", maxTitleLines = Podcasts.TITLE_LINES, maxSubtitleLines = Podcasts.SHOW_LINES, deletable = true, downloadable = true),
            ),
            shows = listOf(
                PodcastListRow("Accidental Tech Podcast", "Marco Arment", deletable = true),
                PodcastListRow("The Talk Show", "John Gruber", deletable = true),
            ),
        )
    }
}
