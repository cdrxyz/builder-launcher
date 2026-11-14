#include "backend.h"

#include "library.h"
#include "merge.h"

#include <QDateTime>
#include <QDir>
#include <QFile>
#include <QJsonArray>
#include <QJsonDocument>
#include <QProcess>
#include <QSaveFile>
#include <QStandardPaths>
#include <QTimeZone>
#include <QUrlQuery>
#include <QXmlStreamReader>

namespace {

const QString kApi = QStringLiteral("https://builder.cdr.xyz");

QString dataDir() {
    const QString dir = QStandardPaths::writableLocation(QStandardPaths::AppDataLocation);
    QDir().mkpath(dir);
    return dir;
}

bool writePrivate(const QString &path, const QByteArray &bytes) {
    QSaveFile file(path);
    if (!file.open(QIODevice::WriteOnly))
        return false;
    if (file.write(bytes) != bytes.size())
        return false;
    if (!file.commit())
        return false;
    QFile::setPermissions(path, QFile::ReadOwner | QFile::WriteOwner);
    return true;
}

QJsonObject readObject(const QString &path) {
    QFile file(path);
    if (!file.open(QIODevice::ReadOnly))
        return {};
    return QJsonDocument::fromJson(file.readAll()).object();
}

QVariantMap row(const QString &id, const QString &title, const QString &subtitle, const QString &trailing, const QString &kind) {
    return {{QStringLiteral("id"), id},
            {QStringLiteral("title"), title},
            {QStringLiteral("subtitle"), subtitle},
            {QStringLiteral("trailing"), trailing},
            {QStringLiteral("kind"), kind}};
}

QString jsonError(const QByteArray &body, int status) {
    const auto object = QJsonDocument::fromJson(body).object();
    const QString message = object.value(QStringLiteral("error")).toString();
    if (!message.isEmpty())
        return message;
    if (status == 0)
        return QStringLiteral("Could not reach builder.cdr.xyz.");
    return QStringLiteral("HTTP %1").arg(status);
}

QString weatherCode(int code) {
    if (code == 0)
        return QStringLiteral("clear");
    if (code <= 3)
        return QStringLiteral("cloud");
    if (code <= 48)
        return QStringLiteral("fog");
    if (code <= 67)
        return QStringLiteral("rain");
    if (code <= 77)
        return QStringLiteral("snow");
    if (code <= 82)
        return QStringLiteral("showers");
    return QStringLiteral("storm");
}

} // namespace

Backend::Backend(QObject *parent) : QObject(parent), m_audio(this), m_player(this) {
    m_player.setAudioOutput(&m_audio);
    m_syncTimer.setSingleShot(true);
    connect(&m_syncTimer, &QTimer::timeout, this, &Backend::syncIfDue);
    m_clockTimer.setInterval(15000);
    connect(&m_clockTimer, &QTimer::timeout, this, &Backend::checkAlarms);
    m_clockTimer.start();
    connect(&m_player, &QMediaPlayer::playbackStateChanged, this, &Backend::changed);
    connect(&m_player, &QMediaPlayer::positionChanged, this, &Backend::changed);
    connect(&m_player, &QMediaPlayer::errorOccurred, this, [this](QMediaPlayer::Error, const QString &message) {
        setStatus(message.isEmpty() ? tr("Playback failed.") : message);
    });
    loadLocal();
    if (!m_token.isEmpty())
        pull();
    rebuild();
}

void Backend::loadDemo() {
    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    m_doc = emptyDoc(now);
    m_doc = addTask(m_doc, QStringLiteral("Sketch the joint"), now - 2000);
    m_doc = addTask(m_doc, QStringLiteral("Order cedar"), now - 1000);
    m_doc = addNote(m_doc, QStringLiteral("Shop notes"), now);
    m_email = QStringLiteral("builder@example.com");
    m_token = QStringLiteral("demo");
    m_page = QStringLiteral("home");
    rebuild();
}

void Backend::loadLocal() {
    const auto session = readObject(dataDir() + QStringLiteral("/session.json"));
    m_email = session.value(QStringLiteral("email")).toString();
    m_token = session.value(QStringLiteral("token")).toString();
    m_refresh = session.value(QStringLiteral("refreshToken")).toString();
    m_includeAi = session.value(QStringLiteral("includeAi")).toBool();
    const auto vault = readObject(dataDir() + QStringLiteral("/vault.json"));
    m_doc = vault.value(QStringLiteral("document")).toObject();
    if (m_doc.isEmpty())
        m_doc = emptyDoc(QDateTime::currentMSecsSinceEpoch());
    m_revision = vault.value(QStringLiteral("revision")).toInt();
    m_dirty = vault.value(QStringLiteral("dirty")).toBool();
}

void Backend::saveVault() {
    QJsonObject payload;
    payload.insert(QStringLiteral("document"), m_doc);
    payload.insert(QStringLiteral("revision"), m_revision);
    payload.insert(QStringLiteral("dirty"), m_dirty);
    writePrivate(dataDir() + QStringLiteral("/vault.json"), QJsonDocument(payload).toJson(QJsonDocument::Compact));
}

void Backend::saveSession() {
    QJsonObject payload;
    payload.insert(QStringLiteral("email"), m_email);
    payload.insert(QStringLiteral("token"), m_token);
    payload.insert(QStringLiteral("refreshToken"), m_refresh);
    payload.insert(QStringLiteral("includeAi"), m_includeAi);
    writePrivate(dataDir() + QStringLiteral("/session.json"), QJsonDocument(payload).toJson(QJsonDocument::Compact));
}

void Backend::setStatus(const QString &status) {
    if (m_status == status)
        return;
    m_status = status;
    emit changed();
}

void Backend::setDraft(const QString &draft) {
    if (m_draft == draft)
        return;
    m_draft = draft;
    if (m_prompt == QLatin1String("/"))
        rebuild();
    else
        emit changed();
}

QString Backend::title() const {
    const QString page = m_page;
    if (page == QLatin1String("home"))
        return tr("Home");
    if (page == QLatin1String("tasks"))
        return tr("Tasks");
    if (page == QLatin1String("notes") || page == QLatin1String("note"))
        return tr("Notes");
    if (page == QLatin1String("stocks"))
        return tr("Stocks");
    if (page == QLatin1String("podcasts") || page == QLatin1String("show") || page == QLatin1String("episode"))
        return tr("Podcasts");
    if (page == QLatin1String("articles") || page == QLatin1String("article"))
        return tr("Articles");
    if (page == QLatin1String("videos") || page == QLatin1String("video"))
        return tr("Videos");
    if (page == QLatin1String("weather"))
        return tr("Weather");
    if (page == QLatin1String("clock"))
        return tr("Clock");
    if (page == QLatin1String("chat"))
        return tr("Ask");
    if (page == QLatin1String("settings"))
        return tr("Settings");
    return tr("Builder Launcher");
}

QString Backend::syncLabel() const {
    if (m_syncing)
        return tr("Syncing…");
    if (m_token.isEmpty())
        return tr("Not signed in");
    if (m_dirty)
        return tr("Waiting to sync");
    return tr("Synced");
}

QString Backend::weatherPlace() const {
    return m_doc.value(QStringLiteral("settings")).toObject().value(QStringLiteral("weatherPlace")).toString();
}

QString Backend::commandHint() const {
    if (m_prompt == QLatin1String("-"))
        return tr("Add a task");
    if (m_prompt == QLatin1String("+"))
        return tr("New note");
    if (m_prompt == QLatin1String("$"))
        return tr("Symbol");
    if (m_prompt == QLatin1String("?"))
        return tr("Ask");
    if (m_prompt == QLatin1String("/"))
        return tr("Command");
    if (m_prompt == QLatin1String(">"))
        return tr("Search");
    return {};
}

QString Backend::positionLabel() const {
    const qint64 pos = m_player.position() / 1000;
    const qint64 dur = m_player.duration() / 1000;
    auto fmt = [](qint64 seconds) {
        return QStringLiteral("%1:%2").arg(seconds / 60).arg(seconds % 60, 2, 10, QLatin1Char('0'));
    };
    if (dur <= 0)
        return fmt(pos);
    return fmt(pos) + QStringLiteral(" / ") + fmt(dur);
}

QStringList Backend::shortcutRows() const {
    return {tr("Enter submits the command"),
            tr("/ opens a page. Tab cycles the prompt"),
            tr("j and k move. Space toggles. x deletes"),
            tr("Ctrl+Z undoes a local edit"),
            tr("? on the list shows this. ? in the bar asks"),
            tr("Ctrl+Q quits")};
}

void Backend::remember() {
    m_undo.append(m_doc);
    if (m_undo.size() > 40)
        m_undo.removeFirst();
    m_redo.clear();
}

void Backend::mutate(const QJsonObject &next, const QString &hold) {
    remember();
    m_doc = next;
    m_dirty = true;
    saveVault();
    rebuild();
    scheduleSync(hold);
}

void Backend::scheduleSync(const QString &hold) {
    const int wait = hold == QLatin1String("text") ? 8000 : hold == QLatin1String("order") ? 5000 : 2000;
    m_holdUntil = QDateTime::currentMSecsSinceEpoch() + wait;
    m_syncTimer.start(wait);
}

void Backend::syncIfDue() {
    if (m_composing && QDateTime::currentMSecsSinceEpoch() < m_holdUntil) {
        m_syncTimer.start(1000);
        return;
    }
    if (m_dirty && !m_token.isEmpty() && m_token != QLatin1String("demo"))
        push();
}

void Backend::go(const QString &page) {
    m_page = page;
    m_selected = 0;
    m_detailId.clear();
    m_detail.clear();
    if (page == QLatin1String("notes") || page == QLatin1String("note"))
        m_prompt = QStringLiteral("+");
    else if (page == QLatin1String("stocks"))
        m_prompt = QStringLiteral("$");
    else if (page == QLatin1String("podcasts") || page == QLatin1String("articles") || page == QLatin1String("videos") ||
             page == QLatin1String("show"))
        m_prompt = QStringLiteral(">");
    else if (page == QLatin1String("chat"))
        m_prompt = QStringLiteral("?");
    else
        m_prompt = QStringLiteral("-");
    m_draft.clear();
    rebuild();
    if (page == QLatin1String("weather"))
        refreshWeather();
}

void Backend::setPrompt(const QString &glyph) {
    if (glyph.isEmpty())
        return;
    m_prompt = glyph.left(1);
    m_draft.clear();
    rebuild();
}

void Backend::moveSelection(int delta) {
    if (m_rows.isEmpty())
        return;
    m_selected = qBound(0, m_selected + delta, m_rows.size() - 1);
    emit changed();
}

QString Backend::selectedId() const {
    if (m_selected < 0 || m_selected >= m_rows.size())
        return {};
    return m_rows.at(m_selected).toMap().value(QStringLiteral("id")).toString();
}

void Backend::rebuild() {
    m_rows.clear();
    m_menu.clear();
    const auto add = [this](const QVariantMap &entry) { m_rows.append(entry); };
    if (m_prompt == QLatin1String("/")) {
        const QString query = m_draft.trimmed().toLower();
        const QList<QPair<QString, QString>> commands = {
            {QStringLiteral("articles"), tr("saved articles")}, {QStringLiteral("clock"), tr("alarms")},
            {QStringLiteral("help"), tr("commands")},           {QStringLiteral("home"), tr("home")},
            {QStringLiteral("notes"), tr("all notes")},         {QStringLiteral("podcasts"), tr("all podcasts")},
            {QStringLiteral("pull"), tr("sync down")},          {QStringLiteral("push"), tr("sync up")},
            {QStringLiteral("settings"), tr("settings")},       {QStringLiteral("stocks"), tr("watchlist")},
            {QStringLiteral("tasks"), tr("all tasks")},         {QStringLiteral("videos"), tr("saved videos")},
            {QStringLiteral("weather"), tr("forecast")}};
        for (const auto &command : commands) {
            if (!query.isEmpty() && !command.first.startsWith(query))
                continue;
            m_menu.append(row(command.first, command.first, command.second, {}, QStringLiteral("slash")));
        }
    }
    if (m_page == QLatin1String("home")) {
        int shown = 0;
        for (const auto &item : openTodos(m_doc)) {
            if (shown++ >= 3)
                break;
            add(row(item.value(QStringLiteral("id")).toString(), item.value(QStringLiteral("text")).toString(), tr("task"), {},
                    QStringLiteral("todo")));
        }
        if (!weatherPlace().isEmpty())
            add(row(QStringLiteral("weather"), weatherPlace(), tr("weather"), {}, QStringLiteral("link")));
        for (const auto &value : m_doc.value(QStringLiteral("alarms")).toArray()) {
            const auto alarm = value.toObject();
            if (alarm.value(QStringLiteral("enabled")).isBool() && !alarm.value(QStringLiteral("enabled")).toBool())
                continue;
            add(row(alarm.value(QStringLiteral("id")).toString(),
                    formatAlarmTime(alarm.value(QStringLiteral("hour")).toInt(), alarm.value(QStringLiteral("minute")).toInt()),
                    alarm.value(QStringLiteral("label")).toString(), formatAlarmDays(alarmDays(alarm)), QStringLiteral("alarm")));
            break;
        }
    } else if (m_page == QLatin1String("tasks")) {
        for (const auto &item : openTodos(m_doc))
            add(row(item.value(QStringLiteral("id")).toString(), item.value(QStringLiteral("text")).toString(), {}, {},
                    QStringLiteral("todo")));
        for (const auto &item : doneTodos(m_doc))
            add(row(item.value(QStringLiteral("id")).toString(), item.value(QStringLiteral("text")).toString(), tr("done"), {},
                    QStringLiteral("done")));
    } else if (m_page == QLatin1String("notes")) {
        for (const auto &item : notesOf(m_doc))
            add(row(item.value(QStringLiteral("id")).toString(), noteTitle(item.value(QStringLiteral("text")).toString()), {}, {},
                    QStringLiteral("note")));
    } else if (m_page == QLatin1String("note")) {
        const auto item = findById(m_doc.value(QStringLiteral("items")).toArray(), m_detailId);
        if (!m_composing)
            m_detail = item.value(QStringLiteral("text")).toString();
    } else if (m_page == QLatin1String("clock")) {
        for (const auto &value : m_doc.value(QStringLiteral("alarms")).toArray()) {
            const auto alarm = value.toObject();
            const bool on = !alarm.value(QStringLiteral("enabled")).isBool() || alarm.value(QStringLiteral("enabled")).toBool();
            add(row(alarm.value(QStringLiteral("id")).toString(),
                    formatAlarmTime(alarm.value(QStringLiteral("hour")).toInt(), alarm.value(QStringLiteral("minute")).toInt()),
                    alarm.value(QStringLiteral("label")).toString(),
                    (on ? tr("on") : tr("off")) + QStringLiteral("  ") + formatAlarmDays(alarmDays(alarm)), QStringLiteral("alarm")));
        }
        for (const auto &value : m_doc.value(QStringLiteral("zones")).toArray()) {
            const auto zone = value.toObject();
            const QDateTime there = QDateTime::currentDateTimeUtc().toTimeZone(QTimeZone(zone.value(QStringLiteral("zoneId")).toString().toUtf8()));
            add(row(zone.value(QStringLiteral("id")).toString(), zone.value(QStringLiteral("label")).toString(),
                    zone.value(QStringLiteral("zoneId")).toString(), there.toString(QStringLiteral("HH:mm")), QStringLiteral("zone")));
        }
    } else if (m_page == QLatin1String("stocks")) {
        for (const auto &value : m_doc.value(QStringLiteral("watchlist")).toArray()) {
            const auto item = value.toObject();
            add(row(item.value(QStringLiteral("symbol")).toString(), item.value(QStringLiteral("symbol")).toString(),
                    item.value(QStringLiteral("name")).toString(), {}, QStringLiteral("stock")));
        }
    } else if (m_page == QLatin1String("podcasts")) {
        for (const auto &value : m_doc.value(QStringLiteral("podcasts")).toObject().value(QStringLiteral("shows")).toArray()) {
            const auto show = value.toObject();
            add(row(show.value(QStringLiteral("feedUrl")).toString(), show.value(QStringLiteral("title")).toString(),
                    show.value(QStringLiteral("author")).toString(), {}, QStringLiteral("show")));
        }
    } else if (m_page == QLatin1String("show")) {
        for (const auto &value : m_doc.value(QStringLiteral("podcasts")).toObject().value(QStringLiteral("episodes")).toArray()) {
            const auto episode = value.toObject();
            if (episode.value(QStringLiteral("feedUrl")).toString() != m_detailId)
                continue;
            add(row(episode.value(QStringLiteral("id")).toString(), episode.value(QStringLiteral("title")).toString(), {}, {},
                    QStringLiteral("episode")));
        }
    } else if (m_page == QLatin1String("articles")) {
        for (const auto &value : m_doc.value(QStringLiteral("reads")).toObject().value(QStringLiteral("articles")).toArray()) {
            const auto article = value.toObject();
            add(row(article.value(QStringLiteral("id")).toString(), article.value(QStringLiteral("title")).toString(),
                    article.value(QStringLiteral("publication")).toString(), {}, QStringLiteral("article")));
        }
    } else if (m_page == QLatin1String("article")) {
        const auto article = findById(m_doc.value(QStringLiteral("reads")).toObject().value(QStringLiteral("articles")).toArray(), m_detailId);
        m_detail = article.value(QStringLiteral("text")).toString();
    } else if (m_page == QLatin1String("videos")) {
        for (const auto &value : m_doc.value(QStringLiteral("videos")).toObject().value(QStringLiteral("videos")).toArray()) {
            const auto video = value.toObject();
            add(row(video.value(QStringLiteral("id")).toString(), video.value(QStringLiteral("title")).toString(),
                    video.value(QStringLiteral("author")).toString(), {}, QStringLiteral("video")));
        }
    } else if (m_page == QLatin1String("chat")) {
        const auto chats = m_doc.value(QStringLiteral("chats")).toArray();
        const auto thread = m_detailId.isEmpty() ? (chats.isEmpty() ? QJsonObject() : chats.last().toObject())
                                                 : findById(chats, m_detailId);
        if (!thread.isEmpty())
            m_detailId = thread.value(QStringLiteral("id")).toString();
        for (const auto &value : thread.value(QStringLiteral("messages")).toArray()) {
            const auto message = value.toObject();
            add(row(message.value(QStringLiteral("id")).toString(), message.value(QStringLiteral("content")).toString(),
                    message.value(QStringLiteral("role")).toString(), {}, QStringLiteral("message")));
        }
    }
    if (m_selected >= m_rows.size())
        m_selected = qMax(0, m_rows.size() - 1);
    emit changed();
}
