QT += core gui qml quick quickcontrols2 network multimedia testlib dbus
CONFIG += c++17 testcase
CONFIG -= app_bundle
TARGET = builder-launcher_tests
TEMPLATE = app
INCLUDEPATH += ../src

HEADERS += ../src/library.h ../src/merge.h ../src/theme.h ../src/translator.h ../src/backend.h ../src/http.h
SOURCES += builder-launcher_tests.cpp ../src/library.cpp ../src/merge.cpp ../src/theme.cpp ../src/translator.cpp \
    ../src/backend.cpp ../src/backend_sync.cpp ../src/backend_remote.cpp ../src/http.cpp
