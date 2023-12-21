package me.john200410.spotify.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
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

	private final String appID;
	private final String appSecret;
	private final String redirectURI;

	private Boolean premium;


	public SpotifyAPI(
			String appID,
			String appSecret,
			String redirectURI
	) {
		this.appID = appID;
		this.appSecret = appSecret;
		this.redirectURI = redirectURI;
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
	
	public PlaybackState getStatus() throws IOException, InterruptedException, JsonSyntaxException {
		this.updateAccessToken();

		Response request = this.makeRequest(
				"GET",
				"/v1/me/player"
		);

		if (request.statusCode != 200) {
			System.out.println("STATUS CODE: " + request.statusCode);

			return null;
		}

		PlaybackState status = GSON.fromJson(request.body, PlaybackState.class);

		return status;
	}
}