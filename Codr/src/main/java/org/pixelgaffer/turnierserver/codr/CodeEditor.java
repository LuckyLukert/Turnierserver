package org.pixelgaffer.turnierserver.codr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;

import javax.xml.parsers.ParserConfigurationException;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import org.apache.commons.io.FileUtils;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.pixelgaffer.katepartparser.EmptyStyle;
import org.pixelgaffer.katepartparser.Style;
import org.pixelgaffer.katepartparser.Styles;
import org.pixelgaffer.katepartparser.SyntaxParser;
import org.pixelgaffer.turnierserver.codr.utilities.ErrorLog;
import org.pixelgaffer.turnierserver.codr.utilities.Paths;
import org.xml.sax.SAXException;

public class CodeEditor
{
	public static void writeSyntax () throws ParserConfigurationException, SAXException
	{
		File syntaxFolder = new File(Paths.syntaxFolder());
		if (syntaxFolder.exists())
			return;
		
		try
		{
			syntaxFolder.mkdirs();
			File tmp = File.createTempFile("syntax", ".zip");
			tmp.deleteOnExit();
			FileUtils.copyURLToFile(CodeEditor.class.getResource("syntax.zip"), tmp);
			ZipFile zipFile = new ZipFile(tmp);
			zipFile.extractAll(syntaxFolder.getAbsolutePath());
			
			p = new Properties();
			for (String filename : syntaxFolder.list())
			{
				SyntaxParser parser = new SyntaxParser(new File(syntaxFolder, filename), new EmptyStyle());
				for (String extension : parser.getExtensions())
					p.put(extension.trim(), filename);
			}
			p.store(new FileOutputStream(new File(syntaxFolder, "index.prop")),
					"Enthält alle Syntax-Dateien sortiert nach den Dateienden");
		}
		catch (ZipException | IOException e)
		{
			ErrorLog.write("Konnte Syntax nicht schreiben: " + e);
			syntaxFolder.delete();
		}
	}
	
	private static Properties p = null;
	
	private static Properties props () throws IOException
	{
		if (p == null)
		{
			p = new Properties();
			p.load(new FileInputStream(new File(Paths.syntaxFolder(), "index.prop")));
		}
		return p;
	}
	
	
	private String savedText = "";
	private File document;
	public boolean loaded = false;
	private CodeArea codeArea;
	private SyntaxParser parser;
	
	/**
	 * Initialisiert den CodeEditor mit der zu zeigenden Datei
	 * 
	 * @param doc
	 */
	public CodeEditor (File doc)
	{
		System.out.println("CodeEditor::CodeEditor(" + doc + ")");
		document = doc;
		codeArea = new CodeArea();
		codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
		
		Style style = new EmptyStyle();
		try
		{
			style = MainApp.cStart.btTheme.isSelected() ? Styles.getStyle("VibrantInk") : new EmptyStyle();
		}
		catch (IOException e1)
		{
			e1.printStackTrace();
		}
		try
		{
			for (Object o : props().keySet())
			{
				String extension = (String)o;
				extension = extension.replace(".", "\\.");
				extension = extension.replace("*", ".*");
				Pattern pattern = Pattern.compile(extension, Pattern.CASE_INSENSITIVE);
				Matcher matcher = pattern.matcher(doc.getName());
				if (matcher.matches())
				{
					parser = new SyntaxParser(new File(Paths.syntaxFolder(), (String)props().get(o)), style);
					break;
				}
			}
		}
		catch (SAXException | ParserConfigurationException | IOException e)
		{
			ErrorLog.write("Fehler beim Laden des SyntaxParser: " + e);
			e.printStackTrace();
		}
		
		if (parser != null)
		{
			codeArea.setId("codeArea");
			try
			{
				codeArea.getStylesheets().add(parser.generateStylesheet("codeArea").toExternalForm());
			}
			catch (IOException e)
			{
				ErrorLog.write("Fehler beim Laden eines generierten StyleSheets: " + e);
				e.printStackTrace();
			}
			codeArea.textProperty().addListener(
					(obs, oldText, newText) -> codeArea.setStyleSpans(0, parser.computeHighlighting(newText)));
		}
		
		load();
	}
	
	
	/**
	 * überprüft, ob der angezeigte Text mit seiner gespeicherten Datei
	 * übereinstimmt
	 * 
	 * @return true, wenn savedText != text
	 */
	public boolean hasChanged ()
	{
		if (loaded)
			return !savedText.equals(getCode());
		else
			return false;
	}
	
	
	public String getCode ()
	{
		return codeArea.getText();
	}
	
	
	public void setCode (String t)
	{
		codeArea.replaceText(0, codeArea.getText().length() - 1, t);
	}
	
	
	/**
	 * Erstellt ein Tab, das in die Tab-Leiste eingefügt werden kann
	 * 
	 * @return das erstellte Tab
	 */
	public Tab getView ()
	{
		BorderPane pane = new BorderPane(codeArea);
		return new Tab(document.getName(), pane);
	}
	
	
	/**
	 * Lädt den Inhalt der Datei in die StringProperty text
	 */
	public void load ()
	{
		try
		{
			setCode(FileUtils.readFileToString(document));
			
			savedText = getCode();
			loaded = true;
		}
		catch (IOException e)
		{
			ErrorLog.write("Quellcode konnte nicht gelesen werden: " + e);
		}
	}
	
	
	/**
	 * Speichert den Inhalt der StringProperty text in die Datei
	 */
	public void save ()
	{
		if (!hasChanged())
			return;
		forceSave();
	}
	
	
	/**
	 * Speichert den Inhalt der StringProperty text in eine Datei. Dabei wird
	 * nicht Überprüft, ob sich der Inhalt verändert hat.
	 */
	public void forceSave ()
	{
		try
		{
			FileWriter writer = new FileWriter(document, false);
			writer.write(getCode());
			writer.close();
			savedText = getCode();
		}
		catch (IOException e)
		{
			ErrorLog.write("Quellcode konnte nicht bearbeitet werden");
		}
	}
	
}
