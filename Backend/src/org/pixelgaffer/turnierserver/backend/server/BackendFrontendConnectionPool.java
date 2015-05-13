package org.pixelgaffer.turnierserver.backend.server;

import naga.NIOSocket;

import org.pixelgaffer.turnierserver.networking.ConnectionPool;

public class BackendFrontendConnectionPool extends ConnectionPool<BackendFrontendConnectionHandler>
{
	public void add (NIOSocket client)
	{
		add(new BackendFrontendConnectionHandler(client));
	}
}