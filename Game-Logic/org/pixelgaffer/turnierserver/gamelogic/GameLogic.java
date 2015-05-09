package org.pixelgaffer.turnierserver.gamelogic;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.msgpack.MessagePack;
import org.pixelgaffer.turnierserver.backend.AiWrapper;
import org.pixelgaffer.turnierserver.backend.Game;

public abstract class GameLogic<E extends AiObject, R> {
	
	/**
	 * Das Spiel, welches von dieser GameLogic geleitet wird
	 */
	protected Game game;
	
	/**
	 * Die Art der Antwort
	 */
	private Class<R> responseType;
	/**
	 * Der Gamstate
	 */
	private Map<String, String> gamestate;
	/**
	 * Die Sachen, welche sich im Gamestate verändert haben
	 */
	private Map<String, String> changed;
	/**
	 * Die MessagePack Instanz, welche zum parsen verwendet wird
	 */
	private MessagePack msgpack;
	
	/**
	 * Der Konstruktor, MUSS verwendet werden
	 * 
	 * @param responseType Die Klasse der Response, die Empfangen werden soll
	 */
	public GameLogic(Class<R> responseType) {
		this.responseType = responseType;
		gamestate = new HashMap<>();
		changed = new HashMap<>();
	}
	
	
	/**
	 * Erstellt das Spiel, inseriert alle Keys in den Gamestate
	 */
	protected abstract void setup();
	/**
	 * Wird aufgerufen, wenn eine Nachricht empfangen wird
	 * 
	 * @param response Die Antwort der AI
	 * @param ai Die AI, welche diese Antwort gesendet hat
	 */
	protected abstract void receive(R response, AiWrapper ai);
	/**
	 * Wird aufgerufen, wenn eine AI aufgegeben hat (oder aufgegeben wurde, z.B. aufgrund illegaler Aktionen oder wenn keine Rechenpunkte mehr übrig sind)
	 * 
	 * @param ai Die AI, welche aufgegeben hat
	 */
	protected abstract void lost(AiWrapper ai);
	/**
	 * Erstellt ein neues AIWrapper Objekt
	 * 
	 * @param ai Die AI, für die das Objekt erstellt werden soll
	 * @return Das AI Objekt
	 */
	protected abstract E createUserObject(AiWrapper ai);
	
	
	/**
	 * Schickt die Änderungen des GameStates an alle AIs
	 * 
	 * @throws IOException
	 */
	protected void sendGameState() throws IOException {
		for(AiWrapper ai : game.getAis()) {
			getUserObject(ai).updateCalculationTimer();
			if(!getUserObject(ai).lost)
				ai.sendMessage(new String(msgpack.write(changed), "UTF-8"));
		}
		changed.clear();
	}
	
	/**
	 * Castet das User Object der AI (Util-Methode)
	 * 
	 * @param ai Die AI, deren Objekt gecastet werden soll
	 * @return Das gecastete User Object
	 */
	@SuppressWarnings("unchecked")
	protected E getUserObject(AiWrapper ai) {
		return (E) ai.getUserObject();
	}
	
	/**
	 * Setzt einen Schlüssel im Gamestate
	 * 
	 * @param key Der Schlüssel
	 * @param value Der Wert
	 */
	protected void set(String key, String value) {
		gamestate.put(key, value);
		changed.put(key, value);
	}
	
	/**
	 * Holt sich den Wert eines Schlüssels im Gamestate
	 * 
	 * @param key Der Schlüssel
	 * @return Der Wert
	 */
	protected String get(String key) {
		if(!gamestate.containsKey(key)) {
			set(key, "");
			return "";
		}
		return gamestate.get(gamestate);
	}
	
	/**
	 * Wird aufgerufen, wenn eine Nachricht empfangen wurde
	 * 
	 * @param message Die Nachricht
	 * @param ai Die AI, von welcher die Nachricht kommt
	 */
	public void receiveMessage(String message, AiWrapper ai) {
		if(getUserObject(ai).lost) {
			return;
		}
		if(message.equals("SURRENDER")) {
			lost(ai);
			return;
		}
		try {
			receive(msgpack.read(message.getBytes("UTF-8"), responseType), ai);
		} catch (IOException e) {
			lost(ai);
		}
	}
	
	/**
	 * Startet das Spiel
	 * 
	 * @param game Das Spiel, welches gestartet werden soll
	 */
	public void startGame(Game game) {
		this.game = game;
		for(AiWrapper ai : game.getAis()) {
			ai.setUserObject(createUserObject(ai));
			getUserObject(ai).setLogic(this);
			getUserObject(ai).setAi(ai);
		}
		setup();
	}
	
}
