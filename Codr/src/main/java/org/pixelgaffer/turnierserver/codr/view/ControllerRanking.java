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
import javafx.util.Callback;

import org.pixelgaffer.turnierserver.codr.CodrAi;
import org.pixelgaffer.turnierserver.codr.CodrGame;
import org.pixelgaffer.turnierserver.codr.MainApp;
import org.pixelgaffer.turnierserver.codr.Version;
import org.pixelgaffer.turnierserver.codr.utilities.Resources;



public class ControllerRanking {
	
	
	@FXML Label lbName;
	@FXML Label lbUser;
	@FXML Label lbElo;
	@FXML Label lbLanguage;
	@FXML Button btChallenge;
	@FXML TextArea tbDescription;
	@FXML TableView<CodrAi> tvAis;
	@FXML TableView<Version> tvVersions;
	@FXML TableView<CodrGame> tvGames;
	@FXML ImageView imageView;
	
	MainApp mainApp;
	CodrAi ai;
	
	
	/**
	 * Initialisiert den Controller
	 * 
	 * @param app eine Referenz auf die MainApp
	 */
	public void setMainApp(MainApp app) {
		mainApp = app;
		mainApp.cRanking = this;
		tvAis.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			clickChangeAi(newValue);
		});
		
		mainApp.loadOnlineAis();
		tvAis.setItems(MainApp.onlineAis);
		
		TableColumn<CodrAi, Image> col0 = new TableColumn<CodrAi, Image>("Bild");
		col0.setMaxWidth(60);
		col0.setMinWidth(60);
		TableColumn<CodrAi, String> col1 = new TableColumn<CodrAi, String>("Name");
		TableColumn<CodrAi, String> col2 = new TableColumn<CodrAi, String>("Besitzer");
		TableColumn<CodrAi, String> col3 = new TableColumn<CodrAi, String>("ELO");
		col3.setMaxWidth(60);
		col3.setMinWidth(60);
		
		col0.setCellValueFactory(new Callback<CellDataFeatures<CodrAi, Image>, ObservableValue<Image>>() {
			@Override public ObservableValue<Image> call(CellDataFeatures<CodrAi, Image> arg0) {
				return arg0.getValue().getPicture();
			}
		});
		col0.setCellFactory(new Callback<TableColumn<CodrAi, Image>, TableCell<CodrAi, Image>>() {
			@Override public TableCell<CodrAi, Image> call(TableColumn<CodrAi, Image> param) {
				final ImageView imageview = new ImageView();
				imageview.setFitHeight(50);
				imageview.setFitWidth(50);
				
				TableCell<CodrAi, Image> cell = new TableCell<CodrAi, Image>() {
					public void updateItem(Image item, boolean empty) {
						if (item != null)
							imageview.imageProperty().set(item);
					}
				};
				cell.setGraphic(imageview);
				return cell;
			}
			
		});
		col1.setCellValueFactory(new Callback<CellDataFeatures<CodrAi, String>, ObservableValue<String>>() {
			public ObservableValue<String> call(CellDataFeatures<CodrAi, String> p) {
				return new SimpleStringProperty(p.getValue().title);
			}
		});
		col2.setCellValueFactory(new Callback<CellDataFeatures<CodrAi, String>, ObservableValue<String>>() {
			public ObservableValue<String> call(CellDataFeatures<CodrAi, String> p) {
				return new SimpleStringProperty(p.getValue().userName);
			}
		});
		col3.setCellValueFactory(new Callback<CellDataFeatures<CodrAi, String>, ObservableValue<String>>() {
			public ObservableValue<String> call(CellDataFeatures<CodrAi, String> p) {
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
		
		

		TableColumn<CodrGame, String> colG0 = new TableColumn<>("Gegner");
		TableColumn<CodrGame, String> colG1 = new TableColumn<>("zum Spiel");
		TableColumn<CodrGame, String> colG2 = new TableColumn<>("Datum");
		TableColumn<CodrGame, String> colG3 = new TableColumn<>("gespielte Zeit");
		TableColumn<CodrGame, String> colG4 = new TableColumn<>("Gewonnen?");
		
		tvGames.getColumns().add(colG0);
		tvGames.getColumns().add(colG1);
		tvGames.getColumns().add(colG2);
		tvGames.getColumns().add(colG3);
		tvGames.getColumns().add(colG4);
		
	}
	
	
	public void showAi(CodrAi aai) {
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
			if (ai.versions.size() != 0){
				tvVersions.prefHeightProperty().bind(tvVersions.fixedCellSizeProperty().multiply(Bindings.size(tvVersions.getItems()).add(1.25)));
				tvVersions.minHeightProperty().bind(tvVersions.prefHeightProperty());
				tvVersions.maxHeightProperty().bind(tvVersions.prefHeightProperty());
			}
			else{
				tvVersions.prefHeightProperty().set(60);
				tvVersions.minHeightProperty().set(60);
				tvVersions.maxHeightProperty().set(60);
			}
			tvGames.setItems(ai.onlineGames);
			if (ai.onlineGames.size() != 0){
				tvGames.prefHeightProperty().bind(tvGames.fixedCellSizeProperty().multiply(Bindings.size(tvGames.getItems()).add(1.25)));
				tvGames.minHeightProperty().bind(tvGames.prefHeightProperty());
				tvGames.maxHeightProperty().bind(tvGames.prefHeightProperty());
			}
			else{
				tvGames.prefHeightProperty().set(60);
				tvGames.minHeightProperty().set(60);
				tvGames.maxHeightProperty().set(60);
			}
		} else {
			lbName.setText("Null");
			tbDescription.setText("Aktuell wird keine KI angezeigt");
			lbUser.setText("Keiner");
			lbElo.setText("1000");
			lbLanguage.setText("Java");
			btChallenge.setDisable(true);
			imageView.imageProperty().set(Resources.defaultPicture());
			tvVersions.setItems(null);
			tvVersions.prefHeightProperty().set(60);
			tvVersions.minHeightProperty().set(60);
			tvVersions.maxHeightProperty().set(60);
			tvGames.setItems(null);
			tvGames.prefHeightProperty().set(60);
			tvGames.minHeightProperty().set(60);
			tvGames.maxHeightProperty().set(60);
		}
	}
	
	
	@FXML public void clickChallenge() {
		mainApp.loadOnlineAis();
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		tvAis.setItems(MainApp.onlineAis);
	}
	
	
	public void clickChangeAi(CodrAi selected) {
		showAi(selected);
	}
	
	
}
