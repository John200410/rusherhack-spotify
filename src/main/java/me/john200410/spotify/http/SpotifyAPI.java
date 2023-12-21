package me.john200410.spotify.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import me.john200410.spotify.http.responses.User;
import me.john200410.spotify.http.responses.CodeGrant;
import me.john200410.spotify.http.responses.PlaybackState;
import me.john200410.spotify.http.responses.Response;
import net.minecraft.Util;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class SpotifyAPI {
	
	/**
	 * Constants
	 */
	private static final Gson GSON = new Gson();
	private static final String API_URL = "https://api.spotify.com";
	private static final String AUTH_URL = "https://accounts.spotify.com";
	
	/**
	 * Variables
	 */
	private PlaybackState currentStatus;

	private String accessToken;
	private String refreshToken;
	public Integer expiresIn;

	private String appID;
	private String appSecret;
	private String redirectURI;

	private Boolean premium;

	public Boolean isPlaying;

	private String deviceID;

	public SpotifyAPI() {
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
	
	private Response makeRequest(String method, String endpoint) throws IOException, InterruptedException {
		final HttpClient client = HttpClient.newHttpClient();
		final HttpRequest request = HttpRequest.newBuilder()
											   .uri(URI.create(API_URL + endpoint))
											   .header("Authorization", "Bearer " + this.accessToken)
											   .header("Content-Type", "application/json")
											   .header("Accept", "application/json")
											   .method(method, HttpRequest.BodyPublishers.noBody())
											   .timeout(Duration.ofSeconds(8))
											   .build();
		
		final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		Map<String, Object> responseBody = new HashMap<>();

		responseBody.put("statusCode", response.statusCode());
		responseBody.put("body", response.body());

		return GSON.fromJson(GSON.toJson(responseBody), Response.class);
	}

	public Boolean authorizationCodeGrant(String code) throws IOException, InterruptedException {
		Map<Object, Object> data = new HashMap<>();
		data.put("grant_type", "authorization_code");
		data.put("redirect_uri", this.redirectURI);
		data.put("code", code);
		data.put("client_id", this.appID);
		data.put("client_secret", this.appSecret);

		String requestBody = data.entrySet().stream()
				.map(entry -> entry.getKey().toString() + "=" + entry.getValue().toString())
				.collect(Collectors.joining("&"));

		final HttpClient client = HttpClient.newHttpClient();

		final HttpRequest request = HttpRequest.newBuilder()
											   .uri(URI.create(AUTH_URL + "/api/token"))
											   .header("Content-Type", "application/x-www-form-urlencoded")
											   .header("Accept", "application/json")
											   .POST(HttpRequest.BodyPublishers.ofString(requestBody))
											   .timeout(Duration.ofSeconds(8))
											   .build();

		final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		final CodeGrant body = GSON.fromJson(response.body(), CodeGrant.class);

		if(response.statusCode() != 200) {
			return false;
		}

		this.accessToken = body.access_token;
		this.refreshToken = body.refresh_token;
		this.expiresIn = body.expires_in;

		return true;
	}

	private Boolean authorizationRefreshToken() throws IOException, InterruptedException {
		List<NameValuePair> urlParameters = new ArrayList<>();
		urlParameters.add(new BasicNameValuePair("grant_type", "refresh_token"));
		urlParameters.add(new BasicNameValuePair("refresh_token", this.refreshToken));
		urlParameters.add(new BasicNameValuePair("client_id", this.appID));
		urlParameters.add(new BasicNameValuePair("client_secret", this.appSecret));

		final HttpClient client = HttpClient.newHttpClient();

		final HttpRequest request = HttpRequest.newBuilder()
											   .uri(URI.create(AUTH_URL + "/api/token"))
											   .header("Content-Type", "application/x-www-form-urlencoded")
											   .header("Accept", "application/json")
											   .POST(HttpRequest.BodyPublishers.ofString(urlParameters.toString()))
											   .timeout(Duration.ofSeconds(8))
											   .build();

		final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		if(response.statusCode() != 200) {
			return false;
		}

		final CodeGrant body = GSON.fromJson(response.body(), CodeGrant.class);

		this.accessToken = body.access_token;
		this.expiresIn = body.expires_in;

		return true;
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

	public void updateAccessToken() throws IOException, InterruptedException {
		if(this.expiresIn == null) {
			return;
		}

		if(System.currentTimeMillis() / 1000 > this.expiresIn) {
			this.authorizationRefreshToken();
		}
	}
	
	public void updateStatus() {

		this.submit(() -> {
			try {
				System.out.println("Updating status");
				this.currentStatus = this.getStatus();
			} catch(IOException | InterruptedException | JsonSyntaxException e) {
				e.printStackTrace();
			}
		});
	}
	
	public Future<?> submit(Runnable runnable) {
		return Util.backgroundExecutor().submit(runnable);
	}

	private String getUrl(String url, Boolean Params) {
		return this.deviceID == null ? url : url + (Params ? "&device_id=" + this.deviceID : "?device_id=" + this.deviceID);
	}

	// only method that doesn't require premium
	public PlaybackState getStatus() throws IOException, InterruptedException, JsonSyntaxException {
		this.updateAccessToken();

		Response request = this.makeRequest(
				"GET",
				this.getUrl("/v1/me/player", false)
		);

		if (request.statusCode != 200) {
			System.out.println("STATUS CODE: " + request.statusCode);

			this.isPlaying = false;

			return null;
		}

		PlaybackState status = GSON.fromJson(request.body, PlaybackState.class);

		this.deviceID = status.device.id;

		return status;
	}

	public Boolean pause() throws IOException, InterruptedException {
		if (!this.checkPremium()) {
			return false;
		}

		this.updateAccessToken();

		Response request = this.makeRequest(
				"PUT",
				this.getUrl("/v1/me/player/pause", false)
		);

		if (request.statusCode != 204) {
			System.out.println("STATUS CODE: " + request.statusCode);

			this.isPlaying = false;

			return false;
		}

		return true;
	}

	public Boolean play() throws IOException, InterruptedException {
		if (!this.checkPremium()) {
			return false;
		}

		this.updateAccessToken();

		Response request = this.makeRequest(
				"PUT",
				this.getUrl("/v1/me/player/play", false)
		);

		if (request.statusCode != 204) {
			System.out.println("STATUS CODE: " + request.statusCode);

			this.isPlaying = false;

			return false;
		}

		return true;
	}

	public Boolean next() throws IOException, InterruptedException {
		if (!this.checkPremium()) {
			return false;
		}

		this.updateAccessToken();

		Response request = this.makeRequest(
				"POST",
				this.getUrl("/v1/me/player/next", false)
		);

		if (request.statusCode != 204) {
			System.out.println("STATUS CODE: " + request.statusCode);

			this.isPlaying = false;

			return false;
		}

		return true;
	}

	public Boolean previous() throws IOException, InterruptedException {
		if (!this.checkPremium()) {
			return false;
		}

		this.updateAccessToken();

		Response request = this.makeRequest(
				"POST",
				this.getUrl("/v1/me/player/previous", false)
		);

		if (request.statusCode != 204) {
			System.out.println("STATUS CODE: " + request.statusCode);

			this.isPlaying = false;

			return false;
		}

		return true;
	}

	public Boolean setShuffle(Boolean shuffle) throws IOException, InterruptedException {
		if (!this.checkPremium()) {
			return false;
		}

		this.updateAccessToken();

		Response request = this.makeRequest(
				"PUT",
				this.getUrl("/v1/me/player/shuffle?state=" + shuffle, true)
		);

		if (request.statusCode != 204) {
			System.out.println("STATUS CODE: " + request.statusCode);

			this.isPlaying = false;

			return false;
		}

		return true;
	}

	// repeat can be one of: track, context, or off.
	// track will repeat the current playlist.
	// context will repeat the current song.
	// off will turn repeat off.
	public Boolean setRepeat(String repeat) throws IOException, InterruptedException {
		if (!this.checkPremium()) {
			return false;
		}

		this.updateAccessToken();

		Response request = this.makeRequest(
				"PUT",
				this.getUrl("/v1/me/player/repeat?state=" + repeat, true)
		);

		if (request.statusCode != 204) {
			System.out.println("STATUS CODE: " + request.statusCode);

			this.isPlaying = false;

			return false;
		}

		return true;
	}

	public Boolean checkPremium() throws IOException, InterruptedException {
		if (this.premium != null) {
			return this.premium;
		}

		this.updateAccessToken();

		Response request = this.makeRequest(
				"GET",
				this.getUrl("/v1/me", false)
		);

		if (request.statusCode != 200) {
			System.out.println("STATUS CODE: " + request.statusCode);

			this.isPlaying = false;

			return false;
		}

		User user = GSON.fromJson(request.body, User.class);

		this.premium = user.product.equals("premium");

		return this.premium;
	}
}