import java.awt.Image;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import b100.asmloader.core.LoaderCoreBetaCraft;

public class CustomLaunch {
	
	private File betacraftFolder;
	private File binFolder;
	private File nativesFolder;
	private File instanceFolder;
	
	private String versionName;
	private String username;
	private String sessionID;
	
	public CustomLaunch(String username, String instanceName, String versionName, String sessionID, String path, Integer height, Integer width, Boolean b1, String s6, String s7, String s8, String s9, String s10, String s11, Image image, ArrayList<?> list) {
		print(username, instanceName, versionName, sessionID, path, height, width, b1, s6, s7, s8, s9, s10, s11, image, list);
		
		this.betacraftFolder = getBetacraftFolder();
		this.versionName = versionName;
		this.username = username;
		this.sessionID = sessionID;
		
		boolean asmLoader;
		try {
			// it works!
			LoaderCoreBetaCraft.getInstance();
			asmLoader = true;
		}catch (Throwable e) {
			asmLoader = false;
		}
		
		log("Found ASMLoader: " + asmLoader);
		
		binFolder = new File(this.betacraftFolder, "bin");
		nativesFolder = new File(this.binFolder, "natives");
		instanceFolder = new File(this.betacraftFolder, instanceName);
		
		String nativesPath = nativesFolder.getAbsolutePath();
		
		log("BetaCraft Folder: " + this.betacraftFolder);
		log("Natives Path: " + nativesPath);
		
		System.setProperty("org.lwjgl.librarypath", nativesPath);
		System.setProperty("net.java.games.input.librarypath", nativesPath);
		
		List<File> launchFiles = getLaunchFiles();
		
		if(asmLoader) {
			launchFiles.addAll(LoaderCoreBetaCraft.getInstance().allModFiles);
		}
		
		log("Launch Files: " + launchFiles);
		
		URLClassLoader minecraftClassLoader = new URLClassLoader(createUrlArray(launchFiles)); 
		
		log("Minecraft Class Loader: " + minecraftClassLoader);
		
		if(asmLoader) {
			LoaderCoreBetaCraft.getInstance().finish(minecraftClassLoader);
		}
		
		launchWithClassLoader(minecraftClassLoader);
	}
	
	private void print(Object... array) {
		for(int i=0; i < array.length; i++) {
			Object obj = array[i];
			if(obj != null) {
				System.out.println(i + ": " + obj.getClass().getName() + ": " + obj);
			}else {
				System.out.println(i + ": " + null);
			}
		}
	}
	
	private void launchWithClassLoader(ClassLoader classLoader) {
		Class<?> mainClass;
		try {
			mainClass = classLoader.loadClass("net.minecraft.client.Minecraft");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		
		log("Main Class: " + mainClass + " loaded by classloader: " + mainClass.getClassLoader());
		
		try {
			log("Setting Minecraft Directory to: " + instanceFolder.getAbsolutePath());
			setMinecraftDir(mainClass, instanceFolder);
		} catch (Exception e) {
			System.err.println("Could not set Minecraft Directory!");
			e.printStackTrace();
		}
		
		Method mainMethod;
		try {
			mainMethod = mainClass.getDeclaredMethod("main", String[].class);
		} catch (Exception e) {
			throw new RuntimeException("Could not get main method!", e);
		}
		
		log("Main Method: " + mainMethod);
		
		try{
			mainMethod.invoke(null, getStringArray(username, sessionID));
		}catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private List<File> getLaunchFiles() {
		List<File> classpathFiles = new ArrayList<>();
		
		File[] files = binFolder.listFiles();
		for (int i = 0; i < files.length; i++) {
			File file = files[i];
			if (file.isFile()) {
				classpathFiles.add(file);
			}
		}
		
		classpathFiles.add(new File(this.betacraftFolder, "versions/" + versionName + ".jar"));
		return classpathFiles;
	}

	public void setMinecraftDir(Class<?> mainClass, File dir) {
		Field[] fields = mainClass.getDeclaredFields();
		
		for (int i = 0; i < fields.length; i++) {
			Field field = fields[i];
			if (field.getType() == File.class && field.getModifiers() == 10) {
				field.setAccessible(true);
				try {
					field.set(null, dir);
					return;
				} catch (Exception var7) {
					throw new RuntimeException(var7);
				}
			}
		}

		throw new RuntimeException("Could not find Minecraft Directory Field!");
	}

	public static Object getStringArray(String... string) {
		return string;
	}

	public static URL[] createUrlArray(List<File> files) {
		URL[] urls = new URL[files.size()];

		for (int i = 0; i < urls.length; ++i) {
			File file = (File) files.get(i);

			try {
				urls[i] = file.toURI().toURL();
			} catch (Exception e) {
				System.err.println(file.getAbsolutePath());
				e.printStackTrace();
			}
		}

		return urls;
	}
	
	private static File getBetacraftFolder() {
		try {
			return new File((String) CustomLaunch.class.getClassLoader().loadClass("org.betacraft.launcher.BC").getDeclaredMethod("get").invoke(null));	
		}catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private static void log(String string) {
		System.out.println("[CustomLaunch] " + string);
	}
}
