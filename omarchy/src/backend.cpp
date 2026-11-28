#include "backend.h"

#include "library.h"
#include "merge.h"
#include "translator.h"

#include <QDate>
#include <QDateTime>
#include <QDir>
#include <QFile>
#include <QJsonArray>
#include <QJsonDocument>
#include <QLocalSocket>
#include <QProcess>
#include <QRegularExpression>
#include <QSaveFile>
#include <QStandardPaths>
#include <QTimeZone>
#include <QUrlQuery>
#include <QXmlStreamReader>

namespace {

const QString kApi = QStringLiteral("https://builder.cdr.xyz");

QString dataDir() {
    return deskDataDir();
}

QLocale deskLocale() {
    return Translator::formatLocale(Translator::readLanguage(QDir::homePath() + QStringLiteral("/.config/omarchy/language")));
}

QVariantMap withQuote(QVariantMap entry, const Quote &quote) {
    if (!quote.ok)
        return entry;
    const QLocale locale = deskLocale();
    entry.insert(QStringLiteral("trailing"), formatQuotePrice(quote, locale));
    entry.insert(QStringLiteral("change"), formatQuoteChange(quote, locale));
    entry.insert(QStringLiteral("tone"), quoteTone(quote.percent));
    return entry;
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

QString leadingPrompt(const QString &draft) {
    if (draft.isEmpty())
        return {};
    const QString first = draft.left(1);
    const QStringList glyphs = {QStringLiteral("-"), QStringLiteral("+"), QStringLiteral("$"), QStringLiteral("?"),
                                QStringLiteral("/"), QStringLiteral(">"), QStringLiteral("@"), QStringLiteral("#"),
                                QStringLiteral("*")};
    return glyphs.contains(first) ? first : QString();
}

QString clockText(qint64 ms) {
    const qint64 seconds = qMax<qint64>(0, ms) / 1000;
    return QStringLiteral("%1:%2").arg(seconds / 60).arg(seconds % 60, 2, 10, QLatin1Char('0'));
}

} // namespace

Backend::Backend(QObject *parent) : QObject(parent), m_audio(this), m_player(this) {
    m_player.setAudioOutput(&m_audio);
    m_syncTimer.setSingleShot(true);
    connect(&m_syncTimer, &QTimer::timeout, this, &Backend::syncIfDue);
    m_clockTimer.setInterval(15000);
    connect(&m_clockTimer, &QTimer::timeout, this, &Backend::checkAlarms);
    m_clockTimer.start();
    m_barTimer.setSingleShot(true);
    m_barTimer.setInterval(400);
    connect(&m_barTimer, &QTimer::timeout, this, &Backend::publishBar);
    m_barPulse.setInterval(2000);
    connect(&m_barPulse, &QTimer::timeout, this, &Backend::publishBar);
    m_barPulse.start();
    connect(&m_player, &QMediaPlayer::playbackStateChanged, this, [this](QMediaPlayer::PlaybackState state) {
        if (state != QMediaPlayer::PlayingState)
            checkpointPlayback(true, false);
        scheduleBar();
        emit changed();
    });
    connect(&m_player, &QMediaPlayer::positionChanged, this, [this] {
        checkpointPlayback(false, false);
        scheduleBar();
        emit changed();
    });
    connect(&m_player, &QMediaPlayer::mediaStatusChanged, this, [this](QMediaPlayer::MediaStatus status) {
        if (m_resumeMs > 0 && (status == QMediaPlayer::LoadedMedia || status == QMediaPlayer::BufferedMedia)) {
            const qint64 resume = m_resumeMs;
            m_resumeMs = 0;
            m_player.setPosition(resume);
        }
        if (status == QMediaPlayer::EndOfMedia)
            checkpointPlayback(true, true);
    });
    connect(&m_player, &QMediaPlayer::errorOccurred, this, [this](QMediaPlayer::Error, const QString &message) {
        setStatus(message.isEmpty() ? tr("Playback failed.") : message);
    });
    loadLocal();
    rebuild();
}

void Backend::start() {
    // main() may call loadDemo() first. A pull started in the constructor would
    // still finish and write that account over the sample. Demo also stays off
    // the bar socket, so a sample run cannot replace the live desk. A signed-out
    // desk still serves the bar, so the tray can use the local vault.
    if (m_token == QLatin1String("demo"))
        return;
    startBar();
    scheduleBar();
    if (m_token.isEmpty())
        return;
    pull();
    QTimer::singleShot(0, this, [this] { hydrate(); });
}

void Backend::loadDemo() {
    m_barPulse.stop();
    m_barTimer.stop();
    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    m_doc = emptyDoc(now);
    m_doc = addTask(m_doc, QStringLiteral("Sketch the joint"), now - 2000);
    m_doc = addTask(m_doc, QStringLiteral("Order cedar"), now - 1000);
    m_doc = addNote(m_doc, QStringLiteral("Shop notes"), now);
    QJsonArray watch;
    QJsonObject up;
    up.insert(QStringLiteral("symbol"), QStringLiteral("ACME"));
    up.insert(QStringLiteral("name"), QStringLiteral("Acme"));
    QJsonObject down;
    down.insert(QStringLiteral("symbol"), QStringLiteral("NOPE"));
    down.insert(QStringLiteral("name"), QStringLiteral("Nope"));
    watch.append(up);
    watch.append(down);
    m_doc.insert(QStringLiteral("watchlist"), watch);
    Quote gained;
    gained.ok = true;
    gained.price = 1234.5;
    gained.percent = 1.24;
    gained.currency = QStringLiteral("USD");
    Quote lost;
    lost.ok = true;
    lost.price = 88;
    lost.percent = -0.4;
    lost.currency = QStringLiteral("USD");
    m_quotes.insert(QStringLiteral("ACME"), gained);
    m_quotes.insert(QStringLiteral("NOPE"), lost);
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

void Backend::scheduleBar() {
    if (!m_barTimer.isActive())
        m_barTimer.start();
}

void Backend::publishBar() {
    if (m_token == QLatin1String("demo"))
        return;
    BarMedia media;
    if (!m_nowPlaying.isEmpty()) {
        const int split = m_nowPlaying.indexOf(QStringLiteral(" — "));
        media.title = split < 0 ? m_nowPlaying : m_nowPlaying.left(split);
        media.detail = split < 0 ? QString() : m_nowPlaying.mid(split + 3);
        media.kind = QStringLiteral("episode");
        media.playing = playing();
        media.position = positionLabel();
    }
    BarMedia reading;
    if (m_page == QLatin1String("article") && !m_detailId.isEmpty()) {
        const auto article =
            findById(m_doc.value(QStringLiteral("reads")).toObject().value(QStringLiteral("articles")).toArray(), m_detailId);
        reading.title = article.value(QStringLiteral("title")).toString();
        reading.detail = article.value(QStringLiteral("publication")).toString();
        reading.kind = QStringLiteral("article");
        reading.position = articlePlace(article);
    }
    QString temp;
    const auto current = m_forecast.value(QStringLiteral("current")).toObject();
    if (current.contains(QStringLiteral("temperature_2m")))
        temp = tr("%1°").arg(displayTemp(current.value(QStringLiteral("temperature_2m")).toDouble()));
    auto status = barStatus(m_doc, m_quotes, media, reading, temp, deskLocale());
    status.insert(QStringLiteral("at"), double(QDateTime::currentMSecsSinceEpoch()));
    writePrivate(barStatusPath(), QJsonDocument(status).toJson(QJsonDocument::Compact));
}

void Backend::startBar() {
    m_barServer.setSocketOptions(QLocalServer::UserAccessOption);
    QLocalServer::removeServer(barSocketPath());
    if (!m_barServer.listen(barSocketPath()))
        return;
    connect(&m_barServer, &QLocalServer::newConnection, this, [this] {
        while (auto *socket = m_barServer.nextPendingConnection()) {
            connect(socket, &QLocalSocket::readyRead, this, [this, socket] {
                while (socket->canReadLine()) {
                    const auto command = QJsonDocument::fromJson(socket->readLine()).object();
                    handleBar(command);
                    socket->write(QByteArrayLiteral("{\"ok\":true}\n"));
                    socket->flush();
                    socket->disconnectFromServer();
                }
            });
            connect(socket, &QLocalSocket::disconnected, socket, &QObject::deleteLater);
        }
    });
}

void Backend::handleBar(const QJsonObject &command) {
    const QString name = command.value(QStringLiteral("cmd")).toString();
    if (name == QLatin1String("ping"))
        return;
    if (name == QLatin1String("play-pause")) {
        if (!m_nowPlaying.isEmpty())
            playPause();
        return;
    }
    if (name == QLatin1String("seek")) {
        if (m_nowPlaying.isEmpty())
            return;
        const int delta = qBound(-300000, command.value(QStringLiteral("ms")).toInt(), 300000);
        qint64 next = m_player.position() + delta;
        if (next < 0)
            next = 0;
        const qint64 dur = m_player.duration();
        if (dur > 0 && next > dur)
            next = dur;
        m_player.setPosition(next);
        return;
    }
    if (name == QLatin1String("toggle")) {
        const QString id = command.value(QStringLiteral("id")).toString();
        const auto item = findById(m_doc.value(QStringLiteral("items")).toArray(), id);
        if (item.value(QStringLiteral("kind")).toString().compare(QLatin1String("todo"), Qt::CaseInsensitive) != 0)
            return;
        mutate(toggleTask(m_doc, id, QDateTime::currentMSecsSinceEpoch()));
        return;
    }
    if (name == QLatin1String("add")) {
        if (m_token == QLatin1String("demo"))
            return;
        const QString text = barTaskText(command.value(QStringLiteral("text")).toString());
        if (text.isEmpty())
            return;
        mutate(addTask(m_doc, text, QDateTime::currentMSecsSinceEpoch()));
        return;
    }
    if (name != QLatin1String("open"))
        return;
    static const QRegularExpression pageOk(QStringLiteral("^(home|tasks|notes|stocks|podcasts|articles|article|videos|weather|clock|settings)$"));
    const QString page = command.value(QStringLiteral("page")).toString();
    const QString id = command.value(QStringLiteral("id")).toString();
    if (pageOk.match(page).hasMatch()) {
        // Opening an article from the list leaves Back on the list.
        go(page == QLatin1String("article") ? QStringLiteral("articles") : page);
        if (page == QLatin1String("article") && !id.isEmpty()) {
            m_detailId = id;
            m_page = QStringLiteral("article");
            rebuild();
        }
    }
    emit raiseRequested();
}

void Backend::setStatus(const QString &status) {
    if (m_status == status)
        return;
    m_status = status;
    emit changed();
}

void Backend::clearDraft() {
    if (m_draft.isEmpty())
        return;
    m_draft.clear();
    emit changed();
}

void Backend::setDraft(const QString &draft) {
    QString next = draft;
    const QString glyph = leadingPrompt(next);
    if (!glyph.isEmpty())
        next = next.mid(1);
    const bool promptChanged = !glyph.isEmpty() && glyph != m_prompt;
    if (promptChanged)
        m_prompt = glyph;
    if (m_draft == next && !promptChanged)
        return;
    m_draft = next;
    if (promptChanged || m_prompt == QLatin1String("/"))
        rebuild();
    else
        emit changed();
}

QVariantList Backend::visibleRows() const {
    return menuOpen() ? m_menu : m_rows;
}

void Backend::showHelp() {
    emit helpRequested();
}

void Backend::hydrate() {
    if (m_token == QLatin1String("demo"))
        return;
    refreshWeather();
    refreshQuotes();
    refreshPodcastFeeds();
}

double Backend::readingFraction() const {
    if (m_page != QLatin1String("article"))
        return 0;
    return articleReadingFraction(m_doc, m_detailId);
}

int Backend::displayTemp(double celsius) const {
    const QString units = m_doc.value(QStringLiteral("settings")).toObject().value(QStringLiteral("weatherUnits")).toString().toUpper();
    const double value = units == QLatin1String("IMPERIAL") ? celsius * 9.0 / 5.0 + 32.0 : celsius;
    return qRound(value);
}

QString Backend::placeLabel(qint64 positionMs, qint64 durationMs) const {
    if (positionMs <= 0 || durationMs <= 0)
        return {};
    return tr("%1 of %2").arg(clockText(positionMs), clockText(durationMs));
}

QString Backend::articlePlace(const QJsonObject &article) const {
    const QString id = article.value(QStringLiteral("id")).toString();
    return placeLabel(articlePositionMs(m_doc, id), speechDurationMs(article.value(QStringLiteral("text")).toString()));
}

QString Backend::episodePlace(const QJsonObject &episode) const {
    const QString id = episode.value(QStringLiteral("id")).toString();
    for (const auto &value : m_doc.value(QStringLiteral("podcasts")).toObject().value(QStringLiteral("progress")).toArray()) {
        const auto row = value.toObject();
        if (row.value(QStringLiteral("episodeId")).toString() != id)
            continue;
        const qint64 duration = qint64(row.value(QStringLiteral("durationMs")).toDouble(episode.value(QStringLiteral("durationMs")).toDouble()));
        return placeLabel(qint64(row.value(QStringLiteral("positionMs")).toDouble()), duration);
    }
    return {};
}

QString Backend::title() const {
    const QString page = m_page;
    if (page == QLatin1String("home"))
        return tr("Home");
    if (page == QLatin1String("tasks"))
        return tr("Tasks");
    if (page == QLatin1String("habits"))
        return tr("Habits");
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
    if (m_token.isEmpty() || m_token == QLatin1String("demo"))
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
    return {tr("Enter submits the command. An empty bar opens the selection"),
            tr("/ opens a page. Tab cycles the prompt. A typed prefix switches mode"),
            tr("Up and Down move. Space toggles when the bar is empty"),
            tr("Delete removes the row when the bar is empty"),
            tr("? in the bar asks. Ctrl+/ shows this"),
            tr("Cmd+[ or Alt+Left goes back. Cmd+] or Alt+Right goes forward."),
            tr("Ctrl+Z undoes a local edit. Ctrl+Q quits")};
}

bool Backend::Place::operator==(const Place &other) const {
    if (page != other.page || detailId != other.detailId || podcastHits != other.podcastHits)
        return false;
    if (!podcastHits)
        return true;
    if (hits.size() != other.hits.size())
        return false;
    for (int index = 0; index < hits.size(); ++index) {
        if (hits.at(index).toMap().value(QStringLiteral("id")).toString() !=
            other.hits.at(index).toMap().value(QStringLiteral("id")).toString())
            return false;
    }
    return true;
}

void Backend::applyPrompt(const QString &page) {
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
}

Backend::Place Backend::capture() const {
    Place place;
    place.page = m_page;
    place.detailId = m_detailId;
    place.podcastHits = m_showPodcastHits;
    if (m_showPodcastHits)
        place.hits = m_podcastHits;
    return place;
}

void Backend::visit() {
    // Page changes that skip go() still finish here. Playback and status
    // updates do not rebuild, so they do not push a history entry.
    if (m_restoring)
        return;
    const Place here = capture();
    if (!m_history.isEmpty() && m_history.at(m_historyIndex) == here)
        return;
    if (m_historyIndex + 1 < m_history.size())
        m_history.resize(m_historyIndex + 1);
    m_history.append(here);
    m_historyIndex = m_history.size() - 1;
    while (m_history.size() > 40) {
        m_history.removeFirst();
        --m_historyIndex;
    }
    emit historyChanged();
}

void Backend::travel(int delta) {
    const int index = m_historyIndex + delta;
    if (index < 0 || index >= m_history.size())
        return;
    const Place place = m_history.at(index);
    m_historyIndex = index;
    // Recording this restore would drop the pages still ahead of it.
    m_restoring = true;
    ++m_searchGen;
    m_showPodcastHits = place.podcastHits;
    m_podcastHits = place.podcastHits ? place.hits : QVariantList{};
    m_page = place.page;
    m_selected = 0;
    m_detailId = place.detailId;
    m_detail.clear();
    applyPrompt(place.page);
    m_status.clear();
    clearDraft();
    rebuild();
    m_restoring = false;
    emit historyChanged();
    if (place.page == QLatin1String("weather"))
        refreshWeather();
    if (place.page == QLatin1String("stocks"))
        refreshQuotes();
}

void Backend::back() { travel(-1); }

void Backend::forward() { travel(1); }

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

void Backend::nudgeHomePane(int dx) {
    if (m_page != QLatin1String("home"))
        return;
    const int next = dx < -48 ? 1 : dx > 48 ? 0 : m_homePane;
    if (next == m_homePane)
        return;
    m_homePane = next;
    rebuild();
}

void Backend::go(const QString &page) {
    ++m_searchGen;
    m_showPodcastHits = false;
    m_podcastHits.clear();
    m_page = page;
    m_selected = 0;
    m_editingHabitId.clear();
    m_detailId.clear();
    m_detail.clear();
    applyPrompt(page);
    // A page change drops the previous result. Callers that navigate to show
    // a message set the status after go().
    m_status.clear();
    clearDraft();
    rebuild();
    if (page == QLatin1String("weather"))
        refreshWeather();
    if (page == QLatin1String("stocks"))
        refreshQuotes();
}

void Backend::setPrompt(const QString &glyph) {
    if (glyph.isEmpty())
        return;
    ++m_searchGen;
    m_showPodcastHits = false;
    m_podcastHits.clear();
    m_prompt = glyph.left(1);
    clearDraft();
    rebuild();
}

void Backend::moveSelection(int delta) {
    const auto rows = visibleRows();
    if (rows.isEmpty())
        return;
    m_selected = qBound(0, m_selected + delta, rows.size() - 1);
    emit changed();
}

QString Backend::selectedId() const {
    const auto rows = visibleRows();
    if (m_selected < 0 || m_selected >= rows.size())
        return {};
    return rows.at(m_selected).toMap().value(QStringLiteral("id")).toString();
}

void Backend::rebuild() {
    m_rows.clear();
    m_menu.clear();
    const auto add = [this](const QVariantMap &entry) { m_rows.append(entry); };
    if (m_prompt == QLatin1String("/")) {
        const QString query = m_draft.trimmed().toLower();
        const QHash<QString, QString> labels = {
            {QStringLiteral("articles"), tr("saved articles")}, {QStringLiteral("clock"), tr("alarms")},
            {QStringLiteral("habits"), tr("habit tracker")}, {QStringLiteral("help"), tr("commands")},           {QStringLiteral("home"), tr("home")},
            {QStringLiteral("notes"), tr("all notes")},         {QStringLiteral("podcasts"), tr("all podcasts")},
            {QStringLiteral("pull"), tr("sync down")},          {QStringLiteral("push"), tr("sync up")},
            {QStringLiteral("settings"), tr("settings")},       {QStringLiteral("stocks"), tr("watchlist")},
            {QStringLiteral("tasks"), tr("all tasks")},         {QStringLiteral("videos"), tr("saved videos")},
            {QStringLiteral("weather"), tr("forecast")}};
        for (const auto &name : slashNames()) {
            if (!query.isEmpty() && !name.startsWith(query) && slashCommand(query) != name)
                continue;
            m_menu.append(row(name, name, labels.value(name), {}, QStringLiteral("slash")));
        }
    }
    if (m_page == QLatin1String("home")) {
        const auto open = openTodos(m_doc);
        if (m_homePane == 1) {
            const qint64 now = QDateTime::currentMSecsSinceEpoch();
            add(row(QStringLiteral("graph"), habitDayLabel(m_doc, now), tr("today"), {}, QStringLiteral("label")));
            int shown = 0;
            for (const auto &value : m_doc.value(QStringLiteral("habits")).toArray()) {
                if (shown++ >= 3)
                    break;
                const auto habit = value.toObject();
                const qint64 slot = openHabitSlot(habit, now);
                const bool done = habitDone(m_doc, habit.value(QStringLiteral("id")).toString(), slot);
                add(row(habit.value(QStringLiteral("id")).toString(), habit.value(QStringLiteral("name")).toString(),
                        habitSchedule(habit), done ? tr("done") : tr("check"), QStringLiteral("habit")));
            }
        } else {
            int shown = 0;
            for (const auto &item : open) {
                if (shown++ >= 3)
                    break;
                add(row(item.value(QStringLiteral("id")).toString(), item.value(QStringLiteral("text")).toString(), tr("task"), {},
                        QStringLiteral("todo")));
            }
        }
        if (m_homePane != 1 && open.size() > 3)
            add(row(QStringLiteral("tasks"), tr("All tasks"), tr("%n open tasks", "", open.size()), {}, QStringLiteral("link")));
        if (!weatherPlace().isEmpty()) {
            const auto current = m_forecast.value(QStringLiteral("current")).toObject();
            QString trailing;
            if (current.contains(QStringLiteral("temperature_2m")))
                trailing = tr("%1°").arg(displayTemp(current.value(QStringLiteral("temperature_2m")).toDouble()));
            add(row(QStringLiteral("weather"), weatherPlace(), tr("weather"), trailing, QStringLiteral("link")));
        }
        const auto watch = m_doc.value(QStringLiteral("watchlist")).toArray();
        if (!watch.isEmpty()) {
            const QString symbol = watch.first().toObject().value(QStringLiteral("symbol")).toString();
            add(withQuote(row(QStringLiteral("stocks"), symbol, tr("stock"), {}, QStringLiteral("link")),
                          m_quotes.value(symbol.trimmed().toUpper())));
        }
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
    } else if (m_page == QLatin1String("habits")) {
        const qint64 now = QDateTime::currentMSecsSinceEpoch();
        const auto settings = m_doc.value(QStringLiteral("settings")).toObject();
        const bool reminders = !settings.contains(QStringLiteral("habitReminders")) || settings.value(QStringLiteral("habitReminders")).toBool();
        const QString sound = settings.value(QStringLiteral("habitSound")).toString(QStringLiteral("PULSE")).toUpper();
        add(row(QStringLiteral("habit-reminders"), tr("alarms"), reminders ? tr("on") : tr("off"), {}, QStringLiteral("habit-reminders")));
        const QStringList tones = {QStringLiteral("PULSE"), QStringLiteral("CHIME"), QStringLiteral("BELL"),
                                   QStringLiteral("ORTHODOX"), QStringLiteral("HUM"), QStringLiteral("OFF")};
        for (const auto &tone : tones)
            add(row(QStringLiteral("habit-sound-") + tone, tone.toLower(), tone == sound ? tr("selected") : QString(), {},
                    QStringLiteral("habit-sound")));
        add(row(QStringLiteral("graph"), habitDayLabel(m_doc, now), tr("today"), {}, QStringLiteral("label")));
        for (const auto &value : m_doc.value(QStringLiteral("habits")).toArray()) {
            const auto habit = value.toObject();
            const qint64 slot = openHabitSlot(habit, now);
            const bool done = habitDone(m_doc, habit.value(QStringLiteral("id")).toString(), slot);
            add(row(habit.value(QStringLiteral("id")).toString(), habit.value(QStringLiteral("name")).toString(),
                    habitSchedule(habit), done ? tr("done") : tr("check"), QStringLiteral("habit")));
        }
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
            const QString symbol = item.value(QStringLiteral("symbol")).toString();
            add(withQuote(row(symbol, symbol, item.value(QStringLiteral("name")).toString(), {}, QStringLiteral("stock")),
                          m_quotes.value(symbol.trimmed().toUpper())));
        }
    } else if (m_page == QLatin1String("weather")) {
        const auto current = m_forecast.value(QStringLiteral("current")).toObject();
        if (current.contains(QStringLiteral("temperature_2m"))) {
            const int code = current.value(QStringLiteral("weather_code")).toInt();
            add(row(QStringLiteral("now"), tr("%1°").arg(displayTemp(current.value(QStringLiteral("temperature_2m")).toDouble())),
                    weatherCode(code), weatherPlace(), QStringLiteral("forecast")));
        }
        const auto daily = m_forecast.value(QStringLiteral("daily")).toObject();
        const auto times = daily.value(QStringLiteral("time")).toArray();
        const auto highs = daily.value(QStringLiteral("temperature_2m_max")).toArray();
        const auto lows = daily.value(QStringLiteral("temperature_2m_min")).toArray();
        const auto codes = daily.value(QStringLiteral("weather_code")).toArray();
        const QDate today = QDate::currentDate();
        for (int index = 0; index < times.size() && index < 7; ++index) {
            const QDate date = QDate::fromString(times.at(index).toString(), Qt::ISODate);
            const QString name = date == today ? tr("Today") : QLocale().dayName(date.dayOfWeek(), QLocale::ShortFormat);
            const QString high = highs.size() > index ? QString::number(displayTemp(highs.at(index).toDouble())) : QString();
            const QString low = lows.size() > index ? QString::number(displayTemp(lows.at(index).toDouble())) : QString();
            const QString kind = codes.size() > index ? weatherCode(codes.at(index).toInt()) : QString();
            add(row(times.at(index).toString(), name, kind, tr("%1°  %2°").arg(high, low), QStringLiteral("forecast")));
        }
    } else if (m_page == QLatin1String("podcasts") && m_showPodcastHits) {
        m_rows = m_podcastHits;
    } else if (m_page == QLatin1String("podcasts")) {
        for (const auto &value : m_doc.value(QStringLiteral("podcasts")).toObject().value(QStringLiteral("shows")).toArray()) {
            const auto show = value.toObject();
            add(row(show.value(QStringLiteral("feedUrl")).toString(), show.value(QStringLiteral("title")).toString(),
                    show.value(QStringLiteral("author")).toString(), {}, QStringLiteral("show")));
        }
    } else if (m_page == QLatin1String("show")) {
        for (const auto &value : m_doc.value(QStringLiteral("podcasts")).toObject().value(QStringLiteral("episodes")).toArray()) {
            const auto episode = value.toObject();
            const QString showId = episode.value(QStringLiteral("showId")).toString(episode.value(QStringLiteral("feedUrl")).toString());
            if (showId.compare(m_detailId, Qt::CaseInsensitive) != 0)
                continue;
            add(row(episode.value(QStringLiteral("id")).toString(), episode.value(QStringLiteral("title")).toString(), {},
                    episodePlace(episode), QStringLiteral("episode")));
        }
    } else if (m_page == QLatin1String("articles")) {
        for (const auto &value : m_doc.value(QStringLiteral("reads")).toObject().value(QStringLiteral("articles")).toArray()) {
            const auto article = value.toObject();
            add(row(article.value(QStringLiteral("id")).toString(), article.value(QStringLiteral("title")).toString(),
                    article.value(QStringLiteral("publication")).toString(), articlePlace(article), QStringLiteral("article")));
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
    const int count = menuOpen() ? m_menu.size() : m_rows.size();
    if (count == 0)
        m_selected = 0;
    else if (m_selected >= count)
        m_selected = count - 1;
    else if (m_selected < 0)
        m_selected = 0;
    visit();
    emit rowsChanged();
    emit changed();
    scheduleBar();
}
