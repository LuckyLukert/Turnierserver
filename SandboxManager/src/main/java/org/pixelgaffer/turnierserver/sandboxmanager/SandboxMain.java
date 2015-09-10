package org.pixelgaffer.turnierserver.sandboxmanager;

import static org.pixelgaffer.turnierserver.PropertyUtils.*;
import static java.lang.System.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import static java.nio.file.attribute.PosixFilePermission.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import lombok.Getter;

import org.pixelgaffer.turnierserver.Logger;
import org.pixelgaffer.turnierserver.networking.NetworkService;

public class SandboxMain
{
	@Getter
	private static final Logger logger = new Logger();
	
	@Getter
	private static WorkerClient client;
	
	public static final HashMap<String, String> commands = new HashMap<>();
	
	private static void searchExecutable (String execfile, String language) throws IOException
	{
		String pathes[] = getenv("PATH").split(":");
		for (String path : pathes)
		{
			File file = new File(path, execfile);
			while (Files.isSymbolicLink(file.toPath()))
				file = file.toPath().toRealPath().toFile();
			if (file.exists() && Files.isExecutable(file.toPath()))
			{
				getLogger().info(language + " gefunden: " + file.getAbsolutePath());
				commands.put(language, file.getAbsolutePath());
				return;
			}
		}
	}
	
	public static File etc;
	
	public static void main (String args[]) throws IOException
	{
		// Properties laden
		loadProperties(args.length > 0 ? args[0] : "/etc/turnierserver/turnierserver.prop");
		loadProperties(args.length > 1 ? args[1] : "/etc/turnierserver/sandbox.prop");
		
		// den Rechner nach Programmiersprachen durchsuchen
		for (int i = 0; i < getIntRequired("languages.size"); i++)
		{
			String lang = getStringRequired("languages." + i);
			String command = getString("languages." + lang, "");
			if (command.isEmpty())
			{
				getLogger().info("Erlaube native Sprache " + lang);
				commands.put(lang, "");
			}
			else
			{
				getLogger().info("Suche Sprache " + lang + " (Befehl " + command + ")");
				searchExecutable(command, lang);
			}
		}
		
		// isolate-Zeugs schreiben
		etc = Files.createTempDirectory("etc").toFile();
		Files.setPosixFilePermissions(etc.toPath(), new HashSet<>(Arrays.asList(
				OWNER_READ, OWNER_WRITE, OWNER_EXECUTE,
				GROUP_READ, GROUP_EXECUTE,
				OTHERS_READ, OTHERS_EXECUTE)));
		File passwd = new File(etc, "passwd");
		PrintWriter pw = new PrintWriter(passwd);
		for (int i = 0; i <= 99; i++)
		{
			int id = 60000 + i;
			pw.println("isolate-user-" + i + ":x:" + id + ":" + id + "::/:/usr/sbin/nologin");
		}
		pw.close();
		File etcJavaDir = new File(etc, "java-jdk8/amd64");
		etcJavaDir.mkdirs();
		File jvmCfg = new File(etcJavaDir, "jvm.cfg");
		pw = new PrintWriter(jvmCfg);
		pw.println("# GENERATED FILE - DO NOT EDIT");
		pw.println("-server KNOWN");
		pw.println("-client IGNORE");
		pw.close();
		
		// Netzwerkzeugs starten
		client = new WorkerClient();
		new Thread( () -> NetworkService.mainLoop(), "NetworkService").start();
		
		// beim beenden alle laufenden kis auch beenden
		Runtime.getRuntime().addShutdownHook(new Thread( () -> {
			
			getLogger().info("Received shutdown signal");
			client.getJobControl().shutdown();
			
		}, "SandboxMain-ShutdownHook"));
	}
}
