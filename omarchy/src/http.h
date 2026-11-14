#pragma once

#include <QByteArray>
#include <QNetworkAccessManager>
#include <QObject>
#include <QString>
#include <functional>

class Http : public QObject {
    Q_OBJECT
public:
    using Done = std::function<void(int status, const QByteArray &body, const QString &error)>;
    explicit Http(QObject *parent = nullptr);
    void request(const QString &method, const QUrl &url, const QByteArray &body, const QString &token, Done done,
                 int timeoutMs = 20000);

private:
    QNetworkAccessManager m_network;
};
