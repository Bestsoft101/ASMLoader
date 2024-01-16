package b100.asmloader.core;

import java.lang.instrument.Instrumentation;

import b100.asmloader.internal.ASMLoaderTransformer;

public class LoaderCoreBetaCraft extends LoaderCoreDefault {
	
	private static LoaderCoreBetaCraft instance;
	
	public static LoaderCoreBetaCraft getInstance() {
		return instance;
	}
	
	private Instrumentation instrumentation;
	
	public LoaderCoreBetaCraft() {
		if(instance != null) {
			throw new IllegalStateException("Instance already exists!");
		}
		
		instance = this;
	}
	
	@Override
	public void preMain(Instrumentation instrumentation) {
		findModFilesAndLoadModInfos();
		
		this.instrumentation = instrumentation;
	}
	
	public void finish(ClassLoader classLoader) {
		loadClassTransformers(classLoader);
		
		instrumentation.addTransformer(new ASMLoaderTransformer(classTransformers, classTransformerToModMap, this));
	}

	@Override
	public void print(String string) {
		System.out.println("[ASMLoader-BetaCraft] " + string);
	}

}
