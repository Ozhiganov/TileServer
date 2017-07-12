package de.metager.tileserver;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.map.awt.graphics.AwtGraphicFactory;
import org.mapsforge.map.layer.cache.FileSystemTileCache;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * @author SumaEV
 * A simple HttpHandler. It initializes the Tile Cache and handles incoming HTTP-Requests
 * If the requests are valid Tile requests it starts the rendering Job.
 */
public class TileHandler implements HttpHandler {

private int x, y, z;
private GraphicFactory GRAPHIC_FACTORY;
private FileSystemTileCache tileCache;
private File fileDir;
	
	public TileHandler(File fileDir) {
		this.fileDir = fileDir;
		File cacheDir = new File(fileDir, "tile_cache");
		cacheDir.mkdirs();
		if(fileDir.canWrite()) {
			this.GRAPHIC_FACTORY = AwtGraphicFactory.INSTANCE;
			this.tileCache = new FileSystemTileCache(20000, cacheDir, this.GRAPHIC_FACTORY, true);
		}else {
			System.err.println("Cannot write to Cache Dir " + cacheDir.getAbsolutePath());
			System.exit(-1);
		}
	}

	/* (non-Javadoc)
	 * @see com.sun.net.httpserver.HttpHandler#handle(com.sun.net.httpserver.HttpExchange)
	 */
	@Override
	public void handle(HttpExchange t) throws IOException {
		
		// If this is not a valid Tile Request ("/z/x/y.png") we'll abort here returning a 404 Error Code
		if(!this.extractRequestedTile(t.getRequestURI())) {
			// The requested URL does not contain the required Information to create a Tile
			// We will return a 404
			String response = "Sorry, we couldn't find the requested Tile";
			t.sendResponseHeaders(404, response.getBytes().length);
			OutputStream os = t.getResponseBody();
			os.write(response.getBytes());
			os.flush();
			os.close();
			return;
		}
		
		// Get the OutputStream that is send back to the user
		OutputStream os = t.getResponseBody();
		
		// Let's create our Tile
		// Initializes all important informations for generating the Tile
		Tile tile = new Tile(this.x, this.y, this.z, this.tileCache, this.GRAPHIC_FACTORY, fileDir);
		
		// If the requested Tile can be rendered with the existing Map Files we'll start the rendering process
		if(tile.isSupportsTile()) {
			// Set the headers
			Headers headers = t.getResponseHeaders();
			headers.add("Access-Control-Allow-Origin", "*");
			headers.add("Content-Type", "image/png");
			// Send the Headers back to the user
			t.sendResponseHeaders(200, 0);
			// Start the Tile Rendering or serve the Tile from the Tile-Cache
			// Data is written to the Outputstream Parameter
			tile.generateTile(os);

		}else {
			// The existing Map Files cannot generate the requested Tile as
			// it doesn't contain the required Data. We will return a 404
			String response = "Sorry, we couldn't find the requested Tile";
			t.sendResponseHeaders(404, response.getBytes().length);
			os.write(response.getBytes());
			os.flush();
			os.close();
			return;
		}
	}

	public GraphicFactory getGRAPHIC_FACTORY() {
		return GRAPHIC_FACTORY;
	}

	public FileSystemTileCache getTileCache() {
		return tileCache;
	}

	/**
	 * Parses the Uri for requested Tile Coordinates and saves them into the global variables
	 * @param uri The Requested Uri that holds the information about x,y,z coordinates
	 * @return true if successful and false if not
	 * 
	 */
	private boolean extractRequestedTile(URI uri) {
		String path = uri.getPath();
		// Check if the URL matches the needed Pattern to contain all the information
		Pattern p = Pattern.compile("^\\/(\\d+)\\/(\\d+)\\/(\\d+)\\.png$");
		Matcher m = p.matcher(path);
		if(m.find()) {
			try {
				this.z = Integer.parseInt(m.group(1));
				this.x = Integer.parseInt(m.group(2));
				this.y = Integer.parseInt(m.group(3));
			return true;
			}catch(NumberFormatException e) {
				return false;
			}
		}else {
			return false;
		}
	}

}
