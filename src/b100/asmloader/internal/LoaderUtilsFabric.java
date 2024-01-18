package b100.asmloader.internal;

import java.io.File;
import java.util.zip.ZipFile;

public class LoaderUtilsFabric extends LoaderUtils {

	public LoaderUtilsFabric(Log logObj) {
		super(logObj);
	}
	
	@Override
	public boolean peepModJson(File file) {
		if(file.isDirectory()) {
			File asmloaderModJson = new File(file, "asmloader.mod.json");
			File fabricModJson = new File(file, "fabric.mod.json");
			if(asmloaderModJson.exists() && !fabricModJson.exists()) {
				return true;
			}
		}else if(file.isFile()) {
			ZipFile zipFile = null;
			try {
				zipFile = new ZipFile(file);
				
				return zipFile.getEntry("asmloader.mod.json") != null && zipFile.getEntry("fabric.mod.json") == null;
			}catch (Exception e) {
				return false;
			}finally {
				try {
					zipFile.close();
				}catch (Exception e) {}
			}
		}
		return false;
	}

}
