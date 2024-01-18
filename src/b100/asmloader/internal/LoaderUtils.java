package b100.asmloader.internal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import b100.asmloader.ClassTransformer;
import b100.json.element.JsonArray;
import b100.json.element.JsonElement;
import b100.json.element.JsonObject;
import b100.utils.StringReader;
import b100.utils.StringUtils;

public class LoaderUtils {
	
	private Log logObj;
	
	public LoaderUtils(Log logObj) {
		this.logObj = logObj;
	}
	
	/**
	 * Search all files and directories on the class path for mod jsons 
	 */
	public List<File> findAllModFilesOnClassPath() {
		List<File> modFiles = new ArrayList<>();
		String[] classPathEntries = System.getProperty("java.class.path").split(System.getProperty("path.separator"));
		
		for(int i=0; i < classPathEntries.length; i++) {
			File file = new File(classPathEntries[i]);
			if(peepModJson(file)) {
				modFiles.add(file);
			}
		}
		
		return modFiles;
	}
	
	/**
	 * Search all .jar and .zip files in the given directory for mod jsons 
	 */
	public List<File> findAllModFilesInDirectory(File directory) {
		List<File> modFiles = new ArrayList<>();
		if(!directory.exists() || !directory.isDirectory()) {
			return modFiles;
		}
		
		File[] files = directory.listFiles();
		if(files == null) {
			return modFiles;
		}
		
		for(int i=0; i < files.length; i++) {
			File file = files[i];
			String name = file.getName();
			if(file.isFile() && (name.endsWith(".zip") || name.endsWith(".jar")) && peepModJson(file)) {
				modFiles.add(file);
			}
		}
		return modFiles;
	}
	
	/**
	 * Check if a mod json exists in the directory or zip file. If any error happens, return false.
	 */
	public boolean peepModJson(File file) {
		if(file.isDirectory()) {
			File modJson = new File(file, "asmloader.mod.json");
			if(modJson.exists()) {
				return true;
			}
		}else if(file.isFile()) {
			ZipFile zipFile = null;
			try {
				zipFile = new ZipFile(file);
				
				return zipFile.getEntry("asmloader.mod.json") != null;
			}catch (Exception e) {
				return false;
			}finally {
				try {
					zipFile.close();
				}catch (Exception e) {}
			}
		}
		return false;
	}
	
	
	public void printModFiles(List<File> allModFiles) {
		if(allModFiles.size() > 0) {
			if(allModFiles.size() == 1) {
				log("Found 1 mod file:");
			}else {
				log("Found " + allModFiles.size() + " mod files:");
			}
			for(File file : allModFiles) {
				log("    " + file.getAbsolutePath());
			}	
		}else {
			log("No mods found!");
		}
	}
	
	public ModInfo readModInfo(File modFile, boolean isOnClassPath) {
		try {
			String modJsonString;
			
			if(modFile.isDirectory()) {
				// Load mod in directory
				File modJsonFile = new File(modFile, "asmloader.mod.json");
				
				if(!modJsonFile.exists() || !modJsonFile.isFile()) {
					throw new RuntimeException("Mod json does not exist: '"+modJsonFile.getAbsolutePath()+"'!");
				}
				
				modJsonString = StringUtils.getFileContentAsString(modJsonFile);
			}else {
				// Load mod in jar file
				ZipFile modZipFile = null;
				InputStream in = null;
				try {
					modZipFile = new ZipFile(modFile);
					
					ZipEntry modJsonEntry = modZipFile.getEntry("asmloader.mod.json");
					
					if(modJsonEntry == null) {
						throw new NullPointerException("No mod json in zip file!");
					}
					
					in = modZipFile.getInputStream(modJsonEntry);
					modJsonString = StringUtils.readInputString(in);
				}finally {
					try {
						modZipFile.close();
					}catch (Exception e) {}
					try {
						in.close();
					}catch (Exception e) {}
				}
			}
			
			// Read Mod Json
			JsonObject modJson = new JsonObject(new StringReader(modJsonString));
			
			String modid = modJson.getString("modid");
			if(modid == null) {
				throw new NullPointerException("No Mod ID in asmloader.mod.json!");
			}
			
			String version = modJson.has("version") ? modJson.getString("version") : null;
			
			// Read transformers array
			List<String> transformers = null;
			if(modJson.has("transformers")) {
				try {
					transformers = getJsonArrayAsStringList(modJson.getArray("transformers"));
				}catch (Exception e) {
					throw new RuntimeException("Error reading transformer array!", e);
				}	
			}else {
				transformers = new ArrayList<>();
			}
			
			return new ModInfo(modFile, modid, version, transformers, isOnClassPath);
		}catch (Exception e) {
			// Add file path to the crash info
			throw new RuntimeException("Error loading mod: '" + modFile.getAbsolutePath() + "'!", e);
		}
	}
	
	public List<String> getJsonArrayAsStringList(JsonArray jsonArray) {
		if(jsonArray == null) {
			return null;
		}
		
		List<String> list = new ArrayList<>(jsonArray.length());
		
		for(int i=0; i < jsonArray.length(); i++) {
			JsonElement element = jsonArray.get(i);
			
			if(!element.isString()) {
				throw new RuntimeException("Element at index " + i + " is not a String!");
			}
			
			list.add(element.getAsString().value);
		}
		
		return list;
	}
	
	public void checkModSet(List<ModInfo> mods) {
		// Check for duplicate mods
		for(int i=0; i < mods.size(); i++) {
			ModInfo modInfo = mods.get(i);
			
			for(int j=0; j < mods.size(); j++) {
				if(j == i) {
					continue;
				}
				
				ModInfo otherModInfo = mods.get(j);
				
				if(modInfo.modid.equalsIgnoreCase(otherModInfo.modid)) {
					throw new RuntimeException("Duplicate Mod ID '" + modInfo.modid + "' in files '" + modInfo.file.getAbsolutePath() + "' and '" + otherModInfo.file.getAbsolutePath() + "'!");
				}
			}
		}
	}
	
	public void loadClassTransformers(ClassLoader classLoader, ModInfo modInfo, List<ClassTransformer> classTransformers) {
		if(modInfo.transformerClasses == null || modInfo.transformerClasses.size() == 0) {
			return;
		}
		
		Class<ClassTransformer> classTransformerClass = ClassTransformer.class;
		
		try {
			for(int i=0; i < modInfo.transformerClasses.size(); i++) {
				String transformerClassName = modInfo.transformerClasses.get(i);
				
				Class<?> transformerClass = classLoader.loadClass(transformerClassName);
				
				boolean isTransformerClass = false;
				boolean hasTransformerSubClasses = false;
				
				Object instance = transformerClass.getDeclaredConstructors()[0].newInstance();
				
				if(instance instanceof ClassTransformer) {
					isTransformerClass = true;
					
					ClassTransformer classTransformer = (ClassTransformer) instance;
					classTransformers.add(classTransformer);
				}
				
				Class<?>[] subClasses = transformerClass.getDeclaredClasses();
				for(int j=0; j < subClasses.length; j++) {
					Class<?> subClass = subClasses[j];
					
					if(!classTransformerClass.isAssignableFrom(subClass)) {
						continue;
					}
					
					hasTransformerSubClasses = true;
					Object subClassInstance = null;
					
					Constructor<?> constructor = subClass.getDeclaredConstructors()[0];

					if(!constructor.isAccessible()) {
						constructor.setAccessible(true);
					}
					
					if(constructor.getParameterCount() == 0) subClassInstance = constructor.newInstance();
					if(constructor.getParameterCount() == 1) subClassInstance = constructor.newInstance(instance);
					
					ClassTransformer classTransformer = (ClassTransformer) subClassInstance;
					classTransformers.add(classTransformer);
				}
				
				if(!isTransformerClass && !hasTransformerSubClasses) {
					StringBuilder msg = new StringBuilder();
					
					msg.append("Invalid transformer entry '" + transformerClassName + "'!\n");
					msg.append("\nNo valid ClassTransformer was found in class!\n");
					msg.append("\nClass '" + transformerClass.getName() + "' extends class '" + transformerClass.getSuperclass().getName() + "', loaded by ClassLoader '" + transformerClass.getSuperclass().getClassLoader() + "'!");
					
					for(int j=0; j < subClasses.length; j++) {
						Class<?> subClass = subClasses[j];
						msg.append("\nSubclass '" + subClass.getName() + "' extends class '" + subClass.getSuperclass().getName() + "', loaded by ClassLoader '" + subClass.getSuperclass().getClassLoader() + "'!");
					}
					
					msg.append("\n\nTransformer classes have to extend '" + classTransformerClass.getName() + "', loaded by ClassLoader '" + classTransformerClass.getClassLoader() + "'!");
					
					throw new RuntimeException(msg.toString());
					//throw new RuntimeException("Invalid transformer entry '" + transformerClassName + "'! Class does not extend b100.asmloader.ClassTransformer, and does not have any subclasses that extend b100.asmloader.ClassTransformer!");
				}
			}
		}catch (Exception e) {
			throw new RuntimeException("Error loading class transformers from mod '" + modInfo.modid + "'!", e);
		}
	}
	
	public void printClassTransformers(List<ClassTransformer> classTransformers, Map<ClassTransformer, ModInfo> classTransformerToModMap) {
		if(classTransformers.size() == 0) {
			log("No class transformers loaded!");
			return;
		}
		
		if(classTransformers.size() == 1) {
			log("Loaded 1 class transformer:");
		}else {
			log("Loaded " + classTransformers.size() + " class transformers:");
		}
		
		for(int i=0; i < classTransformers.size(); i++) {
			ClassTransformer classTransformer = classTransformers.get(i);
			
			ModInfo modInfo = classTransformerToModMap.get(classTransformer);
			
			if(modInfo == null) {
				throw new NullPointerException("ModInfo for transformer '" + classTransformer + "' is null!");
			}
			
			log("    " + classTransformer.getClass().getName() + " " + modInfo.modid);
		}
	}
	
	public List<File> toList(File[] files) {
		List<File> list = new ArrayList<>();
		for(int i=0; i < files.length; i++) {
			if(files[i] != null) {
				list.add(files[i]);
			}
		}
		return list;
	}
	
	public static Path getClassPath(Class<?> clazz) {
		try {
			return Paths.get(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
		}catch (Exception e) {
			throw new RuntimeException("Could not get source of class '" + clazz.getName() + "'!", e);
		}
	}
	
	public byte[] readAll(InputStream inputStream) throws IOException {
		final int cacheSize = 4096;
		
		ByteCache byteCache = new ByteCache();
		while(true) {
			byte[] cache = new byte[cacheSize];
			int read = inputStream.read(cache, 0, cache.length);
			if(read == -1) {
				break;
			}
			byteCache.put(cache, 0, read);
		}
		
		try {
			inputStream.close();
		}catch (Exception e) {}
		
		return byteCache.getAll();
	}
	
	void log(String string) {
		logObj.print(string);
	}

}
