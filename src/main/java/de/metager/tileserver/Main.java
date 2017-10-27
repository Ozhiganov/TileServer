package de.metager.tileserver;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.Rectangle;
import org.mapsforge.map.awt.graphics.AwtGraphicFactory;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.layer.cache.FileSystemTileCache;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.ReadBuffer;
import org.mapsforge.map.rendertheme.ExternalRenderTheme;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;

/**
 * @author SumaEV This is a Java Tileserver as simple as it could be. We use a
 *         com.sun.net.httpserver to open up a Socket on Port 8000 (currently
 *         hardcoded) The TileHandler class takes any request to that port,
 *         parses it for the requested Tile ("/z/x/y.png") and generates/caches
 *         a new PNG Tile with the help of the Mapsforge Libraries
 *         (https://github.com/mapsforge/mapsforge) Additionally a new Cache
 *         Manager Thread is created which lives as long as this Program does.
 *         It manages the expiration of the cached Tiles and handles the pre
 *         generation of the Tiles for zoom 0-12 as those take literally forever
 *         to render. (Too long for the user to wait for)
 * 
 *         IMPORTANT: This TIleserver doesn't provide any possibility of
 *         configuration yet. Make sure that following Files are present in the
 *         same Folder as this .jar File, or the TileServer won't startup: 1.
 *         germany.map File containing the Data that needs to get rendered.
 *         (https://github.com/mapsforge/mapsforge/blob/master/docs/Getting-Started-Map-Writer.md)
 *         2. metager.xml containing the Rendering style that will be used by
 *         Mapsforge.
 *         (https://github.com/mapsforge/mapsforge/blob/master/docs/Rendertheme.md)
 *         3. The assets Folder from Mapsforge containing required images that
 *         get rendered.
 *         (https://github.com/mapsforge/mapsforge/tree/master/mapsforge-themes/src/main/resources/assets)
 * 
 *         The Server works without configuration for our needs. If we find the
 *         time we'll provide the possibility to configure all of this
 *         dynamically.
 */
public class Main {

	public static void main(String[] args) {
		// We get supplied with a path to the tile_cache by the arguments
		// If it's not a valid Path we won't start up this program
		File tileCachePath = new File(args[0]);
		
		int numberOfThreads = Integer.parseInt(args[1]);

		// Second argument is a path to a directory in which there are the .map Files.
		// If there are no Map Files within this directory we won't bother to start
		// either
		File mapFilePath = new File(args[2]);
		if (!mapFilePath.canRead() || !mapFilePath.isDirectory()) {
			System.err.println("Cannot read from the supplied Map File Path or it's not a directory. Sorry!");
			System.exit(-1);
		}
		// Let's check for valid Map Files within the directory, to be sure
		File[] mapFiles = mapFilePath.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.isFile() && pathname.getName().toLowerCase().endsWith(".map");
			}
		});
		if (mapFiles.length <= 0) {
			System.err.println("There are no valid Map Files in the supplied Directory. Sorry!");
			System.exit(-1);
		}
		mapFiles = null;

		// Last argument will be the Rendertheme that is gonna be used
		File renderThemeFile = new File(args[3]);
		if (!renderThemeFile.canRead() || !renderThemeFile.exists() || !renderThemeFile.isFile()) {
			System.err.println("I can either not read from the supplied Rendertheme File, or it doesn't exist. Sorry!");
			System.exit(-1);
		}

		
		//ReadBuffer.setMaximumBufferSize(65000000);
		
		// Create the needed resources to render Tiles
		// Create the multimapDataStore
		MultiMapDataStore mf = MapsforgeHelper.getMultiMapDataStore(mapFilePath);
		GraphicFactory GRAPHIC_FACTORY = AwtGraphicFactory.INSTANCE;
		ExternalRenderTheme xmlRenderTheme = null;
		try {
			xmlRenderTheme = new ExternalRenderTheme(renderThemeFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		DisplayModel displayModel = new DisplayModel();
		displayModel.setFixedTileSize(256);
		RenderThemeFuture renderThemeFuture = new RenderThemeFuture(GRAPHIC_FACTORY, xmlRenderTheme, displayModel);
		new Thread(renderThemeFuture).start();

		FileSystemTileCache tileCache = new FileSystemTileCache(0, tileCachePath, GRAPHIC_FACTORY, true);
		DatabaseRenderer databaseRenderer = new DatabaseRenderer(mf, GRAPHIC_FACTORY, tileCache, null, true, false, null);
		
		
		// Distinct whether this is a Tileserver process or a prerendering process:
		if(args.length == 5 && args[4].equals("prerender")) {
			prerender(numberOfThreads, mapFilePath, tileCachePath, mf, renderThemeFuture, displayModel, databaseRenderer, tileCache, GRAPHIC_FACTORY);
		}else {
			// Create the socket which will accept a new Connection
			// Each connection will be a tilerequest
			try {
				ServerSocket serverSocket = new ServerSocket(63825);
				ThreadPoolExecutor executor = new ThreadPoolExecutor(numberOfThreads, numberOfThreads*2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>());
				while(true) {
					if(Thread.interrupted()) break;
					Socket clientSocket = serverSocket.accept();
					executor.execute(
							new TileRenderer(mf, renderThemeFuture, displayModel, databaseRenderer, tileCache, GRAPHIC_FACTORY, tileCachePath, clientSocket)
							);
				}
				serverSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private static void prerender(int processes, File mapFilePath, File outputdir, MultiMapDataStore mf, RenderThemeFuture renderThemeFuture, DisplayModel displayModel, DatabaseRenderer databaseRenderer, FileSystemTileCache tileCache, GraphicFactory gRAPHIC_FACTORY) {
		// Find out which Bounding Box should get prerendered
		BoundingBox bbox = MapsforgeHelper.generateBoundingBox(mapFilePath);

		System.out.println("[" + Instant.now() + "] Starting prerendering with " + processes + " processes");
		LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
		ThreadPoolExecutor executor = new ThreadPoolExecutor(processes, processes, 0L, TimeUnit.MILLISECONDS, queue);
		int startZoom = 0;
		int endZoom = 13;
		
		int currentX = 0;
		int currentY = 0;
		
		if(outputdir.exists()) {
			try {
				FileUtils.deleteDirectory(outputdir);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		
		
		
		for(int currentZoom = startZoom; currentZoom <= endZoom; currentZoom++) {
			for(int x = 0; x <= currentX; x++) {
				for(int y = 0; y <= currentY; y++) {
					// Check if this Tile should get rendered
					org.mapsforge.core.model.Tile tile = new org.mapsforge.core.model.Tile(x, y, (byte)currentZoom, 256);
					Rectangle rect = bbox.getPositionRelativeToTile(tile);
					if(rect.left > 256 || rect.top > 256 || rect.right < 0 || rect.bottom < 0) continue;
					File outputFile = new File(outputdir, currentZoom + File.separator + x + File.separator + y + ".png");
					TileWriter writer = new TileWriter(currentZoom + ";" + x + ";" + y,
							outputFile, mf, renderThemeFuture, displayModel, databaseRenderer, tileCache, gRAPHIC_FACTORY);
					executor.execute(writer);
				}
			}
			currentX = (currentX * 2) + 1;
			currentY = (currentY * 2) + 1;
		}
		
		executor.shutdown();
		int queuedProcesses = 0;
		try {
			while(!executor.awaitTermination(1, TimeUnit.SECONDS)) {
				if(queue.size() != queuedProcesses) {
					queuedProcesses = queue.size();
					System.out.println("[" + Instant.now() + "] " + Integer.toString(queuedProcesses) + " Tiles remaining.");
				}
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("[" + Instant.now() + "] Finished");
	}
}
