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
    // Shown from onCompleted unless this process was started for the bar alone.
    // A binding would pin a resident window hidden after the bar asks it to open.
    visible: false
    title: "Builder Launcher"
    color: "#0e0e10"

    Material.theme: Material.Dark
    Material.accent: theme.accent
    Material.roundedScale: theme.radius

    property bool helpVisible: false
    property int pad: Math.min(8, theme.radius)

    property bool quitForReal: false

    // Closing the window takes it off the workspace. The desk keeps running so
    // the bar can mark tasks and drive playback with no window open.
    function leave() {
        win.quitForReal = true
        Qt.quit()
    }

    Component.onCompleted: {
        if (!startHidden)
            win.show()
    }

    onClosing: function (close) {
        if (!stayResident || win.quitForReal) {
            close.accepted = true
            return
        }
        close.accepted = false
        win.hide()
    }

    Connections {
        target: backend
        function onRaiseRequested() {
            win.show()
            win.raise()
            win.requestActivate()
        }
    }
    Shortcut { sequence: "Ctrl+Q"; context: Qt.ApplicationShortcut; onActivated: backend.dirty ? quitDialog.open() : win.leave() }
    Shortcut { sequences: [StandardKey.Undo]; context: Qt.ApplicationShortcut; onActivated: backend.undo() }
    Shortcut { sequences: [StandardKey.Redo]; context: Qt.ApplicationShortcut; onActivated: backend.redo() }
    Shortcut { sequence: "Ctrl+/"; context: Qt.ApplicationShortcut; onActivated: win.helpVisible = !win.helpVisible }
    // Omarchy delivers Cmd+[ and Cmd+] as Alt+Left and Alt+Right. That is
    // StandardKey.Back and Forward here, along with the Back and Forward keys.
    // Meta+[ is the same chord when the compositor does not translate it.
    Shortcut {
        sequences: [StandardKey.Back, "Meta+["]
        context: Qt.ApplicationShortcut
        enabled: backend.canBack
        onActivated: backend.back()
    }
    Shortcut {
        sequences: [StandardKey.Forward, "Meta+]"]
        context: Qt.ApplicationShortcut
        enabled: backend.canForward
        onActivated: backend.forward()
    }
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
                spacing: 2
                AbstractButton {
                    id: backButton
                    enabled: backend.canBack
                    hoverEnabled: true
                    onClicked: backend.back()
                    Layout.preferredWidth: 32
                    Layout.preferredHeight: 32
                    Layout.alignment: Qt.AlignVCenter
                    Accessible.name: qsTr("Back")
                    contentItem: Text {
                        text: Qt.application.layoutDirection === Qt.RightToLeft ? "→" : "←"
                        color: backButton.enabled ? "#f4f4f5" : "#3a3a40"
                        font.pixelSize: 20
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                    }
                    background: Rectangle {
                        color: backButton.down ? "#2a2a30" : (backButton.hovered ? "#1c1c22" : "transparent")
                        radius: Math.min(4, theme.radius)
                    }
                    ToolTip.visible: backButton.hovered && backButton.enabled
                    ToolTip.text: qsTr("Back")
                    ToolTip.delay: 500
                }
                AbstractButton {
                    id: forwardButton
                    enabled: backend.canForward
                    hoverEnabled: true
                    onClicked: backend.forward()
                    Layout.preferredWidth: 32
                    Layout.preferredHeight: 32
                    Layout.alignment: Qt.AlignVCenter
                    Accessible.name: qsTr("Forward")
                    contentItem: Text {
                        text: Qt.application.layoutDirection === Qt.RightToLeft ? "←" : "→"
                        color: forwardButton.enabled ? "#f4f4f5" : "#3a3a40"
                        font.pixelSize: 20
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                    }
                    background: Rectangle {
                        color: forwardButton.down ? "#2a2a30" : (forwardButton.hovered ? "#1c1c22" : "transparent")
                        radius: Math.min(4, theme.radius)
                    }
                    ToolTip.visible: forwardButton.hovered && forwardButton.enabled
                    ToolTip.text: qsTr("Forward")
                    ToolTip.delay: 500
                }
                Label { text: backend.title; font.pixelSize: 22; font.bold: true; Layout.leftMargin: 6 }
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
                    reuseItems: true
                    model: backend.menuOpen ? backend.menu : backend.rows
                    // Qt's wheel handler on Wayland moves about one row per notch, and a
                    // trackpad pixelDelta is left raw. Four desktop steps matches the
                    // other Omarchy apps. Keyboard movement still follows the selection;
                    // playback and status updates do not pull the list back.
                    property int followedSelection: -1
                    function clampedContentY(y) {
                        const top = originY
                        const bottom = originY + Math.max(0, contentHeight - height)
                        return Math.max(top, Math.min(bottom, y))
                    }
                    function wheelDistance(wheel) {
                        const notch = Math.max(1, Application.styleHints.wheelScrollLines) * 24 * 4
                        const device = wheel.device
                        const trackpad = device && (device.type === PointerDevice.TouchPad
                                                     || device.pointerType === PointerDevice.Finger)
                        if (trackpad)
                            return wheel.pixelDelta.y !== 0 ? wheel.pixelDelta.y * 4 : wheel.angleDelta.y / 120 * notch
                        if (wheel.angleDelta.y !== 0)
                            return wheel.angleDelta.y / 120 * notch
                        return wheel.pixelDelta.y * 4
                    }
                    Component.onCompleted: followedSelection = backend.selected
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
                                Label {
                                    text: modelData.trailing || ""
                                    color: "#9a9aa3"
                                    visible: text.length > 0
                                }
                                Label {
                                    text: modelData.change || ""
                                    visible: text.length > 0
                                    color: modelData.tone === "up" ? "#3DDC97" : modelData.tone === "down" ? "#FF5F57" : "#9a9aa3"
                                }
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
                            if (list.followedSelection === backend.selected)
                                return
                            const index = backend.selected
                            list.followedSelection = index
                            Qt.callLater(function() {
                                if (index >= 0 && index < list.count)
                                    list.positionViewAtIndex(index, ListView.Contain)
                            })
                        }
                    }
                    Keys.onPressed: event => {
                        if (event.isAutoRepeat && (event.key === Qt.Key_Space || event.key === Qt.Key_X)) {
                            event.accepted = true
                            return
                        }
                        if (event.key === Qt.Key_J || event.key === Qt.Key_Down) { backend.moveSelection(1); event.accepted = true }
                        if (event.key === Qt.Key_K || event.key === Qt.Key_Up) { backend.moveSelection(-1); event.accepted = true }
                        if (event.key === Qt.Key_Space) { backend.toggleSelection(); event.accepted = true }
                        if (event.key === Qt.Key_X) { backend.deleteSelection(); event.accepted = true }
                        if (event.key === Qt.Key_Left && backend.page === "home") { backend.nudgeHomePane(80); event.accepted = true }
                        if (event.key === Qt.Key_Right && backend.page === "home") { backend.nudgeHomePane(-80); event.accepted = true }
                        if (event.key === Qt.Key_Question) { win.helpVisible = !win.helpVisible; event.accepted = true }
                    }
                    // The list would otherwise consume the wheel before a child sees it.
                    WheelHandler {
                        blocking: true
                        onWheel: wheel => {
                            const dy = list.wheelDistance(wheel)
                            if (dy !== 0) {
                                list.cancelFlick()
                                list.contentY = list.clampedContentY(list.contentY - dy)
                            }
                            wheel.accepted = true
                        }
                    }
                    MouseArea {
                        anchors.fill: parent
                        propagateComposedEvents: true
                        property real startX: 0
                        onPressed: mouse => { startX = mouse.x; mouse.accepted = false }
                        onReleased: mouse => {
                            if (backend.page === "home")
                                backend.nudgeHomePane(mouse.x - startX)
                            mouse.accepted = false
                        }
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
                            if (event.isAutoRepeat && (event.key === Qt.Key_Space || event.key === Qt.Key_Delete)) {
                                event.accepted = true
                                return
                            }
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

            Text {
                // Material's Label paints links in the accent color. A plain Text
                // with an explicit font color keeps Cedar Labs light grey and underlined.
                text: qsTr("Sign-in merges this desk with the account. It does not replace either side. Built by <a href=\"https://cdr.xyz\"><font color=\"#d7d7de\"><u>Cedar Labs</u></font></a>.")
                textFormat: Text.RichText
                color: "#6e6e78"
                linkColor: "#d7d7de"
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

        // Side buttons only. Left clicks and the wheel still reach the page.
        MouseArea {
            anchors.fill: parent
            acceptedButtons: Qt.BackButton | Qt.ForwardButton
            onPressed: mouse => {
                if (mouse.button === Qt.BackButton)
                    backend.back()
                else if (mouse.button === Qt.ForwardButton)
                    backend.forward()
                mouse.accepted = true
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
            Button { text: qsTr("Quit"); DialogButtonBox.buttonRole: DialogButtonBox.AcceptRole; onClicked: win.leave() }
            Button { text: qsTr("Stay"); DialogButtonBox.buttonRole: DialogButtonBox.RejectRole }
        }
    }
}
