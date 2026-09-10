package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xyz.cdr.builderlauncher.ui.theme.Accent

@Composable
fun ScreenHeader(
    title: String,
    leading: @Composable BoxScope.() -> Unit,
    trailing: @Composable BoxScope.() -> Unit = {},
) {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.CenterStart), content = leading)
        Text(title, color = Accent, modifier = Modifier.align(Alignment.Center))
        Box(Modifier.align(Alignment.CenterEnd), content = trailing)
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
