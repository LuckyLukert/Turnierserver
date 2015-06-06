package org.pixelgaffer.turnierserver.codr;


import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;

import org.json.JSONArray;
import org.json.JSONObject;
import org.pixelgaffer.turnierserver.codr.utilities.ErrorLog;
import org.pixelgaffer.turnierserver.codr.utilities.Paths;
import org.pixelgaffer.turnierserver.codr.utilities.Resources;
import org.pixelgaffer.turnierserver.codr.utilities.WebConnector;



public class CodrAi {
	
	public final String title;
	public final AiMode mode;
	public String gametype;
	public String userName = "";
	public String language;
	public String description = "(keine Beschreibung)";
	public String elo = "leere Elo";
	public int id;
	public ObjectProperty<Image> onlinePicture = new SimpleObjectProperty<Image>();
	public ObservableList<Version> versions = FXCollections.observableArrayList();
	
	
	public static enum AiMode {
		saved, online, simplePlayer
	}
	
	public static enum NewVersionType {
		fromFile, simplePlayer, lastVersion
	}
	
	
	/**
	 * Erstellt eine neue Online-Ai aus einem JSONObject
	 * 
	 * @param json das übergebene JSONObject
	 * @throws
	 */
	public CodrAi(JSONObject json, WebConnector connector) {
		title = json.getString("name");
		mode = AiMode.online;
		userName = json.getString("author");
		description = json.getString("description");
		gametype = json.getJSONObject("gametype").getString("name");
		language = json.getJSONObject("lang").getString("name");
		id = json.getInt("id");
		JSONArray versions = json.getJSONArray("versions");
		for (int i = 0; i < versions.length(); i++) {
			newVersion(versions.getJSONObject(i));
		}
		
		
		Task<Image> updateC = new Task<Image>() {
			public Image call() {
				try {
					Image img = connector.getImage(json.getInt("id"));
					return img;
				} catch (IOException e) {
					return null;
				}
			}
		};
		
		updateC.valueProperty().addListener((observableValue, oldValue, newValue) -> {
			if (newValue != null)
				setPicture(newValue);
			else
				ErrorLog.write("ERROR: Konnte das Bild der AI " + json.getString("name") + " nicht laden.");
		});
		
		
		
		Thread thread = new Thread(() -> loadPicture(json, connector), "Image Loader");
		thread.setDaemon(true);
		thread.start();
	}
	
	
	public CodrAi() {
		title = "<Neue KI>";
		mode = AiMode.online;
	}
	
	
	/**
	 * Erstellt eine neue Ai
	 * 
	 * @param tit der übergebene Titel
	 */
	public CodrAi(String tit, AiMode mmode) {
		title = tit;
		mode = mmode;
		gametype = MainApp.actualGameType.get();
		if (mode == AiMode.saved || mode == AiMode.simplePlayer) {
			loadProps();
			loadVersions();
		}
	}
	
	
	/**
	 * Speichert eine neue Ai mit dem übergebenen Titel und der Sprache ab.
	 * 
	 * @param tit der übergebene Titel
	 * @param lang die übergebene Sprache
	 */
	public CodrAi(String tit, String lang) {
		title = tit;
		language = lang;
		mode = AiMode.saved;
		gametype = MainApp.actualGameType.get();
		
		File dir = new File(Paths.ai(this));
		if (!dir.mkdirs()) {
			ErrorLog.write("Der Spieler existiert bereits.");
			description = "invalid";
		} else {
			storeProps();
		}
	}
	
	
	private void loadPicture(JSONObject json, WebConnector connector) {
		
	}
	
	
	/**
	 * gibt die neueste Version oder null zurück
	 * 
	 * @return gibt null zurück, wenn es keine Version gibt
	 */
	public Version lastVersion() {
		if (versions.size() > 0) {
			return versions.get(versions.size() - 1);
		} else {
			return null;
		}
	}
	
	
	/**
	 * Fügt eine neue Version der Versionsliste hinzu.
	 * 
	 * @param type die Art, in der die Version hinzugefügt werden soll
	 * @return die Version, die hinzugefügt wurde
	 */
	public Version newVersion(NewVersionType type) {
		if (type == NewVersionType.fromFile) {
			return null;
		}
		return newVersion(type, "");
	}
	
	
	/**
	 * Fügt eine neue Version, die mit JSON erstellt wurde, der Versionsliste hinzu.
	 * 
	 * @param json hieraus wird die Version erstellt
	 * @return die Version, die erstellt wurde
	 */
	public Version newVersion(JSONObject json) {
		Version version = new Version(this, json.getInt("id") - 1, json);
		versions.add(version);
		return version;
	}
	
	
	/**
	 * Fügt eine neue Version der Versionsliste hinzu.
	 * 
	 * @param type die Art, in der die Version hinzugefügt werden soll
	 * @param path der Pfad, von dem die Version kopiert werden soll, falls type==fromFile
	 * @return die Version, die hinzugefügt wurde
	 */
	public Version newVersion(NewVersionType type, String path) {
		Version version = null;
		switch (type) {
		case fromFile:
			version = new Version(this, versions.size(), path);
			break;
		case lastVersion:
			if (versions.size() == 0) {
				return null;
			}
			version = new Version(this, versions.size(), Paths.version(this, versions.size() - 1));
			break;
		case simplePlayer:
			version = new Version(this, versions.size(), mode);
			break;
		}
		versions.add(version);
		storeProps();
		return version;
	}
	
	
	
	/**
	 * Lädt aus dem Dateiverzeichnis die Eigenschaften des Players.
	 */
	public void loadProps() {
		if (mode != AiMode.saved && mode != AiMode.simplePlayer) {
			ErrorLog.write("dies ist kein lesbares Objekt (AI.loadProps)");
			return;
		}
		try {
			Reader reader = new FileReader(Paths.aiProperties(this));
			Properties prop = new Properties();
			prop.load(reader);
			reader.close();
			gametype = prop.getProperty("gametype");
			description = prop.getProperty("description");
			language = prop.getProperty("language");
		} catch (IOException e) {
			ErrorLog.write("Fehler bei Laden aus der properties.txt");
		}
	}
	
	
	/**
	 * Speichert die Eigenschaften des Players in das Dateiverzeichnis.
	 */
	public void storeProps() {
		if (mode != AiMode.saved) {
			ErrorLog.write("dies ist kein speicherbares Objekt (AI.storeProps)");
			return;
		}
		
		Properties prop = new Properties();
		prop.setProperty("description", description);
		prop.setProperty("versionAmount", "" + versions.size());
		prop.setProperty("language", language.toString());
		prop.setProperty("gametype", gametype + "");
		
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
	public void loadVersions() {
		versions.clear();
		int versionAmount = 0;
		try {
			Reader reader = new FileReader(Paths.aiProperties(this));
			Properties prop = new Properties();
			prop.load(reader);
			reader.close();
			versionAmount = Integer.parseInt(prop.getProperty("versionAmount"));
		} catch (IOException e) {
			ErrorLog.write("Fehler bei Laden aus der properties.txt (loadVerions)");
		}
		for (int i = 0; i < versionAmount; i++) {
			versions.add(new Version(this, i, mode));
		}
	}
	
	
	/**
	 * Setzt die Ai-Beschreibung.
	 * 
	 * @param des die Beschreibung des Players
	 */
	public void setDescription(String des) {
		description = des;
		storeProps();
	}
	
	
	/**
	 * Gibt das gespeicherte Bild des Spielers zurück.
	 * 
	 * @return das gespeicherte Bild
	 */
	public ObjectProperty<Image> getPicture() {
		if (mode == AiMode.online) {
			if (onlinePicture != null) {
				return onlinePicture;
			} else {
				return new SimpleObjectProperty<>(Resources.defaultPicture());
			}
		}
		ObjectProperty<Image> img = new SimpleObjectProperty<>(Resources.imageFromFile(Paths.aiPicture(this)));
		if (img.get() == null) {
			img = new SimpleObjectProperty<>(Resources.defaultPicture());
		}
		return img;
	}
	
	
	/**
	 * Speichert das Bild des Spielers in der Datei picture.png.
	 * 
	 * @param img das zu speichernde Bild
	 */
	public void setPicture(Image img) {
		if (mode == AiMode.online) {
			onlinePicture.set(img);
		} else {
			try {
				if (img == null) {
					File file = new File(Paths.aiPicture(this));
					file.delete();
				} else {
					ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", new File(Paths.aiPicture(this)));
				}
			} catch (IOException e) {
				ErrorLog.write("Bild konnte nicht gespeichert werden.");
			}
		}
	}
	
	
	/**
	 * damit die Ai-Liste richtig angezeigt wird
	 */
	public String toString() {
		return title;
	}
	
}
