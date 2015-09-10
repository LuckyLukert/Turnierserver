package org.pixelgaffer.turnierserver.worker;

import static org.pixelgaffer.turnierserver.networking.messages.SandboxCommand.KILL_AI;
import static org.pixelgaffer.turnierserver.networking.messages.SandboxCommand.RUN_AI;
import static org.pixelgaffer.turnierserver.networking.messages.SandboxCommand.CPU_TIME;
import static org.pixelgaffer.turnierserver.networking.messages.SandboxCommand.TERM_AI;
import static org.pixelgaffer.turnierserver.networking.messages.SandboxMessage.FINISHED_AI;
import static org.pixelgaffer.turnierserver.networking.messages.SandboxMessage.KILLED_AI;
import static org.pixelgaffer.turnierserver.networking.messages.SandboxMessage.STARTED_AI;
import static org.pixelgaffer.turnierserver.networking.messages.SandboxMessage.TERMINATED_AI;
import static org.pixelgaffer.turnierserver.networking.messages.WorkerConnectionType.SANDBOX;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.pixelgaffer.turnierserver.networking.messages.SandboxCommand;
import org.pixelgaffer.turnierserver.networking.messages.SandboxMessage;
import org.pixelgaffer.turnierserver.networking.messages.WorkerInfo.SandboxInfo;
import org.pixelgaffer.turnierserver.worker.server.WorkerConnectionHandler;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * Repräsentiert eine Sandbox.
 */
@ToString(exclude = { "connection" })
public class Sandbox
{
	@Getter
	private SandboxInfo sandboxInfo = new SandboxInfo();
	
	@Getter
	private long lastCpuTime;
	private Object cpuTimeLock = new Object();
	
	public void updateCpuTime() 
	{
		WorkerMain.getLogger().debug("update CPU time");
		try {
			WorkerMain.getLogger().debug("sende");
			sendJob(new SandboxCommand(CPU_TIME, -1, -1, "", null));
			WorkerMain.getLogger().debug("gesendet");
		} catch (Exception e) {
			e.printStackTrace();
		}
		synchronized (cpuTimeLock) 
		{
			try 
			{
				WorkerMain.getLogger().debug("warte auf notify");
				cpuTimeLock.wait();
				WorkerMain.getLogger().debug("ich wurde notifiziert");
			} 
			catch (InterruptedException e) 
			{
				e.printStackTrace();
			}
		}
	}
	
	public long getCpuTimeDiff()
	{
		long oldTime = lastCpuTime;
		try 
		{
			Sandboxes.send(new SandboxCommand(CPU_TIME, -1, -1, "", null));
		}
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		synchronized (cpuTimeLock) 
		{
			try 
			{
				cpuTimeLock.wait();
			} 
			catch (InterruptedException e) 
			{
				e.printStackTrace();
			}
		}
		return Math.max(lastCpuTime - oldTime, 0);
	}
	
	private void setBusy (boolean busy)
	{
		if (isBusy() != busy)
		{
			sandboxInfo.setBusy(busy);
			try
			{
				WorkerMain.notifyInfoUpdated();
			}
			catch (IOException e)
			{
				WorkerMain.getLogger().critical("Failed to notify Backend that the Worker changed: " + e);
			}
		}
	}
	
	public boolean isBusy ()
	{
		return sandboxInfo.isBusy();
	}
	
	public void setLangs (@NonNull Set<String> langs)
	{
		if (!langs.equals(getLangs()))
		{
			sandboxInfo.setLangs(langs);
			try
			{
				WorkerMain.notifyInfoUpdated();
			}
			catch (IOException e)
			{
				WorkerMain.getLogger().critical("Failed to notify Backend that the Worker changed: " + e);
			}
		}
	}
	
	public Set<String> getLangs ()
	{
		return sandboxInfo.getLangs();
	}
	
	/** Die UUID des aktuell in der Sandbox ausgeführten Jobs. */
	private UUID currentJob;
	
	/** Die Connection von der Sandbox zum Worker. */
	@Getter
	private WorkerConnectionHandler connection;
	
	public Sandbox (WorkerConnectionHandler connectionHandler)
	{
		if (connectionHandler.getType().getType() != SANDBOX)
			throw new IllegalArgumentException();
		connection = connectionHandler;
	}
	
	/**
	 * Schickt den Job an die Sandbox. Dabei wird vorrausgesetzt, dass die
	 * Sandbox nicht beschäftigt ist. Gibt bei Erfolg true zurück, ansonsten
	 * false.
	 */
	public synchronized boolean sendJob (SandboxCommand job) throws IOException
	{
		WorkerMain.getLogger().debug("Sende " + job);
		if (job.getCommand() == RUN_AI)
		{
			if (isBusy())
				return false;
			setBusy(true);
		}
		else if ((job.getCommand() == KILL_AI) || (job.getCommand() == TERM_AI))
			setBusy(false);
		currentJob = job.getUuid();
		connection.sendJob(job);
		return true;
	}
	
	/**
	 * Empfängt die Antwort der Sandbox.
	 */
	public synchronized void sandboxAnswer (SandboxMessage answer)
	{
		switch (answer.getEvent())
		{
			case TERMINATED_AI:
			case KILLED_AI:
			case FINISHED_AI:
				try
				{
					WorkerMain.getBackendClient().sendSandboxMessage(answer);
				}
				catch (IOException e)
				{
					WorkerMain.getLogger().critical("Fehler beim notifien des Backends (" + answer + "): " + e);
					e.printStackTrace();
				}
				setBusy(false);
				break;
			case STARTED_AI:
				WorkerMain.getLogger().todo("Hier sollte ich mir überlegen ob ich iwas notifien soll");
				setBusy(true);
				break;
			case CPU_TIME:
				WorkerMain.getLogger().debug("CPU time answer");
				lastCpuTime = answer.getCpuTime();
				WorkerMain.getLogger().debug("Habe CPU time gelesen und notify nun");
				synchronized (cpuTimeLock) {
					cpuTimeLock.notifyAll();
				}
				WorkerMain.getLogger().debug("notified");
				break;
			default:
				WorkerMain.getLogger().critical("Unknown event received:" + answer);
				break;
		}
	}
	
	/**
	 * Wird aufgerufen, wenn sich die Sandbox disconnected hat.
	 */
	public void disconnected ()
	{
		sandboxAnswer(new SandboxMessage(TERMINATED_AI, currentJob));
	}
}
