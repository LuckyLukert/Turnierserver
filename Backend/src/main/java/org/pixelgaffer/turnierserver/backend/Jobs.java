package org.pixelgaffer.turnierserver.backend;

import static org.pixelgaffer.turnierserver.networking.bwprotocol.WorkerCommandAnswer.SUCCESS;
import static org.pixelgaffer.turnierserver.networking.messages.WorkerCommand.COMPILE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import org.pixelgaffer.turnierserver.Parsers;
import org.pixelgaffer.turnierserver.backend.server.BackendFrontendConnectionHandler;
import org.pixelgaffer.turnierserver.backend.server.message.BackendFrontendCommand;
import org.pixelgaffer.turnierserver.backend.server.message.BackendFrontendCommandProcessed;
import org.pixelgaffer.turnierserver.backend.server.message.BackendFrontendResult;
import org.pixelgaffer.turnierserver.networking.bwprotocol.WorkerCommandAnswer;
import org.pixelgaffer.turnierserver.networking.messages.WorkerCommand;

/**
 * Diese Klasse speichert Informationen zu den aktuell ausgeführten
 * Kompilierungsaufträgen.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Jobs
{
	/** Die Liste mit Befehlen die noch nicht processed wurden. */
	private static final List<BackendFrontendCommand> pending = new ArrayList<>();
	
	/** Die Liste aller Jobs. */
	private static final List<Job> jobs = new ArrayList<>();
	
	/** Die Map mit den UUIDs und den zugehörigen Jobs. */
	private static final Map<UUID, Job> jobUuids = new HashMap<>();
	
	/** Die Map mit den Request IDs und den zugehörigen Jobs. */
	private static final Map<Integer, Job> jobRequestIds = new HashMap<>();
	
	/**
	 * Schreibt alle aktuell bekannten Jobs in die angegebene Datei.
	 */
	public static void storeJobs (File file) throws IOException
	{
		PrintStream out = new PrintStream(file);
		synchronized (jobs)
		{
			for (Job job : jobs)
			{
				out.println(Parsers.getParser(false).parse(job.getFrontendCommand(), false));
			}
		}
		synchronized (pending)
		{
			for (BackendFrontendCommand cmd : pending)
			{
				out.println(Parsers.getParser(false).parse(cmd, false));
			}
		}
		out.close();
	}
	
	/**
	 * Liest alle gespeicherten Jobs ein und processet diese.
	 */
	public static void restoreJobs (File file) throws IOException
	{
		BufferedReader in = new BufferedReader(new FileReader(file));
		String line;
		while ((line = in.readLine()) != null)
		{
			BackendFrontendCommand cmd = Parsers.getParser(false).parse(line.getBytes(), BackendFrontendCommand.class);
			BackendMain.getLogger().info("Job wiederhergestellt: " + cmd);
			processCommand(cmd);
		}
		in.close();
	}
	
	/**
	 * Gibt die RequestId des Jobs mit der angegebenen UUID zurück.
	 * 
	 * @throws NullPointerException Wenn kein solcher Job gefunden wurde.
	 */
	public static final int findRequestId (UUID uuid)
	{
		return jobUuids.get(uuid).getRequestId();
	}
	
	/**
	 * Gibt die UUID des Jobs mit der angegebenen RequestId zurück.
	 * 
	 * @throws NullPointerException Wenn kein solcher Job gefunden wurde.
	 */
	public static final UUID findUuid (int requestId)
	{
		return jobRequestIds.get(requestId).getUuid();
	}
	
	/**
	 * Fügt den Job zur Liste der Jobs hinzu.
	 */
	private static void addJob (@NonNull Job job)
	{
		synchronized (jobs)
		{
			jobs.add(job);
			jobUuids.put(job.getUuid(), job);
			jobRequestIds.put(job.getRequestId(), job);
		}
	}
	
	/**
	 * Verarbeitet den angegebenen Command und startet dafür die nötigen Jobs.
	 * Diese Methode startet dafür einen neuen Thread und arbeitet somit
	 * asynchron.
	 */
	public static void processCommand (@NonNull BackendFrontendCommand cmd)
	{
		new Thread( () -> {
			synchronized (pending)
			{
				pending.add(cmd);
			}
			
			if (cmd.getAction().equals("compile"))
			{
				try
				{
					WorkerConnection worker = Workers.getCompilableWorker();
					WorkerCommand wcmd = worker.compile(cmd.getId(), cmd.getLanguage(), cmd.getGametype());
					Job job = new Job(wcmd, cmd, worker);
					addJob(job);
					BackendFrontendConnectionHandler.getFrontend().sendMessage(
							Parsers.getFrontend()
									.parse(new BackendFrontendCommandProcessed(cmd.getRequestid()), false));
				}
				catch (Exception e)
				{
					e.printStackTrace();
					BackendFrontendResult result = new BackendFrontendResult(cmd.getRequestid(), false, e);
					try
					{
						BackendFrontendConnectionHandler.getFrontend().sendMessage(
								Parsers.getFrontend().parse(result, false));
					}
					catch (IOException e1)
					{
						e1.printStackTrace();
					}
				}
			}
				else if (cmd.getAction().equals("start"))
				{
					try
					{
						Games.startGame(cmd.getGametype(), cmd.getRequestid(), cmd.getLanguages(), cmd.getAis());
						BackendFrontendConnectionHandler.getFrontend().sendMessage(
								Parsers.getFrontend().parse(
										new BackendFrontendCommandProcessed(cmd.getRequestid()), false));
					}
					catch (Exception e)
					{
						e.printStackTrace();
						BackendFrontendResult result = new BackendFrontendResult(cmd.getRequestid(), false, e);
						try
						{
							BackendFrontendConnectionHandler.getFrontend().sendMessage(
									Parsers.getFrontend().parse(result, false));
						}
						catch (IOException e1)
						{
							e1.printStackTrace();
						}
					}
				}
				else if (cmd.getAction().equals("qualify"))
				{
					try
					{
						Games.startQualifyGame(cmd.getGametype(), cmd.getRequestid(), cmd.getLanguage(), cmd.getId());
						BackendFrontendConnectionHandler.getFrontend().sendMessage(
								Parsers.getFrontend().parse(
										new BackendFrontendCommandProcessed(cmd.getRequestid()), false));
					}
					catch (Exception e)
					{
						e.printStackTrace();
						BackendFrontendResult result = new BackendFrontendResult(cmd.getRequestid(), false, e);
						try
						{
							BackendFrontendConnectionHandler.getFrontend().sendMessage(
									Parsers.getFrontend().parse(result, false));
						}
						catch (IOException e1)
						{
							e1.printStackTrace();
						}
					}
				}
				else
					BackendMain.getLogger().critical("Unknown action from Frontend: " + cmd.getAction());
				
				synchronized (pending)
				{
					pending.remove(cmd);
				}
				
			}).start();
	}
	
	/**
	 * Muss aufgerufen werden, wenn ein Job fertig ist. Entfernt den Job aus der
	 * Liste und benachrichtigt das Frontend.
	 */
	public static void jobFinished (@NonNull WorkerCommandAnswer answer) throws IOException
	{
		UUID uuid = answer.getUuid();
		Job job = jobUuids.get(uuid);
		if (job == null)
		{
			BackendMain.getLogger().critical("Couldn't find job with UUID " + uuid);
			return;
		}
		
		if (job.getWorkerCommand().getAction() == COMPILE)
			job.getWorker().setCompiling(false);
		
		int requestId = job.getRequestId();
		BackendFrontendResult result = new BackendFrontendResult(requestId,
				answer.getWhat() == SUCCESS);
		BackendFrontendConnectionHandler.getFrontend().sendMessage(Parsers.getFrontend().parse(result, false));
		synchronized (jobs)
		{
			jobs.remove(job);
			jobUuids.remove(uuid);
			jobRequestIds.remove(requestId);
		}
	}
	
	/**
	 * Muss aufgerufen werden, wenn sich ein Worker disconnected. Alle
	 * ausstehenden Jobs des Workers werden anschließend neu gestartet.
	 */
	public static void workerDisconnected (@NonNull WorkerConnection worker)
	{
		synchronized (jobs)
		{
			for (int i = 0; i < jobs.size();)
			{
				if (jobs.get(i).getWorker().equals(worker))
				{
					processCommand(jobs.get(i).getFrontendCommand());
					jobs.remove(i);
				}
				else
					i++;
			}
		}
	}
}
