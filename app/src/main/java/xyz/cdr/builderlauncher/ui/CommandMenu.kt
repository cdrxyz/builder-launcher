package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.cdr.builderlauncher.commands.PrefixCommand
import xyz.cdr.builderlauncher.commands.PrefixCommands
import xyz.cdr.builderlauncher.commands.SlashCommand
import xyz.cdr.builderlauncher.commands.SlashCommands
import xyz.cdr.builderlauncher.ui.theme.Accent
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.Paper

internal fun commandMenuMaxHeight(maxHeight: Dp): Dp {
    val reserve = 56.dp
    return if (maxHeight < Dp.Infinity) {
        (maxHeight - reserve).coerceAtLeast(80.dp)
    } else {
        280.dp
    }
}

@Composable
fun CommandMenu(
    selected: Int = -1,
    onSelect: (PrefixCommand) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected, PrefixCommands.all.size) {
        if (selected in PrefixCommands.all.indices) listState.scrollToItem(selected)
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(PrefixCommands.all, key = { _, cmd -> cmd.glyph }) { index, cmd ->
            CommandMenuRow(
                glyph = cmd.glyph.toString(),
                label = cmd.label,
                highlight = index == selected,
                onClick = { onSelect(cmd) },
            )
        }
    }
}

@Composable
fun SlashCommandMenu(
    commands: List<SlashCommand> = SlashCommands.all,
    selected: Int = -1,
    onSelect: (SlashCommand) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected, commands.size) {
        if (selected in commands.indices) listState.scrollToItem(selected)
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(commands, key = { _, cmd -> cmd.name }) { index, cmd ->
            CommandMenuRow(
                glyph = cmd.name,
                label = cmd.label,
                highlight = index == selected,
                onClick = { onSelect(cmd) },
            )
        }
    }
}

@Composable
private fun CommandMenuRow(
    glyph: String,
    label: String,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(
            glyph,
            color = Accent,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(
            label,
            color = if (highlight) Paper else Dim,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
