package org.pixelgaffer.turnierserver.backend;

import it.sauronsoftware.ftp4j.FTPAbortedException;
import it.sauronsoftware.ftp4j.FTPDataTransferException;
import it.sauronsoftware.ftp4j.FTPException;
import it.sauronsoftware.ftp4j.FTPIllegalReplyException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

import org.pixelgaffer.turnierserver.backend.server.BackendWorkerConnectionHandler;
import org.pixelgaffer.turnierserver.backend.workerclient.WorkerClient;
import org.pixelgaffer.turnierserver.networking.DatastoreFtpClient;
import org.pixelgaffer.turnierserver.networking.messages.MessageForward;
import org.pixelgaffer.turnierserver.networking.messages.WorkerCommand;
import org.pixelgaffer.turnierserver.networking.messages.WorkerInfo;
import org.pixelgaffer.turnierserver.networking.messages.WorkerInfo.SandboxInfo;

/**
 * Diese Klasse speichert Informationen über einen verbundenen Worker.
 */
@ToString(exclude = { "connection", "client" })
@EqualsAndHashCode(of = { "id" })
public class WorkerConnection
{
	// id des Workers
	private static long nid = 0;
	private long id = nid++;
	
	private List<SandboxInfo> sandboxes;
	
	/** Gibt an, ob gerade ein Kompilierungsauftrag läuft. */
	@Getter
	@Setter
	private boolean compiling;
	
	/** Die Connection vom Backend zum Worker. */
	private BackendWorkerConnectionHandler connection;
	
	/** Die Connection vom Worker zum Backend. */
	private WorkerClient client;
	
	public WorkerConnection (@NonNull BackendWorkerConnectionHandler con, String addr, WorkerInfo info)
			throws IOException
	{
		connection = con;
		sandboxes = info.getSandboxes();
		client = new WorkerClient(addr, info);
	}
	
	/**
	 * Disconnected alle Verbindungen zum Worker.
	 */
	public void disconnect ()
	{
		disconnectClient();
		disconnectConnection();
	}
	
	/**
	 * Disconnected den Client zum Worker.
	 */
	public void disconnectClient ()
	{
		client.disconnect();
	}
	
	/**
	 * Disconnected die Verbindung vom Worker zum BackendWorkerServer.
	 */
	public void disconnectConnection ()
	{
		connection.disconnect();
	}
	
	/**
	 * Gibt an, ob der Worker gerade eine KI starten kann oder ob alle Worker
	 * komplett ausgelastet sind.
	 */
	public synchronized boolean canStartAi (String lang)
	{
		for (SandboxInfo info : sandboxes)
			if ((lang == null || info.getLangs().contains(lang)) && !info.isBusy())
				return true;
		return false;
	}
	
	/**
	 * Schickt einen Kompilieren-Befehl an den Worker, ai enthält
	 * ${ai-id}v${ai-version}.
	 */
	public WorkerCommand compile (String ai, String lang, int game)
			throws IOException, NumberFormatException, FTPIllegalReplyException, FTPException,
			FTPDataTransferException, FTPAbortedException
	{
		String s[] = ai.split("v");
		if (s.length != 2)
			throw new IllegalArgumentException(ai);
		return compile(Integer.parseInt(s[0]), Integer.parseInt(s[1]), lang, game);
	}
	
	/**
	 * Schickt einen Kompilieren-Befehl an den Worker.
	 */
	public synchronized WorkerCommand compile (int aiId, int version, String lang, int game)
			throws IOException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException
	{
		if (isCompiling())
			BackendMain.getLogger().warning("Gebe Kompilierungsauftrag an beschägtigten Worker weiter");
		setCompiling(true);
		if (lang == null)
			lang = DatastoreFtpClient.retrieveAiLanguage(aiId);
		WorkerCommand cmd = new WorkerCommand(WorkerCommand.COMPILE, aiId, version, lang, game, UUID.randomUUID());
		connection.sendCommand(cmd);
		return cmd;
	}
	
	/**
	 * Schickt einen StarteKI-Befehl an den Worker insofern dieser nicht
	 * komplett beschäftigt ist.
	 */
	public synchronized boolean addJob (AiWrapper ai, int game) throws IOException
	{
		if (!canStartAi(ai.getLang()))
			return false;
		connection.sendCommand(new WorkerCommand(WorkerCommand.STARTAI,
				ai.getAiId(), ai.getVersion(), ai.getLang(), game, ai.getUuid()));
		return true;
	}
	
	/**
	 * Schickt einen TerminiereKI-Befehl an den Worker.
	 */
	public void terminateJob (AiWrapper ai) throws IOException
	{
		connection.sendCommand(new WorkerCommand(WorkerCommand.TERMAI,
				ai.getAiId(), ai.getVersion(), ai.getLang(), -1, ai.getUuid()));
	}
	
	/**
	 * Schickt einen TerminiereKI-Befehl an den Worker.
	 */
	public void killJob (AiWrapper ai) throws IOException
	{
		connection.sendCommand(new WorkerCommand(WorkerCommand.KILLAI,
				ai.getAiId(), ai.getVersion(), ai.getLang(), -1, ai.getUuid()));
	}
	
	/**
	 * Schickt den MessageForward an den Worker weiter.
	 */
	public void sendMessage (MessageForward mf) throws IOException
	{
		client.sendMessage(mf);
	}
	
	/**
	 * Aktualisiert die Daten dieses Workers.
	 */
	public synchronized void update (WorkerInfo info)
	{
		BackendMain.getLogger().info("Der Worker " + id + " hat sich geupdated: " + info);
		sandboxes = info.getSandboxes();
		
		// Workers notifien
		Workers.workerIsAvailable();
	}
	
	/**
	 * Wird aufgerufen wenn eine KI fertig ist und der Worker somit die nächste
	 * starten kann.
	 */
	public synchronized void aiFinished ()
	{
		BackendMain.getLogger().todo("Brauche ich diese Methode?");
	}
}
