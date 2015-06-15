package org.pixelgaffer.turnierserver.codr;


import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;

import org.pixelgaffer.turnierserver.codr.utilities.Dialog;
import org.pixelgaffer.turnierserver.codr.utilities.ErrorLog;
import org.pixelgaffer.turnierserver.codr.utilities.Paths;



public class AiExtern extends AiSaved {
	
	public String path;
	
	
	public AiExtern(String ttitle) {
		super(ttitle, AiMode.extern);
		loadProps();
		loadVersions();
	}
	
	
	public AiExtern(String ttitle, String lang, String ppath) {
		super(ttitle, AiMode.extern);
		path = ppath;
		
		language = lang;
		
		File dir = new File(Paths.ai(this));
		if (!dir.mkdirs()) {
			Dialog.error("Der Spieler existiert bereits.");
			description = "invalid";
		} else {
			storeProps();
		}
		
		versions.add(new Version(this, 0, mode, path));
		
	}
	
	
	
	/**
	 * Lädt aus dem Dateiverzeichnis die Eigenschaften des Players.
	 */
	@Override public void loadProps() {
		try {
			Reader reader = new FileReader(Paths.aiProperties(this));
			Properties prop = new Properties();
			prop.load(reader);
			reader.close();
			gametype = prop.getProperty("gametype");
			description = prop.getProperty("description");
			language = prop.getProperty("language");
			path = prop.getProperty("path");
			
		} catch (IOException e) {
			ErrorLog.write("Fehler bei Laden aus der properties.txt");
		}
	}
	
	
	
	/**
	 * Speichert die Eigenschaften des Players in das Dateiverzeichnis.
	 */
	@Override public void storeProps() {
		
		Properties prop = new Properties();
		prop.setProperty("description", description);
		prop.setProperty("language", language.toString());
		prop.setProperty("gametype", gametype + "");
		prop.setProperty("path", path);
		
		try {
			Writer writer = new FileWriter(Paths.aiProperties(this));
			prop.store(writer, title);
			writer.close();
		} catch (IOException e) {
			ErrorLog.write("Es kann keine Properties-Datei angelegt werden. (Ai)");
		}
	}
	
	
	
	/**
	 * Lädt die Versionen aus dem Dateisystem, mit Hilfe der versionAmount-Property
	 */
	@Override public void loadVersions() {
		versions.clear();
		versions.add(new Version(this, 0, AiMode.extern));
	}
	
	
	
	@Override public Version newVersion(NewVersionType type) {
		return null;
	}
	
	
	@Override public Version newVersion(NewVersionType type, String path) {
		return null;
	}
	
	
	
}
