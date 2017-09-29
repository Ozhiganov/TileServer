package de.metager.tileserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MetaGerTileCache {

	public static FileInputStream get(File cacheDir, int x, int y, int z) {
		File cacheFile = new File(cacheDir, z + "/" + x + "/" + y + ".png");
		try {
			return new FileInputStream(cacheFile);
		} catch (FileNotFoundException e) {
			return null;
		}
	}


	public static OutputStream put(File cacheDir, int x, int y, int z) {
		File dir =  new File(cacheDir, z + "/" + x + "/");
		dir.mkdirs();
		File cacheFile = new File(dir,  y + ".png");
		try {
			return new FileOutputStream(cacheFile);
		} catch (FileNotFoundException e) {
			return null;
		}
	}
}
