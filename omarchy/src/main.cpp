// Builder Launcher — the desk for the builder.cdr.xyz account.

#include <QCoreApplication>
#include <QGuiApplication>
#include <QIcon>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QQuickStyle>
#include <QQuickWindow>
#include <QTimer>
#include <QUrl>

#include "backend.h"
#include "library.h"
#include "theme.h"
#include "translator.h"

int main(int argc, char *argv[]) {
    const QStringList args = [&] {
        QStringList out;
        for (int i = 1; i < argc; ++i)
            out.append(QString::fromLocal8Bit(argv[i]));
        return out;
    }();
    if (!args.isEmpty() && args.first() == QLatin1String("bar")) {
        QCoreApplication core(argc, argv);
        core.setApplicationName(QStringLiteral("builder-launcher"));
        core.setOrganizationName(QStringLiteral("cdr"));
        return sendBarCommand(args.mid(1));
    }
    const bool demo = args.contains(QStringLiteral("--demo"));
    const bool resident = args.contains(QStringLiteral("--resident"));
    const int shot = args.indexOf(QStringLiteral("--screenshot"));
    const bool headless = !demo && !resident && !args.isEmpty() && args.first() != QLatin1String("--screenshot") &&
                          args.first() != QLatin1String("open");
    // The bar can start a windowless desk. A later launch, including one that
    // asks for a window, talks to the desk that already holds the socket.
    if (!demo && shot < 0 && !headless) {
        QCoreApplication probe(argc, argv);
        probe.setApplicationName(QStringLiteral("builder-launcher"));
        probe.setOrganizationName(QStringLiteral("cdr"));
        if (barDeskListening())
            return resident ? 0 : sendBarCommand({QStringLiteral("open")});
    }
    if (headless)
        qputenv("QT_QPA_PLATFORM", "offscreen");

    QGuiApplication app(argc, argv);
    app.setApplicationName(QStringLiteral("builder-launcher"));
    app.setOrganizationName(QStringLiteral("cdr"));
    app.setApplicationVersion(QStringLiteral("0.1.0"));
    app.setDesktopFileName(QStringLiteral("builder-launcher"));
    app.setWindowIcon(QIcon::fromTheme(QStringLiteral("builder-launcher")));
    // Closing the window hides it. The process keeps the bar socket. Demo and
    // screenshots still quit with the window.
    app.setQuitOnLastWindowClosed(demo || shot >= 0);
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
    engine.rootContext()->setContextProperty(QStringLiteral("startHidden"), resident);
    engine.rootContext()->setContextProperty(QStringLiteral("stayResident"), !demo && shot < 0);
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
