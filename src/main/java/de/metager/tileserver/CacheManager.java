package de.metager.tileserver;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;

import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.map.awt.graphics.AwtGraphicFactory;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.layer.cache.FileSystemTileCache;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.rendertheme.ExternalRenderTheme;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;

public class CacheManager implements Runnable {

	private FileSystemTileCache tileCache;
	private GraphicFactory GRAPHIC_FACTORY;
	private File cacheDir;
	private final ArrayList<HashMap<String, Integer>> preRenderAreas = new ArrayList<>();
	private DatabaseRenderer databaseRenderer;
	private MultiMapDataStore mf;
	private RenderThemeFuture renderThemeFuture;
	private DisplayModel displayModel;
	private ExternalRenderTheme xmlRenderTheme;
	private File mapFileDir;

	public CacheManager(File tileCacheDir, File mapFileDir, File renderThemeFile) {
		this.GRAPHIC_FACTORY = AwtGraphicFactory.INSTANCE;
		this.tileCache = new FileSystemTileCache(0, tileCacheDir, this.GRAPHIC_FACTORY, true);
		
		// Create the multimapDataStore
		this.mf = MapsforgeHelper.getMultiMapDataStore(mapFileDir);
	
		this.databaseRenderer = new DatabaseRenderer(this.mf, this.GRAPHIC_FACTORY, this.tileCache, null, true, false, null);
		
		this.displayModel = new DisplayModel();
		this.displayModel.setFixedTileSize(256);
		try {
			this.xmlRenderTheme = new ExternalRenderTheme(renderThemeFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		this.renderThemeFuture = new RenderThemeFuture(this.GRAPHIC_FACTORY, this.xmlRenderTheme, this.displayModel);
		new Thread(this.renderThemeFuture).start();

		// Fill the Array List of Areas that need to get prerenderd
		// Syntax for an Area is:
		// {
		// minX - minimal xTile (Zoom 7)
		// minY - minimal yTile (Zoom 7)
		// maxX - maximal xTile (Zoom 7)
		// maxY - maximal yTile (Zoom 7)
		// maxZoom - Zoom Level upto which we should prerender this area
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
	
		// Poland: 
		HashMap<String, Integer> poland = new HashMap<>();
		poland.put("minX", 70);
		poland.put("minY", 40);
		poland.put("maxX", 72);
		poland.put("maxY", 43);
		poland.put("maxZoom", 12);
		// Add poland to the Prerender List
		this.preRenderAreas.add(poland);

		this.cacheDir = new File(tileCacheDir, "tiles");
		if (!cacheDir.canWrite()) {
			System.err.println("I don't have write permissions to the Cache Directory." + cacheDir.getAbsolutePath()
					+ " Exiting!");
			System.exit(-1);
		}
		this.mapFileDir = mapFileDir;
	}

	@Override
	public void run() {
		while(true) {
			// We won't rerender everything if there is no new Data.
			// We will check if the tile 0/0/0.png is older then the MapFile
			File testTile = new File(this.cacheDir, "0/0/0.png");
			if(testTile.exists()) {
				// From all available MapFiles we will get the date of the newest one to compare with
				File[] mapFiles = mapFileDir.listFiles(new FileFilter() {
					@Override
					public boolean accept(File pathname) {
						return pathname.isFile() && pathname.getName().toLowerCase().endsWith(".map");
					}
				});
				long latestMapFileDate = 0;
				for(File file : mapFiles) {
					if(file.lastModified() > latestMapFileDate) {
						latestMapFileDate = file.lastModified();
					}
				}
				if(latestMapFileDate < testTile.lastModified()) {
					// We have no newer Data for our Tiles so it's pointless to rerender everything
					return;
				}
			}
			
			/*
			 * We need to take two steps 1. Invalidate/Delete every Cache Entry that has
			 * a higher zoom level then the prerendering (12)
			 */
			this.invalidateCache();
			// 2. Create the new Tiles
			// We will prerender every Tile between Zoom 0 and Zoom 6
			this.updateCache(0, 0, 0, 0, 0, 6);
	
			// Prerender all declared Areas
			for (HashMap<String, Integer> area : this.preRenderAreas) {
				this.updateCache(area.get("minX"), area.get("minY"), area.get("maxX"), area.get("maxY"), 7,
						area.get("maxZoom"));
			}
			try {
				Thread.sleep(1000 * 60 * 60 * 2);
			} catch (InterruptedException e) {
				break;
			}
		}
	}

	private void updateCache(int minX, int minY, int maxX, int maxY, int minZoom, int maxZoom) {
		ArrayList<Thread> threads = new ArrayList<>();
		for (int z = minZoom; z <= maxZoom; z++) {
			Thread t = new Thread(new Updater(minX, minY, maxX, maxY, z, this.tileCache, this.GRAPHIC_FACTORY, this.mf,
					this.renderThemeFuture, this.databaseRenderer, this.displayModel, this.cacheDir));
			threads.add(t);
			t.start();

			minX = minX * 2;
			minY = minY * 2;
			maxX = (maxX * 2) + 1;
			maxY = (maxY * 2) + 1;
		}

		// We will make this thread wait until all Updaters are finished
		while (true) {
			boolean running = false;
			for (Thread t : threads) {
				if (t.isAlive()) {
					running = true;
					break;
				}
			}
			if (running) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				continue;
			} else {
				break;
			}
		}
	}

	private void invalidateCache() {
		File[] zoomFolders = this.cacheDir.listFiles();
		for (File file : zoomFolders) {
			int zoom = Integer.parseInt(file.getName());
			if(zoom < 0 || zoom > 22) continue;
			if(zoom > 12) file.delete();
		}
	}

	private class Updater implements Runnable {
		private int maxX;
		private int maxY;
		private FileSystemTileCache tileCache;
		private GraphicFactory GRAPHIC_FACTORY;
		private int z;
		private int minX;
		private int minY;
		private File cacheDir;
		private MultiMapDataStore mf;
		private DatabaseRenderer databaseRenderer;
		private RenderThemeFuture renderThemeFuture;
		private DisplayModel displayModel;

		public Updater(int minX, int minY, int maxX, int maxY, int z, FileSystemTileCache tileCache,
				GraphicFactory gRAPHIC_FACTORY, MultiMapDataStore mf, RenderThemeFuture renderThemeFuture,
				DatabaseRenderer databaseRenderer, DisplayModel displayModel, File cacheDir) {
			this.minX = minX;
			this.minY = minY;
			this.maxX = maxX;
			this.maxY = maxY;
			this.z = z;
			this.tileCache = tileCache;
			this.GRAPHIC_FACTORY = gRAPHIC_FACTORY;
			this.cacheDir = cacheDir;
			this.mf = mf;
			this.databaseRenderer = databaseRenderer;
			this.renderThemeFuture = renderThemeFuture;
			this.displayModel = displayModel;
		}

		@Override
		public void run() {
			System.out.println("Start of rendering zoom " + z);

			for (int x = minX; x <= maxX; x++) {
				for (int y = minY; y <= maxY; y++) {
					Tile tile = new Tile(x, y, z, this.mf, this.renderThemeFuture, this.displayModel,
							this.databaseRenderer, this.tileCache, this.GRAPHIC_FACTORY);
					tile.updateCache(this.cacheDir);
				}
			}
			System.out.println("Finished with rendering zoom " + z);
		}
	}

}
