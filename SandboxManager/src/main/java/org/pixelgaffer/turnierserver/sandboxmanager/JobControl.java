package org.pixelgaffer.turnierserver.sandboxmanager;

import static org.pixelgaffer.turnierserver.sandboxmanager.SandboxMain.getLogger;

import java.util.Deque;
import java.util.LinkedList;
import java.util.UUID;

import lombok.Getter;

public class JobControl
{
	@Getter
	private boolean active = true;
	
	private final Deque<Job> queue = new LinkedList<>();
	@Getter
	private AiExecutor current = null;
	
	/**
	 * Shuts this JobControl down. Needs to be called before the system exits.
	 */
	public void shutdown ()
	{
		getLogger().info("Shutting down the JobControll");
		active = false;
		synchronized (queue)
		{
			queue.clear();
			if (current != null)
				current.terminateAi();
		}
	}
	
	public void doJob (Job job)
	{
		current = new AiExecutor(job);
		new Thread(current, "AiExecutor-Thread").start();
	}
	
	public void addJob (Job job)
	{
		synchronized (queue)
		{
			if (current == null)
				doJob(job);
			else
				queue.offerLast(job);
		}
	}
	
	public void terminateJob (UUID uuid)
	{
		synchronized (queue)
		{
			if ((current != null) && (current.getJob().getUuid() == uuid))
				current.terminateAi();
			else
				while (queue.remove(new Job(uuid)))
					;
		}
	}
	
	public void killJob (UUID uuid)
	{
		synchronized (queue)
		{
			if ((current != null) && (current.getJob().getUuid() == uuid))
				current.killAi();
			else
				while (queue.remove(new Job(uuid)))
					;
		}
	}
	
	public void jobFinished (UUID uuid)
	{
		if (current.getJob().getUuid() == uuid)
		{
			if (queue.isEmpty())
			{
				getLogger().debug("Habe aktuell keinen Auftrag, setze current auf null");
				current = null;
			}
			else
				doJob(queue.pollFirst());
		}
		else
			getLogger().warning("Aktueller Job ist " + current.getJob().getUuid() + " aber " + uuid + " wurde beendet");
	}
}
