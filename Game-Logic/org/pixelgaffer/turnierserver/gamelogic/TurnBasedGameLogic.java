package org.pixelgaffer.turnierserver.gamelogic;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.pixelgaffer.turnierserver.backend.AiWrapper;
import org.pixelgaffer.turnierserver.backend.Game;

public abstract class TurnBasedGameLogic<E extends AiObject, R> extends GameLogic<E, R> {
	
	/**
	 * Die AIs, deren Antworten erhalten wurden
	 */
	private Set<AiWrapper> received;
	
	public TurnBasedGameLogic(Class<R> responseType) {
		super(responseType);
		received = new HashSet<>();
	}
	
	/**
	 * Wird aufgerufen, wenn alle AIs geantwortet haben, und der Gamestate geupdated werden muss
	 * 
	 * @return Das Objekt für den renderer
	 */
	protected abstract Object update();
	
	/**
	 * Verarbeitet eine Antwort einer AI
	 * 
	 * @param message Die Antwort einer AI
	 * @param ai Die AI, welche die Antwort gesendet hat
	 */
	protected abstract void processResponse(R message, AiWrapper ai);
	
	private int updateCounter = 1;
	
	@Override
	protected void receive(R response, AiWrapper ai) {
		if(received.contains(ai)) {
			getUserObject(ai).loose();
			return;
		}
		
		getUserObject(ai).stopCalculationTimer();
		received.add(ai);
		processResponse(response, ai);
		
		if(received.size() == game.getAiCount()) {
			Object update = update();
			RenderData data = new RenderData();
			data.update = updateCounter;
			updateCounter++;
			data.data = update;
			sendToFronted(data);
			
			try {
				sendGameState();
				for(AiWrapper wrapper : game.getAis()) {
					if(!getUserObject(wrapper).lost) {
						getUserObject(wrapper).startCalculationTimer();
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			received.clear();
			for(AiWrapper wrapper : game.getAis()) {
				if(getUserObject(wrapper).lost) {
					received.add(wrapper);
				}
			}
		}
	}
	
	@Override
	public void startGame(Game game) {
		super.startGame(game);
		try {
			sendGameState();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
