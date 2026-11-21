#include "backend.h"

#include "library.h"
#include "merge.h"

#include <QDateTime>
#include <QJsonArray>
#include <QJsonDocument>
#include <QSet>
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
    // Keep the current session until the new one arrives. A failed login must
    // not sign the desk out, and the login call itself must not send the old token.
    m_http.request(QStringLiteral("POST"), QUrl(m_api + QStringLiteral("/api/auth/login")),
                   QJsonDocument(body).toJson(QJsonDocument::Compact), {},
                   [this](int status, const QByteArray &bytes, const QString &transport) {
                       if (status < 200 || status >= 300) {
                           setStatus(failure(bytes, status, transport));
                           return;
                       }
                       afterAuth(objectOf(bytes));
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
        hydrate();
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
    hydrate();
}

void Backend::runSlash(const QString &name) {
    const QString key = slashCommand(name);
    clearDraft();
    if (key == QLatin1String("pull")) {
        m_prompt = QStringLiteral("-");
        pull();
        rebuild();
        return;
    }
    if (key == QLatin1String("push")) {
        m_prompt = QStringLiteral("-");
        push();
        rebuild();
        return;
    }
    if (key == QLatin1String("help")) {
        m_prompt = QStringLiteral("-");
        showHelp();
        rebuild();
        return;
    }
    if (key.isEmpty()) {
        setStatus(tr("Unknown slash command."));
        rebuild();
        return;
    }
    go(key);
}

void Backend::openSelection() {
    const auto rows = visibleRows();
    if (m_selected < 0 || m_selected >= rows.size())
        return;
    const QString kind = rows.at(m_selected).toMap().value(QStringLiteral("kind")).toString();
    if (kind == QLatin1String("slash") || kind == QLatin1String("note") || kind == QLatin1String("show") ||
        kind == QLatin1String("episode") || kind == QLatin1String("article") || kind == QLatin1String("video") ||
        kind == QLatin1String("hit") || kind == QLatin1String("link"))
        activateSelection();
}

void Backend::submit() {
    const QString text = m_draft.trimmed();
    if (m_prompt == QLatin1String("@") || m_prompt == QLatin1String("#") || m_prompt == QLatin1String("*")) {
        setStatus(tr("Use the Android app for text and calls."));
        return;
    }
    if (text.isEmpty()) {
        if (m_prompt == QLatin1String("/")) {
            runSlash(selectedId());
            return;
        }
        if (m_prompt == QLatin1String("+")) {
            // From anywhere else, an empty note prompt opens the list.
            // On the list, it opens the selected note.
            if (m_page == QLatin1String("notes"))
                openSelection();
            else
                go(QStringLiteral("notes"));
            return;
        }
        if (m_prompt == QLatin1String("$")) {
            go(QStringLiteral("stocks"));
            return;
        }
        openSelection();
        return;
    }
    if (m_prompt == QLatin1String("/")) {
        runSlash(text);
        return;
    }
    if (m_prompt == QLatin1String("-") && m_page == QLatin1String("habits")) {
        const auto draft = parseHabit(text);
        if (draft.isEmpty()) {
            setStatus(tr("stretch daily · water every 4 hours · meds 7am and 7pm"));
            return;
        }
        mutate(addHabit(m_doc, draft, QDateTime::currentMSecsSinceEpoch()));
        m_draft.clear();
        return;
    }
    if (m_prompt == QLatin1String("-")) {
        if (m_page == QLatin1String("clock")) {
            const auto alarms = parseAlarmInputs(text);
            if (!alarms.isEmpty()) {
                mutate(addAlarms(m_doc, alarms, QDateTime::currentMSecsSinceEpoch()));
                clearDraft();
                setStatus(tr("Alarm saved."));
                return;
            }
        }
        if (looksLikeMath(text)) {
            setStatus(evaluateMath(text));
            clearDraft();
            return;
        }
        if (text.isEmpty())
            return;
        mutate(addTask(m_doc, text, QDateTime::currentMSecsSinceEpoch()));
        clearDraft();
        return;
    }
    if (m_prompt == QLatin1String("+")) {
        if (text.isEmpty()) {
            go(QStringLiteral("notes"));
            return;
        }
        const auto next = addNote(m_doc, text, QDateTime::currentMSecsSinceEpoch());
        m_detailId = next.value(QStringLiteral("items")).toArray().first().toObject().value(QStringLiteral("id")).toString();
        // Set the page before mutate rebuilds, or history records the old page
        // with this note's id and Back stops on a blank extra step.
        m_page = QStringLiteral("note");
        mutate(next);
        clearDraft();
        rebuild();
        return;
    }
    if (m_prompt == QLatin1String("$")) {
        if (text.isEmpty()) {
            go(QStringLiteral("stocks"));
            return;
        }
        searchStock(text);
        clearDraft();
        return;
    }
    if (m_prompt == QLatin1String("?")) {
        if (text.isEmpty())
            return;
        ask(text);
        clearDraft();
        return;
    }
    if (m_prompt == QLatin1String(">")) {
        if (m_page == QLatin1String("articles")) {
            bool ok = false;
            const int index = text.toInt(&ok);
            if (ok) {
                if (index >= 1 && index <= m_rows.size()) {
                    m_detailId = m_rows.at(index - 1).toMap().value(QStringLiteral("id")).toString();
                    m_page = QStringLiteral("article");
                    clearDraft();
                    rebuild();
                } else {
                    setStatus(tr("No article %1.").arg(text));
                }
                return;
            }
            if (!looksLikeUrl(text)) {
                setStatus(tr("Paste an article URL."));
                return;
            }
            saveArticle(text);
            clearDraft();
            return;
        }
        if (m_page == QLatin1String("videos")) {
            if (youtubeIdOf(text).isEmpty()) {
                setStatus(tr("Paste a YouTube URL."));
                return;
            }
            saveVideo(text);
            clearDraft();
            return;
        }
        if (!youtubeIdOf(text).isEmpty())
            saveVideo(text);
        else if (looksLikeUrl(text))
            saveArticle(text);
        else
            searchPodcast(text);
        clearDraft();
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
    const auto rows = visibleRows();
    const QString kind = rows.at(m_selected).toMap().value(QStringLiteral("kind")).toString();
    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    if (kind == QLatin1String("habit-reminders")) {
        const auto settings = m_doc.value(QStringLiteral("settings")).toObject();
        const bool on = !settings.contains(QStringLiteral("habitReminders")) || settings.value(QStringLiteral("habitReminders")).toBool();
        setHabitReminders(!on);
    } else if (kind == QLatin1String("habit-sound")) {
        setHabitSound(id.mid(QStringLiteral("habit-sound-").size()));
    } else if (kind == QLatin1String("habit")) {
        const qint64 slot = openHabitSlot(findById(m_doc.value(QStringLiteral("habits")).toArray(), id), now);
        mutate(toggleHabit(m_doc, id, slot, now));
    } else if (kind == QLatin1String("todo") || kind == QLatin1String("done"))
        mutate(toggleTask(m_doc, id, now));
    else if (kind == QLatin1String("alarm"))
        mutate(toggleAlarm(m_doc, id, now));
}

void Backend::deleteSelection() {
    const QString id = selectedId();
    if (id.isEmpty())
        return;
    const auto rows = visibleRows();
    const QString kind = rows.at(m_selected).toMap().value(QStringLiteral("kind")).toString();
    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    if (kind == QLatin1String("habit"))
        mutate(deleteHabit(m_doc, id, now));
    else if (kind == QLatin1String("alarm"))
        mutate(deleteAlarm(m_doc, id, now));
    else if (kind == QLatin1String("todo") || kind == QLatin1String("done") || kind == QLatin1String("note"))
        mutate(deleteItem(m_doc, id, now));
    else if (kind == QLatin1String("stock")) {
        QJsonArray kept;
        for (const auto &value : m_doc.value(QStringLiteral("watchlist")).toArray())
            if (value.toObject().value(QStringLiteral("symbol")).toString().compare(id, Qt::CaseInsensitive) != 0)
                kept.append(value);
        auto next = m_doc;
        next.insert(QStringLiteral("watchlist"), kept);
        mutate(touchExported(next, now));
    } else if (kind == QLatin1String("article") || kind == QLatin1String("video")) {
        const QString bagName = kind == QLatin1String("article") ? QStringLiteral("reads") : QStringLiteral("videos");
        const QString listName = kind == QLatin1String("article") ? QStringLiteral("articles") : QStringLiteral("videos");
        const QString progressKey = kind == QLatin1String("article") ? QStringLiteral("articleId") : QStringLiteral("videoId");
        auto bag = m_doc.value(bagName).toObject();
        QJsonArray kept;
        for (const auto &value : bag.value(listName).toArray())
            if (value.toObject().value(QStringLiteral("id")).toString() != id)
                kept.append(value);
        QJsonArray progress;
        for (const auto &value : bag.value(QStringLiteral("progress")).toArray())
            if (value.toObject().value(progressKey).toString() != id)
                progress.append(value);
        QJsonArray deleted = bag.value(QStringLiteral("deletedIds")).toArray();
        deleted.append(id);
        bag.insert(listName, kept);
        bag.insert(QStringLiteral("progress"), progress);
        bag.insert(QStringLiteral("deletedIds"), deleted);
        auto next = m_doc;
        next.insert(bagName, bag);
        mutate(touchExported(next, now));
    } else if (kind == QLatin1String("show")) {
        auto pods = m_doc.value(QStringLiteral("podcasts")).toObject();
        QJsonArray shows;
        for (const auto &value : pods.value(QStringLiteral("shows")).toArray())
            if (value.toObject().value(QStringLiteral("feedUrl")).toString().compare(id, Qt::CaseInsensitive) != 0)
                shows.append(value);
        QJsonArray episodes;
        QSet<QString> dropped;
        for (const auto &value : pods.value(QStringLiteral("episodes")).toArray()) {
            const auto episode = value.toObject();
            const QString showId = episode.value(QStringLiteral("showId")).toString(episode.value(QStringLiteral("feedUrl")).toString());
            if (showId.compare(id, Qt::CaseInsensitive) == 0)
                dropped.insert(episode.value(QStringLiteral("id")).toString());
            else
                episodes.append(episode);
        }
        QJsonArray progress;
        for (const auto &value : pods.value(QStringLiteral("progress")).toArray())
            if (!dropped.contains(value.toObject().value(QStringLiteral("episodeId")).toString()))
                progress.append(value);
        pods.insert(QStringLiteral("shows"), shows);
        pods.insert(QStringLiteral("episodes"), episodes);
        pods.insert(QStringLiteral("progress"), progress);
        auto next = m_doc;
        next.insert(QStringLiteral("podcasts"), pods);
        mutate(touchExported(next, now));
    }
}

void Backend::activateSelection() {
    const QString id = selectedId();
    if (id.isEmpty())
        return;
    const auto rows = visibleRows();
    const QString kind = rows.at(m_selected).toMap().value(QStringLiteral("kind")).toString();
    if (kind == QLatin1String("todo") || kind == QLatin1String("done") || kind == QLatin1String("habit") ||
        kind == QLatin1String("habit-reminders") || kind == QLatin1String("habit-sound")) {
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
        playUrl(episode.value(QStringLiteral("enclosureUrl")).toString(), episode.value(QStringLiteral("title")).toString(),
                tr("Podcasts"), id);
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
        const auto map = rows.at(m_selected).toMap();
        subscribeShow(id, map.value(QStringLiteral("title")).toString(), map.value(QStringLiteral("subtitle")).toString());
        return;
    }
    if (kind == QLatin1String("link"))
        go(id);
    if (kind == QLatin1String("slash"))
        runSlash(id);
}

void Backend::setComposing(bool on) {
    m_composing = on;
}

void Backend::saveDetail(const QString &text) {
    if (m_detailId.isEmpty() || m_page != QLatin1String("note"))
        return;
    if (text == findById(m_doc.value(QStringLiteral("items")).toArray(), m_detailId).value(QStringLiteral("text")).toString())
        return;
    // Keep the editor's text. rebuild() leaves it alone while composing, and a
    // binding back to the previous detail would eat the keystroke.
    m_detail = text;
    mutate(updateItemText(m_doc, m_detailId, text, QDateTime::currentMSecsSinceEpoch()), QStringLiteral("text"));
}

void Backend::playPause() {
    if (m_player.playbackState() == QMediaPlayer::PlayingState)
        m_player.pause();
    else
        m_player.play();
}

void Backend::playUrl(const QString &url, const QString &title, const QString &show, const QString &episodeId) {
    if (url.isEmpty()) {
        setStatus(tr("Nothing to play."));
        return;
    }
    if (!m_episodeId.isEmpty())
        checkpointPlayback(true, false);
    m_episodeId = episodeId;
    m_resumeMs = episodeId.isEmpty() ? 0 : savedResumeMs(m_doc, episodeId);
    m_playSavedAt = 0;
    m_playSavedPos = 0;
    m_player.setSource(QUrl(url));
    m_player.play();
    m_nowPlaying = title + QStringLiteral(" — ") + show;
    setStatus(tr("Playing."));
}

void Backend::checkpointPlayback(bool force, bool ended) {
    if (m_episodeId.isEmpty())
        return;
    // The player reports 0 until the saved place is applied. Writing that
    // would wipe the place the next play is about to seek to.
    if (m_resumeMs > 0 && !ended)
        return;
    const qint64 pos = qMax<qint64>(0, m_player.position());
    qint64 dur = m_player.duration();
    if (dur < 0)
        dur = 0;
    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    if (!playbackDue(m_playSavedAt, now, m_playSavedPos, pos, force))
        return;
    m_playSavedAt = now;
    m_playSavedPos = pos;
    // A moving playhead is not an edit Ctrl+Z should undo.
    m_doc = saveEpisodeProgress(m_doc, m_episodeId, pos, dur, now, ended);
    m_dirty = true;
    saveVault();
    scheduleSync({});
    if (m_page == QLatin1String("show"))
        rebuild();
    else
        emit changed();
}

void Backend::dismissAlert() {
    if (m_alertId.isEmpty())
        return;
    mutate(acknowledgeAlarm(m_doc, m_alertId, QDateTime::currentMSecsSinceEpoch()));
    m_alert.clear();
    m_alertTime.clear();
    m_alertId.clear();
    rebuild();
}

void Backend::snoozeAlert() {
    if (m_alertId.isEmpty())
        return;
    mutate(snoozeAlarm(m_doc, m_alertId, QDateTime::currentMSecsSinceEpoch()));
    m_alert.clear();
    m_alertTime.clear();
    m_alertId.clear();
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
        // The desk may be hidden for the bar. The alarm screen lives on the window.
        emit raiseRequested();
        return;
    }
    const auto settings = m_doc.value(QStringLiteral("settings")).toObject();
    const bool reminders = !settings.contains(QStringLiteral("habitReminders")) || settings.value(QStringLiteral("habitReminders")).toBool();
    if (!reminders)
        return;
    const auto due = dueHabit(m_doc, now, m_habitReminded);
    if (!due.isEmpty()) {
        m_habitReminded.insert(due.value(QStringLiteral("key")).toString());
        notify(due.value(QStringLiteral("name")).toString(), due.value(QStringLiteral("body")).toString());
        setHabitSound(settings.value(QStringLiteral("habitSound")).toString(QStringLiteral("PULSE")));
    }
}

void Backend::setHabitReminders(bool on) {
    auto settings = m_doc.value(QStringLiteral("settings")).toObject();
    settings.insert(QStringLiteral("habitReminders"), on);
    auto next = m_doc;
    next.insert(QStringLiteral("settings"), settings);
    mutate(next);
}

void Backend::setHabitSound(const QString &sound) {
    const QString name = sound.toUpper();
    auto settings = m_doc.value(QStringLiteral("settings")).toObject();
    if (settings.value(QStringLiteral("habitSound")).toString() != name) {
        settings.insert(QStringLiteral("habitSound"), name);
        auto next = m_doc;
        next.insert(QStringLiteral("settings"), settings);
        mutate(next);
    }
    if (name == QLatin1String("OFF"))
        return;
    const QString event = name == QLatin1String("CHIME") ? QStringLiteral("complete")
        : name == QLatin1String("BELL")                  ? QStringLiteral("bell")
        : name == QLatin1String("ORTHODOX")              ? QStringLiteral("dialog-information")
        : name == QLatin1String("HUM")                   ? QStringLiteral("audio-volume-change")
                                                         : QStringLiteral("message");
    QProcess::startDetached(QStringLiteral("canberra-gtk-play"), {QStringLiteral("-i"), event});
}

void Backend::notify(const QString &title, const QString &body) {
    QProcess::startDetached(QStringLiteral("notify-send"), {QStringLiteral("--urgency=critical"), title, body});
}

bool looksLikeUrl(const QString &text) {
    return text.contains(QLatin1String("://")) || (text.contains('.') && !text.contains(' '));
}
