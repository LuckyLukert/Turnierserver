package org.pixelgaffer.turnierserver.backend;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import org.pixelgaffer.turnierserver.Parsers;
import org.pixelgaffer.turnierserver.backend.server.BackendFrontendCommand;
import org.pixelgaffer.turnierserver.backend.server.BackendFrontendCommandProcessed;
import org.pixelgaffer.turnierserver.backend.server.BackendFrontendConnectionHandler;
import org.pixelgaffer.turnierserver.backend.server.BackendFrontendResult;
import org.pixelgaffer.turnierserver.networking.bwprotocol.WorkerCommandAnswer;
import org.pixelgaffer.turnierserver.networking.messages.WorkerCommand;

/**
 * Diese Klasse speichert Informationen zu den aktuell ausgeführten Jobs.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Jobs
{
	/** Die Liste aller Jobs. */
	private static final List<Job> jobs = new ArrayList<>();
	
	/** Die Map mit den UUIDs und den zugehörigen Jobs. */
	private static final Map<UUID, Job> jobUuids = new HashMap<>();
	
	/** Die Map mit den Request IDs und den zugehörigen Jobs. */
	private static final Map<Integer, Job> jobRequestIds = new HashMap<>();
	
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
		jobs.add(job);
		jobUuids.put(job.getUuid(), job);
		jobRequestIds.put(job.getRequestId(), job);
	}
	
	/**
	 * Verarbeitet den angegebenen Command und startet dafür die nötigen Jobs.
	 * Diese Methode startet dafür einen neuen Thread und arbeitet somit
	 * asynchron.
	 */
	public static void processCommand (@NonNull BackendFrontendCommand cmd)
	{
		new Thread( () -> {
			if (cmd.getAction().equals("compile"))
			{
				try
				{
					WorkerCommand wcmd = Workers.getCompilableWorker().compile(cmd.getId(), cmd.getGametype());
					Job job = new Job(wcmd, cmd);
					addJob(job);
					BackendFrontendConnectionHandler.getFrontend().sendMessage(
							Parsers.getFrontend()
									.parse(new BackendFrontendCommandProcessed(cmd.getRequestid())));
				}
				catch (Exception e)
				{
					e.printStackTrace();
					BackendFrontendResult result = new BackendFrontendResult(cmd.getRequestid(), false, e);
					try
					{
						BackendFrontendConnectionHandler.getFrontend().sendMessage(
								Parsers.getFrontend().parse(result));
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
						Games.startGame(cmd.getGametype(), cmd.getRequestid(), cmd.getAis());
						BackendFrontendConnectionHandler.getFrontend().sendMessage(
								Parsers.getFrontend().parse(
										new BackendFrontendCommandProcessed(cmd.getRequestid())));
					}
					catch (Exception e)
					{
						e.printStackTrace();
						BackendFrontendResult result = new BackendFrontendResult(cmd.getRequestid(), false, e);
						try
						{
							BackendFrontendConnectionHandler.getFrontend().sendMessage(
									Parsers.getFrontend().parse(result));
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
						Games.startQualifyGame(cmd.getGametype(), cmd.getRequestid(), cmd.getId());
						BackendFrontendConnectionHandler.getFrontend().sendMessage(
								Parsers.getFrontend().parse(
										new BackendFrontendCommandProcessed(cmd.getRequestid())));
					}
					catch (Exception e)
					{
						e.printStackTrace();
						BackendFrontendResult result = new BackendFrontendResult(cmd.getRequestid(), false, e);
						try
						{
							BackendFrontendConnectionHandler.getFrontend().sendMessage(
									Parsers.getFrontend().parse(result));
						}
						catch (IOException e1)
						{
							e1.printStackTrace();
						}
					}
				}
				else
					BackendMain.getLogger().severe(
							"Unknown action from Frontend: " + cmd.getAction());
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
			BackendMain.getLogger().severe("Couldn't find job with UUID " + uuid);
			return;
		}
		int requestId = job.getRequestId();
		BackendFrontendResult result = new BackendFrontendResult(requestId,
				answer.getWhat() == WorkerCommandAnswer.SUCCESS);
		BackendFrontendConnectionHandler.getFrontend().sendMessage(Parsers.getFrontend().parse(result));
		jobs.remove(job);
		jobUuids.remove(uuid);
		jobRequestIds.remove(requestId);
	}
}
