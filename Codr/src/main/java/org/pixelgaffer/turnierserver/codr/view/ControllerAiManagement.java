package org.pixelgaffer.turnierserver.codr.view;


import java.io.File;
import java.io.IOException;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.TreeView.EditEvent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.util.Callback;
import net.lingala.zip4j.exception.ZipException;

import org.pixelgaffer.turnierserver.codr.CodeEditor;
import org.pixelgaffer.turnierserver.codr.AiBase;
import org.pixelgaffer.turnierserver.codr.AiBase.AiMode;
import org.pixelgaffer.turnierserver.codr.AiBase.NewVersionType;
import org.pixelgaffer.turnierserver.codr.MainApp;
import org.pixelgaffer.turnierserver.codr.Version;
import org.pixelgaffer.turnierserver.codr.utilities.Dialog;
import org.pixelgaffer.turnierserver.codr.utilities.Exceptions.CompileException;
import org.pixelgaffer.turnierserver.codr.utilities.Paths;
import org.pixelgaffer.turnierserver.codr.utilities.Resources;



public class ControllerAiManagement {
	
	
	@FXML Button btAbort;
	@FXML Button btEdit;
	@FXML Button btNewVersion;
	@FXML Button btCompile;
	@FXML Button btQualify;
	@FXML Button btFinish;
	@FXML Button btUpload;
	@FXML Button btToActual;
	@FXML Button btChangeImage;
	@FXML Button btDeleteImage;
	@FXML Label lbName;
	@FXML Label lbLanguage;
	@FXML Label lbCompiled;
	@FXML Label lbFinished;
	@FXML Label lbUploaded;
	@FXML RadioButton rbSimple;
	@FXML RadioButton rbContinue;
	@FXML RadioButton rbFromFile;
	@FXML TextField tbFile;
	@FXML TextField tbName;
	@FXML TextArea tbOutput;
	@FXML TextArea tbDescription;
	@FXML ChoiceBox<Version> cbVersion;
	@FXML ChoiceBox<String> cbLanguage;
	@FXML ListView<AiBase> lvAis;
	@FXML ImageView image;
	@FXML TabPane tpCode;
	@FXML Hyperlink hlShowQualified;
	@FXML ProgressIndicator prUpload;
	
	@FXML BorderPane bpAis;
	@FXML TreeView<File> tvFiles;
	
	public Tab infoTab;
	public Tab newFileTab;
	
	public MainApp mainApp;
	public AiBase ai;
	public Version version;
	
	
	/**
	 * Initialisiert den Controller
	 * 
	 * @param app eine Referenz auf die MainApp
	 */
	public void setMainApp(MainApp app) {
		mainApp = app;
		MainApp.cAi = this;
		cbVersion.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			clickVersionChange();
		});
		lvAis.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			clickChangeAi();
		});
		tpCode.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			clickTabSelection(oldValue, newValue);
		});
		tvFiles.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			if (newValue != null && newValue.getValue().isFile()) {
				for (Tab tab : tpCode.getTabs()) {
					if (tab.getText().equals(newValue.getValue().getName())) {
						tpCode.getSelectionModel().select(tab);
						break;
					}
				}
			}
		});
		
		tpCode.setStyle("-fx-open-tab-animation: NONE; -fx-close-tab-animation: NONE;");
		
		cbLanguage.getItems().addAll(MainApp.languages);
		cbLanguage.getSelectionModel().selectFirst();
		
		infoTab = tpCode.getTabs().get(0);
		newFileTab = tpCode.getTabs().get(1);
		
		lvAis.setItems(mainApp.aiManager.ais);
		lvAis.getSelectionModel().selectFirst();
	}
	
	
	/**
	 * Zeigt eine KI und eine ihrer Versionen an
	 * 
	 * @param p die KI
	 * @param v die zugehörige Version
	 */
	public void showAi(AiBase p, Version v) {
		ai = p;
		version = v;
		showAi();
	}
	
	
	/**
	 * Setzt alle Eigenschaften der Benutzeroberfläche, wie z.B. das KI-Namensfeld, das KI-Bild, die KI-Beschreibung, ...
	 */
	public void showAi() {
		
		// Ai-spezifisches
		if (ai != null) {
			lbName.setText(ai.title);
			lbLanguage.setText("Sprache: " + ai.language.toString());
			tbDescription.setText(ai.description);
			cbVersion.getSelectionModel().clearSelection();
			cbVersion.setItems(ai.versions);
			Bindings.bindBidirectional(image.imageProperty(), ai.getPicture());
			
			btChangeImage.setDisable(false);
			btDeleteImage.setDisable(false);
			btNewVersion.setDisable(false);
			btEdit.setDisable(false);
			btToActual.setDisable(false);
			
			if (version == null) {  // versuchen, die Version zu setzen, wenn keine ausgewählt ist
				version = ai.lastVersion();
			}
			boolean containing = false;
			for (int i = 0; i < ai.versions.size(); i++)
				if (version == ai.versions.get(i))
					containing = true;
			if (!containing) {  // oder eine nicht-zugehörige ausgewählt ist
				version = ai.lastVersion();
			}
		} else {
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
		
		// Beschreibung setzen
		tbDescription.setEditable(false);
		btAbort.setVisible(false);
		btEdit.setText("Bearbeiten");
		tbDescription.setEditable(false);
		
		// Version-spezifisches
		if (version != null && ai != null) {
			cbVersion.setValue(version);
			tbOutput.setText("");
			if (!version.compileOutput.equals("")) {
				tbOutput.setText(version.compileOutput);
			}
			if (!version.qualifyOutput.equals("")) {
				tbOutput.setText(version.qualifyOutput);
			}
			lbCompiled.setVisible(version.compiled);
			hlShowQualified.setVisible(version.qualified);
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
		} else {
			cbVersion.setValue(null);
			tbOutput.setText("");
			lbCompiled.setVisible(false);
			hlShowQualified.setVisible(false);
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
		
		if (ai != null) {
			if (ai.mode == AiMode.simplePlayer) {
				btEdit.setDisable(true);
				btNewVersion.setDisable(true);
				btCompile.setDisable(true);
				btQualify.setDisable(true);
				btFinish.setDisable(true);
				btUpload.setDisable(true);
				lbCompiled.setVisible(false);
				hlShowQualified.setVisible(false);
				lbFinished.setVisible(false);
				lbUploaded.setVisible(false);
				
				btChangeImage.setDisable(true);
				btDeleteImage.setDisable(true);
			}
		}
		
	}
	
	
	/**
	 * Lädt mithilfe der CodeEditoren der anzuzeigenden Version alle Dateien der Version in die Tab-Leiste
	 */
	private void setVersionTabs() {
		version.findCode();
		
		tvFiles.setRoot(version.rootFile);
		
		if (version.finished || version.ai.mode != AiMode.saved)
			tvFiles.setEditable(false);
		else
			tvFiles.setEditable(true);
		
		tvFiles.setCellFactory(new Callback<TreeView<File>, TreeCell<File>>() {
			@Override public TreeCell<File> call(TreeView<File> p) {
				return new TreeFileCell();
			}
		});
		tvFiles.setOnEditCommit(new EventHandler<EditEvent<File>>() {
			@Override public void handle(EditEvent<File> event) {
				System.out.println(event);
				setVersionTabs();
			}
		});
		
		String oldTabName = tpCode.getSelectionModel().getSelectedItem().getText();
		tpCode.getTabs().clear();
		tpCode.getTabs().add(infoTab);
		for (CodeEditor file : version.files) {
			Tab tab = file.getView();
			if (version.finished) {
				if (tab.getContent() != null)
					tab.getContent().setDisable(true);
				else
					tab.setDisable(true);
			}
			tpCode.getTabs().add(tab);
			if (tab.getText().equals(oldTabName)) {
				tpCode.getSelectionModel().select(tab);
			}
		}
		if (!version.finished)
			tpCode.getTabs().add(newFileTab);
	}
	
	
	/**
	 * Speichert und überprüft, ob auf das "neue Datei"-Tab geklickt wurde
	 * 
	 * @param oldTab der zuvor ausgewählte Tab
	 * @param newTab der neu ausgewählte Tab
	 */
	void clickTabSelection(Tab oldTab, Tab newTab) {
		
		// TreeView
		if (newTab == infoTab) {
			tvFiles.setVisible(false);
			bpAis.setVisible(true);
		} else {
			tvFiles.setVisible(true);
			bpAis.setVisible(false);
		}
		
		// Speichern
		if (version != null && !version.finished)
			version.saveCode();
		
		// NewFile +
		if (newTab == newFileTab && newTab != oldTab) {
			tpCode.getSelectionModel().select(oldTab);
			if (version == null) {
				Dialog.error("Bitte legen Sie zuerst eine Version an.", "Keine Version");
				return;
			}
			File result = Dialog.fileSaver(MainApp.stage, "Bitte einen Ort und Dateinamen auswählen", Paths.version(version));
			if (result != null) {
				CodeEditor editor = new CodeEditor(result);
				editor.forceSave();
				version.files.add(editor);
				tpCode.getTabs().add(tpCode.getTabs().size() - 1, editor.getView());
				tpCode.getSelectionModel().select(tpCode.getTabs().size() - 2);
				setVersionTabs();
			}
		}
	}
	
	
	/**
	 * Button: Neue KI anlegen
	 */
	@FXML void clickNewAi() {
		String title = tbName.getText().replace(" ", "");
		
		if (title.equals("")) {
			Dialog.error("Bitte einen Namen für die KI eingeben", "Kein Name");
			return;
		}
		
		for (int i = 0; i < lvAis.getItems().size(); i++) {  // Testen, ob die KI schon existiert
			if (title.equals(lvAis.getItems().get(i).title)) {
				Dialog.error("Es können keine zwei KIs mit dem gleichen Namen erstellt werden", "Doppelter Name");
				return;
			}
		}
		
		mainApp.aiManager.ais.add(new AiBase(title, cbLanguage.getValue()));
		lvAis.getSelectionModel().selectLast();
	}
	
	
	/**
	 * Listenselektions-Änderung: zeigt andere KI an
	 */
	@FXML void clickChangeAi() {
		ai = lvAis.getSelectionModel().getSelectedItem();
		if (ai != null) {
			version = ai.lastVersion();
			showAi();
		}
	}
	
	
	/**
	 * Button: Abbruch der Bearbeitung der Beschreibung der KI
	 */
	@FXML void clickAbort() {
		btAbort.setVisible(false);
		btEdit.setText("Bearbeiten");
		tbDescription.setEditable(false);
		tbDescription.setText(ai.description);
	}
	
	
	/**
	 * Button: Bearbeitung der Beschreibung der KI
	 */
	@FXML void clickEdit() {
		if (!btAbort.isVisible()) {
			btAbort.setVisible(true);
			btEdit.setText("Speichern");
			tbDescription.setEditable(true);
		} else {
			btAbort.setVisible(false);
			btEdit.setText("Bearbeiten");
			tbDescription.setEditable(false);
			ai.setDescription(tbDescription.getText());
		}
	}
	
	
	/**
	 * Button: aktuelle Version der KI wird ausgewählt
	 */
	@FXML void clickToActual() {
		version = ai.lastVersion();
		showAi();
	}
	
	
	/**
	 * Listenselektions-Änderung: zeigt andere Version an
	 */
	@FXML void clickVersionChange() {
		if (version != cbVersion.getValue() && cbVersion.getValue() != null) {
			version = cbVersion.getValue();
			showAi();
		}
	}
	
	
	/**
	 * Radiobutton: "SimplePlayer" wurde ausgewählt
	 */
	@FXML void clickRbSimple() {
		rbSimple.setSelected(true);
		rbContinue.setSelected(false);
		rbFromFile.setSelected(false);
	}
	
	
	/**
	 * Radiobutton: "Weiterschreiben" wurde ausgewählt
	 */
	@FXML void clickRbContinue() {
		rbSimple.setSelected(false);
		rbContinue.setSelected(true);
		rbFromFile.setSelected(false);
	}
	
	
	/**
	 * Radiobutton: "Aus Datei" wurde ausgewählt
	 */
	@FXML void clickRbFromFile() {
		rbSimple.setSelected(false);
		rbContinue.setSelected(false);
		rbFromFile.setSelected(true);
	}
	
	
	/**
	 * Button: Dateiauswahl wenn "Aus Datei" ausgewählt ist
	 */
	@FXML void clickSelectFile() {
		File result = Dialog.folderChooser(MainApp.stage, "Bitte einen Ordner auswählen");
		if (result != null)
			tbFile.setText(result.getPath());
	}
	
	
	/**
	 * Button: neue Version erstellen
	 */
	@FXML void clickNewVersion() {
		if (rbFromFile.isSelected()) {
			showAi(ai, ai.newVersion(NewVersionType.fromFile, tbFile.getText()));
		} else if (rbContinue.isSelected()) {
			showAi(ai, ai.newVersion(NewVersionType.lastVersion));
		} else {
			showAi(ai, ai.newVersion(NewVersionType.simplePlayer));
		}
	}
	
	
	/**
	 * Button: Kompilieren
	 */
	@FXML void clickCompile() {
		version.compile();
		showAi();
	}
	
	
	/**
	 * Button: Qualifizieren
	 */
	@FXML void clickQualify() {
		version.qualify();
		showAi();
	}
	
	
	/**
	 * Button: Fertigstellen
	 */
	@FXML void clickFinish() {
		if (Dialog.okAbort("Wenn eine Version fertiggestellt wird, kann sie nicht mehr bearbeitet werden.\n\nFortfahren?", "Version einfrieren")) {
			version.finish();
		}
		showAi();
	}
	
	
	private String nameOfNewAi = "";
	
	
	/**
	 * Button: Hochladen
	 */
	@FXML void clickUpload() {
		
		Task<Boolean> getOwn = new Task<Boolean>() {
			public Boolean call() {
				System.out.println("Angekommenerst1");
				if (MainApp.webConnector.isLoggedIn())
					MainApp.ownOnlineAis = MainApp.webConnector.getOwnAis(MainApp.actualGameType.get());
				System.out.println("Angekommenerst");
				return true;
			}
		};
		
		
		getOwn.valueProperty().addListener((observableValue, oldValue, newValue) -> {
			
			System.out.println("Angekommen");
			if (MainApp.ownOnlineAis == null) {
				Dialog.error("Bitte erst Anmelden");
				return;
			}
			
			AiBase result = Dialog.selectOwnVersion();
			if (result == null) {
				return;
			}
			
			if (result.title.equals("<Neue KI>")) {
				nameOfNewAi = Dialog.textInput("Bitte einen Namen eingeben", "Neue KI erstellen");
				if (nameOfNewAi == null)
					return;
			}
			
			Task<String> upload = new Task<String>() {
				public String call() {
					
					int id = result.id;
					id = MainApp.webConnector.createAi(ai, nameOfNewAi);
					if (id == -1) {
						return "errorConnection";
					}
					
					try {
						MainApp.webConnector.uploadVersion(version, id);
					} catch (ZipException | IOException e) {
						e.printStackTrace();
						return "errorConnection";
					}
					
					try {
						MainApp.webConnector.compile(id);
						return "finished";
					} catch (IOException e) {
						e.printStackTrace();
						return "errorConnection";
					} catch (CompileException e) {
						return "Fehler beim Kompilieren auf dem Server:\n\n" + e.compileOutput;
					}
				}
			};
			
			prUpload.setVisible(true);
			
			upload.valueProperty().addListener((observableValue1, oldValue1, newValue1) -> {
				prUpload.setVisible(false);
				switch (newValue1) {
				case "errorConnection":
					Dialog.error("Fehler bei der Verbindung mit dem Server.", "Verbindungsfehler");
					break;
				case "finished":
					Dialog.info("Die KI wurde erfolgreich hochgeladen, kompiliert (und qualifiziert).", "Upload fertig");
					break;
				default:
					Dialog.error(newValue1, "Fehler");
					break;
				}
			});
			
			Thread thread = new Thread(upload, "upload");
			thread.setDaemon(true);
			thread.start();
		});
		
		System.out.println("Angekommenerst0");
		Thread thread = new Thread(getOwn, "getOwn");
		thread.setDaemon(true);
		thread.start();
		
	}
	
	
	/**
	 * Hyperlink: zeigt das Qualifizier-Spiel an
	 */
	@FXML void clickShowQualified() {
		tbFile.setText("Info14 geklickt");
	}
	
	
	/**
	 * Button: Bild ändern
	 */
	@FXML void clickChangeImage() {
		File result = Dialog.fileChooser(MainApp.stage, "Bild ausw�hlen");
		Image img = Resources.imageFromFile(result);
		if (img != null) {
			ai.setPicture(Resources.imageFromFile(result));
		}
		showAi();
	}
	
	
	/**
	 * Button: Bild löschen
	 */
	@FXML void clickDeleteImage() {
		ai.setPicture(null);
		showAi();
	}
	
}
