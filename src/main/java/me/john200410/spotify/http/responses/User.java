package me.john200410.spotify.http;

/**
 * @author John200410 12/19/2023
 */
public class User {

    public String country;
    public String display_name;
    public String email;
    public ExplicitContent explicit_content;
    public ExternalUrls external_urls;
    public Followers followers;
    public String href;
    public String id;
    public Image[] images;
    public String product;
    public String type;
    public String uri;

    public static class ExplicitContent {
        public boolean filter_enabled;
        public boolean filter_locked;
    }

    public static class ExternalUrls {
        public String spotify;
    }

    public static class Followers {
        public String href;
        public int total;
    }

    public static class Image {
        public String url;
        public int height;
        public int width;
    }
}
