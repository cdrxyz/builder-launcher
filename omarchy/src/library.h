#pragma once

#include <QHash>
#include <QJsonArray>
#include <QJsonObject>
#include <QLocale>
#include <QString>
#include <QStringList>
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
QString savedVideoId(const QString &raw);
QString slashCommand(const QString &query);
QStringList slashNames();
QJsonArray parseFeedEpisodes(const QByteArray &xml, const QString &feedUrl);

// Shows with no episodes always need a fetch. A show that already has episodes
// is fetched again once lastCheckedAt is missing or older than staleMs. The
// open show is first so that page fills before the rest of the library.
QStringList feedsToRefresh(const QJsonObject &doc, qint64 now, const QString &openFeed, qint64 staleMs = 30LL * 60 * 1000);

qint64 speechDurationMs(const QString &text);
qint64 articlePositionMs(const QJsonObject &doc, const QString &articleId);
double articleReadingFraction(const QJsonObject &doc, const QString &articleId);

// The page writes a playhead about every 10 seconds, or at once on pause.
bool playbackDue(qint64 lastSavedAt, qint64 now, qint64 lastPos, qint64 pos, bool force);
bool playbackFinished(qint64 positionMs, qint64 durationMs);
QJsonObject saveEpisodeProgress(QJsonObject doc, const QString &episodeId, qint64 positionMs, qint64 durationMs,
                                qint64 now, bool ended);
qint64 savedResumeMs(const QJsonObject &doc, const QString &episodeId);

QJsonObject touchExported(QJsonObject doc, qint64 now);
QJsonObject restoreLocalMedia(QJsonObject adopted, const QJsonObject &local);
bool slimDiffers(const QJsonObject &left, const QJsonObject &right);

QVector<int> alarmDays(const QJsonObject &alarm);
QJsonObject findById(const QJsonArray &list, const QString &id, const char *key = "id");

// A Yahoo chart quote. Percent is the daily move against the previous close.
struct Quote {
    bool ok = false;
    double price = 0;
    double percent = 0;
    QString currency;
};

Quote quoteFromChart(const QJsonObject &body);
QString formatQuotePrice(const Quote &quote, const QLocale &locale);
QString formatQuoteChange(const Quote &quote, const QLocale &locale);
// "up", "down", or "flat", using the same tenth-of-a-percent the label shows.
QString quoteTone(double percent);

// What the top bar is allowed to show. Titles are plain text: a task body is
// untrusted, and the bar paints it in a process that treats rich text as markup.
struct BarMedia {
    QString title;
    QString detail;
    QString kind;
    bool playing = false;
    QString position;
};

QString deskDataDir();
QString barSocketPath();
QString barStatusPath();
QJsonObject barStatus(const QJsonObject &doc, const QHash<QString, Quote> &quotes, const BarMedia &media,
                      const BarMedia &reading, const QString &weatherTemp, const QLocale &locale);
// Text from the bar's add line. Empty after trim is rejected. Controls become
// spaces so a paste cannot store a newline. Angle brackets stay in the vault;
// the bar display strips those when it paints.
QString barTaskText(const QString &text);
// Talks to the running desk. status prints the snapshot. Anything else needs the desk.
int sendBarCommand(const QStringList &args);
// True when a desk is already listening. A second launch uses this to raise
// that desk instead of taking its socket.
bool barDeskListening();

