#include "library.h"

#include "merge.h"

#include <QDateTime>
#include <QRegularExpression>
#include <QTimeZone>
#include <QUrl>
#include <QUrlQuery>
#include <QtMath>
#include <algorithm>
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
