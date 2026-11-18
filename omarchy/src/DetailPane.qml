import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Material

TextArea {
    id: editor
    wrapMode: TextArea.Wrap
    readOnly: backend.page === "article"
    color: "#f4f4f5"
    Material.accent: theme.accent
    placeholderText: backend.page === "note" ? qsTr("Write the note") : ""
    // Typing breaks a text: binding, so the next note would keep this text and
    // saveDetail would write it onto that note. The field is filled only when
    // the open document changes, and an article is scrolled to its saved place once.
    property string placedKey: ""
    property string scrolledKey: ""
    property bool applying: false
    onActiveFocusChanged: backend.setComposing(activeFocus)
    onTextChanged: if (!applying && backend.page === "note" && activeFocus) backend.saveDetail(text)
    function showDocument() {
        const note = backend.page === "note"
        const article = backend.page === "article"
        if (!note && !article) {
            placedKey = ""
            scrolledKey = ""
            return
        }
        const key = backend.detailKey
        if (!(note && activeFocus && key === placedKey)) {
            if (text !== backend.detail) {
                applying = true
                text = backend.detail
                applying = false
            }
            placedKey = key
        }
        if (article && scrolledKey !== key)
            placeReading()
    }
    function placeReading() {
        if (backend.page !== "article" || contentHeight <= height)
            return
        contentY = (contentHeight - height) * backend.readingFraction
        scrolledKey = backend.detailKey
    }
    onContentHeightChanged: if (backend.page === "article" && scrolledKey !== backend.detailKey) placeReading()
    Connections {
        target: backend
        function onChanged() { editor.showDocument() }
    }
    Component.onCompleted: showDocument()
}
