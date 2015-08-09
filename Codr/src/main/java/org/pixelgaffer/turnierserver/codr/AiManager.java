package org.pixelgaffer.turnierserver.codr;


import java.io.File;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.pixelgaffer.turnierserver.codr.utilities.ErrorLog;
import org.pixelgaffer.turnierserver.codr.utilities.Paths;


/**
 * Managet das Laden der
 * - in Codr gespeicherten KIs
 * - extern gespeicherten KIs
 * - SimplePlayer-KIs
 * 
 * @author Philip
 */
public class AiManager {
	
	
	public ObservableList<AiSimple> ais = FXCollections.observableArrayList();
	
	
	/**
	 * Lädt alle Spieler aus dem Dateisystem in die Liste
	 */
	public void loadAis() {
		
		// in Codr gespeicherte KIs
		ais.clear();
		File dir = new File(Paths.aiFolder());
		dir.mkdirs();
		File[] playerDirs = dir.listFiles();
		if (playerDirs == null) {
			ErrorLog.write("keine Spieler vorhanden");
			return;
		}
		for (int i = 0; i < playerDirs.length; i++) {
			if (playerDirs[i].isDirectory())
				ais.add(new AiSaved(playerDirs[i].getName()));
		}
		
		// extern gespeicherte KIs
		File externDir = new File(Paths.aiExternFolder());
		externDir.mkdirs();
		File[] externDirs = externDir.listFiles();
		if (externDirs == null) {
			ErrorLog.write("keine Spieler vorhanden");
			return;
		}
		for (int i = 0; i < externDirs.length; i++) {
			if (externDirs[i].isDirectory())
				ais.add(new AiExtern(externDirs[i].getName()));
		}
		
		// simplePlayer, die aus dem Download-Ordner geladen werden
		File simpleDir = new File(Paths.simplePlayerFolder(MainApp.actualGameType.get()));
		simpleDir.mkdirs();
		File[] simpleDirs = simpleDir.listFiles();
		if (simpleDirs == null) {
			ErrorLog.write("keine SimplePlayer vorhanden");
			return;
		}
		for (int i = 0; i < simpleDirs.length; i++) {
			if (simpleDirs[i].isDirectory())
				ais.add(new AiSimple(simpleDirs[i].getName()));
		}
	}
	
	
}
