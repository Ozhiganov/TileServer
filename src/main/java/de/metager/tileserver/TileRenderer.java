package de.metager.tileserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.OverlappingFileLockException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.map.awt.graphics.AwtGraphicFactory;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.layer.cache.FileSystemTileCache;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.rendertheme.ExternalRenderTheme;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;

/**
 * @author SumaEV A simple HttpHandler. It initializes the Tile Cache and
 *         handles incoming HTTP-Requests If the requests are valid Tile
 *         requests it starts the rendering Job.
 */
public class TileRenderer implements Runnable {

	private GraphicFactory GRAPHIC_FACTORY;
	private FileSystemTileCache tileCache;
	private MultiMapDataStore mf;
	private DatabaseRenderer databaseRenderer;
	private DisplayModel displayModel;
	private ExternalRenderTheme xmlRenderTheme;
	private RenderThemeFuture renderThemeFuture;
	private File cacheDir;
	private ArrayBlockingQueue<File> bq;

	public TileRenderer(File tileCacheDir, File mapFileDir, File renderThemeFile, ArrayBlockingQueue<File> bq) {
		this.GRAPHIC_FACTORY = AwtGraphicFactory.INSTANCE;
		this.tileCache = new FileSystemTileCache(0, tileCacheDir, this.GRAPHIC_FACTORY, true);
		this.bq = bq;
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

		this.cacheDir = new File(tileCacheDir, "tiles");
		if (!cacheDir.canWrite()) {
			System.err.println("I don't have write permissions to the Cache Directory." + cacheDir.getAbsolutePath()
					+ " Exiting!");
			System.exit(-1);
		}
	}

	@Override
	public void run() {
		while(true) {
			try {
				File newFile = this.bq.poll(10L, TimeUnit.SECONDS);
				if(newFile == null) continue;
				this.handleRequest(newFile);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}
	
	private void handleRequest(File file) {
		if(file.canWrite()) {
			// We can write to that File so let's lock it for other ones and then create the requested File
			FileOutputStream out = null;
			try {
				out = new FileOutputStream(file);
				out.getChannel().lock();
			}catch(Exception e) {
				try {
					out.close();
				} catch (IOException e1) {}
				return;
			}
			
			// Let's check if the File Name is a valid Request
			Pattern pattern = Pattern.compile("^(\\d+)-(\\d+)-(\\d+)\\.request");
			Matcher matcher = pattern.matcher(file.getName());
			if(matcher.find()){
				int z = Integer.parseInt(matcher.group(1));
				int x = Integer.parseInt(matcher.group(2));
				int y = Integer.parseInt(matcher.group(3));
				
				Tile tile = new Tile(x, y, z, this.mf, this.renderThemeFuture, this.displayModel, this.databaseRenderer, this.tileCache, this.GRAPHIC_FACTORY);
				tile.generateTile(this.cacheDir);
			}
			try {
				out.close();
			} catch (IOException e) {}
			file.delete();
		}
	}

}
