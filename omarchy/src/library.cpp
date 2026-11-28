#include "library.h"

#include "merge.h"

#include <QDateTime>
#include <QDir>
#include <QFile>
#include <QHash>
#include <QJsonDocument>
#include <QLocalSocket>
#include <QRegularExpression>
#include <QSet>
#include <QStandardPaths>
#include <QTimeZone>
#include <QUrl>
#include <QUrlQuery>
#include <QXmlStreamReader>
#include <QtMath>
#include <algorithm>
#include <cstdio>
#include <functional>

namespace {

QJsonArray itemsOf(const QJsonObject &doc) {
    return doc.value(QStringLiteral("items")).toArray();
}

QString itemId(const QJsonObject &item) {
    return item.value(QStringLiteral("id")).toString();
}

bool isTodo(const QJsonObject &item) {
    return item.value(QStringLiteral("kind")).toString().compare(QLatin1String("todo"), Qt::CaseInsensitive) == 0;
}

bool isNote(const QJsonObject &item) {
    return item.value(QStringLiteral("kind")).toString().compare(QLatin1String("note"), Qt::CaseInsensitive) == 0;
}

bool isDone(const QJsonObject &item) {
    const auto value = item.value(QStringLiteral("completedAt"));
    return !value.isUndefined() && !value.isNull();
}

double rankOf(const QJsonObject &item) {
    const double order = docNum(item, "order");
    return order != 0 ? order : docNum(item, "createdAt");
}

QJsonObject replaceItems(QJsonObject doc, const QJsonArray &items, qint64 now) {
    doc.insert(QStringLiteral("items"), items);
    return touchExported(doc, now);
}

QVector<int> daySet(const QJsonValue &value) {
    QVector<int> days;
    for (const auto &entry : value.toArray()) {
        const int day = entry.toInt();
        if (day >= 1 && day <= 7)
            days.append(day);
    }
    std::sort(days.begin(), days.end());
    days.erase(std::unique(days.begin(), days.end()), days.end());
    return days;
}

QJsonArray dayArray(const QVector<int> &days) {
    QJsonArray out;
    for (int day : days)
        out.append(day);
    return out;
}

int isoDay(const QDateTime &local) {
    return local.date().dayOfWeek();
}

QDateTime atTime(const QDateTime &local, int hour, int minute) {
    return QDateTime(local.date(), QTime(hour, minute, 0), local.timeSpec());
}

} // namespace

QString newId(qint64 now) {
    return QString::number(now, 36);
}

bool signupCodeOpens(const QString &code) {
    return code.trimmed().toLower() == QLatin1String("timetobuild");
}

QJsonObject touchExported(QJsonObject doc, qint64 now) {
    doc.insert(QStringLiteral("exportedAt"), double(now));
    if (!doc.contains(QStringLiteral("version")))
        doc.insert(QStringLiteral("version"), 1);
    return doc;
}

QJsonObject addTask(QJsonObject doc, const QString &text, qint64 now) {
    QJsonObject item;
    item.insert(QStringLiteral("id"), newId(now));
    item.insert(QStringLiteral("kind"), QStringLiteral("todo"));
    item.insert(QStringLiteral("text"), text);
    item.insert(QStringLiteral("createdAt"), double(now));
    item.insert(QStringLiteral("completedAt"), QJsonValue(QJsonValue::Null));
    item.insert(QStringLiteral("updatedAt"), 0);
    auto items = itemsOf(doc);
    items.prepend(item);
    return replaceItems(doc, items, now);
}

QJsonObject toggleTask(QJsonObject doc, const QString &id, qint64 now) {
    QJsonArray items;
    for (const auto &value : itemsOf(doc)) {
        auto item = value.toObject();
        if (itemId(item) != id) {
            items.append(item);
            continue;
        }
        const bool markingDone = item.value(QStringLiteral("completedAt")).isNull() ||
                                 item.value(QStringLiteral("completedAt")).isUndefined();
        item.insert(QStringLiteral("completedAt"), markingDone ? QJsonValue(double(now)) : QJsonValue(QJsonValue::Null));
        item.insert(QStringLiteral("updatedAt"), double(now));
        items.append(item);
    }
    return replaceItems(doc, items, now);
}

QJsonObject updateItemText(QJsonObject doc, const QString &id, const QString &text, qint64 now) {
    QJsonArray items;
    for (const auto &value : itemsOf(doc)) {
        auto item = value.toObject();
        if (itemId(item) == id) {
            item.insert(QStringLiteral("text"), text);
            item.insert(QStringLiteral("updatedAt"), double(now));
        }
        items.append(item);
    }
    return replaceItems(doc, items, now);
}

QJsonObject deleteItem(QJsonObject doc, const QString &id, qint64 now) {
    QJsonArray items;
    for (const auto &value : itemsOf(doc)) {
        if (itemId(value.toObject()) != id)
            items.append(value);
    }
    QJsonArray deleted = doc.value(QStringLiteral("deletedIds")).toArray();
    bool seen = false;
    for (const auto &value : deleted)
        if (value.toString() == id)
            seen = true;
    if (!seen)
        deleted.append(id);
    doc.insert(QStringLiteral("deletedIds"), deleted);
    return replaceItems(doc, items, now);
}

QJsonObject moveOpenTask(QJsonObject doc, int from, int to, qint64 now) {
    auto open = openTodos(doc);
    if (from == to || from < 0 || to < 0 || from >= open.size() || to >= open.size())
        return doc;
    const auto moved = open.takeAt(from);
    open.insert(to, moved);
    const qint64 top = now > open.size() ? now - 1 : open.size();
    QHash<QString, QJsonObject> ranked;
    for (int index = 0; index < open.size(); ++index) {
        auto item = open.at(index);
        item.insert(QStringLiteral("order"), double(top - index));
        item.insert(QStringLiteral("orderedAt"), double(now));
        ranked.insert(itemId(item), item);
    }
    QJsonArray items;
    for (const auto &value : itemsOf(doc)) {
        const auto item = value.toObject();
        items.append(ranked.contains(itemId(item)) ? ranked.value(itemId(item)) : item);
    }
    return replaceItems(doc, items, now);
}

QString noteTitle(const QString &text) {
    const QString line = text.split('\n').value(0).trimmed().remove(QRegularExpression(QStringLiteral("^#+\\s*")));
    return line.isEmpty() ? QStringLiteral("untitled") : line;
}

QJsonObject addNote(QJsonObject doc, const QString &title, qint64 now) {
    const QString heading = title.trimmed();
    QJsonObject item;
    item.insert(QStringLiteral("id"), newId(now));
    item.insert(QStringLiteral("kind"), QStringLiteral("note"));
    item.insert(QStringLiteral("text"), heading.isEmpty() ? QStringLiteral("# ") : QStringLiteral("# ") + heading);
    item.insert(QStringLiteral("createdAt"), double(now));
    item.insert(QStringLiteral("completedAt"), QJsonValue(QJsonValue::Null));
    item.insert(QStringLiteral("updatedAt"), 0);
    auto items = itemsOf(doc);
    items.prepend(item);
    return replaceItems(doc, items, now);
}

QVector<QJsonObject> openTodos(const QJsonObject &doc) {
    QVector<QJsonObject> open;
    for (const auto &value : itemsOf(doc)) {
        const auto item = value.toObject();
        if (isTodo(item) && !isDone(item))
            open.append(item);
    }
    std::stable_sort(open.begin(), open.end(), [](const QJsonObject &a, const QJsonObject &b) {
        return rankOf(a) > rankOf(b);
    });
    return open;
}

QVector<QJsonObject> doneTodos(const QJsonObject &doc) {
    QVector<QJsonObject> done;
    for (const auto &value : itemsOf(doc)) {
        const auto item = value.toObject();
        if (isTodo(item) && isDone(item))
            done.append(item);
    }
    std::stable_sort(done.begin(), done.end(), [](const QJsonObject &a, const QJsonObject &b) {
        return docNum(a, "completedAt") > docNum(b, "completedAt");
    });
    return done;
}

QVector<QJsonObject> notesOf(const QJsonObject &doc) {
    QVector<QJsonObject> notes;
    for (const auto &value : itemsOf(doc)) {
        const auto item = value.toObject();
        if (isNote(item))
            notes.append(item);
    }
    std::stable_sort(notes.begin(), notes.end(), [](const QJsonObject &a, const QJsonObject &b) {
        const double ae = docNum(a, "updatedAt") > 0 ? docNum(a, "updatedAt") : docNum(a, "createdAt");
        const double be = docNum(b, "updatedAt") > 0 ? docNum(b, "updatedAt") : docNum(b, "createdAt");
        return ae > be;
    });
    return notes;
}

namespace {

struct TimeHit {
    int hour = -1;
    int minute = 0;
    int start = -1;
    int length = 0;
};

TimeHit findTime(const QString &text) {
    static const QRegularExpression expression(
        QStringLiteral("(?i)(?:^|\\s)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?(?=\\s|$)"));
    auto it = expression.globalMatch(text);
    TimeHit hit;
    while (it.hasNext()) {
        const auto match = it.next();
        int hour = match.captured(1).toInt();
        int minute = match.captured(2).isEmpty() ? 0 : match.captured(2).toInt();
        const QString suffix = match.captured(3).toLower();
        if (!suffix.isEmpty()) {
            if (hour < 1 || hour > 12 || minute > 59)
                continue;
            if (suffix == QLatin1String("pm") && hour < 12)
                hour += 12;
            if (suffix == QLatin1String("am") && hour == 12)
                hour = 0;
        } else if (hour > 23 || minute > 59) {
            continue;
        }
        hit.hour = hour;
        hit.minute = minute;
        hit.start = match.capturedStart(1);
        hit.length = match.capturedEnd(0) - hit.start;
        if (!suffix.isEmpty() || !match.captured(2).isEmpty())
            return hit;
    }
    return hit;
}

QVector<int> extractDays(QString &text) {
    const QString lower = text.toLower();
    QVector<int> days;
    auto take = [&](const QString &word, const QVector<int> &set) {
        const int at = lower.indexOf(word);
        if (at < 0)
            return false;
        text.remove(at, word.size());
        days = set;
        return true;
    };
    if (take(QStringLiteral("weekdays"), {1, 2, 3, 4, 5}) || take(QStringLiteral("weekends"), {6, 7}) ||
        take(QStringLiteral("daily"), {1, 2, 3, 4, 5, 6, 7})) {
        text = text.simplified();
        return days;
    }
    const QStringList names = {QStringLiteral("mon"), QStringLiteral("tue"), QStringLiteral("wed"), QStringLiteral("thu"),
                               QStringLiteral("fri"), QStringLiteral("sat"), QStringLiteral("sun")};
    for (int index = 0; index < names.size(); ++index) {
        const auto expression = QRegularExpression(QStringLiteral("\\b") + names.at(index) + QStringLiteral("\\w*\\b"),
                                                   QRegularExpression::CaseInsensitiveOption);
        if (expression.match(text).hasMatch()) {
            text.remove(expression);
            days.append(index + 1);
        }
    }
    std::sort(days.begin(), days.end());
    text = text.simplified();
    return days;
}

} // namespace

QVector<ParsedAlarm> parseAlarmInputs(const QString &raw) {
    QString text = raw.trimmed();
    if (text.isEmpty())
        return {};
    int everyMinutes = 0;
    static const QRegularExpression interval(
        QStringLiteral("(?i)\\bevery\\s+(\\d+)\\s+(hours?|minutes?)\\b(?:\\s+starting(?:\\s+at)?)?"));
    const auto intervalMatch = interval.match(text);
    if (intervalMatch.hasMatch()) {
        const int count = intervalMatch.captured(1).toInt();
        const bool hours = intervalMatch.captured(2).startsWith(QLatin1String("hour"), Qt::CaseInsensitive);
        everyMinutes = hours ? count * 60 : count;
        text.remove(intervalMatch.capturedStart(), intervalMatch.capturedLength());
        text = text.simplified();
        if (everyMinutes <= 0 || 24 * 60 % everyMinutes != 0 || (24 * 60 / everyMinutes) > 24)
            return {};
    }
    const auto days = extractDays(text);
    const auto time = findTime(text);
    if (time.hour < 0)
        return {};
    QString label = text;
    label.remove(time.start, time.length);
    label.remove(QRegularExpression(QStringLiteral("(?i)\\b(starting|at)\\b")));
    label = label.simplified();
    QVector<ParsedAlarm> out;
    if (everyMinutes == 0) {
        out.append({time.hour, time.minute, label, days});
        return out;
    }
    int minutes = time.hour * 60 + time.minute;
    const int count = 24 * 60 / everyMinutes;
    for (int index = 0; index < count; ++index) {
        const int wrapped = (minutes + index * everyMinutes) % (24 * 60);
        out.append({wrapped / 60, wrapped % 60, label, days});
    }
    return out;
}

QString formatAlarmTime(int hour, int minute) {
    return QStringLiteral("%1:%2").arg(hour, 2, 10, QLatin1Char('0')).arg(minute, 2, 10, QLatin1Char('0'));
}

QString formatAlarmDays(const QVector<int> &days) {
    QVector<int> wanted;
    for (int day : days)
        if (day >= 1 && day <= 7)
            wanted.append(day);
    std::sort(wanted.begin(), wanted.end());
    wanted.erase(std::unique(wanted.begin(), wanted.end()), wanted.end());
    if (wanted.isEmpty())
        return {};
    if (wanted.size() == 7)
        return QStringLiteral("daily");
    if (wanted == QVector<int>({1, 2, 3, 4, 5}))
        return QStringLiteral("weekdays");
    if (wanted == QVector<int>({6, 7}))
        return QStringLiteral("weekends");
    const QStringList names = {QString(), QStringLiteral("Mon"), QStringLiteral("Tue"), QStringLiteral("Wed"),
                               QStringLiteral("Thu"), QStringLiteral("Fri"), QStringLiteral("Sat"), QStringLiteral("Sun")};
    QStringList parts;
    for (int day : wanted)
        parts.append(names.at(day));
    return parts.join(' ');
}

QJsonObject addAlarms(QJsonObject doc, const QVector<ParsedAlarm> &alarms, qint64 now) {
    auto list = doc.value(QStringLiteral("alarms")).toArray();
    auto deleted = doc.value(QStringLiteral("deletedAlarmIds")).toArray();
    for (int index = 0; index < alarms.size(); ++index) {
        const auto &alarm = alarms.at(index);
        const QString id = newId(now + index);
        QJsonObject row;
        row.insert(QStringLiteral("id"), id);
        row.insert(QStringLiteral("hour"), alarm.hour);
        row.insert(QStringLiteral("minute"), alarm.minute);
        row.insert(QStringLiteral("enabled"), true);
        row.insert(QStringLiteral("label"), alarm.label);
        row.insert(QStringLiteral("days"), dayArray(alarm.days));
        row.insert(QStringLiteral("snoozeUntil"), QJsonValue(QJsonValue::Null));
        row.insert(QStringLiteral("lastFiredAt"), QJsonValue(QJsonValue::Null));
        row.insert(QStringLiteral("updatedAt"), double(now));
        list.append(row);
    }
    doc.insert(QStringLiteral("alarms"), list);
    doc.insert(QStringLiteral("deletedAlarmIds"), deleted);
    return touchExported(doc, now);
}

QJsonObject mapAlarm(QJsonObject doc, const QString &id, const std::function<QJsonObject(QJsonObject)> &edit, qint64 now) {
    QJsonArray list;
    for (const auto &value : doc.value(QStringLiteral("alarms")).toArray()) {
        auto alarm = value.toObject();
        if (itemId(alarm) == id)
            alarm = edit(alarm);
        list.append(alarm);
    }
    doc.insert(QStringLiteral("alarms"), list);
    return touchExported(doc, now);
}

QJsonObject toggleAlarm(QJsonObject doc, const QString &id, qint64 now) {
    return mapAlarm(
        doc, id,
        [now](QJsonObject alarm) {
            const bool enabled = alarm.value(QStringLiteral("enabled")).isBool() ? alarm.value(QStringLiteral("enabled")).toBool() : true;
            alarm.insert(QStringLiteral("enabled"), !enabled);
            alarm.insert(QStringLiteral("updatedAt"), double(now));
            if (enabled)
                alarm.insert(QStringLiteral("snoozeUntil"), QJsonValue(QJsonValue::Null));
            return alarm;
        },
        now);
}

QJsonObject deleteAlarm(QJsonObject doc, const QString &id, qint64 now) {
    QJsonArray list;
    for (const auto &value : doc.value(QStringLiteral("alarms")).toArray())
        if (itemId(value.toObject()) != id)
            list.append(value);
    auto deleted = doc.value(QStringLiteral("deletedAlarmIds")).toArray();
    deleted.append(id);
    doc.insert(QStringLiteral("alarms"), list);
    doc.insert(QStringLiteral("deletedAlarmIds"), deleted);
    return touchExported(doc, now);
}

QJsonObject snoozeAlarm(QJsonObject doc, const QString &id, qint64 now) {
    return mapAlarm(
        doc, id,
        [now](QJsonObject alarm) {
            alarm.insert(QStringLiteral("enabled"), true);
            alarm.insert(QStringLiteral("snoozeUntil"), double(now + 8 * 60 * 1000));
            alarm.insert(QStringLiteral("updatedAt"), double(now));
            return alarm;
        },
        now);
}

QJsonObject acknowledgeAlarm(QJsonObject doc, const QString &id, qint64 now) {
    return mapAlarm(
        doc, id,
        [now](QJsonObject alarm) {
            const double last = docNum(alarm, "lastFiredAt");
            alarm.insert(QStringLiteral("snoozeUntil"), QJsonValue(QJsonValue::Null));
            alarm.insert(QStringLiteral("lastFiredAt"), double(std::max(last, double(now))));
            return alarm;
        },
        now);
}

QJsonObject stampAlarmFired(QJsonObject doc, const QString &id, qint64 now) {
    return mapAlarm(
        doc, id,
        [now](QJsonObject alarm) {
            alarm.insert(QStringLiteral("snoozeUntil"), QJsonValue(QJsonValue::Null));
            alarm.insert(QStringLiteral("lastFiredAt"), double(now));
            return alarm;
        },
        now);
}

qint64 nextTrigger(int hour, int minute, qint64 now, const QVector<int> &days) {
    const QDateTime local = QDateTime::fromMSecsSinceEpoch(now);
    QDateTime candidate = atTime(local, hour, minute);
    if (!candidate.isValid() || candidate <= local)
        candidate = candidate.addDays(1);
    if (days.isEmpty())
        return candidate.toMSecsSinceEpoch();
    for (int hop = 0; hop < 8; ++hop) {
        if (days.contains(isoDay(candidate)) && candidate > local)
            return candidate.toMSecsSinceEpoch();
        candidate = candidate.addDays(1);
    }
    return candidate.toMSecsSinceEpoch();
}

qint64 previousTrigger(int hour, int minute, qint64 now, const QVector<int> &days) {
    const QDateTime local = QDateTime::fromMSecsSinceEpoch(now);
    QDateTime candidate = atTime(local, hour, minute);
    if (!candidate.isValid() || candidate > local)
        candidate = candidate.addDays(-1);
    if (days.isEmpty())
        return candidate.toMSecsSinceEpoch();
    for (int hop = 0; hop < 8; ++hop) {
        if (days.contains(isoDay(candidate)) && candidate <= local)
            return candidate.toMSecsSinceEpoch();
        candidate = candidate.addDays(-1);
    }
    return candidate.toMSecsSinceEpoch();
}

bool alarmDue(const QJsonObject &alarm, qint64 now) {
    const auto enabled = alarm.value(QStringLiteral("enabled"));
    if (enabled.isBool() && !enabled.toBool())
        return false;
    const double snooze = docNum(alarm, "snoozeUntil");
    if (snooze > now)
        return false;
    if (snooze > 0 && snooze <= now && alarm.value(QStringLiteral("snoozeUntil")).isDouble())
        return docNum(alarm, "lastFiredAt") < snooze;
    const auto days = daySet(alarm.value(QStringLiteral("days")));
    const qint64 prev = previousTrigger(alarm.value(QStringLiteral("hour")).toInt(), alarm.value(QStringLiteral("minute")).toInt(), now, days);
    const qint64 age = now - prev;
    if (age < 1 || age >= 2LL * 60 * 60 * 1000)
        return false;
    return docNum(alarm, "lastFiredAt") < prev;
}

namespace {

struct Parser {
    QString text;
    int index = 0;
    bool ok = true;
    QChar peek() const { return index < text.size() ? text.at(index) : QChar(); }
    void skip() {
        while (peek().isSpace())
            ++index;
    }
    double parseExpr();
    double parseTerm();
    double parseFactor();
};

double Parser::parseExpr() {
    double value = parseTerm();
    while (ok) {
        skip();
        if (peek() == '+') {
            ++index;
            value += parseTerm();
        } else if (peek() == '-') {
            ++index;
            value -= parseTerm();
        } else {
            break;
        }
    }
    return value;
}

double Parser::parseTerm() {
    double value = parseFactor();
    while (ok) {
        skip();
        if (peek() == '*') {
            ++index;
            value *= parseFactor();
        } else if (peek() == '/') {
            ++index;
            const double right = parseFactor();
            if (right == 0) {
                ok = false;
                return 0;
            }
            value /= right;
        } else {
            break;
        }
    }
    return value;
}

double Parser::parseFactor() {
    skip();
    if (peek() == '(') {
        ++index;
        const double value = parseExpr();
        skip();
        if (peek() != ')')
            ok = false;
        else
            ++index;
        return value;
    }
    if (peek() == '-') {
        ++index;
        return -parseFactor();
    }
    const int start = index;
    while (peek().isDigit() || peek() == '.')
        ++index;
    if (start == index) {
        ok = false;
        return 0;
    }
    bool converted = false;
    const double value = text.mid(start, index - start).toDouble(&converted);
    ok = ok && converted;
    return value;
}

} // namespace

bool looksLikeMath(const QString &text) {
    const QString compact = text.simplified();
    if (compact.size() < 3)
        return false;
    static const QRegularExpression expression(QStringLiteral("^[0-9.\\s+\\-*/()]+$"));
    if (!expression.match(compact).hasMatch())
        return false;
    return compact.contains(QRegularExpression(QStringLiteral("[+\\-*/]")));
}

QString evaluateMath(const QString &text) {
    if (!looksLikeMath(text))
        return {};
    Parser parser;
    parser.text = text.simplified();
    const double value = parser.parseExpr();
    parser.skip();
    if (!parser.ok || parser.index != parser.text.size() || !std::isfinite(value))
        return {};
    if (qFuzzyCompare(value, qRound(value)) && qAbs(value) < 1e15)
        return QString::number(qRound(value));
    return QString::number(value, 'g', 12);
}

QString articleIdForUrl(const QString &raw) {
    QString text = raw.trimmed();
    if (text.isEmpty() || text.contains(' '))
        return {};
    if (!text.contains(QLatin1String("://"))) {
        if (!text.contains('.') || text.startsWith('/') || text.startsWith('.'))
            return {};
        text.prepend(QStringLiteral("https://"));
    }
    QUrl url(text);
    if (!url.isValid() || (url.scheme() != QLatin1String("http") && url.scheme() != QLatin1String("https")))
        return {};
    if (!url.userName().isEmpty() || !url.password().isEmpty())
        return {};
    url.setHost(url.host().toLower());
    url.setFragment({});
    if ((url.scheme() == QLatin1String("https") && url.port() == 443) ||
        (url.scheme() == QLatin1String("http") && url.port() == 80))
        url.setPort(-1);
    if (url.path().size() > 1 && url.path().endsWith('/'))
        url.setPath(url.path().chopped(1));
    const QByteArray key = url.toString(QUrl::FullyEncoded).toUtf8();
    quint64 hash = 0xcbf29ce484222325ULL;
    for (unsigned char byte : key) {
        hash ^= byte;
        hash *= 0x100000001b3ULL;
    }
    return QStringLiteral("r") + QString::number(hash, 16).rightJustified(16, QLatin1Char('0'));
}

QString savedVideoId(const QString &raw) {
    const QString id = youtubeIdOf(raw);
    return id.isEmpty() ? QString() : QStringLiteral("v") + id;
}

QStringList slashNames() {
    return {QStringLiteral("articles"), QStringLiteral("clock"),  QStringLiteral("help"),     QStringLiteral("home"),
            QStringLiteral("notes"),    QStringLiteral("podcasts"), QStringLiteral("pull"),   QStringLiteral("push"),
            QStringLiteral("settings"), QStringLiteral("stocks"),  QStringLiteral("tasks"),   QStringLiteral("videos"),
            QStringLiteral("weather")};
}

QString slashCommand(const QString &query) {
    const QString q = query.trimmed().toLower();
    if (q.isEmpty())
        return {};
    const QHash<QString, QString> alias = {
        {QStringLiteral("note"), QStringLiteral("notes")},       {QStringLiteral("stock"), QStringLiteral("stocks")},
        {QStringLiteral("podcast"), QStringLiteral("podcasts")}, {QStringLiteral("pods"), QStringLiteral("podcasts")},
        {QStringLiteral("article"), QStringLiteral("articles")}, {QStringLiteral("video"), QStringLiteral("videos")},
        {QStringLiteral("setting"), QStringLiteral("settings")}, {QStringLiteral("backup"), QStringLiteral("settings")},
        {QStringLiteral("todo"), QStringLiteral("tasks")},       {QStringLiteral("todos"), QStringLiteral("tasks")},
        {QStringLiteral("ai"), QStringLiteral("chat")},          {QStringLiteral("ask"), QStringLiteral("chat")},
        {QStringLiteral("chat"), QStringLiteral("chat")}};
    if (alias.contains(q))
        return alias.value(q);
    const QStringList names = slashNames();
    if (names.contains(q))
        return q;
    QStringList hits;
    for (const auto &name : names)
        if (name.startsWith(q))
            hits.append(name);
    return hits.size() == 1 ? hits.first() : QString();
}

qint64 feedDurationMs(const QString &raw) {
    const QString text = raw.trimmed();
    if (text.isEmpty())
        return 0;
    const auto parts = text.split(QLatin1Char(':'));
    if (parts.size() == 1) {
        bool ok = false;
        const double seconds = text.toDouble(&ok);
        return ok ? qint64(seconds * 1000.0) : 0;
    }
    qint64 total = 0;
    for (const auto &part : parts) {
        bool ok = false;
        total = total * 60 + part.toInt(&ok);
        if (!ok)
            return 0;
    }
    return total * 1000;
}

QJsonArray parseFeedEpisodes(const QByteArray &xml, const QString &feedUrl) {
    QXmlStreamReader reader(xml);
    QString itemTitle;
    QString enclosure;
    QString guid;
    QString pubDate;
    QString duration;
    bool inItem = false;
    QJsonArray episodes;
    while (!reader.atEnd()) {
        reader.readNext();
        if (reader.isStartElement()) {
            const auto name = reader.name();
            if (name == QLatin1String("item") || name == QLatin1String("entry")) {
                inItem = true;
                itemTitle.clear();
                enclosure.clear();
                guid.clear();
                pubDate.clear();
                duration.clear();
            } else if (inItem && name == QLatin1String("title")) {
                itemTitle = reader.readElementText(QXmlStreamReader::IncludeChildElements).trimmed();
            } else if (inItem && (name == QLatin1String("guid") || name == QLatin1String("id"))) {
                guid = reader.readElementText().trimmed();
            } else if (inItem && (name == QLatin1String("pubDate") || name == QLatin1String("published"))) {
                pubDate = reader.readElementText().trimmed();
            } else if (inItem && name == QLatin1String("duration")) {
                duration = reader.readElementText().trimmed();
            } else if (inItem && name == QLatin1String("enclosure")) {
                enclosure = reader.attributes().value(QStringLiteral("url")).toString();
            }
        } else if (reader.isEndElement() && (reader.name() == QLatin1String("item") || reader.name() == QLatin1String("entry"))) {
            inItem = false;
            if (itemTitle.isEmpty() || enclosure.isEmpty())
                continue;
            QJsonObject episode;
            episode.insert(QStringLiteral("id"), guid.isEmpty() ? enclosure : guid);
            episode.insert(QStringLiteral("showId"), feedUrl);
            episode.insert(QStringLiteral("title"), itemTitle);
            episode.insert(QStringLiteral("enclosureUrl"), enclosure);
            const QDateTime when = QDateTime::fromString(pubDate, Qt::RFC2822Date);
            episode.insert(QStringLiteral("pubDate"), when.isValid() ? double(when.toMSecsSinceEpoch()) : 0);
            episode.insert(QStringLiteral("durationMs"), double(feedDurationMs(duration)));
            episodes.append(episode);
            if (episodes.size() >= 40)
                break;
        }
    }
    return episodes;
}

QStringList feedsToRefresh(const QJsonObject &doc, qint64 now, const QString &openFeed, qint64 staleMs) {
    const auto pods = doc.value(QStringLiteral("podcasts")).toObject();
    QSet<QString> have;
    for (const auto &value : pods.value(QStringLiteral("episodes")).toArray()) {
        const auto episode = value.toObject();
        const QString showId = episode.value(QStringLiteral("showId")).toString(episode.value(QStringLiteral("feedUrl")).toString());
        if (!showId.isEmpty())
            have.insert(showId.toLower());
    }
    struct Due {
        QString url;
        int rank = 1;
    };
    QVector<Due> due;
    const QString open = openFeed.trimmed().toLower();
    for (const auto &value : pods.value(QStringLiteral("shows")).toArray()) {
        const auto show = value.toObject();
        const QString feed = show.value(QStringLiteral("feedUrl")).toString();
        if (feed.isEmpty())
            continue;
        const QString key = feed.toLower();
        const qint64 last = qint64(show.value(QStringLiteral("lastCheckedAt")).toDouble());
        if (have.contains(key) && last > 0 && now - last < staleMs)
            continue;
        due.append(Due{feed, !open.isEmpty() && key == open ? 0 : 1});
    }
    std::stable_sort(due.begin(), due.end(), [](const Due &a, const Due &b) { return a.rank < b.rank; });
    QStringList urls;
    for (const auto &item : due)
        urls.append(item.url);
    return urls;
}

qint64 speechDurationMs(const QString &text) {
    int words = 0;
    static const QRegularExpression splitter(QStringLiteral("\\s+"));
    static const QRegularExpression letter(QStringLiteral("[0-9A-Za-z]"));
    for (const auto &word : text.split(splitter, Qt::SkipEmptyParts))
        if (word.contains(letter))
            ++words;
    if (words <= 0)
        return 0;
    return words * 60000LL / 160;
}

qint64 articlePositionMs(const QJsonObject &doc, const QString &articleId) {
    if (articleId.isEmpty())
        return 0;
    for (const auto &value : doc.value(QStringLiteral("reads")).toObject().value(QStringLiteral("progress")).toArray()) {
        const auto row = value.toObject();
        if (row.value(QStringLiteral("articleId")).toString() == articleId)
            return qMax<qint64>(0, qint64(row.value(QStringLiteral("positionMs")).toDouble()));
    }
    return 0;
}

double articleReadingFraction(const QJsonObject &doc, const QString &articleId) {
    QString text;
    for (const auto &value : doc.value(QStringLiteral("reads")).toObject().value(QStringLiteral("articles")).toArray()) {
        const auto article = value.toObject();
        if (article.value(QStringLiteral("id")).toString() == articleId)
            text = article.value(QStringLiteral("text")).toString();
    }
    const qint64 duration = speechDurationMs(text);
    if (duration <= 0)
        return 0;
    return std::clamp(double(articlePositionMs(doc, articleId)) / double(duration), 0.0, 1.0);
}

QString youtubeIdOf(const QString &raw) {
    const QString text = raw.trimmed();
    static const QRegularExpression id(QStringLiteral("^[A-Za-z0-9_-]{11}$"));
    if (id.match(text).hasMatch())
        return text;
    QUrl url(text.contains(QLatin1String("://")) ? text : QStringLiteral("https://") + text);
    if (!url.isValid())
        return {};
    QString host = url.host().toLower();
    if (host.startsWith(QLatin1String("www.")))
        host = host.mid(4);
    if (host == QLatin1String("youtu.be")) {
        const QString part = url.path().split('/', Qt::SkipEmptyParts).value(0);
        return id.match(part).hasMatch() ? part : QString();
    }
    if (host != QLatin1String("youtube.com") && host != QLatin1String("m.youtube.com") &&
        host != QLatin1String("music.youtube.com"))
        return {};
    const QString fromQuery = url.query(QUrl::FullyDecoded).contains(QLatin1String("v=")) ? QUrlQuery(url).queryItemValue(QStringLiteral("v")) : QString();
    if (id.match(fromQuery).hasMatch())
        return fromQuery;
    const auto parts = url.path().split('/', Qt::SkipEmptyParts);
    const QStringList markers = {QStringLiteral("shorts"), QStringLiteral("embed"), QStringLiteral("live"), QStringLiteral("v")};
    for (int index = 0; index < parts.size(); ++index) {
        if (markers.contains(parts.at(index)) && index + 1 < parts.size() && id.match(parts.at(index + 1)).hasMatch())
            return parts.at(index + 1);
    }
    return {};
}

QVector<int> alarmDays(const QJsonObject &alarm) {
    QVector<int> days;
    for (const auto &value : alarm.value(QStringLiteral("days")).toArray()) {
        const int day = value.toInt();
        if (day >= 1 && day <= 7)
            days.append(day);
    }
    return days;
}

QJsonObject findById(const QJsonArray &list, const QString &id, const char *key) {
    for (const auto &value : list) {
        const auto item = value.toObject();
        if (item.value(QLatin1String(key)).toString() == id)
            return item;
    }
    return {};
}

QJsonObject restoreLocalMedia(QJsonObject adopted, const QJsonObject &local) {
    auto pods = adopted.value(QStringLiteral("podcasts")).toObject();
    const auto localPods = local.value(QStringLiteral("podcasts")).toObject();
    if (pods.value(QStringLiteral("episodes")).toArray().isEmpty())
        pods.insert(QStringLiteral("episodes"), localPods.value(QStringLiteral("episodes")));
    if (!pods.contains(QStringLiteral("cacheBytes")))
        pods.insert(QStringLiteral("cacheBytes"), localPods.value(QStringLiteral("cacheBytes")));
    adopted.insert(QStringLiteral("podcasts"), pods);
    return adopted;
}

bool slimDiffers(const QJsonObject &left, const QJsonObject &right) {
    return !jsonEqual(slimDoc(left), slimDoc(right));
}

bool playbackDue(qint64 lastSavedAt, qint64 now, qint64 lastPos, qint64 pos, bool force) {
    if (force)
        return true;
    return !(now - lastSavedAt < 10000 && qAbs(pos - lastPos) < 10000);
}

bool playbackFinished(qint64 positionMs, qint64 durationMs) {
    if (durationMs <= 0)
        return false;
    const qint64 remaining = durationMs - positionMs;
    return remaining <= 30000 || positionMs >= durationMs * 95 / 100;
}

namespace {

QString currencySymbol(const QString &code) {
    const QString key = code.trimmed().toUpper();
    static const QHash<QString, QString> symbols = {
        {QStringLiteral("USD"), QStringLiteral("$")},  {QStringLiteral("EUR"), QStringLiteral("€")},
        {QStringLiteral("GBP"), QStringLiteral("£")},  {QStringLiteral("JPY"), QStringLiteral("¥")},
        {QStringLiteral("CNY"), QStringLiteral("¥")},  {QStringLiteral("DKK"), QStringLiteral("kr")},
        {QStringLiteral("SEK"), QStringLiteral("kr")}, {QStringLiteral("NOK"), QStringLiteral("kr")},
        {QStringLiteral("CHF"), QStringLiteral("CHF")},{QStringLiteral("CAD"), QStringLiteral("$")},
        {QStringLiteral("AUD"), QStringLiteral("$")},  {QStringLiteral("NZD"), QStringLiteral("$")},
        {QStringLiteral("INR"), QStringLiteral("₹")},  {QStringLiteral("KRW"), QStringLiteral("₩")},
        {QStringLiteral("HKD"), QStringLiteral("$")},  {QStringLiteral("SGD"), QStringLiteral("$")},
        {QStringLiteral("BRL"), QStringLiteral("R$")}, {QStringLiteral("ZAR"), QStringLiteral("R")},
        {QStringLiteral("PLN"), QStringLiteral("zł")}, {QStringLiteral("TRY"), QStringLiteral("₺")},
        {QStringLiteral("CZK"), QStringLiteral("Kč")}, {QStringLiteral("HUF"), QStringLiteral("Ft")},
        {QStringLiteral("ISK"), QStringLiteral("kr")}};
    return symbols.value(key, key);
}

int quotePrecision(const QString &currency, double price) {
    const QString key = currency.trimmed().toUpper();
    if (key == QLatin1String("JPY") || key == QLatin1String("KRW") || key == QLatin1String("VND") ||
        key == QLatin1String("HUF") || key == QLatin1String("ISK"))
        return 0;
    if (qAbs(price) > 0 && qAbs(price) < 1.0)
        return 4;
    return 2;
}

int quoteTenths(double percent) {
    return qRound(percent * 10.0);
}

// The bar's text is untrusted. Dropping the markup characters keeps a title
// such as "<img src='https://example.invalid/t'>" from being fetched as rich text.
QString plainBar(const QString &text) {
    QString out;
    out.reserve(text.size());
    for (const QChar &ch : text) {
        if (ch == QLatin1Char('<') || ch == QLatin1Char('>') || ch == QLatin1Char('&'))
            continue;
        if (ch == QLatin1Char('\n') || ch == QLatin1Char('\r') || ch == QLatin1Char('\t'))
            out.append(QLatin1Char(' '));
        else if (ch.category() != QChar::Other_Control)
            out.append(ch);
    }
    out = out.simplified();
    if (out.size() > 180)
        out = out.left(177) + QStringLiteral("...");
    return out;
}

bool barIdOk(const QString &id) {
    static const QRegularExpression pattern(QStringLiteral("^[A-Za-z0-9_-]{1,80}$"));
    return pattern.match(id).hasMatch();
}

} // namespace

QString barTaskText(const QString &text) {
    QString out;
    out.reserve(text.size());
    for (const QChar &ch : text) {
        if (ch == QLatin1Char('\n') || ch == QLatin1Char('\r') || ch == QLatin1Char('\t'))
            out.append(QLatin1Char(' '));
        else if (ch.category() != QChar::Other_Control)
            out.append(ch);
    }
    out = out.simplified();
    if (out.size() > 400)
        out.truncate(400);
    if (!out.isEmpty() && out.back().isHighSurrogate())
        out.chop(1);
    return out;
}

Quote quoteFromChart(const QJsonObject &body) {
    Quote quote;
    const auto results = body.value(QStringLiteral("chart")).toObject().value(QStringLiteral("result")).toArray();
    if (results.isEmpty())
        return quote;
    const auto meta = results.first().toObject().value(QStringLiteral("meta")).toObject();
    if (!meta.contains(QStringLiteral("regularMarketPrice")))
        return quote;
    quote.price = meta.value(QStringLiteral("regularMarketPrice")).toDouble();
    const double previous = meta.contains(QStringLiteral("chartPreviousClose"))
                                ? meta.value(QStringLiteral("chartPreviousClose")).toDouble()
                                : meta.value(QStringLiteral("previousClose")).toDouble(quote.price);
    quote.percent = previous == 0 ? 0 : (quote.price - previous) / previous * 100.0;
    quote.currency = meta.value(QStringLiteral("currency")).toString();
    quote.ok = true;
    return quote;
}

QString formatQuotePrice(const Quote &quote, const QLocale &locale) {
    if (!quote.ok)
        return {};
    const int precision = quotePrecision(quote.currency, quote.price);
    const QString symbol = currencySymbol(quote.currency);
    if (symbol.isEmpty())
        return locale.toString(quote.price, 'f', precision);
    return locale.toCurrencyString(quote.price, symbol, precision);
}

QString formatQuoteChange(const Quote &quote, const QLocale &locale) {
    const int tenths = quoteTenths(quote.percent);
    const QString body = locale.toString(qAbs(tenths / 10.0), 'f', 1);
    if (tenths > 0)
        return QStringLiteral("+") + body + QLatin1Char('%');
    if (tenths < 0)
        return locale.negativeSign() + body + QLatin1Char('%');
    return body + QLatin1Char('%');
}

QString quoteTone(double percent) {
    const int tenths = quoteTenths(percent);
    if (tenths > 0)
        return QStringLiteral("up");
    if (tenths < 0)
        return QStringLiteral("down");
    return QStringLiteral("flat");
}

QString deskDataDir() {
    const QString dir = QStandardPaths::writableLocation(QStandardPaths::AppDataLocation);
    QDir().mkpath(dir);
    return dir;
}

QString barSocketPath() {
    return deskDataDir() + QStringLiteral("/bar.sock");
}

QString barStatusPath() {
    return deskDataDir() + QStringLiteral("/bar.json");
}

QJsonObject barStatus(const QJsonObject &doc, const QHash<QString, Quote> &quotes, const BarMedia &media,
                      const BarMedia &reading, const QString &weatherTemp, const QLocale &locale) {
    QJsonObject status;
    QJsonArray todos;
    int shown = 0;
    for (const auto &item : openTodos(doc)) {
        if (shown++ >= 3)
            break;
        const QString id = item.value(QStringLiteral("id")).toString();
        if (!barIdOk(id))
            continue;
        QJsonObject row;
        row.insert(QStringLiteral("id"), id);
        row.insert(QStringLiteral("title"), plainBar(item.value(QStringLiteral("text")).toString()));
        todos.append(row);
    }
    status.insert(QStringLiteral("todos"), todos);

    const auto mediaRow = [](const BarMedia &item) {
        QJsonObject row;
        row.insert(QStringLiteral("title"), plainBar(item.title));
        row.insert(QStringLiteral("detail"), plainBar(item.detail));
        row.insert(QStringLiteral("kind"), item.kind == QLatin1String("article") ? QStringLiteral("article") : QStringLiteral("episode"));
        row.insert(QStringLiteral("playing"), item.playing);
        row.insert(QStringLiteral("position"), plainBar(item.position));
        return row;
    };
    if (!plainBar(media.title).isEmpty())
        status.insert(QStringLiteral("media"), mediaRow(media));
    if (!plainBar(reading.title).isEmpty())
        status.insert(QStringLiteral("reading"), mediaRow(reading));

    QJsonArray ticker;
    int quoted = 0;
    for (const auto &value : doc.value(QStringLiteral("watchlist")).toArray()) {
        if (quoted++ >= 12)
            break;
        const QString symbol = value.toObject().value(QStringLiteral("symbol")).toString().trimmed();
        if (symbol.isEmpty())
            continue;
        QJsonObject row;
        row.insert(QStringLiteral("symbol"), plainBar(symbol));
        const Quote quote = quotes.value(symbol.toUpper());
        if (quote.ok) {
            row.insert(QStringLiteral("price"), formatQuotePrice(quote, locale));
            row.insert(QStringLiteral("change"), formatQuoteChange(quote, locale));
            row.insert(QStringLiteral("tone"), quoteTone(quote.percent));
        }
        ticker.append(row);
    }
    status.insert(QStringLiteral("quotes"), ticker);

    const QString place = doc.value(QStringLiteral("settings")).toObject().value(QStringLiteral("weatherPlace")).toString();
    if (!plainBar(place).isEmpty() || !plainBar(weatherTemp).isEmpty()) {
        QJsonObject weather;
        weather.insert(QStringLiteral("place"), plainBar(place));
        weather.insert(QStringLiteral("temp"), plainBar(weatherTemp));
        status.insert(QStringLiteral("weather"), weather);
    }

    for (const auto &value : doc.value(QStringLiteral("alarms")).toArray()) {
        const auto alarm = value.toObject();
        if (alarm.value(QStringLiteral("enabled")).isBool() && !alarm.value(QStringLiteral("enabled")).toBool())
            continue;
        QJsonObject row;
        row.insert(QStringLiteral("time"),
                   formatAlarmTime(alarm.value(QStringLiteral("hour")).toInt(), alarm.value(QStringLiteral("minute")).toInt()));
        row.insert(QStringLiteral("label"), plainBar(alarm.value(QStringLiteral("label")).toString()));
        status.insert(QStringLiteral("alarm"), row);
        break;
    }
    return status;
}

bool barDeskListening() {
    QLocalSocket socket;
    socket.connectToServer(barSocketPath());
    if (!socket.waitForConnected(200))
        return false;
    socket.disconnectFromServer();
    return true;
}

int sendBarCommand(const QStringList &args) {
    if (args.isEmpty() || args.first() == QLatin1String("status")) {
        QFile file(barStatusPath());
        QByteArray body = QByteArrayLiteral("{}\n");
        if (file.open(QIODevice::ReadOnly)) {
            body = file.readAll();
            if (!body.endsWith('\n'))
                body.append('\n');
        }
        QFile out;
        if (out.open(stdout, QIODevice::WriteOnly))
            out.write(body);
        return 0;
    }

    const QString name = args.first();
    QJsonObject cmd;
    if (name == QLatin1String("play-pause") || name == QLatin1String("ping")) {
        cmd.insert(QStringLiteral("cmd"), name);
    } else if (name == QLatin1String("back") || name == QLatin1String("forward")) {
        cmd.insert(QStringLiteral("cmd"), QStringLiteral("seek"));
        cmd.insert(QStringLiteral("ms"), name == QLatin1String("back") ? -15000 : 30000);
    } else if (name == QLatin1String("toggle") && args.size() >= 2 && barIdOk(args.at(1))) {
        cmd.insert(QStringLiteral("cmd"), QStringLiteral("toggle"));
        cmd.insert(QStringLiteral("id"), args.at(1));
    } else if (name == QLatin1String("open")) {
        cmd.insert(QStringLiteral("cmd"), QStringLiteral("open"));
        if (args.size() >= 2)
            cmd.insert(QStringLiteral("page"), args.at(1));
        if (args.size() >= 3 && barIdOk(args.at(2)))
            cmd.insert(QStringLiteral("id"), args.at(2));
    } else if (name == QLatin1String("add")) {
        const QString text = barTaskText(args.mid(1).join(QLatin1Char(' ')));
        if (text.isEmpty()) {
            std::fprintf(stderr, "Usage: builder-launcher bar add TEXT\n");
            return 2;
        }
        cmd.insert(QStringLiteral("cmd"), QStringLiteral("add"));
        cmd.insert(QStringLiteral("text"), text);
    } else {
        std::fprintf(stderr, "Usage: builder-launcher bar [status|ping|play-pause|back|forward|toggle ID|open PAGE|add TEXT]\n");
        return 2;
    }

    QLocalSocket socket;
    socket.connectToServer(barSocketPath());
    if (!socket.waitForConnected(400)) {
        std::fprintf(stderr, "Builder Launcher is not running.\n");
        return 1;
    }
    const QByteArray line = QJsonDocument(cmd).toJson(QJsonDocument::Compact) + '\n';
    if (socket.write(line) != line.size() || !socket.waitForBytesWritten(400) || !socket.waitForReadyRead(400))
        return 1;
    const auto reply = QJsonDocument::fromJson(socket.readAll()).object();
    return reply.value(QStringLiteral("ok")).toBool() ? 0 : 1;
}

qint64 savedResumeMs(const QJsonObject &doc, const QString &episodeId) {
    const auto progress = doc.value(QStringLiteral("podcasts")).toObject().value(QStringLiteral("progress")).toArray();
    for (const auto &value : progress) {
        const auto row = value.toObject();
        if (row.value(QStringLiteral("episodeId")).toString() != episodeId)
            continue;
        const qint64 pos = qint64(row.value(QStringLiteral("positionMs")).toDouble());
        const qint64 dur = qint64(row.value(QStringLiteral("durationMs")).toDouble());
        if (row.value(QStringLiteral("finished")).toBool() || playbackFinished(pos, dur))
            return 0;
        return qMax<qint64>(0, pos);
    }
    return 0;
}

QJsonObject saveEpisodeProgress(QJsonObject doc, const QString &episodeId, qint64 positionMs, qint64 durationMs, qint64 now,
                                bool ended) {
    auto pods = doc.value(QStringLiteral("podcasts")).toObject();
    QJsonArray kept;
    QJsonObject prev;
    for (const auto &value : pods.value(QStringLiteral("progress")).toArray()) {
        const auto row = value.toObject();
        if (row.value(QStringLiteral("episodeId")).toString() == episodeId)
            prev = row;
        else
            kept.append(value);
    }
    const qint64 dur = durationMs > 0 ? durationMs : qint64(prev.value(QStringLiteral("durationMs")).toDouble());
    const qint64 pos = qMax<qint64>(0, positionMs);
    QJsonObject next;
    next.insert(QStringLiteral("episodeId"), episodeId);
    next.insert(QStringLiteral("positionMs"), double(pos));
    next.insert(QStringLiteral("durationMs"), double(dur));
    next.insert(QStringLiteral("lastPlayedAt"), double(now));
    next.insert(QStringLiteral("finished"), ended || playbackFinished(pos, dur));
    next.insert(QStringLiteral("skipped"), false);
    kept.append(next);
    pods.insert(QStringLiteral("progress"), kept);
    doc.insert(QStringLiteral("podcasts"), pods);
    return doc;
}

constexpr int kDefaultMinute = 8 * 60;
constexpr int kDayMinutes = 24 * 60;

namespace {

struct ClockHit {
    int minute = 0;
    int start = 0;
    int end = 0;
};

int ampmMinute(const QRegularExpressionMatch &match) {
    int hour = match.captured(1).toInt();
    const int minute = match.captured(2).isEmpty() ? 0 : match.captured(2).toInt();
    if (hour < 1 || hour > 12 || minute > 59)
        return -1;
    if (hour == 12)
        hour = 0;
    if (match.captured(3).startsWith(QLatin1Char('p'), Qt::CaseInsensitive))
        hour += 12;
    return hour * 60 + minute;
}

int compactMinute(const QString &raw) {
    const int hour = raw.size() == 3 ? raw.left(1).toInt() : raw.left(2).toInt();
    const int minute = raw.right(2).toInt();
    if (hour > 23 || minute > 59)
        return -1;
    return hour * 60 + minute;
}

void collect(QVector<ClockHit> &found, const QRegularExpression &re, const QString &text,
             const std::function<int(const QRegularExpressionMatch &)> &minuteOf) {
    auto it = re.globalMatch(text);
    while (it.hasNext()) {
        const auto match = it.next();
        const int minute = minuteOf(match);
        if (minute >= 0)
            found.append({minute, static_cast<int>(match.capturedStart()), static_cast<int>(match.capturedEnd())});
    }
}

QVector<ClockHit> clockHits(const QString &text) {
    QVector<ClockHit> found;
    static const QRegularExpression named(QStringLiteral("(?<![A-Za-z0-9])(noon|midday|midnight)\\b"),
                                           QRegularExpression::CaseInsensitiveOption);
    static const QRegularExpression ampm(QStringLiteral("(?<![A-Za-z0-9])(\\d{1,2})(?::([0-5]\\d))?\\s*(am|pm|a|p)\\b"),
                                          QRegularExpression::CaseInsensitiveOption);
    static const QRegularExpression colon(QStringLiteral("(?<![A-Za-z0-9])([01]?\\d|2[0-3]):([0-5]\\d)\\b"));
    static const QRegularExpression compact(QStringLiteral("(?<![A-Za-z0-9])(\\d{3,4})\\b"));
    collect(found, named, text, [](const QRegularExpressionMatch &match) {
        return match.captured(1).compare(QStringLiteral("midnight"), Qt::CaseInsensitive) == 0 ? 0 : 12 * 60;
    });
    collect(found, ampm, text, [](const QRegularExpressionMatch &match) { return ampmMinute(match); });
    collect(found, colon, text, [](const QRegularExpressionMatch &match) {
        const int hour = match.captured(1).toInt();
        const int minute = match.captured(2).toInt();
        if (hour > 23 || minute > 59)
            return -1;
        return hour * 60 + minute;
    });
    collect(found, compact, text, [](const QRegularExpressionMatch &match) { return compactMinute(match.captured(1)); });
    std::sort(found.begin(), found.end(), [](const ClockHit &a, const ClockHit &b) {
        if (a.start != b.start)
            return a.start < b.start;
        return a.end > b.end;
    });
    QVector<ClockHit> kept;
    for (const auto &hit : found) {
        bool overlap = false;
        for (const auto &prev : kept) {
            if (prev.start <= hit.end - 1 && hit.start <= prev.end - 1)
                overlap = true;
        }
        if (!overlap)
            kept.append(hit);
    }
    return kept;
}

QVector<int> clocksIn(const QString &text) {
    QVector<int> found;
    for (const auto &hit : clockHits(text))
        found.append(hit.minute);
    return found;
}

QString cleanHabitName(QString raw) {
    const auto hits = clockHits(raw);
    for (int i = hits.size() - 1; i >= 0; --i)
        raw.remove(hits.at(i).start, hits.at(i).end - hits.at(i).start);
    static const QRegularExpression filler(QStringLiteral("\\b(?:starting at|starts at|from|at|and|daily|every\\s+day|everyday)\\b|[,]+"),
                                            QRegularExpression::CaseInsensitiveOption);
    return raw.replace(filler, QStringLiteral(" ")).simplified();
}

qint64 dayStartMs(qint64 epoch) {
    const QDateTime local = QDateTime::fromMSecsSinceEpoch(epoch);
    return QDateTime(local.date(), QTime(0, 0), local.timeZone()).toMSecsSinceEpoch();
}

qint64 plusDaysMs(qint64 start, int days) {
    return QDateTime::fromMSecsSinceEpoch(start).addDays(days).toMSecsSinceEpoch();
}

QVector<int> intervalMinutes(const QJsonObject &habit) {
    const int step = habit.value(QStringLiteral("everyMinutes")).toInt();
    if (step <= 0)
        return {};
    const auto times = habit.value(QStringLiteral("times")).toArray();
    const int anchor = times.isEmpty() ? kDefaultMinute : times.first().toInt();
    const int phase = ((anchor % step) + step) % step;
    QVector<int> minutes;
    for (int minute = phase; minute < kDayMinutes; minute += step)
        minutes.append(minute);
    return minutes;
}

QVector<qint64> slotsOnDay(const QJsonObject &habit, qint64 start) {
    if (habit.value(QStringLiteral("enabled")).isBool() && !habit.value(QStringLiteral("enabled")).toBool())
        return {};
    const qint64 end = plusDaysMs(start, 1);
    const qint64 created = static_cast<qint64>(habit.value(QStringLiteral("createdAt")).toDouble());
    if (created >= end)
        return {};
    const QString kind = habit.value(QStringLiteral("kind")).toString();
    if (kind == QLatin1String("daily"))
        return {start};
    QVector<int> minutes;
    if (kind == QLatin1String("interval"))
        minutes = intervalMinutes(habit);
    else {
        for (const auto &value : habit.value(QStringLiteral("times")).toArray())
            minutes.append(value.toInt());
    }
    // Named daySlots because Qt turns the word slots into a macro.
    QVector<qint64> daySlots;
    for (int minute : minutes) {
        const qint64 slot = start + static_cast<qint64>(minute) * 60'000;
        if (slot >= created && slot < end)
            daySlots.append(slot);
    }
    return daySlots;
}

} // namespace

QJsonObject parseHabit(const QString &raw) {
    const QString text = raw.simplified();
    if (text.isEmpty())
        return {};
    static const QRegularExpression interval(QStringLiteral("\\bevery\\s+(\\d+)\\s*(hours?|hrs?|h|minutes?|mins?|m)\\b"),
                                              QRegularExpression::CaseInsensitiveOption);
    const auto hit = interval.match(text);
    if (hit.hasMatch()) {
        const int count = hit.captured(1).toInt();
        const int minutes = hit.captured(2).toLower().startsWith('h') ? count * 60 : count;
        if (minutes <= 0 || minutes > kDayMinutes || kDayMinutes % minutes != 0)
            return {};
        const auto after = clocksIn(text.mid(hit.capturedEnd()));
        const QString name = cleanHabitName(text.left(hit.capturedStart()) + text.mid(hit.capturedEnd()));
        if (name.isEmpty())
            return {};
        QJsonObject draft;
        draft.insert(QStringLiteral("name"), name);
        draft.insert(QStringLiteral("kind"), QStringLiteral("interval"));
        draft.insert(QStringLiteral("everyMinutes"), minutes);
        draft.insert(QStringLiteral("times"), QJsonArray{after.isEmpty() ? kDefaultMinute : after.first()});
        return draft;
    }
    const auto times = clocksIn(text);
    if (times.size() >= 2) {
        const QString name = cleanHabitName(text);
        if (name.isEmpty())
            return {};
        QJsonArray sorted;
        QVector<int> unique = times;
        std::sort(unique.begin(), unique.end());
        unique.erase(std::unique(unique.begin(), unique.end()), unique.end());
        for (int minute : unique)
            sorted.append(minute);
        QJsonObject draft;
        draft.insert(QStringLiteral("name"), name);
        draft.insert(QStringLiteral("kind"), QStringLiteral("times"));
        draft.insert(QStringLiteral("everyMinutes"), 0);
        draft.insert(QStringLiteral("times"), sorted);
        return draft;
    }
    if (text.contains(QRegularExpression(QStringLiteral("\\b(?:daily|every\\s+day|everyday)\\b"), QRegularExpression::CaseInsensitiveOption)) ||
        times.size() == 1) {
        const QString name = cleanHabitName(text);
        if (name.isEmpty())
            return {};
        QJsonObject draft;
        draft.insert(QStringLiteral("name"), name);
        draft.insert(QStringLiteral("kind"), QStringLiteral("daily"));
        draft.insert(QStringLiteral("everyMinutes"), 0);
        draft.insert(QStringLiteral("times"), QJsonArray{times.isEmpty() ? kDefaultMinute : times.first()});
        return draft;
    }
    return {};
}

QString habitSchedule(const QJsonObject &habit) {
    const QString kind = habit.value(QStringLiteral("kind")).toString();
    if (kind == QLatin1String("daily")) {
        const auto times = habit.value(QStringLiteral("times")).toArray();
        const int minute = times.isEmpty() ? kDefaultMinute : times.first().toInt();
        return minute == kDefaultMinute ? QStringLiteral("daily") : QStringLiteral("daily at %1").arg(formatAlarmTime(minute / 60, minute % 60).toLower());
    }
    if (kind == QLatin1String("interval")) {
        const int every = habit.value(QStringLiteral("everyMinutes")).toInt();
        return every % 60 == 0 ? QStringLiteral("every %1 hours").arg(every / 60) : QStringLiteral("every %1 minutes").arg(every);
    }
    QStringList labels;
    for (const auto &value : habit.value(QStringLiteral("times")).toArray()) {
        const int minute = value.toInt();
        labels.append(formatAlarmTime(minute / 60, minute % 60).toLower());
    }
    return labels.join(QStringLiteral(" and "));
}

qint64 openHabitSlot(const QJsonObject &habit, qint64 now) {
    const auto daySlots = slotsOnDay(habit, dayStartMs(now));
    if (daySlots.isEmpty())
        return 0;
    if (habit.value(QStringLiteral("kind")).toString() == QLatin1String("daily"))
        return daySlots.first();
    qint64 current = 0;
    for (qint64 slot : daySlots) {
        if (slot <= now)
            current = slot;
    }
    return current != 0 ? current : daySlots.first();
}

bool habitDone(const QJsonObject &doc, const QString &habitId, qint64 slot) {
    for (const auto &value : doc.value(QStringLiteral("habitMarks")).toArray()) {
        const auto mark = value.toObject();
        if (mark.value(QStringLiteral("habitId")).toString() == habitId &&
            static_cast<qint64>(mark.value(QStringLiteral("slotAt")).toDouble()) == slot &&
            mark.value(QStringLiteral("done")).toBool())
            return true;
    }
    return false;
}

QString habitMinuteLabel(int minute) {
    const int h24 = minute / 60;
    const int m = minute % 60;
    const QString ap = h24 < 12 ? QStringLiteral("am") : QStringLiteral("pm");
    const int h = h24 % 12 == 0 ? 12 : h24 % 12;
    return m == 0 ? QStringLiteral("%1%2").arg(h).arg(ap)
                  : QStringLiteral("%1:%2%3").arg(h).arg(m, 2, 10, QLatin1Char('0')).arg(ap);
}

QString habitPhrase(const QJsonObject &habit) {
    const QString name = habit.value(QStringLiteral("name")).toString();
    const QString kind = habit.value(QStringLiteral("kind")).toString();
    const auto times = habit.value(QStringLiteral("times")).toArray();
    const int minute = times.isEmpty() ? 8 * 60 : times.first().toInt();
    if (kind == QLatin1String("daily"))
        return minute == 8 * 60 ? QStringLiteral("%1 daily").arg(name)
                                : QStringLiteral("%1 daily at %2").arg(name, habitMinuteLabel(minute));
    if (kind == QLatin1String("interval")) {
        const int every = habit.value(QStringLiteral("everyMinutes")).toInt();
        const QString gap = every % 60 == 0 ? QStringLiteral("every %1 hours").arg(every / 60)
                                             : QStringLiteral("every %1 minutes").arg(every);
        return minute == 8 * 60 ? QStringLiteral("%1 %2").arg(name, gap)
                                : QStringLiteral("%1 %2 starting at %3").arg(name, gap, habitMinuteLabel(minute));
    }
    QStringList labels;
    for (const auto &value : times)
        labels.append(habitMinuteLabel(value.toInt()));
    return QStringLiteral("%1 %2").arg(name, labels.join(QStringLiteral(" and ")));
}

QJsonObject updateHabit(QJsonObject doc, const QString &id, const QJsonObject &draft, qint64 now) {
    if (id.isEmpty() || draft.isEmpty())
        return doc;
    QJsonArray habits;
    bool found = false;
    for (const auto &value : doc.value(QStringLiteral("habits")).toArray()) {
        auto habit = value.toObject();
        if (habit.value(QStringLiteral("id")).toString() == id) {
            found = true;
            habit.insert(QStringLiteral("name"), draft.value(QStringLiteral("name")));
            habit.insert(QStringLiteral("kind"), draft.value(QStringLiteral("kind")));
            habit.insert(QStringLiteral("everyMinutes"), draft.value(QStringLiteral("everyMinutes")));
            habit.insert(QStringLiteral("times"), draft.value(QStringLiteral("times")));
            habit.insert(QStringLiteral("updatedAt"), now);
        }
        habits.append(habit);
    }
    if (!found)
        return doc;
    doc.insert(QStringLiteral("habits"), habits);
    return touchExported(doc, now);
}

QJsonObject addHabit(QJsonObject doc, const QJsonObject &draft, qint64 now) {
    if (draft.isEmpty())
        return doc;
    QJsonObject habit = draft;
    habit.insert(QStringLiteral("id"), newId(now));
    habit.insert(QStringLiteral("createdAt"), now);
    habit.insert(QStringLiteral("updatedAt"), now);
    habit.insert(QStringLiteral("enabled"), true);
    QJsonArray habits;
    habits.append(habit);
    for (const auto &value : doc.value(QStringLiteral("habits")).toArray())
        habits.append(value);
    doc.insert(QStringLiteral("habits"), habits);
    QJsonArray deleted;
    for (const auto &value : doc.value(QStringLiteral("deletedHabitIds")).toArray()) {
        if (value.toString() != habit.value(QStringLiteral("id")).toString())
            deleted.append(value);
    }
    doc.insert(QStringLiteral("deletedHabitIds"), deleted);
    return touchExported(doc, now);
}

QJsonObject toggleHabit(QJsonObject doc, const QString &id, qint64 slot, qint64 now) {
    if (id.isEmpty() || slot == 0)
        return doc;
    const QString markId = id + QLatin1Char(':') + QString::number(slot);
    bool done = false;
    QJsonArray marks;
    for (const auto &value : doc.value(QStringLiteral("habitMarks")).toArray()) {
        const auto mark = value.toObject();
        if (mark.value(QStringLiteral("id")).toString() == markId) {
            done = mark.value(QStringLiteral("done")).toBool();
            continue;
        }
        marks.append(mark);
    }
    QJsonObject next;
    next.insert(QStringLiteral("id"), markId);
    next.insert(QStringLiteral("habitId"), id);
    next.insert(QStringLiteral("slotAt"), slot);
    next.insert(QStringLiteral("done"), !done);
    next.insert(QStringLiteral("updatedAt"), now);
    marks.append(next);
    doc.insert(QStringLiteral("habitMarks"), marks);
    return touchExported(doc, now);
}

QJsonObject deleteHabit(QJsonObject doc, const QString &id, qint64 now) {
    QJsonArray habits;
    for (const auto &value : doc.value(QStringLiteral("habits")).toArray()) {
        if (value.toObject().value(QStringLiteral("id")).toString() != id)
            habits.append(value);
    }
    QJsonArray marks;
    for (const auto &value : doc.value(QStringLiteral("habitMarks")).toArray()) {
        if (value.toObject().value(QStringLiteral("habitId")).toString() != id)
            marks.append(value);
    }
    QJsonArray deleted = doc.value(QStringLiteral("deletedHabitIds")).toArray();
    deleted.append(id);
    doc.insert(QStringLiteral("habits"), habits);
    doc.insert(QStringLiteral("habitMarks"), marks);
    doc.insert(QStringLiteral("deletedHabitIds"), deleted);
    return touchExported(doc, now);
}

QString habitDayLabel(const QJsonObject &doc, qint64 now) {
    const qint64 start = dayStartMs(now);
    int due = 0;
    int completed = 0;
    for (const auto &value : doc.value(QStringLiteral("habits")).toArray()) {
        const auto habit = value.toObject();
        for (qint64 slot : slotsOnDay(habit, start)) {
            due += 1;
            if (habitDone(doc, habit.value(QStringLiteral("id")).toString(), slot))
                completed += 1;
        }
    }
    const int percent = due == 0 ? 0 : (completed * 100) / due;
    return QStringLiteral("%1 of %2 · %3%").arg(completed).arg(due).arg(percent);
}

QJsonObject dueHabit(const QJsonObject &doc, qint64 now, const QSet<QString> &reminded) {
    for (const auto &value : doc.value(QStringLiteral("habits")).toArray()) {
        const auto habit = value.toObject();
        const qint64 slot = openHabitSlot(habit, now);
        if (slot == 0)
            continue;
        const QString id = habit.value(QStringLiteral("id")).toString();
        if (habitDone(doc, id, slot))
            continue;
        qint64 at = slot;
        if (habit.value(QStringLiteral("kind")).toString() == QLatin1String("daily")) {
            const auto times = habit.value(QStringLiteral("times")).toArray();
            const int minute = times.isEmpty() ? kDefaultMinute : times.first().toInt();
            at = dayStartMs(slot) + static_cast<qint64>(minute) * 60'000;
        }
        if (now < at)
            continue;
        const QString key = id + QLatin1Char(':') + QString::number(slot);
        if (reminded.contains(key))
            continue;
        QJsonObject due;
        due.insert(QStringLiteral("id"), id);
        due.insert(QStringLiteral("slot"), slot);
        due.insert(QStringLiteral("name"), habit.value(QStringLiteral("name")));
        due.insert(QStringLiteral("key"), key);
        due.insert(QStringLiteral("body"), habitSchedule(habit));
        return due;
    }
    return {};
}
