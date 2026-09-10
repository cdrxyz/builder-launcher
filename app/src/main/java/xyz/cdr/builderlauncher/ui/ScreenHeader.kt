package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.cdr.builderlauncher.ui.theme.Accent
import xyz.cdr.builderlauncher.ui.theme.LocalTokens
import xyz.cdr.builderlauncher.ui.theme.Paper
import xyz.cdr.builderlauncher.ui.theme.ThemeChrome

@Composable
fun ScreenHeader(
    title: String,
    leading: @Composable BoxScope.() -> Unit,
    trailing: @Composable BoxScope.() -> Unit = {},
) {
    when (LocalTokens.current.chrome) {
        ThemeChrome.TUI, ThemeChrome.PLAIN -> Box(Modifier.fillMaxWidth()) {
            Box(Modifier.align(Alignment.CenterStart), content = leading)
            Text(title, color = Accent, modifier = Modifier.align(Alignment.Center))
            Box(Modifier.align(Alignment.CenterEnd), content = trailing)
        }
        ThemeChrome.MATERIAL -> Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(content = leading)
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                color = Paper,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Box(content = trailing)
        }
        ThemeChrome.IOS -> Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.align(Alignment.CenterStart), content = leading)
                Box(Modifier.align(Alignment.CenterEnd), content = trailing)
            }
            Text(
                title.replaceFirstChar { it.uppercase() },
                color = Paper,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
fun ScreenBack(
    label: String = "<",
    onBack: (() -> Unit)? = null,
    description: String = "back",
) {
    Text(
        label,
        color = Accent,
        modifier = Modifier
            .then(
                if (onBack != null) {
                    Modifier
                        .semantics { contentDescription = description }
                        .clickable { onBack() }
                } else {
                    Modifier
                },
            )
            .padding(vertical = 6.dp),
    )
}
