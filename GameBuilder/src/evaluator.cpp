/*
 * evaluator.cpp
 *
 * Copyright (C) 2015 Pixelgaffer
 *
 * This work is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or any later
 * version.
 *
 * This work is distributed in the hope that it will be useful, but without
 * any warranty; without even the implied warranty of merchantability or
 * fitness for a particular purpose. See version 2 and version 3 of the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#include "buildinstructions.h"
#include "evaluator.h"
#include "langspec.h"

#include <stdio.h>

#include <QDebug>
#include <QDir>
#include <QEventLoop>
#include <QHttpMultiPart>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QRegularExpression>

#include <quazip5/JlCompress.h>

// zeugs zum passwort lesen
#include <iostream>
#include <string>
#if defined __unix
#  include <termios.h>
#  include <unistd.h>
#elif defined __WIN32 || defined __WIN64
#  include <windows.h>
#endif
using namespace std;

Evaluator::Evaluator(const BuildInstructions &instructions)
	: _instructions(instructions)
	, pwCache(QSettings::IniFormat, QSettings::UserScope, "Pixelgaffer", "GameBuilder")
{
}

Evaluator::~Evaluator()
{
	qDeleteAll(langSpecs);
	if (mgr)
		delete mgr;
}

bool Evaluator::createLangSpecs(bool verbose)
{
	for (QString lang : instructions().langs())
	{
		if (verbose)
			qDebug() << "Erstelle LangSpec für die Sprache" << lang;
		LangSpec *spec = new LangSpec(instructions(), lang);
		if (!spec->read(verbose))
			return false;
		langSpecs << spec;
	}
	return true;
}

int Evaluator::target(const QString &target)
{
	for (LangSpec *spec : langSpecs)
	{
		int ret = this->target(target, spec);
		if (ret != 0)
			return ret;
	}
	return 0;
}

int Evaluator::target(const QString &target, LangSpec *spec)
{
	printf("Baue Ziel %s für %s\n", qPrintable(target), qPrintable(spec->lang()));
	QStringList commands = spec->targetCommands(target);
	for (QString command : commands)
	{
		command = spec->fillVars(command);
		if (command.startsWith("mkdir "))
		{
			QString dir = command.mid(6).trimmed();
			if (!QFileInfo(dir).exists() && !QDir().mkdir(dir))
			{
				fprintf(stderr, "Konnte das Verzeichnis %s nicht anlegen\n", qPrintable(dir));
				return 1;
			}
		}
		else if (command.startsWith("rm "))
		{
			QString file = command.mid(3);
			QFileInfo fileInfo(file);
			bool success = true; // wenn file nicht existiert
			if (fileInfo.isDir())
				success = QDir(file).removeRecursively();
			else if (fileInfo.exists())
				success = QFile(file).remove();
			if (!success)
			{
				fprintf(stderr, "Konnte %s nicht löschen\n", qPrintable(file));
				return 1;
			}
		}
		else if (command.startsWith("exec"))
		{
			QRegularExpression regex("^exec(\\s+in\\s+\"(?P<wd>[^\"]+)\")?\\s+(?P<cmd>[^\r\n]+)\\s*$");
			QRegularExpressionMatch match = regex.match(command);
			if (!match.hasMatch())
			{
				fprintf(stderr, "Syntaxfehler im exec-Befehl\n%s\n     ^\n", qPrintable(command));
				return 1;
			}
			
			QString wd = match.captured("wd");
			QString cwd;
			if (!wd.isEmpty())
			{
				cwd = QDir::currentPath();
				if (!QDir::setCurrent(wd))
				{
					fprintf(stderr, "Fehler beim Ändern des Arbeitsverzeichnises zu %s\n", qPrintable(wd));
					return 1;
				}
			}
			
			QString cmd = match.captured("cmd");
			//qDebug() << "cmd:" << cmd;
			printf("%s$ %s\n", qPrintable(wd), qPrintable(cmd));
			int ret = system(qPrintable(cmd));
			if (ret != 0)
				return ret;
			
			if (!cwd.isEmpty())
			{
				if (!QDir::setCurrent(cwd))
				{
					fprintf(stderr, "Fehler beim Ändern des Arbeitsverzeichnises zu %s\n", qPrintable(cwd));
					return 1;
				}
			}
		}
		else if (command.startsWith("echo "))
		{
			QRegularExpression regex("^echo\\s+\"(?P<text>[^\"]+)\"(\\s+(?P<operator>\\>|\\>\\>)\\s*\"(?P<destination>[^\"]+)\")?\\s*");
			QRegularExpressionMatch match = regex.match(command);
			if (!match.hasMatch())
			{
				fprintf(stderr, "Syntaxfehler im echo-Befehl\n%s\n     ^\n", qPrintable(command));
				return 1;
			}
			
			QString text = match.captured("text");
			QString op = match.captured("operator");
			QString destination = match.captured("destination");
			FILE *file = destination.isEmpty() ? stdout : fopen(qPrintable(destination), op == ">" ? "w" : "a");
			if (!file)
			{
				fprintf(stderr, "Kann Datei %s nicht öffnen\n", qPrintable(destination));
				return 1;
			}
			fprintf(file, "%s\n", qPrintable(text));
			if (!destination.isEmpty())
				fclose(file);
		}
		else if (command.startsWith("upload "))
		{
			QRegularExpression regex("^upload \"(?P<file>[^\"]+)\" to \"(?P<destination>[^\"]+)\"\\s*$");
			QRegularExpressionMatch match = regex.match(command);
			if (!match.hasMatch())
			{
				fprintf(stderr, "Syntaxfehler im upload-Befehö\n%s\n       ^\n", qPrintable(command));
				return 1;
			}
			
			QString file = match.captured("file");
			QFileInfo fileInfo(file);
			if (!fileInfo.exists())
			{
				fprintf(stderr, "%s existiert nicht\n", qPrintable(file));
				return 1;
			}
			QString destination = match.captured("destination");
			
			
			// wenn der NetworkManager 0 ist diesen erstellen
			if (!mgr)
			{
				QTextStream in(stdin);
				mgr = new QNetworkAccessManager;
				
				// testen ob https unterstützt wird
				QNetworkRequest pingRequest("https://" + host + "/api/ping");
				pingRequest.setHeader(QNetworkRequest::UserAgentHeader, "GameBuilder (QtNetwork " QT_VERSION_STR ")");
				QEventLoop loop1;
				QObject::connect(mgr, SIGNAL(finished(QNetworkReply*)), &loop1, SLOT(quit()));
				QNetworkReply *reply = mgr->get(pingRequest);
				loop1.exec();
				https = reply->error() != QNetworkReply::NoError;
				delete reply;
				if (!https)
					fprintf(stderr, "WARNUNG: Benutze unsicheres http-Protokol\n");
				
				pwCache.beginGroup("pwCache");
				
				// host fragen
				if (host.isEmpty())
				{
					QString def = pwCache.value("host").toString();
					if (def.isEmpty())
						printf("Host: ");
					else
						printf("Host(%s): ", qPrintable(def));
					host = in.readLine();
					if (host.isEmpty())
						host = def;
					pwCache.setValue("host", host);
				}
				// benutzer fragen
				if (email.isEmpty())
				{
					QString def = pwCache.value("mail").toString();
					if (def.isEmpty())
						printf("E-Mail: ");
					else
						printf("E-Mail(%s): ", qPrintable(def));
					email = in.readLine();
					if (email.isEmpty())
						email = def;
					pwCache.setValue("mail", email);
				}
				// passwort fragen
				if (pass.isEmpty())
				{
#if defined __unix
					printf("Passwort: ");
					
					termios oldt;
					tcgetattr(STDIN_FILENO, &oldt);
					termios newt = oldt;
					newt.c_lflag &= ~ECHO;
					tcsetattr(STDIN_FILENO, TCSANOW, &newt);
					
					string s;
					getline(cin, s);
					pass = s.data();
					printf("\n");
					
					tcsetattr(STDIN_FILENO, TCSANOW, &oldt);
#elif defined __WIN32 || defined __WIN64
					printf("Passwort: ");
					
					HANDLE hStdin = GetStdHandle(STD_INPUT_HANDLE); 
					DWORD mode = 0;
					GetConsoleMode(hStdin, &mode);
					SetConsoleMode(hStdin, mode & (~ENABLE_ECHO_INPUT));
					
					string s;
					getline(cin, s);
					pass = s.data();
					
					SetConsoleMode(hStdin, mode);
#elif defined __APPLE__
					char *s = getpass("Passwort: ");
					pass = s;
					
#else
					printf("Passwort (wird auf der Kommandozeile angezeigt): ");
					in >> pass;
#endif
				}
				
				pwCache.endGroup();
				
				// anmelden
				QNetworkRequest loginRequest("http://" + host + "/api/login"); // sollte https werden
				loginRequest.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
				loginRequest.setHeader(QNetworkRequest::UserAgentHeader, "GameBuilder (QtNetwork " QT_VERSION_STR ")");
				QJsonObject json;
				json.insert("email", email);
				json.insert("password", pass);
				QJsonDocument doc(json);
				QEventLoop loop;
				QObject::connect(mgr, SIGNAL(finished(QNetworkReply*)), &loop, SLOT(quit()));
				reply = mgr->post(loginRequest, doc.toJson(QJsonDocument::Compact));
				loop.exec();
				if (reply->error() != QNetworkReply::NoError)
				{
					fprintf(stderr, "Fehler beim Anmelden: %s\n", qPrintable(reply->errorString()));
					if (reply->header(QNetworkRequest::ContentTypeHeader).toString() == "application/json")
					{
						QJsonDocument jsondoc = QJsonDocument::fromJson(reply->readAll());
						QJsonObject json = jsondoc.object();
						fprintf(stderr, "Fehler: %s\n", qPrintable(json.value("error").toString()));
					}
					else
						fprintf(stderr, "%s\n", reply->readAll().data());
					return 1;
				}
			}
			
			// schauen ob es den gametype schon gibt
			bool gameExists = false; int gameid = -1;
			QNetworkRequest checkRequest("http://" + host + "/api/gametypes"); // sollte https werden
			QEventLoop loop1;
			QObject::connect(mgr, SIGNAL(finished(QNetworkReply*)), &loop1, SLOT(quit()));
			QNetworkReply *reply = mgr->get(checkRequest);
			loop1.exec();
			if (reply->error() != QNetworkReply::NoError)
			{
				fprintf(stderr, "Fehler beim Abrufen der Spiele: %s\n", qPrintable(reply->errorString()));
				if (reply->header(QNetworkRequest::ContentTypeHeader).toString() == "application/json")
				{
					QJsonDocument jsondoc = QJsonDocument::fromJson(reply->readAll());
					QJsonObject json = jsondoc.object();
					fprintf(stderr, "Fehler: %s\n", qPrintable(json.value("error").toString()));
				}
				else
					fprintf(stderr, "%s\n", reply->readAll().data());
				delete reply;
				return 1;
			}
			QJsonDocument jsonDoc = QJsonDocument::fromJson(reply->readAll());
			QJsonArray gametypes = jsonDoc.array();
			for (int i = 0; i < gametypes.size(); i++)
			{
				QJsonObject gametype = gametypes[i].toObject();
				
				if (gametype.value("name").toString() == instructions().values().value("NAME"))
				{
					gameExists = true;
					gameid = gametype.value("id").toInt();
				}
				
			}
			delete reply;
			destination.replace("<gameid>", QString::number(gameid));
			
			if (!gameExists)
			{
				printf("Das Spiel %s existiert noch nicht. Möchtest du es anlegen? [y/n] ", qPrintable(instructions().values().value("NAME")));
				if (getc(stdin) == 'y')
				{
					QNetworkRequest createRequest("http://" + host + "/api/add_gametype/" + instructions().values().value("NAME"));
					QEventLoop loop;
					QObject::connect(mgr, SIGNAL(finished(QNetworkReply*)), &loop, SLOT(quit()));
					reply = mgr->post(createRequest, QByteArray());
					loop.exec();
					if (reply->error() != QNetworkReply::NoError)
					{
						fprintf(stderr, "Fehler beim Erstellen des Spiels: %s\n", qPrintable(reply->errorString()));
						if (reply->header(QNetworkRequest::ContentTypeHeader).toString() == "application/json")
						{
							QJsonDocument jsondoc = QJsonDocument::fromJson(reply->readAll());
							QJsonObject json = jsondoc.object();
							fprintf(stderr, "Fehler: %s\n", qPrintable(json.value("error").toString()));
						}
						else
							fprintf(stderr, "%s\n", reply->readAll().data());
						delete reply;
						return 1;
					}
					QJsonDocument jsonDoc = QJsonDocument::fromJson(reply->readAll());
					QJsonObject gameObj = jsonDoc.object();
					gameid = gameObj.value("id").toInt();
					printf("Das neue Spiel wurde erfolgreich angelegt\n");
					delete reply;
				}
				else
					return 1;
			}
			
			if (QFileInfo(file).isDir())
			{
				fprintf(stderr, "Baue ZIP-Datei für '%s' ...", qPrintable(file));
				file = createZip(file);
				fprintf(stderr, " '%s'\n", qPrintable(file));
			}
			
			// die Datei hochladen
			QFile in(file);
			if (!in.open(QIODevice::ReadOnly))
			{
				fprintf(stderr, "Kann Datei %s nicht öffnen", qPrintable(file));
				if (reply->header(QNetworkRequest::ContentTypeHeader).toString() == "application/json")
				{
					QJsonDocument jsondoc = QJsonDocument::fromJson(reply->readAll());
					QJsonObject json = jsondoc.object();
					fprintf(stderr, "Fehler: %s\n", qPrintable(json.value("error").toString()));
				}
				else
					fprintf(stderr, "%s\n", reply->readAll().data());
				delete reply;
				return 1;
			}
			QNetworkRequest request("http://" + host + destination); // sollte https werden
			printf("Uploading %s -> %s\n", qPrintable(file), qPrintable(request.url().toString()));
			request.setHeader(QNetworkRequest::ContentTypeHeader, "application/octet-stream");
			request.setHeader(QNetworkRequest::UserAgentHeader, "GameBuilder (QtNetwork " QT_VERSION_STR ")");
			request.setRawHeader("X-FileName", fileInfo.fileName().toUtf8());
			QEventLoop loop2;
			QObject::connect(mgr, SIGNAL(finished(QNetworkReply*)), &loop2, SLOT(quit()));
			reply = mgr->post(request, &in);
			loop2.exec();
			if (reply->error() != QNetworkReply::NoError)
			{
				fprintf(stderr, "Fehler beim Hochladen von %s: %s\n", qPrintable(file), qPrintable(reply->errorString()));
				if (reply->header(QNetworkRequest::ContentTypeHeader).toString() == "application/json")
				{
					QJsonDocument jsondoc = QJsonDocument::fromJson(reply->readAll());
					QJsonObject json = jsondoc.object();
					fprintf(stderr, "Fehler: %s\n", qPrintable(json.value("error").toString()));
				}
				else
					fprintf(stderr, "%s\n", reply->readAll().data());
				delete reply;
				return 1;
			}
			delete reply;
		}
		else
		{
			if (command.contains(QRegularExpression("[^a-z]")))
			{
				fprintf(stderr, "Unknown command: %s\n", qPrintable(command));
				return 1;
			}
			else
			{
				int ret = this->target(command, spec);
				if (ret != 0)
					return ret;
			}
		}
	}
	return 0;
}

QString Evaluator::createZip(const QDir &dir, QString filename)
{
	if (filename.isEmpty())
		filename = dir.absolutePath() + ".zip";
	
	JlCompress::compressDir(filename, dir.absolutePath());
	
	return filename;
}
