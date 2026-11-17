package xyz.cdr.builderlauncher.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.cdr.builderlauncher.hub.HubItem
import xyz.cdr.builderlauncher.hub.HubMessages
import xyz.cdr.builderlauncher.hub.HubStore
import xyz.cdr.builderlauncher.ui.theme.Accent
import xyz.cdr.builderlauncher.ui.theme.Dim
import xyz.cdr.builderlauncher.ui.theme.Line
import xyz.cdr.builderlauncher.ui.theme.Paper

@Composable
internal fun HubPane(
    hub: List<HubItem>,
    replyKey: String?,
    replyText: String,
    onBack: () -> Unit,
    onStartReply: (String) -> Unit,
    onReplyText: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onSend: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = HubMessages.TITLE,
            leading = { ScreenBack(HubMessages.BACK, onBack = onBack) },
            trailing = {
                DeleteIcon(
                    Modifier
                        .semantics { contentDescription = HubMessages.CLEAR_ALL }
                        .clickable { HubStore.clearAll() }
                        .padding(vertical = 6.dp),
                )
            },
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
            items(hub, key = { it.key }) { item ->
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier
                                .weight(1f)
                                .semantics { contentDescription = "open message" }
                                .clickable { HubStore.open(item.key) }
                                .padding(vertical = 6.dp),
                        ) {
                            Text(item.source, color = Dim, style = MaterialTheme.typography.labelSmall)
                            Text(item.title, color = Paper)
                            if (item.body.isNotBlank()) {
                                Text(
                                    item.body,
                                    color = Dim,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        ReplyIcon(
                            Modifier
                                .semantics { contentDescription = "reply" }
                                .clickable {
                                    if (item.canInlineReply) onStartReply(item.key) else HubStore.open(item.key)
                                }
                                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                        )
                        DeleteIcon(
                            Modifier
                                .semantics { contentDescription = "dismiss" }
                                .clickable { onDismiss(item.key) }
                                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                        )
                    }
                    if (replyKey == item.key && item.canInlineReply) {
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                if (replyText.isEmpty()) {
                                    Text("reply", color = Dim, style = MaterialTheme.typography.bodyMedium)
                                }
                                BasicTextField(
                                    value = replyText,
                                    onValueChange = onReplyText,
                                    singleLine = true,
                                    cursorBrush = SolidColor(Accent),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Paper),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Sentences,
                                        imeAction = ImeAction.Send,
                                    ),
                                    keyboardActions = KeyboardActions(onSend = { onSend(item.key) }),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            SendIcon(
                                Modifier
                                    .semantics { contentDescription = "send" }
                                    .clickable { onSend(item.key) }
                                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                            )
                        }
                        HorizontalDivider(color = Line, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            if (hub.isEmpty()) {
                item {
                    Text(
                        "Grant notification access in settings to fill the hub with messages you can reply to.",
                        color = Dim,
                    )
                }
            }
        }
    }
}
