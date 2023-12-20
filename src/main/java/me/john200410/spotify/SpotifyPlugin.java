package me.john200410.spotify;

import com.sun.net.httpserver.HttpServer;
import me.john200410.spotify.http.SpotifyAPI;
import me.john200410.spotify.http.Status;
import me.john200410.spotify.ui.SpotifyHudElement;
import org.apache.commons.io.IOUtils;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Objects;

/**
 * @author John200410
 */
public class SpotifyPlugin extends Plugin {
	
	public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	
	private HttpServer httpServer;
	private SpotifyAPI api;
	
	@Override
	public void onLoad() {
		try {
			this.httpServer = this.setupServer();
			this.httpServer.start();
			
			//hud element
			RusherHackAPI.getHudManager().registerFeature(new SpotifyHudElement(this));
		} catch(IOException e) {
			//throw exception so plugin doesnt load
			throw new RuntimeException(e);
		}
		
		//TODO: maybe window in the future?
	}
	
	@Override
	public void onUnload() {
		if(this.httpServer != null) {
			this.httpServer.stop(0);
		}
	}
	
	@Override
	public String getName() {
		return "Spotify";
	}
	
	@Override
	public String getVersion() {
		return "1.0.0";
	}
	
	@Override
	public String getDescription() {
		return "Spotify integration for rusherhack";
	}
	
	@Override
	public String[] getAuthors() {
		return new String[]{"John200410", "DarkerInk"};
	}
	
	public SpotifyAPI getAPI() {
		return this.api;
	}
	
	private HttpServer setupServer() throws IOException {
		final HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 4000), 0);
		server.createContext("/", (req) -> {
			final URI uri = req.getRequestURI();
			
			byte[] response = new byte[0];
			
			if(uri.getPath().equals("/callback")) {
				final String code = uri.getQuery().split("=")[1];
				this.api = new SpotifyAPI(code);
				
				try(InputStream is = SpotifyPlugin.class.getResourceAsStream("/site/success.html")) {
					Objects.requireNonNull(is, "Couldn't find login.html");
					response = IOUtils.toByteArray(is);
				}
			} else if(uri.getPath().equals("/")) {
				
				try(InputStream is = SpotifyPlugin.class.getResourceAsStream("/site/login.html")) {
					Objects.requireNonNull(is, "Couldn't find login.html");
					response = IOUtils.toByteArray(is);
				}
			}
			
			req.getResponseHeaders().add("Content-Type", "text/html");
			req.sendResponseHeaders(200, response.length);
			req.getResponseBody().write(response);
			req.getResponseBody().close();
		});
		
		return server;
	}
	
}
