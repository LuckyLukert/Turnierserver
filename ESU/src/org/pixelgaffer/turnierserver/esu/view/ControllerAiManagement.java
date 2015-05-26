package org.pixelgaffer.turnierserver.esu.view;

import java.io.File;

import org.pixelgaffer.turnierserver.esu.*;
import org.pixelgaffer.turnierserver.esu.Dialog;
import org.pixelgaffer.turnierserver.esu.Player.Language;
import org.pixelgaffer.turnierserver.esu.Player.NewVersionType;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;

public class ControllerAiManagement{
	

	@FXML public Button btAbort;
	@FXML public Button btEdit;
	@FXML public Button btNewVersion;
	@FXML public Button btCompile;
	@FXML public Button btQualify;
	@FXML public Button btFinish;
	@FXML public Button btUpload;
	@FXML public Button btToActual;
	@FXML public Button btChangeImage;
	@FXML public Button btDeleteImage;
	@FXML public Label lbName;
	@FXML public Label lbLanguage;
	@FXML public Label lbCompiled;
	@FXML public Label lbQualified;
	@FXML public Label lbFinished;
	@FXML public Label lbUploaded;
	@FXML public RadioButton rbSimple;
	@FXML public RadioButton rbContinue;
	@FXML public RadioButton rbFromFile;
	@FXML public TextField tbFile;
	@FXML public TextField tbName;
	@FXML public TextArea tbOutput;
	@FXML public TextArea tbDescription;
	@FXML public ChoiceBox<Version> cbVersion;
	@FXML public ChoiceBox<Language> cbLanguage;
	@FXML public ListView<Player> lvAis;
	@FXML public ImageView image;
	@FXML public TabPane tpCode;
	
	public MainApp mainApp;
	public PlayerManager manager = new PlayerManager();
	public Player player;
	public Version version;

	/**
	 * Initialisiert den Controller
	 * 
	 * @param app eine Referenz auf die MainApp
	 */
	public void setMainApp(MainApp app){
		mainApp = app;
		mainApp.cAi = this;
		cbVersion.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Version>() {
		    @Override
		    public void changed(ObservableValue<? extends Version> observable, Version oldValue, Version newValue) {
		        clickVersionChange();
		    }
		});
		lvAis.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Player>() {
		    @Override
		    public void changed(ObservableValue<? extends Player> observable, Player oldValue, Player newValue) {
		        clickChangeAi();
		    }
		});
		cbLanguage.itemsProperty().get().add(Language.Java);
		cbLanguage.itemsProperty().get().add(Language.Python);
		cbLanguage.getSelectionModel().selectFirst();
		
		manager.loadPlayers();
		showPlayers();
	}
	
	
	/**
	 * L�dt alle KIs in die KI-Liste
	 */
	public void showPlayers(){
		lvAis.setItems(manager.players);
		try {
			lvAis.getSelectionModel().selectFirst();
		} catch (Exception e) {}
	}
	
	/**
	 * Zeigt eine KI und eine ihrer Versionen an
	 * 
	 * @param p die KI
	 * @param v die zugeh�rige Version
	 */
	public void showPlayer(Player p, Version v){
		player = p;
		version = v;
		showPlayer();
	}
	/**
	 * Setzt alle Eigenschaften der Benutzeroberfl�che, wie z.B. das KI-Namensfeld, das KI-Bild, die KI-Beschreibung, ...
	 */
	public void showPlayer(){
		
		// Player-spezifisches
		if (player != null){
			lbName.setText(player.title);
			lbLanguage.setText("Sprache: " + player.language.toString());
			tbDescription.setText(player.description);
			cbVersion.getSelectionModel().clearSelection();
			cbVersion.setItems(player.versions);
			image.setImage(player.getPicture());
			
			btChangeImage.setDisable(false);
			btDeleteImage.setDisable(false);
			btNewVersion.setDisable(false);
			btEdit.setDisable(false);
			btToActual.setDisable(false);
			
			if (version == null){  //versuchen, die Version zu setzen, wenn keine ausgew�hlt ist
				version = player.lastVersion();
			}
			boolean containing = false;
			for (int i = 0; i < player.versions.size(); i++)
				if (version == player.versions.get(i))
					containing = true;
			if(!containing){  //oder eine nicht-zugeh�rige ausgew�hlt ist
				version = player.lastVersion();
			}
		}
		else{
			lbName.setText("Name");
			lbLanguage.setText("Sprache: ");
			tbDescription.setText("Momentan ist kein Spieler ausgew�hlt");
			cbVersion.getSelectionModel().clearSelection();
			ObservableList<Version> emptyFill = FXCollections.observableArrayList();
			cbVersion.setItems(emptyFill);
			image.setImage(Resources.defaultPicture());

			btChangeImage.setDisable(true);
			btDeleteImage.setDisable(true);
			btNewVersion.setDisable(true);
			btEdit.setDisable(true);
			btToActual.setDisable(true);
		}
		
		//Beschreibung setzen
		tbDescription.setEditable(false);
		btAbort.setVisible(false);
		btEdit.setText("Bearbeiten");
		tbDescription.setEditable(false);
		
		//Version-spezifisches
		if (version != null && player != null){
			cbVersion.setValue(version);
			tbOutput.setText("");
			if (version.compiled){
				tbOutput.setText(version.compileOutput);
			}
			if (version.qualified){
				tbOutput.setText(version.qualifyOutput);
			}
			lbCompiled.setVisible(version.compiled);
			lbQualified.setVisible(version.qualified);
			lbFinished.setVisible(version.finished);
			lbUploaded.setVisible(version.uploaded);
			btCompile.setDisable(version.compiled || version.finished);
			btQualify.setDisable(version.qualified || !version.compiled || version.finished);
			btFinish.setDisable(version.finished);
			btUpload.setDisable(false);
			rbContinue.setDisable(false);

			rbContinue.setSelected(true);
			rbFromFile.setSelected(false);
			rbSimple.setSelected(false);
			
			setVersionTabs();
		}
		else{
			cbVersion.setValue(null);
			tbOutput.setText("");
			lbCompiled.setVisible(false);
			lbQualified.setVisible(false);
			lbFinished.setVisible(false);
			lbUploaded.setVisible(false);
			btCompile.setDisable(true);
			btQualify.setDisable(true);
			btFinish.setDisable(true);
			btUpload.setDisable(true);
			rbContinue.setDisable(true);
			
			rbContinue.setSelected(false);
			rbFromFile.setSelected(false);
			rbSimple.setSelected(true);
		}
	}
	
	/**
	 * L�dt mithilfe der CodeEditoren der anzuzeigenden Version alle Dateien der Version in die Tab-Leiste
	 */
	private void setVersionTabs(){
		while (tpCode.getTabs().size() > 1){
			tpCode.getTabs().remove(tpCode.getTabs().size()-1);
		}
		for (int i = 0; i < version.files.size(); i++){
			version.files.get(i).load();
			tpCode.getTabs().add(version.files.get(i).getView());
		}
	}
	
	
	
	/**
	 * Button: Neue KI anlegen
	 */
	@FXML void clickNewAi(){
		String title = tbName.getText();
		for (int i = 0; i < lvAis.getItems().size(); i++){  //Testen, ob die KI schon existiert
			if (title.equals(lvAis.getItems().get(i).title)){
				Dialog.error("Es k�nnen keine zwei KIs mit dem gleichen Namen erstellt werden", "Doppelter Name");
				return;
			}
		}
		
		manager.players.add(new Player(title, cbLanguage.getValue()));
		lvAis.getSelectionModel().selectLast();
	}

	/**
	 * Listenselektions-�nderung: zeigt andere KI an
	 */
	@FXML void clickChangeAi(){
		player = lvAis.getSelectionModel().getSelectedItem();
		version = player.lastVersion();
		showPlayer();
	}
	
	/**
	 * Button: Abbruch der Bearbeitung der Beschreibung der KI
	 */
	@FXML void clickAbort(){
		btAbort.setVisible(false);
		btEdit.setText("Bearbeiten");
		tbDescription.setEditable(false);
		tbDescription.setText(player.description);
	}
	
	/**
	 * Button: Bearbeitung der Beschreibung der KI
	 */
	@FXML void clickEdit(){
		if (!btAbort.isVisible()){
			btAbort.setVisible(true);
			btEdit.setText("Speichern");
			tbDescription.setEditable(true);
		}
		else{
			btAbort.setVisible(false);
			btEdit.setText("Bearbeiten");
			tbDescription.setEditable(false);
			player.setDescription(tbDescription.getText());
		}
	}
	
	/**
	 * Button: aktuelle Version der KI wird ausgew�hlt
	 */
	@FXML void clickToActual(){
		version = player.lastVersion();
		showPlayer();
	}
	
	/**
	 * Listenselektions-�nderung: zeigt andere Version an
	 */
	@FXML void clickVersionChange(){
		if (version != cbVersion.getValue() && cbVersion.getValue() != null){
			version = cbVersion.getValue();
			showPlayer();
		}
	}
	
	/**
	 * Radiobutton: "SimplePlayer" wurde ausgew�hlt
	 */
	@FXML void clickRbSimple(){
		rbSimple.setSelected(true);
		rbContinue.setSelected(false);
		rbFromFile.setSelected(false);
	}
	
	/**
	 * Radiobutton: "Weiterschreiben" wurde ausgew�hlt
	 */
	@FXML void clickRbContinue(){
		rbSimple.setSelected(false);
		rbContinue.setSelected(true);
		rbFromFile.setSelected(false);
	}
	
	/**
	 * Radiobutton: "Aus Datei" wurde ausgew�hlt
	 */
	@FXML void clickRbFromFile(){
		rbSimple.setSelected(false);
		rbContinue.setSelected(false);
		rbFromFile.setSelected(true);
	}
	
	/**
	 * Button: Dateiauswahl wenn "Aus Datei" ausgew�hlt ist
	 */
	@FXML void clickSelectFile(){
		tbFile.setText(Dialog.folderChooser(mainApp.stage, "Bitte einen Ordner ausw�hlen").getPath());
	}
	
	/**
	 * Button: neue Version erstellen
	 */
	@FXML void clickNewVersion(){
		if (rbFromFile.isSelected()){
			showPlayer(player, player.newVersion(NewVersionType.fromFile, tbFile.getText()));
		}
		else if (rbContinue.isSelected()){
			showPlayer(player, player.newVersion(NewVersionType.lastVersion));
		}
		else{
			showPlayer(player, player.newVersion(NewVersionType.simplePlayer));
		}
	}
	
	/**
	 * Button: Kompilieren
	 */
	@FXML void clickCompile(){
		version.compile();
		showPlayer();
	}
	
	/**
	 * Button: Qualifizieren
	 */
	@FXML void clickQualify(){
		version.qualify();
		showPlayer();
	}
	
	/**
	 * Button: Fertigstellen
	 */
	@FXML void clickFinish(){
		if (Dialog.okAbort("Wenn eine Version fertiggestellt wird, kann sie nicht mehr bearbeitet werden.\n\nFortfahren?", "Version einfrieren")){
			version.finish();
		}
		showPlayer();
	}
	
	/**
	 * Button: Hochladen
	 */
	@FXML void clickUpload(){
		tbFile.setText("Info14 geklickt");
	}
	
	/**
	 * Button: Bild �ndern
	 */
	@FXML void clickChangeImage(){
		File result = Dialog.fileChooser(mainApp.stage, "Bild ausw�hlen");
		Image img = Resources.imageFromFile(result);
		if (img != null){
			player.setPicture(Resources.imageFromFile(result));
		}
		showPlayer();
	}

	/**
	 * Button: Bild l�schen
	 */
	@FXML void clickDeleteImage(){
		player.setPicture(null);
		showPlayer();
	}
	
}
