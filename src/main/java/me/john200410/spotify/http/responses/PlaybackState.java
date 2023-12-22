package me.john200410.spotify.http.responses;

/**
 * @author John200410 12/19/2023
 */
public class PlaybackState {

	public Device device;
	public String repeat_state;
	public boolean shuffle_state;
	public Context context;
	public long timestamp;
	public int progress_ms;
	public boolean is_playing;
	public Item item;
	public String currently_playing_type;
	public Actions actions;

	public static class Device {
		public String id;
		public boolean is_active;
		public boolean is_private_session;
		public boolean is_restricted;
		public String name;
		public String type;
		public int volume_percent;
		public boolean supports_volume;
	}

	public static class Context {
		public String type;
		public String href;
		public ExternalUrls external_urls;
		public String uri;

		public static class ExternalUrls {
			public String spotify;
		}
	}

	public static class Item {
		public Album album;
		public Artist[] artists;
		public String[] available_markets;
		public int disc_number;
		public long duration_ms;
		public boolean explicit;
		public ExternalIds external_ids;
		public ExternalUrls external_urls;
		public String href;
		public String id;
		public String name;
		public int popularity;
		public String preview_url;
		public int track_number;
		public String type;
		public String uri;
		public boolean is_local;

		public static class Album {
			public String album_type;
			public int total_tracks;
			public String[] available_markets;
			public ExternalUrls external_urls;
			public String href;
			public String id;
			public Image[] images;
			public String name;
			public String release_date;
			public String release_date_precision;
			public String type;
			public String uri;
			public Artist[] artists;

			public static class Image {
				public String url;
				public int height;
				public int width;
			}
		}

		public static class Artist {
			public ExternalUrls external_urls;
			public String href;
			public String id;
			public String name;
			public String type;
			public String uri;
		}

		public static class ExternalIds {
			public String isrc;
		}

		public static class ExternalUrls {
			public String spotify;
		}
	}

	public static class Actions {
		public Disallows disallows;

		public static class Disallows {
			public boolean resuming;
		}
	}
}
