package b100.asmloader.core;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.ProtectionDomain;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import b100.asmloader.internal.ASMHelper;
import b100.asmloader.internal.ASMLoaderTransformer;
import b100.asmloader.internal.Log;

public class LoaderCoreFabric extends LoaderCoreDefault implements Log {

	private static LoaderCoreFabric instance;

	private FabricTransformer fabricTransformer;
	private Instrumentation instrumentation;
	private ASMLoaderTransformer asmloaderTransformer;
	
	public LoaderCoreFabric() {
		if(instance != null) {
			throw new RuntimeException("Instance already exists!");
		}
		instance = this;
	}
	
	@Override
	public void preMain(Instrumentation instrumentation) {
		this.instrumentation = instrumentation;
		this.fabricTransformer = new FabricTransformer();
		
		instrumentation.addTransformer(fabricTransformer);
	}
	
	public void init(ClassLoader classLoader) {
		print("Init!");
		print("Fabric Class Loader: " + classLoader);
		
		findModFilesAndLoadModInfos();

		addModFilesToSystemClassLoader(instrumentation);
		addModFilesToFabricClassLoader(classLoader);
		
		loadClassTransformers(getClass().getClassLoader());
		
		this.asmloaderTransformer = new ASMLoaderTransformer(classTransformers, classTransformerToModMap, this);
		instrumentation.addTransformer(asmloaderTransformer);
		
		instrumentation.removeTransformer(fabricTransformer);
	}
	
	public void addModFilesToFabricClassLoader(ClassLoader classLoader) {
		if(classLoader.getClass().getName().equals("net.fabricmc.loader.impl.launch.knot.KnotClassLoader")) {
			try{
				Field urlLoaderField = classLoader.getClass().getDeclaredField("urlLoader");
				urlLoaderField.setAccessible(true);
				
				Object urlLoader = urlLoaderField.get(classLoader);
				
				Method addUrlMethod = urlLoader.getClass().getDeclaredMethod("addURL", URL.class);
				addUrlMethod.setAccessible(true);
				
				for(File file : modFilesInModsFolder) {
					addUrlMethod.invoke(urlLoader, file.toURI().toURL());
				}
				
			}catch (Exception e) {
				throw new RuntimeException(e);
			}
		}else {
			throw new RuntimeException("Unknown Fabric ClassLoader Class: " + classLoader.getClass().getName());
		}
	}
	
	@Override
	public void print(String string) {
		System.out.print("[ASMLoader-Fabric] " + string + "\n");
	}
	
	public static void onKnotInit(ClassLoader classLoader) {
		instance.init(classLoader);
	}
	
	private static class FabricTransformer implements ClassFileTransformer {

		private boolean transformedKnot = false;
		
		public byte[] transform(String name, byte[] bytes) {
			if(!transformedKnot && "net/fabricmc/loader/impl/launch/knot/Knot".equals(name)) {
				transformedKnot = true;
				
				ClassNode classNode = ASMHelper.getClassNode(bytes);
				MethodNode initMethod = ASMHelper.findMethod(classNode, "init", "([Ljava/lang/String;)Ljava/lang/ClassLoader;");
				
				AbstractInsnNode first = initMethod.instructions.getFirst();
				AbstractInsnNode node = ASMHelper.findInstruction(first, false, (n) -> ASMHelper.methodInsn(n, "net/fabricmc/loader/impl/launch/knot/KnotClassLoaderInterface", "getClassLoader", "()Ljava/lang/ClassLoader;"));
				
				if(node == null) {
					throw new NullPointerException("Could not find getClassLoader method call!");
				}
				
				InsnList insert = new InsnList();
				insert.add(new InsnNode(Opcodes.DUP));
				insert.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "b100/asmloader/core/LoaderCoreFabric", "onKnotInit", "(Ljava/lang/ClassLoader;)V"));
				
				initMethod.instructions.insert(node, insert);
				
				return ASMHelper.getBytes(classNode);
			}
			return bytes;
		}
				
		@Override
		public byte[] transform(ClassLoader loader, String name, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] bytes) throws IllegalClassFormatException {
			if(name != null && bytes != null) {
				try {
					return transform(name, bytes);
				}catch (Exception e) {
					e.printStackTrace();
					System.exit(-1);
					throw new RuntimeException();
				}
			}
			
			return bytes;
		}
		
	}

}
