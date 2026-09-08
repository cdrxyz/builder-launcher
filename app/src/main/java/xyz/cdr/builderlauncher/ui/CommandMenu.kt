package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.cdr.builderlauncher.commands.PrefixCommand
import xyz.cdr.builderlauncher.commands.PrefixCommands
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.Paper
import xyz.cdr.builderlauncher.ui.theme.Accent

@Composable
fun CommandMenu(
    selected: Int = -1,
    onSelect: (PrefixCommand) -> Unit = {},
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        PrefixCommands.all.forEachIndexed { index, cmd ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(cmd) }
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    cmd.glyph.toString(),
                    color = Accent,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Text(
                    cmd.label,
                    color = if (index == selected) Paper else Dim,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
