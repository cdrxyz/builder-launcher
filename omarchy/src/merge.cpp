#include "merge.h"

#include <QHash>
#include <QJsonArray>
#include <QSet>
#include <QVector>
#include <algorithm>
#include <cmath>

namespace {

QJsonArray concat(const QJsonArray &a, const QJsonArray &b) {
    QJsonArray out = a;
    for (const auto &value : b)
        out.append(value);
    return out;
}

QJsonObject obj(const QJsonValue &value) {
    return value.isObject() ? value.toObject() : QJsonObject();
}

QJsonArray arr(const QJsonValue &value) {
    return value.isArray() ? value.toArray() : QJsonArray();
}

// JavaScript `value || fallback`: missing, null, false, "", and 0 all fall through.
double orNum(const QJsonValue &value, double fallback = 0) {
    if (!value.isDouble())
        return fallback;
    const double number = value.toDouble();
    return number == 0 ? fallback : number;
}

double orNum(const QJsonObject &object, const char *key, double fallback = 0) {
    return orNum(object.value(key), fallback);
}

QString str(const QJsonValue &value) {
    return value.isString() ? value.toString() : QString();
}

bool isFalse(const QJsonValue &value) {
    return value.isBool() && !value.toBool();
}

bool isTrue(const QJsonValue &value) {
    return value.isBool() && value.toBool();
}

QString idOf(const QJsonObject &object, const char *key = "id") {
    const QJsonValue value = object.value(key);
    if (value.isString())
        return value.toString();
    if (value.isDouble())
        return QString::number(value.toDouble(), 'f', 0);
    return {};
}

double stamp(const QJsonObject &item) {
    return std::max({orNum(item, "updatedAt"), orNum(item, "completedAt"), orNum(item, "createdAt"),
                     orNum(item, "lastPlayedAt")});
}

QHash<QString, QJsonObject> byId(const QJsonArray &list, const char *key = "id") {
    QHash<QString, QJsonObject> map;
    QStringList order;
    for (const auto &value : list) {
        if (!value.isObject())
            continue;
        const auto item = value.toObject();
        const QString id = idOf(item, key);
        if (id.isEmpty() || map.contains(id))
            continue;
        map.insert(id, item);
        order.append(id);
    }
    map.insert(QStringLiteral("\x1eorder"), QJsonObject());
    // Order is kept separately so a hash cannot reshuffle the union.
    Q_UNUSED(order);
    return map;
}

QStringList idOrder(const QJsonArray &list, const char *key = "id") {
    QStringList order;
    QSet<QString> seen;
    for (const auto &value : list) {
        if (!value.isObject())
            continue;
        const QString id = idOf(value.toObject(), key);
        if (id.isEmpty() || seen.contains(id))
            continue;
        seen.insert(id);
        order.append(id);
    }
    return order;
}

QStringList unionOrder(const QStringList &left, const QStringList &right) {
    QStringList order = left;
    QSet<QString> seen(left.begin(), left.end());
    for (const auto &id : right) {
        if (seen.contains(id))
            continue;
        seen.insert(id);
        order.append(id);
    }
    return order;
}

QJsonObject pickOrder(const QJsonObject &x, const QJsonObject &y, const QJsonObject &content) {
    const double ax = orNum(x, "orderedAt");
    const double ay = orNum(y, "orderedAt");
    if (ax != ay)
        return ax > ay ? x : y;
    const double ox = orNum(x, "order");
    const double oy = orNum(y, "order");
    if (ox != 0 && oy == 0)
        return x;
    if (oy != 0 && ox == 0)
        return y;
    return content;
}

QJsonObject mergeItem(const QJsonObject &x, const QJsonObject &y, bool hasX, bool hasY) {
    if (!hasX)
        return y;
    if (!hasY)
        return x;
    const QJsonObject content = stamp(x) >= stamp(y) ? x : y;
    const QJsonObject orderSource = pickOrder(x, y, content);
    if (orderSource == content)
        return content;
    QJsonObject out = content;
    out.insert(QStringLiteral("order"), orNum(orderSource, "order"));
    out.insert(QStringLiteral("orderedAt"), orNum(orderSource, "orderedAt"));
    return out;
}

QJsonArray mergeItemList(const QJsonArray &a, const QJsonArray &b) {
    const auto left = byId(a);
    const auto right = byId(b);
    QJsonArray out;
    for (const auto &id : unionOrder(idOrder(a), idOrder(b))) {
        out.append(mergeItem(left.value(id), right.value(id), left.contains(id), right.contains(id)));
    }
    return out;
}

bool isTodo(const QJsonObject &item) {
    return item.value(QStringLiteral("kind")).toString().compare(QLatin1String("todo"), Qt::CaseInsensitive) == 0;
}

bool isDone(const QJsonObject &item) {
    return !item.value(QStringLiteral("completedAt")).isUndefined() && !item.value(QStringLiteral("completedAt")).isNull();
}

bool isOpenTodo(const QJsonObject &item) {
    return isTodo(item) && !isDone(item);
}

double taskRank(const QJsonObject &item) {
    const double order = orNum(item, "order");
    return order != 0 ? order : orNum(item, "createdAt");
}

QJsonArray alignOpenOrder(const QJsonArray &leftList, const QJsonArray &rightList, const QJsonArray &merged) {
    QSet<QString> leftIds;
    QSet<QString> rightIds;
    for (const auto &id : idOrder(leftList))
        leftIds.insert(id);
    for (const auto &id : idOrder(rightList))
        rightIds.insert(id);

    QVector<QJsonObject> open;
    QVector<QJsonObject> rest;
    for (const auto &value : merged) {
        const auto item = value.toObject();
        if (isOpenTodo(item))
            open.append(item);
        else
            rest.append(item);
    }

    QVector<QJsonObject> shared;
    QVector<QJsonObject> unseen;
    for (const auto &item : open) {
        const QString id = idOf(item);
        const bool onLeft = leftIds.contains(id);
        const bool onRight = rightIds.contains(id);
        if (onLeft && onRight)
            shared.append(item);
        else if (onLeft != onRight)
            unseen.append(item);
    }

    QHash<QString, double> updates;
    if (!shared.isEmpty() && !unseen.isEmpty()) {
        double maxShared = taskRank(shared.first());
        for (const auto &item : shared)
            maxShared = std::max(maxShared, taskRank(item));
        QVector<QJsonObject> need;
        for (const auto &item : unseen) {
            if (!(orNum(item, "orderedAt") > 0) && taskRank(item) <= maxShared)
                need.append(item);
        }
        std::stable_sort(need.begin(), need.end(), [](const QJsonObject &a, const QJsonObject &b) {
            return orNum(a, "createdAt") < orNum(b, "createdAt");
        });
        for (int index = 0; index < need.size(); ++index)
            updates.insert(idOf(need.at(index)), maxShared + 1 + index);
    }

    QVector<QJsonObject> lifted;
    lifted.reserve(merged.size());
    for (const auto &value : merged) {
        auto item = value.toObject();
        const auto found = updates.constFind(idOf(item));
        if (found != updates.constEnd())
            item.insert(QStringLiteral("order"), found.value());
        lifted.append(item);
    }

    QVector<QJsonObject> openLifted;
    QVector<QJsonObject> restLifted;
    for (const auto &item : lifted) {
        if (isOpenTodo(item))
            openLifted.append(item);
        else
            restLifted.append(item);
    }
    std::stable_sort(openLifted.begin(), openLifted.end(), [](const QJsonObject &a, const QJsonObject &b) {
        return taskRank(a) > taskRank(b);
    });

    QJsonArray out;
    for (const auto &item : openLifted)
        out.append(item);
    for (const auto &item : restLifted)
        out.append(item);
    return out;
}

QJsonObject mergeAlarm(const QJsonObject &x, const QJsonObject &y) {
    const double last = std::max(orNum(x, "lastFiredAt"), orNum(y, "lastFiredAt"));
    const double sx = orNum(x, "updatedAt");
    const double sy = orNum(y, "updatedAt");
    QJsonObject base = x;
    if (sy > sx)
        base = y;
    else if (sx > sy)
        base = x;
    else if (isFalse(x.value(QStringLiteral("enabled"))) && !isFalse(y.value(QStringLiteral("enabled"))))
        base = x;
    else if (isFalse(y.value(QStringLiteral("enabled"))) && !isFalse(x.value(QStringLiteral("enabled"))))
        base = y;
    const double snooze = isFalse(base.value(QStringLiteral("enabled")))
                              ? 0
                              : sx != sy ? orNum(base, "snoozeUntil")
                                         : std::max(orNum(x, "snoozeUntil"), orNum(y, "snoozeUntil"));
    QJsonObject out = base;
    out.insert(QStringLiteral("lastFiredAt"), last == 0 ? QJsonValue(QJsonValue::Null) : QJsonValue(last));
    out.insert(QStringLiteral("snoozeUntil"), snooze > last ? QJsonValue(snooze) : QJsonValue(QJsonValue::Null));
    return out;
}

QJsonArray mergeAlarms(const QJsonArray &a, const QJsonArray &b) {
    const auto left = byId(a);
    const auto right = byId(b);
    QJsonArray out;
    for (const auto &id : unionOrder(idOrder(a), idOrder(b))) {
        if (!left.contains(id))
            out.append(right.value(id));
        else if (!right.contains(id))
            out.append(left.value(id));
        else
            out.append(mergeAlarm(left.value(id), right.value(id)));
    }
    return out;
}

QJsonArray mergeById(const QJsonArray &a, const QJsonArray &b) {
    const auto left = byId(a);
    const auto right = byId(b);
    QJsonArray out;
    for (const auto &id : unionOrder(idOrder(a), idOrder(b))) {
        if (!left.contains(id))
            out.append(right.value(id));
        else if (!right.contains(id))
            out.append(left.value(id));
        else
            out.append(stamp(left.value(id)) >= stamp(right.value(id)) ? left.value(id) : right.value(id));
    }
    return out;
}

QJsonArray mergeWatch(const QJsonArray &a, const QJsonArray &b, bool preferA) {
    QJsonArray out;
    QSet<QString> seen;
    const QJsonArray lists[2] = {preferA ? a : b, preferA ? b : a};
    for (const auto &list : lists) {
        for (const auto &value : list) {
            if (!value.isObject())
                continue;
            const auto item = value.toObject();
            const QString symbol = item.value(QStringLiteral("symbol")).toString().toUpper();
            if (symbol.isEmpty() || seen.contains(symbol))
                continue;
            seen.insert(symbol);
            out.append(item);
        }
    }
    return out;
}

QJsonArray mergeShows(const QJsonArray &a, const QJsonArray &b) {
    QStringList order;
    QHash<QString, QJsonObject> map;
    for (const auto &value : concat(a, b)) {
        if (!value.isObject())
            continue;
        const auto show = value.toObject();
        const QString key = show.value(QStringLiteral("feedUrl")).toString().toLower();
        if (key.isEmpty())
            continue;
        const bool known = map.contains(key);
        const auto prev = map.value(key);
        if (!known || orNum(show, "subscribedAt") >= orNum(prev, "subscribedAt"))
            map.insert(key, show);
        if (!known)
            order.append(key);
    }
    QJsonArray out;
    for (const auto &key : order)
        out.append(map.value(key));
    return out;
}

QJsonObject mergeProgressRow(const QJsonObject &prev, const QJsonObject &row) {
    const double nextAt = orNum(row, "lastPlayedAt");
    const double prevAt = orNum(prev, "lastPlayedAt");
    QJsonObject chosen = prev;
    if (nextAt > prevAt)
        chosen = row;
    else if (nextAt < prevAt)
        chosen = prev;
    else if (isTrue(row.value(QStringLiteral("skipped"))) != isTrue(prev.value(QStringLiteral("skipped"))))
        chosen = isTrue(row.value(QStringLiteral("skipped"))) ? row : prev;
    else
        chosen = row;
    const double savedAt = std::max({orNum(chosen, "savedAt"), orNum(prev, "savedAt"), orNum(row, "savedAt")});
    if (savedAt == orNum(chosen, "savedAt"))
        return chosen;
    QJsonObject out = chosen;
    out.insert(QStringLiteral("savedAt"), savedAt);
    return out;
}

QJsonArray mergeProgress(const QJsonArray &a, const QJsonArray &b) {
    QStringList order;
    QHash<QString, QJsonObject> map;
    for (const auto &value : concat(a, b)) {
        if (!value.isObject())
            continue;
        const auto row = value.toObject();
        const QString id = idOf(row, "episodeId");
        if (id.isEmpty())
            continue;
        if (!map.contains(id)) {
            map.insert(id, row);
            order.append(id);
            continue;
        }
        map.insert(id, mergeProgressRow(map.value(id), row));
    }
    QJsonArray out;
    for (const auto &id : order)
        out.append(map.value(id));
    return out;
}

QJsonObject mergeEpisode(const QJsonObject &x, const QJsonObject &y, bool hasX, bool hasY) {
    if (!hasX)
        return y;
    if (!hasY)
        return x;
    const bool xNewer = orNum(x, "pubDate") >= orNum(y, "pubDate");
    const QJsonObject newer = xNewer ? x : y;
    const QJsonObject older = xNewer ? y : x;
    QJsonObject out = older;
    for (auto it = newer.begin(); it != newer.end(); ++it)
        out.insert(it.key(), it.value());
    auto keep = [](const QJsonObject &first, const QJsonObject &second, const char *key) {
        const QString value = first.value(QLatin1String(key)).toString();
        return value.isEmpty() ? second.value(QLatin1String(key)).toString() : value;
    };
    out.insert(QStringLiteral("description"), keep(newer, older, "description"));
    out.insert(QStringLiteral("title"), keep(newer, older, "title"));
    out.insert(QStringLiteral("enclosureUrl"), keep(newer, older, "enclosureUrl"));
    out.insert(QStringLiteral("durationMs"), std::max(orNum(newer, "durationMs"), orNum(older, "durationMs")));
    return out;
}

QJsonArray mergeEpisodes(const QJsonArray &a, const QJsonArray &b) {
    const auto left = byId(a);
    const auto right = byId(b);
    QJsonArray out;
    for (const auto &id : unionOrder(idOrder(a), idOrder(b)))
        out.append(mergeEpisode(left.value(id), right.value(id), left.contains(id), right.contains(id)));
    return out;
}

QJsonObject mergePods(const QJsonValue &a, const QJsonValue &b) {
    const auto left = obj(a);
    const auto right = obj(b);
    QJsonObject out;
    out.insert(QStringLiteral("shows"), mergeShows(arr(left.value(QStringLiteral("shows"))), arr(right.value(QStringLiteral("shows")))));
    out.insert(QStringLiteral("episodes"),
               mergeEpisodes(arr(left.value(QStringLiteral("episodes"))), arr(right.value(QStringLiteral("episodes")))));
    out.insert(QStringLiteral("progress"),
               mergeProgress(arr(left.value(QStringLiteral("progress"))), arr(right.value(QStringLiteral("progress")))));
    out.insert(QStringLiteral("cacheBytes"), std::max(orNum(left, "cacheBytes"), orNum(right, "cacheBytes")));
    return out;
}

QJsonObject mergeArticle(const QJsonObject &prev, const QJsonObject &item, bool hasPrev) {
    if (!hasPrev)
        return item;
    const bool itemNewer = orNum(item, "addedAt") >= orNum(prev, "addedAt");
    const QJsonObject newer = itemNewer ? item : prev;
    const QJsonObject older = itemNewer ? prev : item;
    const QString itemText = item.value(QStringLiteral("text")).toString();
    const QString prevText = prev.value(QStringLiteral("text")).toString();
    const QString text = itemText.size() >= prevText.size() ? itemText : prevText;
    QJsonObject out = newer;
    auto keep = [](const QJsonObject &first, const QJsonObject &second, const char *key) {
        const QString value = first.value(QLatin1String(key)).toString();
        return value.isEmpty() ? second.value(QLatin1String(key)).toString() : value;
    };
    out.insert(QStringLiteral("title"), keep(newer, older, "title"));
    out.insert(QStringLiteral("publication"), keep(newer, older, "publication"));
    out.insert(QStringLiteral("text"), text.isEmpty() ? older.value(QStringLiteral("text")).toString() : text);
    return out;
}

QJsonObject mergeReadProgress(const QJsonObject &prev, const QJsonObject &row, bool hasPrev) {
    if (!hasPrev)
        return row;
    const double nextAt = orNum(row, "updatedAt");
    const double prevAt = orNum(prev, "updatedAt");
    if (nextAt > prevAt)
        return row;
    if (nextAt < prevAt)
        return prev;
    return orNum(row, "positionMs") >= orNum(prev, "positionMs") ? row : prev;
}

QStringList restoredIds(const QJsonObject &doc) {
    const auto bag = obj(doc.value(QStringLiteral("reads")));
    QSet<QString> deleted;
    for (const auto &value : arr(bag.value(QStringLiteral("deletedIds"))))
        deleted.insert(value.toString());
    QStringList ids;
    for (const auto &value : arr(bag.value(QStringLiteral("articles")))) {
        const QString id = idOf(value.toObject());
        if (!id.isEmpty() && !deleted.contains(id))
            ids.append(id);
    }
    return ids;
}

QJsonObject mergeReads(const QJsonValue &a, const QJsonValue &b, const QSet<QString> &restored) {
    const auto left = obj(a);
    const auto right = obj(b);
    QStringList deletedIds;
    QSet<QString> seenDelete;
    for (const auto &value : concat(arr(left.value(QStringLiteral("deletedIds"))), arr(right.value(QStringLiteral("deletedIds"))))) {
        const QString id = value.isString() ? value.toString() : value.toVariant().toString();
        if (id.isEmpty() || seenDelete.contains(id) || restored.contains(id))
            continue;
        seenDelete.insert(id);
        deletedIds.append(id);
    }
    QStringList articleOrder;
    QHash<QString, QJsonObject> articles;
    for (const auto &value : concat(arr(left.value(QStringLiteral("articles"))), arr(right.value(QStringLiteral("articles"))))) {
        if (!value.isObject())
            continue;
        const auto item = value.toObject();
        const QString id = idOf(item);
        if (id.isEmpty() || seenDelete.contains(id))
            continue;
        const bool known = articles.contains(id);
        articles.insert(id, mergeArticle(articles.value(id), item, known));
        if (!known)
            articleOrder.append(id);
    }
    std::stable_sort(articleOrder.begin(), articleOrder.end(), [&](const QString &x, const QString &y) {
        return orNum(articles.value(x), "addedAt") > orNum(articles.value(y), "addedAt");
    });

    QStringList progressOrder;
    QHash<QString, QJsonObject> progress;
    for (const auto &value : concat(arr(left.value(QStringLiteral("progress"))), arr(right.value(QStringLiteral("progress"))))) {
        if (!value.isObject())
            continue;
        const auto row = value.toObject();
        const QString id = idOf(row, "articleId");
        if (id.isEmpty() || seenDelete.contains(id) || !articles.contains(id))
            continue;
        const bool known = progress.contains(id);
        progress.insert(id, mergeReadProgress(progress.value(id), row, known));
        if (!known)
            progressOrder.append(id);
    }

    QJsonArray articleList;
    for (const auto &id : articleOrder)
        articleList.append(articles.value(id));
    QJsonArray progressList;
    for (const auto &id : progressOrder)
        progressList.append(progress.value(id));
    QJsonArray deleted;
    for (const auto &id : deletedIds)
        deleted.append(id);
    QJsonObject out;
    out.insert(QStringLiteral("articles"), articleList);
    out.insert(QStringLiteral("progress"), progressList);
    out.insert(QStringLiteral("deletedIds"), deleted);
    return out;
}

QJsonObject mergeVideos(const QJsonValue &a, const QJsonValue &b, const QStringList &restoredList) {
    QSet<QString> restored(restoredList.begin(), restoredList.end());
    const auto left = obj(a);
    const auto right = obj(b);
    QStringList deletedIds;
    QSet<QString> seenDelete;
    for (const auto &value : concat(arr(left.value(QStringLiteral("deletedIds"))), arr(right.value(QStringLiteral("deletedIds"))))) {
        const QString id = value.toString();
        if (id.isEmpty() || seenDelete.contains(id) || restored.contains(id))
            continue;
        seenDelete.insert(id);
        deletedIds.append(id);
    }
    QStringList order;
    QHash<QString, QJsonObject> videos;
    for (const auto &value : concat(arr(left.value(QStringLiteral("videos"))), arr(right.value(QStringLiteral("videos"))))) {
        if (!value.isObject())
            continue;
        const auto item = value.toObject();
        const QString id = idOf(item);
        if (id.isEmpty() || seenDelete.contains(id))
            continue;
        const bool known = videos.contains(id);
        if (!known || orNum(item, "addedAt") >= orNum(videos.value(id), "addedAt"))
            videos.insert(id, item);
        if (!known)
            order.append(id);
    }
    std::stable_sort(order.begin(), order.end(), [&](const QString &x, const QString &y) {
        return orNum(videos.value(x), "addedAt") > orNum(videos.value(y), "addedAt");
    });
    QJsonArray list;
    for (const auto &id : order)
        list.append(videos.value(id));
    QJsonArray deleted;
    for (const auto &id : deletedIds)
        deleted.append(id);
    QJsonObject out;
    out.insert(QStringLiteral("videos"), list);
    out.insert(QStringLiteral("progress"), QJsonArray());
    out.insert(QStringLiteral("deletedIds"), deleted);
    return out;
}

QJsonObject pickPinChange(const QJsonObject &prev, const QJsonObject &change, bool hasPrev) {
    if (!hasPrev)
        return change;
    const double at = orNum(change, "at");
    const double prevAt = orNum(prev, "at");
    if (at > prevAt)
        return change;
    if (at < prevAt)
        return prev;
    if (isFalse(change.value(QStringLiteral("pinned"))))
        return change;
    if (isFalse(prev.value(QStringLiteral("pinned"))))
        return prev;
    return change;
}

struct PinMerge {
    QJsonArray pins;
    QJsonArray pinChanges;
};

PinMerge mergePins(const QJsonArray &aPins, const QJsonArray &bPins, const QJsonArray &aChanges, const QJsonArray &bChanges) {
    QStringList changeOrder;
    QHash<QString, QJsonObject> latest;
    for (const auto &value : concat(aChanges, bChanges)) {
        if (!value.isObject())
            continue;
        const auto change = value.toObject();
        const QString key = change.value(QStringLiteral("packageName")).toString();
        if (key.isEmpty())
            continue;
        const bool known = latest.contains(key);
        latest.insert(key, pickPinChange(latest.value(key), change, known));
        if (!known)
            changeOrder.append(key);
    }
    QJsonArray pins;
    QSet<QString> seen;
    for (const auto &value : concat(aPins, bPins)) {
        const QString key = value.toString();
        const auto change = latest.value(key);
        if (latest.contains(key) && isFalse(change.value(QStringLiteral("pinned"))))
            continue;
        if (key.isEmpty() || seen.contains(key))
            continue;
        seen.insert(key);
        pins.append(value);
    }
    for (const auto &key : changeOrder) {
        const auto change = latest.value(key);
        if (isTrue(change.value(QStringLiteral("pinned"))) && !seen.contains(key)) {
            seen.insert(key);
            pins.append(change.value(QStringLiteral("packageName")));
        }
    }
    QJsonArray changes;
    for (const auto &key : changeOrder)
        changes.append(latest.value(key));
    return {pins, changes};
}

QJsonArray uniqueValues(const QJsonArray &list) {
    QJsonArray out;
    QSet<QString> seen;
    for (const auto &value : list) {
        const QString key = value.toVariant().toString();
        if (seen.contains(key))
            continue;
        seen.insert(key);
        out.append(value);
    }
    return out;
}

QJsonObject mergeSettings(const QJsonValue &olderValue, const QJsonValue &newerValue) {
    const auto previous = obj(olderValue);
    const auto incoming = obj(newerValue);
    QJsonObject merged = previous;
    for (auto it = incoming.begin(); it != incoming.end(); ++it)
        merged.insert(it.key(), it.value());
    const QStringList keys = {QStringLiteral("apiKey"), QStringLiteral("oauthAccess"), QStringLiteral("oauthRefresh"),
                              QStringLiteral("oauthAccount"), QStringLiteral("oauthExpiresAtEpochMs")};
    for (const auto &key : keys) {
        const auto incomingValue = incoming.value(key);
        const bool blank = incomingValue.isUndefined() || incomingValue.isNull() ||
                           (incomingValue.isString() && incomingValue.toString().isEmpty());
        if (!blank)
            continue;
        const auto kept = previous.value(key);
        if (kept.isUndefined() || kept.isNull()) {
            if (incomingValue.isUndefined())
                merged.remove(key);
            else
                merged.insert(key, incomingValue);
        } else {
            merged.insert(key, kept);
        }
    }
    return merged;
}

QJsonArray slimWatch(const QJsonArray &list) {
    QJsonArray out;
    for (const auto &value : list) {
        if (!value.isObject())
            continue;
        const auto item = value.toObject();
        QJsonObject slim;
        slim.insert(QStringLiteral("symbol"), item.value(QStringLiteral("symbol")));
        const QString name = item.value(QStringLiteral("name")).toString();
        slim.insert(QStringLiteral("name"), name.isEmpty() ? item.value(QStringLiteral("symbol")) : QJsonValue(name));
        slim.insert(QStringLiteral("addedAt"), orNum(item, "addedAt"));
        slim.insert(QStringLiteral("exchange"), item.value(QStringLiteral("exchange")).toString());
        out.append(slim);
    }
    return out;
}

QStringList videoIds(const QJsonObject &doc) {
    QStringList ids;
    for (const auto &value : arr(obj(doc.value(QStringLiteral("videos"))).value(QStringLiteral("videos")))) {
        const QString id = idOf(value.toObject());
        if (!id.isEmpty())
            ids.append(id);
    }
    return ids;
}

} // namespace

double docNum(const QJsonObject &object, const char *key) {
    return orNum(object, key);
}

QJsonObject emptyDoc(qint64 now) {
    QJsonObject doc;
    doc.insert(QStringLiteral("version"), 1);
    doc.insert(QStringLiteral("exportedAt"), double(now));
    doc.insert(QStringLiteral("items"), QJsonArray());
    doc.insert(QStringLiteral("deletedIds"), QJsonArray());
    doc.insert(QStringLiteral("watchlist"), QJsonArray());
    QJsonObject pods;
    pods.insert(QStringLiteral("shows"), QJsonArray());
    pods.insert(QStringLiteral("episodes"), QJsonArray());
    pods.insert(QStringLiteral("progress"), QJsonArray());
    doc.insert(QStringLiteral("podcasts"), pods);
    QJsonObject reads;
    reads.insert(QStringLiteral("articles"), QJsonArray());
    reads.insert(QStringLiteral("progress"), QJsonArray());
    reads.insert(QStringLiteral("deletedIds"), QJsonArray());
    doc.insert(QStringLiteral("reads"), reads);
    QJsonObject videos;
    videos.insert(QStringLiteral("videos"), QJsonArray());
    videos.insert(QStringLiteral("deletedIds"), QJsonArray());
    doc.insert(QStringLiteral("videos"), videos);
    return doc;
}

QJsonObject slimDoc(const QJsonObject &doc) {
    if (doc.isEmpty())
        return doc;
    const auto pods = obj(doc.value(QStringLiteral("podcasts")));
    const auto reads = obj(doc.value(QStringLiteral("reads")));
    QJsonArray progress;
    for (const auto &value : arr(pods.value(QStringLiteral("progress")))) {
        if (!value.isObject())
            continue;
        const auto row = value.toObject();
        if (orNum(row, "lastPlayedAt") > 0 || orNum(row, "savedAt") > 0 || isTrue(row.value(QStringLiteral("skipped"))))
            progress.append(row);
    }
    QJsonObject slimPods;
    slimPods.insert(QStringLiteral("shows"), arr(pods.value(QStringLiteral("shows"))));
    slimPods.insert(QStringLiteral("episodes"), QJsonArray());
    slimPods.insert(QStringLiteral("progress"), progress);
    QJsonObject slimReads;
    slimReads.insert(QStringLiteral("articles"), arr(reads.value(QStringLiteral("articles"))));
    slimReads.insert(QStringLiteral("progress"), arr(reads.value(QStringLiteral("progress"))));
    slimReads.insert(QStringLiteral("deletedIds"), arr(reads.value(QStringLiteral("deletedIds"))));
    QJsonObject out = doc;
    out.insert(QStringLiteral("watchlist"), slimWatch(arr(doc.value(QStringLiteral("watchlist")))));
    out.insert(QStringLiteral("podcasts"), slimPods);
    out.insert(QStringLiteral("reads"), slimReads);
    const auto videos = doc.value(QStringLiteral("videos"));
    if (videos.isObject())
        out.insert(QStringLiteral("videos"), videos);
    else {
        QJsonObject emptyVideos;
        emptyVideos.insert(QStringLiteral("videos"), QJsonArray());
        emptyVideos.insert(QStringLiteral("deletedIds"), QJsonArray());
        out.insert(QStringLiteral("videos"), emptyVideos);
    }
    return out;
}

QJsonObject mergeHabit(const QJsonObject &a, const QJsonObject &b) {
    const double au = std::max(orNum(a, "updatedAt"), orNum(a, "createdAt"));
    const double bu = std::max(orNum(b, "updatedAt"), orNum(b, "createdAt"));
    if (au != bu)
        return au > bu ? a : b;
    const bool aOff = a.value(QStringLiteral("enabled")).isBool() && !a.value(QStringLiteral("enabled")).toBool();
    const bool bOff = b.value(QStringLiteral("enabled")).isBool() && !b.value(QStringLiteral("enabled")).toBool();
    if (aOff && !bOff)
        return a;
    if (bOff && !aOff)
        return b;
    return b;
}

QJsonArray mergeHabitList(const QJsonArray &a, const QJsonArray &b) {
    QHash<QString, QJsonObject> map;
    QStringList order;
    for (const auto &value : concat(a, b)) {
        if (!value.isObject())
            continue;
        const auto item = value.toObject();
        const QString id = idOf(item);
        if (!map.contains(id))
            order.append(id);
        const auto prev = map.value(id);
        map.insert(id, prev.isEmpty() ? item : mergeHabit(prev, item));
    }
    QJsonArray out;
    for (const auto &id : order)
        out.append(map.value(id));
    return out;
}

QJsonArray mergeMarkList(const QJsonArray &a, const QJsonArray &b) {
    QHash<QString, QJsonObject> map;
    QStringList order;
    for (const auto &value : concat(a, b)) {
        if (!value.isObject())
            continue;
        const auto item = value.toObject();
        const QString id = idOf(item);
        if (!map.contains(id))
            order.append(id);
        const auto prev = map.value(id);
        map.insert(id, prev.isEmpty() || orNum(item, "updatedAt") >= orNum(prev, "updatedAt") ? item : prev);
    }
    QJsonArray out;
    for (const auto &id : order)
        out.append(map.value(id));
    return out;
}

QJsonObject mergeDocs(const QJsonObject &a, const QJsonObject &b) {
    if (a.isEmpty())
        return b;
    if (b.isEmpty())
        return a;
    const bool preferA = orNum(a, "exportedAt") >= orNum(b, "exportedAt");
    const QJsonObject newer = preferA ? a : b;
    const QJsonObject older = preferA ? b : a;
    const QJsonArray deletedIds = uniqueValues(concat(arr(a.value(QStringLiteral("deletedIds"))), arr(b.value(QStringLiteral("deletedIds")))));
    QSet<QString> deleted;
    for (const auto &value : deletedIds)
        deleted.insert(value.toVariant().toString());
    QJsonArray mergedItems;
    for (const auto &value : mergeItemList(arr(a.value(QStringLiteral("items"))), arr(b.value(QStringLiteral("items"))))) {
        if (!deleted.contains(idOf(value.toObject())))
            mergedItems.append(value);
    }
    const QJsonArray items = alignOpenOrder(arr(a.value(QStringLiteral("items"))), arr(b.value(QStringLiteral("items"))), mergedItems);
    const QJsonArray deletedAlarmIds =
        uniqueValues(concat(arr(a.value(QStringLiteral("deletedAlarmIds"))), arr(b.value(QStringLiteral("deletedAlarmIds")))));
    QSet<QString> dropAlarms;
    for (const auto &value : deletedAlarmIds)
        dropAlarms.insert(value.toVariant().toString());
    const QJsonArray deletedHabitIds =
        uniqueValues(concat(arr(a.value(QStringLiteral("deletedHabitIds"))), arr(b.value(QStringLiteral("deletedHabitIds")))));
    QSet<QString> dropHabits;
    for (const auto &value : deletedHabitIds)
        dropHabits.insert(value.toVariant().toString());
    const auto pinMerge = mergePins(arr(a.value(QStringLiteral("pins"))), arr(b.value(QStringLiteral("pins"))),
                                    arr(a.value(QStringLiteral("pinChanges"))), arr(b.value(QStringLiteral("pinChanges"))));
    QJsonArray alarms;
    for (const auto &value : mergeAlarms(arr(a.value(QStringLiteral("alarms"))), arr(b.value(QStringLiteral("alarms"))))) {
        if (!dropAlarms.contains(idOf(value.toObject())))
            alarms.append(value);
    }
    QSet<QString> restored;
    for (const auto &id : restoredIds(newer))
        restored.insert(id);
    QJsonObject out = older;
    for (auto it = newer.begin(); it != newer.end(); ++it)
        out.insert(it.key(), it.value());
    out.insert(QStringLiteral("version"), std::max(orNum(a.value(QStringLiteral("version")), 1), orNum(b.value(QStringLiteral("version")), 1)));
    out.insert(QStringLiteral("exportedAt"), std::max(orNum(a, "exportedAt"), orNum(b, "exportedAt")));
    out.insert(QStringLiteral("items"), items);
    out.insert(QStringLiteral("deletedIds"), deletedIds);
    out.insert(QStringLiteral("chats"), mergeById(arr(a.value(QStringLiteral("chats"))), arr(b.value(QStringLiteral("chats")))));
    out.insert(QStringLiteral("pins"), pinMerge.pins);
    out.insert(QStringLiteral("pinChanges"), pinMerge.pinChanges);
    out.insert(QStringLiteral("watchlist"), mergeWatch(arr(a.value(QStringLiteral("watchlist"))), arr(b.value(QStringLiteral("watchlist"))), preferA));
    out.insert(QStringLiteral("podcasts"), mergePods(a.value(QStringLiteral("podcasts")), b.value(QStringLiteral("podcasts"))));
    out.insert(QStringLiteral("reads"), mergeReads(a.value(QStringLiteral("reads")), b.value(QStringLiteral("reads")), restored));
    out.insert(QStringLiteral("videos"), mergeVideos(a.value(QStringLiteral("videos")), b.value(QStringLiteral("videos")), videoIds(newer)));
    out.insert(QStringLiteral("alarms"), alarms);
    out.insert(QStringLiteral("zones"), mergeById(arr(a.value(QStringLiteral("zones"))), arr(b.value(QStringLiteral("zones")))));
    QJsonArray habits;
    for (const auto &value : mergeHabitList(arr(a.value(QStringLiteral("habits"))), arr(b.value(QStringLiteral("habits"))))) {
        if (!dropHabits.contains(idOf(value.toObject())))
            habits.append(value);
    }
    QJsonArray habitMarks;
    for (const auto &value : mergeMarkList(arr(a.value(QStringLiteral("habitMarks"))), arr(b.value(QStringLiteral("habitMarks"))))) {
        if (!dropHabits.contains(value.toObject().value(QStringLiteral("habitId")).toVariant().toString()))
            habitMarks.append(value);
    }
    out.insert(QStringLiteral("habits"), habits);
    out.insert(QStringLiteral("habitMarks"), habitMarks);
    out.insert(QStringLiteral("deletedHabitIds"), deletedHabitIds);
    out.insert(QStringLiteral("settings"), mergeSettings(older.value(QStringLiteral("settings")), newer.value(QStringLiteral("settings"))));
    out.insert(QStringLiteral("deletedAlarmIds"), deletedAlarmIds);
    return out;
}

namespace {

bool sameNumber(double left, double right) {
    return left == right || (std::isnan(left) && std::isnan(right));
}

} // namespace

bool jsonEqual(const QJsonValue &left, const QJsonValue &right) {
    if (left.isDouble() || right.isDouble()) {
        if (!(left.isDouble() && right.isDouble()))
            return false;
        return sameNumber(left.toDouble(), right.toDouble());
    }
    if (left.type() != right.type())
        return false;
    if (left.isArray()) {
        const auto a = left.toArray();
        const auto b = right.toArray();
        if (a.size() != b.size())
            return false;
        for (int i = 0; i < a.size(); ++i)
            if (!jsonEqual(a.at(i), b.at(i)))
                return false;
        return true;
    }
    if (left.isObject()) {
        const auto a = left.toObject();
        const auto b = right.toObject();
        if (a.size() != b.size())
            return false;
        for (auto it = a.begin(); it != a.end(); ++it) {
            if (!b.contains(it.key()) || !jsonEqual(it.value(), b.value(it.key())))
                return false;
        }
        return true;
    }
    return left == right;
}
