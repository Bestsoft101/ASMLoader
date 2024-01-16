package b100.asmloader;

import org.objectweb.asm.tree.ClassNode;

public abstract class ClassTransformer {
	
	private static int hashCount = 0;
	
	private final int hash = hashCount++;
	
	public abstract boolean accepts(String className);
	
	public abstract void transform(String className, ClassNode classNode);
	
	public final int hashCode() {
		return hash;
	}

}
