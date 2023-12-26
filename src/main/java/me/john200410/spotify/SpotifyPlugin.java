package me.john200410.spotify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpServer;
import me.john200410.spotify.http.SpotifyAPI;
import me.john200410.spotify.ui.SpotifyHudElement;
import org.apache.commons.io.IOUtils;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author John200410
 */
public class SpotifyPlugin extends Plugin {
	
	public static final File CONFIG_FILE = RusherHackAPI.getConfigPath().resolve("spotify.json").toFile();
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	
	private Config config = new Config();
	private HttpServer httpServer;
	private SpotifyAPI api;
	
	@Override
	public void onLoad() {
		
		//try load config
		if(CONFIG_FILE.exists()) {
			try {
				this.config = GSON.fromJson(IOUtils.toString(CONFIG_FILE.toURI(), StandardCharsets.UTF_8), Config.class);
			} catch(IOException e) {
				this.logger.warn("Failed to load config");
			}
		}
		
		try {
			this.httpServer = this.setupServer();
			this.httpServer.start();
			this.api = new SpotifyAPI(this);
			this.api.appID = this.config.appId;
			this.api.appSecret = this.config.appSecret;
			this.api.refreshToken = this.config.refresh_token;
			
			if(!this.api.appID.isEmpty() && !this.api.appSecret.isEmpty() && !this.api.refreshToken.isEmpty()) {
				try {
					this.api.authorizationRefreshToken();
				} catch(Throwable t) {
					t.printStackTrace();
				}
			}
			
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
		
		this.saveConfig();
	}
	
	@Override
	public String getName() {
		return "Spotify";
	}
	
	@Override
	public String getVersion() {
		return "1.1.0";
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
			
			final Map<String, String> queryParams = getQueryParameters(uri.getQuery());
			
			if(uri.getPath().equals("/callback")) {
				final String code = queryParams.get("code");
				
				try {
					var res = this.api.authorizationCodeGrant(code);
					
					if(res) {
						this.logger.info("Successfully got access token");
					} else {
						this.logger.error("Failed to get access token");
					}
					
					
				} catch(InterruptedException e) {
					e.printStackTrace();
					this.logger.error("Failed to get access token");
				}
				
				try(InputStream is = SpotifyPlugin.class.getResourceAsStream("/site/success.html")) {
					Objects.requireNonNull(is, "Couldn't find login.html");
					response = IOUtils.toByteArray(is);
				}
				
				req.getResponseHeaders().add("Content-Type", "text/html");
			} else if(uri.getPath().equals("/")) {
				
				try(InputStream is = SpotifyPlugin.class.getResourceAsStream("/site/login.html")) {
					Objects.requireNonNull(is, "Couldn't find login.html");
					response = IOUtils.toByteArray(is);
				}
				
				req.getResponseHeaders().add("Content-Type", "text/html");
			} else if(uri.getPath().equals("/setup")) {
				final String appId = queryParams.get("appId");
				final String appSecret = queryParams.get("appSecret");
				
				String oauthUrl = this.api.setAppID(appId).setAppSecret(appSecret).setRedirectURI("http://localhost:4000/callback").generateOAuthUrl();
				
				response = ("{\"url\": \"" + oauthUrl + "\"}").getBytes();
				req.getResponseHeaders().add("Content-Type", "application/json");
			}
			
			req.getResponseHeaders().add("Content-Type", "text/html");
			req.sendResponseHeaders(200, response.length);
			req.getResponseBody().write(response);
			req.getResponseBody().close();
		});
		
		return server;
	}
	
	private Map<String, String> getQueryParameters(String query) {
		Map<String, String> queryParams = new HashMap<>();
		
		if(query != null) {
			String[] pairs = query.split("&");
			
			for(String pair : pairs) {
				String[] keyValue = pair.split("=");
				if(keyValue.length == 2) {
					String key = keyValue[0];
					String value = keyValue[1];
					queryParams.put(key, value);
				}
			}
		}
		
		return queryParams;
	}
	
	public Config getConfig() {
		return this.config;
	}
	
	public void saveConfig() {
		try(FileWriter writer = new FileWriter(CONFIG_FILE)) {
			GSON.toJson(this.config, writer);
		} catch(IOException e) {
			this.logger.error("Failed to save config");
		}
	}
	
}
