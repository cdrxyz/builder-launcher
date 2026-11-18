import QtQuick
import Quickshell
import Quickshell.Io
import qs.Commons
import qs.Commons as Commons
import qs.Ui

// The bar keeps the Builder mark. The tasks, the podcast, the open article,
// and the watchlist live in the panel that opens under it. The desk writes
// bar.json; this file only reads it and sends commands back. The desk does
// not need a window on a workspace: a hidden resident serves the same socket.
WidgetButton {
    id: root

    property string moduleName
    property var settings
    property bool popupOpen: false

    hasVisualContent: true
    labelVisible: false
    fixedWidth: vertical ? -1 : Style.bar.iconSlot
    fixedHeight: vertical ? Style.bar.iconSlot : -1
    tooltipText: popupOpen ? "" : strings.t("bar.open")
    onPressed: function (pressedButton) {
        if (pressedButton !== Qt.LeftButton)
            return
        if (!popupOpen)
            ensureResident()
        popupOpen = !popupOpen
    }

    onPopupOpenChanged: {
        if (popupOpen)
            Qt.callLater(function () { taskInput.forceActiveFocus() })
    }

    readonly property color ink: Commons.Color.popups.text
    readonly property color dim: Qt.darker(ink, 1.45)
    readonly property color up: "#3DDC97"
    readonly property color down: "#FF5F57"
    readonly property string statusPath: {
        var home = Quickshell.env("XDG_DATA_HOME") || ((Quickshell.env("HOME") || "") + "/.local/share")
        return home + "/cdr/builder-launcher/bar.json"
    }
    readonly property var todos: status.todos instanceof Array ? status.todos.slice(0, 3) : []
    readonly property var quotes: status.quotes instanceof Array ? status.quotes : []
    readonly property bool hasMedia: !!(status.media && status.media.title)
    readonly property bool hasReading: !!(status.reading && status.reading.title)
    readonly property string weatherText: status.weather && status.weather.temp ? String(status.weather.temp) : ""
    readonly property string weatherPlace: status.weather && status.weather.place ? plain(status.weather.place) : ""
    readonly property string alarmText: status.alarm && status.alarm.time ? String(status.alarm.time) : ""
    readonly property string alarmLabel: status.alarm && status.alarm.label ? plain(status.alarm.label) : ""
    readonly property bool hasFoot: weatherText.length > 0 || alarmText.length > 0

    property var status: ({})
    property double now: 0
    readonly property bool live: !!status.at && (now - status.at) < 6000

    function close() { popupOpen = false }

    function apply(raw) {
        try {
            var parsed = JSON.parse(raw || "")
            if (!parsed || typeof parsed !== "object" || Array.isArray(parsed))
                return
            status = parsed
        } catch (e) {
        }
    }

    // Tooltips go through the shell's text, which can read markup. The desk
    // already strips it; this is the second gate.
    function plain(value) {
        return String(value || "").replace(/[<>&]/g, "")
    }

    // file:///path from Qt.resolvedUrl. The helper prefers this checkout's
    // build, so a click cannot start an older package that treats `bar` as
    // a reason to open another window.
    readonly property string deskProgram: {
        var text = String(Qt.resolvedUrl("desk"))
        if (text.indexOf("file://") === 0)
            text = decodeURIComponent(text.slice(7))
        return text
    }

    // One try per eight seconds. A click after the desk has quit may try again.
    // The login spawn must not be undone by a later timer, so this is not polled.
    property double residentAttempt: 0
    property var pendingCommand: []
    property int commandTries: 0

    function ensureResident() {
        if (live || !bar)
            return
        var nowMs = Date.now()
        if (nowMs - residentAttempt < 8000)
            return
        residentAttempt = nowMs
        bar.run(Util.shellQuote(deskProgram) + " --resident")
    }

    function shellCommand(args) {
        if (!bar)
            return
        var all = [deskProgram, "bar"].concat(args)
        bar.run(all.map(function (part) { return Util.shellQuote(part) }).join(" "))
    }

    function command(args) {
        ensureResident()
        if (live) {
            shellCommand(args)
            return
        }
        pendingCommand = args
        commandTries = 0
        commandHold.start()
    }

    Timer {
        id: commandHold
        interval: 200
        repeat: true
        onTriggered: {
            root.commandTries += 1
            if (root.live) {
                stop()
                var held = root.pendingCommand
                root.pendingCommand = []
                root.shellCommand(held)
                return
            }
            if (root.commandTries >= 25)
                stop()
        }
    }

    Component.onCompleted: root.ensureResident()

    function focusDesk() {
        if (!bar)
            return
        bar.run("hyprctl dispatch " + Util.shellQuote("focuswindow class:^(builder-launcher)$"))
    }

    function openPage(page, id) {
        popupOpen = false
        var args = ["open"]
        if (page)
            args.push(page)
        if (id)
            args.push(id)
        command(args)
        focusDesk()
    }

    function addTypedTask(raw) {
        var title = String(raw || "").trim()
        if (!title)
            return
        command(["add", title])
        taskInput.text = ""
        taskInput.forceActiveFocus()
    }

    function openDesk() {
        if (live) {
            openPage("home")
            return
        }
        popupOpen = false
        if (bar)
            bar.run(Util.shellQuote(deskProgram) + " open")
    }

    function toneColor(tone) {
        if (tone === "up")
            return up
        if (tone === "down")
            return down
        return dim
    }

    Commons.Translations {
        id: strings
        directory: Qt.resolvedUrl("i18n")
    }

    FileView {
        id: statusFile
        path: root.statusPath
        watchChanges: true
        printErrors: false
        onLoaded: root.apply(text())
        onFileChanged: reload()
    }

    Timer {
        interval: 1000
        running: true
        repeat: true
        triggeredOnStart: true
        onTriggered: {
            root.now = Date.now()
            statusFile.reload()
        }
    }

    component Plain: Text {
        textFormat: Text.PlainText
        color: root.ink
        font.family: root.fontFamily
        font.pixelSize: Style.font.body
        elide: Text.ElideRight
    }

    // The letter only, in the bar's foreground. Its ink is centered the way
    // the shell centers its own glyphs, so the line box does not ride high.
    // The letter is a logo, so a right-to-left layout leaves it facing forward.
    component Mark: Item {
        id: mark
        property real glyphSize: Style.bar.iconSize
        property color markColor: root.foreground
        implicitWidth: glyphSize
        implicitHeight: glyphSize
        LayoutMirroring.enabled: false

        readonly property int renderedFontSize: Math.max(1, Math.round(glyphSize))
        readonly property int metricsScale: 100
        readonly property rect ink: Qt.rect(metrics.tightBoundingRect.x / metricsScale,
                                             metrics.tightBoundingRect.y / metricsScale,
                                             metrics.tightBoundingRect.width / metricsScale,
                                             metrics.tightBoundingRect.height / metricsScale)
        readonly property real tightW: Math.max(1, ink.width)
        readonly property real tightH: Math.max(1, ink.height)
        readonly property real paintedScale: width > 0 && height > 0
            ? Math.min(glyphSize, width, height) / Math.max(tightW, tightH)
            : 1

        TextMetrics {
            id: metrics
            font.family: "sans-serif"
            font.bold: true
            font.pixelSize: mark.renderedFontSize * mark.metricsScale
            text: "B"
        }

        Text {
            text: "B"
            textFormat: Text.PlainText
            color: mark.markColor
            font.family: "sans-serif"
            font.bold: true
            font.pixelSize: mark.renderedFontSize
            renderType: Text.QtRendering
            transformOrigin: Item.TopLeft
            scale: mark.paintedScale
            x: mark.width / 2 - (mark.ink.x + mark.tightW / 2) * mark.paintedScale
            y: mark.height / 2 - (baselineOffset + mark.ink.y + mark.tightH / 2) * mark.paintedScale
        }
    }

    Mark {
        anchors.fill: parent
        glyphSize: Style.bar.iconSize
        scale: root.pressScale
        transformOrigin: Item.Center
    }

    PopupCard {
        id: popup
        anchorItem: root
        bar: root.bar
        owner: root
        open: root.popupOpen
        contentWidth: popup.fittedContentWidth(Style.space(300))
        contentHeight: popup.fittedContentHeight(Math.min(body.implicitHeight, Style.space(420)))

        Flickable {
            id: flick
            anchors.fill: parent
            contentWidth: width
            contentHeight: body.implicitHeight
            clip: true
            boundsBehavior: Flickable.StopAtBounds
            flickableDirection: Flickable.VerticalFlick
            interactive: contentHeight > height

            Column {
                id: body
                width: flick.width
                spacing: Style.space(8)

                MouseArea {
                    width: body.width
                    height: heading.implicitHeight
                    hoverEnabled: true
                    cursorShape: Qt.PointingHandCursor
                    onClicked: root.openDesk()

                    Row {
                        id: heading
                        spacing: Style.space(8)

                        Mark { glyphSize: Style.font.body; markColor: root.ink }

                        Column {
                            width: body.width - Style.space(26)
                            spacing: 0

                            Plain {
                                width: parent.width
                                text: strings.t("bar.idle")
                                font.bold: true
                            }
                            Plain {
                                width: parent.width
                                text: strings.t("bar.open")
                                color: root.dim
                                font.pixelSize: Style.font.caption
                                visible: !root.live
                            }
                        }
                    }
                }

                Column {
                    visible: root.hasMedia
                    width: body.width
                    spacing: Style.space(4)

                    Rectangle { width: parent.width; height: 1; color: Qt.alpha(root.ink, 0.16) }

                    Row {
                        spacing: Style.space(4)
                        LayoutMirroring.enabled: false
                        LayoutMirroring.childrenInherit: true

                        Button {
                            text: root.status.media && root.status.media.playing ? "❚❚" : "▶"
                            tooltipText: root.status.media && root.status.media.playing ? strings.t("bar.pause") : strings.t("bar.play")
                            foreground: root.ink
                            horizontalPadding: Style.space(8)
                            verticalPadding: Style.space(4)
                            onClicked: root.command(["play-pause"])
                        }
                        Button {
                            text: "−15"
                            tooltipText: strings.t("bar.back")
                            foreground: root.ink
                            horizontalPadding: Style.space(8)
                            verticalPadding: Style.space(4)
                            onClicked: root.command(["back"])
                        }
                        Button {
                            text: "+30"
                            tooltipText: strings.t("bar.forward")
                            foreground: root.ink
                            horizontalPadding: Style.space(8)
                            verticalPadding: Style.space(4)
                            onClicked: root.command(["forward"])
                        }
                    }

                    Plain {
                        width: parent.width
                        text: root.plain(root.status.media ? root.status.media.title : "")
                    }
                    Plain {
                        width: parent.width
                        color: root.dim
                        font.pixelSize: Style.font.caption
                        text: root.plain((root.status.media && root.status.media.detail ? root.status.media.detail : "") + (root.status.media && root.status.media.position ? "  " + root.status.media.position : ""))
                        visible: text.length > 0
                    }
                }

                MouseArea {
                    visible: root.hasReading
                    width: body.width
                    height: Math.max(readingRow.implicitHeight, Style.space(22))
                    hoverEnabled: true
                    cursorShape: Qt.PointingHandCursor
                    onClicked: root.openPage("articles")

                    Column {
                        id: readingRow
                        width: parent.width
                        spacing: Style.space(4)

                        Rectangle { width: parent.width; height: 1; color: Qt.alpha(root.ink, 0.16) }
                        Plain {
                            width: parent.width
                            text: strings.t("bar.reading") + "  " + root.plain(root.status.reading ? root.status.reading.title : "")
                            color: root.dim
                        }
                    }
                }

                Column {
                    width: body.width
                    spacing: Style.space(4)

                    Rectangle { width: parent.width; height: 1; color: Qt.alpha(root.ink, 0.16) }

                    TextField {
                        id: taskInput
                        width: parent.width
                        placeholderText: strings.t("bar.add")
                        foreground: root.ink
                        font.family: root.fontFamily
                        font.pixelSize: Style.font.body
                        horizontalPadding: Style.space(8)
                        verticalPadding: Style.space(3)
                        maximumLength: 400
                        selectByMouse: true
                        onAccepted: root.addTypedTask(text)
                    }

                    Repeater {
                        model: root.todos
                        delegate: Item {
                            id: todo
                            required property var modelData
                            width: body.width
                            height: Style.space(26)

                            Plain {
                                id: circle
                                anchors.verticalCenter: parent.verticalCenter
                                text: "○"
                                color: root.dim
                            }
                            Plain {
                                id: titleLabel
                                x: circle.implicitWidth + Style.space(8)
                                width: parent.width - x
                                anchors.verticalCenter: parent.verticalCenter
                                text: root.plain(todo.modelData.title || "")
                            }
                            MouseArea {
                                width: circle.implicitWidth + Style.space(10)
                                anchors.left: parent.left
                                anchors.top: parent.top
                                anchors.bottom: parent.bottom
                                hoverEnabled: true
                                cursorShape: Qt.PointingHandCursor
                                onClicked: if (todo.modelData.id) root.command(["toggle", todo.modelData.id])
                            }
                            MouseArea {
                                anchors.left: titleLabel.left
                                anchors.right: parent.right
                                anchors.top: parent.top
                                anchors.bottom: parent.bottom
                                hoverEnabled: true
                                cursorShape: Qt.PointingHandCursor
                                onClicked: root.openPage("tasks")
                            }
                        }
                    }
                }

                Column {
                    visible: root.quotes.length > 0
                    width: body.width
                    spacing: Style.space(2)

                    Rectangle { width: parent.width; height: 1; color: Qt.alpha(root.ink, 0.16) }

                    Repeater {
                        model: root.quotes
                        delegate: MouseArea {
                            id: quote
                            required property var modelData
                            width: body.width
                            height: Style.space(22)
                            hoverEnabled: true
                            cursorShape: Qt.PointingHandCursor
                            onClicked: root.openPage("stocks")

                            Plain {
                                anchors.left: parent.left
                                anchors.verticalCenter: parent.verticalCenter
                                width: parent.width * 0.34
                                text: root.plain(quote.modelData.symbol || "")
                            }
                            Plain {
                                anchors.right: changeLabel.left
                                anchors.rightMargin: Style.space(8)
                                anchors.verticalCenter: parent.verticalCenter
                                width: Math.max(0, parent.width * 0.4)
                                horizontalAlignment: Text.AlignRight
                                color: root.dim
                                text: root.plain(quote.modelData.price || "")
                            }
                            Plain {
                                id: changeLabel
                                anchors.right: parent.right
                                anchors.verticalCenter: parent.verticalCenter
                                horizontalAlignment: Text.AlignRight
                                text: root.plain(quote.modelData.change || "")
                                color: root.toneColor(quote.modelData.tone)
                            }
                        }
                    }
                }

                MouseArea {
                    visible: root.hasFoot
                    width: body.width
                    height: foot.implicitHeight
                    hoverEnabled: true
                    cursorShape: Qt.PointingHandCursor
                    onClicked: root.openPage(root.weatherText.length > 0 ? "weather" : "clock")

                    Column {
                        id: foot
                        width: parent.width
                        spacing: Style.space(4)

                        Rectangle { width: parent.width; height: 1; color: Qt.alpha(root.ink, 0.16) }
                        Row {
                            width: parent.width
                            spacing: Style.space(12)

                            Plain {
                                text: root.plain(root.weatherPlace.length > 0 ? root.weatherPlace + "  " + root.weatherText : root.weatherText)
                                color: root.dim
                                visible: root.weatherText.length > 0
                            }
                            Plain {
                                text: root.plain(root.alarmLabel.length > 0 ? root.alarmText + "  " + root.alarmLabel : root.alarmText)
                                color: root.dim
                                visible: root.alarmText.length > 0
                            }
                        }
                    }
                }
            }
        }
    }
}
