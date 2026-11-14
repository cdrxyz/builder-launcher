#include "backend.h"

#include "library.h"

#include <QDateTime>
#include <QJsonArray>
#include <QJsonDocument>
#include <QUrl>
#include <QUrlQuery>
#include <QXmlStreamReader>

namespace {

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

QString chatBearer(const QJsonObject &settings) {
    const QString access = settings.value(QStringLiteral("oauthAccess")).toString();
    if (!access.isEmpty())
        return access;
    return settings.value(QStringLiteral("apiKey")).toString();
}

QString providerBase(const QString &provider) {
    const QString key = provider.toUpper();
    if (key == QLatin1String("XAI"))
        return QStringLiteral("https://api.x.ai/v1");
    if (key == QLatin1String("OPENAI"))
        return QStringLiteral("https://api.openai.com/v1");
    if (key == QLatin1String("ANTHROPIC"))
        return QStringLiteral("https://api.anthropic.com");
    if (key == QLatin1String("GROQ"))
        return QStringLiteral("https://api.groq.com/openai/v1");
    if (key == QLatin1String("OPENROUTER"))
        return QStringLiteral("https://openrouter.ai/api/v1");
    return {};
}

QJsonArray parseRss(const QByteArray &xml, const QString &feedUrl) {
    QXmlStreamReader reader(xml);
    QString showTitle;
    QString itemTitle;
    QString enclosure;
    QString guid;
    bool inItem = false;
    QJsonArray episodes;
    while (!reader.atEnd()) {
        reader.readNext();
        if (reader.isStartElement()) {
            const auto name = reader.name();
            if (name == QLatin1String("item")) {
                inItem = true;
                itemTitle.clear();
                enclosure.clear();
                guid.clear();
            } else if (!inItem && name == QLatin1String("title")) {
                showTitle = reader.readElementText();
            } else if (inItem && name == QLatin1String("title")) {
                itemTitle = reader.readElementText();
            } else if (inItem && name == QLatin1String("guid")) {
                guid = reader.readElementText();
            } else if (inItem && name == QLatin1String("enclosure")) {
                enclosure = reader.attributes().value(QStringLiteral("url")).toString();
            }
        } else if (reader.isEndElement() && reader.name() == QLatin1String("item")) {
            inItem = false;
            if (itemTitle.isEmpty() || enclosure.isEmpty())
                continue;
            QJsonObject episode;
            episode.insert(QStringLiteral("id"), guid.isEmpty() ? enclosure : guid);
            episode.insert(QStringLiteral("feedUrl"), feedUrl);
            episode.insert(QStringLiteral("title"), itemTitle);
            episode.insert(QStringLiteral("enclosureUrl"), enclosure);
            episodes.append(episode);
            if (episodes.size() >= 40)
                break;
        }
    }
    Q_UNUSED(showTitle);
    return episodes;
}

} // namespace

void Backend::searchStock(const QString &query) {
    const QString symbol = query.trimmed().toUpper();
    setStatus(tr("Looking up %1…").arg(symbol));
    api(QStringLiteral("GET"), QStringLiteral("/api/yahoo/search?q=") + QString::fromUtf8(QUrl::toPercentEncoding(symbol)), {}, true,
        [this, symbol](QJsonObject body, QString error) {
            if (!error.isEmpty()) {
                setStatus(error);
                return;
            }
            const auto quotes = body.value(QStringLiteral("quotes")).toArray();
            const auto quote = quotes.isEmpty() ? QJsonObject() : quotes.first().toObject();
            const QString found = quote.value(QStringLiteral("symbol")).toString(symbol);
            auto list = m_doc.value(QStringLiteral("watchlist")).toArray();
            for (const auto &value : list)
                if (value.toObject().value(QStringLiteral("symbol")).toString().compare(found, Qt::CaseInsensitive) == 0) {
                    setStatus(tr("%1 is already on the list.").arg(found));
                    return;
                }
            QJsonObject item;
            item.insert(QStringLiteral("symbol"), found);
            item.insert(QStringLiteral("name"), quote.value(QStringLiteral("shortname")).toString(found));
            item.insert(QStringLiteral("exchange"), quote.value(QStringLiteral("exchange")).toString());
            item.insert(QStringLiteral("addedAt"), double(QDateTime::currentMSecsSinceEpoch()));
            list.prepend(item);
            auto next = m_doc;
            next.insert(QStringLiteral("watchlist"), list);
            mutate(touchExported(next, QDateTime::currentMSecsSinceEpoch()));
            go(QStringLiteral("stocks"));
        });
}

void Backend::searchPodcast(const QString &query) {
    setStatus(tr("Searching podcasts…"));
    api(QStringLiteral("GET"), QStringLiteral("/api/podcasts/search?q=") + QString::fromUtf8(QUrl::toPercentEncoding(query)), {}, true,
        [this](QJsonObject body, QString error) {
            m_rows.clear();
            if (!error.isEmpty()) {
                setStatus(error);
                return;
            }
            const auto results = body.value(QStringLiteral("results")).toArray();
            if (results.isEmpty()) {
                setStatus(tr("No podcast matches."));
                rebuild();
                return;
            }
            for (const auto &value : results) {
                const auto hit = value.toObject();
                const QString feed = hit.value(QStringLiteral("feedUrl")).toString();
                const QString title = hit.value(QStringLiteral("collectionName")).toString(hit.value(QStringLiteral("trackName")).toString());
                if (feed.isEmpty() || title.isEmpty())
                    continue;
                m_rows.append(QVariantMap{{QStringLiteral("id"), feed},
                                          {QStringLiteral("title"), title},
                                          {QStringLiteral("subtitle"), hit.value(QStringLiteral("artistName")).toString()},
                                          {QStringLiteral("trailing"), QString()},
                                          {QStringLiteral("kind"), QStringLiteral("hit")}});
            }
            if (m_rows.isEmpty())
                setStatus(tr("No podcast matches."));
            else
                setStatus(tr("Enter subscribes the selected show."));
            emit changed();
        });
}

void Backend::saveArticle(const QString &url) {
    setStatus(tr("Reading the article…"));
    api(QStringLiteral("GET"), QStringLiteral("/api/articles?url=") + QString::fromUtf8(QUrl::toPercentEncoding(url)), {}, true,
        [this, url](QJsonObject body, QString error) {
            if (!error.isEmpty()) {
                setStatus(error);
                return;
            }
            const QString id = articleIdForUrl(body.value(QStringLiteral("url")).toString(url));
            if (id.isEmpty()) {
                setStatus(tr("That link is not a public article."));
                return;
            }
            auto reads = m_doc.value(QStringLiteral("reads")).toObject();
            auto articles = reads.value(QStringLiteral("articles")).toArray();
            QJsonArray kept;
            for (const auto &value : articles)
                if (value.toObject().value(QStringLiteral("id")).toString() != id)
                    kept.append(value);
            QJsonObject article;
            article.insert(QStringLiteral("id"), id);
            article.insert(QStringLiteral("url"), body.value(QStringLiteral("url")).toString(url));
            article.insert(QStringLiteral("title"), body.value(QStringLiteral("title")).toString());
            article.insert(QStringLiteral("publication"), body.value(QStringLiteral("publication")).toString());
            article.insert(QStringLiteral("text"), body.value(QStringLiteral("text")).toString());
            article.insert(QStringLiteral("addedAt"), double(QDateTime::currentMSecsSinceEpoch()));
            kept.prepend(article);
            QJsonArray deleted;
            for (const auto &value : reads.value(QStringLiteral("deletedIds")).toArray())
                if (value.toString() != id)
                    deleted.append(value);
            reads.insert(QStringLiteral("articles"), kept);
            reads.insert(QStringLiteral("deletedIds"), deleted);
            auto next = m_doc;
            next.insert(QStringLiteral("reads"), reads);
            mutate(touchExported(next, QDateTime::currentMSecsSinceEpoch()));
            m_detailId = id;
            m_page = QStringLiteral("article");
            rebuild();
        });
}

void Backend::saveVideo(const QString &url) {
    const QString id = youtubeIdOf(url);
    if (id.isEmpty()) {
        setStatus(tr("Paste a YouTube URL."));
        return;
    }
    auto videos = m_doc.value(QStringLiteral("videos")).toObject();
    auto list = videos.value(QStringLiteral("videos")).toArray();
    QJsonArray kept;
    for (const auto &value : list)
        if (value.toObject().value(QStringLiteral("id")).toString() != id)
            kept.append(value);
    QJsonObject video;
    video.insert(QStringLiteral("id"), id);
    video.insert(QStringLiteral("url"), QStringLiteral("https://www.youtube.com/watch?v=") + id);
    video.insert(QStringLiteral("title"), url);
    video.insert(QStringLiteral("author"), QString());
    video.insert(QStringLiteral("addedAt"), double(QDateTime::currentMSecsSinceEpoch()));
    kept.prepend(video);
    QJsonArray deleted;
    for (const auto &value : videos.value(QStringLiteral("deletedIds")).toArray())
        if (value.toString() != id)
            deleted.append(value);
    videos.insert(QStringLiteral("videos"), kept);
    videos.insert(QStringLiteral("deletedIds"), deleted);
    auto next = m_doc;
    next.insert(QStringLiteral("videos"), videos);
    mutate(touchExported(next, QDateTime::currentMSecsSinceEpoch()));
    resolveVideo(id);
}

void Backend::resolveVideo(const QString &id) {
    setStatus(tr("Opening the video…"));
    api(QStringLiteral("GET"), QStringLiteral("/api/videos/resolve?id=") + QString::fromUtf8(QUrl::toPercentEncoding(id)), {}, true,
        [this, id](QJsonObject body, QString error) {
            if (!error.isEmpty()) {
                setStatus(error);
                return;
            }
            const QString url = body.value(QStringLiteral("videoUrl")).toString();
            const auto video = findById(m_doc.value(QStringLiteral("videos")).toObject().value(QStringLiteral("videos")).toArray(), id);
            playUrl(m_api + url, video.value(QStringLiteral("title")).toString(), tr("Saved Videos"));
        });
}

void Backend::ask(const QString &question) {
    if (!m_includeAi) {
        setStatus(tr("Turn on Include AI credentials in settings. The key stays out of sync until then."));
        go(QStringLiteral("settings"));
        return;
    }
    const auto settings = m_doc.value(QStringLiteral("settings")).toObject();
    const QString provider = settings.value(QStringLiteral("provider")).toString(QStringLiteral("XAI")).toUpper();
    const QString base = providerBase(provider);
    const QString bearer = chatBearer(settings);
    if (base.isEmpty()) {
        setStatus(tr("The web app can only reach a public HTTPS host. A LAN box stays on the phone."));
        return;
    }
    if (bearer.isEmpty()) {
        setStatus(tr("Sign in to the provider on the phone, or save an API key here."));
        return;
    }
    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    QString threadId = m_detailId;
    auto chats = m_doc.value(QStringLiteral("chats")).toArray();
    QJsonObject thread = findById(chats, threadId);
    QJsonArray messages = thread.value(QStringLiteral("messages")).toArray();
    QJsonObject user;
    user.insert(QStringLiteral("id"), newId(now));
    user.insert(QStringLiteral("role"), QStringLiteral("user"));
    user.insert(QStringLiteral("content"), question);
    messages.append(user);
    if (thread.isEmpty()) {
        threadId = newId(now);
        thread.insert(QStringLiteral("id"), threadId);
        thread.insert(QStringLiteral("createdAt"), double(now));
    }
    thread.insert(QStringLiteral("updatedAt"), double(now));
    thread.insert(QStringLiteral("messages"), messages);
    QJsonArray nextChats;
    bool replaced = false;
    for (const auto &value : chats) {
        if (value.toObject().value(QStringLiteral("id")).toString() == threadId) {
            nextChats.append(thread);
            replaced = true;
        } else {
            nextChats.append(value);
        }
    }
    if (!replaced)
        nextChats.append(thread);
    auto next = m_doc;
    next.insert(QStringLiteral("chats"), nextChats);
    m_detailId = threadId;
    m_page = QStringLiteral("chat");
    m_asking = true;
    mutate(touchExported(next, now));
    QJsonArray turns;
    for (const auto &value : messages) {
        const auto message = value.toObject();
        QJsonObject turn;
        turn.insert(QStringLiteral("role"), message.value(QStringLiteral("role")).toString());
        turn.insert(QStringLiteral("content"), message.value(QStringLiteral("content")).toString());
        turns.append(turn);
    }
    QJsonObject plan;
    plan.insert(QStringLiteral("provider"), provider);
    plan.insert(QStringLiteral("kind"), provider == QLatin1String("ANTHROPIC") ? QStringLiteral("anthropic") : QStringLiteral("openai"));
    plan.insert(QStringLiteral("base"), base);
    plan.insert(QStringLiteral("model"), settings.value(QStringLiteral("model")).toString());
    plan.insert(QStringLiteral("bearer"), bearer);
    plan.insert(QStringLiteral("oauth"), !settings.value(QStringLiteral("oauthAccess")).toString().isEmpty());
    plan.insert(QStringLiteral("messages"), turns);
    api(QStringLiteral("POST"), QStringLiteral("/api/ai/chat"), plan, true, [this, threadId](QJsonObject body, QString error) {
        m_asking = false;
        const QString text = error.isEmpty() ? body.value(QStringLiteral("text")).toString() : error;
        auto chats = m_doc.value(QStringLiteral("chats")).toArray();
        QJsonArray updated;
        for (const auto &value : chats) {
            auto thread = value.toObject();
            if (thread.value(QStringLiteral("id")).toString() != threadId) {
                updated.append(thread);
                continue;
            }
            auto messages = thread.value(QStringLiteral("messages")).toArray();
            QJsonObject reply;
            reply.insert(QStringLiteral("id"), newId(QDateTime::currentMSecsSinceEpoch()));
            reply.insert(QStringLiteral("role"), QStringLiteral("assistant"));
            reply.insert(QStringLiteral("content"), text.isEmpty() ? tr("Empty reply from the model.") : text);
            messages.append(reply);
            thread.insert(QStringLiteral("messages"), messages);
            updated.append(thread);
        }
        auto next = m_doc;
        next.insert(QStringLiteral("chats"), updated);
        mutate(touchExported(next, QDateTime::currentMSecsSinceEpoch()));
    });
}

void Backend::subscribeShow(const QString &feedUrl, const QString &title, const QString &author) {
    setStatus(tr("Subscribing…"));
    m_http.request(QStringLiteral("GET"), QUrl(m_api + QStringLiteral("/api/feed?url=") + QString::fromUtf8(QUrl::toPercentEncoding(feedUrl))),
                   {}, m_token, [this, feedUrl, title, author](int status, const QByteArray &body, const QString &error) {
                       if (status < 200 || status >= 300) {
                           setStatus(error.isEmpty() ? tr("Could not read that feed.") : error);
                           return;
                       }
                       auto pods = m_doc.value(QStringLiteral("podcasts")).toObject();
                       auto shows = pods.value(QStringLiteral("shows")).toArray();
                       QJsonArray kept;
                       for (const auto &value : shows)
                           if (value.toObject().value(QStringLiteral("feedUrl")).toString().compare(feedUrl, Qt::CaseInsensitive) != 0)
                               kept.append(value);
                       QJsonObject show;
                       show.insert(QStringLiteral("feedUrl"), feedUrl);
                       show.insert(QStringLiteral("title"), title);
                       show.insert(QStringLiteral("author"), author);
                       show.insert(QStringLiteral("subscribedAt"), double(QDateTime::currentMSecsSinceEpoch()));
                       kept.prepend(show);
                       auto episodes = pods.value(QStringLiteral("episodes")).toArray();
                       for (const auto &value : parseRss(body, feedUrl))
                           episodes.prepend(value);
                       pods.insert(QStringLiteral("shows"), kept);
                       pods.insert(QStringLiteral("episodes"), episodes);
                       auto next = m_doc;
                       next.insert(QStringLiteral("podcasts"), pods);
                       mutate(touchExported(next, QDateTime::currentMSecsSinceEpoch()));
                       m_detailId = feedUrl;
                       m_page = QStringLiteral("show");
                       m_prompt = QStringLiteral(">");
                       rebuild();
                   });
}

void Backend::setWeatherPlace(const QString &query) {
    const QString place = query.trimmed();
    if (place.isEmpty())
        return;
    setStatus(tr("Looking up %1…").arg(place));
    api(QStringLiteral("GET"), QStringLiteral("/api/weather/search?q=") + QString::fromUtf8(QUrl::toPercentEncoding(place)), {}, true,
        [this, place](QJsonObject body, QString error) {
            if (!error.isEmpty()) {
                setStatus(error);
                return;
            }
            const auto results = body.value(QStringLiteral("results")).toArray();
            if (results.isEmpty()) {
                setStatus(tr("No place matches."));
                return;
            }
            const auto hit = results.first().toObject();
            auto settings = m_doc.value(QStringLiteral("settings")).toObject();
            const QString name = hit.value(QStringLiteral("name")).toString(place);
            settings.insert(QStringLiteral("weatherPlace"), name);
            settings.insert(QStringLiteral("weatherLat"), hit.value(QStringLiteral("latitude")).toDouble());
            settings.insert(QStringLiteral("weatherLon"), hit.value(QStringLiteral("longitude")).toDouble());
            auto next = m_doc;
            next.insert(QStringLiteral("settings"), settings);
            mutate(touchExported(next, QDateTime::currentMSecsSinceEpoch()));
            refreshWeather();
        });
}

void Backend::refreshWeather() {
    const auto settings = m_doc.value(QStringLiteral("settings")).toObject();
    const double lat = settings.value(QStringLiteral("weatherLat")).toDouble();
    const double lon = settings.value(QStringLiteral("weatherLon")).toDouble();
    if (!settings.contains(QStringLiteral("weatherLat"))) {
        setStatus(weatherPlace().isEmpty() ? tr("Set a place in settings.") : weatherPlace());
        return;
    }
    api(QStringLiteral("GET"), QStringLiteral("/api/weather/forecast?lat=%1&lon=%2").arg(lat).arg(lon), {}, true,
        [this](QJsonObject body, QString error) {
            if (!error.isEmpty()) {
                setStatus(error);
                return;
            }
            const auto current = body.value(QStringLiteral("current")).toObject();
            const int temp = qRound(current.value(QStringLiteral("temperature_2m")).toDouble());
            const QString kind = weatherCode(current.value(QStringLiteral("weather_code")).toInt());
            setStatus(tr("%1°  %2").arg(temp).arg(kind));
        });
}
