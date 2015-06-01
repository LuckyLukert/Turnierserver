package org.pixelgaffer.turnierserver.esu.utilities;

import java.io.File;
import java.io.FileInputStream;

import javafx.scene.image.Image;

import org.pixelgaffer.turnierserver.esu.Game;
import org.pixelgaffer.turnierserver.esu.Player;
import org.pixelgaffer.turnierserver.esu.Version;
import org.pixelgaffer.turnierserver.esu.Player.Language;

public class Resources {
	
	/**
	 * Gibt den Pfad zum Spieler-Ordner zur�ck
	 */
	public static String playerFolder(){
		return "Players";
	}
	/**
	 * Gibt den Pfad zum Spiele-Ordner zur�ck
	 */
	public static String gameFolder(){
		return "Games";
	}
	
	/**
	 * Gibt den Pfad zum Ordner eines bestimmten Spiels zur�ck
	 */
	public static String game(Game game){
		return gameFolder() + "/" + game.ID;
	}
	/**
	 * Gibt den Pfad zu den Properties eines bestimmten Spiels zur�ck
	 */
	public static String gameProperties(Game game){
		return gameFolder() + "/" + game.ID + "/properties.txt";
	}
	
	/**
	 * Gibt den Pfad zum Ordner eines bestimmten Spielers zur�ck
	 */
	public static String player(Player player){
		return playerFolder() + "/" + player.title;
	}
	/**
	 * Gibt den Pfad zu den Properties eines Spielers zur�ck
	 */
	public static String playerProperties(Player player){
		return playerFolder() + "/" + player.title + "/properties.txt";
	}
	/**
	 * Gibt den Pfad zum Bild eines Spielers zur�ck
	 */
	public static String playerPicture(Player player){
		return playerFolder() + "/" + player.title + "/picture.png";
	}

	/**
	 * Gibt den Pfad zu einer bestimmten Version zur�ck
	 */
	public static String version(Version version){
		return playerFolder() + "/" + version.player.title + "/v" + version.number;
	}
	/**
	 * Gibt den Pfad zu einer bestimmten Version zur�ck
	 */
	public static String version(Player player, int number){
		return playerFolder() + "/" + player.title + "/v" + number;
	}
	/**
	 * Gibt den Pfad zu den Properties einer Version zur�ck
	 */
	public static String versionProperties(Version version){
		return playerFolder() + "/" + version.player.title + "/v" + version.number + "/properties.txt";
	}
	/**
	 * Gibt den Pfad zu den Properties einer Version zur�ck
	 */
	public static String versionProperties(Player player, int number){
		return playerFolder() + "/" + player.title + "/v" + number + "/properties.txt";
	}

	/**
	 * Gibt den Pfad zum SimplePlayer einer Sprache zur�ck
	 */
	public static String simplePlayer(Language language){
		return "Downloads/SimplePlayer/" + language.toString();
	}

	/**
	 * Gibt das Default-Bild f�r die KIs zur�ck
	 */
	public static Image defaultPicture(){
		try {
			return new Image(Resources.class.getResourceAsStream("default_ai.png"));
		} catch (Exception ex) {
			ErrorLog.write("Default-Bild konnte nicht geladen werden.");
			return null;
		}
	}

	/**
	 * Gibt das Default-Bild f�r die KIs zur�ck
	 */
	public static Image imageFromFile(File file){
		try {
			return imageFromFile(file.getPath());
		} catch (Exception e) {
			return null;
		}
	}
	/**
	 * Gibt das Bild, das an der �bergebenen Stelle gespeichert ist, zur�ck
	 */
	public static Image imageFromFile(String path){
		try {
			FileInputStream fin = new FileInputStream(path);
			Image img = new Image(fin);
			fin.close();
			return img;
		} catch (Exception e) {
			return null;
		}
	}
	
	
}
