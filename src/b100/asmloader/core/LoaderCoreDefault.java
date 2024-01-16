package b100.asmloader.core;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import b100.asmloader.ClassTransformer;
import b100.asmloader.internal.ASMLoader;
import b100.asmloader.internal.ASMLoaderTransformer;
import b100.asmloader.internal.LoaderUtils;
import b100.asmloader.internal.Log;
import b100.asmloader.internal.ModInfo;

/**
 * The default loader core. Mod loading is completely done in the init method, and everything is done on the system ClassLoader. 
 */
public class LoaderCoreDefault extends ASMLoaderCore implements Log {
	
	public File runDirectory = new File("").getAbsoluteFile();
	public File modsFolder = new File(runDirectory, "mods");

	public List<File> modFilesOnClassPath;
	public List<File> modFilesInModsFolder;
	
	public List<File> allModFiles = new ArrayList<>();
	public List<ModInfo> modInfos = new ArrayList<>();
	
	public Map<ClassTransformer, ModInfo> classTransformerToModMap = new HashMap<>();
	public List<ClassTransformer> classTransformers = new ArrayList<>();
	
	public LoaderUtils loaderUtils = new LoaderUtils(this);
	
	@Override
	public void preMain(Instrumentation instrumentation) {
		findModFilesAndLoadModInfos();
		
		addModFilesToSystemClassLoader(instrumentation);
		
		loadClassTransformers(getClass().getClassLoader());
		
		instrumentation.addTransformer(new ASMLoaderTransformer(classTransformers, classTransformerToModMap, this));
	}
	
	public void findModFilesAndLoadModInfos() {
		print("Run Directory: " + runDirectory.getAbsolutePath());

		if(!modsFolder.exists()) {
			modsFolder.mkdirs();
		}
		
		modFilesOnClassPath = loaderUtils.findAllModFilesOnClassPath();
		modFilesInModsFolder = loaderUtils.findAllModFilesInDirectory(modsFolder);

		allModFiles.addAll(modFilesOnClassPath);
		allModFiles.addAll(modFilesInModsFolder);
		
		loaderUtils.printModFiles(allModFiles);
		
		for(File modFile : modFilesOnClassPath) {
			modInfos.add(loaderUtils.readModInfo(modFile, true));
		}
		for(File modFile : modFilesInModsFolder) {
			modInfos.add(loaderUtils.readModInfo(modFile, false));
		}
		
		loaderUtils.checkModSet(modInfos);
	}
	
	public void addModFilesToSystemClassLoader(Instrumentation instrumentation) {
		for(File modFile : modFilesInModsFolder) {
			JarFile jarFile = null;
			// try try again
			try {
				try {
					jarFile = new JarFile(modFile);
				}catch (Exception e) {
					throw new RuntimeException("Could not read jar file: '" + modFile.getAbsolutePath() + "'!", e);
				}
				try{
					instrumentation.appendToSystemClassLoaderSearch(jarFile);	
				}catch (Exception e) {
					throw new RuntimeException("Could not add jar file to class path: '" + modFile.getAbsolutePath() + "'!", e);
				}
			}finally {
				try {
					jarFile.close();
				}catch (Exception e) {}
			}
		}
	}
	
	public void loadClassTransformers(ClassLoader classLoader) {
		for(ModInfo modInfo : modInfos) {
			List<ClassTransformer> thisModsClassTransformers = new ArrayList<>();
			
			loaderUtils.loadClassTransformers(classLoader, modInfo, thisModsClassTransformers);
			
			for(ClassTransformer classTransformer : thisModsClassTransformers) {
				classTransformerToModMap.put(classTransformer, modInfo);
			}
			
			classTransformers.addAll(thisModsClassTransformers);
		}
		
		loaderUtils.printClassTransformers(classTransformers, classTransformerToModMap);
	}
	
	@Override
	public void print(String string) {
		ASMLoader.log(string);
	}

}
