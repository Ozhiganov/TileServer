package de.metager.tileserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.layer.cache.FileSystemTileCache;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;

import com.google.common.io.Files;

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
	private RenderThemeFuture renderThemeFuture;
	private Socket clientSocket;
	private File prerenderedTiles;
	public TileRenderer(MultiMapDataStore mf, RenderThemeFuture renderThemeFuture, DisplayModel displayModel,
			DatabaseRenderer databaseRenderer, FileSystemTileCache tileCache, GraphicFactory gRAPHIC_FACTORY, File prerenderedTiles, Socket clientSocket) {
			this.mf = mf;
			this.renderThemeFuture = renderThemeFuture;
			this.displayModel = displayModel;
			this.databaseRenderer = databaseRenderer;
			this.tileCache = tileCache;
			this.GRAPHIC_FACTORY = gRAPHIC_FACTORY;
			this.clientSocket = clientSocket;
			this.prerenderedTiles = prerenderedTiles;
	}

	@Override
	public void run() {
		try {
			handleRequest();
		} catch (IOException e) {
			try {
				clientSocket.close();
			} catch (IOException e1) {}
		}
	}
	private void handleRequest() throws IOException {
		// Read in the request
		InputStream is = clientSocket.getInputStream();
		OutputStream os = clientSocket.getOutputStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String request = br.readLine();
		Pattern pattern = Pattern.compile("(\\d+);(\\d+);(\\d+)");
		Matcher m = pattern.matcher(request);
		if(m.find()) {
			int z = Integer.parseInt(m.group(1));
			int x = Integer.parseInt(m.group(2));
			int y = Integer.parseInt(m.group(3));
			
			// Check if this tile is prerendered
			File tileFile = new File(prerenderedTiles, z + File.separator + x + File.separator + y + ".png");
			if(tileFile.exists())
				IOUtils.copy(new FileInputStream(tileFile), os);
			else {
				Tile tile = new Tile(x, y, z, this.mf, this.renderThemeFuture, this.displayModel, this.databaseRenderer, this.tileCache, this.GRAPHIC_FACTORY);
				tile.generateTile(os);
			}
			os.close();
			is.close();
			clientSocket.close();
		}
	
	}

}
