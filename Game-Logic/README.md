#Game-Logic
Die Spiellogik wird eine Java-Bibliothek sein, welche es ermoeglicht, Spiellogiken fuer den Turnierserver zu schreiben. Die Spiellogiken werden per Refelection in den Backendserver geladen, und dort ausgefuehrt.

##GameLogic.java
-------------------------
Diese abstrakte Klasse ist die Basisklasse f�r alle Spiellogiken.

####Abstrakte Methoden
- public void startGame(int playerCount)
  * *Startet das Spiel mit einer bestimmten Anzahl an Spielern
- public void receive(String message, int player)
  * *Empf�ngt eine Nachricht des Spielers player

####Util Methoden
- protected void sendMessage(String message, int player)
  * *Sendet eine Nachricht an den Spieler player
  