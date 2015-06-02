package org.pixelgaffer.turnierserver.backend;

import java.io.IOException;
import java.util.UUID;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import org.pixelgaffer.turnierserver.backend.Games.GameImpl;
import org.pixelgaffer.turnierserver.gamelogic.interfaces.Ai;
import org.pixelgaffer.turnierserver.gamelogic.interfaces.AiObject;
import org.pixelgaffer.turnierserver.networking.messages.MessageForward;

/**
 * Diese Klasse repräsentiert eine KI intern für Backend und Spiellogik.
 */
@RequiredArgsConstructor
public class AiWrapper implements Ai
{
	/** Das zugrundeliegende Spiel, enthält die Spiellogik. */
	@NonNull
	@Getter
	private GameImpl game;
	
	/** Die UUID der KI im Netzwerk. */
	@Getter
	@NonNull
	private UUID uuid;
	
	/** Die ID dieser KI. */
	@Setter
	@Getter
	private int id;
	
	/** Die Version dieser KI. */
	@Setter
	@Getter
	private int version;
	
	/** Der Index der KI in der Liste. */
	@Getter
	@Setter
	private int index;
	
	/** Die {@link WorkerConnection} dieser KI. */
	@Setter
	@Getter
	private WorkerConnection connection;
	
	/** Empfängt eine Nachricht und leitet sie an die Speillogik weiter. */
	public void receiveMessage (byte message[])
	{
		getGame().getLogic().receiveMessage(message, this);
	}
	
	/** Sendet eine Nachricht an die KI. */
	public void sendMessage (byte message[]) throws IOException
	{
		if (connection == null)
			throw new IOException("Not connected");
		MessageForward mf = new MessageForward(uuid, message);
		connection.sendMessage(mf);
	}
	
	/** Hier kann die Spiellogik Informationen über die KI abspeichern. */
	@Getter
	@Setter
	private AiObject object;
	
	@Override
	public void disconnect () throws IOException
	{
		connection.killJob(this);
	}
}
