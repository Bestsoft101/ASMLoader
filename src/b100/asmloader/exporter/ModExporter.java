package b100.asmloader.exporter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.tree.ClassNode;

import b100.asmloader.ClassTransformer;
import b100.asmloader.internal.ASMHelper;
import b100.asmloader.internal.LoaderUtils;
import b100.asmloader.internal.Log;
import b100.asmloader.internal.ModInfo;
import b100.utils.FileUtils;

public class ModExporter implements Log {
	
	public final List<File> modFiles = new ArrayList<>();
	
	public File minecraftJar;
	public File outputFile;
	
	public boolean includeOverrides = true;
	public boolean includeModFiles = true;
	
	protected List<ModInfo> modInfos = new ArrayList<>();
	protected List<ClassTransformer> classTransformers = new ArrayList<>();
	protected Map<String, ClassNode> allClasses = new HashMap<>();
	protected Set<String> modifiedClasses = new HashSet<>();
	
	protected LoaderUtils loaderUtils = new LoaderUtils(this);
	
	protected ClassLoader classLoader;
	
	public void run() throws IOException {
		if(outputFile == null) {
			outputFile = new File("asmloader-export.jar");
		}
		
		loadMods();

		if(modInfos.size() == 1) {
			print("Loaded 1 mod!");
		}else {
			print("Loaded " + modInfos.size() + " mods!");
		}
		
		createClassLoader();
		
		loadClassTransformers();
		
		if(classTransformers.size() == 1) {
			print("Loaded 1 Class Transformer!");
		}else {
			print("Loaded " + classTransformers.size() + " Class Transformers!");
		}
		
		loadMinecraftJarClasses();

		if(allClasses.size() == 1) {
			print("Loaded 1 Class!");
		}else {
			print("Loaded " + allClasses.size() + " Classes!");
		}
		
		transformMinecraftClasses();
		
		export();
		
		print("Done!");
	}
	
	protected void loadMods() {
		for(int i=0; i < modFiles.size(); i++) {
			File modFile = modFiles.get(i);
			
			ModInfo modInfo;
			
			try{
				modInfo = loaderUtils.readModInfo(modFile, false);
			}catch (Exception e) {
				throw new ModExporterException("Could not read mod: '" + modFile.getAbsolutePath() + "'!", e);
			}
			
			print(modFile.getName() + ": " + modInfo.modid + ", " + modInfo.transformerClasses.size() + " transformers");
			
			modInfos.add(modInfo);
		}
	}
	
	protected void createClassLoader() throws IOException {
		URL[] urls = new URL[modInfos.size()];
		
		for(int i=0; i < modInfos.size(); i++) {
			ModInfo mod = modInfos.get(i);
			
			URL url;
			if(mod.file.isFile()) {
				url = new URL("jar:file:" + mod.file.getAbsolutePath() + "!/");
			}else {
				url = mod.file.toURI().toURL();
			}
			urls[i] = url;
		}
		
		this.classLoader = new URLClassLoader(urls);
	}
	
	protected void loadClassTransformers() {
		for(int i=0; i < modInfos.size(); i++) {
			loaderUtils.loadClassTransformers(classLoader, modInfos.get(i), classTransformers);
		}
	}
	
	protected void loadMinecraftJarClasses() {
		ZipFile zipFile;
		try {
			zipFile = new ZipFile(minecraftJar);
		} catch (IOException e) {
			throw new RuntimeException("Could not open minecraft jar! ", e);
		}
		
		Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
		while(enumeration.hasMoreElements()) {
			ZipEntry entry = enumeration.nextElement();	
			
			String name = entry.getName();
			if(!name.endsWith(".class")) {
				continue;
			}
			
			String className = name;
			className = className.substring(0, className.length() - 6);
			
			InputStream in;
			byte[] bytes;
			try {
				in = zipFile.getInputStream(entry);
				bytes = readAll(in);
			}catch (Exception e) {
				throw new RuntimeException("Could not read class: '"+className+"'!", e);
			}
			
			ClassNode classNode = ASMHelper.getClassNode(bytes);
			
			allClasses.put(className, classNode);
		}
		
		try {
			zipFile.close();
		}catch (Exception e) {}
	}
	
	protected void transformMinecraftClasses() {
		for(String className : allClasses.keySet()) {
			ClassNode classNode = allClasses.get(className);
			
			boolean modified = false;
			
			for(ClassTransformer classTransformer : classTransformers) {
				if(classTransformer.accepts(className)) {
					print("Transforming " + className);
					classTransformer.transform(className, classNode);
					
					modified = true;
				}
			}
			
			if(modified) {
				modifiedClasses.add(className);
			}
		}
	}
	
	protected void export() throws IOException {
		FileOutputStream fileOutputStream;
		ZipOutputStream zipOutputStream;
		FileUtils.createFolderForFile(outputFile);
		try {
			fileOutputStream = new FileOutputStream(outputFile);
			zipOutputStream = new ZipOutputStream(fileOutputStream);
		}catch (Exception e) {
			throw new RuntimeException("Could not open output file: "+outputFile.getAbsolutePath(), e);
		}
		
		Set<String> writtenEntries = new HashSet<>();
		
		if(includeOverrides) {
			// Write modified classes into jar
			for(String className : modifiedClasses) {
				String classPath = className + ".class";
				
				ClassNode classNode = allClasses.get(className);
				byte[] bytes = ASMHelper.getBytes(classNode);
				
				writeToZip(zipOutputStream, classPath, bytes);
				writtenEntries.add(classPath);
			}
		}
		if(includeModFiles) {
			// Write other resources into jar
			for(ModInfo mod : modInfos) {
				ZipFile zipFile = null;
				
				List<String> allResources = new ArrayList<>();
				
				if(mod.file.isFile()) {
					zipFile = new ZipFile(mod.file);
					
					Enumeration<? extends ZipEntry> entries = zipFile.entries();
					while(entries.hasMoreElements()) {
						ZipEntry entry = entries.nextElement();
						if(entry.getName().endsWith("/")) {
							continue;
						}
						allResources.add(entry.getName());
					}
				}else {
					int pathLength = mod.file.getAbsolutePath().length();
					List<File> allFiles = FileUtils.getAllFiles(mod.file);
					for(File file : allFiles) {
						allResources.add(file.getAbsolutePath().substring(pathLength + 1).replace('\\', '/'));
					}
				}
				
				for(String entry : allResources) {
					print("Copy Resource: '" + entry + "'");
					if(entry.equals("asmloader.mod.json")) {
						continue;
					}
					
					if(writtenEntries.contains(entry)) {
						print("Duplicate Resource: '"+entry+"'!");
						continue;
					}
					
					InputStream in = null;
					if(zipFile != null) {
						in = zipFile.getInputStream(zipFile.getEntry(entry));
					}else {
//						File file = new File(mod.file, entry);
						in = new FileInputStream(new File(mod.file, entry));
					}
					
					try {
						byte[] data = readAll(in);
						
						zipOutputStream.putNextEntry(new ZipEntry(entry));
						zipOutputStream.write(data);
						
						writtenEntries.add(entry);
					} catch (Exception e) {
						throw new RuntimeException("Error writing to zip file!", e);
					}
					
					try {
						in.close();
					}catch (Exception e) {}
				}
				
				if(zipFile != null) {
					try {
						zipFile.close();
					}catch (Exception e) {}
				}
			}
		}
		
		try {
			zipOutputStream.close();
		}catch (Exception e) {}
		try {
			fileOutputStream.close();
		}catch (Exception e) {}
	}

	@Override
	public void print(String string) {
		System.out.print("[Mod Exporter] " + string + "\n");
	}

	@SuppressWarnings("serial")
	public static class ModExporterException extends RuntimeException {
		
		public ModExporterException(String message) {
			super(message);
		}
		
		public ModExporterException(String message, Throwable throwable) {
			super(message, throwable);
		}
		
	}
	
	private static void writeToZip(ZipOutputStream zipOutputStream, String entry, byte[] bytes) {
		try {
			zipOutputStream.putNextEntry(new ZipEntry(entry));
			zipOutputStream.write(bytes);
		}catch (Exception e) {
			throw new RuntimeException("Error to zip file: "+entry, e);
		}
	}
	
	private static byte[] readAll(InputStream inputStream) throws IOException {
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
	
}
