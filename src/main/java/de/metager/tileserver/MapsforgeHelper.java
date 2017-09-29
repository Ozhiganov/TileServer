package de.metager.tileserver;

import java.io.File;
import java.io.FileFilter;

import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.datastore.MultiMapDataStore.DataPolicy;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.reader.header.MapFileException;

public class MapsforgeHelper {

	public static MultiMapDataStore getMultiMapDataStore(File mapFileDir) {
		File[] mapFiles = mapFileDir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.isFile() && pathname.getName().toLowerCase().endsWith(".map");
			}
		});
		if(mapFiles.length <= 0) {
			return null;
		}else {
			MultiMapDataStore mf =new MultiMapDataStore(DataPolicy.RETURN_ALL);
			for(File mapFile : mapFiles) {
				try {
					mf.addMapDataStore(new MapFile(mapFile), false, false);
				}catch(MapFileException e) {
					System.err.println("Couldn't load " + mapFile.getAbsolutePath());
				}
			}
			return mf;
		}
	}

}
