package org.pixelgaffer.turnierserver.codr.utilities;


import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Tab;

import org.pixelgaffer.turnierserver.codr.CodrAi.AiMode;
import org.pixelgaffer.turnierserver.codr.view.ControllerStartPage;



public class Settings {
	
	
	public static String webUrl = "localhost:5000";
	
	
	
	public void store(ControllerStartPage cStart) {
		Properties prop = new Properties();
		prop.setProperty("webUrl", webUrl);
		
		if (cStart == null) {
			System.out.println("Konnte Einstellungen nicht speichern");
		} else {
			prop.setProperty("theme", cStart.btTheme.isSelected() + "");
			prop.setProperty("fontSize", cStart.slFontSize.getValue() + "");
			prop.setProperty("scrollSpeed", cStart.slScrollSpeed.getValue() + "");
			prop.setProperty("pythonInterpreter", cStart.tbPythonInterpreter.getText());
			prop.setProperty("cplusplusCompiler", cStart.tbCplusplusCompiler.getText());
		}
		
		try {
			Writer writer = new FileWriter(Paths.settings());
			prop.store(writer, "Settings");
			writer.close();
		} catch (IOException e) {
			ErrorLog.write("Es kann keine Settings-Datei angelegt werden.");
			return;
		}
	}
	
	
	public void loadUrl() {
		Properties prop = new Properties();
		
		try {
			Reader reader = new FileReader(Paths.settings());
			prop.load(reader);
			reader.close();
			
		} catch (IOException e) {
			ErrorLog.write("Fehler bei Laden aus der settings.txt");
			return;
		}
		
		String newUrl = prop.getProperty("webUrl");
		if (newUrl != null) {
			webUrl = newUrl;
		}
	}
	
	
	public void load(ControllerStartPage cStart) {
		Properties prop = new Properties();
		
		try {
			Reader reader = new FileReader(Paths.settings());
			prop.load(reader);
			reader.close();
		} catch (IOException e) {
			ErrorLog.write("Fehler bei Laden aus der settings.txt");
			return;
		}
		String newUrl = prop.getProperty("webUrl");
		if (newUrl != null) {
			webUrl = newUrl;
		}
		
		if (cStart == null) {
			System.out.println("Konnte Einstellungen nicht laden  (Fatal ERROR)");
		} else {
			try {
				cStart.btTheme.setSelected(Boolean.parseBoolean(prop.getProperty("theme")));
				cStart.slFontSize.setValue(Double.parseDouble(prop.getProperty("fontSize")));
				cStart.slScrollSpeed.setValue(Double.parseDouble(prop.getProperty("scrollSpeed")));
				cStart.tbPythonInterpreter.setText(prop.getProperty("pythonInterpreter"));
				cStart.tbCplusplusCompiler.setText(prop.getProperty("cplusplusCompiler"));
				
			} catch (NullPointerException e) {
				System.out.println("Konnte Einstellungen nicht laden");
			}
		}
	}
	
}
