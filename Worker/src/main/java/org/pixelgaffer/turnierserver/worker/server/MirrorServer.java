package org.pixelgaffer.turnierserver.worker.server;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.pixelgaffer.turnierserver.networking.DatastoreFtpClient;

/**
 * Dieser Server spiegelt den FTP-Server auf dem Datastore für die Sandboxen,
 * die aus Sicherheitsgründen nur mit dem Worker kommunizieren dürfen.
 */
public class MirrorServer extends Thread
{
	public static final int DEFAULT_PORT = 1338;
	
	private ServerSocket server;
	
	/**
	 * Öffnet den Server auf dem angegebenen Port.
	 */
	public MirrorServer (int port) throws IOException
	{
		server = new ServerSocket(port);
	}
	
	@Override
	public void run ()
	{
		while (!server.isClosed())
		{
			try
			{
				Socket client = server.accept();
				System.out.println("MirrorServer:33: wie wärs mit authentikation?");
				new Thread( () -> {
					try
					{
						BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
						int id = Integer.valueOf(in.readLine());
						int version = Integer.valueOf(in.readLine());
						OutputStream out = client.getOutputStream();
						out.write((Long.toString(DatastoreFtpClient.aiSize(id, version)) + "\n").getBytes(UTF_8));
						DatastoreFtpClient.retrieveAi(id, version, out);
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
					finally
					{
						try
						{
							client.close();
						}
						catch (IOException ioe)
						{
						}
					}
				}).start();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
}
