package org.pixelgaffer.turnierserver.worker;

import static org.pixelgaffer.turnierserver.PropertyUtils.BACKEND_HOST;
import static org.pixelgaffer.turnierserver.PropertyUtils.BACKEND_WORKER_SERVER_PORT;
import static org.pixelgaffer.turnierserver.PropertyUtils.WORKER_MIRROR_PORT;
import static org.pixelgaffer.turnierserver.PropertyUtils.WORKER_SERVER_MAX_CLIENTS;
import static org.pixelgaffer.turnierserver.PropertyUtils.WORKER_SERVER_PORT;
import static org.pixelgaffer.turnierserver.PropertyUtils.getInt;
import static org.pixelgaffer.turnierserver.PropertyUtils.getIntRequired;
import static org.pixelgaffer.turnierserver.PropertyUtils.getStringRequired;
import static org.pixelgaffer.turnierserver.PropertyUtils.loadProperties;

import java.io.IOException;
import java.util.logging.Logger;

import lombok.Getter;
import naga.ConnectionAcceptor;

import org.pixelgaffer.turnierserver.networking.NetworkService;
import org.pixelgaffer.turnierserver.networking.messages.WorkerInfo;
import org.pixelgaffer.turnierserver.worker.backendclient.BackendClient;
import org.pixelgaffer.turnierserver.worker.server.MirrorServer;
import org.pixelgaffer.turnierserver.worker.server.WorkerServer;

public class WorkerMain
{
	public static final WorkerInfo workerInfo = new WorkerInfo();
	
	public static void notifyInfoUpdated () throws IOException
	{
		getBackendClient().sendInfo(workerInfo);
	}
	
	@Getter
	private static BackendClient backendClient;
	
	/** Gibt den Standartlogger zurück. */
	public static Logger getLogger ()
	{
		return Logger.getLogger("BackendServer");
	}
	
	public static void main (String args[]) throws IOException
	{
		// Properties laden
		loadProperties(args.length > 0 ? args[0] : "/etc/turnierserver/turnierserver.prop");
		
		// Server starten
		getLogger().info("WorkerServer starting");
		int port = getInt(WORKER_SERVER_PORT, WorkerServer.DEFAULT_PORT);
		workerInfo.setPort(port);
		int maxClients = getInt(WORKER_SERVER_MAX_CLIENTS, -1);
		WorkerServer server = new WorkerServer(port, maxClients);
		server.setConnectionAcceptor(ConnectionAcceptor.ALLOW);
		new Thread( () -> NetworkService.mainLoop(), "NetworkService").start();
		getLogger().info("WorkerServer started");
		
		// Connect to Backend
		backendClient = new BackendClient(getStringRequired(BACKEND_HOST), getIntRequired(BACKEND_WORKER_SERVER_PORT));
		
		// Mirror starten
		port = getInt(WORKER_MIRROR_PORT, MirrorServer.DEFAULT_PORT);
		MirrorServer mirror = new MirrorServer(port);
		mirror.start();
		getLogger().info("MirrorServer started");
	}
}
