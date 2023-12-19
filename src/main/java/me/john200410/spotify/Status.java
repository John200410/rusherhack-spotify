package me.john200410.spotify;

/**
 * @author John200410 12/19/2023
 */
public class Status {
	
	Data data;
	Meta meta;
	
	static class Data {
		boolean shuffling;
		Progress progress;
		Song song;
		boolean is_playing;
		
		static class Progress {
			int current;
			int total;
			String expected_to_end_at;
		}
		
		static class Song {
			String name;
			String album;
			String release_date;
			boolean explicit;
			Thumbnail[] thumbnails;
			
			static class Thumbnail {
				String url;
				int height;
				int width;
			}
		}
		
	}
	
	static class Meta {
		boolean refreshed;
		String newToken;
	}
	
}