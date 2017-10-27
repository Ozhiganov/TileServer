package de.metager.tileserver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.layer.cache.FileSystemTileCache;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;

/**
 * @author SumaEV A simple HttpHandler. It initializes the Tile Cache and
 *         handles incoming HTTP-Requests If the requests are valid Tile
 *         requests it starts the rendering Job.
 */
public class TileWriter implements Runnable {

	private GraphicFactory GRAPHIC_FACTORY;
	private FileSystemTileCache tileCache;
	private MultiMapDataStore mf;
	private DatabaseRenderer databaseRenderer;
	private DisplayModel displayModel;
	private RenderThemeFuture renderThemeFuture;
	private String tile;
	private File outputPath;
	public TileWriter(String tile, File outputPath, MultiMapDataStore mf, RenderThemeFuture renderThemeFuture, DisplayModel displayModel,
			DatabaseRenderer databaseRenderer, FileSystemTileCache tileCache, GraphicFactory gRAPHIC_FACTORY) {
			this.mf = mf;
			this.renderThemeFuture = renderThemeFuture;
			this.displayModel = displayModel;
			this.databaseRenderer = databaseRenderer;
			this.tileCache = tileCache;
			this.GRAPHIC_FACTORY = gRAPHIC_FACTORY;
			this.tile = tile;
			this.outputPath = outputPath;
	}

	@Override
	public void run() {
		try {
			handleRequest();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private void handleRequest() throws IOException {
		outputPath.getParentFile().mkdirs();
		outputPath.createNewFile();
		try(OutputStream os = new FileOutputStream(outputPath);){
			Pattern pattern = Pattern.compile("(\\d+);(\\d+);(\\d+)");
			Matcher m = pattern.matcher(tile);
			if(m.find()) {
				int z = Integer.parseInt(m.group(1));
				int x = Integer.parseInt(m.group(2));
				int y = Integer.parseInt(m.group(3));
				
				Tile tile = new Tile(x, y, z, this.mf, this.renderThemeFuture, this.displayModel, this.databaseRenderer, this.tileCache, this.GRAPHIC_FACTORY);
				tile.generateTile(os);
			}
		}
	
	}

}
