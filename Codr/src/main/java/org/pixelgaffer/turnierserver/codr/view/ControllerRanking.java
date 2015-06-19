package org.pixelgaffer.turnierserver.codr.view;


import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import org.pixelgaffer.turnierserver.codr.AiOnline;
import org.pixelgaffer.turnierserver.codr.GameBase;
import org.pixelgaffer.turnierserver.codr.MainApp;
import org.pixelgaffer.turnierserver.codr.Version;
import org.pixelgaffer.turnierserver.codr.utilities.Resources;



public class ControllerRanking {
	
	
	@FXML Label lbName;
	@FXML Label lbUser;
	@FXML Label lbElo;
	@FXML Label lbLanguage;
	@FXML Button btChallenge;
	@FXML Button btEdit;
	@FXML Button btAbort;
	@FXML HBox hbEdit;
	@FXML VBox vbContent;
	@FXML TextArea tbDescription;
	@FXML TableView<AiOnline> tvAis;
	@FXML TableView<Version> tvVersions;
	@FXML TableView<GameBase> tvGames;
	@FXML ImageView imageView;
	
	
	MainApp mainApp;
	public AiOnline ai;
	
	
	/**
	 * Initialisiert den Controller
	 * 
	 * @param app eine Referenz auf die MainApp
	 */
	public void setMainApp(MainApp app) {
		mainApp = app;
		MainApp.cRanking = this;
		tvAis.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			clickChangeAi(newValue);
		});
		
		mainApp.loadOnlineAis();
		tvAis.setItems(MainApp.onlineAis);
		
		TableColumn<AiOnline, Image> col0 = new TableColumn<AiOnline, Image>("Bild");
		col0.setMaxWidth(60);
		col0.setMinWidth(60);
		TableColumn<AiOnline, String> col1 = new TableColumn<AiOnline, String>("Name");
		TableColumn<AiOnline, String> col2 = new TableColumn<AiOnline, String>("Besitzer");
		TableColumn<AiOnline, String> col3 = new TableColumn<AiOnline, String>("ELO");
		col3.setMaxWidth(60);
		col3.setMinWidth(60);
		
		col0.setCellValueFactory(new Callback<CellDataFeatures<AiOnline, Image>, ObservableValue<Image>>() {
			@Override public ObservableValue<Image> call(CellDataFeatures<AiOnline, Image> arg0) {
				return arg0.getValue().getPicture();
			}
		});
		col0.setCellFactory(new Callback<TableColumn<AiOnline, Image>, TableCell<AiOnline, Image>>() {
			@Override public TableCell<AiOnline, Image> call(TableColumn<AiOnline, Image> param) {
				final ImageView imageview = new ImageView();
				imageview.setFitHeight(50);
				imageview.setFitWidth(50);
				
				TableCell<AiOnline, Image> cell = new TableCell<AiOnline, Image>() {
					public void updateItem(Image item, boolean empty) {
						if (item != null)
							imageview.imageProperty().set(item);
					}
				};
				cell.setGraphic(imageview);
				return cell;
			}
			
		});
		col1.setCellValueFactory(new Callback<CellDataFeatures<AiOnline, String>, ObservableValue<String>>() {
			public ObservableValue<String> call(CellDataFeatures<AiOnline, String> p) {
				return new SimpleStringProperty(p.getValue().title);
			}
		});
		col2.setCellValueFactory(new Callback<CellDataFeatures<AiOnline, String>, ObservableValue<String>>() {
			public ObservableValue<String> call(CellDataFeatures<AiOnline, String> p) {
				return new SimpleStringProperty(p.getValue().userName);
			}
		});
		col3.setCellValueFactory(new Callback<CellDataFeatures<AiOnline, String>, ObservableValue<String>>() {
			public ObservableValue<String> call(CellDataFeatures<AiOnline, String> p) {
				return new SimpleStringProperty(p.getValue().elo);
			}
		});
		
		col0.setStyle("-fx-alignment: CENTER-LEFT;");
		col1.setStyle("-fx-alignment: CENTER-LEFT;");
		col2.setStyle("-fx-alignment: CENTER-LEFT;");
		col3.setStyle("-fx-alignment: CENTER-LEFT;");
		
		tvAis.getColumns().add(col0);
		tvAis.getColumns().add(col1);
		tvAis.getColumns().add(col2);
		tvAis.getColumns().add(col3);
		
		
		
		TableColumn<Version, String> colV0 = new TableColumn<>("Version");
		TableColumn<Version, String> colV1 = new TableColumn<>("Kompiliert");
		TableColumn<Version, String> colV2 = new TableColumn<>("Qualifiziert");
		TableColumn<Version, String> colV3 = new TableColumn<>("Freigegeben");
		
		colV0.setCellValueFactory(new Callback<CellDataFeatures<Version, String>, ObservableValue<String>>() {
			public ObservableValue<String> call(CellDataFeatures<Version, String> p) {
				return new SimpleStringProperty(p.getValue().number + "");
			}
		});
		colV1.setCellValueFactory(new Callback<CellDataFeatures<Version, String>, ObservableValue<String>>() {
			public ObservableValue<String> call(CellDataFeatures<Version, String> p) {
				if (p.getValue().compiled)
					return new SimpleStringProperty("Ja");
				else
					return new SimpleStringProperty("Nein");
			}
		});
		colV2.setCellValueFactory(new Callback<CellDataFeatures<Version, String>, ObservableValue<String>>() {
			public ObservableValue<String> call(CellDataFeatures<Version, String> p) {
				if (p.getValue().qualified)
					return new SimpleStringProperty("Ja");
				else
					return new SimpleStringProperty("Nein");
			}
		});
		colV3.setCellValueFactory(new Callback<CellDataFeatures<Version, String>, ObservableValue<String>>() {
			public ObservableValue<String> call(CellDataFeatures<Version, String> p) {
				if (p.getValue().finished)
					return new SimpleStringProperty("Ja");
				else
					return new SimpleStringProperty("Nein");
			}
		});
		
		colV0.setStyle("-fx-alignment: CENTER;");
		colV1.setStyle("-fx-alignment: CENTER;");
		colV2.setStyle("-fx-alignment: CENTER;");
		colV3.setStyle("-fx-alignment: CENTER;");
		
		tvVersions.getColumns().add(colV0);
		tvVersions.getColumns().add(colV1);
		tvVersions.getColumns().add(colV2);
		tvVersions.getColumns().add(colV3);
		tvVersions.setFixedCellSize(25);
		
		
		
		TableColumn<GameBase, String> colG0 = new TableColumn<>("Gegner");
		TableColumn<GameBase, String> colG1 = new TableColumn<>("zum Spiel");
		TableColumn<GameBase, String> colG2 = new TableColumn<>("Datum");
		TableColumn<GameBase, String> colG3 = new TableColumn<>("gespielte Zeit");
		TableColumn<GameBase, String> colG4 = new TableColumn<>("Gewonnen?");
		
		tvGames.getColumns().add(colG0);
		tvGames.getColumns().add(colG1);
		tvGames.getColumns().add(colG2);
		tvGames.getColumns().add(colG3);
		tvGames.getColumns().add(colG4);
		
	}
	
	
	public void showAi(AiOnline aai) {
		ai = aai;
		showAi();
	}
	
	
	public void showAi() {
		if (ai != null) {
			lbName.setText(ai.title);
			tbDescription.setText(ai.description);
			lbUser.setText(ai.userName);
			lbElo.setText(ai.elo);
			lbLanguage.setText(ai.language.toString());
			btChallenge.setDisable(false);
			imageView.imageProperty().unbind();
			imageView.imageProperty().bind(ai.getPicture());
			
			tvVersions.setItems(ai.versions);
			if (ai.versions.size() != 0) {
				tvVersions.prefHeightProperty().bind(tvVersions.fixedCellSizeProperty().multiply(Bindings.size(tvVersions.getItems()).add(1.25)));
				tvVersions.minHeightProperty().bind(tvVersions.prefHeightProperty());
				tvVersions.maxHeightProperty().bind(tvVersions.prefHeightProperty());
			} else {
				tvVersions.prefHeightProperty().set(60);
				tvVersions.minHeightProperty().set(60);
				tvVersions.maxHeightProperty().set(60);
			}
			tvGames.setItems(ai.onlineGames);
			if (ai.onlineGames.size() != 0) {
				tvGames.prefHeightProperty().bind(tvGames.fixedCellSizeProperty().multiply(Bindings.size(tvGames.getItems()).add(1.25)));
				tvGames.minHeightProperty().bind(tvGames.prefHeightProperty());
				tvGames.maxHeightProperty().bind(tvGames.prefHeightProperty());
			} else {
				tvGames.prefHeightProperty().set(60);
				tvGames.minHeightProperty().set(60);
				tvGames.maxHeightProperty().set(60);
			}
			
			if (ai.userName.equals(MainApp.webConnector.userName)) {
				vbContent.getChildren().remove(hbEdit);
				vbContent.getChildren().add(1, hbEdit);
				btChallenge.setText("Löschen");
				btAbort.setVisible(false);
				btEdit.setText("Bearbeiten");
				tbDescription.setEditable(false);
			} else {
				vbContent.getChildren().remove(hbEdit);
				btChallenge.setText("Herausfordern");
			}
			
		} else {
			lbName.setText("Null");
			tbDescription.setText("Aktuell wird keine KI angezeigt");
			lbUser.setText("Keiner");
			lbElo.setText("1000");
			lbLanguage.setText("Java");
			btChallenge.setDisable(true);
			imageView.imageProperty().unbind();
			imageView.imageProperty().set(Resources.defaultPicture());
			tvVersions.setItems(null);
			tvVersions.prefHeightProperty().set(60);
			tvVersions.minHeightProperty().set(60);
			tvVersions.maxHeightProperty().set(60);
			tvGames.setItems(null);
			tvGames.prefHeightProperty().set(60);
			tvGames.minHeightProperty().set(60);
			tvGames.maxHeightProperty().set(60);
			
			vbContent.getChildren().remove(hbEdit);
			btChallenge.setText("Herausfordern");
		}
	}
	
	
	@FXML public void clickChallenge() {
		if (btChallenge.getText().equals("Löschen")) {
			MainApp.webConnector.deleteKI(ai.id);
		} else {
			
		}
	}
	
	
	@FXML public void clickAbort() {
		btAbort.setVisible(false);
		btEdit.setText("Bearbeiten");
		tbDescription.setEditable(false);
		tbDescription.setText(ai.description);
	}
	
	
	@FXML public void clickEdit() {
		if (!btAbort.isVisible()) {
			btAbort.setVisible(true);
			btEdit.setText("Speichern");
			tbDescription.setEditable(true);
		} else {
			btAbort.setVisible(false);
			btEdit.setText("Bearbeiten");
			tbDescription.setEditable(false);
			ai.description = tbDescription.getText();
			MainApp.webConnector.changeDescription(ai.description, ai.id);
		}
	}
	
	
	public void clickChangeAi(AiOnline selected) {
		showAi(selected);
	}
	
	
}
