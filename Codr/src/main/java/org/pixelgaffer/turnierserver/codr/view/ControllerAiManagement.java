/*
 * ControllerAiManagement.java
 *
 * Copyright (C) 2015 Pixelgaffer
 *
 * This work is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or any later
 * version.
 *
 * This work is distributed in the hope that it will be useful, but without
 * any warranty; without even the implied warranty of merchantability or
 * fitness for a particular purpose. See version 2 and version 3 of the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.pixelgaffer.turnierserver.codr.view;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.pixelgaffer.turnierserver.codr.AiBase;
import org.pixelgaffer.turnierserver.codr.AiBase.AiMode;
import org.pixelgaffer.turnierserver.codr.AiBase.NewVersionType;
import org.pixelgaffer.turnierserver.codr.AiExtern;
import org.pixelgaffer.turnierserver.codr.AiFake;
import org.pixelgaffer.turnierserver.codr.AiOnline;
import org.pixelgaffer.turnierserver.codr.AiSaved;
import org.pixelgaffer.turnierserver.codr.AiSimple;
import org.pixelgaffer.turnierserver.codr.CodeEditor;
import org.pixelgaffer.turnierserver.codr.MainApp;
import org.pixelgaffer.turnierserver.codr.Version;
import org.pixelgaffer.turnierserver.codr.utilities.Dialog;
import org.pixelgaffer.turnierserver.codr.utilities.ErrorLog;
import org.pixelgaffer.turnierserver.codr.utilities.Exceptions.CompileException;
import org.pixelgaffer.turnierserver.codr.utilities.Paths;
import org.pixelgaffer.turnierserver.codr.utilities.Resources;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
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
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeView;
import javafx.scene.control.TreeView.EditEvent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.util.Callback;
import net.lingala.zip4j.exception.ZipException;

public class ControllerAiManagement {

	@FXML Button btDelete;
	@FXML Button btAbort;
	@FXML Button btEdit;
	@FXML Button btNewVersion;
	@FXML Button btCompile;
	@FXML Button btQualify;
	@FXML Button btFinish;
	@FXML public Button btUpload;
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
	@FXML TextField tbName;
	@FXML TextArea tbOutput;
	@FXML TextArea tbDescription;
	@FXML ChoiceBox<Version> cbVersion;
	@FXML ChoiceBox<String> cbLanguage;
	@FXML ListView<AiSimple> lvAis;
	@FXML ImageView image;
	@FXML TabPane tpCode;
	@FXML Hyperlink hlShowQualified;
	@FXML ProgressIndicator prUpload;
	@FXML ProgressIndicator prCompile;
	@FXML ProgressIndicator prQualify;
	@FXML TitledPane tpNewVersion;
	@FXML BorderPane bpAis;
	@FXML TreeView<File> tvFiles;
	

	public Tab infoTab;
	public Tab newFileTab;

	public MainApp mainApp;
	public AiSimple ai;
	public Version version;

	/**
	 * Initialisiert den Controller
	 * 
	 * @param app eine Referenz auf die MainApp
	 */
	public void setMainApp(MainApp app) {
		mainApp = app;
		MainApp.cAi = this;
		cbVersion.getSelectionModel().selectedItemProperty() .addListener((observableValue, oldValue, newValue) -> {
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

		lvAis.setItems(MainApp.aiManager.ais);
		lvAis.getSelectionModel().selectFirst();
	}

	/**
	 * Zeigt eine KI und eine ihrer Versionen an
	 * 
	 * @param p die KI
	 * @param v die zugehörige Version
	 */
	public void showAi(AiSimple p, Version v) {
		ai = p;
		version = v;
		showAi();
	}

	/**
	 * Setzt alle Eigenschaften der Benutzeroberfläche, wie z.B. das
	 * KI-Namensfeld, das KI-Bild, die KI-Beschreibung, ...
	 */
	public void showAi() {

		// Ai-spezifisches
		if (ai != null) {
			lbName.setText(ai.title);
			lbLanguage.setText("Sprache: " + ai.language.toString());
			btDelete.setDisable(false);
			tbDescription.setText(ai.description);
			cbVersion.getSelectionModel().clearSelection();
			cbVersion.setItems(ai.versions);
			Bindings.bindBidirectional(image.imageProperty(), ai.getPicture());

			newFileTab.setDisable(false);
			btChangeImage.setDisable(false);
			btDeleteImage.setDisable(false);
			btToActual.setDisable(false);
			cbVersion.setDisable(false);
			tpNewVersion.setDisable(false);
			tpNewVersion.setExpanded(true);
			btEdit.setDisable(false);
			btToActual.setDisable(false);

			if (version == null) { // versuchen, die Version zu setzen, wenn
									// keine ausgewählt ist
				version = ai.lastVersion();
			}
			boolean containing = false;
			for (int i = 0; i < ai.versions.size(); i++)
				if (version == ai.versions.get(i))
					containing = true;
			if (!containing) { // oder eine nicht-zugehörige ausgewählt ist
				version = ai.lastVersion();
			}
		} else {
			newFileTab.setDisable(true);
			lbName.setText("Name");
			lbLanguage.setText("Sprache: ");
			tbDescription.setText("Momentan ist kein Spieler ausgew�hlt");
			btDelete.setDisable(true);
			cbVersion.getSelectionModel().clearSelection();
			ObservableList<Version> emptyFill = FXCollections
					.observableArrayList();
			cbVersion.setItems(emptyFill);
			image.setImage(Resources.defaultPicture());

			btChangeImage.setDisable(true);
			btDeleteImage.setDisable(true);
			btToActual.setDisable(true);
			cbVersion.setDisable(true);
			tpNewVersion.setDisable(true);
			tpNewVersion.setExpanded(false);
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
			newFileTab.setDisable(false);
			cbVersion.setValue(version);
			tbOutput.setText("");
			if (!version.compileOutput.equals("")) {
				tbOutput.setText(version.compileOutput);
			}
			if (!version.qualifyOutput.equals("")) {
				tbOutput.setText(version.qualifyOutput);
			}
			lbCompiled.visibleProperty().bind(version.compiled);
			hlShowQualified.visibleProperty().bind(version.qualified);
			lbFinished.visibleProperty().bind(version.finished);
			lbUploaded.setVisible(false);
			btCompile.disableProperty().bind(version.finished.or(version.compiled));
			btQualify.disableProperty().bind(version.compiled.not().or(version.finished).or(version.qualified));
			btFinish.disableProperty().bind(version.finished.or(new SimpleBooleanProperty(version.ai.mode == AiMode.extern)));
			btUpload.disableProperty().bind(new SimpleBooleanProperty(false));
			rbContinue.setDisable(false);

			rbContinue.setSelected(true);
			rbFromFile.setSelected(false);
			rbSimple.setSelected(false);

			setVersionTabs();
		} else {
			newFileTab.setDisable(true);
			cbVersion.setValue(null);
			tbOutput.setText("");
			lbCompiled.visibleProperty().bind(new SimpleBooleanProperty(false));
			hlShowQualified.visibleProperty().bind(new SimpleBooleanProperty(false));
			lbFinished.visibleProperty().bind(new SimpleBooleanProperty(false));
			lbUploaded.setVisible(false);
			btCompile.disableProperty().bind(new SimpleBooleanProperty(true));
			btQualify.disableProperty().bind(new SimpleBooleanProperty(true));
			btFinish.disableProperty().bind(new SimpleBooleanProperty(true));
			btUpload.disableProperty().bind(new SimpleBooleanProperty(true));
			rbContinue.setDisable(true);

			rbContinue.setSelected(false);
			rbFromFile.setSelected(false);
			rbSimple.setSelected(true);
		}

		// SimplePlayer
		if (ai != null) {
			if (ai.mode == AiMode.simplePlayer) {
				newFileTab.setDisable(true);
				btDelete.setDisable(true);
				btEdit.setDisable(true);
				btToActual.setDisable(true);
				cbVersion.setDisable(true);
				tpNewVersion.setDisable(true);
				tpNewVersion.setExpanded(false);
				lbCompiled.visibleProperty().bind(new SimpleBooleanProperty(false));
				hlShowQualified.visibleProperty().bind(new SimpleBooleanProperty(false));
				lbFinished.visibleProperty().bind(new SimpleBooleanProperty(false));
				lbUploaded.setVisible(false);
				btCompile.disableProperty().bind(new SimpleBooleanProperty(true));
				btQualify.disableProperty().bind(new SimpleBooleanProperty(true));
				btFinish.disableProperty().bind(new SimpleBooleanProperty(true));
				btUpload.disableProperty().bind(new SimpleBooleanProperty(true));

				btChangeImage.setDisable(true);
				btDeleteImage.setDisable(true);

			} else if (ai.mode == AiMode.extern) {
				btToActual.setDisable(true);
				cbVersion.setDisable(true);
				tpNewVersion.setDisable(true);
				tpNewVersion.setExpanded(false);
			}
		}

	}

	// public void updateAiLabels(){
	//
	// // Ai-spezifisches
	// if (ai != null) {
	// lbName.setText(ai.title);
	// lbLanguage.setText("Sprache: " + ai.language.toString());
	// tbDescription.setText(ai.description);
	// }
	//
	//
	// // Version-spezifisches
	// if (version != null && ai != null) {
	// lbCompiled.setVisible(version.compiled);
	// hlShowQualified.setVisible(version.qualified);
	// lbFinished.setVisible(version.finished);
	// lbUploaded.setVisible(version.uploaded);
	// btCompile.setDisable(version.compiled || version.finished);
	// btQualify.setDisable(version.qualified || !version.compiled ||
	// version.finished);
	// btFinish.setDisable(version.finished);
	// }
	//
	// if (ai != null) {
	// if (ai.mode == AiMode.simplePlayer) {
	// newFileTab.setDisable(true);
	// btDelete.setDisable(true);
	// btEdit.setDisable(true);
	// btToActual.setDisable(true);
	// cbVersion.setDisable(true);
	// tpNewVersion.setDisable(true);
	// tpNewVersion.setExpanded(false);
	// btCompile.setDisable(true);
	// btQualify.setDisable(true);
	// btFinish.setDisable(true);
	// btUpload.setDisable(true);
	// lbCompiled.setVisible(false);
	// hlShowQualified.setVisible(false);
	// lbFinished.setVisible(false);
	// lbUploaded.setVisible(false);
	//
	// btChangeImage.setDisable(true);
	// btDeleteImage.setDisable(true);
	//
	// } else if (ai.mode == AiMode.extern) {
	// btToActual.setDisable(true);
	// cbVersion.setDisable(true);
	// tpNewVersion.setDisable(true);
	// tpNewVersion.setExpanded(false);
	// }
	// }
	// }

	/**
	 * Lädt mithilfe der CodeEditoren der anzuzeigenden Version alle Dateien der
	 * Version in die Tab-Leiste
	 */
	private void setVersionTabs() {
		if (version == null) {
			tpCode.getTabs().clear();
			tpCode.getTabs().add(infoTab);
			tpCode.getTabs().add(newFileTab);
			return;
		}

		version.findCode();

		tvFiles.setRoot(version.rootFile);

		if (version.finished.get() || (version.ai.mode != AiMode.saved && version.ai.mode != AiMode.extern))
			tvFiles.setEditable(false);
		else
			tvFiles.setEditable(true);

		tvFiles.setCellFactory(new Callback<TreeView<File>, TreeCell<File>>() {
			@Override
			public TreeCell<File> call(TreeView<File> p) {
				return new TreeFileCell();
			}
		});
		tvFiles.setOnEditCommit(new EventHandler<EditEvent<File>>() {
			@Override
			public void handle(EditEvent<File> event) {
				setVersionTabs();
			}
		});

		String oldTabName = tpCode.getSelectionModel().getSelectedItem().getText();
		tpCode.getTabs().clear();
		tpCode.getTabs().add(infoTab);
		for (CodeEditor file : version.files) {
			Tab tab = file.getView();
			if (version.finished.get()) {
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
		if (!version.finished.get())
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
		if (version != null && !version.finished.get()) {
			version.saveCode();
		}

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
	 * Button: KI löschen
	 */
	@FXML
	void clickDelete() {
		boolean result = Dialog.okAbort("KI wirklich löschen?", "Löschen");
		if (result) {
			try {
				FileUtils.deleteDirectory(new File(Paths.ai(ai)));
				MainApp.aiManager.loadAis();
				lvAis.getSelectionModel().selectFirst();
			} catch (IOException e) {
				ErrorLog.write("Die KI " + ai.title + " konnte nicht gelöscht werden.");
			}
		}
	}

	/**
	 * Button: Neue KI anlegen
	 */
	@FXML
	void clickNewAi() {
		String title = tbName.getText().replace(" ", "");

		if (title.equals("")) {
			Dialog.error("Bitte einen Namen für die KI eingeben", "Kein Name");
			return;
		}

		for (int i = 0; i < lvAis.getItems().size(); i++) { // Testen, ob die KI
															// schon existiert
			if (title.equals(lvAis.getItems().get(i).title)) {
				Dialog.error("Es können keine zwei KIs mit dem gleichen Namen erstellt werden", "Doppelter Name");
				return;
			}
		}

		MainApp.aiManager.ais.add(new AiSaved(title, cbLanguage.getValue()));
		lvAis.getSelectionModel().selectLast();
		setVersionTabs();
	}

	/**
	 * Button: Neue KI anlegen
	 */
	@FXML
	void clickNewExtern() {
		String title = tbName.getText().replace(" ", "");

		if (title.equals("")) {
			Dialog.error("Bitte einen Namen für die KI eingeben", "Kein Name");
			return;
		}

		for (int i = 0; i < lvAis.getItems().size(); i++) { // Testen, ob die KI
															// schon existiert
			if (title.equals(lvAis.getItems().get(i).title)) {
				Dialog.error("Es können keine zwei KIs mit dem gleichen Namen erstellt werden", "Doppelter Name");
				return;
			}
		}

		File path = Dialog.folderChooser(MainApp.stage, "Bitte den Pfad auswählen");
		if (path == null)
			return;

		AiExtern newAi = new AiExtern(title, cbLanguage.getValue(), path.getPath());
		MainApp.aiManager.ais.add(newAi);
		lvAis.getSelectionModel().selectLast();

		boolean result = false;
		if (!new File(Paths.versionSrc(newAi.lastVersion())).exists()) {
			ErrorLog.write("Der ausgewählte Ordner existert nicht.");
		} else if (new File(Paths.versionSrc(newAi.lastVersion())).list().length == 0) {
			result = Dialog.okAbort("Die Version wurde angelegt.\nDer Ordner ist leer.\n\nSoll der SimplePlayer hinein kopiert werden?");
		}

		if (result) {
			ErrorLog.write(Paths.simplePlayer(MainApp.actualGameType.get(), newAi.language));
			newAi.lastVersion().copyFromFile(Paths.simplePlayer(MainApp.actualGameType.get(), newAi.language) + "/src");
			new File(newAi.path + "/versionProperties.txt").delete();
		}
		setVersionTabs();
	}

	/**
	 * Listenselektions-Änderung: zeigt andere KI an
	 */
	@FXML
	void clickChangeAi() {
		ai = lvAis.getSelectionModel().getSelectedItem();
		if (ai != null) {
			version = ai.lastVersion();
			showAi();
		}
	}

	/**
	 * Button: Abbruch der Bearbeitung der Beschreibung der KI
	 */
	@FXML
	void clickAbort() {
		btAbort.setVisible(false);
		btEdit.setText("Bearbeiten");
		tbDescription.setEditable(false);
		tbDescription.setText(ai.description);
	}

	/**
	 * Button: Bearbeitung der Beschreibung der KI
	 */
	@FXML
	void clickEdit() {
		if (!btAbort.isVisible()) {
			btAbort.setVisible(true);
			btEdit.setText("Speichern");
			tbDescription.setEditable(true);
		} else {
			btAbort.setVisible(false);
			btEdit.setText("Bearbeiten");
			tbDescription.setEditable(false);
			if (ai.getClass() == AiSaved.class) {
				((AiSaved) ai).setDescription(tbDescription.getText());
			}
		}
	}

	/**
	 * Button: aktuelle Version der KI wird ausgewählt
	 */
	@FXML
	void clickToActual() {
		version = ai.lastVersion();
		showAi();
	}

	/**
	 * Listenselektions-Änderung: zeigt andere Version an
	 */
	@FXML
	void clickVersionChange() {
		if (version != cbVersion.getValue() && cbVersion.getValue() != null) {
			version = cbVersion.getValue();
			showAi();
		}
	}

	/**
	 * Radiobutton: "SimplePlayer" wurde ausgewählt
	 */
	@FXML
	void clickRbSimple() {
		rbSimple.setSelected(true);
		rbContinue.setSelected(false);
		rbFromFile.setSelected(false);
	}

	/**
	 * Radiobutton: "Weiterschreiben" wurde ausgewählt
	 */
	@FXML
	void clickRbContinue() {
		rbSimple.setSelected(false);
		rbContinue.setSelected(true);
		rbFromFile.setSelected(false);
	}

	/**
	 * Radiobutton: "Aus Datei" wurde ausgewählt
	 */
	@FXML
	void clickRbFromFile() {
		rbSimple.setSelected(false);
		rbContinue.setSelected(false);
		rbFromFile.setSelected(true);
	}

	/**
	 * Button: neue Version erstellen
	 */
	@FXML
	void clickNewVersion() {
		if (ai.getClass() == AiSaved.class) {

			if (ai.versions.size() > 0 && !ai.lastVersion().finished.get())
				if (!Dialog.okAbort("Wenn eine neue Version angelegt wird, wird die alte fertiggestellt.\nDas bedeutet, dass sie nicht mehr verändert werden kann.\n\nFortfahren?", "Neue Version"))
					return;
			if (ai.versions.size() > 0)
				ai.lastVersion().finished.set(true);

			if (rbFromFile.isSelected()) {
				File result = Dialog.folderChooser(MainApp.stage, "Bitte einen Ordner auswählen");
				if (result != null)
					showAi(ai, ((AiSaved) ai).newVersion(NewVersionType.fromFile, result.getPath()));
			} else if (rbContinue.isSelected()) {
				showAi(ai, ((AiSaved) ai).newVersion(NewVersionType.lastVersion));
			} else {
				showAi(ai, ((AiSaved) ai).newVersion(NewVersionType.simplePlayer));
			}
		}
	}

	/**
	 * Button: Kompilieren
	 */
	@FXML
	void clickCompile() {
		Task<Boolean> compile = new Task<Boolean>() {
			public Boolean call() {
				try {
					version.compile();
				} catch (Exception e) {
					e.printStackTrace();
				}
				return true;
			}
		};

		compile.valueProperty().addListener((observableValue, oldValue, newValue) -> {
			prCompile.setVisible(false);
			showAi();
		});

		prCompile.setVisible(true);
		Thread thread = new Thread(compile, "compile");
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * Button: Qualifizieren
	 */
	@FXML
	void clickQualify() {
		Task<Boolean> qualify = new Task<Boolean>() {
			public Boolean call() {
				try {
					version.qualify();
				} catch (Exception e) {
					e.printStackTrace();
				}
				return true;
			}
		};

		qualify.valueProperty().addListener((observableValue, oldValue, newValue) -> {
			prQualify.setVisible(false);
			showAi();
		});

		prQualify.setVisible(true);
		Thread thread = new Thread(qualify, "qualify");
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * Button: Fertigstellen
	 */
	@FXML
	void clickFinish() {
		if (Dialog.okAbort("Wenn eine Version fertiggestellt wird, kann sie nicht mehr bearbeitet werden.\n\nFortfahren?", "Version einfrieren")) {
			version.finish();
		}
		showAi();
	}

	private String localNameOfNewAi = "";

	/**
	 * Button: Hochladen
	 */
	@FXML
	void clickUpload() {

		prUpload.setVisible(true);

		Task<Boolean> getOwn = new Task<Boolean>() {
			public Boolean call() {
				if (MainApp.ownOnlineAis == null) {
					System.out.println("vielleicht unmöglich");
					MainApp.ownOnlineAis = MainApp.webConnector.getOwnAis(MainApp.actualGameType.get());
				} else if (MainApp.ownOnlineAis.size() == 0){
					MainApp.ownOnlineAis.addAll(MainApp.webConnector.getOwnAis(MainApp.actualGameType.get()));
				}
				return true;
			}
		};

		getOwn.valueProperty().addListener((observableValue, oldValue, newValue) -> {

			if (MainApp.ownOnlineAis == null) {
				prUpload.setVisible(false);
				lbUploaded.setText("Fehler");
				Dialog.error("Bitte erst Anmelden");
				return;
			}

			AiBase result = Dialog.selectOwnVersion();
			if (result == null) {
				prUpload.setVisible(false);
				lbUploaded.setText("Fehler");
				return;
			}

			if (result.getClass() == AiFake.class) {
				localNameOfNewAi = Dialog.textInput("Bitte einen Namen eingeben", "Neue KI erstellen");
				if (localNameOfNewAi == null){
					prUpload.setVisible(false);
					lbUploaded.setText("Fehler");
					return;
				}
			}

			Task<String> upload = new Task<String>() {
				public String call() {
					int id = -1;
					if (result instanceof AiOnline) {
						id = ((AiOnline) result).id;
					} else {
						updateValue("aiCreating");
						id = MainApp.webConnector.createAi(ai, localNameOfNewAi);
					}
					if (id == -1) {
						return "errorConnection";
					}
					
					try {
						updateValue("uploading");
						MainApp.webConnector.uploadVersion(version, id);
					} catch (ZipException | IOException e) {
						e.printStackTrace();
						return "errorConnection";
					} catch (IllegalStateException e) {
						return "Fehler beim Hochladen: " + e.getMessage();
					} catch (Exception e){
						e.printStackTrace();
					}

					try {
						updateValue("compiling");
						MainApp.webConnector.compile(id);
					} catch (IOException e) {
						e.printStackTrace();
						return "errorConnection";
					} catch (CompileException e) {
						return "Fehler beim Kompilieren auf dem Server:\n\n" + e.compileOutput;
					}

					try {
						updateValue("qualifying");
						if (MainApp.webConnector.qualify(id))
							return "finished--" + id;
						else
							return "Fehler beim Qualifizieren auf dem Server.";
					} catch (IOException e) {
						e.printStackTrace();
						return "errorConnection";
					}
					
				}
			};

										
			upload.valueProperty().addListener((observableValue1, oldValue1, newValue1) -> {
				if (newValue1.equals("aiCreating")){
					lbUploaded.setText("erstelle KI...");
				} else if (newValue1.equals("uploading")){
					lbUploaded.setText("lade Version hoch...");
				} else if (newValue1.equals("compiling")){
					lbUploaded.setText("kompiliere Version...");
				} else if (newValue1.equals("qualifying")){
					lbUploaded.setText("qualifiziere Version...");
				} else if (newValue1.equals("errorConnection")){
					prUpload.setVisible(false);
					lbUploaded.setText("Fehler");
					Dialog.error("Fehler bei der Verbindung mit dem Server.", "Verbindungsfehler");
				} else if (newValue1.substring(0, 10).equals("finished--")){
					int id = Integer.parseInt(newValue1.substring(10));
					if (Dialog.okAbort("Die KI wurde erfolgreich hochgeladen, kompiliert und qualifiziert.\n"
							+ "Soll sie fertiggestellt und aktiviert werden?", "Upload fertig")){
						
						Task<String> finishActivate = new Task<String>() {
							public String call() {

								try {
									updateValue("finishing");
									MainApp.webConnector.freeze(id);
								} catch (IOException e) {
									e.printStackTrace();
									return "errorConnection";
								}
								
								try {
									updateValue("activating");
									MainApp.webConnector.setActive(id, -1);
								} catch (IOException e) {
									e.printStackTrace();
									return "errorConnection";
								}
								
								return "finish";
								
							}
						};
						
						finishActivate.valueProperty().addListener((observableValue2, oldValue2, newValue2) -> {
							if (newValue2.equals("finishing")){
								lbUploaded.setText("stelle Version fertig...");
							} else if (newValue2.equals("activating")){
								lbUploaded.setText("aktiviere Version...");
							} else if (newValue2.equals("errorConnection")){
								prUpload.setVisible(false);
								lbUploaded.setText("Fehler");
								Dialog.error("Fehler bei der Verbindung mit dem Server.", "Verbindungsfehler");
							} else if (newValue2.equals("finish")){
								prUpload.setVisible(false);
								lbUploaded.setText("Version wurde hochgeladen");
								Dialog.error("Die Version wurde erfolgreich fertiggestellt und aktiviert", "Fertig");
							} else {
								prUpload.setVisible(false);
								lbUploaded.setText("Fehler");
								Dialog.error(newValue1, "Fehler");
							}
						});
						

						Thread thread = new Thread(finishActivate, "finishActivate");
						thread.setDaemon(true);
						thread.start();
						
					} else {
						prUpload.setVisible(false);
						lbUploaded.setText("Version wurde hochgeladen");
					}
				} else {
					prUpload.setVisible(false);
					lbUploaded.setText("Fehler");
					Dialog.error(newValue1, "Fehler");
				}
			});

			Thread thread = new Thread(upload, "upload");
			thread.setDaemon(true);
			thread.start();
		});

		lbUploaded.setVisible(true);
		lbUploaded.setText("Lade KIs...");
		Thread thread = new Thread(getOwn, "getOwn");
		thread.setDaemon(true);
		thread.start();

	}

	/**
	 * Hyperlink: zeigt das Qualifizier-Spiel an
	 */
	@FXML
	void clickShowQualified() {
		ErrorLog.write("Diese Funktion ist noch nicht verfügbar");
	}

	/**
	 * Button: Bild ändern
	 */
	@FXML
	void clickChangeImage() {
		File result = Dialog.fileChooser(MainApp.stage, "Bild auswählen");
		Image img = Resources.imageFromFile(result);
		if (img != null) {
			ai.setPicture(result);
		}
		showAi();
	}

	/**
	 * Button: Bild löschen
	 */
	@FXML
	void clickDeleteImage() {
		ai.setPicture(new File(""));
		showAi();
	}

}
