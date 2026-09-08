package xyz.cdr.builderlauncher.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.data.KeyboardMode
import xyz.cdr.builderlauncher.data.LlmProvider
import xyz.cdr.builderlauncher.stocks.StockPoint
import xyz.cdr.builderlauncher.ui.theme.BuilderTheme

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
    fun homeCommandMenu() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "",
                    todos = listOf("buy milk", "ship builder-launcher CI"),
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
                    points = points,
                    range = xyz.cdr.builderlauncher.stocks.StockRange.D1,
                    stats = listOf(
                        StockStatRow("Open", "328.00", "High", "328.93"),
                        StockStatRow("Low", "317.86", "Vol", "39.6M"),
                        StockStatRow("Prev", "328.21", "52W H", "344.57"),
                        StockStatRow("52W L", "225.95", "Chg", "-2.51%"),
                    ),
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
                    input = "",
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
}
