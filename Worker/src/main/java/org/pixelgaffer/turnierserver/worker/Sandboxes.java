/*
 * Sandboxes.java
 *
 * Copyright (C) 2015 Pixelgaffer
 *
 * This work is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or any later
 * version.
 *
 * This work is distributed in the hope that it will be useful, but without
 * any warranty; without even the implied warranty of merchantability or
 * fitness for a particular purpose. See version 2 and version 3 of the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.pixelgaffer.turnierserver.worker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.pixelgaffer.turnierserver.Airbrake;
import org.pixelgaffer.turnierserver.networking.messages.SandboxCommand;
import static org.pixelgaffer.turnierserver.PropertyUtils.*;

/**
 * Diese Klasse speichert alle verbundenen Sandboxen.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Sandboxes
{
	private static final List<Sandbox> sandboxes = new ArrayList<>();
	
	public static final Map<UUID, Sandbox> sandboxJobs = new HashMap<>();
	
	private static final Set<Integer> isolateBoxids = new HashSet<>();
	private static final Map<UUID, Integer> jobBoxids = new HashMap<>(); 
	private static final Random rnd = new Random();
	
	private static int isolateMin = -1, isolateMax = -1;
	/** Gibt eine freie Boxid für isolate zurück. */
	public static int nextIsolateBoxid (UUID uuid)
	{
		WorkerMain.getLogger().todo("Soll diese Methode blockieren?");
		if (isolateMin == -1)
			isolateMin = getInt("isolate.id.min", 0);
		if (isolateMax == -1)
			isolateMax = getInt("isolate.id.max", 99);
		while (true)
		{
			int boxid = rnd.nextInt(isolateMax - isolateMin) + isolateMin;
			WorkerMain.getLogger().info("Next isolate id: " + boxid + " (<= " + isolateMin + " >= " + isolateMax + ")");
			synchronized (isolateBoxids)
			{
				if (!isolateBoxids.contains(boxid))
				{
					isolateBoxids.add(boxid);
					jobBoxids.put(uuid, boxid);
					return boxid;
				}
			}
		}
	}
	/** Markiert die zum Job gehörende Boxid von isolate als verfügbar. */
	public static void releaseIsolateBoxid (@NonNull UUID uuid)
	{
		synchronized (isolateBoxids)
		{
			if (!jobBoxids.containsKey(uuid))
			{
				WorkerMain.getLogger().warning("Trying to release isolate boxid of unknown job " + uuid);
				return;
			}
			int boxid = jobBoxids.get(uuid);
			WorkerMain.getLogger().info("Release isolate id " + boxid);
			isolateBoxids.remove(boxid);
			jobBoxids.remove(uuid);
		}
	}
	
	/**
	 * Fügt die Sandbox zur Liste hinzu.
	 */
	public static boolean addSandbox (Sandbox sandbox)
	{
		boolean success;
		synchronized (sandboxes)
		{
			success = sandboxes.add(sandbox);
		}
		WorkerMain.workerInfo.getSandboxes().add(sandbox.getSandboxInfo());
		try
		{
			WorkerMain.notifyInfoUpdated();
		}
		catch (IOException e)
		{
			Airbrake.log(e).printStackTrace();
		}
		return success;
	}
	
	/**
	 * Entfernt die Sandbox aus der Liste. Ruft außerdem
	 * {@link Sandbox#disconnected()} auf.
	 */
	public static boolean removeSandbox (Sandbox sandbox)
	{
		WorkerMain.getLogger().info("Die Sandbox " + sandbox + " hat sich disconnected");
		sandbox.disconnected();
		boolean success;
		synchronized (sandboxes)
		{
			success = sandboxes.remove(sandbox);
		}
		WorkerMain.workerInfo.getSandboxes().remove(sandbox.getSandboxInfo());
		try
		{
			WorkerMain.notifyInfoUpdated();
		}
		catch (IOException e)
		{
			Airbrake.log(e).printStackTrace();
		}
		return success;
	}
	
	/**
	 * Schickt den Job an eine Sandbox und gibt diese zurück. Wenn keine Sandbox
	 * unbeschäftigt ist, wird null zurückgegeben. DIESE METHODE WARTET NICHT
	 * AUF EINE FREIE SANDBOX!
	 */
	public static Sandbox send (SandboxCommand job) throws IOException
	{
		synchronized (sandboxes)
		{
			for (Sandbox s : sandboxes)
			{
				if (s.getLangs().contains(job.getLang()) && !s.isBusy())
				{
					if (s.sendJob(job))
						return s;
				}
			}
		}
		return null;
	}
}
