#include <QFile>
#include <QJsonArray>
#include <QJsonDocument>
#include <QStandardPaths>
#include <QTemporaryDir>
#include <QtTest>

#include "backend.h"
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
    void feedsRefreshEmptyShowsFirst();
    void readingFractionUsesSavedPlace();
    void themeFallsBack();
    void translatorFallsBackToEnglish();
    void historyWalksPagesAndNotes();
    void quotesUseCurrencyAndDailyColor();
    void barStatusKeepsPlainGlanceables();
    void barAddKeepsTheTypedTask();

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

void BuilderLauncherTests::feedsRefreshEmptyShowsFirst() {
    QJsonObject showA;
    showA.insert(QStringLiteral("feedUrl"), QStringLiteral("https://example.com/a"));
    showA.insert(QStringLiteral("lastCheckedAt"), 1000000);
    QJsonObject showB;
    showB.insert(QStringLiteral("feedUrl"), QStringLiteral("https://example.com/b"));
    QJsonObject episode;
    episode.insert(QStringLiteral("id"), QStringLiteral("1"));
    episode.insert(QStringLiteral("showId"), QStringLiteral("https://example.com/a"));
    QJsonArray shows;
    shows.append(showA);
    shows.append(showB);
    QJsonArray episodes;
    episodes.append(episode);
    QJsonObject pods;
    pods.insert(QStringLiteral("shows"), shows);
    pods.insert(QStringLiteral("episodes"), episodes);
    QJsonObject doc;
    doc.insert(QStringLiteral("podcasts"), pods);
    const auto due = feedsToRefresh(doc, 1001000, QStringLiteral("https://example.com/b"));
    QCOMPARE(due, QStringList({QStringLiteral("https://example.com/b")}));
    const auto later = feedsToRefresh(doc, 1000000 + 31LL * 60 * 1000, {});
    QCOMPARE(later, QStringList({QStringLiteral("https://example.com/a"), QStringLiteral("https://example.com/b")}));
    const auto openFirst = feedsToRefresh(doc, 1000000 + 31LL * 60 * 1000, QStringLiteral("https://example.com/b"));
    QCOMPARE(openFirst, QStringList({QStringLiteral("https://example.com/b"), QStringLiteral("https://example.com/a")}));
}

void BuilderLauncherTests::readingFractionUsesSavedPlace() {
    QJsonObject article;
    article.insert(QStringLiteral("id"), QStringLiteral("r1"));
    article.insert(QStringLiteral("text"), QStringLiteral("one two three four"));
    QJsonObject progress;
    progress.insert(QStringLiteral("articleId"), QStringLiteral("r1"));
    progress.insert(QStringLiteral("positionMs"), 750);
    QJsonArray articles;
    articles.append(article);
    QJsonArray rows;
    rows.append(progress);
    QJsonObject reads;
    reads.insert(QStringLiteral("articles"), articles);
    reads.insert(QStringLiteral("progress"), rows);
    QJsonObject doc;
    doc.insert(QStringLiteral("reads"), reads);
    QCOMPARE(speechDurationMs(QStringLiteral("one two three four")), 1500);
    QCOMPARE(articleReadingFraction(doc, QStringLiteral("r1")), 0.5);
    QVERIFY(articleReadingFraction(doc, QStringLiteral("missing")) == 0.0);
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

void BuilderLauncherTests::historyWalksPagesAndNotes() {
    QStandardPaths::setTestModeEnabled(true);
    const QString dir = QStandardPaths::writableLocation(QStandardPaths::AppDataLocation);
    QVERIFY(!dir.contains(QStringLiteral("/cdr/builder-launcher")));
    Backend backend;
    QCOMPARE(backend.page(), QStringLiteral("home"));
    QVERIFY(!backend.canBack());
    QVERIFY(!backend.canForward());
    backend.back();
    QCOMPARE(backend.page(), QStringLiteral("home"));

    backend.loadDemo();
    backend.go(QStringLiteral("tasks"));
    backend.go(QStringLiteral("tasks"));
    backend.setPrompt(QStringLiteral("/"));
    QVERIFY(backend.canBack());
    QVERIFY(!backend.canForward());
    backend.back();
    QCOMPARE(backend.page(), QStringLiteral("home"));
    QVERIFY(backend.canForward());

    backend.forward();
    QCOMPARE(backend.page(), QStringLiteral("tasks"));
    backend.go(QStringLiteral("notes"));
    backend.back();
    backend.go(QStringLiteral("stocks"));
    QVERIFY(!backend.canForward());
    QCOMPARE(backend.page(), QStringLiteral("stocks"));
    backend.back();
    QCOMPARE(backend.page(), QStringLiteral("tasks"));

    backend.go(QStringLiteral("notes"));
    backend.activateSelection();
    QCOMPARE(backend.page(), QStringLiteral("note"));
    QCOMPARE(backend.detail(), QStringLiteral("# Shop notes"));
    backend.back();
    QCOMPARE(backend.page(), QStringLiteral("notes"));
    backend.forward();
    QCOMPARE(backend.page(), QStringLiteral("note"));
    QCOMPARE(backend.detail(), QStringLiteral("# Shop notes"));
    // Note ids are the clock, so a second note in the same millisecond would
    // share the first note's id and look like the same page.
    QTest::qWait(2);
    backend.setDraft(QStringLiteral("Second note"));
    backend.submit();
    QCOMPARE(backend.page(), QStringLiteral("note"));
    QCOMPARE(backend.detail(), QStringLiteral("# Second note"));
    backend.back();
    QCOMPARE(backend.page(), QStringLiteral("note"));
    QCOMPARE(backend.detail(), QStringLiteral("# Shop notes"));
    QVERIFY(backend.canForward());
}

void BuilderLauncherTests::quotesUseCurrencyAndDailyColor() {
    const QLocale en(QLocale::English, QLocale::UnitedStates);
    Quote gained;
    gained.ok = true;
    gained.price = 1234.5;
    gained.percent = 1.24;
    gained.currency = QStringLiteral("USD");
    QCOMPARE(formatQuotePrice(gained, en), QStringLiteral("$1,234.50"));
    QCOMPARE(formatQuoteChange(gained, en), QStringLiteral("+1.2%"));
    QCOMPARE(quoteTone(gained.percent), QStringLiteral("up"));

    Quote lost;
    lost.ok = true;
    lost.price = 88;
    lost.percent = -1.26;
    lost.currency = QStringLiteral("USD");
    QCOMPARE(formatQuotePrice(lost, en), QStringLiteral("$88.00"));
    QCOMPARE(formatQuoteChange(lost, en), QStringLiteral("-1.3%"));
    QCOMPARE(quoteTone(lost.percent), QStringLiteral("down"));

    Quote flat;
    flat.ok = true;
    flat.price = 10;
    flat.percent = 0.04;
    flat.currency = QStringLiteral("USD");
    QCOMPARE(formatQuoteChange(flat, en), QStringLiteral("0.0%"));
    QCOMPARE(quoteTone(flat.percent), QStringLiteral("flat"));

    Quote kroner;
    kroner.ok = true;
    kroner.price = 1234.5;
    kroner.percent = 0.2;
    kroner.currency = QStringLiteral("DKK");
    const QString dkk = formatQuotePrice(kroner, QLocale(QLocale::Danish, QLocale::Denmark));
    QVERIFY(dkk.contains(QStringLiteral("kr")));
    QVERIFY(dkk.contains(QLatin1Char(',')));

    const auto missing = quoteFromChart(QJsonObject());
    QVERIFY(!missing.ok);
    const auto parsed = quoteFromChart(QJsonDocument::fromJson(
        R"({"chart":{"result":[{"meta":{"regularMarketPrice":190.5,"chartPreviousClose":180,"currency":"USD"}}]}})")
                                            .object());
    QVERIFY(parsed.ok);
    QCOMPARE(parsed.currency, QStringLiteral("USD"));
    QCOMPARE(quoteTone(parsed.percent), QStringLiteral("up"));
    QCOMPARE(formatQuoteChange(parsed, en), QStringLiteral("+5.8%"));
}

void BuilderLauncherTests::barStatusKeepsPlainGlanceables() {
    QJsonObject doc = emptyDoc(1000);
    doc = addTask(doc, QStringLiteral("one"), 1000);
    doc = addTask(doc, QStringLiteral("two"), 2000);
    doc = addTask(doc, QStringLiteral("three"), 3000);
    doc = addTask(doc, QStringLiteral("four"), 4000);
    doc = addTask(doc, QStringLiteral("<img src='https://example.invalid/t'>"), 5000);
    auto settings = doc.value(QStringLiteral("settings")).toObject();
    settings.insert(QStringLiteral("apiKey"), QStringLiteral("secret-token"));
    settings.insert(QStringLiteral("weatherPlace"), QStringLiteral("Harbor"));
    doc.insert(QStringLiteral("settings"), settings);
    QJsonObject listed;
    listed.insert(QStringLiteral("symbol"), QStringLiteral("ACME"));
    QJsonArray watch;
    watch.append(listed);
    doc.insert(QStringLiteral("watchlist"), watch);
    QJsonObject alarm;
    alarm.insert(QStringLiteral("hour"), 7);
    alarm.insert(QStringLiteral("minute"), 30);
    alarm.insert(QStringLiteral("label"), QStringLiteral("Breakfast"));
    alarm.insert(QStringLiteral("enabled"), true);
    QJsonArray alarms;
    alarms.append(alarm);
    doc.insert(QStringLiteral("alarms"), alarms);

    Quote gained;
    gained.ok = true;
    gained.price = 10;
    gained.percent = 1;
    gained.currency = QStringLiteral("USD");
    QHash<QString, Quote> quotes;
    quotes.insert(QStringLiteral("ACME"), gained);
    BarMedia media;
    media.title = QStringLiteral("Episode <b>");
    media.detail = QStringLiteral("Show");
    media.kind = QStringLiteral("episode");
    media.playing = true;
    media.position = QStringLiteral("1:02");
    const QLocale en(QLocale::English, QLocale::UnitedStates);
    const auto status = barStatus(doc, quotes, media, BarMedia(), QStringLiteral("12°"), en);
    const auto body = QJsonDocument(status).toJson(QJsonDocument::Compact);
    QVERIFY(!body.contains("secret-token"));
    QVERIFY(!body.contains("<img"));
    QVERIFY(!body.contains("<b>"));
    QCOMPARE(status.value(QStringLiteral("todos")).toArray().size(), 3);
    QCOMPARE(status.value(QStringLiteral("todos")).toArray().first().toObject().value(QStringLiteral("title")).toString(),
             QStringLiteral("img src='https://example.invalid/t'"));
    QCOMPARE(status.value(QStringLiteral("media")).toObject().value(QStringLiteral("title")).toString(), QStringLiteral("Episode b"));
    QCOMPARE(status.value(QStringLiteral("media")).toObject().value(QStringLiteral("playing")).toBool(), true);
    const auto quote = status.value(QStringLiteral("quotes")).toArray().first().toObject();
    QCOMPARE(quote.value(QStringLiteral("price")).toString(), QStringLiteral("$10.00"));
    QCOMPARE(quote.value(QStringLiteral("change")).toString(), QStringLiteral("+1.0%"));
    QCOMPARE(quote.value(QStringLiteral("tone")).toString(), QStringLiteral("up"));
    QCOMPARE(status.value(QStringLiteral("weather")).toObject().value(QStringLiteral("temp")).toString(), QStringLiteral("12°"));
    QCOMPARE(status.value(QStringLiteral("alarm")).toObject().value(QStringLiteral("time")).toString(), QStringLiteral("07:30"));
}

void BuilderLauncherTests::barAddKeepsTheTypedTask() {
    QCOMPARE(barTaskText(QString()), QString());
    QCOMPARE(barTaskText(QStringLiteral("   \n\t  ")), QString());
    QCOMPARE(barTaskText(QStringLiteral("  buy <cedar> & oil  ")), QStringLiteral("buy <cedar> & oil"));
    QString noisy = QStringLiteral("line\rone\ttwo");
    noisy.append(QChar(7));
    QCOMPARE(barTaskText(noisy), QStringLiteral("line one two"));
    QCOMPARE(barTaskText(QString(500, QLatin1Char('n'))).size(), 400);

    const auto doc = addTask(emptyDoc(1), barTaskText(QStringLiteral("  buy <cedar> & oil  ")), 2);
    const auto item = doc.value(QStringLiteral("items")).toArray().first().toObject();
    QCOMPARE(item.value(QStringLiteral("text")).toString(), QStringLiteral("buy <cedar> & oil"));
    QCOMPARE(item.value(QStringLiteral("kind")).toString(), QStringLiteral("todo"));
    QVERIFY(item.value(QStringLiteral("completedAt")).isNull());
    QCOMPARE(openTodos(doc).size(), 1);

    const QLocale en(QLocale::English, QLocale::UnitedStates);
    const auto status = barStatus(doc, {}, BarMedia(), BarMedia(), {}, en);
    QCOMPARE(status.value(QStringLiteral("todos")).toArray().first().toObject().value(QStringLiteral("title")).toString(),
             QStringLiteral("buy cedar oil"));
}

QTEST_MAIN(BuilderLauncherTests)
#include "builder-launcher_tests.moc"
