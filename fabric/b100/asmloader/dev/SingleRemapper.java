package b100.asmloader.dev;

import org.objectweb.asm.commons.Remapper;

class SingleRemapper extends Remapper {
	
	private String oldName;
	private String newName;
	
	public SingleRemapper(String oldName, String newName) {
		this.oldName = oldName;
		this.newName = newName;
	}
	
	@Override
	public String map(String internalName) {
		if(internalName.equals(oldName)) {
			return newName;
		}
		return super.map(internalName);
	}
	
}