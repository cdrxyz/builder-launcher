package xyz.cdr.builderlauncher.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import xyz.cdr.builderlauncher.data.BuilderSettings
import xyz.cdr.builderlauncher.data.KeyboardMode
import xyz.cdr.builderlauncher.data.LlmProvider
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
    fun hub() {
        paparazzi.snapshot {
            BuilderTheme {
                HubChrome(
                    rows = listOf(
                        HubRow("Messages", "Jason", "on my way"),
                        HubRow("Calendar", "dentist", "Tue 9:00"),
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
