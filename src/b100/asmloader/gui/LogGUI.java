package b100.asmloader.gui;

import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.DefaultCaret;

import b100.asmloader.gui.utils.GridPanel;
import b100.asmloader.gui.utils.GuiPrintStream;

@SuppressWarnings("serial")
public class LogGUI extends GridPanel implements ActionListener {
	
	protected JTextArea textArea;
	protected JButton clearButton;
	protected JScrollPane scrollPane;
	
	public LogGUI() {
		getGridBagConstraints().insets = new Insets(4, 4, 4, 4);

		textArea = new JTextArea();
		textArea.setFont(new Font("monospaced", 1, 12));
		textArea.setEditable(false);
		textArea.setAutoscrolls(true);
		
		DefaultCaret defaultCaret = (DefaultCaret) textArea.getCaret();
		defaultCaret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		
		clearButton = new JButton("Clear");
		clearButton.addActionListener(this);
		
		scrollPane = new JScrollPane(textArea);
		
		add(scrollPane, 0, 0, 1, 1);
		add(clearButton, 0, 1, 1, 0);

		System.setOut(new GuiPrintStream(System.out, textArea));
		System.setErr(new GuiPrintStream(System.err, textArea));
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == clearButton) {
			clear();
		}
	}
	
	public void clear() {
		textArea.setText("");
	}

}
