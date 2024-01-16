package b100.asmloader.internal;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import b100.utils.interfaces.Condition;

public class ASMHelper {
	
	public static ClassNode getClassNode(byte[] bytes) {
		ClassNode classNode = new ClassNode();
		ClassReader classReader = new ClassReader(bytes);
		classReader.accept(classNode, 0);
		return classNode;
	}
	
	public static byte[] getBytes(ClassNode classNode) {
		ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		classNode.accept(classWriter);
		return classWriter.toByteArray();
	}
	
	public static AbstractInsnNode findInstruction(AbstractInsnNode startInstruction, boolean backwards, Condition<AbstractInsnNode> condition) {
		AbstractInsnNode instruction = startInstruction;
		while(true) {
			if(instruction == null) {
				return null;
			}
			if(condition.isTrue(instruction)) {
				return instruction;
			}
			instruction = backwards ? instruction.getPrevious() : instruction.getNext();
		}
	}
	
	public static MethodNode findMethod(ClassNode classNode, String name, String desc) {
		List<MethodNode> foundMethods = new ArrayList<MethodNode>();
		
		for(MethodNode methodNode : classNode.methods) {
			if((name == null || methodNode.name.equals(name)) && (desc == null || methodNode.desc.equals(desc))) {
				foundMethods.add(methodNode);
			}
		}
		
		if(foundMethods.size() != 1) {
			StringBuilder msg = new StringBuilder();
			msg.append("\n\n");
			
			if(foundMethods.size() < 1) {
				msg.append("Found no methods with name '"+name+"' and descriptor '"+desc+"' in class '"+classNode.name+"'!\n");
			}else {
				msg.append("Found more than one method matching name '"+name+"' and descriptor '"+desc+"' in class '"+classNode.name+"'!\n");
			}
			
			msg.append('\n');
			msg.append("All methods in class '"+classNode.name+"': \n");
			for(MethodNode method : classNode.methods) {
				msg.append("    ").append(method.name).append(method.desc).append('\n');
			}
			
			throw new RuntimeException(msg.toString());
		}
		
		return foundMethods.get(0);
	}
	
	public static boolean methodInsn(AbstractInsnNode node, String owner, String name, String desc) {
		if(node instanceof MethodInsnNode) {
			MethodInsnNode node1 = (MethodInsnNode) node;
			return node1.owner.equals(owner) && node1.name.equals(name) && node1.desc.equals(desc);
		}
		return false;
	}

}
