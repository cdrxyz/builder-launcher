#include "backend.h"

#include "library.h"
#include "merge.h"

#include <QDateTime>
#include <QJsonArray>
#include <QJsonDocument>
#include <QProcess>
#include <QUrl>
#include <QUrlQuery>
#include <QXmlStreamReader>

namespace {

QJsonObject objectOf(const QByteArray &body) {
    return QJsonDocument::fromJson(body).object();
}

QString failure(const QByteArray &body, int status, const QString &transport) {
    if (!transport.isEmpty() && status == 0)
        return transport;
    const QString message = objectOf(body).value(QStringLiteral("error")).toString();
    if (!message.isEmpty())
        return message;
    return status == 0 ? QStringLiteral("Could not reach builder.cdr.xyz.") : QStringLiteral("HTTP %1").arg(status);
}

} // namespace

bool looksLikeUrl(const QString &text);

void Backend::api(const QString &method, const QString &path, const QJsonObject &body, bool refresh,
                  const std::function<void(QJsonObject, QString)> &done) {
    const QByteArray payload = body.isEmpty() && method == QLatin1String("GET") ? QByteArray() : QJsonDocument(body).toJson(QJsonDocument::Compact);
    m_http.request(method, QUrl(m_api + path), method == QLatin1String("GET") ? QByteArray() : payload, m_token,
                   [this, method, path, body, refresh, done](int status, const QByteArray &bytes, const QString &transport) {
                       if (status == 401 && refresh && !m_refresh.isEmpty() && !m_refreshing) {
                           m_refreshing = true;
                           QJsonObject refreshBody;
                           refreshBody.insert(QStringLiteral("refreshToken"), m_refresh);
                           m_http.request(QStringLiteral("POST"), QUrl(m_api + QStringLiteral("/api/auth/refresh")),
                                          QJsonDocument(refreshBody).toJson(QJsonDocument::Compact), {},
                                          [this, method, path, body, done](int code, const QByteArray &raw, const QString &error) {
                                              m_refreshing = false;
                                              const auto next = objectOf(raw);
                                              if (code < 200 || code >= 300 || next.value(QStringLiteral("token")).toString().isEmpty()) {
                                                  done({}, error.isEmpty() ? failure(raw, code, error) : error);
                                                  return;
                                              }
                                              m_token = next.value(QStringLiteral("token")).toString();
                                              if (!next.value(QStringLiteral("refreshToken")).toString().isEmpty())
                                                  m_refresh = next.value(QStringLiteral("refreshToken")).toString();
                                              saveSession();
                                              api(method, path, body, false, done);
                                          });
                           return;
                       }
                       if (status < 200 || status >= 300) {
                           done({}, failure(bytes, status, transport));
                           return;
                       }
                       done(objectOf(bytes), {});
                   });
}

void Backend::afterAuth(const QJsonObject &body) {
    m_email = body.value(QStringLiteral("email")).toString();
    m_token = body.value(QStringLiteral("token")).toString();
    m_refresh = body.value(QStringLiteral("refreshToken")).toString();
    saveSession();
    emit changed();
    pull();
}

void Backend::signIn(const QString &email, const QString &password) {
    QJsonObject body{{QStringLiteral("email"), email.trimmed()}, {QStringLiteral("password"), password}};
    setStatus(tr("Signing in…"));
    m_token.clear();
    api(QStringLiteral("POST"), QStringLiteral("/api/auth/login"), body, false, [this](QJsonObject body, QString error) {
        if (!error.isEmpty()) {
            setStatus(error);
            return;
        }
        afterAuth(body);
    });
}

void Backend::signUp(const QString &email, const QString &password, const QString &code) {
    if (!signupCodeOpens(code)) {
        joinWaitlist(email);
        return;
    }
    QJsonObject body{{QStringLiteral("email"), email.trimmed()},
                     {QStringLiteral("password"), password},
                     {QStringLiteral("code"), code.trimmed()}};
    setStatus(tr("Creating the account…"));
    api(QStringLiteral("POST"), QStringLiteral("/api/auth/signup"), body, false, [this](QJsonObject body, QString error) {
        if (!error.isEmpty()) {
            setStatus(error);
            return;
        }
        afterAuth(body);
    });
}

void Backend::joinWaitlist(const QString &email) {
    QJsonObject body{{QStringLiteral("email"), email.trimmed()}};
    api(QStringLiteral("POST"), QStringLiteral("/api/waitlist"), body, false, [this](QJsonObject, QString error) {
        setStatus(error.isEmpty() ? tr("Joined the waitlist.") : error);
    });
}

void Backend::signOut() {
    QJsonObject body{{QStringLiteral("refreshToken"), m_refresh}};
    const QString token = m_token;
    m_token.clear();
    m_refresh.clear();
    m_email.clear();
    saveSession();
    if (!token.isEmpty() && token != QLatin1String("demo"))
        api(QStringLiteral("POST"), QStringLiteral("/api/auth/logout"), body, false, [](QJsonObject, QString) {});
    setStatus(tr("Signed out."));
    rebuild();
}

void Backend::setIncludeAi(bool on) {
    m_includeAi = on;
    saveSession();
    emit changed();
}

void Backend::saveApiKey(const QString &key) {
    if (!m_includeAi) {
        setStatus(tr("Turn on Include AI credentials to sync the key."));
        return;
    }
    auto settings = m_doc.value(QStringLiteral("settings")).toObject();
    settings.insert(QStringLiteral("apiKey"), key.trimmed());
    auto next = m_doc;
    next.insert(QStringLiteral("settings"), settings);
    mutate(touchExported(next, QDateTime::currentMSecsSinceEpoch()));
    setStatus(key.trimmed().isEmpty() ? tr("Cleared the API key.") : tr("Saved the API key."));
}

void Backend::pull() {
    if (m_token.isEmpty() || m_token == QLatin1String("demo")) {
        setStatus(tr("Sign in to sync."));
        return;
    }
    m_syncing = true;
    emit changed();
    api(QStringLiteral("GET"), QStringLiteral("/api/vault"), {}, true, [this](QJsonObject body, QString error) {
        m_syncing = false;
        if (!error.isEmpty()) {
            setStatus(error);
            emit changed();
            return;
        }
        const bool existed = !body.value(QStringLiteral("document")).isNull() && body.value(QStringLiteral("document")).isObject();
        applyRemote(body.value(QStringLiteral("document")).toObject(), existed);
    });
}

void Backend::push() {
    if (m_token.isEmpty() || m_token == QLatin1String("demo")) {
        setStatus(tr("Sign in to sync."));
        return;
    }
    m_syncing = true;
    emit changed();
    const auto local = m_doc;
    QJsonObject body;
    body.insert(QStringLiteral("document"), slimDoc(m_doc));
    api(QStringLiteral("PUT"), QStringLiteral("/api/vault"), body, true, [this, local](QJsonObject body, QString error) {
        m_syncing = false;
        if (!error.isEmpty()) {
            setStatus(error);
            emit changed();
            return;
        }
        m_revision = body.value(QStringLiteral("revision")).toInt();
        m_doc = restoreLocalMedia(body.value(QStringLiteral("document")).toObject(), local);
        m_dirty = false;
        saveVault();
        setStatus(tr("Synced."));
        rebuild();
    });
}

void Backend::applyRemote(const QJsonObject &remote, bool existed) {
    if (!existed) {
        if (m_dirty)
            push();
        else
            setStatus(tr("Signed in. Nothing in the account yet."));
        emit changed();
        return;
    }
    const auto local = m_doc;
    const auto merged = mergeDocs(local, remote);
    m_revision = 0;
    if (m_dirty || slimDiffers(merged, remote)) {
        m_doc = merged;
        push();
        return;
    }
    m_doc = restoreLocalMedia(merged, local);
    m_dirty = false;
    saveVault();
    setStatus(tr("Synced."));
    rebuild();
}

void Backend::submit() {
    const QString text = m_draft.trimmed();
    if (m_prompt == QLatin1String("@") || m_prompt == QLatin1String("#")) {
        setStatus(tr("Use the Android app for text and calls."));
        return;
    }
    if (m_prompt == QLatin1String("/")) {
        const QString name = text.toLower();
        m_draft.clear();
        if (name == QLatin1String("pull"))
            pull();
        else if (name == QLatin1String("push"))
            push();
        else if (name == QLatin1String("help"))
            go(QStringLiteral("help"));
        else if (!name.isEmpty())
            go(name == QLatin1String("podcasts") ? QStringLiteral("podcasts") : name);
        return;
    }
    if (m_prompt == QLatin1String("-")) {
        if (m_page == QLatin1String("clock")) {
            const auto alarms = parseAlarmInputs(text);
            if (!alarms.isEmpty()) {
                mutate(addAlarms(m_doc, alarms, QDateTime::currentMSecsSinceEpoch()));
                m_draft.clear();
                setStatus(tr("Alarm saved."));
                return;
            }
        }
        if (looksLikeMath(text)) {
            setStatus(evaluateMath(text));
            m_draft.clear();
            return;
        }
        if (text.isEmpty())
            return;
        mutate(addTask(m_doc, text, QDateTime::currentMSecsSinceEpoch()));
        m_draft.clear();
        return;
    }
    if (m_prompt == QLatin1String("+")) {
        if (text.isEmpty()) {
            go(QStringLiteral("notes"));
            return;
        }
        const auto next = addNote(m_doc, text, QDateTime::currentMSecsSinceEpoch());
        m_detailId = next.value(QStringLiteral("items")).toArray().first().toObject().value(QStringLiteral("id")).toString();
        mutate(next);
        m_page = QStringLiteral("note");
        m_draft.clear();
        rebuild();
        return;
    }
    if (m_prompt == QLatin1String("$")) {
        if (text.isEmpty()) {
            go(QStringLiteral("stocks"));
            return;
        }
        searchStock(text);
        m_draft.clear();
        return;
    }
    if (m_prompt == QLatin1String("?")) {
        if (text.isEmpty())
            return;
        ask(text);
        m_draft.clear();
        return;
    }
    if (m_prompt == QLatin1String(">")) {
        if (text.isEmpty())
            return;
        if (m_page == QLatin1String("articles") || looksLikeUrl(text))
            saveArticle(text);
        else if (m_page == QLatin1String("videos") || !youtubeIdOf(text).isEmpty())
            saveVideo(text);
        else
            searchPodcast(text);
        m_draft.clear();
        return;
    }
}

bool Backend::signupOpens(const QString &code) const {
    return signupCodeOpens(code);
}

void Backend::undo() {
    if (m_undo.isEmpty())
        return;
    m_redo.append(m_doc);
    m_doc = m_undo.takeLast();
    m_dirty = true;
    saveVault();
    rebuild();
    scheduleSync({});
}

void Backend::redo() {
    if (m_redo.isEmpty())
        return;
    m_undo.append(m_doc);
    m_doc = m_redo.takeLast();
    m_dirty = true;
    saveVault();
    rebuild();
    scheduleSync({});
}

void Backend::toggleSelection() {
    const QString id = selectedId();
    if (id.isEmpty())
        return;
    const QString kind = m_rows.at(m_selected).toMap().value(QStringLiteral("kind")).toString();
    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    if (kind == QLatin1String("todo") || kind == QLatin1String("done"))
        mutate(toggleTask(m_doc, id, now));
    else if (kind == QLatin1String("alarm"))
        mutate(toggleAlarm(m_doc, id, now));
}

void Backend::deleteSelection() {
    const QString id = selectedId();
    if (id.isEmpty())
        return;
    const QString kind = m_rows.at(m_selected).toMap().value(QStringLiteral("kind")).toString();
    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    if (kind == QLatin1String("alarm"))
        mutate(deleteAlarm(m_doc, id, now));
    else if (kind == QLatin1String("todo") || kind == QLatin1String("done") || kind == QLatin1String("note"))
        mutate(deleteItem(m_doc, id, now));
}

void Backend::activateSelection() {
    const QString id = selectedId();
    if (id.isEmpty())
        return;
    const QString kind = m_rows.at(m_selected).toMap().value(QStringLiteral("kind")).toString();
    if (kind == QLatin1String("todo") || kind == QLatin1String("done")) {
        toggleSelection();
        return;
    }
    if (kind == QLatin1String("note")) {
        m_detailId = id;
        m_page = QStringLiteral("note");
        rebuild();
        return;
    }
    if (kind == QLatin1String("show")) {
        m_detailId = id;
        m_page = QStringLiteral("show");
        rebuild();
        return;
    }
    if (kind == QLatin1String("episode")) {
        const auto episode = findById(m_doc.value(QStringLiteral("podcasts")).toObject().value(QStringLiteral("episodes")).toArray(), id);
        playUrl(episode.value(QStringLiteral("enclosureUrl")).toString(), episode.value(QStringLiteral("title")).toString(), tr("Podcasts"));
        return;
    }
    if (kind == QLatin1String("article")) {
        m_detailId = id;
        m_page = QStringLiteral("article");
        rebuild();
        return;
    }
    if (kind == QLatin1String("video")) {
        resolveVideo(id);
        return;
    }
    if (kind == QLatin1String("hit")) {
        const auto map = m_rows.at(m_selected).toMap();
        subscribeShow(id, map.value(QStringLiteral("title")).toString(), map.value(QStringLiteral("subtitle")).toString());
        return;
    }
    if (kind == QLatin1String("link"))
        go(id);
    if (kind == QLatin1String("slash"))
        go(id);
}

void Backend::setComposing(bool on) {
    m_composing = on;
}

void Backend::saveDetail(const QString &text) {
    if (m_detailId.isEmpty() || m_page != QLatin1String("note"))
        return;
    if (text == findById(m_doc.value(QStringLiteral("items")).toArray(), m_detailId).value(QStringLiteral("text")).toString())
        return;
    mutate(updateItemText(m_doc, m_detailId, text, QDateTime::currentMSecsSinceEpoch()), QStringLiteral("text"));
}

void Backend::playPause() {
    if (m_player.playbackState() == QMediaPlayer::PlayingState)
        m_player.pause();
    else
        m_player.play();
}

void Backend::playUrl(const QString &url, const QString &title, const QString &show) {
    if (url.isEmpty()) {
        setStatus(tr("Nothing to play."));
        return;
    }
    m_player.setSource(QUrl(url));
    m_player.play();
    m_nowPlaying = title + QStringLiteral(" — ") + show;
    setStatus(tr("Playing."));
}

void Backend::dismissAlert() {
    if (m_alertId.isEmpty())
        return;
    mutate(acknowledgeAlarm(m_doc, m_alertId, QDateTime::currentMSecsSinceEpoch()));
    m_alert.clear();
    m_alertTime.clear();
    m_alertId.clear();
    m_player.stop();
    rebuild();
}

void Backend::snoozeAlert() {
    if (m_alertId.isEmpty())
        return;
    mutate(snoozeAlarm(m_doc, m_alertId, QDateTime::currentMSecsSinceEpoch()));
    m_alert.clear();
    m_alertTime.clear();
    m_alertId.clear();
    m_player.stop();
    setStatus(tr("Snoozed 8 min."));
}

void Backend::checkAlarms() {
    if (!m_alertId.isEmpty())
        return;
    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    for (const auto &value : m_doc.value(QStringLiteral("alarms")).toArray()) {
        const auto alarm = value.toObject();
        if (!alarmDue(alarm, now))
            continue;
        m_alertId = alarm.value(QStringLiteral("id")).toString();
        m_alert = alarm.value(QStringLiteral("label")).toString();
        if (m_alert.isEmpty())
            m_alert = tr("Alarm");
        m_alertTime = formatAlarmTime(alarm.value(QStringLiteral("hour")).toInt(), alarm.value(QStringLiteral("minute")).toInt());
        mutate(stampAlarmFired(m_doc, m_alertId, now));
        notify(m_alert, m_alertTime);
        emit changed();
        return;
    }
}

void Backend::notify(const QString &title, const QString &body) {
    QProcess::startDetached(QStringLiteral("notify-send"), {QStringLiteral("--urgency=critical"), title, body});
}

bool looksLikeUrl(const QString &text) {
    return text.contains(QLatin1String("://")) || (text.contains('.') && !text.contains(' '));
}
