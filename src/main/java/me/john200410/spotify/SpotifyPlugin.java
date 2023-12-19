package me.john200410.spotify;

import com.mojang.realmsclient.client.Request;
import com.sun.net.httpserver.HttpServer;
import me.john200410.spotify.ui.SpotifyHudElement;
import org.apache.commons.io.IOUtils;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;
import org.rusherhack.client.api.utils.ChatUtils;
import org.rusherhack.core.logging.ILogger;
import org.rusherhack.core.notification.NotificationType;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Objects;

/**
 * @author John200410
 */
public class SpotifyPlugin extends Plugin {
	
	/**
	 * Constants
	 */
	public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	public static HttpServer HTTP_SERVER;
	
	static {
		try {
			HTTP_SERVER = HttpServer.create(new InetSocketAddress("0.0.0.0", 4000), 0);
			HTTP_SERVER.createContext("/", (req) -> {
				final URI uri = req.getRequestURI();
				
				//TODO: remove
				ChatUtils.print("Got request to " + uri.toString());
				
				byte[] response;
				
				try (InputStream is = Request.class.getResourceAsStream("/site/login.html")) {
					Objects.requireNonNull(is, "Couldn't find login.html");
					response = IOUtils.toByteArray(is);
				}
				
				req.getResponseHeaders().add("Content-Type", "text/html");
				req.sendResponseHeaders(200, response.length);
				req.getResponseBody().write(response);
				req.getResponseBody().close();
			});
		} catch (Exception e) {
			RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "Failed to setup server.");
			e.printStackTrace();
		}
	}
	
	private Status currentStatus;
	
	@Override
	public void onLoad() {
		if(HTTP_SERVER == null) {
			RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "Failed to setup server.");
			return;
		}
		
		HTTP_SERVER.start();
		
		//dummy status for now
		this.currentStatus = new Status();
		this.currentStatus.data = new Status.Data();
		final Status.Data.Song song = new Status.Data.Song();
		song.name = "Dummy Song";
		song.album = "Dummy Album";
		song.release_date = "2021-09-17";
		song.explicit = false;
		
		final Status.Data.Song.Thumbnail x300 = new Status.Data.Song.Thumbnail();
		x300.url = "https://i.scdn.co/image/ab67616d00001e02be82673b5f79d9658ec0a9fd";
		x300.width = x300.height = 300;
		
		song.thumbnails = new Status.Data.Song.Thumbnail[]{
				x300
		};
		
		this.currentStatus.data.song = song;
		
		//hud element
		RusherHackAPI.getHudManager().registerFeature(new SpotifyHudElement(this));
		
		//TODO: maybe window in the future?
	}
	
	@Override
	public void onUnload() {
		if(HTTP_SERVER != null) {
			HTTP_SERVER.stop(0);
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
	
	public Status getStatus() {
		return this.currentStatus;
	}
	
	public Status.Data.Song getSong() {
		return this.currentStatus.data.song;
	}
	
}
