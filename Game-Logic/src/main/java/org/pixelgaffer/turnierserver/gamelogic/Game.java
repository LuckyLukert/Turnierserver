package org.pixelgaffer.turnierserver.gamelogic;

import java.util.List;

public interface Game {
	
	/**
	 * Gibt die Liste mitden diesem Spiel angehörigen Ais zurück
	 * 
	 * @return Die diesem Spiel zugehörigen Ais
	 */
	public List<Ai> getAis();
	/**
	 * Gibt das Frontend zurück
	 * 
	 * @return Das Frontend
	 */
	public Frontend getFrontend();
	/**
	 * Disconnected alle Ais. Falls eine Ai schon disconnected wurde, passiert mit dieser nichts.
	 */
	public void finishGame();
	
}
