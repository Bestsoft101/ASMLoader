package b100.asmloader.gui.utils;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.JList;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import b100.asmloader.internal.ModInfo;

public class ModList implements Iterable<ModInfo>, KeyListener {
	
	private List<ModInfo> entries = new ArrayList<>();
	private List<ListDataListener> listDataListeners = new ArrayList<>();
	
	public JList<ModInfo> list;
	
	public ModList() {
		this.list = new JList<>(new ListModelImpl());
		this.list.addKeyListener(this);
	}
	
	public int getSize() {
		return entries.size();
	}
	
	public ModInfo get(int i) {
		return entries.get(i);
	}
	
	public void add(ModInfo modInfo) {
		entries.add(modInfo);
		onUpdate();
	}
	
	public void clear() {
		entries.clear();
		onUpdate();
	}
	
	public void onUpdate() {
		ListDataEvent listDataEvent = new ListDataEvent(list, ListDataEvent.CONTENTS_CHANGED, 0, 0);
		for(ListDataListener listDataListener : listDataListeners) {
			listDataListener.contentsChanged(listDataEvent);
		}
	}

	@Override
	public Iterator<ModInfo> iterator() {
		return new IteratorImpl();
	}
	
	class ListModelImpl implements ListModel<ModInfo> {
		@Override
		public int getSize() {
			return entries.size();
		}

		@Override
		public ModInfo getElementAt(int index) {
			return entries.get(index);
		}

		@Override
		public void addListDataListener(ListDataListener l) {
			listDataListeners.add(l);
		}

		@Override
		public void removeListDataListener(ListDataListener l) {
			listDataListeners.remove(l);
		}
	}
	
	class IteratorImpl implements Iterator<ModInfo> {

		private int pos;
		
		@Override
		public boolean hasNext() {
			return pos < entries.size();
		}

		@Override
		public ModInfo next() {
			return entries.get(pos++);
		}
		
	}

	@Override
	public void keyReleased(KeyEvent e) {
		if(e.getKeyCode() == KeyEvent.VK_DELETE) {
			if(list.getSelectedIndex() != -1) {
				entries.remove(list.getSelectedIndex());
				onUpdate();
				list.clearSelection();
			}
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {
	}

	@Override
	public void keyPressed(KeyEvent e) {
	}
}
