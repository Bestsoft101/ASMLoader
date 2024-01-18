package b100.asmloader.core;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import b100.asmloader.internal.ASMHelper;
import b100.asmloader.internal.ASMLoaderTransformer;
import b100.asmloader.internal.LoaderUtils;
import b100.asmloader.internal.LoaderUtilsFabric;
import b100.asmloader.internal.Log;
import b100.utils.ReflectUtils;

public class LoaderCoreFabric extends LoaderCoreDefault implements Log {

	private static LoaderCoreFabric instance;

	private FabricTransformer fabricTransformer;
	private Instrumentation instrumentation;
	private ASMLoaderTransformer asmloaderTransformer;
	private ClassLoader fabricClassLoader;
	
	public LoaderCoreFabric() {
		if(instance != null) {
			throw new RuntimeException("Instance already exists!");
		}
		instance = this;
		
		this.loaderUtils = new LoaderUtilsFabric(this);
	}
	
	@Override
	public void preMain(Instrumentation instrumentation) {
		this.instrumentation = instrumentation;
		this.fabricTransformer = new FabricTransformer();
		
		instrumentation.addTransformer(fabricTransformer);
	}
	
	public void init(ClassLoader classLoader) {
		this.fabricClassLoader = classLoader;
		
		print("Init!");
		print("Fabric Class Loader: " + fabricClassLoader);
		
		findModFilesAndLoadModInfos();

		addModFilesToSystemClassLoader(instrumentation);
		
		loadClassTransformers(getClass().getClassLoader());
		
		this.asmloaderTransformer = new ASMLoaderTransformer(classTransformers, classTransformerToModMap, this);
		instrumentation.addTransformer(asmloaderTransformer);
		
		instrumentation.removeTransformer(fabricTransformer);
	}
	
	/**
	 * Add all ASMLoader mod files to the list of files the Fabric Class Loader is allowed to load 
	 */
	@SuppressWarnings("unchecked")
	public void addModFilesToFabricClassLoader(ClassLoader classLoader) {
		Class<?> classLoaderClass = classLoader.getClass();
		
		if(classLoaderClass.getName().equals("net.fabricmc.loader.impl.launch.knot.KnotClassLoader")) {
			try {
				Object urlLoader = ReflectUtils.getValue(ReflectUtils.getField(classLoaderClass, "urlLoader"), classLoader);
				Object delegate = ReflectUtils.getValue(ReflectUtils.getField(classLoaderClass, "delegate"), classLoader);
				
				Method addUrlMethod = urlLoader.getClass().getDeclaredMethod("addURL", URL.class);
				addUrlMethod.setAccessible(true);
				
				// Add mod files to fabrics class loader
				for(File file : modFilesInModsFolder) {
					addUrlMethod.invoke(urlLoader, file.toURI().toURL());
				}
				
				// Add ASMLoader jar to fabrics class loader, required for Java 17
				Set<Path> validParentCodeSources = (Set<Path>) ReflectUtils.getValue(ReflectUtils.getField(delegate.getClass(), "validParentCodeSources"), delegate);
				validParentCodeSources.add(LoaderUtils.getClassPath(LoaderCoreFabric.class));
			}catch (Exception e) {
				throw new RuntimeException(e);
			}
		}else {
			throw new RuntimeException("Unknown Fabric ClassLoader Class: " + classLoaderClass.getName());
		}
	}
	
	@Override
	public void print(String string) {
		System.out.print("[ASMLoader-Fabric] " + string + "\n");
	}
	
	// Listeners
	
	public static void onKnotInit(ClassLoader classLoader) {
		instance.init(classLoader);
	}
	
	public static void onSetClassPath() {
		instance.addModFilesToFabricClassLoader(instance.fabricClassLoader);
	}
	
	// Transformer
	
	private static class FabricTransformer implements ClassFileTransformer {

		private static final String LOADER_CORE = "b100/asmloader/core/LoaderCoreFabric";
		
		private boolean transformedKnot = false;
		
		public byte[] transform(String name, byte[] bytes) {
			if(!transformedKnot && "net/fabricmc/loader/impl/launch/knot/Knot".equals(name)) {
				transformedKnot = true;

				ClassNode classNode = ASMHelper.getClassNode(bytes);
				
				transformInit(classNode);
				transformSetValidParentClassPath(classNode);
				
				return ASMHelper.getBytes(classNode);
			}
			return bytes;
		}
		
		private void transformInit(ClassNode classNode) {
			MethodNode method = ASMHelper.findMethod(classNode, "init", "([Ljava/lang/String;)Ljava/lang/ClassLoader;");
			
			AbstractInsnNode first = method.instructions.getFirst();
			AbstractInsnNode node = ASMHelper.findInstruction(first, false, (n) -> ASMHelper.methodInsn(n, "net/fabricmc/loader/impl/launch/knot/KnotClassLoaderInterface", "getClassLoader", "()Ljava/lang/ClassLoader;"));
			
			if(node == null) {
				throw new NullPointerException("Could not find getClassLoader method call!");
			}
			
			InsnList insert = new InsnList();
			insert.add(new InsnNode(Opcodes.DUP));
			insert.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LOADER_CORE, "onKnotInit", "(Ljava/lang/ClassLoader;)V"));
			
			method.instructions.insert(node, insert);
		}
		
		private void transformSetValidParentClassPath(ClassNode classNode) {
			MethodNode setValidParentClassPath = ASMHelper.findMethod(classNode, "setValidParentClassPath", "(Ljava/util/Collection;)V");
			
			AbstractInsnNode returnNode = ASMHelper.findInstruction(setValidParentClassPath.instructions.getLast(), true, (n) -> n.getOpcode() == Opcodes.RETURN);
			
			setValidParentClassPath.instructions.insertBefore(returnNode, new MethodInsnNode(Opcodes.INVOKESTATIC, LOADER_CORE, "onSetClassPath", "()V"));
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
