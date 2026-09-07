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
                    input = "?summarize this PR",
                    todos = listOf("buy milk", "ship builder-launcher CI", "call dentist"),
                    doneTodos = listOf("pack charger"),
                    moreTodos = true,
                )
            }
        }
    }

    @Test
    fun homeMoreTodos() {
        paparazzi.snapshot {
            BuilderTheme {
                HomeChrome(
                    time = "15:42",
                    date = "Mon 7 Sep",
                    weather = "18° cloudy",
                    input = "",
                    todos = listOf(
                        "buy milk",
                        "ship builder-launcher CI",
                        "call dentist",
                        "pack charger",
                        "review PR after lunch",
                    ),
                    doneTodos = listOf("mail keys"),
                    moreTodos = true,
                    todosExpanded = true,
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
                        HubRow("todo", "ship builder-launcher CI"),
                        HubRow("note", "review PR after lunch"),
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
