#pragma once

#include <QJsonArray>
#include <QJsonObject>
#include <QJsonValue>
#include <QString>

// Same rules as website/public/web/merge.js, items.js, reads.js, and videos.js.
// A drift here writes the wrong vault back. tests/fixtures/merge.json is the
// oracle, regenerated from those modules.

QJsonObject mergeDocs(const QJsonObject &a, const QJsonObject &b);
QJsonObject slimDoc(const QJsonObject &doc);
QJsonObject emptyDoc(qint64 now);

double docNum(const QJsonObject &object, const char *key);
bool jsonEqual(const QJsonValue &left, const QJsonValue &right);
