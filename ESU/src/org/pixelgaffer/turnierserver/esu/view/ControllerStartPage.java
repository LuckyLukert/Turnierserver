package org.pixelgaffer.turnierserver.esu.view;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.web.WebView;

public class ControllerStartPage {
	
	
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
