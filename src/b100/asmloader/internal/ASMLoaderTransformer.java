package b100.asmloader.internal;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.tree.ClassNode;

import b100.asmloader.ClassTransformer;

public class ASMLoaderTransformer implements ClassFileTransformer {
	
	private Log logObj;
	private List<ClassTransformer> classTransformers;
	private Map<ClassTransformer, ModInfo> classTransformerToModMap;
	
	public ASMLoaderTransformer(List<ClassTransformer> classTransformers, Map<ClassTransformer, ModInfo> classTransformerToModMap, Log logObj) {
		this.classTransformers = classTransformers;
		this.classTransformerToModMap = classTransformerToModMap;
		this.logObj = logObj;
	}
	
	public byte[] transform(String className, byte[] classfileBuffer) {
		ClassNode classNode = null;
		
		for(int i=0; i < classTransformers.size(); i++) {
			ClassTransformer classTransformer = classTransformers.get(i);
			
			String modId = null;
			if(classTransformerToModMap != null) {
				ModInfo modInfo = classTransformerToModMap.get(classTransformer);
				if(modInfo != null) {
					modId = modInfo.modid;
				}
			}
			
			if(classTransformer.accepts(className)) {
				if(modId != null) {
					log("Transforming class '" + className + "' using transformer '" + classTransformer.getClass().getName() + "' provided by mod '" + modId + "'!");
				}else {
					log("Transforming class '" + className + "' using transformer '" + classTransformer.getClass().getName() + "'!");
				}
				
				if(classNode == null) {
					classNode = ASMHelper.getClassNode(classfileBuffer);
				}
				
				classTransformer.transform(className, classNode);
			}
		}
		
		if(classNode != null) {
			return ASMHelper.getBytes(classNode);
		}
		
		return classfileBuffer;
	}
	
	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		if(className == null) {
			return classfileBuffer;
		}

		try {
			return transform(className, classfileBuffer);
		}catch (Exception e) {
			System.err.println("Error transforming class '" + className + "'!");
			e.printStackTrace();
			System.exit(1);
			throw new RuntimeException();
		}
	}
	
	private void log(String string) {
		logObj.print(string);
	}

}
