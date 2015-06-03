package org.pixelgaffer.turnierserver.networking;

import it.sauronsoftware.ftp4j.FTPAbortedException;
import it.sauronsoftware.ftp4j.FTPClient;
import it.sauronsoftware.ftp4j.FTPDataTransferException;
import it.sauronsoftware.ftp4j.FTPException;
import it.sauronsoftware.ftp4j.FTPFile;
import it.sauronsoftware.ftp4j.FTPIllegalReplyException;
import it.sauronsoftware.ftp4j.FTPListParseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Diese Klasse verbindet sich zum FTP Server auf dem Datastore und enthält
 * Methoden zum Abrufen der Daten.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DatastoreFtpClient
{
	private static FtpConnectionPool cons = new FtpConnectionPool();
	
	/**
	 * Verbindet mit dem Server mithilfe der Systemproperties.
	 */
	static void connect (FTPClient client) throws IOException, FTPIllegalReplyException, FTPException
	{
		if (client != null)
		{
			try
			{
				client.noop();
			}
			catch (FTPException e)
			{
				try
				{
					client.disconnect(true);
				}
				catch (Exception ex)
				{
				}
				client = null;
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		
		if (client == null)
			client = new FTPClient();
		if (!client.isConnected())
		{
			// zum Server verbinden
			client.connect(System.getProperty("turnierserver.datastore.host"),
					Integer.parseInt(System.getProperty("turnierserver.datastore.port")));
			// beim Server anmelden
			System.out.println("logging in …");
			String username = System.getProperty("turnierserver.datastore.username");
			String password = System.getProperty("turnierserver.datastore.password");
			client.login(username, password);
			System.out.println("logged in");
			// passiven Modus benutzen (Firewall …)
			client.setPassive(true);
		}
		
	}
	
	private static String aiPath (int id)
	{
		return "AIs/" + id;
	}
	
	private static String aiBinPath (int id)
	{
		return aiPath(id) + "/bin";
	}
	
	private static String aiSourcePath (int id, int version)
	{
		return aiPath(id) + "/v" + version;
	}
	
	/**
	 * Speichert den Remote-File im OutputStream local.
	 */
	private static void retrieveFile (String remote, OutputStream local, FtpConnection con)
			throws IOException, FTPIllegalReplyException, FTPException, IllegalStateException,
			FTPDataTransferException, FTPAbortedException
	{
		boolean conWasNull = con == null;
		if (conWasNull)
			con = cons.getClient();
		FTPClient client = con.getFtpClient();
		
		client.download(remote, local, 0, null);
		
		if (conWasNull)
			con.setBusy(false);
		
		local.close();
	}
	
	/**
	 * Speichert den Remote-File in local.
	 */
	private static void retrieveFile (String remote, File local, FtpConnection con)
			throws IOException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException
	{
		boolean conWasNull = con == null;
		if (conWasNull)
			con = cons.getClient();
		FTPClient client = con.getFtpClient();
		
		client.download(remote, local);
		
		if (conWasNull)
			con.setBusy(false);
	}
	
	/**
	 * Speichert alles im Remote-Directory im local-Verzeichnis.
	 */
	public static void retrieveDir (String remote, File local, FtpConnection con)
			throws IOException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException,
			FTPListParseException
	{
		boolean conWasNull = con == null;
		if (conWasNull)
			con = cons.getClient();
		FTPClient client = con.getFtpClient();
		
		System.out.println("retrieveDir: " + remote);
		String cwd = client.currentDirectory();
		client.changeDirectory(remote);
		local.mkdirs();
		for (FTPFile f : client.list())
		{
			if (f.getType() == FTPFile.TYPE_FILE)
				retrieveFile(f.getName(), new File(local, f.getName()), con);
			else if (f.getType() == FTPFile.TYPE_DIRECTORY)
				retrieveDir(f.getName(), new File(local, f.getName()), con);
		}
		client.changeDirectory(cwd);
		
		if (conWasNull)
			con.setBusy(false);
	}
	
	/**
	 * Speichert das tar-bzip2-Archiv mit den compilierten Daten der KI im
	 * OutputStream local.
	 */
	public static void retrieveAi (int id, int version, OutputStream local)
			throws IllegalStateException, IOException, FTPIllegalReplyException, FTPException,
			FTPDataTransferException, FTPAbortedException
	{
		retrieveFile(aiBinPath(id) + "/v" + version + ".tar.bz2", local, null);
	}
	
	/**
	 * Speichert das tar-bzip2-Archiv mit den compilierten Daten der KI in
	 * local.
	 */
	public static void retrieveAi (int id, int version, File local)
			throws IOException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException
	{
		retrieveFile(aiBinPath(id) + "/v" + version + ".tar.bz2", local, null);
	}
	
	/**
	 * Speichert alle Dateien im Source-Folder der KI in einem temporären
	 * Verzeichnis und gibt dieses zurück.
	 */
	public static File retrieveAiSource (int id, int version)
			throws IOException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException,
			FTPListParseException
	{
		File tmp = Files.createTempDirectory("ai").toFile();
		retrieveDir(aiSourcePath(id, version), tmp, null);
		return tmp;
	}
	
	/**
	 * Gibt die Sprache der KI zurück.
	 */
	public static String retrieveAiLanguage (int id)
			throws IOException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException
	{
		File tmp = Files.createTempFile("lang", ".txt").toFile();
		retrieveFile(aiPath(id) + "/language.txt", tmp, null);
		
		StringBuilder lang = new StringBuilder();
		Reader in = new InputStreamReader(new FileInputStream(tmp));
		char buf[] = new char[8192];
		int read;
		while ((read = in.read(buf)) > 0)
			lang.append(new String(buf, 0, read));
		in.close();
		
		return lang.toString().trim();
	}
	
	/**
	 * Speichert das jar-Archiv der Gamelogic im OutputStream local.
	 */
	public static void retrieveGameLogic (int game, File local)
			throws IOException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException
	{
		retrieveFile("Games/" + game + "/Logic.jar", local, null);
	}
	
	/**
	 * Speichert die AiLibrary der angegebenen Sprache im Verzeichnis local.
	 */
	public static void retrieveAiLibrary (int game, String language, File local)
			throws IOException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException,
			FTPListParseException
	{
		retrieveDir("Games/" + game + "/" + language + "/ailib", local, null);
	}
	
	/**
	 * Speichert die jar-Archive der Bibliothek im Verzeichnis local.
	 */
	public static void retrieveLibrary (String lib, String language, File local)
			throws IOException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException,
			FTPListParseException
	{
		retrieveDir("Libraries/" + language + "/" + lib, local, null);
	}
	
	/**
	 * Lädt den Inhalt des InputStreams local nach remote.
	 */
	private static void storeFile (String remote, InputStream local, FtpConnection con)
			throws IOException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException
	{
		boolean conWasNull = con == null;
		if (conWasNull)
			con = cons.getClient();
		FTPClient client = con.getFtpClient();
		
		System.out.println("storeFile: " + client.currentDirectory() + " / " + remote);
		client.upload(remote, local, 0, 0, null);
		
		if (conWasNull)
			con.setBusy(false);
		
		local.close();
	}
	
	/**
	 * Lädt den Inhalt des InputStreams local als Binary-tar-bz2-Archiv der KI
	 * hoch.
	 */
	public static void storeAi (int ai, int version, InputStream local)
			throws IOException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException
	{
		storeFile(aiBinPath(ai) + "/v" + version + ".tar.bz2", local, null);
	}
	
	/**
	 * Lädt die Ausgabe des Kompilierungsprozess der KI hoch.
	 */
	public static void storeAiCompileOutput (int aiId, int version, File local)
			throws IOException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException
	{
		storeFile(aiBinPath(aiId) + "/v1-compile.out", new FileInputStream(local), null);
	}
	
	/**
	 * Gibt die Größe der angegebenen Datei zurück.
	 */
	private static long fileSize (String remote, FtpConnection con)
			throws IOException, FTPIllegalReplyException, FTPException
	{
		boolean conWasNull = con == null;
		if (conWasNull)
			con = cons.getClient();
		FTPClient client = con.getFtpClient();
		
		long retval = client.fileSize(remote);
		
		if (conWasNull)
			con.setBusy(false);
		
		return retval;
	}
	
	/**
	 * Gibt die Größe des tar-bzip2-Archivs mit den compilierten Daten der KI
	 * zurück.
	 */
	public static long aiSize (int id, int version)
			throws IOException, FTPIllegalReplyException, FTPException
	{
		return fileSize(aiBinPath(id) + "/v" + version + ".tar.bz2", null);
	}
}
