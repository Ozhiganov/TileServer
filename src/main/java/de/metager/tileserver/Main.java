package de.metager.tileserver;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

/**
 * @author SumaEV
 * This is a Java Tileserver as simple as it could be.
 * We use a com.sun.net.httpserver to open up a Socket on Port 8000 (currently hardcoded)
 * The TileHandler class takes any request to that port, parses it for the requested Tile ("/z/x/y.png")
 * and generates/caches a new PNG Tile with the help of the Mapsforge Libraries (https://github.com/mapsforge/mapsforge)
 * Additionally a new Cache Manager Thread is created which lives as long as this Program does.
 * It manages the expiration of the cached Tiles and handles the pre generation of the Tiles for zoom 0-12 as those
 * take literally forever to render. (Too long for the user to wait for)
 * 
 * IMPORTANT: This TIleserver doesn't provide any possibility of configuration yet. Make sure that following Files are present
 * in the same Folder as this .jar File, or the TileServer won't startup:
 * 	1. germany.map File containing the Data that needs to get rendered. (https://github.com/mapsforge/mapsforge/blob/master/docs/Getting-Started-Map-Writer.md)
 * 	2. metager.xml containing the Rendering style that will be used by Mapsforge. (https://github.com/mapsforge/mapsforge/blob/master/docs/Rendertheme.md)
 * 	3. The assets Folder from Mapsforge containing required images that get rendered. (https://github.com/mapsforge/mapsforge/tree/master/mapsforge-themes/src/main/resources/assets)
 * 
 * The Server works without configuration for our needs. If we find the time we'll provide the possibility to configure all of this dynamically.
 */
public class Main {
		
	public static void main(String[] args) {
		try {
			File fileDir = null;
			try {
				String absolutePath = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
				absolutePath = absolutePath.substring(0, absolutePath.lastIndexOf("/"));
			    absolutePath = absolutePath.replaceAll("%20"," "); // Surely need to do this here
				fileDir = new File(absolutePath);
			} catch (URISyntaxException e) {
				e.printStackTrace();
				System.exit(-1);
			}
			
			HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
			TileHandler tilehandler = new TileHandler(fileDir);
			server.createContext("/", tilehandler);
			server.setExecutor(new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>()));
			server.start();
			
			// Create The CacheManager
			CacheManager cacheManager = new CacheManager(fileDir, tilehandler.getTileCache(), tilehandler.getGRAPHIC_FACTORY());
			new Thread(cacheManager).start();
			
		} catch (IOException e) {
			System.err.println("Couldn't Create Instance of a new HTTP Server");
			e.printStackTrace();
			System.exit(-1);
		}
	}
}
