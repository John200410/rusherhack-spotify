package me.john200410.spotify.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import joptsimple.internal.Strings;
import me.john200410.spotify.SpotifyPlugin;
import me.john200410.spotify.http.SpotifyAPI;
import me.john200410.spotify.http.responses.PlaybackState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.rusherhack.client.api.events.client.input.EventMouse;
import org.rusherhack.client.api.feature.hud.ResizeableHudElement;
import org.rusherhack.client.api.render.IRenderer2D;
import org.rusherhack.client.api.render.RenderContext;
import org.rusherhack.client.api.render.font.IFontRenderer;
import org.rusherhack.client.api.render.graphic.VectorGraphic;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.client.api.ui.ScaledElementBase;
import org.rusherhack.client.api.utils.InputUtils;
import org.rusherhack.core.event.stage.Stage;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.interfaces.IClickable;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.NumberSetting;
import org.rusherhack.core.utils.ColorUtils;
import org.rusherhack.core.utils.MathUtils;
import org.rusherhack.core.utils.Timer;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * @author John200410
 */
public class SpotifyHudElement extends ResizeableHudElement {
	
	public static final int BACKGROUND_COLOR = ColorUtils.transparency(Color.BLACK.getRGB(), 0.5f);
	
	/**
	 * Settings
	 */
	private final BooleanSetting authenticateButton = new BooleanSetting("Authenticate", true)
			.setVisibility(() -> !this.isConnected());
	private final BooleanSetting background = new BooleanSetting("Background", true);
	private final ColorSetting backgroundColor = new ColorSetting("Color", new Color(BACKGROUND_COLOR, true));
	private final NumberSetting<Double> updateDelay = new NumberSetting<>("UpdateDelay", 0.5d, 0.25d, 2d);
	
	/**
	 * Media Controller
	 */
	private final SongInfoHandler songInfo;
	private final DurationHandler duration;
	private final MediaControllerHandler mediaController;
	
	/**
	 * Variables
	 */
	private final VectorGraphic spotifyLogo;
	private final ResourceLocation trackThumbnailResourceLocation;
	private final DynamicTexture trackThumbnailTexture;
	private final SpotifyPlugin plugin;
	private PlaybackState.Item song = null;
	private boolean consumedButtonClick = false;
	
	public SpotifyHudElement(SpotifyPlugin plugin) throws IOException {
		super("Spotify");
		this.plugin = plugin;
		
		this.mediaController = new MediaControllerHandler();
		this.duration = new DurationHandler();
		this.songInfo = new SongInfoHandler();
		
		this.spotifyLogo = new VectorGraphic("spotify/graphics/spotify_logo.svg", 32, 32);
		
		this.trackThumbnailTexture = new DynamicTexture(640, 640, false);
		this.trackThumbnailTexture.setFilter(true, true);
		this.trackThumbnailResourceLocation = mc.getTextureManager().register("rusherhack-spotify-track-thumbnail/", this.trackThumbnailTexture);
		
		//dummy setting whos only purpose is to be clicked to open the web browser
		this.authenticateButton.onChange((b) -> {
			if(!b) {
				try {
					Desktop.getDesktop().browse(new URI("http://localhost:4000/"));
				} catch(IOException | URISyntaxException e) {
					this.plugin.getLogger().error(e.getMessage());
					e.printStackTrace();
				}
				
				this.authenticateButton.setValue(true);
			}
		});
		
		this.background.addSubSettings(backgroundColor);
		
		this.registerSettings(authenticateButton, background, updateDelay);
		
		//dont ask
		//this.setupDummyModuleBecauseImFuckingStupidAndForgotToRegisterHudElementsIntoTheEventBus();
	}
	
	@Override
	public void tick() {
		
		if(!this.isConnected()) {
			return;
		}
		
		final SpotifyAPI api = this.plugin.getAPI();
		api.updateStatus((long) (this.updateDelay.getValue() * 1000));
		
		final PlaybackState status = api.getCurrentStatus();
		
		if(status == null) {
			//ChatUtils.print("Status null?");
			return;
		}
		
		//ChatUtils.print(status.data.song.name);
		
		if(this.song == null || !this.song.equals(status.item)) {
			
			this.song = status.item;
			this.songInfo.updateSong(this.song);
			//update texture
			if(this.song.album.images.length > 0) {
				
				//highest resolution thumbnail
				PlaybackState.Item.Album.Image thumbnail = null;
				for(PlaybackState.Item.Album.Image t : this.song.album.images) {
					
					//too large
					if(t.width < 640 || t.height < 640) continue;
					
					if(thumbnail == null || (t.width > thumbnail.width && t.height > thumbnail.height)) {
						thumbnail = t;
					}
				}
				
				if(thumbnail == null) {
					this.plugin.getLogger().error("Thumbnail null?");
					return;
				}
				
				final String thumbnailURL = thumbnail.url;
				api.submit(() -> {
					try {
						final HttpRequest request = HttpRequest.newBuilder(new URI(thumbnailURL))
															   .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.102 Safari/537.36")
															   .build();
						
						final HttpResponse<InputStream> response = SpotifyAPI.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
						final NativeImage nativeImage = NativeImage.read(response.body());
						
						RenderSystem.recordRenderCall(() -> {
							this.trackThumbnailTexture.setPixels(nativeImage);
							this.trackThumbnailTexture.upload();
							this.trackThumbnailTexture.setFilter(true, true);
						});
					} catch(URISyntaxException | IOException | InterruptedException e) {
						this.trackThumbnailTexture.setPixels(null);
						this.plugin.getLogger().error(e.getMessage());
						e.printStackTrace();
					}
				});
			} else {
				this.trackThumbnailTexture.setPixels(null);
			}
		}
	}
	
	@Override
	public void renderContent(RenderContext context, int mouseX, int mouseY) {
		final IRenderer2D renderer = this.getRenderer();
		final IFontRenderer fr = this.getFontRenderer();
		final SpotifyAPI api = this.plugin.getAPI();
		final PoseStack matrixStack = context.pose();
		
		//background
		if(this.background.getValue()) {
			renderer._drawRoundedRectangle(0, 0, this.getWidth(), this.getHeight(), 5, true, false, 0, this.getFillColor(), 0);
		}
		//logo
		renderer.drawGraphicRectangle(this.spotifyLogo, this.getWidth() - 5 - 16, 5, 16, 16);
		
		if(!api.isConnected()) {
			this.trackThumbnailTexture.setPixels(null);
			fr.drawString("Not authenticated with spotify!", 5, 10, -1);
			fr.drawString("Click the \"Authenticate\" button", 5, 30, -1);
			fr.drawString("in the settings to authenticate.", 5, 40, -1);
			return;
		}
		
		if(!api.isPlaybackAvailable()) {
			this.trackThumbnailTexture.setPixels(null);
			fr.drawString("Playback unavailable!", 5, 10, -1);
			fr.drawString("Open spotify on your device", 5, 30, -1);
			return;
		}
		
		final PlaybackState status = api.getCurrentStatus();
		
		if(status == null) {
			this.trackThumbnailTexture.setPixels(null);
			fr.drawString("No status", 5, 10, -1);
			return;
		}
		
		final PlaybackState.Item song = status.item;
		
		if(song == null) {
			this.trackThumbnailTexture.setPixels(null);
			fr.drawString("No song loaded", 5, 10, -1);
			return;
		}
		
		final double leftOffset = 75;
		
		//set correct mouse pos because its set to -1, -1 when not in hud editor
		mouseX = (int) InputUtils.getMouseX();
		mouseY = (int) InputUtils.getMouseY();
		
		/////////////////////////////////////////////////////////////////////
		//top
		/////////////////////////////////////////////////////////////////////
		double topOffset = 5;
		
		//song details
		this.songInfo.setX(leftOffset);
		this.songInfo.setY(topOffset);
		this.songInfo.render(renderer, context, mouseX, mouseY, status);
		topOffset += this.songInfo.getHeight();
		
		/////////////////////////////////////////////////////////////////////
		
		/////////////////////////////////////////////////////////////////////
		//bottom
		/////////////////////////////////////////////////////////////////////
		final double bottomOffset = this.getHeight() - 5 - this.duration.getHeight();
		this.duration.setX(leftOffset);
		this.duration.setY(bottomOffset);
		this.duration.render(renderer, context, mouseX, mouseY, status);
		
		//media controls
		this.mediaController.setX(leftOffset);
		this.mediaController.setY(topOffset);
		this.mediaController.setHeight(bottomOffset - topOffset);
		this.mediaController.render(renderer, context, mouseX, mouseY, status);
		/////////////////////////////////////////////////////////////////////
	}
	
	@Override
	public void postRender(RenderContext context, int mouseX, int mouseY) {
		if(this.trackThumbnailTexture.getPixels() == null || !this.isAvailable()) return;
		
		//draw track thumbnail
		final PoseStack matrixStack = context.pose();
		final GuiGraphics graphics = context.graphics();
		
		//matrixStack.pushPose();
		//matrixStack.translate(0, 0, -250);
		graphics.blit(this.trackThumbnailResourceLocation, 5, 5, 0, 0, 65, 65, 65, 65);
		//matrixStack.popPose();
	}
	
	// clicking on the buttons while in chat
	@Subscribe(stage = Stage.PRE)
	private void onMouseClick(EventMouse.Key event) {
		if(event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return;
		}
		if(!(mc.screen instanceof ChatScreen)) {
			return;
		}
		
		double mouseX = event.getMouseX();
		double mouseY = event.getMouseY();
		
		if(isHovered(mouseX, mouseY) && event.getAction() == GLFW.GLFW_PRESS) {
			this.consumedButtonClick = true;
			mouseClicked(mouseX, mouseY, event.getButton());
			this.consumedButtonClick = false;
		} else if(event.getAction() == GLFW.GLFW_RELEASE) {
			mouseReleased(mouseX, mouseY, event.getButton());
		}
	}
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		
		if(this.mediaController.mouseClicked(mouseX, mouseY, button)) {
			return true;
		} else if(this.duration.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		
		if(this.consumedButtonClick) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}
	
	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		this.duration.mouseReleased(mouseX, mouseY, button);
		this.mediaController.mouseReleased(mouseX, mouseY, button);
		super.mouseReleased(mouseX, mouseY, button);
	}
	
	@Override
	public double getWidth() {
		return 225;
	}
	
	@Override
	public double getHeight() {
		return 75;
	}
	
	private int getFillColor() {
		//TODO: return color based on song thumbnail
		return this.backgroundColor.getValueRGB();
	}
	
	private boolean isConnected() {
		return this.plugin.getAPI().isConnected();
	}
	
	private boolean isAvailable() {
		return this.isConnected() && this.plugin.getAPI().isPlaybackAvailable();
	}
	
	abstract class ElementHandler extends ScaledElementBase implements IClickable {
		
		abstract void render(IRenderer2D renderer, RenderContext context, int mouseX, int mouseY, PlaybackState status);
		
		@Override
		public double getWidth() {
			return SpotifyHudElement.this.getWidth() - 75 - 5;
		}
		
		@Override
		public double getScale() {
			return SpotifyHudElement.this.getScale();
		}
		
		public double getScaledX() {
			return this.getX() * this.getScale();
		}
		
		public double getScaledY() {
			return this.getY() * this.getScale();
		}
		
		@Override
		public boolean isHovered(double mouseX, double mouseY) {
			mouseX -= SpotifyHudElement.this.getStartX();
			mouseY -= SpotifyHudElement.this.getStartY();
			
			return mouseX >= this.getScaledX() && mouseX <= this.getScaledX() + this.getScaledWidth() && mouseY >= this.getScaledY() && mouseY <= this.getScaledY() + this.getScaledHeight();
		}
	}
	
	class SongInfoHandler extends ElementHandler {
		
		private final ScrollingText title = new ScrollingText();
		private final ScrollingText artists = new ScrollingText();
		private final ScrollingText album = new ScrollingText();
		
		@Override
		void render(IRenderer2D renderer, RenderContext context, int mouseX, int mouseY, PlaybackState status) {
			final IFontRenderer fr = SpotifyHudElement.this.getFontRenderer();
			final PoseStack matrixStack = context.pose();
			
			matrixStack.pushPose();
			matrixStack.translate(this.getX(), this.getY(), 0);
			renderer.scissorBox(0, 0, this.getWidth(), this.getHeight());
			
			//smaller scissorbox for title to make room for spotify logo
			final double titleMaxWidth = this.getWidth() - 20;
			renderer.scissorBox(0, -1, titleMaxWidth, this.getHeight());
			this.title.render(context, renderer, fr, titleMaxWidth, -1);
			
			matrixStack.translate(0, fr.getFontHeight() + 1, 0);
			matrixStack.scale(0.75f, 0.75f, 1);
			
			this.artists.render(context, renderer, fr, titleMaxWidth / 0.75, Color.LIGHT_GRAY.getRGB());
			
			renderer.popScissorBox();
			
			matrixStack.translate(0, fr.getFontHeight() + 1, 0);
			
			this.album.render(context, renderer, fr, this.getWidth() / 0.75, Color.LIGHT_GRAY.getRGB());
			
			renderer.popScissorBox();
			matrixStack.popPose();
		}
		
		@Override
		public double getHeight() {
			return (getFontRenderer().getFontHeight() + 1) * 3;
		}
		
		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			return false;
		}
		
		public void updateSong(PlaybackState.Item song) {
			this.title.setText(song.name);
			
			final String[] artists = new String[song.artists.length];
			for(int i = 0; i < song.artists.length; i++) {
				artists[i] = song.artists[i].name;
			}
			
			this.artists.setText("by " + Strings.join(artists, ", "));
			this.album.setText("on " + song.album.name);
		}
		
		static class ScrollingText {
			
			private String text;
			private double scroll = 0;
			private boolean scrolling = false;
			private boolean scrollingForward = false;
			private final Timer pauseTimer = new Timer();
			private long lastUpdate = 0;
			
			void render(RenderContext context, IRenderer2D renderer, IFontRenderer fr, double width, int color) {
				if(this.text == null) {
					return;
				}
				
				final double textWidth = fr.getStringWidth(this.text);
				final double maxScroll = textWidth - width;
				
				if(maxScroll <= 0) {
					fr.drawString(this.text, 0, 0, color);
					return;
				}
				
				if(this.scrolling) {
					this.pauseTimer.reset();
					
					if(this.scrollingForward) {
						this.scroll += (System.currentTimeMillis() - this.lastUpdate) / 75f;
						
						if(this.scroll >= maxScroll) {
							this.scroll = maxScroll;
							this.scrolling = false;
						}
					} else {
						this.scroll -= (System.currentTimeMillis() - this.lastUpdate) / 75f;
						
						if(this.scroll <= 0) {
							this.scroll = 0;
							this.scrolling = false;
						}
					}
				} else {
					if(this.pauseTimer.passed(2500)) {
						this.scrolling = true;
						this.scrollingForward = !this.scrollingForward;
					}
				}
				
				fr.drawString(this.text, -this.scroll, 0, color);
				this.lastUpdate = System.currentTimeMillis();
			}
			
			void setText(String text) {
				this.text = text;
				this.scroll = 0;
				this.pauseTimer.reset();
				this.scrolling = false;
				this.scrollingForward = false;
			}
		}
	}
	
	class DurationHandler extends ElementHandler {
		
		private static final double PROGRESS_BAR_HEIGHT = 2;
		
		/**
		 * Variables
		 */
		private boolean seeking = false;
		
		@Override
		void render(IRenderer2D renderer, RenderContext context, int mouseX, int mouseY, PlaybackState status) {
			final IFontRenderer fr = SpotifyHudElement.this.getFontRenderer();
			final PoseStack matrixStack = context.pose();
			final PlaybackState.Item song = status.item;
			final boolean hovered = this.isHovered(mouseX, mouseY);
			
			matrixStack.pushPose();
			matrixStack.translate(this.getX(), this.getY(), 0);
			
			final double mouseXOffset = SpotifyHudElement.this.getStartX() + this.getScaledX();
			final double mouseYOffset = SpotifyHudElement.this.getStartY() + this.getScaledY();
			mouseX -= (int) mouseXOffset;
			mouseY -= (int) mouseYOffset;
			mouseX = (int) (mouseX / this.getScale());
			mouseY = (int) (mouseY / this.getScale());
			
			final double width = this.getWidth();
			double bottomOffset = this.getHeight();
			final double seekingProgress = MathUtils.clamp((double) mouseX / width, 0, 1);
			
			//progress bar
			final double progressBarHeight = PROGRESS_BAR_HEIGHT;
			final double progress = (double) status.progress_ms / (double) song.duration_ms;
			final boolean hoveredOverProgressBar = hovered && mouseY >= bottomOffset - progressBarHeight - 1;
			renderer._drawRoundedRectangle(0, bottomOffset - progressBarHeight, width, progressBarHeight, 1, true, false, 0, Color.GRAY.getRGB(), 0);
			renderer._drawRoundedRectangle(0, bottomOffset - progressBarHeight, width * (this.seeking ? seekingProgress : progress), progressBarHeight, 1, true, false, 0, hoveredOverProgressBar || this.seeking ? Color.GREEN.getRGB() : Color.WHITE.getRGB(), 0);
			bottomOffset -= progressBarHeight + 1;
			
			//duration
			final String current = String.format("%d:%02d", status.progress_ms / 60000, status.progress_ms / 1000 % 60);
			final String length = String.format("%d:%02d", song.duration_ms / 60000, song.duration_ms / 1000 % 60);
			final double durationHeight = fr.getFontHeight() + 1;
			fr.drawString(current, 0, bottomOffset - durationHeight, Color.LIGHT_GRAY.getRGB());
			fr.drawString(length, width - fr.getStringWidth(length), bottomOffset - durationHeight, Color.LIGHT_GRAY.getRGB());
			bottomOffset -= durationHeight - 1;
			
			//seeking
			if(this.seeking) {
				final int seekingProgressMs = (int) (seekingProgress * song.duration_ms);
				final String seekingTime = String.format("%d:%02d", seekingProgressMs / 60000, seekingProgressMs / 1000 % 60);
				final double seekingTimeWidth = fr.getStringWidth(seekingTime);
				final double seekX = MathUtils.clamp(mouseX, 0, width);
				
				renderer._drawRoundedRectangle(seekX - seekingTimeWidth / 2f, this.getHeight() - progressBarHeight - 1 - durationHeight, seekingTimeWidth, durationHeight, 1, true, false, 0, BACKGROUND_COLOR, 0);
				fr.drawString(seekingTime, seekX - seekingTimeWidth / 2f, this.getHeight() - progressBarHeight - 1 - durationHeight, -1);
				
				renderer.drawCircle(seekX, this.getHeight() - 1, 3, Color.WHITE.getRGB());
			}
			
			matrixStack.popPose();
		}
		
		@Override
		public double getHeight() {
			return PROGRESS_BAR_HEIGHT + 1 + SpotifyHudElement.this.getFontRenderer().getFontHeight() + 1;
		}
		
		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if(!this.isHovered(mouseX, mouseY)) {
				return false;
			}
			
			//localize mouse pos
			mouseY -= SpotifyHudElement.this.getStartY();
			mouseY -= this.getScaledY();
			mouseY = (int) (mouseY / this.getScale());
			
			final SpotifyAPI api = plugin.getAPI();
			final boolean hoveredOverProgressBar = mouseY >= this.getHeight() - PROGRESS_BAR_HEIGHT - 1;
			
			if(hoveredOverProgressBar) {
				this.seeking = true;
				return true;
			}
			
			/*
			try {
				api.authorizationRefreshToken();
				return true;
			} catch(Exception e) {
				e.printStackTrace();
			}
			 */
			
			return false;
		}
		
		@Override
		public void mouseReleased(double mouseX, double mouseY, int button) {
			if(this.seeking) {
				this.seeking = false;
				
				final SpotifyAPI api = plugin.getAPI();
				final PlaybackState status = api.getCurrentStatus();
				
				if(status == null) {
					return;
				}
				
				final PlaybackState.Item song = status.item;
				
				if(song == null) {
					return;
				}
				
				//localize mouse pos
				mouseX -= SpotifyHudElement.this.getStartX();
				mouseY -= SpotifyHudElement.this.getStartY();
				mouseX -= this.getScaledX();
				mouseY -= this.getScaledY();
				mouseX = (int) (mouseX / this.getScale());
				mouseY = (int) (mouseY / this.getScale());
				
				
				final double progress = MathUtils.clamp(mouseX / this.getWidth(), 0, 1);
				final int progressMs = (int) (progress * song.duration_ms);
				
				api.submitSeek(progressMs);
			}
			
			super.mouseReleased(mouseX, mouseY, button);
		}
		
	}
	
	class MediaControllerHandler extends ElementHandler {
		
		private static final double CONTROL_SIZE = 16;
		private static final double PAUSE_PLAY_SIZE = CONTROL_SIZE + 4; //bigger than other controls
		
		/**
		 * Controls
		 */
		private final VectorGraphic playGraphic, pauseGraphic;
		private final VectorGraphic backGraphic, nextGraphic;
		private final VectorGraphic shuffleOnGraphic, shuffleOffGraphic;
		private final VectorGraphic loopOffGraphic, loopAllGraphic, loopSameGraphic;
		private double playPauseX, backX, nextX, shuffleX, loopX;
		
		/**
		 * Variables
		 */
		private double height;
		
		public MediaControllerHandler() throws IOException {
			//load graphics
			this.playGraphic = new VectorGraphic("spotify/graphics/play.svg", 48, 48);
			this.pauseGraphic = new VectorGraphic("spotify/graphics/pause.svg", 48, 48);
			this.backGraphic = new VectorGraphic("spotify/graphics/back.svg", 48, 48);
			this.nextGraphic = new VectorGraphic("spotify/graphics/next.svg", 48, 48);
			this.shuffleOnGraphic = new VectorGraphic("spotify/graphics/shuffle_on.svg", 48, 48);
			this.shuffleOffGraphic = new VectorGraphic("spotify/graphics/shuffle_off.svg", 48, 48);
			this.loopOffGraphic = new VectorGraphic("spotify/graphics/loop_off.svg", 48, 48);
			this.loopAllGraphic = new VectorGraphic("spotify/graphics/loop_all.svg", 48, 48);
			this.loopSameGraphic = new VectorGraphic("spotify/graphics/loop_same.svg", 48, 48);
		}
		
		//TODO: this could use some object oriented programming
		public void render(IRenderer2D renderer, RenderContext context, int mouseX, int mouseY, PlaybackState status) {
			final PoseStack matrixStack = context.pose();
			matrixStack.pushPose();
			matrixStack.translate(this.getX(), this.getY(), 0);
			
			final boolean hovered = this.isHovered(mouseX, mouseY);
			final double mouseXOffset = SpotifyHudElement.this.getStartX() + this.getScaledX();
			final double mouseYOffset = SpotifyHudElement.this.getStartY() + this.getScaledY();
			mouseX -= (int) mouseXOffset;
			mouseY -= (int) mouseYOffset;
			mouseX = (int) (mouseX / this.getScale());
			mouseY = (int) (mouseY / this.getScale());
			
			final double width = this.getWidth();
			final double mediaControlsCenter = this.getHeight() / 2f;
			
			//play/pause
			final VectorGraphic playPauseGraphic = status.is_playing ? this.pauseGraphic : this.playGraphic;
			this.playPauseX = width / 2f - PAUSE_PLAY_SIZE / 2f;
			final boolean playPauseHovered = hovered && mouseX >= this.playPauseX && mouseX <= this.playPauseX + PAUSE_PLAY_SIZE && mouseY <= this.getScaledY() + PAUSE_PLAY_SIZE;
			if(playPauseHovered) {
				renderer._drawRoundedRectangle(this.playPauseX - 1, mediaControlsCenter - PAUSE_PLAY_SIZE / 2f - 1, PAUSE_PLAY_SIZE + 2, PAUSE_PLAY_SIZE + 2, 3, true, false, 0, BACKGROUND_COLOR, 0);
			}
			renderer.drawGraphicRectangle(playPauseGraphic, this.playPauseX, mediaControlsCenter - PAUSE_PLAY_SIZE / 2f, PAUSE_PLAY_SIZE, PAUSE_PLAY_SIZE);
			
			final double smallerGraphicY = mediaControlsCenter - CONTROL_SIZE / 2f;
			double mediaLeftOffset = width / 2f - CONTROL_SIZE / 2f - 5;
			double mediaRightOffset = width / 2f + CONTROL_SIZE / 2f + 5;
			
			//back
			this.backX = mediaLeftOffset - CONTROL_SIZE;
			final boolean backHovered = hovered && mouseX >= this.backX && mouseX <= this.backX + CONTROL_SIZE && mouseY <= this.getScaledY() + CONTROL_SIZE;
			if(backHovered) {
				renderer._drawRoundedRectangle(this.backX, smallerGraphicY, CONTROL_SIZE, CONTROL_SIZE, 3, true, false, 0, BACKGROUND_COLOR, 0);
			}
			renderer.drawGraphicRectangle(this.backGraphic, this.backX, smallerGraphicY, CONTROL_SIZE, CONTROL_SIZE);
			mediaLeftOffset -= CONTROL_SIZE + 5;
			
			//next
			this.nextX = mediaRightOffset;
			final boolean nextHovered = hovered && mouseX >= this.nextX && mouseX <= this.nextX + CONTROL_SIZE && mouseY <= this.getScaledY() + CONTROL_SIZE;
			if(nextHovered) {
				renderer._drawRoundedRectangle(this.nextX, smallerGraphicY, CONTROL_SIZE, CONTROL_SIZE, 3, true, false, 0, BACKGROUND_COLOR, 0);
			}
			renderer.drawGraphicRectangle(this.nextGraphic, this.nextX, smallerGraphicY, CONTROL_SIZE, CONTROL_SIZE);
			mediaRightOffset += CONTROL_SIZE + 5;
			
			//shuffle
			final VectorGraphic shuffleGraphic = status.shuffle_state ? this.shuffleOnGraphic : this.shuffleOffGraphic;
			this.shuffleX = mediaLeftOffset - CONTROL_SIZE;
			final boolean shuffleHovered = hovered && mouseX >= this.shuffleX && mouseX <= this.shuffleX + CONTROL_SIZE && mouseY <= this.getScaledY() + CONTROL_SIZE;
			if(shuffleHovered) {
				renderer._drawRoundedRectangle(this.shuffleX, smallerGraphicY, CONTROL_SIZE, CONTROL_SIZE, 3, true, false, 0, BACKGROUND_COLOR, 0);
			}
			renderer.drawGraphicRectangle(shuffleGraphic, this.shuffleX, smallerGraphicY, CONTROL_SIZE, CONTROL_SIZE);
			mediaLeftOffset -= CONTROL_SIZE + 5;
			
			//loop
			final VectorGraphic loopGraphic = status.repeat_state.equals("off") ? this.loopOffGraphic : status.repeat_state.equals("track") ? this.loopSameGraphic : this.loopAllGraphic;
			this.loopX = mediaRightOffset;
			final boolean loopHovered = hovered && mouseX >= this.loopX && mouseX <= this.loopX + CONTROL_SIZE && mouseY <= this.getScaledY() + CONTROL_SIZE;
			if(loopHovered) {
				renderer._drawRoundedRectangle(this.loopX, smallerGraphicY, CONTROL_SIZE, CONTROL_SIZE, 3, true, false, 0, BACKGROUND_COLOR, 0);
			}
			renderer.drawGraphicRectangle(loopGraphic, this.loopX, smallerGraphicY + 1, CONTROL_SIZE, CONTROL_SIZE);
			mediaRightOffset += CONTROL_SIZE + 5;
			
			matrixStack.popPose();
		}
		
		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if(!this.isHovered(mouseX, mouseY)) {
				return false;
			}
			
			final SpotifyAPI api = plugin.getAPI();
			if(api == null) {
				return false;
			}
			
			final PlaybackState status = api.getCurrentStatus();
			
			if(status == null) {
				return false;
			}
			
			final PlaybackState.Item song = status.item;
			
			if(song == null) {
				return false;
			}
			
			//localize mouse pos
			mouseX -= SpotifyHudElement.this.getStartX();
			mouseY -= SpotifyHudElement.this.getStartY();
			mouseX -= this.getScaledX();
			mouseY -= this.getScaledY();
			mouseX = (int) (mouseX / this.getScale());
			mouseY = (int) (mouseY / this.getScale());
			
			//pause/play button
			if(mouseX >= this.playPauseX && mouseX <= this.playPauseX + PAUSE_PLAY_SIZE && mouseY <= this.getScaledY() + PAUSE_PLAY_SIZE) {
				api.submitTogglePlay();
				return true;
			}
			
			if(mouseY > this.getScaledY() + CONTROL_SIZE) {
				return false;
			}
			
			//back button
			if(mouseX >= this.backX && mouseX <= this.backX + CONTROL_SIZE) {
				api.submitPrevious();
				return true;
			}
			
			//next button
			if(mouseX >= this.nextX && mouseX <= this.nextX + CONTROL_SIZE && mouseY <= this.getScaledY() + CONTROL_SIZE) {
				api.submitNext();
				return true;
			}
			
			//shuffle button
			if(mouseX >= this.shuffleX && mouseX <= this.shuffleX + CONTROL_SIZE && mouseY <= this.getScaledY() + CONTROL_SIZE) {
				api.submitToggleShuffle();
				return true;
			}
			
			//loop button
			if(mouseX >= this.loopX && mouseX <= this.loopX + CONTROL_SIZE && mouseY <= this.getScaledY() + CONTROL_SIZE) {
				api.submitToggleRepeat();
				return true;
			}
			
			return false;
		}
		
		@Override
		public double getHeight() {
			return this.height;
		}
		
		public void setHeight(double v) {
			this.height = v;
		}
		
	}
	
}