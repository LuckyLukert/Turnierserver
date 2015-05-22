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

#include "global.h"

#include "buffer.h"
#include "mirrorclient.h"

#include <stdio.h>

#include <QFile>
#include <QJsonDocument>
#include <QJsonObject>

MirrorClient::MirrorClient(const QString &host, quint16 port, QObject *parent)
	: QObject(parent)
	, _host(host)
	, _port(port)
{
	socket.connectToHost(host, port);
}

void MirrorClient::retrieveAi (int id, int version, const QString &filename)
{
	if (!socket.isOpen())
		if (!socket.waitForConnected(timeout()))
			if (!reconnect())
				return;
	
	QJsonObject json;
	json.insert("id", id);
	json.insert("version", version);
	QJsonDocument jsondoc(json);
	
	socket.write(jsondoc.toJson(QJsonDocument::Compact) + "\n");
	if (!socket.waitForBytesWritten(timeout()))
	{
		printf("failed to write bytes to mirror\n");
		return;
	}
	
	Buffer buf;
	QByteArray line;
	while ((line = buf.readLine()).isEmpty())
	{
		if (!socket.waitForReadyRead(timeout()))
		{
			fprintf(stderr, "MirrorClient::retrieveAi(): Failed to wait for file length.\n");
			return;
		}
		buf.append(socket.read(30)); // ich erwarte einen long (max 22 zeichen)
	}
	line = line.trimmed();
	qint64 size = line.toLongLong();
	printf("size: %d\n", size);
	
	QFile out(filename);
	if (!out.open(QIODevice::WriteOnly))
	{
		fprintf(stderr, "MirrorClient::retrieveAi(): Failed to open output file %s: %s\n", qPrintable(filename), qPrintable(out.errorString()));
		return;
	}
	qint64 written = buf.size();
	printf("left buffer size: %d\n", written);
	out.write(buf.read());
	while (written < size)
	{
		printf("written: %d / %d\n", written, size);
		if (!socket.waitForReadyRead(timeout()))
		{
			fprintf(stderr, "MirrorClient::retrieveAi(): Failed to wait for data.\n");
			return;
		}
		QByteArray read = socket.read(size - written);
		written += read.size();
		out.write(read);
	}
	out.close();
}

void MirrorClient::connected ()
{
	printf("connected to mirror :)\n");
}

void MirrorClient::disconnected ()
{
	printf("disconnected from mirror :(\n");
	if (!reconnect())
		printf("failed to reconnect to mirror\n");
}

bool MirrorClient::reconnect()
{
	printf("keine ahnung \n");
	return false;
}
