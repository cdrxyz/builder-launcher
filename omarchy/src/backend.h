#pragma once

#include <QHash>
#include <QJsonObject>
#include <QLocalServer>
#include <QStringList>
#include <QMediaPlayer>
#include <QAudioOutput>
#include <QObject>
#include <QSet>
#include <QTimer>
#include <QVariantList>
#include <QVector>

#include "http.h"
#include "library.h"

class Backend : public QObject {
    Q_OBJECT
    Q_PROPERTY(QString page READ page NOTIFY changed)
    Q_PROPERTY(int homePane READ homePane NOTIFY changed)
    Q_PROPERTY(QString prompt READ prompt NOTIFY changed)
    Q_PROPERTY(QString draft READ draft WRITE setDraft NOTIFY changed)
    Q_PROPERTY(QString status READ status NOTIFY changed)
    Q_PROPERTY(QString title READ title NOTIFY changed)
    Q_PROPERTY(bool signedIn READ signedIn NOTIFY changed)
    Q_PROPERTY(QString email READ email NOTIFY changed)
    Q_PROPERTY(bool syncing READ syncing NOTIFY changed)
    Q_PROPERTY(QString syncLabel READ syncLabel NOTIFY changed)
    Q_PROPERTY(QVariantList rows READ rows NOTIFY rowsChanged)
    Q_PROPERTY(int selected READ selected NOTIFY changed)
    Q_PROPERTY(QString detail READ detail NOTIFY changed)
    Q_PROPERTY(QString detailKey READ detailKey NOTIFY changed)
    Q_PROPERTY(double readingFraction READ readingFraction NOTIFY changed)
    Q_PROPERTY(bool detailEditor READ detailEditor NOTIFY changed)
    Q_PROPERTY(QString nowPlaying READ nowPlaying NOTIFY changed)
    Q_PROPERTY(bool playing READ playing NOTIFY changed)
    Q_PROPERTY(QString positionLabel READ positionLabel NOTIFY changed)
    Q_PROPERTY(QVariantList menu READ menu NOTIFY rowsChanged)
    Q_PROPERTY(bool menuOpen READ menuOpen NOTIFY rowsChanged)
    Q_PROPERTY(QString alert READ alert NOTIFY changed)
    Q_PROPERTY(QString alertTime READ alertTime NOTIFY changed)
    Q_PROPERTY(bool includeAi READ includeAi NOTIFY changed)
    Q_PROPERTY(QString weatherPlace READ weatherPlace NOTIFY changed)
    Q_PROPERTY(QString commandHint READ commandHint NOTIFY changed)
    Q_PROPERTY(bool dirty READ dirty NOTIFY changed)
    Q_PROPERTY(bool asking READ asking NOTIFY changed)
    Q_PROPERTY(bool canBack READ canBack NOTIFY historyChanged)
    Q_PROPERTY(bool canForward READ canForward NOTIFY historyChanged)

public:
    explicit Backend(QObject *parent = nullptr);

    QString page() const { return m_page; }
    QString prompt() const { return m_prompt; }
    QString draft() const { return m_draft; }
    QString status() const { return m_status; }
    QString title() const;
    bool signedIn() const { return !m_token.isEmpty() && m_token != QLatin1String("demo"); }
    QString email() const { return m_email; }
    bool syncing() const { return m_syncing; }
    QString syncLabel() const;
    QVariantList rows() const { return m_rows; }
    int selected() const { return m_selected; }
    QString detail() const { return m_detail; }
    QString detailKey() const { return m_page + QLatin1Char(':') + m_detailId; }
    double readingFraction() const;
    bool detailEditor() const { return m_page == QLatin1String("note"); }
    QString nowPlaying() const { return m_nowPlaying; }
    bool playing() const { return m_player.playbackState() == QMediaPlayer::PlayingState; }
    QString positionLabel() const;
    QVariantList menu() const { return m_menu; }
    bool menuOpen() const { return m_prompt == QLatin1String("/"); }
    QString alert() const { return m_alert; }
    QString alertTime() const { return m_alertTime; }
    bool includeAi() const { return m_includeAi; }
    QString weatherPlace() const;
    QString commandHint() const;
    bool dirty() const { return m_dirty; }
    bool asking() const { return m_asking; }
    bool canBack() const { return m_historyIndex > 0; }
    bool canForward() const { return m_historyIndex + 1 < m_history.size(); }

    void setDraft(const QString &draft);
    Q_INVOKABLE void loadDemo();
    void start();

    Q_INVOKABLE void submit();
    Q_INVOKABLE void setPrompt(const QString &glyph);
    Q_INVOKABLE void go(const QString &page);
    Q_INVOKABLE void back();
    Q_INVOKABLE void forward();
    Q_INVOKABLE void nudgeHomePane(int dx);
    int homePane() const { return m_homePane; }
    Q_INVOKABLE void moveSelection(int delta);
    Q_INVOKABLE void activateSelection();
    Q_INVOKABLE void deleteSelection();
    Q_INVOKABLE void toggleSelection();
    Q_INVOKABLE void undo();
    Q_INVOKABLE void redo();
    Q_INVOKABLE void pull();
    Q_INVOKABLE void push();
    Q_INVOKABLE void signIn(const QString &email, const QString &password);
    Q_INVOKABLE void signUp(const QString &email, const QString &password, const QString &code);
    Q_INVOKABLE void joinWaitlist(const QString &email);
    Q_INVOKABLE void signOut();
    Q_INVOKABLE void setIncludeAi(bool on);
    Q_INVOKABLE void saveApiKey(const QString &key);
    Q_INVOKABLE void playPause();
    Q_INVOKABLE void dismissAlert();
    Q_INVOKABLE void snoozeAlert();
    Q_INVOKABLE void setComposing(bool on);
    Q_INVOKABLE void saveDetail(const QString &text);
    Q_INVOKABLE void setWeatherPlace(const QString &query);
    Q_INVOKABLE bool signupOpens(const QString &code) const;
    Q_INVOKABLE QStringList shortcutRows() const;
    Q_INVOKABLE void showHelp();

signals:
    void changed();
    // Rows are a new list on every read. Notifying them from changed() made the
    // task list rebuild, and playback ticks then yanked the scroll position.
    void rowsChanged();
    void helpRequested();
    void historyChanged();
    void raiseRequested();

private:
    // A page plus the detail it opened. Podcast search keeps its hit list so
    // Back can return to those results after a show is opened.
    struct Place {
        QString page;
        QString detailId;
        bool podcastHits = false;
        QVariantList hits;
        bool operator==(const Place &other) const;
    };

    void applyPrompt(const QString &page);
    Place capture() const;
    void visit();
    void travel(int delta);
    void setStatus(const QString &status);
    void clearDraft();
    void rebuild();
    QVariantList visibleRows() const;
    void runSlash(const QString &name);
    void openSelection();
    void hydrate();
    int displayTemp(double celsius) const;
    QString placeLabel(qint64 positionMs, qint64 durationMs) const;
    QString articlePlace(const QJsonObject &article) const;
    QString episodePlace(const QJsonObject &episode) const;
    void mutate(const QJsonObject &next, const QString &hold = {});
    void remember();
    void saveVault();
    void saveSession();
    void loadLocal();
    void scheduleSync(const QString &hold);
    void syncIfDue();
    void api(const QString &method, const QString &path, const QJsonObject &body, bool refresh,
             const std::function<void(QJsonObject, QString)> &done);
    void afterAuth(const QJsonObject &body);
    void applyRemote(const QJsonObject &remote, bool existed);
    void finishPush(const QJsonObject &remote);
    QString selectedId() const;
    void playUrl(const QString &url, const QString &title, const QString &show, const QString &episodeId = {});
    void checkpointPlayback(bool force, bool ended);
    void checkAlarms();
    void notify(const QString &title, const QString &body);
    void setHabitReminders(bool on);
    void setHabitSound(const QString &sound);
    void searchStock(const QString &query);
    void searchPodcast(const QString &query);
    void saveArticle(const QString &url);
    void saveVideo(const QString &url);
    void resolveVideo(const QString &id);
    void ask(const QString &question);
    void refreshWeather();
    void refreshQuotes();
    void scheduleBar();
    void publishBar();
    void startBar();
    void handleBar(const QJsonObject &command);
    void refreshPodcastFeeds();
    void pumpFeeds();
    void storeFeed(const QString &feedUrl, const QByteArray &xml);
    void subscribeShow(const QString &feedUrl, const QString &title, const QString &author);

    Http m_http;
    QJsonObject m_doc;
    QString m_page = QStringLiteral("home");
    QVector<Place> m_history;
    int m_historyIndex = 0;
    bool m_restoring = false;
    QString m_prompt = QStringLiteral("-");
    QString m_draft;
    QString m_status;
    QString m_email;
    QString m_token;
    QString m_refresh;
    QString m_detailId;
    QString m_detail;
    QString m_nowPlaying;
    QString m_alert;
    QString m_alertTime;
    QString m_alertId;
    QVariantList m_rows;
    QVariantList m_menu;
    QVector<QJsonObject> m_undo;
    QVector<QJsonObject> m_redo;
    int m_selected = 0;
    int m_homePane = 0;
    int m_revision = 0;
    bool m_dirty = false;
    bool m_syncing = false;
    bool m_includeAi = false;
    bool m_asking = false;
    bool m_composing = false;
    bool m_refreshing = false;
    qint64 m_holdUntil = 0;
    QTimer m_syncTimer;
    QTimer m_clockTimer;
    QSet<QString> m_habitReminded;
    QMediaPlayer m_player;
    QAudioOutput m_audio;
    QJsonObject m_forecast;
    QHash<QString, Quote> m_quotes;
    QLocalServer m_barServer;
    QTimer m_barTimer;
    QTimer m_barPulse;
    QString m_api = QStringLiteral("https://builder.cdr.xyz");
    QString m_episodeId;
    QStringList m_feedQueue;
    QVariantList m_podcastHits;
    int m_searchGen = 0;
    bool m_feedBusy = false;
    bool m_showPodcastHits = false;
    qint64 m_resumeMs = 0;
    qint64 m_playSavedAt = 0;
    qint64 m_playSavedPos = 0;
};
