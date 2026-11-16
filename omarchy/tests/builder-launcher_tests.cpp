#include <QFile>
#include <QJsonArray>
#include <QJsonDocument>
#include <QTemporaryDir>
#include <QtTest>

#include "library.h"
#include "merge.h"
#include "theme.h"
#include "translator.h"

class BuilderLauncherTests : public QObject {
    Q_OBJECT
private slots:
    void mergeMatchesTheWebClient();
    void signupCodeIsTrimmed();
    void reopenKeepsANewerStamp();
    void deleteRecordsTheId();
    void mathDoesNotStealABareNumber();
    void alarmParsesAWeekdayTime();
    void videoIdMatchesThePhone();
    void slashResolvesAUniquePrefix();
    void feedEpisodesUseShowId();
    void playbackWaitsTenSeconds();
    void playbackMarksNearlyFinished();
    void playbackProgressRoundTrips();
    void themeFallsBack();
    void translatorFallsBackToEnglish();

private:
    static void write(const QString &path, const QByteArray &contents) {
        QFile file(path);
        QVERIFY(file.open(QIODevice::WriteOnly));
        file.write(contents);
    }
};

void BuilderLauncherTests::mergeMatchesTheWebClient() {
    QFile file(QStringLiteral(QT_TESTCASE_BUILDDIR "/../../tests/fixtures/merge.json"));
    if (!file.exists())
        file.setFileName(QStringLiteral("fixtures/merge.json"));
    QVERIFY(file.open(QIODevice::ReadOnly));
    for (const auto &value : QJsonDocument::fromJson(file.readAll()).array()) {
        const auto row = value.toObject();
        const auto name = row.value(QStringLiteral("name")).toString();
        QVERIFY2(jsonEqual(mergeDocs(row.value(QStringLiteral("a")).toObject(), row.value(QStringLiteral("b")).toObject()),
                           row.value(QStringLiteral("ab"))),
                 qPrintable(name));
        QVERIFY2(jsonEqual(mergeDocs(row.value(QStringLiteral("b")).toObject(), row.value(QStringLiteral("a")).toObject()),
                           row.value(QStringLiteral("ba"))),
                 qPrintable(name + " reverse"));
        QVERIFY2(jsonEqual(slimDoc(row.value(QStringLiteral("a")).toObject()), row.value(QStringLiteral("slimA"))),
                 qPrintable(name + " slim"));
    }
}

void BuilderLauncherTests::signupCodeIsTrimmed() {
    QVERIFY(signupCodeOpens(QStringLiteral("  TimeToBuild ")));
    QVERIFY(!signupCodeOpens(QStringLiteral("nope")));
}

void BuilderLauncherTests::reopenKeepsANewerStamp() {
    QJsonObject item;
    item.insert(QStringLiteral("id"), QStringLiteral("abc"));
    item.insert(QStringLiteral("kind"), QStringLiteral("todo"));
    item.insert(QStringLiteral("text"), QStringLiteral("buy screws"));
    item.insert(QStringLiteral("createdAt"), 10);
    item.insert(QStringLiteral("completedAt"), 20);
    item.insert(QStringLiteral("updatedAt"), 20);
    QJsonObject doc;
    QJsonArray items;
    items.append(item);
    doc.insert(QStringLiteral("items"), items);
    const auto next = toggleTask(doc, QStringLiteral("abc"), 40);
    QCOMPARE(next.value(QStringLiteral("items")).toArray().first().toObject().value(QStringLiteral("completedAt")).isNull(), true);
    QCOMPARE(next.value(QStringLiteral("items")).toArray().first().toObject().value(QStringLiteral("updatedAt")).toDouble(), 40.0);
}

void BuilderLauncherTests::deleteRecordsTheId() {
    const auto added = addTask(emptyDoc(1), QStringLiteral("keep"), 2);
    const QString id = added.value(QStringLiteral("items")).toArray().first().toObject().value(QStringLiteral("id")).toString();
    const auto deleted = deleteItem(added, id, 3);
    QCOMPARE(deleted.value(QStringLiteral("items")).toArray().size(), 0);
    QCOMPARE(deleted.value(QStringLiteral("deletedIds")).toArray().first().toString(), id);
}

void BuilderLauncherTests::mathDoesNotStealABareNumber() {
    QVERIFY(!looksLikeMath(QStringLiteral("500")));
    QCOMPARE(evaluateMath(QStringLiteral("2+2")), QStringLiteral("4"));
}

void BuilderLauncherTests::alarmParsesAWeekdayTime() {
    const auto alarms = parseAlarmInputs(QStringLiteral("weekdays 7:30 stand up"));
    QCOMPARE(alarms.size(), 1);
    QCOMPARE(alarms.first().hour, 7);
    QCOMPARE(alarms.first().minute, 30);
    QCOMPARE(alarms.first().days, QVector<int>({1, 2, 3, 4, 5}));
}

void BuilderLauncherTests::videoIdMatchesThePhone() {
    QCOMPARE(savedVideoId(QStringLiteral("https://www.youtube.com/watch?v=dQw4w9WgXcQ")), QStringLiteral("vdQw4w9WgXcQ"));
    QCOMPARE(savedVideoId(QStringLiteral("https://youtu.be/dQw4w9WgXcQ")), QStringLiteral("vdQw4w9WgXcQ"));
    QCOMPARE(savedVideoId(QStringLiteral("not a video")), QString());
}

void BuilderLauncherTests::slashResolvesAUniquePrefix() {
    QCOMPARE(slashCommand(QStringLiteral("notes")), QStringLiteral("notes"));
    QCOMPARE(slashCommand(QStringLiteral("no")), QStringLiteral("notes"));
    QCOMPARE(slashCommand(QStringLiteral("pods")), QStringLiteral("podcasts"));
    QCOMPARE(slashCommand(QStringLiteral("ai")), QStringLiteral("chat"));
    QCOMPARE(slashCommand(QStringLiteral("s")), QString());
    QCOMPARE(slashCommand(QString()), QString());
}

void BuilderLauncherTests::feedEpisodesUseShowId() {
    const auto episodes = parseFeedEpisodes(
        QByteArray("<rss><channel><item><title>Ep</title><guid>g1</guid><duration>1:30</duration>"
                   "<enclosure url=\"https://example.com/a.mp3\"/></item></channel></rss>"),
        QStringLiteral("https://example.com/feed"));
    QCOMPARE(episodes.size(), 1);
    const auto episode = episodes.first().toObject();
    QCOMPARE(episode.value(QStringLiteral("showId")).toString(), QStringLiteral("https://example.com/feed"));
    QCOMPARE(episode.value(QStringLiteral("id")).toString(), QStringLiteral("g1"));
    QCOMPARE(episode.value(QStringLiteral("enclosureUrl")).toString(), QStringLiteral("https://example.com/a.mp3"));
    QCOMPARE(episode.value(QStringLiteral("durationMs")).toDouble(), 90000.0);
}

void BuilderLauncherTests::playbackWaitsTenSeconds() {
    QVERIFY(!playbackDue(0, 5000, 0, 1000, false));
    QVERIFY(playbackDue(0, 5000, 0, 20000, false));
    QVERIFY(playbackDue(0, 11000, 0, 1000, false));
    QVERIFY(playbackDue(1000, 1500, 0, 0, true));
}

void BuilderLauncherTests::playbackMarksNearlyFinished() {
    QVERIFY(!playbackFinished(10, 0));
    QVERIFY(!playbackFinished(10000, 3600000));
    QVERIFY(playbackFinished(95, 100));
    QVERIFY(playbackFinished(3580000, 3600000));
}

void BuilderLauncherTests::playbackProgressRoundTrips() {
    const auto saved = saveEpisodeProgress(emptyDoc(1), QStringLiteral("ep"), 12000, 90000, 50, false);
    QCOMPARE(savedResumeMs(saved, QStringLiteral("ep")), 12000);
    const auto row = saved.value(QStringLiteral("podcasts")).toObject().value(QStringLiteral("progress")).toArray().first().toObject();
    QCOMPARE(row.value(QStringLiteral("episodeId")).toString(), QStringLiteral("ep"));
    QCOMPARE(row.value(QStringLiteral("lastPlayedAt")).toDouble(), 50.0);
    QCOMPARE(row.value(QStringLiteral("finished")).toBool(), false);
    QCOMPARE(row.value(QStringLiteral("skipped")).toBool(), false);
    const auto again = saveEpisodeProgress(saved, QStringLiteral("ep"), 88000, 90000, 60, false);
    QCOMPARE(again.value(QStringLiteral("podcasts")).toObject().value(QStringLiteral("progress")).toArray().size(), 1);
    QVERIFY(again.value(QStringLiteral("podcasts")).toObject().value(QStringLiteral("progress")).toArray().first().toObject().value(QStringLiteral("finished")).toBool());
    QCOMPARE(savedResumeMs(again, QStringLiteral("ep")), 0);
}

void BuilderLauncherTests::themeFallsBack() {
    QTemporaryDir dir;
    Theme theme(dir.path());
    QCOMPARE(theme.accent(), QStringLiteral("#FFD60A"));
    QCOMPARE(theme.accentForeground(), QStringLiteral("black"));
}

void BuilderLauncherTests::translatorFallsBackToEnglish() {
    QTemporaryDir dir;
    Translator translator(dir.path() + QStringLiteral("/language"), dir.path());
    QCOMPARE(translator.language(), QStringLiteral("en"));
    QVERIFY(translator.translate("Main", "Save").isNull());
}

QTEST_MAIN(BuilderLauncherTests)
#include "builder-launcher_tests.moc"
