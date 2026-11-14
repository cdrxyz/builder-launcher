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

    property bool helpVisible: backend.page === "help"
    property int pad: Math.min(8, theme.radius)

    Shortcut { sequence: "Ctrl+Q"; context: Qt.ApplicationShortcut; onActivated: backend.dirty ? quitDialog.open() : Qt.quit() }
    Shortcut { sequences: [StandardKey.Undo]; context: Qt.ApplicationShortcut; onActivated: backend.undo() }
    Shortcut { sequences: [StandardKey.Redo]; context: Qt.ApplicationShortcut; onActivated: backend.redo() }
    Shortcut { sequence: "Ctrl+/"; context: Qt.ApplicationShortcut; onActivated: win.helpVisible = !win.helpVisible }
    Shortcut { sequence: "Esc"; context: Qt.ApplicationShortcut; onActivated: win.helpVisible ? win.helpVisible = false : backend.go("home") }

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
                        height: 52
                        required property var modelData
                        required property int index
                        Rectangle {
                            anchors.fill: parent
                            radius: win.pad
                            color: index === backend.selected ? "#1c1c22" : "transparent"
                            border.color: index === backend.selected ? theme.accent : "transparent"
                            RowLayout {
                                anchors.fill: parent
                                anchors.margins: 10
                                Label {
                                    text: modelData.kind === "todo" ? "○" : modelData.kind === "done" ? "●" : ""
                                    color: theme.accent
                                    visible: text.length > 0
                                }
                                ColumnLayout {
                                    Layout.fillWidth: true
                                    spacing: 0
                                    Label { text: modelData.title; elide: Text.ElideRight; Layout.fillWidth: true }
                                    Label { text: modelData.subtitle; color: "#9a9aa3"; font.pixelSize: 12; visible: text.length > 0; elide: Text.ElideRight; Layout.fillWidth: true }
                                }
                                Label { text: modelData.trailing; color: "#9a9aa3" }
                            }
                            MouseArea {
                                anchors.fill: parent
                                onClicked: { backend.moveSelection(index - backend.selected); backend.activateSelection() }
                            }
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
                DetailPane { }
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
                        text: backend.draft
                        font.family: "monospace"
                        font.pixelSize: 16
                        leftPadding: 12
                        Material.accent: theme.accent
                        onTextChanged: if (text !== backend.draft) backend.draft = text
                        onAccepted: backend.submit()
                        Keys.onTabPressed: {
                            const order = ["-", "+", "$", "?", "/", ">"]
                            backend.setPrompt(order[(order.indexOf(backend.prompt) + 1) % order.length])
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
            Column {
                anchors.centerIn: parent
                spacing: 8
                width: Math.min(420, parent.width - 48)
                Label {
                    text: backend.alert.length > 0 ? backend.alert : qsTr("Shortcuts")
                    font.pixelSize: 22
                    color: theme.accent
                }
                Label { text: backend.alertTime; visible: backend.alert.length > 0; font.pixelSize: 28 }
                Repeater {
                    model: backend.shortcutRows()
                    Label { required property string modelData; text: modelData; color: "#d7d7de" }
                }
                Row {
                    spacing: 8
                    visible: backend.alert.length > 0
                    Button { text: qsTr("Snooze"); onClicked: backend.snoozeAlert(); Material.accent: theme.accent }
                    Button { text: qsTr("Dismiss"); onClicked: backend.dismissAlert() }
                }
            }
            MouseArea { anchors.fill: parent; onClicked: win.helpVisible = false }
        }
    }

    Dialog {
        id: quitDialog
        title: qsTr("Quit")
        modal: true
        anchors.centerIn: parent
        Label { text: qsTr("Unsynced edits stay on this desk. Quit anyway?") }
        footer: DialogButtonBox {
            Button { text: qsTr("Quit"); DialogButtonBox.buttonRole: DialogButtonBox.AcceptRole; onClicked: Qt.quit() }
            Button { text: qsTr("Stay"); DialogButtonBox.buttonRole: DialogButtonBox.RejectRole }
        }
    }
}
