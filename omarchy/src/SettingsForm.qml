import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts

Flickable {
    clip: true
    contentHeight: form.implicitHeight
    ColumnLayout {
        id: form
        width: parent.width
        spacing: 8
        Label { text: qsTr("Account"); color: "#9a9aa3" }
        TextField {
            id: email
            property string accountEmail: backend.email
            placeholderText: qsTr("Email")
            text: accountEmail
            Material.accent: theme.accent
            Layout.fillWidth: true
            onAccountEmailChanged: if (!activeFocus) text = accountEmail
        }
        TextField { id: password; placeholderText: qsTr("Password"); echoMode: TextInput.Password; Material.accent: theme.accent; Layout.fillWidth: true }
        TextField { id: code; placeholderText: qsTr("Invite code"); Material.accent: theme.accent; Layout.fillWidth: true }
        RowLayout {
            Button {
                text: qsTr("Sign in")
                Material.accent: theme.accent
                onClicked: backend.signIn(email.text, password.text)
            }
            Button {
                text: qsTr("Sign out")
                visible: backend.signedIn
                onClicked: backend.signOut()
            }
            Button {
                text: backend.signupOpens(code.text) ? qsTr("Create account") : qsTr("Join waitlist")
                visible: !backend.signedIn
                onClicked: backend.signupOpens(code.text) ? backend.signUp(email.text, password.text, code.text) : backend.joinWaitlist(email.text)
            }
            Button { text: qsTr("Sync down"); onClicked: backend.pull() }
            Button { text: qsTr("Sync up"); onClicked: backend.push() }
        }
        Label { text: qsTr("AI"); color: "#9a9aa3" }
        CheckBox {
            text: qsTr("Include AI credentials")
            checked: backend.includeAi
            onToggled: backend.setIncludeAi(checked)
        }
        TextField {
            id: apiKey
            placeholderText: qsTr("API key")
            echoMode: TextInput.Password
            Material.accent: theme.accent
            Layout.fillWidth: true
            onAccepted: backend.saveApiKey(text)
        }
        Label {
            text: qsTr("The Worker forwards the key and does not store it. A home Hermes box on the LAN stays on the phone.")
            wrapMode: Text.WordWrap
            color: "#9a9aa3"
            Layout.fillWidth: true
        }
        Label { text: qsTr("Weather"); color: "#9a9aa3" }
        TextField {
            id: place
            property string savedPlace: backend.weatherPlace
            placeholderText: qsTr("Place")
            text: savedPlace
            Material.accent: theme.accent
            Layout.fillWidth: true
            onSavedPlaceChanged: if (!activeFocus) text = savedPlace
            onAccepted: backend.setWeatherPlace(text)
        }
    }
}
