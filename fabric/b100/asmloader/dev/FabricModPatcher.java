package b100.asmloader.dev;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import b100.asmloader.ClassTransformer;
import b100.asmloader.internal.ASMHelper;
import b100.asmloader.internal.LoaderUtils;
import b100.asmloader.internal.LoaderUtilsFabric;
import b100.asmloader.internal.Log;
import b100.asmloader.internal.ModInfo;
import b100.json.element.JsonArray;
import b100.json.element.JsonObject;
import b100.json.element.JsonString;
import b100.utils.FileUtils;
import b100.utils.StreamUtils;
import b100.utils.StringUtils;
import b100.utils.interfaces.Condition;

/**
 * This tool adds all files necessary to run ASMLoader mods on Fabric into a ASMLoader mod 
 */
public class FabricModPatcher implements Log {
	
	public static void patchAllModsInDirectory(File dir, File minecraftJar) {
		FabricModPatcher fabricModPatcher = new FabricModPatcher();
		fabricModPatcher.minecraftJar = minecraftJar;
		
		File[] files = dir.listFiles();
		for(int i=0; i < files.length; i++) {
			File file = files[i];
			
			if(fabricModPatcher.loaderUtils.peepModJson(file)) {
				fabricModPatcher.modFile = file;
				fabricModPatcher.outputFile = null;
				try {
					fabricModPatcher.run();
				}catch (Exception e) {
					System.err.println("Could not patch mod: '" + file.getAbsolutePath() + "'!");
					e.printStackTrace();
				}
			}
		}
	}
	
	public File minecraftJar;
	public File modFile;
	public File outputFile;
	
	private File workDir;
	
	private String modid;
	private String version;
	
	protected LoaderUtils loaderUtils = new LoaderUtilsFabric(this);
	
	private List<ClassTransformer> classTransformers;
	private Set<String> mixinClassList;
	
	public void run() {
		workDir = createEmptyDirectory(new File("temp"));
		
		if(outputFile == null) {
			outputFile = new File(getNewName(modFile.getName()));
		}
		
		copyASMLoaderClasses();
		
		print("Unpack " + modFile.getName() + " into " + workDir.getName());
		unpackZipFile(modFile, workDir, null);
		
		print("Read Mod Info");
		readModInfo();
		
		print("Mod ID: '" + modid + "'");
		print("Mod Version: " + version);
		print("Class Transformers: " + classTransformers.size());
		
		print("Create Fabric Jsons");
		createFabricModJson();
		
		createMixinClassList();
		
		addFabricClasses();

		print("Pack Mod: " + outputFile.getName());
		packZipFile(outputFile, workDir);
	}
	
	/**
	 * Copy ClassTransformer and util classes
	 */
	public void copyASMLoaderClasses() {
		File file = LoaderUtils.getClassPath(FabricModPatcher.class).toFile();
		
		Condition<String> shouldCopy = (entry) -> !entry.startsWith("b100/asmloader/") || entry.equals("b100/asmloader/ClassTransformer.class");
		
		if(file.isDirectory()) {
			int pathLength = file.getAbsolutePath().length();
			
			List<File> allFiles = FileUtils.getAllFiles(new File(file, "b100"));
			
			for(int i=0; i < allFiles.size(); i++) {
				File oldFile = allFiles.get(i);
				
				String entry = oldFile.getAbsolutePath().substring(pathLength + 1).replace('\\', '/');
				
				if(!shouldCopy.isTrue(entry)) {
					continue;
				}
				
				File newFile = new File(workDir, entry);
				
				FileUtils.copy(oldFile, newFile);
			}
		}else {
			unpackZipFile(file, workDir, shouldCopy);
		}
	}
	
	public void readModInfo() {
		ModInfo modInfo = loaderUtils.readModInfo(modFile, false);
		
		this.modid = modInfo.modid;
		if(modInfo.version != null) {
			this.version = modInfo.version;
		}else {
			this.version = "UNKNOWN";
		}
		
		ClassLoader classLoader;
		try {
			classLoader = new URLClassLoader(new URL[] { modFile.toURI().toURL() });
		}catch (Exception e) {
			throw new RuntimeException("Creating URL ClassLoader", e);
		}
		
		classTransformers = new ArrayList<>();
		loaderUtils.loadClassTransformers(classLoader, modInfo, classTransformers);
	}
	
	public void createFabricModJson() {
		if(modid == null) {
			throw new NullPointerException("Mod ID is null!");
		}
		
		File fabricModJsonFile = new File(workDir, "fabric.mod.json");
		JsonObject fabricModJson = new JsonObject();
		fabricModJson.set("schemaVersion", 1);
		fabricModJson.set("id", modid);
		fabricModJson.set("version", version);
		fabricModJson.set("environment", "*");
		fabricModJson.set("mixins", new JsonArray(1).set(0, new JsonString(modid + ".mixins.json")));
		StringUtils.saveStringToFile(fabricModJsonFile, fabricModJson.toString());
		
		File mixinsJsonFile = new File(workDir, modid + ".mixins.json");
		JsonObject mixinsJson = new JsonObject();
		mixinsJson.set("required", "true");
		mixinsJson.set("package", "asmcompat." + modid + ".mixin");
		mixinsJson.set("plugin", "asmcompat." + modid + ".Plugin");
		mixinsJson.set("mixins", new JsonArray(0));
		mixinsJson.set("injectors", new JsonObject().set("defaultRequire", 1));
		StringUtils.saveStringToFile(mixinsJsonFile, mixinsJson.toString());
	}
	
	public void createMixinClassList() {
		Set<String> allMinecraftJarClasses = new HashSet<>();
		
		ZipFile zipFile = null;
		try {
			zipFile = new ZipFile(minecraftJar);
			
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			
			while(entries.hasMoreElements()) {
				String entryName = entries.nextElement().getName();
				
				if(entryName.endsWith(".class")) {
					entryName = entryName.substring(0, entryName.length() - 6); // Remove .class
					if(!allMinecraftJarClasses.contains(entryName)) {
//						print("Class: '" + entryName + "'");
						allMinecraftJarClasses.add(entryName);
					}
				}
			}
		}catch (Exception e) {
			throw new RuntimeException("Reading minecraft jar '" + minecraftJar.getAbsolutePath() + "'", e);
		}finally {
			try {
				zipFile.close();
			}catch (Exception e) {}
		}
		
		mixinClassList = new HashSet<>();
		
		for(String className : allMinecraftJarClasses) {
			for(int i=0; i < classTransformers.size(); i++) {
				ClassTransformer classTransformer = classTransformers.get(i);
				if(classTransformer.accepts(className)) {
					mixinClassList.add(className);
				}
			}
		}
	}
	
	public void addFabricClasses() {
		File fabricZipFile = new File(workDir, "fabric.zip");
		
		extractResource("/fabric.zip", fabricZipFile);
		
		String[][] extractList = new String[][] {
				{ "asmcompat/" + modid + "/mixin", "Dummy"},
				{ "asmcompat/" + modid, "Plugin"}
		};
		
		ZipFile zipFile = null;
		try {
			zipFile = new ZipFile(fabricZipFile);
			
			for(int i=0; i < extractList.length; i++) {
				String packageName = extractList[i][0];
				String className = extractList[i][1];
				
				ZipEntry entry = zipFile.getEntry(className + ".class");
				File file = new File(workDir, packageName + "/" + className + ".class");
				File parent = file.getParentFile();
				if(!parent.exists()) {
					parent.mkdirs();
				}
				
				InputStream in = null;
				OutputStream out = null;
				
				try {
					in = zipFile.getInputStream(entry);
					
					byte[] bytes = loaderUtils.readAll(in);
					
					ClassNode classNode = ASMHelper.getClassNode(bytes);
					
					classNode = renameClass(classNode, packageName + "/" + className);
					
					List<AnnotationNode> annotations = classNode.invisibleAnnotations;
					if(annotations != null && annotations.size() > 0 && annotations.get(0).desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
						// Update Mixin Header
						
						if(mixinClassList == null) {
							throw new NullPointerException();
						}
						
						List<String> list = new ArrayList<>(mixinClassList);
						list.sort(String.CASE_INSENSITIVE_ORDER);
						
						annotations.get(0).values.set(1, list);
					}
					
					bytes = ASMHelper.getBytes(classNode);
					
					out = new FileOutputStream(file);
					out.write(bytes, 0, bytes.length);	
				}catch (Exception e) {
					throw new RuntimeException("Extracting Fabric Classes", e);
				}finally {
					try {
						in.close();
					}catch (Exception e) {}
					try {
						out.close();
					}catch (Exception e) {}
				}
			}
		}catch (Exception e) {
			throw new RuntimeException("Extracting file: '" + fabricZipFile.getAbsolutePath() + "'", e);
		}finally {
			try {
				zipFile.close();
			}catch (Exception e) {}
		}
		
		fabricZipFile.delete();
	}
	
	public ClassNode renameClass(ClassNode classNode, String newName) {
		ClassNode newNode = new ClassNode();
		
		Remapper remapper = new SingleRemapper(classNode.name, newName);
		
		ClassRemapper classRemapper = new ClassRemapper(newNode, remapper);
		
		classNode.accept(classRemapper);
		
		return newNode;
	}
	
	public File createEmptyDirectory(File file) {
		if(file.exists()) {
			if(!file.isDirectory()) {
				file.delete();
				file.mkdirs();
			}else {
				deleteAllFilesInDirectory(file);
			}
		}
		if(!file.exists()) {
			file.mkdirs();
		}
		return file;
	}
	
	public void deleteAllFilesInDirectory(File dir) {
		File[] files = dir.listFiles();
		if(files == null) {
			return;
		}
		for(int i=0; i < files.length; i++) {
			File file = files[i];
			if(file.isDirectory()) {
				deleteAllFilesInDirectory(file);
			}
			file.delete();
		}
	}
	
	public void unpackZipFile(File zip, File target, Condition<String> shouldCopy) {
		ZipFile zipFile = null;
		try {
			zipFile = new ZipFile(zip);
			
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while(entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String entryName = entry.toString();
				
				if(entryName.endsWith("/")) {
					continue;
				}
				
				if(shouldCopy != null && !shouldCopy.isTrue(entryName)) {
					continue;
				}

				File file = new File(target, entryName);
				File parent = file.getParentFile();
				if(!parent.exists()) {
					parent.mkdirs();
				}
				
//				System.out.println("Unpacking: '" + entryName + "'");
				
				try {
					InputStream in = zipFile.getInputStream(entry);
					OutputStream out = new FileOutputStream(file);
					
					transferDataAndClose(in, out);	
				}catch (Exception e) {
					throw new RuntimeException("Unpacking Zip Entry '" + entry + "'!", e);
				}
			}
		}catch (Exception e) {
			throw new RuntimeException("Unpacking Zip File '" + zip.getAbsolutePath() + "' into '" + target.getAbsolutePath() + "'!", e);
		}finally {
			try {
				zipFile.close();
			}catch (Exception e) {}
		}
	}
	
	public void packZipFile(File zip, File directory) {
		ZipOutputStream out = null;
		
		try {
			String dirPath = directory.getAbsolutePath();
			
			List<File> allFiles = FileUtils.getAllFiles(directory);
			
			for(int i=0; i < allFiles.size(); i++) {
			}
			
			out = new ZipOutputStream(new FileOutputStream(zip));
			
			for(int i=0; i < allFiles.size(); i++) {
				File file = allFiles.get(i);
				String path = file.getAbsolutePath();
				if(!path.startsWith(dirPath)) {
					throw new RuntimeException("Invalid Path: '" + path + "'!");
				}
				String entryName = path.substring(dirPath.length() + 1).replace('\\', '/');
				
//				System.out.println("Packing: '" + entryName + "'");
				
				ZipEntry entry = new ZipEntry(entryName);
				out.putNextEntry(entry);
				
				FileInputStream in = null;
				try {
					in = new FileInputStream(file);
					StreamUtils.transferData(in, out);
				}catch (Exception e) {
					throw new RuntimeException("Packing file '" + file.getAbsolutePath() + "'", e);
				}finally {
					try {
						in.close();
					}catch (Exception e) {}
				}
			}
		}catch (Exception e) {
			throw new RuntimeException("Pack directory '" + directory.getAbsolutePath() + "' into zip file '" + zip.getAbsolutePath() + "'!", e);
		}finally {
			try {
				out.close();
			}catch (Exception e) {}
		}
	}
	
	public void extractResource(String resourcePath, File file) {
		try {
			InputStream in = FabricModPatcher.class.getResourceAsStream(resourcePath);
			OutputStream out = new FileOutputStream(file);
			
			transferDataAndClose(in, out);
		}catch (Exception e) {
			throw new RuntimeException("Extracting resource '" + resourcePath + "' to '" + file.getAbsolutePath() + "'", e);
		}
	}
	
	public void transferDataAndClose(InputStream in, OutputStream out) {
		try {
			StreamUtils.transferData(in, out);
		}catch (Exception e) {
			throw new RuntimeException("Transfering Data!", e);
		}finally {
			try {
				in.close();
			}catch (Exception e) {}
			try {
				out.close();
			}catch (Exception e) {}
		}
	}
	
	public String getNewName(String name) {
		String newName;
		
		int asmloaderIndex = name.toLowerCase().indexOf("asmloader");
		int extensionPointIndex = name.lastIndexOf('.');
		
		if(extensionPointIndex == -1 || name.lastIndexOf(' ') > extensionPointIndex) {
			throw new RuntimeException("Invalid file name!");
		}
		
		// Change file extension to jar
		newName = name.substring(0, extensionPointIndex) + ".jar";
		
		if(asmloaderIndex != -1) {
			// If name contains 'ASMLOADER' replace ASMLOADER with UNIVERSAL
			newName = name.substring(0, asmloaderIndex) + "UNIVERSAL" + name.substring(asmloaderIndex + 9);
		}else {
			// If name does not contain 'ASMLOADER' add -UNIVERSAL before file extension
			newName = name.substring(0, extensionPointIndex) + "-UNIVERSAL" + name.substring(extensionPointIndex);
		}
		
		return newName;
	}

	@Override
	public void print(String string) {
		System.out.print("[FabricModPatcher] " + string + "\n");
	}
	
}
