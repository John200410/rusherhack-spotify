package me.john200410.spotify;

import me.john200410.spotify.ui.SpotifyHudElement;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;

/**
 * @author John200410
 */
public class SpotifyPlugin extends Plugin {
	
	@Override
	public void onLoad() {
		RusherHackAPI.getHudManager().registerFeature(new SpotifyHudElement());
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
}
