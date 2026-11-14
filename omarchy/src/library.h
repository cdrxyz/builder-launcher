#pragma once

#include <QJsonArray>
#include <QJsonObject>
#include <QString>
#include <QVector>

// Local edits to the vault document. Stamps match the phone and the page so a
// later sync does not resurrect a delete or turn an alarm back on.

struct ParsedAlarm {
    int hour = 0;
    int minute = 0;
    QString label;
    QVector<int> days;
};

QString newId(qint64 now);
bool signupCodeOpens(const QString &code);

QJsonObject addTask(QJsonObject doc, const QString &text, qint64 now);
QJsonObject toggleTask(QJsonObject doc, const QString &id, qint64 now);
QJsonObject updateItemText(QJsonObject doc, const QString &id, const QString &text, qint64 now);
QJsonObject deleteItem(QJsonObject doc, const QString &id, qint64 now);
QJsonObject moveOpenTask(QJsonObject doc, int from, int to, qint64 now);
QJsonObject addNote(QJsonObject doc, const QString &title, qint64 now);

QVector<QJsonObject> openTodos(const QJsonObject &doc);
QVector<QJsonObject> doneTodos(const QJsonObject &doc);
QVector<QJsonObject> notesOf(const QJsonObject &doc);
QString noteTitle(const QString &text);

QVector<ParsedAlarm> parseAlarmInputs(const QString &raw);
QString formatAlarmTime(int hour, int minute);
QString formatAlarmDays(const QVector<int> &days);
QJsonObject addAlarms(QJsonObject doc, const QVector<ParsedAlarm> &alarms, qint64 now);
QJsonObject toggleAlarm(QJsonObject doc, const QString &id, qint64 now);
QJsonObject deleteAlarm(QJsonObject doc, const QString &id, qint64 now);
QJsonObject snoozeAlarm(QJsonObject doc, const QString &id, qint64 now);
QJsonObject acknowledgeAlarm(QJsonObject doc, const QString &id, qint64 now);
QJsonObject stampAlarmFired(QJsonObject doc, const QString &id, qint64 now);

qint64 nextTrigger(int hour, int minute, qint64 now, const QVector<int> &days);
qint64 previousTrigger(int hour, int minute, qint64 now, const QVector<int> &days);
bool alarmDue(const QJsonObject &alarm, qint64 now);

QString evaluateMath(const QString &text);
bool looksLikeMath(const QString &text);

QString articleIdForUrl(const QString &raw);
QString youtubeIdOf(const QString &raw);

QJsonObject touchExported(QJsonObject doc, qint64 now);
QJsonObject restoreLocalMedia(QJsonObject adopted, const QJsonObject &local);
bool slimDiffers(const QJsonObject &left, const QJsonObject &right);

QVector<int> alarmDays(const QJsonObject &alarm);
QJsonObject findById(const QJsonArray &list, const QString &id, const char *key = "id");

