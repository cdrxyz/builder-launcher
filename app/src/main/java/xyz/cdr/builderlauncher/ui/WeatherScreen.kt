package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.cdr.builderlauncher.data.WeatherUnits
import xyz.cdr.builderlauncher.ui.theme.Accent
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.Line
import xyz.cdr.builderlauncher.ui.theme.Paper
import xyz.cdr.builderlauncher.weather.WeatherCodes
import xyz.cdr.builderlauncher.weather.WeatherForecast
import xyz.cdr.builderlauncher.weather.WeatherFormat
import java.time.LocalDate

@Composable
fun WeatherScreen(
    place: String,
    units: WeatherUnits,
    forecast: WeatherForecast?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            "<",
            color = Accent,
            modifier = Modifier.clickable { onBack() }.padding(vertical = 6.dp),
        )
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .weight(1f, fill = true)
                .verticalScroll(rememberScrollState()),
        ) {
            if (forecast == null) {
                Text(
                    if (place.isBlank()) "Set a city in settings." else "Waiting on forecast…",
                    color = Dim,
                )
                if (place.isBlank()) {
                    Text(
                        "settings",
                        color = Accent,
                        modifier = Modifier.clickable { onOpenSettings() }.padding(vertical = 8.dp),
                    )
                }
            } else {
                WeatherBody(place = place.ifBlank { "Here" }, units = units, forecast = forecast)
            }
        }
    }
}

@Composable
fun WeatherBody(place: String, units: WeatherUnits, forecast: WeatherForecast) {
    val now = forecast.current
    val today = forecast.daily.firstOrNull()
    Text(place, color = Dim, style = MaterialTheme.typography.bodyMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            WeatherFormat.temp(now.temperatureC, units).removeSuffix("°"),
            color = Paper,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 72.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 76.sp,
            ),
        )
        WeatherCodes.kind(now.code)?.let { kind ->
            WeatherGlyph(
                kind = kind,
                isDay = now.isDay,
                color = Paper,
                size = 72.dp,
                modifier = Modifier.padding(start = 12.dp),
                contentDescription = WeatherCodes.label(now.code),
            )
        }
    }
    Text(WeatherCodes.label(now.code), color = Paper, style = MaterialTheme.typography.bodyLarge)
    if (today != null) {
        Text(
            "H ${WeatherFormat.temp(today.highC, units)}  L ${WeatherFormat.temp(today.lowC, units)}",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        WeatherStat("Feels", WeatherFormat.temp(now.feelsC, units))
        WeatherStat("Precip", WeatherFormat.precipChance(now.precipProb))
        WeatherStat("Wind", WeatherFormat.wind(now.windKmh, now.windDir, units))
    }
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = Line)
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        forecast.hourly.forEach { hour ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
                Text(WeatherFormat.hourLabel(hour.epochMs, forecast.timezone), color = Dim, style = MaterialTheme.typography.labelSmall)
                WeatherCodes.kind(hour.code)?.let { kind ->
                    WeatherGlyph(
                        kind = kind,
                        isDay = hour.isDay,
                        color = Paper,
                        size = 20.dp,
                        modifier = Modifier.padding(vertical = 4.dp),
                        contentDescription = WeatherCodes.label(hour.code),
                    )
                } ?: Spacer(Modifier.height(28.dp))
                Text(WeatherFormat.temp(hour.temperatureC, units), color = Paper, style = MaterialTheme.typography.bodyMedium)
                Text(WeatherFormat.precipChance(hour.precipProb), color = Dim, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = Line)
    Spacer(Modifier.height(8.dp))
    val todayIso = LocalDate.now().toString()
    forecast.daily.take(WeatherForecast.DAYS).forEach { day ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                WeatherFormat.weekday(day.date, todayIso),
                color = Paper,
                modifier = Modifier.width(56.dp),
            )
            WeatherCodes.kind(day.code)?.let { kind ->
                WeatherGlyph(
                    kind = kind,
                    isDay = true,
                    color = Paper,
                    size = 22.dp,
                    modifier = Modifier.padding(end = 10.dp),
                    contentDescription = WeatherCodes.label(day.code),
                )
            } ?: Spacer(Modifier.width(32.dp))
            Text(
                WeatherFormat.daySummary(day, units),
                color = Dim,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${WeatherFormat.temp(day.highC, units)}  ${WeatherFormat.temp(day.lowC, units)}",
                color = Paper,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = Line)
    Spacer(Modifier.height(8.dp))
    WeatherDetail("Humidity", WeatherFormat.humidity(now.humidity))
    WeatherDetail("Dew point", now.dewC?.let { WeatherFormat.temp(it, units) } ?: "—")
    WeatherDetail("UV index", WeatherFormat.uvLabel(now.uv ?: today?.uv))
    WeatherDetail("Pressure", WeatherFormat.pressure(now.pressureHpa, units))
    WeatherDetail("Visibility", WeatherFormat.visibility(now.visibilityM, units))
    WeatherDetail("Cloud cover", WeatherFormat.cloud(now.cloud))
    WeatherDetail("Sunrise", today?.sunrise ?: "—")
    WeatherDetail("Sunset", today?.sunset ?: "—")
    WeatherDetail("Wind", WeatherFormat.wind(now.windKmh, now.windDir, units))
    WeatherDetail("Gusts", now.gustKmh?.let { WeatherFormat.wind(it, null, units) } ?: "—")
    WeatherDetail("Air quality", WeatherFormat.aqiLabel(forecast.aqi))
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun WeatherStat(label: String, value: String) {
    Column {
        Text(label.uppercase(), color = Dim, style = MaterialTheme.typography.labelSmall)
        Text(value, color = Paper, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WeatherDetail(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Dim, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = Paper, style = MaterialTheme.typography.bodyMedium)
    }
}
