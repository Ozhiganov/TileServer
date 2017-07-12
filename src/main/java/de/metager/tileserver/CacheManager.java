package de.metager.tileserver;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.map.layer.cache.FileSystemTileCache;

public class CacheManager implements Runnable {

	private FileSystemTileCache tileCache;
	private GraphicFactory GRAPHIC_FACTORY;
	private final long MAX_AGE = 1209600000; // Two Weeks in ms
	private File cacheDir;
	private final ArrayList<HashMap<String, Integer>> preRenderAreas = new ArrayList<>();
	private File fileDir;
	
	public CacheManager(File fileDir, FileSystemTileCache tileCache, GraphicFactory graphic_FACTORY) {
		this.tileCache = tileCache;
		this.GRAPHIC_FACTORY = graphic_FACTORY;
		
		// Fill the Array List of Areas that need to get prerenderd
		// Syntax for an Area is:
		// {
		//		minX	-	minimal xTile (Zoom 7)
		//		minY	-	minimal yTile (Zoom 7)
		//		maxX	-	maximal xTile (Zoom 7)
		//		maxY	-	maximal yTile (Zoom 7)
		//		maxZoom	-	Zoom Level upto which we should prerender this area
		// }
		// Germany:
		HashMap<String, Integer> germany = new HashMap<>();
		germany.put("minX", 65);
		germany.put("minY", 40);
		germany.put("maxX", 69);
		germany.put("maxY", 44);
		germany.put("maxZoom", 12);
		// Add Germany to the Prerender List
		this.preRenderAreas.add(germany);
		
		this.fileDir = fileDir;
		this.cacheDir = new File(fileDir, "tile_cache");
		if(!cacheDir.canWrite()) {
			System.err.println("I don't have write permissions to the Cache Directory." + cacheDir.getAbsolutePath() + " Exiting!");
			System.exit(-1);
		}
	}

	@Override
	public void run() {
		while(true) {
			/*
			 * We need to take two steps
			 * 1. Invalidate/Delete every Cache Entry that is older than MaxAge
			 */
			int deletedFiles = this.invalidateCache();
			System.out.println("Deleted " + deletedFiles + " old Cache Files");
			// 2. Create the new Tiles
			// We will prerender every Tile between Zoom 0 and Zoom 6
			this.updateCache(0, 0, 0, 0, 0, 6);
			
			// Prerender all declared Areas
			for(HashMap<String, Integer> area : this.preRenderAreas) {
				this.updateCache(area.get("minX"), area.get("minY"), area.get("maxX"), area.get("maxY"), 7, area.get("maxZoom"));
			}
			try {
				Thread.sleep(1000 * 60 * 60 * 2);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // Sleep for two hours and then recheck
		}
	}
	
	private void updateCache(int minX, int minY, int maxX, int maxY, int minZoom, int maxZoom) {
		ArrayList<Thread> threads = new ArrayList<>();
		for(int z = minZoom; z <= maxZoom; z++) {
			Thread t = new Thread(new Updater(minX, minY, maxX, maxY, z, this.tileCache, this.GRAPHIC_FACTORY, fileDir));
			threads.add(t);
			t.start();
			
			minX = minX * 2;
			minY = minY * 2;
			maxX = (maxX * 2) + 1;
			maxY = (maxY * 2) + 1;
		}
		
		// We will make this thread wait until all Updaters are finished
		while(true) {
			boolean running = false;
			for(Thread t : threads) {
				if(t.isAlive()) {
					running = true;
					break;
				}
			}
			if(running) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				continue;
			}else {
				break;
			}
		}
	}

	private int invalidateCache() {
		ArrayList<File> cachedFiles = this.getCachedFiles(this.cacheDir);
		long currentTime = System.currentTimeMillis();
		int count = 0;
		for(File file : cachedFiles) {
			long age = currentTime - file.lastModified();
			if(age > this.MAX_AGE) {
				file.delete();
				count++;
			}	
		}
		return count;
	}

	private ArrayList<File> getCachedFiles(File dir){
		ArrayList<File> result = new ArrayList<>();
		for(File file : dir.listFiles()) {
			if(file.isDirectory()) {
				result.addAll(getCachedFiles(file));
			}else {
				result.add(file);
			}
		}
		return result;
	}
	
	private class Updater implements Runnable{
		private int maxX;
		private int maxY;
		private FileSystemTileCache tileCache;
		private GraphicFactory GRAPHIC_FACTORY;
		private int z;
		private int minX;
		private int minY;
		private File fileDir;

		public Updater(int minX, int minY, int maxX, int maxY, int z, FileSystemTileCache tileCache, GraphicFactory gRAPHIC_FACTORY, File fileDir) {
			this.minX = minX;
			this.minY = minY;
			this.maxX = maxX;
			this.maxY = maxY;
			this.z = z;
			this.tileCache = tileCache;
			this.GRAPHIC_FACTORY = gRAPHIC_FACTORY;
			this.fileDir = fileDir;
		}
		
		@Override
		public void run() {
			System.out.println("Start of rendering zoom " + z);
			
			for(int x = minX; x <= maxX; x++) {
				for(int y = minY; y <= maxY; y++) {
					Tile tile = new Tile(x, y, z, this.tileCache, this.GRAPHIC_FACTORY, fileDir);
					tile.updateCache();
				}
			}
			System.out.println("Finished with rendering zoom " + z);
		}
	}

}
