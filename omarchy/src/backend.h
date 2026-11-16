#pragma once

#include <QHash>
#include <QJsonObject>
#include <QMediaPlayer>
#include <QAudioOutput>
#include <QObject>
#include <QTimer>
#include <QVariantList>
#include <QVector>

#include "http.h"

class Backend : public QObject {
    Q_OBJECT
    Q_PROPERTY(QString page READ page NOTIFY changed)
    Q_PROPERTY(QString prompt READ prompt NOTIFY changed)
    Q_PROPERTY(QString draft READ draft WRITE setDraft NOTIFY changed)
    Q_PROPERTY(QString status READ status NOTIFY changed)
    Q_PROPERTY(QString title READ title NOTIFY changed)
    Q_PROPERTY(bool signedIn READ signedIn NOTIFY changed)
    Q_PROPERTY(QString email READ email NOTIFY changed)
    Q_PROPERTY(bool syncing READ syncing NOTIFY changed)
    Q_PROPERTY(QString syncLabel READ syncLabel NOTIFY changed)
    Q_PROPERTY(QVariantList rows READ rows NOTIFY changed)
    Q_PROPERTY(int selected READ selected NOTIFY changed)
    Q_PROPERTY(QString detail READ detail NOTIFY changed)
    Q_PROPERTY(bool detailEditor READ detailEditor NOTIFY changed)
    Q_PROPERTY(QString nowPlaying READ nowPlaying NOTIFY changed)
    Q_PROPERTY(bool playing READ playing NOTIFY changed)
    Q_PROPERTY(QString positionLabel READ positionLabel NOTIFY changed)
    Q_PROPERTY(QVariantList menu READ menu NOTIFY changed)
    Q_PROPERTY(bool menuOpen READ menuOpen NOTIFY changed)
    Q_PROPERTY(QString alert READ alert NOTIFY changed)
    Q_PROPERTY(QString alertTime READ alertTime NOTIFY changed)
    Q_PROPERTY(bool includeAi READ includeAi NOTIFY changed)
    Q_PROPERTY(QString weatherPlace READ weatherPlace NOTIFY changed)
    Q_PROPERTY(QString commandHint READ commandHint NOTIFY changed)
    Q_PROPERTY(bool dirty READ dirty NOTIFY changed)
    Q_PROPERTY(bool asking READ asking NOTIFY changed)

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

    void setDraft(const QString &draft);
    Q_INVOKABLE void loadDemo();
    void start();

    Q_INVOKABLE void submit();
    Q_INVOKABLE void setPrompt(const QString &glyph);
    Q_INVOKABLE void go(const QString &page);
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
    void helpRequested();

private:
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
    void searchStock(const QString &query);
    void searchPodcast(const QString &query);
    void saveArticle(const QString &url);
    void saveVideo(const QString &url);
    void resolveVideo(const QString &id);
    void ask(const QString &question);
    void refreshWeather();
    void refreshQuotes();
    void subscribeShow(const QString &feedUrl, const QString &title, const QString &author);

    Http m_http;
    QJsonObject m_doc;
    QString m_page = QStringLiteral("home");
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
    QMediaPlayer m_player;
    QAudioOutput m_audio;
    QJsonObject m_forecast;
    QHash<QString, QString> m_quotes;
    QString m_api = QStringLiteral("https://builder.cdr.xyz");
    QString m_episodeId;
    qint64 m_resumeMs = 0;
    qint64 m_playSavedAt = 0;
    qint64 m_playSavedPos = 0;
};
