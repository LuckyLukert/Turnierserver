package org.pixelgaffer.turnierserver.esu.view;

import org.pixelgaffer.turnierserver.esu.MainApp;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

public class ControllerStartPage{
	
	MainApp mainApp;
	@FXML Button btInfo;
	@FXML Button btDownload;
	@FXML Button btRegister;
	@FXML Button btSignIn;
	@FXML Hyperlink hlForgotPassword;
	@FXML TextField tbActualLogic;
	@FXML TextField tbEmail;
	@FXML PasswordField tbPassword;
	@FXML TitledPane tpLogic;
	@FXML TitledPane tpRegister;
	@FXML WebView wfNews;
	WebEngine webEngine;
	


	/**
	 * Initialisiert den Controller
	 * 
	 * @param app eine Referenz auf die MainApp
	 */
	public void setMainApp(MainApp app){
		mainApp = app;
		mainApp.cStart = this;

		webEngine = wfNews.getEngine();
		webEngine.setJavaScriptEnabled(true);
		webEngine.load("http://www.bundeswettbewerb-informatik.de/");
		wfNews.setZoom(0.9);
	}
	
	@FXML
	void clickInfo(){
		tbActualLogic.setText("Info geklickt");
	}
	
	@FXML
	void clickDownload(){
		tbActualLogic.setText("Download geklickt");
	}
	
	@FXML
	void clickRegister(){
		tbActualLogic.setText("Registrieren geklickt");
	}
	
	@FXML
	void clickSignIn(){
		tbActualLogic.setText("Anmelden geklickt");
	}
	
}
