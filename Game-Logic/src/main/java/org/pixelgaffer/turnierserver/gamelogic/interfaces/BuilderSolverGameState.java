package org.pixelgaffer.turnierserver.gamelogic.interfaces;

import org.pixelgaffer.turnierserver.gamelogic.messages.BuilderSolverChange;

/**
 * @param <C> Das Veränderungsobjekt, welches an die Ai gesendet werden soll
 * @param <B> Die BuildResponse
 * @param <B> Die SolveResponse
 */
public interface BuilderSolverGameState<C, B, S> {
	
	public class Response<C> {
		/**
		 * True wenn die Antwort der Ai valide war
		 */
		public boolean valid;
		/**
		 * True wenn die Aufgabe mit der Antwort abgeschlossen ist
		 */
		public boolean finished;
		/**
		 * Die Änderungen, die während diesem Schritt vorgenommen wurden. Null, wenn nichts gesendet werden soll.
		 */
		public BuilderSolverChange<C> changes;
		/**
		 * Die Renderdaten. Null wenn nichts gesendet werden soll
		 */
		public Object renderData;
	}
	
	/**
	 * Builded den GameState nach der Antwort der Ai, wird nur auf der GameLogic aufgerufen
	 * 
	 * @param response Die Antwort der Ai
	 * @param ai Die Ai, welche geantwortet hat
	 * @return Die BuildResponse
	 */
	public Response<C> build(B response);
	
	/**
	 * Solved den GameState nach der Antwort der Ai, wird nur auf der GameLogic aufgerufen
	 * 
	 * @param response Die Antwort der Ai
	 * @param ai Die Ai, welche geantwortet hat
	 * @return Die SolveResponse
	 */
	public Response<C> solve(S response);
	
	/**
	 * Gibt ein Objekt zurück, welches an die Ai geschickt werden kann, damit diese diesen Zustand erhält bevor sie anfängt zulösen
	 * 
	 * @return Diesen Zustand als Änderungsobjekt
	 */
	public C getState();
	
	/**
	 * Wendet eine Änderung am GameState an, wie von der Ai angefordert
	 * 
	 * @param change Die Änderung, welche angefordert wurde
	 */
	public void applyChange(C change);
		
}
