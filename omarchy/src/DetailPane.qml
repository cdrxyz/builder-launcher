import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Material

TextArea {
    id: editor
    wrapMode: TextArea.Wrap
    readOnly: backend.page === "article"
    text: backend.detail
    color: "#f4f4f5"
    Material.accent: theme.accent
    placeholderText: backend.page === "note" ? qsTr("Write the note") : ""
    onActiveFocusChanged: backend.setComposing(activeFocus)
    onTextChanged: if (backend.page === "note" && activeFocus) backend.saveDetail(text)
    Keys.onEscapePressed: backend.go("notes")
}
