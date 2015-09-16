#-------------------------------------------------
#
# Project created by QtCreator 2015-05-24T21:55:21
#
#-------------------------------------------------

QT       += core network
QT       -= gui

TARGET = buildgame
CONFIG   += console
CONFIG   -= app_bundle

QMAKE_CXXFLAGS += -std=c++11

OBJECTS_DIR = obj/
MOC_DIR = gen/moc
RCC_DIR = gen/rc
UI_DIR  = gen/ui

TEMPLATE = app

# ENTFERNEN SOBALD WIR GÜLTIGE SSL ZERTIFIKATE HABEN !!!
DEFINES += NO_CERT_CHECK

LIBS += -lquazip5

INCLUDEPATH += include/

SOURCES += \
    src/main.cpp \
    src/buildinstructions.cpp \
    src/langspec.cpp \
    src/evaluator.cpp

DISTFILES += \
    examples/game.txt \
    langs/default.txt \
	langs/go.txt \
    langs/logic.txt \
	langs/java.txt \
    langs/javadef.txt \
    langs/python.txt

RESOURCES += \
    langs/langs.qrc \
    examples/examples.qrc

HEADERS += \
    include/buildinstructions.h \
    include/langspec.h \
    include/evaluator.h
