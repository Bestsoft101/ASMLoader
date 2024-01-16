package b100.asmloader.gui;

import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;

import b100.asmloader.gui.utils.GridPanel;

public class ASMLoaderGUI {
	
	public JFrame frame;
	public GridPanel mainPanel;
	public JTabbedPane tabs;
	
	public ModExporterGUI exporterGUI;
	public LogGUI log;
	
	public ASMLoaderGUI() {
		frame = new JFrame("ASMLoader GUI");
		frame.setMinimumSize(new Dimension(400, 400));
		
		mainPanel = new GridPanel();
		
		exporterGUI = new ModExporterGUI(this);
		log = new LogGUI();
		
		tabs = new JTabbedPane();
		tabs.addTab("Export", exporterGUI);
		tabs.addTab("Log", log);
		tabs.setPreferredSize(new Dimension(480, 520));
		
		mainPanel.add(tabs, 0, 0, 1, 1);
		
		frame.add(mainPanel);
		frame.pack();
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	public void showLog() {
		tabs.setSelectedIndex(tabs.indexOfTab("Log"));
	}
	
	public static void main(String[] args) {
		new ASMLoaderGUI();
	}

}
