package me.john200410.spotify;

/**
 * @author John200410 12/19/2023
 */
public class Status {
	
	public Data data;
	public Meta meta;
	
	public static class Data {
		public boolean shuffling;
		public Progress progress;
		public Song song;
		public boolean is_playing;
		
		public static class Progress {
			public int current;
			public int total;
			public String expected_to_end_at;
		}
		
		public static class Song {
			public String name;
			public String album;
			public String release_date;
			public boolean explicit;
			public Thumbnail[] thumbnails;
			
			public static class Thumbnail {
				public String url;
				public int height;
				public int width;
			}
			
			@Override
			public int hashCode() {
				return this.name.hashCode() + this.album.hashCode() + this.release_date.hashCode() + (this.explicit ? 1 : 0);
			}
			
			@Override
			public boolean equals(Object obj) {
				return obj instanceof Song && this.hashCode() == obj.hashCode();
			}
		}
		
	}
	
	public static class Meta {
		public boolean refreshed;
		public String newToken;
	}
	
}