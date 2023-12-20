package me.john200410.spotify;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;

public class SpotifyRequester {

    private String token;

    public Boolean premium;

    public String apiUrl = "https://spotfy.darkerink.workers.dev";

    public SpotifyRequester() {

    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getToken() {
        return this.token;
    }

    public String makeRequest(
            String method,
            String endpoint
    ) throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();

        var request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + endpoint))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method(method, java.net.http.HttpRequest.BodyPublishers.noBody())
                .build();

        var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        return response.body();
    }

    public boolean pause() throws IOException, InterruptedException {
        if (!isPremium()) {
            return false;
        }

        String response = makeRequest("POST", "/s/pause");

        Gson gson = new Gson();

        Status status = gson.fromJson(response, Status.class);

        if (status.meta.refreshed) {
            this.token = status.meta.newToken;
        }

        return status.data.success;
    }

    public Boolean isPremium() throws IOException, InterruptedException {
        if (this.premium != null) {
            return this.premium;
        }

        String response = makeRequest("GET", "/s/premium");

        Gson gson = new Gson();

        Status status = gson.fromJson(response, Status.class);

        if (status.meta.refreshed) {
            this.token = status.meta.newToken;
        }

        this.premium = status.data.success;

        return status.data.success;
    }

    public Boolean skip() throws IOException, InterruptedException {
        if (!isPremium()) {
            return false;
        }

        String response = makeRequest("POST", "/s/next");

        Gson gson = new Gson();

        Status status = gson.fromJson(response, Status.class);

        if (status.meta.refreshed) {
            this.token = status.meta.newToken;
        }

        return status.data.success;
    }

    public Boolean previous() throws IOException, InterruptedException {
        if (!isPremium()) {
            return false;
        }

        String response = makeRequest("POST", "/s/previous");

        Gson gson = new Gson();

        Status status = gson.fromJson(response, Status.class);

        if (status.meta.refreshed) {
            this.token = status.meta.newToken;
        }

        return status.data.success;
    }

    public Boolean play() throws IOException, InterruptedException {
        if (!isPremium()) {
            return false;
        }

        String response = makeRequest("POST", "/s/play");

        Gson gson = new Gson();

        Status status = gson.fromJson(response, Status.class);

        if (status.meta.refreshed) {
            this.token = status.meta.newToken;
        }

        return status.data.success;
    }

    public Status.Data getSong() throws IOException, InterruptedException {
        if (!isPremium()) {
            return null;
        }

        String response = makeRequest("GET", "/s/current");

        Gson gson = new Gson();

        Status status = gson.fromJson(response, Status.class);

        if (status.meta.refreshed) {
            this.token = status.meta.newToken;
        }

        return status.data;
    }

    public Boolean shuffle(
            Boolean shuffle
    ) throws IOException, InterruptedException {
        if (!isPremium()) {
            return false;
        }

        String response = makeRequest("POST", "/s/shuffle" + (shuffle ? "/enable" : "/disable"));

        Gson gson = new Gson();

        Status status = gson.fromJson(response, Status.class);

        if (status.meta.refreshed) {
            this.token = status.meta.newToken;
        }

        return status.data.success;
    }
}