QT += core gui qml quick quickcontrols2 dbus network multimedia
CONFIG += c++17 release
CONFIG -= app_bundle
TARGET = builder-launcher
TEMPLATE = app

HEADERS += \
    src/backend.h \
    src/http.h \
    src/library.h \
    src/merge.h \
    src/theme.h \
    src/translator.h

SOURCES += \
    src/main.cpp \
    src/backend.cpp \
    src/backend_remote.cpp \
    src/backend_sync.cpp \
    src/http.cpp \
    src/library.cpp \
    src/merge.cpp \
    src/theme.cpp \
    src/translator.cpp

RESOURCES += src/resources.qrc
