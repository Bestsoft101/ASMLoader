package b100.asmloader.internal;

import java.lang.instrument.Instrumentation;

import b100.asmloader.core.ASMLoaderCore;
import b100.asmloader.core.LoaderCoreBetaCraft;
import b100.asmloader.core.LoaderCoreDefault;
import b100.asmloader.core.LoaderCoreFabric;

public class ASMLoader {
	
	public static void init(Instrumentation instrumentation) {
		if(instrumentation == null) {
			throw new NullPointerException("Instrumentation is null!");
		}
		
		log("ASMLoader-v2-Init");
		
		ASMLoaderCore asmLoaderCore = null;
		
		String coreName = System.getProperty("asmloader.core");
		
		if(coreName != null) {
			if(coreName.equals("fabric")) {
				asmLoaderCore = new LoaderCoreFabric();
			}else if(coreName.equals("betacraft")) {
				asmLoaderCore = new LoaderCoreBetaCraft();
			}else {
				// TODO Custom Cores?
				log("Invalid core: '" + coreName + "'!");
				System.exit(-1);
				return;
			}
		}else {
			if("true".equalsIgnoreCase(System.getProperty("asmloader.fabric"))) {
				log("Found legacy fabric JVM Argument: 'asmloader.fabric'");
				asmLoaderCore = new LoaderCoreFabric();
			}
		}
		
		if(asmLoaderCore == null) {
			asmLoaderCore = new LoaderCoreDefault();
		}
		
		log("Using Core: '" + asmLoaderCore.getClass().getName() + "'");
		
		asmLoaderCore.preMain(instrumentation);
	}
	
	public static void log(String string) {
		System.out.print("[ASMLoader] " + string + "\n");
	}

}
