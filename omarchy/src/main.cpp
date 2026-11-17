// Builder Launcher — the desk for the builder.cdr.xyz account.

#include <QGuiApplication>
#include <QIcon>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QQuickStyle>
#include <QQuickWindow>
#include <QTimer>
#include <QUrl>

#include "backend.h"
#include "theme.h"
#include "translator.h"

int main(int argc, char *argv[]) {
    const QStringList args = [&] {
        QStringList out;
        for (int i = 1; i < argc; ++i)
            out.append(QString::fromLocal8Bit(argv[i]));
        return out;
    }();
    const bool demo = args.contains(QStringLiteral("--demo"));
    const int shot = args.indexOf(QStringLiteral("--screenshot"));
    if (!demo && !args.isEmpty() && args.first() != QLatin1String("--screenshot") && args.first() != QLatin1String("open")) {
        qputenv("QT_QPA_PLATFORM", "offscreen");
    }

    QGuiApplication app(argc, argv);
    app.setApplicationName(QStringLiteral("builder-launcher"));
    app.setOrganizationName(QStringLiteral("cdr"));
    app.setApplicationVersion(QStringLiteral("0.1.0"));
    app.setDesktopFileName(QStringLiteral("builder-launcher"));
    app.setWindowIcon(QIcon::fromTheme(QStringLiteral("builder-launcher")));
    QQuickStyle::setStyle(QStringLiteral("Material"));

    Translator translator;
    app.installTranslator(&translator);
    Theme theme;
    Backend backend;
    if (demo)
        backend.loadDemo();
    backend.start();

    if (!args.isEmpty() && args.first() == QLatin1String("tasks")) {
        // Headless listing is printed by the GUI object's rows after a rebuild.
        return 0;
    }

    QQmlApplicationEngine engine;
    QObject::connect(&translator, &Translator::changed, &engine, &QQmlEngine::retranslate);
    engine.rootContext()->setContextProperty(QStringLiteral("translator"), &translator);
    engine.rootContext()->setContextProperty(QStringLiteral("theme"), &theme);
    engine.rootContext()->setContextProperty(QStringLiteral("backend"), &backend);
    engine.load(QUrl(QStringLiteral("qrc:/Main.qml")));
    if (engine.rootObjects().isEmpty())
        return 1;

    if (shot >= 0 && shot + 1 < args.size()) {
        const QString path = args.at(shot + 1);
        QTimer::singleShot(500, &app, [&engine, path, &app] {
            auto *window = qobject_cast<QQuickWindow *>(engine.rootObjects().constFirst());
            if (window)
                window->grabWindow().save(path);
            app.quit();
        });
    }
    return app.exec();
}
