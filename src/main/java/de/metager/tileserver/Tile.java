package de.metager.tileserver;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.map.awt.graphics.AwtTileBitmap;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.layer.cache.FileSystemTileCache;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.layer.renderer.RendererJob;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;

/**
 * @author SumaEV
 * This class represents a Tile within our server
 * In the constructor we provide the needed Information about the Tile that needs to get rendered.
 * The method generateTile() starts the rendering process to a given Outputstream or serves the Tile
 * from the Cache if possible.
 */
public class Tile {
	private MultiMapDataStore mf;
	private DatabaseRenderer databaserenderer;
	private DisplayModel displayModel;
	private RenderThemeFuture renderThemeFuture;
	private boolean supportsTile;
	private org.mapsforge.core.model.Tile tile;
	private FileSystemTileCache tileCache;
	private int x;
	private int y;
	private int z;
	public Tile(int x, int y, int z, MultiMapDataStore mf, RenderThemeFuture renderThemeFuture, DisplayModel displayModel, DatabaseRenderer databaserenderer, FileSystemTileCache tileCache, GraphicFactory gRAPHIC_FACTORY) {
		
		this.x = x;
		this.y = y;
		this.z = z;
		
		this.tileCache = tileCache;
		// Create the Tile
		this.tile = new org.mapsforge.core.model.Tile(x, y, (byte) z, 256);

		this.mf = mf;
		
		if (!this.mf.supportsTile(tile)) {
			this.supportsTile = false;
			return;
		} else {
			this.supportsTile = true;
		}
		
		this.renderThemeFuture = renderThemeFuture;
		this.displayModel = displayModel;
		this.databaserenderer = databaserenderer;
	}

	public boolean isSupportsTile() {
		return supportsTile;
	}

	public void generateTile(OutputStream os) {
			// The Tile cannot be loaded from the cache
			this.renderThemeFuture.incrementRefCount();
			RendererJob rendererJob = new RendererJob(this.tile, this.mf, this.renderThemeFuture, this.displayModel,
					(float) 1, false, false);
			AwtTileBitmap tileImage = (AwtTileBitmap) this.databaserenderer.executeJob(rendererJob);
			if(tileImage != null) {
				// Put this new Tile into the Cache
				try {
					tileImage.incrementRefCount();
					tileImage.compress(os);
					os.flush();
					tileImage.decrementRefCount();
				} catch (IOException e) {}
			}
			this.renderThemeFuture.decrementRefCount();
	}

	public void updateCache(File cacheDir) {
		RendererJob rendererJob = new RendererJob(this.tile, this.mf, this.renderThemeFuture, this.displayModel,
				(float) 1, false, false);
		if(this.isSupportsTile() && !this.tileCache.containsKey(rendererJob)) {
			AwtTileBitmap tileImage = (AwtTileBitmap) this.databaserenderer.executeJob(rendererJob);
			if(tileImage != null) {
				try {
					OutputStream cos = MetaGerTileCache.put(cacheDir, this.x, this.y, this.z);
					tileImage.incrementRefCount();
					tileImage.compress(cos);
					cos.flush();
					cos.close();
					tileImage.decrementRefCount();
				} catch (IOException e) {}
			}
		}
	}

}
