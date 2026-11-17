import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts

ApplicationWindow {
    id: win
    width: 960
    height: 680
    minimumWidth: 720
    minimumHeight: 480
    visible: true
    title: "Builder Launcher"
    color: "#0e0e10"

    Material.theme: Material.Dark
    Material.accent: theme.accent
    Material.roundedScale: theme.radius

    property bool helpVisible: false
    property int pad: Math.min(8, theme.radius)

    Shortcut { sequence: "Ctrl+Q"; context: Qt.ApplicationShortcut; onActivated: backend.dirty ? quitDialog.open() : Qt.quit() }
    Shortcut { sequences: [StandardKey.Undo]; context: Qt.ApplicationShortcut; onActivated: backend.undo() }
    Shortcut { sequences: [StandardKey.Redo]; context: Qt.ApplicationShortcut; onActivated: backend.redo() }
    Shortcut { sequence: "Ctrl+/"; context: Qt.ApplicationShortcut; onActivated: win.helpVisible = !win.helpVisible }
    Shortcut {
        sequence: "Esc"
        context: Qt.ApplicationShortcut
        onActivated: {
            if (quitDialog.visible)
                quitDialog.close()
            else if (win.helpVisible)
                win.helpVisible = false
            else if (backend.page === "note")
                backend.go("notes")
            else
                backend.go("home")
        }
    }

    Connections {
        target: backend
        function onHelpRequested() { win.helpVisible = true }
    }

    Item {
        anchors.fill: parent
        LayoutMirroring.enabled: Qt.application.layoutDirection === Qt.RightToLeft
        LayoutMirroring.childrenInherit: true

        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 18
            spacing: 10

            RowLayout {
                Layout.fillWidth: true
                Label { text: backend.title; font.pixelSize: 22; font.bold: true }
                Item { Layout.fillWidth: true }
                Label { text: backend.syncLabel; color: "#9a9aa3"; font.pixelSize: 12 }
            }

            Label {
                visible: backend.status.length > 0
                text: backend.status
                color: theme.accent
                wrapMode: Text.WordWrap
                Layout.fillWidth: true
            }

            StackLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                currentIndex: backend.page === "settings" ? 1 : backend.page === "note" || backend.page === "article" ? 2 : 0

                ListView {
                    id: list
                    clip: true
                    spacing: 4
                    model: backend.menuOpen ? backend.menu : backend.rows
                    delegate: Item {
                        width: list.width
                        required property var modelData
                        required property int index
                        implicitHeight: Math.max(52, row.implicitHeight + 16)
                        height: implicitHeight
                        Rectangle {
                            anchors.fill: parent
                            radius: win.pad
                            color: index === backend.selected ? "#1c1c22" : "transparent"
                            border.color: index === backend.selected ? theme.accent : "transparent"
                            RowLayout {
                                id: row
                                anchors.left: parent.left
                                anchors.right: parent.right
                                anchors.verticalCenter: parent.verticalCenter
                                anchors.margins: 10
                                Label {
                                    text: modelData.kind === "todo" ? "○" : modelData.kind === "done" ? "●" : ""
                                    color: theme.accent
                                    visible: text.length > 0
                                }
                                ColumnLayout {
                                    Layout.fillWidth: true
                                    spacing: 0
                                    Label {
                                        text: modelData.title
                                        wrapMode: modelData.kind === "message" ? Text.Wrap : Text.NoWrap
                                        elide: modelData.kind === "message" ? Text.ElideNone : Text.ElideRight
                                        Layout.fillWidth: true
                                    }
                                    Label {
                                        text: modelData.subtitle
                                        color: "#9a9aa3"
                                        font.pixelSize: 12
                                        visible: text.length > 0
                                        wrapMode: modelData.kind === "message" ? Text.Wrap : Text.NoWrap
                                        elide: modelData.kind === "message" ? Text.ElideNone : Text.ElideRight
                                        Layout.fillWidth: true
                                    }
                                }
                                Label { text: modelData.trailing; color: "#9a9aa3" }
                            }
                            MouseArea {
                                anchors.fill: parent
                                onClicked: { backend.moveSelection(index - backend.selected); backend.activateSelection() }
                            }
                        }
                    }
                    Connections {
                        target: backend
                        function onChanged() {
                            Qt.callLater(function() {
                                if (backend.selected >= 0 && backend.selected < list.count)
                                    list.positionViewAtIndex(backend.selected, ListView.Contain)
                            })
                        }
                    }
                    Keys.onPressed: event => {
                        if (event.key === Qt.Key_J || event.key === Qt.Key_Down) { backend.moveSelection(1); event.accepted = true }
                        if (event.key === Qt.Key_K || event.key === Qt.Key_Up) { backend.moveSelection(-1); event.accepted = true }
                        if (event.key === Qt.Key_Space) { backend.toggleSelection(); event.accepted = true }
                        if (event.key === Qt.Key_X) { backend.deleteSelection(); event.accepted = true }
                        if (event.key === Qt.Key_Question) { win.helpVisible = !win.helpVisible; event.accepted = true }
                    }
                }

                SettingsForm { }
                DetailPane { id: editor }
                Connections {
                    target: backend
                    property string seenPage: ""
                    function onChanged() {
                        const page = backend.page
                        if (page === "note" && seenPage !== "note")
                            Qt.callLater(function() {
                                editor.forceActiveFocus()
                                editor.cursorPosition = editor.length
                            })
                        if (page !== "note" && seenPage === "note")
                            Qt.callLater(function() { command.forceActiveFocus() })
                        seenPage = page
                    }
                }
            }

            Rectangle {
                visible: backend.nowPlaying.length > 0
                Layout.fillWidth: true
                height: 36
                radius: win.pad
                color: "#16161a"
                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 12
                    anchors.rightMargin: 12
                    Label { text: backend.playing ? "▶" : "❚❚"; color: theme.accent }
                    Label { text: backend.nowPlaying; elide: Text.ElideRight; Layout.fillWidth: true }
                    Label { text: backend.positionLabel; color: "#9a9aa3" }
                }
                MouseArea { anchors.fill: parent; onClicked: backend.playPause() }
            }

            RowLayout {
                Layout.fillWidth: true
                spacing: 8
                Label {
                    text: backend.prompt
                    color: theme.accent
                    font.pixelSize: 20
                    font.family: "monospace"
                    Layout.alignment: Qt.AlignVCenter
                }
                Item {
                    Layout.fillWidth: true
                    implicitHeight: 48
                    TextField {
                        id: command
                        anchors.fill: parent
                        placeholderText: ""
                        font.family: "monospace"
                        font.pixelSize: 16
                        leftPadding: 12
                        Material.accent: theme.accent
                        // Typing breaks a text: binding, so user edits go in through
                        // textEdited and the field is filled back only when the draft changes.
                        onTextEdited: if (text !== backend.draft) backend.draft = text
                        onAccepted: backend.submit()
                        Connections {
                            target: backend
                            function onChanged() {
                                if (command.text !== backend.draft)
                                    command.text = backend.draft
                            }
                        }
                        Keys.onTabPressed: event => {
                            const order = ["-", "+", "$", "?", "/", ">"]
                            const index = order.indexOf(backend.prompt)
                            backend.setPrompt(order[(index < 0 ? 0 : index + 1) % order.length])
                            event.accepted = true
                        }
                        Keys.onPressed: event => {
                            if (event.key === Qt.Key_Up || event.key === Qt.Key_Down) {
                                backend.moveSelection(event.key === Qt.Key_Down ? 1 : -1)
                                event.accepted = true
                            } else if (event.key === Qt.Key_Space && text.length === 0) {
                                backend.toggleSelection()
                                event.accepted = true
                            } else if (event.key === Qt.Key_Delete && text.length === 0) {
                                backend.deleteSelection()
                                event.accepted = true
                            }
                        }
                        Component.onCompleted: forceActiveFocus()
                    }
                    Label {
                        anchors.left: parent.left
                        anchors.leftMargin: 14
                        anchors.verticalCenter: parent.verticalCenter
                        text: command.text.length === 0 ? backend.commandHint : ""
                        color: "#6e6e78"
                        font.family: "monospace"
                        font.pixelSize: 16
                        enabled: false
                    }
                }
            }

            Label {
                text: qsTr("Sign-in merges this desk with the account. It does not replace either side. Built by <a href=\"https://cdr.xyz\">Cedar Labs</a>.")
                textFormat: Text.RichText
                color: "#6e6e78"
                font.pixelSize: 11
                wrapMode: Text.WordWrap
                Layout.fillWidth: true
                onLinkActivated: link => Qt.openUrlExternally(link)
            }
        }

        Rectangle {
            visible: win.helpVisible || backend.alert.length > 0
            anchors.fill: parent
            color: "#cc0e0e10"
            MouseArea {
                anchors.fill: parent
                onClicked: win.helpVisible = false
            }
            Column {
                anchors.centerIn: parent
                spacing: 8
                width: Math.min(480, parent.width - 48)
                Label {
                    text: backend.alert.length > 0 ? backend.alert : qsTr("Shortcuts")
                    font.pixelSize: 22
                    color: theme.accent
                    width: parent.width
                    wrapMode: Text.WordWrap
                }
                Label { text: backend.alertTime; visible: backend.alert.length > 0; font.pixelSize: 28 }
                Repeater {
                    model: backend.alert.length > 0 ? [] : backend.shortcutRows()
                    Label { required property string modelData; text: modelData; color: "#d7d7de"; width: parent.width; wrapMode: Text.WordWrap }
                }
                Row {
                    spacing: 8
                    visible: backend.alert.length > 0
                    Button { text: qsTr("Snooze"); onClicked: backend.snoozeAlert(); Material.accent: theme.accent }
                    Button { text: qsTr("Dismiss"); onClicked: backend.dismissAlert() }
                }
            }
        }
    }

    Dialog {
        id: quitDialog
        title: qsTr("Quit")
        modal: true
        anchors.centerIn: parent
        width: 440
        standardButtons: Dialog.NoButton
        contentItem: Label {
            width: 400
            text: qsTr("Unsynced edits stay on this desk. Quit anyway?")
            wrapMode: Text.WordWrap
            color: "#f4f4f5"
        }
        footer: DialogButtonBox {
            Button { text: qsTr("Quit"); DialogButtonBox.buttonRole: DialogButtonBox.AcceptRole; onClicked: Qt.quit() }
            Button { text: qsTr("Stay"); DialogButtonBox.buttonRole: DialogButtonBox.RejectRole }
        }
    }
}
