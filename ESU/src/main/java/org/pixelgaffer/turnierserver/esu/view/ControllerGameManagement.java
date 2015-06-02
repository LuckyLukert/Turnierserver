package org.pixelgaffer.turnierserver.esu.view;

import org.pixelgaffer.turnierserver.esu.*;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

public class ControllerGameManagement {
	
	
	@FXML public Label lbMode;
	@FXML public Label lbDate;
	@FXML public Label lbDuration;
	@FXML public Label lbLogic;
	@FXML public Label lbChallenger;
	@FXML public Label lbJudged;
	@FXML public ToggleButton btOffline;
	@FXML public ToggleButton btOnline;
	@FXML public ChoiceBox<String> cbLogic;
	@FXML public ListView<Player> lvPlayer1;
	@FXML public ListView<Player> lvPlayer2;
	@FXML public ProgressIndicator progress;
	@FXML public Button btLoadOnline;
	@FXML public TextArea tbOutput1;
	@FXML public TextArea tbOutput2;
	@FXML public TableView<ParticipantResult> tableResult;
	
	MainApp mainApp;
	
	Game game = null;
	
	/**
	 * Initialisiert den Controller
	 * 
	 * @param app eine Referenz auf die MainApp
	 */
	public void setMainApp(MainApp app){
		mainApp = app;
		mainApp.cGame = this;

		TableColumn col0 = new TableColumn("Spieler");
		TableColumn col1 = new TableColumn("KI");
		TableColumn col2 = new TableColumn("gespielte Zeit");
		TableColumn col3 = new TableColumn("Züge");
		TableColumn col4 = new TableColumn("Punkte");
		TableColumn col5 = new TableColumn("Gewonnen?");
		
		col0.setCellValueFactory(new PropertyValueFactory<ParticipantResult, String>("playerName"));
		col1.setCellValueFactory(new PropertyValueFactory<ParticipantResult, String>("kiName"));
		col2.setCellValueFactory(new PropertyValueFactory<ParticipantResult, String>("duration"));
		col3.setCellValueFactory(new PropertyValueFactory<ParticipantResult, String>("moveCount"));
		col4.setCellValueFactory(new PropertyValueFactory<ParticipantResult, String>("points"));
		col5.setCellValueFactory(new PropertyValueFactory<ParticipantResult, String>("won"));
		
		tableResult.getColumns().addAll(col0, col1, col2, col3, col4, col5);
		
		final ToggleGroup group = new ToggleGroup();
		btOffline.setToggleGroup(group);
		btOffline.setSelected(true);
		btOnline.setToggleGroup(group);
		
		mainApp.gameManager.loadGames();
		showGame();
	}
	
	
	public void showGame(Game ggame){
		
	}
	
	public void showGame(){
		if (game != null){
			if (game.isOnline)
				lbMode.setText("Online");
			else
				lbMode.setText("Offline");
			lbDate.setText(game.date);
			lbDuration.setText(game.duration);
			lbLogic.setText(game.logic);
			tableResult.setItems(game.participants);
		}
		else{
			lbMode.setText("Offline");
			lbDate.setText("Jetzt");
			lbDuration.setText("555ms");
			lbLogic.setText("Test-Logik");
			tableResult.setItems(null);
		}
	}
	
	

	@FXML
	void clickChallenger(){
		tbOutput1.setText("Info1 geklickt");
	}

	@FXML
	void clickOnline(){
//		btOnline.setSelected(false);
//		btOffline.setSelected(true);
	}

	@FXML
	void clickOffline(){
		lvPlayer1.setItems(mainApp.playerManager.players);
		lvPlayer2.setItems(mainApp.playerManager.players);
//		btOnline.setSelected(true);
//		btOffline.setSelected(false);
	}

	@FXML
	void clickStartGame(){
		tbOutput1.setText("Info4 geklickt");
	}

	@FXML
	void clickLoadSaved(){
		tbOutput1.setText("Info5 geklickt");
	}

	@FXML
	void clickLoadOnline(){
		tbOutput1.setText("Info6 geklickt");
	}
}
