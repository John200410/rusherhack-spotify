package me.john200410.spotify;

import me.john200410.spotify.ui.SpotifyHudElement;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;

import java.net.http.HttpClient;

/**
 * @author John200410
 */
public class SpotifyPlugin extends Plugin {
	
	/**
	 * Constants
	 */
	public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	
	private Status currentStatus;
	
	@Override
	public void onLoad() {
		
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
