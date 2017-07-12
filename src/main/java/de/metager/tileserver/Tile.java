package de.metager.tileserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.map.awt.graphics.AwtTileBitmap;
import org.mapsforge.map.layer.cache.FileSystemTileCache;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.layer.renderer.RendererJob;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.reader.ReadBuffer;
import org.mapsforge.map.rendertheme.ExternalRenderTheme;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;

/**
 * @author SumaEV
 * This class represents a Tile within our server
 * In the constructor we provide the needed Information about the Tile that needs to get rendered.
 * The method generateTile() starts the rendering process to a given Outputstream or serves the Tile
 * from the Cache if possible.
 */
public class Tile {
	private MapFile mf;
	private DatabaseRenderer databaserenderer;
	private DisplayModel displayModel;
	private ExternalRenderTheme xmlRenderTheme;
	private RenderThemeFuture renderThemeFuture;
	private boolean supportsTile;
	private org.mapsforge.core.model.Tile tile;
	private FileSystemTileCache tileCache;
	private GraphicFactory GRAPHIC_FACTORY;

	public Tile(int x, int y, int z, FileSystemTileCache tileCache, GraphicFactory gRAPHIC_FACTORY, File fileDir) {
		ReadBuffer.setMaximumBufferSize(6500000);
		File mapFile = new File(fileDir, "germany.map");
		if (!mapFile.exists()) {
			System.err.println("Konnte die angegebene MapFile nicht finden");
			System.exit(-1);
		}
		this.tileCache = tileCache;
		this.GRAPHIC_FACTORY = gRAPHIC_FACTORY;
		this.mf = new MapFile(mapFile);
		// Create the Tile
		this.tile = new org.mapsforge.core.model.Tile(x, y, (byte) z, 256);

		if (!this.mf.supportsTile(tile)) {
			this.supportsTile = false;
			return;
		} else {
			this.supportsTile = true;
		}

		this.databaserenderer = new DatabaseRenderer(this.mf, this.GRAPHIC_FACTORY, this.tileCache, null, true, false,
				null);
		this.displayModel = new DisplayModel();
		this.displayModel.setFixedTileSize(256);
		// this.displayModel.setUserScaleFactor(0.5f);
		File file = new File(fileDir, "metager.xml");
		try {
			this.xmlRenderTheme = new ExternalRenderTheme(file);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(-1);
		}
		this.renderThemeFuture = new RenderThemeFuture(this.GRAPHIC_FACTORY, this.xmlRenderTheme, this.displayModel);
		new Thread(this.renderThemeFuture).start();
	}

	public boolean isSupportsTile() {
		return supportsTile;
	}

	public void generateTile(OutputStream os) {
		RendererJob rendererJob = new RendererJob(this.tile, this.mf, this.renderThemeFuture, this.displayModel,
				(float) 1, false, false);
		AwtTileBitmap tileImage = (AwtTileBitmap) this.tileCache.get(rendererJob);
		if (tileImage == null) {
			tileImage = (AwtTileBitmap) this.databaserenderer.executeJob(rendererJob);
			this.tileCache.put(rendererJob, tileImage);
		}
		try {
			tileImage.compress(os);
			os.flush();
			os.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void updateCache() {
		RendererJob rendererJob = new RendererJob(this.tile, this.mf, this.renderThemeFuture, this.displayModel,
				(float) 1, false, false);
		if(this.isSupportsTile() && !this.tileCache.containsKey(rendererJob)) {
			AwtTileBitmap tileImage = (AwtTileBitmap) this.databaserenderer.executeJob(rendererJob);
			this.tileCache.put(rendererJob, tileImage);
		}
	}

}
