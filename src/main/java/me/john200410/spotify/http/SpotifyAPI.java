package me.john200410.spotify.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import net.minecraft.Util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Future;

public class SpotifyAPI {
	
	/**
	 * Constants
	 */
	private static final Gson GSON = new Gson();
	private static final String API_URL = "https://spotfy.darkerink.workers.dev";
	
	/**
	 * Variables
	 */
	private Status currentStatus;
	private String token;
	private Boolean premium;
	
	public SpotifyAPI(String token) {
		this.token = token;
		this.updateStatus();
	}
	
	public Status getCurrentStatus() {
		return this.currentStatus;
	}
	
	private String makeRequest(String method, String endpoint) throws IOException, InterruptedException {
		final HttpClient client = HttpClient.newHttpClient();
		final HttpRequest request = HttpRequest.newBuilder()
											   .uri(URI.create(API_URL + endpoint))
											   .header("Authorization", "Bearer " + token)
											   .header("Content-Type", "application/json")
											   .header("Accept", "application/json")
											   .method(method, HttpRequest.BodyPublishers.noBody())
											   .timeout(Duration.ofSeconds(5))
											   .build();
		
		final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		
		return response.body();
	}
	
	public void updateStatus() {
		
		if(false) {
			this.currentStatus = new Status();
			this.currentStatus.data = new Status.Data();
			
			
			this.currentStatus.data.song = new Status.Data.Song();
			this.currentStatus.data.song.name = "Song Name";
			this.currentStatus.data.song.album = "Album";
			this.currentStatus.data.song.release_date = "Release Date";
			this.currentStatus.data.song.explicit = true;
			
			final Status.Data.Song.Thumbnail thumbnailSmall = new Status.Data.Song.Thumbnail();
			thumbnailSmall.url = "https://i.scdn.co/image/ab67616d00004851be82673b5f79d9658ec0a9fd";
			thumbnailSmall.width = thumbnailSmall.height = 64;
			
			final Status.Data.Song.Thumbnail thumbnailMedium = new Status.Data.Song.Thumbnail();
			thumbnailMedium.url = "https://i.scdn.co/image/ab67616d00001e02be82673b5f79d9658ec0a9fd";
			thumbnailMedium.width = thumbnailMedium.height = 300;
			
			final Status.Data.Song.Thumbnail thumbnailLarge = new Status.Data.Song.Thumbnail();
			thumbnailLarge.url = "https://i.scdn.co/image/ab67616d0000b273be82673b5f79d9658ec0a9fd";
			thumbnailLarge.width = thumbnailLarge.height = 640;
			
			
			this.currentStatus.data.song.thumbnails = new Status.Data.Song.Thumbnail[]{/*thumbnailSmall, thumbnailMedium, */thumbnailLarge};
			
			this.currentStatus.data.progress = new Status.Data.Progress();
			this.currentStatus.data.progress.current = 5848;
			this.currentStatus.data.progress.total = 137704;
			
			
			return;
		}
		
		this.submit(() -> {
			try {
				this.currentStatus = this.getStatus();
			} catch(IOException | InterruptedException | JsonSyntaxException e) {
				e.printStackTrace();
			}
		});
	}
	
	public Future<?> submit(Runnable runnable) {
		return Util.backgroundExecutor().submit(runnable);
	}
	
	public Status getStatus() throws IOException, InterruptedException, JsonSyntaxException {
		final String response = makeRequest("GET", "/s/current");
		
		System.out.println("RESPONSE: " + response);
		
		final Status status = GSON.fromJson(response, Status.class);
		
		System.out.println("STATUS: " + status);
		
		if(status.meta.refreshed) {
			this.token = status.meta.newToken;
		}
		
		return status;
	}
	
	public boolean pause() throws IOException, InterruptedException {
		if(!isPremium()) {
			return false;
		}
		
		String response = makeRequest("POST", "/s/pause");
		Status status = GSON.fromJson(response, Status.class);
		
		if(status.meta.refreshed) {
			this.token = status.meta.newToken;
		}
		
		return status.data.success;
	}
	
	public Boolean isPremium() throws IOException, InterruptedException {
		if(this.premium != null) {
			return this.premium;
		}
		
		String response = makeRequest("GET", "/s/premium");
		Status status = GSON.fromJson(response, Status.class);
		
		if(status.meta.refreshed) {
			this.token = status.meta.newToken;
		}
		
		this.premium = status.data.success;
		
		return status.data.success;
	}
	
	public Boolean skip() throws IOException, InterruptedException {
		if(!isPremium()) {
			return false;
		}
		
		String response = makeRequest("POST", "/s/next");
		Status status = GSON.fromJson(response, Status.class);
		
		if(status.meta.refreshed) {
			this.token = status.meta.newToken;
		}
		
		return status.data.success;
	}
	
	public Boolean previous() throws IOException, InterruptedException {
		if(!isPremium()) {
			return false;
		}
		
		String response = makeRequest("POST", "/s/previous");
		Status status = GSON.fromJson(response, Status.class);
		
		if(status.meta.refreshed) {
			this.token = status.meta.newToken;
		}
		
		return status.data.success;
	}
	
	public Boolean play() throws IOException, InterruptedException {
		if(!isPremium()) {
			return false;
		}
		
		String response = makeRequest("POST", "/s/play");
		Status status = GSON.fromJson(response, Status.class);
		
		if(status.meta.refreshed) {
			this.token = status.meta.newToken;
		}
		
		return status.data.success;
	}
	
	public Status.Data getSong() throws IOException, InterruptedException {
		if(!isPremium()) {
			return null;
		}
		
		String response = makeRequest("GET", "/s/current");
		Status status = GSON.fromJson(response, Status.class);
		
		if(status.meta.refreshed) {
			this.token = status.meta.newToken;
		}
		
		return status.data;
	}
	
	public Boolean shuffle(
			Boolean shuffle
	) throws IOException, InterruptedException {
		if(!isPremium()) {
			return false;
		}
		
		String response = makeRequest("POST", "/s/shuffle" + (shuffle ? "/enable" : "/disable"));
		Status status = GSON.fromJson(response, Status.class);
		
		if(status.meta.refreshed) {
			this.token = status.meta.newToken;
		}
		
		return status.data.success;
	}
}