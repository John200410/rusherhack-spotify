package me.john200410.spotify.http;

import com.google.gson.JsonSyntaxException;
import me.john200410.spotify.Config;
import me.john200410.spotify.SpotifyPlugin;
import me.john200410.spotify.http.responses.CodeGrant;
import me.john200410.spotify.http.responses.PlaybackState;
import me.john200410.spotify.http.responses.Response;
import me.john200410.spotify.http.responses.User;
import net.minecraft.Util;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.core.notification.NotificationType;
import org.rusherhack.core.utils.MathUtils;
import org.rusherhack.core.utils.Timer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class SpotifyAPI {
	
	/**
	 * Constants
	 */
	public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	private static final String API_URL = "https://api.spotify.com";
	private static final String AUTH_URL = "https://accounts.spotify.com";
	
	/**
	 * Variables
	 */
	private final SpotifyPlugin plugin;
	private boolean isConnected = false;
	private boolean playbackAvailable = false;
	private PlaybackState currentStatus;
	private final Timer statusUpdateTimer = new Timer();
	
	private final Timer refreshTokenTimer = new Timer();
	private String accessToken;
	public String refreshToken;
	private Integer expiresIn;
	
	public String appID;
	public String appSecret;
	private String redirectURI;
	
	private Boolean premium;
	
	private String deviceID;
	
	public SpotifyAPI(SpotifyPlugin plugin) {
		this.plugin = plugin;
	}
	
	public void updateStatus(long rateLimit) {
		
		if(rateLimit > 0 && !this.statusUpdateTimer.passed(rateLimit)) {
			return;
		}
		
		this.submit(() -> {
			try {
				this.currentStatus = this.getStatus();
			} catch(IOException | InterruptedException | JsonSyntaxException e) {
				e.printStackTrace();
			}
		});
		this.statusUpdateTimer.reset();
	}
	
	public void submitTogglePlay() {
		this.submit(() -> {
			try {
				this.togglePlay();
			} catch(NoPremiumException e) {
				RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "Spotify Premium is required for this function!");
			} catch(IOException | InterruptedException e) {
				e.printStackTrace();
			}
		});
	}
	
	public void submitNext() {
		this.submit(() -> {
			try {
				this.next();
			} catch(NoPremiumException e) {
				RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "Spotify Premium is required for this function!");
			} catch(IOException | InterruptedException e) {
				e.printStackTrace();
			}
		});
	}
	
	public void submitPrevious() {
		this.submit(() -> {
			try {
				this.previous();
			} catch(NoPremiumException e) {
				RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "Spotify Premium is required for this function!");
			} catch(IOException | InterruptedException e) {
				e.printStackTrace();
			}
		});
	}
	
	public void submitToggleShuffle() {
		this.submit(() -> {
			try {
				this.setShuffle(!this.currentStatus.shuffle_state);
			} catch(NoPremiumException e) {
				RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "Spotify Premium is required for this function!");
			} catch(IOException | InterruptedException e) {
				e.printStackTrace();
			}
		});
	}
	
	public void submitToggleRepeat() {
		this.submit(() -> {
			try {
				String repeat = this.currentStatus.repeat_state;
				if(repeat.equals("track")) {
					repeat = "off";
				} else if(repeat.equals("context")) {
					repeat = "track";
				} else {
					repeat = "context";
				}
				
				this.setRepeat(repeat);
			} catch(NoPremiumException e) {
				RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "Spotify Premium is required for this function!");
			} catch(IOException | InterruptedException e) {
				e.printStackTrace();
			}
		});
	}
	
	public void submitSeek(long ms) {
		this.submit(() -> {
			try {
				this.seek(ms);
			} catch(NoPremiumException e) {
				RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "Spotify Premium is required for this function!");
			} catch(IOException | InterruptedException e) {
				e.printStackTrace();
			}
		});
	}
	
	// only method that doesn't require premium
	private PlaybackState getStatus() throws IOException, InterruptedException, JsonSyntaxException {
		this.updateAccessToken();
		
		final Response request = this.makeRequest(
				"GET",
				this.getUrl("/v1/me/player", false)
		);

		this.statusUpdateTimer.reset();
		
		switch(request.statusCode()) {
			case 200 -> this.playbackAvailable = true;
			case 204 -> {
				this.playbackAvailable = false;
				//this.plugin.getLogger().error("UPDATESTATUS STATUS CODE: " + request.statusCode());
				return null;
			}
			case 401 -> {
				this.isConnected = false;
				this.plugin.getLogger().error("UPDATESTATUS STATUS CODE: " + request.statusCode());
				return null;
			}
			default -> {
				this.plugin.getLogger().error("UPDATESTATUS STATUS CODE: " + request.statusCode());
				return null;
			}
		}
		
		final PlaybackState status = SpotifyPlugin.GSON.fromJson(request.body(), PlaybackState.class);
		this.deviceID = status.device.id;
		return status;
	}
	
	private boolean togglePlay() throws IOException, InterruptedException, NoPremiumException {
		if(!this.isPremium()) {
			throw new NoPremiumException();
		}
		
		this.updateAccessToken();
		
		final String url = this.currentStatus.is_playing ? "/v1/me/player/pause" : "/v1/me/player/play";
		final Response request = this.makeRequest(
				"PUT",
				this.getUrl(url, false)
		);
		
		switch(request.statusCode()) {
			case 204 -> this.playbackAvailable = true;
			case 401 -> {
				this.isConnected = false;
				this.plugin.getLogger().error("TOGGLEPLAY STATUS CODE: " + request.statusCode());
				return false;
			}
			default -> {
				this.plugin.getLogger().error("TOGGLEPLAY STATUS CODE: " + request.statusCode());
				return false;
			}
		}
		
		//update status
		this.currentStatus = this.getStatus();
		return true;
	}
	
	private boolean next() throws NoPremiumException, IOException, InterruptedException {
		if(!this.isPremium()) {
			throw new NoPremiumException();
		}
		
		this.updateAccessToken();
		
		final Response request = this.makeRequest(
				"POST",
				this.getUrl("/v1/me/player/next", false)
		);
		
		switch(request.statusCode()) {
			case 204 -> this.playbackAvailable = true;
			case 401 -> {
				this.isConnected = false;
				this.plugin.getLogger().error("NEXT STATUS CODE: " + request.statusCode());
				return false;
			}
			default -> {
				this.plugin.getLogger().error("NEXT STATUS CODE: " + request.statusCode());
				return false;
			}
		}
		
		//update status
		this.currentStatus = this.getStatus();
		return true;
	}
	
	private boolean previous() throws NoPremiumException, IOException, InterruptedException {
		if(!this.isPremium()) {
			throw new NoPremiumException();
		}
		
		this.updateAccessToken();
		
		final Response request = this.makeRequest(
				"POST",
				this.getUrl("/v1/me/player/previous", false)
		);
		
		switch(request.statusCode()) {
			case 204 -> this.playbackAvailable = true;
			case 401 -> {
				this.isConnected = false;
				this.plugin.getLogger().error("PREVIOUS STATUS CODE: " + request.statusCode());
				return false;
			}
			default -> {
				this.plugin.getLogger().error("PREVIOUS STATUS CODE: " + request.statusCode());
				return false;
			}
		}
		
		//update status
		this.currentStatus = this.getStatus();
		return true;
	}
	
	private boolean setShuffle(boolean shuffle) throws NoPremiumException, IOException, InterruptedException {
		if(!this.isPremium()) {
			throw new NoPremiumException();
		}
		
		this.updateAccessToken();
		
		final Response request = this.makeRequest(
				"PUT",
				this.getUrl("/v1/me/player/shuffle?state=" + shuffle, true)
		);
		
		switch(request.statusCode()) {
			case 204 -> this.playbackAvailable = true;
			case 401 -> {
				this.isConnected = false;
				this.plugin.getLogger().error("SHUFFLE STATUS CODE: " + request.statusCode());
				return false;
			}
			default -> {
				this.plugin.getLogger().error("SHUFFLE STATUS CODE: " + request.statusCode());
				return false;
			}
		}
		
		//update status
		this.currentStatus = this.getStatus();
		return true;
	}
	
	// repeat can be one of: track, context, or off.
	// track will repeat the current playlist.
	// context will repeat the current song.
	// off will turn repeat off.
	private boolean setRepeat(String repeat) throws NoPremiumException, IOException, InterruptedException {
		if(!this.isPremium()) {
			throw new NoPremiumException();
		}
		
		this.updateAccessToken();
		
		final Response request = this.makeRequest(
				"PUT",
				this.getUrl("/v1/me/player/repeat?state=" + repeat, true)
		);
		
		switch(request.statusCode()) {
			case 204 -> this.playbackAvailable = true;
			case 401 -> {
				this.isConnected = false;
				this.plugin.getLogger().error("REPEAT STATUS CODE: " + request.statusCode());
				return false;
			}
			default -> {
				this.plugin.getLogger().error("REPEAT STATUS CODE: " + request.statusCode());
				return false;
			}
		}
		
		//update status
		this.currentStatus = this.getStatus();
		return true;
	}
	
	private boolean seek(long ms) throws NoPremiumException, IOException, InterruptedException {
		if(!this.isPremium()) {
			throw new NoPremiumException();
		}
		
		this.updateAccessToken();
		
		final long duration = this.currentStatus.item.duration_ms;
		
		//clamp
		ms = MathUtils.clamp(ms, 0, duration);
		
		final Response request = this.makeRequest(
				"PUT",
				this.getUrl("/v1/me/player/seek?position_ms=" + ms, true)
		);
		
		switch(request.statusCode()) {
			case 204:
				this.playbackAvailable = true;
				break;
			case 401:
				this.plugin.getLogger().error("Lost connection to Spotify");
				this.isConnected = false;
			default:
				this.plugin.getLogger().error("REPEAT STATUS CODE: " + request.statusCode());
				return false;
		}
		
		//update status
		this.currentStatus = this.getStatus();
		return true;
	}
	
	private boolean isPremium() {
		if(this.premium != null) {
			return this.premium;
		}
		
		try {
			this.updateAccessToken();
			
			final Response request = this.makeRequest(
					"GET",
					this.getUrl("/v1/me", false)
			);
			
			switch(request.statusCode()) {
				case 200 -> {
				}
				case 401 -> {
					this.isConnected = false;
					this.plugin.getLogger().error("USER STATUS CODE: " + request.statusCode());
					return false;
				}
				default -> {
					this.plugin.getLogger().error("USER STATUS CODE: " + request.statusCode());
					return false;
				}
			}
			
			final User user = SpotifyPlugin.GSON.fromJson(request.body(), User.class);
			this.premium = user.product.equals("premium");
			return this.premium;
		} catch(IOException | InterruptedException e) {
			return false;
		}
	}
	
	private Response makeRequest(String method, String endpoint) throws IOException, InterruptedException {
		final HttpRequest request = HttpRequest.newBuilder()
											   .uri(URI.create(API_URL + endpoint))
											   .header("Authorization", "Bearer " + this.accessToken)
											   .header("Content-Type", "application/json")
											   .header("Accept", "application/json")
											   .method(method, HttpRequest.BodyPublishers.noBody())
											   .timeout(Duration.ofSeconds(8))
											   .build();
		
		final HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		return new Response(response.statusCode(), response.body());
	}
	
	
	public boolean authorizationCodeGrant(String code) throws IOException, InterruptedException {
		Map<Object, Object> data = new HashMap<>();
		data.put("grant_type", "authorization_code");
		data.put("redirect_uri", this.redirectURI);
		data.put("code", code);
		data.put("client_id", this.appID);
		data.put("client_secret", this.appSecret);
		
		String requestBody = data.entrySet().stream()
								 .map(entry -> entry.getKey().toString() + "=" + entry.getValue().toString())
								 .collect(Collectors.joining("&"));
		
		final HttpRequest request = HttpRequest.newBuilder()
											   .uri(URI.create(AUTH_URL + "/api/token"))
											   .header("Content-Type", "application/x-www-form-urlencoded")
											   .header("Accept", "application/json")
											   .POST(HttpRequest.BodyPublishers.ofString(requestBody))
											   .timeout(Duration.ofSeconds(8))
											   .build();
		
		final HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		
		final CodeGrant body = SpotifyPlugin.GSON.fromJson(response.body(), CodeGrant.class);
		
		if(response.statusCode() != 200) {
			return false;
		}
		
		this.accessToken = body.access_token;
		this.refreshToken = body.refresh_token;
		this.setExpiration(body.expires_in);
		this.isConnected = true;
		
		final Config config = this.plugin.getConfig();
		config.appId = this.appID;
		config.appSecret = this.appSecret;
		config.refresh_token = this.refreshToken;
		this.plugin.saveConfig();
		
		return true;
	}
	
	public boolean authorizationRefreshToken() throws IOException, InterruptedException {
		Map<Object, Object> data = new HashMap<>();
		data.put("grant_type", "refresh_token");
		data.put("refresh_token", this.refreshToken);
		data.put("client_id", this.appID);
		data.put("client_secret", this.appSecret);
		
		String requestBody = data.entrySet().stream()
								 .map(entry -> entry.getKey().toString() + "=" + entry.getValue().toString())
								 .collect(Collectors.joining("&"));
		
		final HttpRequest request = HttpRequest.newBuilder()
											   .uri(URI.create(AUTH_URL + "/api/token"))
											   .header("Content-Type", "application/x-www-form-urlencoded")
											   .header("Accept", "application/json")
											   .POST(HttpRequest.BodyPublishers.ofString(requestBody))
											   .timeout(Duration.ofSeconds(8))
											   .build();
		
		final HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		
		if(response.statusCode() != 200) {
			/*
			ChatUtils.print("debug 8");
			this.plugin.getLogger().info(response.body());
			
			 */
			this.isConnected = false;
			return false;
		}
		
		final CodeGrant body = SpotifyPlugin.GSON.fromJson(response.body(), CodeGrant.class);
		
		this.accessToken = body.access_token;
		this.setExpiration(body.expires_in);
		this.isConnected = true;
		
		final Config config = this.plugin.getConfig();
		config.appId = this.appID;
		config.appSecret = this.appSecret;
		config.refresh_token = this.refreshToken;
		this.plugin.saveConfig();
		
		return true;
	}
	
	private void setExpiration(int expiresIn) {
		this.expiresIn = expiresIn;
		this.refreshTokenTimer.reset();
	}
	
	public String generateOAuthUrl() {
		final String[] scopes = new String[]{
				"user-read-private", // So we can check if the user is premium
				"user-read-currently-playing", // So we can get the current song
				"user-read-playback-state", // So we can get the current song
				"user-modify-playback-state" // So we can control the player
		};
		
		return AUTH_URL + "/authorize?client_id=" + this.appID + "&response_type=code&redirect_uri=" + this.redirectURI + "&scope=" + String.join("%20", scopes);
	}
	
	private void updateAccessToken() throws IOException, InterruptedException {
		if(this.expiresIn == null) {
			return;
		} else if(!this.isConnected && this.refreshTokenTimer.passed(10000L)) {
			this.authorizationRefreshToken();
			this.refreshTokenTimer.reset();
			return;
		}
		
		if(this.refreshTokenTimer.passed(this.expiresIn * 1000L)) {
			this.authorizationRefreshToken();
		}
	}
	
	public Future<?> submit(Runnable runnable) {
		return Util.backgroundExecutor().submit(runnable);
	}
	
	private String getUrl(String url, Boolean Params) {
		return this.deviceID == null ? url : url + (Params ? "&device_id=" + this.deviceID : "?device_id=" + this.deviceID);
	}
	
	public SpotifyAPI setAppID(String appID) {
		this.appID = appID;
		return this;
	}
	
	public SpotifyAPI setAppSecret(String appSecret) {
		this.appSecret = appSecret;
		return this;
	}
	
	public SpotifyAPI setRedirectURI(String redirectURI) {
		this.redirectURI = redirectURI;
		return this;
	}
	
	public PlaybackState getCurrentStatus() {
		return this.currentStatus;
	}
	
	public boolean isConnected() {
		return this.isConnected;
	}
	
	public boolean isPlaybackAvailable() {
		return this.playbackAvailable;
	}
	
}