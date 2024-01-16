package b100.asmloader.gui;

import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

import b100.asmloader.exporter.ModExporter;
import b100.asmloader.exporter.ModExporter.ModExporterException;
import b100.asmloader.gui.utils.FileDropHandler;
import b100.asmloader.gui.utils.FileDropListener;
import b100.asmloader.gui.utils.FileTextField;
import b100.asmloader.gui.utils.GridPanel;
import b100.asmloader.gui.utils.ModList;
import b100.asmloader.internal.LoaderUtils;
import b100.asmloader.internal.Log;
import b100.asmloader.internal.ModInfo;

@SuppressWarnings("serial")
public class ModExporterGUI extends GridPanel implements ActionListener, FileDropListener, Log, Runnable {
	
	public ASMLoaderGUI asmLoaderGUI;
	
	public JTextField minecraftJarTextField;
	public JTextField outputJarTextField;
	
	public ModList modList;
	
	public JCheckBox includeOverridesCheckbox;
	public JCheckBox includeModFilesCheckbox;
	
	public JButton exportButton;
	
	public LoaderUtils loaderUtils = new LoaderUtils(this);
	
	public ModExporterGUI(ASMLoaderGUI asmLoaderGUI) {
		this.asmLoaderGUI = asmLoaderGUI;

		getGridBagConstraints().insets = new Insets(4, 4, 4, 4);
		defaultWeightX = 1.0;
		defaultWeightY = 0.0;
		
		// Setup Components
		minecraftJarTextField = new FileTextField();
		outputJarTextField = new FileTextField();
		
		modList = new ModList();
		FileDropHandler.addFileDropListener(modList.list, this);
		
		includeOverridesCheckbox = new JCheckBox("Overrides");
		includeOverridesCheckbox.setSelected(true);
		
		includeModFilesCheckbox = new JCheckBox("Mod Files");
		includeModFilesCheckbox.setSelected(true);
		
		exportButton = new JButton("Export");
		exportButton.addActionListener(this);
		
		// Setup Layout Elements
		GridPanel filesPanel = new GridPanel(4, 0.0, 0.0);
		filesPanel.setBorder(new TitledBorder("Files"));
		filesPanel.add(new JLabel("Minecraft Jar"), 0, 0, 0, 0);
		filesPanel.add(new JLabel("Output Jar"), 0, 1, 0, 0);
		filesPanel.add(minecraftJarTextField, 1, 0, 1, 0);
		filesPanel.add(outputJarTextField, 1, 1, 1, 0);
		
		GridPanel modsPanel = new GridPanel(4, 1.0, 1.0);
		modsPanel.setBorder(new TitledBorder("Mods"));
		modsPanel.add(modList.list, 0, 0);
		
		GridPanel includePanel = new GridPanel(0, 1.0, 1.0);
		includePanel.setBorder(new TitledBorder("Include"));
		includePanel.add(includeOverridesCheckbox, 0, 0);
		includePanel.add(includeModFilesCheckbox, 1, 0);
		
		add(filesPanel, 0, 0);
		add(modsPanel, 0, 1, 1.0, 1.0);
		add(includePanel, 0, 2);
		add(exportButton, 0, 3);
	}

	@Override
	public void onFileDrop(List<File> files) {
		new Thread(() -> {
			if(files.size() == 0) {
				return;
			}
			if(files.size() == 1) {
				File file = files.get(0);
				
				// Dropped folder that is not a mod, add all mods in folder
				if(file.isDirectory() && !loaderUtils.peepModJson(file)) {
					File[] filesInDirectory = file.listFiles();
					
					if(filesInDirectory != null && filesInDirectory.length > 0) {
						addMultipleMods(LoaderUtils.toList(filesInDirectory));
					}
					
					return;
				}
				
				addMod(file);
			}else {
				addMultipleMods(files);	
			}
		}).start();
	}
	
	protected void addMultipleMods(List<File> files) {
		List<File> erroredFiles = new ArrayList<>();
		
		for(File file : files) {
			if(!loaderUtils.peepModJson(file)) {
				continue;
			}
			
			ModInfo modInfo;
			try {
				modInfo = loaderUtils.readModInfo(file, false);
			}catch (Exception e) {
				erroredFiles.add(file);
				System.err.println("Error while reading mod '" + file.getName() + "'!");
				e.printStackTrace();
				continue;
			}
			modList.add(modInfo);
		}
		
		if(erroredFiles.size() == 1) {
			File file = erroredFiles.get(0);

			JOptionPane.showMessageDialog(this, "Error while reading mod '" + file.getName() + "'! Check the log for more information.", "Error", JOptionPane.ERROR_MESSAGE);
		}else if(erroredFiles.size() > 1) {
			JOptionPane.showMessageDialog(this, erroredFiles.size() + " mods could not be added! Check the log for more information.", "Error", JOptionPane.ERROR_MESSAGE);
		}
		modList.onUpdate();
	}
	
	protected void addMod(File file) {
		if(!loaderUtils.peepModJson(file)) {
			JOptionPane.showMessageDialog(this, "File '" + file.getName() + "' does not contain a mod!", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		ModInfo modInfo;
		try {
			modInfo = loaderUtils.readModInfo(file, false);
		}catch (Exception e) {
			JOptionPane.showMessageDialog(this, "Error while reading file '" + file.getName() + "'! Check the log for more information.", "Error", JOptionPane.ERROR_MESSAGE);
			System.err.println("Error while reading mod '" + file.getName() + "'!");
			e.printStackTrace();
			return;
		}
		
		modList.add(modInfo);
		modList.onUpdate();
	}

	@Override
	public void run() {
		exportButton.setEnabled(false);
		
		asmLoaderGUI.log.clear();
		
		try {
			ModExporter exporter = new ModExporter();
			
			if(minecraftJarTextField.getText().length() > 0) {
				File minecraftJarFile = new File(minecraftJarTextField.getText());
				if(minecraftJarFile.exists()) {
					exporter.minecraftJar = minecraftJarFile;
				}else {
					JOptionPane.showMessageDialog(this, "File '" + minecraftJarFile.getAbsolutePath() + "' does not exist!", "Failure", JOptionPane.ERROR_MESSAGE);
					return;
				}
			}else {
				JOptionPane.showMessageDialog(this, "No Minecraft Jar provided!", "Failure", JOptionPane.ERROR_MESSAGE);
				return;
			}
			
			File outputJarFile;
			if(outputJarTextField.getText().length() > 0) {
				outputJarFile = new File(outputJarTextField.getText());
			}else {
				outputJarFile = new File("asmloader-export.jar");
			}
			
			if(outputJarFile.isDirectory()) {
				JOptionPane.showMessageDialog(this, "Output Jar File is a directory!", "Failure", JOptionPane.ERROR_MESSAGE);
				return;
			}
			
			exporter.outputFile = outputJarFile;
			
			exporter.includeModFiles = includeModFilesCheckbox.isSelected();
			exporter.includeOverrides = includeOverridesCheckbox.isSelected();
			
			if(modList.getSize() == 0) {
				JOptionPane.showMessageDialog(this, "No Mods provided!", "Failure", JOptionPane.ERROR_MESSAGE);
				return;
			}
			
			if(outputJarFile.exists()) {
				if(JOptionPane.showConfirmDialog(this, "Output file '" + outputJarFile.getAbsolutePath() + "' already exists! Do you want to override it?", "", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
					return;
				}
			}
			
			for(int i=0; i < modList.getSize(); i++) {
				ModInfo modInfo = modList.get(i);
				
				exporter.modFiles.add(modInfo.file);
			}

			asmLoaderGUI.showLog();
			
			try {
				exporter.run();

				JOptionPane.showMessageDialog(this, "Jar file has been saved to '" + exporter.outputFile.getAbsolutePath() + "'!", "Success", JOptionPane.INFORMATION_MESSAGE);
			}catch (ModExporterException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), "Failure", JOptionPane.ERROR_MESSAGE);
				e.printStackTrace();
			}catch (Exception e) {
				JOptionPane.showMessageDialog(this, "An Unexpected Error occurred. Please check the log for more information.", "Failure", JOptionPane.ERROR_MESSAGE);
				e.printStackTrace();
			}
		}finally {
			exportButton.setEnabled(true);
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == exportButton) {
			new Thread(this).start();
		}
	}

	@Override
	public void print(String string) {
		System.out.print("[Mod Exporter] " + string + "\n");
	}

}
