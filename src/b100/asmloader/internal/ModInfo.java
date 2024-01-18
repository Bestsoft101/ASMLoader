package b100.asmloader.internal;

import java.io.File;
import java.util.List;

import b100.asmloader.ClassTransformer;

public class ModInfo {
	
	/**
	 * This mod's file. May be a zip / jar file, or a directory!
	 */
	public final File file;
	
	/**
	 * This mod's Mod ID. There can not be multiple mods with the same Mod ID!
	 */
	public final String modid;
	
	/**
	 * This mod's version! May be null!
	 */
	public final String version;
	
	/**
	 * All names of this mod's {@link ClassTransformer} classes. May be null!
	 */
	public final List<String> transformerClasses;
	
	public final boolean isOnClassPath;

	public ModInfo(File file, String modid, String version, List<String> transformerClasses, boolean isOnClassPath) {
		if(file == null) {
			throw new NullPointerException("Mod file is null!");
		}
		if(modid == null) {
			throw new NullPointerException("Mod ID is null!");
		}
		
		this.file = file;
		this.modid = modid;
		this.version = version;
		this.transformerClasses = transformerClasses;
		this.isOnClassPath = isOnClassPath;
	}
	
	@Override
	public String toString() {
		return modid;
	}

}
