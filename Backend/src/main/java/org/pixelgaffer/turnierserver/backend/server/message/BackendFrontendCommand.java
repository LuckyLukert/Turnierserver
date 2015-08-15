package org.pixelgaffer.turnierserver.backend.server.message;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Diese Message parst einen Befehl vom Frontend.
 */
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public class BackendFrontendCommand
{
	/** Der Befehl des Frontends. */
	@Getter
	private String action;
	
	/** Die request-id des Frontends. */
	@Getter
	private int requestid;
	
	/** Die id des Spiels. */
	@Getter
	private int gametype;
	
	// action=compile oder action=qualify
	
	/** Falls der Befehl kompilieren ist, ist dies die id der KI. */
	@Getter
	private String id;
	
	// action=start
	
	/** Falls der Befehl Spiel starten ist, sind dies die beteiligten KIs. */
	@Getter
	private String ais[];
	
	/** Die Sprache der Ki, beim ausführen */
	@Getter
	private String languages[];
	
	/** Die Sprache der Ki, beim kompilieren*/
	@Getter
	private String language;
}
