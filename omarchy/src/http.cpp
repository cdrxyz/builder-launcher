#include "http.h"

#include <QNetworkReply>
#include <QTimer>

Http::Http(QObject *parent) : QObject(parent) {}

void Http::request(const QString &method, const QUrl &url, const QByteArray &body, const QString &token, Done done,
                   int timeoutMs) {
    QNetworkRequest request(url);
    request.setHeader(QNetworkRequest::UserAgentHeader, QStringLiteral("BuilderLauncher/0.1.0"));
    request.setRawHeader("accept", "application/json");
    if (!body.isEmpty())
        request.setHeader(QNetworkRequest::ContentTypeHeader, QStringLiteral("application/json"));
    if (!token.isEmpty())
        request.setRawHeader("authorization", QByteArray("Bearer ") + token.toUtf8());
    QNetworkReply *reply = nullptr;
    if (method == QLatin1String("GET"))
        reply = m_network.get(request);
    else if (method == QLatin1String("PUT"))
        reply = m_network.put(request, body);
    else
        reply = m_network.sendCustomRequest(request, method.toUtf8(), body);
    auto *timer = new QTimer(reply);
    timer->setSingleShot(true);
    QObject::connect(timer, &QTimer::timeout, reply, [reply] { reply->abort(); });
    timer->start(timeoutMs);
    QObject::connect(reply, &QNetworkReply::finished, reply, [reply, done] {
        const auto error = reply->error();
        const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
        const QByteArray payload = reply->readAll();
        QString message;
        if (error == QNetworkReply::OperationCanceledError)
            message = QStringLiteral("Request timed out. Try again.");
        else if (error != QNetworkReply::NoError && status == 0)
            message = reply->errorString();
        done(status, payload, message);
        reply->deleteLater();
    });
}
