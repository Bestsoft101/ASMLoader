package b100.asmloader.internal;

import java.lang.instrument.Instrumentation;

public class ASMLoaderAgent {
	
	public static void premain(String args, Instrumentation instrumentation) {
		ASMLoader.init(instrumentation);
	}

}
