package xyz.cdr.builderlauncher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ThemedList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(tokens.radius)
    when (tokens.chrome) {
        ThemeChrome.IOS -> Column(
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(tokens.card),
            content = content,
        )
        else -> Column(
            modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(tokens.listGap),
            content = content,
        )
    }
}

@Composable
fun ThemedRow(
    modifier: Modifier = Modifier,
    last: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalTokens.current
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(tokens.radius)
    val rowMod = when (tokens.chrome) {
        ThemeChrome.TUI -> Modifier.fillMaxWidth()
        ThemeChrome.PLAIN -> Modifier
            .fillMaxWidth()
            .border(1.5.dp, accent, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
        ThemeChrome.MATERIAL -> Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tokens.card)
            .padding(horizontal = 16.dp, vertical = 12.dp)
        ThemeChrome.IOS -> Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    }
    Column(modifier.fillMaxWidth()) {
        Row(rowMod, verticalAlignment = Alignment.CenterVertically, content = content)
        if (tokens.chrome == ThemeChrome.IOS && !last) {
            HorizontalDivider(color = Line, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

@Composable
fun ThemedChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val tokens = LocalTokens.current
    val accent = LocalAccent.current
    val click = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    when (tokens.chrome) {
        ThemeChrome.TUI -> Text(
            label,
            color = if (selected) accent else Dim,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.then(click).padding(vertical = 6.dp, horizontal = 2.dp),
        )
        ThemeChrome.PLAIN -> Text(
            label,
            color = if (selected) accent else Dim,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
                .then(click)
                .border(1.dp, if (selected) accent else Line, RoundedCornerShape(tokens.radius))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        ThemeChrome.MATERIAL -> Text(
            label,
            color = if (selected) tokens.ink else Paper,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
                .then(click)
                .clip(RoundedCornerShape(tokens.radius))
                .background(if (selected) accent else tokens.field)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
        ThemeChrome.IOS -> Text(
            label,
            color = if (selected) Color.White else Paper,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
                .then(click)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) accent else Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun ThemedSegmented(
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit = {},
) {
    val tokens = LocalTokens.current
    if (tokens.chrome == ThemeChrome.IOS) {
        Row(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(Line.copy(alpha = 0.35f))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            labels.forEachIndexed { index, label ->
                ThemedChip(
                    label = label,
                    selected = index == selected,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(index) },
                )
            }
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEachIndexed { index, label ->
                ThemedChip(label = label, selected = index == selected, onClick = { onSelect(index) })
            }
        }
    }
}

@Composable
fun ThemedBadge(text: String, tone: Color, modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    when (tokens.chrome) {
        ThemeChrome.TUI, ThemeChrome.IOS -> Text(
            text,
            color = tone,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
        ThemeChrome.PLAIN -> Text(
            text,
            color = tone,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
                .border(1.dp, tone, RoundedCornerShape(tokens.radius))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        ThemeChrome.MATERIAL -> Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
                .clip(RoundedCornerShape(tokens.radius))
                .background(tone)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun ThemedPlayer(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val tokens = LocalTokens.current
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(tokens.radius)
    val chrome = when (tokens.chrome) {
        ThemeChrome.TUI -> Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ThemeChrome.PLAIN -> Modifier
            .fillMaxWidth()
            .border(1.5.dp, accent, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
        ThemeChrome.MATERIAL -> Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tokens.card)
            .padding(horizontal = 16.dp, vertical = 12.dp)
        ThemeChrome.IOS -> Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tokens.card)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    }
    Row(modifier.then(chrome), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun ThemedSectionLabel(title: String) {
    val tokens = LocalTokens.current
    when (tokens.chrome) {
        ThemeChrome.TUI -> Column(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        ) {
            HorizontalDivider(color = Line)
            Text(
                title,
                color = Dim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        ThemeChrome.PLAIN -> Text(
            title,
            color = Accent,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, bottom = 2.dp),
        )
        ThemeChrome.MATERIAL -> Text(
            title,
            color = Paper,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        ThemeChrome.IOS -> Text(
            title.uppercase(),
            color = Dim,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
        )
    }
}

@Composable
fun ThemedPlayWell(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val tokens = LocalTokens.current
    if (tokens.chrome == ThemeChrome.MATERIAL) {
        Box(
            modifier
                .clip(CircleShape)
                .background(Accent)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    } else {
        Box(modifier, contentAlignment = Alignment.Center) { content() }
    }
}
