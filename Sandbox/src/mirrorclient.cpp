/*
 * mirrorclient.cpp
 * 
 * Copyright (C) 2015 Dominic S. Meiser <meiserdo@web.de>
 * 
 * This work is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or any later
 * version.
 * 
 * This work is distributed in the hope that it will be useful, but without
 * any warranty; without even the implied warranty of merchantability or
 * fitness for a particular purpose. See version 2 and version 3 of the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#include "buffer.h"
#include "global.h"
#include "logger.h"
#include "mirrorclient.h"

#include <stdio.h>

#include <QCryptographicHash>
#include <QFile>
#include <QJsonDocument>
#include <QJsonObject>

MirrorClient::MirrorClient(const QString &host, quint16 port, QObject *parent)
	: QObject(parent)
	, _host(host)
	, _port(port)
{
	connect(&socket, SIGNAL(connected()), this, SLOT(connected()));
	connect(&socket, SIGNAL(disconnected()), this, SLOT(disconnected()));
	socket.connectToHost(host, port);
}

QByteArray sha256 (const QByteArray &salt)
{
	QByteArray hash = config->value("Worker/MirrorPassword").toByteArray();
	for (int i = 0; i < config->value("Worker/MirrorPasswordRepeats").toInt(); i++)
	{
		QByteArray b = salt + hash;
		hash = QCryptographicHash::hash(b, QCryptographicHash::Sha256);
	}
	return hash;
}

bool MirrorClient::retrieveAi (int id, int version, const QString &filename)
{
    // this is just the same as retrieving a library - the mirror will automatically know what to return
    return retrieveLib(QString::number(version), QString::number(id), filename);
}

bool MirrorClient::retrieveLib (const QString &language, const QString &lib, const QString &filename)
{
	if (!isConnected())
		return false;
	
    socket.write((lib + "\n" + language + "\n").toUtf8());
	if (!socket.waitForBytesWritten(timeout()))
	{
		LOG_CRITICAL << "failed to write bytes to mirror";
		return false;
	}
	
	if (!socket.waitForReadyRead(timeout()))
	{
        LOG_CRITICAL << "Failed to wait for data";
		return false;
	}
	QByteArray salt = socket.read(config->value("Worker/MirrorSaltLength").toInt());
	socket.write(sha256(salt).toBase64(QByteArray::KeepTrailingEquals) + "\n");
	
	Buffer buf;
	QByteArray line;
	while ((line = buf.readLine()).isEmpty())
	{
		if (!socket.waitForReadyRead(timeout()))
		{
            LOG_CRITICAL << "Failed to wait for file length";
			return false;
		}
		buf.append(socket.read(30)); // ich erwarte einen long (max 22 zeichen)
	}
	line = line.trimmed();
	qint64 size = line.toLongLong();
	LOG_INFO << "Empfange Datei: " + QString::number(size);
	
	QFile out(filename);
	if (!out.open(QIODevice::WriteOnly))
	{
        LOG_CRITICAL << "Failed to open output file " + filename + ": " + out.errorString();
		return false;
	}
	qint64 written = buf.size();
	out.write(buf.read());
	while (written < size)
	{
		if (!socket.waitForReadyRead(timeout()))
		{
            LOG_CRITICAL << "Failed to wait for data";
			return false;
		}
		qint64 toRead = size - written;
		while (toRead > 0)
		{
			QByteArray read = socket.read(qMin(toRead, (qint64)8192));
			if (read.isEmpty())
				break;
			written += read.size();
			toRead  -= read.size();
			out.write(read);
//			printf("read %d bytes\twritten: %lli/%lli\n", read.size(), written, size);
		}
	}
	out.close();
	
	return true;
}

void MirrorClient::connected ()
{
//	printf("connected to mirror :)\n");
	_connected = true;
}

void MirrorClient::disconnected ()
{
//	printf("disconnected from mirror :(\n");
	_connected = false;
	reconnect();
}

void MirrorClient::reconnect()
{
	socket.connectToHost(host(), port());
}
