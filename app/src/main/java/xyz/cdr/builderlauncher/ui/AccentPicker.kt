package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xyz.cdr.builderlauncher.data.AccentColor
import xyz.cdr.builderlauncher.ui.theme.Accent
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.Line
import xyz.cdr.builderlauncher.ui.theme.Paper

@Composable
fun AccentPicker(
    hex: String,
    onPick: ((String) -> Unit)? = null,
) {
    Column {
        Text("Accent", color = Dim, style = MaterialTheme.typography.labelSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            AccentColor.presets.forEach { swatch ->
                val selected = AccentColor.same(hex, swatch.hex)
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(1.dp, if (selected) Paper else Line)
                        .background(Color(AccentColor.argb(swatch.hex)))
                        .then(
                            if (onPick != null) {
                                Modifier.clickable { onPick(AccentColor.normalize(swatch.hex)) }
                            } else {
                                Modifier
                            },
                        )
                        .semantics { contentDescription = swatch.name },
                )
            }
        }
        Text(
            AccentColor.nameOf(hex) ?: "custom",
            color = Accent,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Cursor, the > prompt, and links like all notes.",
            color = Dim,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
