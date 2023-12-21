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
import org.rusherhack.client.api.ui.ScaledElementBase;
import org.rusherhack.client.api.utils.ChatUtils;
import org.rusherhack.core.event.stage.Stage;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.interfaces.IClickable;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.utils.ColorUtils;

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
	
	/**
	 * Settings
	 */
	private final BooleanSetting authenticateButton = new BooleanSetting("Authenticate", true)
			.setVisibility(() -> !this.isConnected());
	
	/**
	 * Media Controller
	 */
	private final MediaControllerHandler mediaController;
	
	/**
	 * Variables
	 */
	private final ResourceLocation trackThumbnailResourceLocation;
	private final DynamicTexture trackThumbnailTexture;
	private final SpotifyPlugin plugin;
	private PlaybackState.Item song = null;
	private boolean consumedButtonClick = false;
	
	public SpotifyHudElement(SpotifyPlugin plugin) throws IOException {
		super("Spotify");
		this.plugin = plugin;
		
		this.mediaController = new MediaControllerHandler();
		
		this.trackThumbnailTexture = new DynamicTexture(640, 640, false);
		this.trackThumbnailTexture.setFilter(true, true);
		this.trackThumbnailResourceLocation = mc.getTextureManager().register("rusherhack-spotify-track-thumbnail/", this.trackThumbnailTexture);
		
		//dummy setting whos only purpose is to be clicked to open the web browser
		this.authenticateButton.onChange((b) -> {
			try {
				Desktop.getDesktop().browse(new URI("http://localhost:4000/"));
			} catch(IOException | URISyntaxException e) {
				this.plugin.getLogger().error(e.getMessage());
				e.printStackTrace();
			}
		});
		
		this.registerSettings(authenticateButton);
		
		//dont ask
		//this.setupDummyModuleBecauseImFuckingStupidAndForgotToRegisterHudElementsIntoTheEventBus();
	}
	
	@Override
	public void tick() {
		
		if(!this.isConnected()) {
			return;
		}
		
		final SpotifyAPI api = this.plugin.getAPI();
		api.updateStatus(true);
		
		final PlaybackState status = api.getCurrentStatus();
		
		if(status == null) {
			//ChatUtils.print("Status null?");
			return;
		}
		
		//ChatUtils.print(status.data.song.name);
		
		if(this.song == null || !this.song.equals(status.item)) {
			this.song = status.item;
			
			//update texture
			if(this.song.artists.length > 0) {
				
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
		
		//background
		renderer._drawRoundedRectangle(0, 0, this.getWidth(), this.getHeight(), 5, true, false, 0, this.getFillColor(), 0);
		
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
		
		final double contentWidth = this.getWidth() - 75 - 5;
		final double leftOffset = 75;
		
		/////////////////////////////////////////////////////////////////////
		//top
		/////////////////////////////////////////////////////////////////////
		double topOffset = 5;
		
		//song details
		fr.drawString(song.name, leftOffset, topOffset, -1);
		topOffset += fr.getFontHeight() + 1;
		
		final String[] artists = new String[song.artists.length];
		for(int i = 0; i < song.artists.length; i++) {
			artists[i] = song.artists[i].name;
		}
		fr.drawString("by " + Strings.join(artists, ", "), leftOffset, topOffset, Color.LIGHT_GRAY.getRGB());
		topOffset += fr.getFontHeight() + 1;
		
		fr.drawString("on " + song.album.name, leftOffset, topOffset, Color.LIGHT_GRAY.getRGB());
		topOffset += fr.getFontHeight() + 1;
		
		/////////////////////////////////////////////////////////////////////
		
		/////////////////////////////////////////////////////////////////////
		//bottom
		/////////////////////////////////////////////////////////////////////
		double bottomOffset = this.getHeight() - 5;
		
		//progress bar
		final double progressBarWidth = contentWidth;
		final double progressBarHeight = 2;
		final double progress = (double) status.progress_ms / (double) song.duration_ms;
		
		renderer._drawRoundedRectangle(leftOffset, bottomOffset - progressBarHeight, progressBarWidth, progressBarHeight, 1, true, false, 0, Color.GRAY.getRGB(), 0);
		renderer._drawRoundedRectangle(leftOffset, bottomOffset - progressBarHeight, progressBarWidth * progress, progressBarHeight, 1, true, false, 0, Color.WHITE.getRGB(), 0);
		bottomOffset -= progressBarHeight + 1;
		
		//duration
		final String current = String.format("%d:%02d", status.progress_ms / 60000, status.progress_ms / 1000 % 60);
		final String length = String.format("%d:%02d", song.duration_ms / 60000, song.duration_ms / 1000 % 60);
		final double durationHeight = fr.getFontHeight() + 1;
		fr.drawString(current, leftOffset, bottomOffset - durationHeight, Color.LIGHT_GRAY.getRGB());
		fr.drawString(length, leftOffset + contentWidth - fr.getStringWidth(length), bottomOffset - durationHeight, Color.LIGHT_GRAY.getRGB());
		bottomOffset -= durationHeight - 1;
		
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
		if(event.getAction() != GLFW.GLFW_PRESS) {
			return;
		}
		if(event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return;
		}
		if(!(mc.screen instanceof ChatScreen)) {
			return;
		}
		
		double mouseX = event.getMouseX();
		double mouseY = event.getMouseY();
		
		if(!isHovered(mouseX, mouseY)) {
			return;
		}
		
		this.consumedButtonClick = true;
		mouseClicked(mouseX, mouseY, event.getButton());
	}
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		
		if(this.mediaController.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		
		if(this.consumedButtonClick) {
			this.consumedButtonClick = false;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
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
		//TODO: return color based on song
		return ColorUtils.transparency(Color.BLACK.getRGB(), 0.5f);
	}
	
	private boolean isConnected() {
		return this.plugin.getAPI().isConnected();
	}
	
	private boolean isAvailable() {
		return this.isConnected() && this.plugin.getAPI().isPlaybackAvailable();
	}
	
	class MediaControllerHandler extends ScaledElementBase implements IClickable {
		
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
		
		public void render(IRenderer2D renderer, RenderContext context, int mouseX, int mouseY, PlaybackState status) {
			final PoseStack matrixStack = context.pose();
			matrixStack.pushPose();
			matrixStack.translate(this.getX(), this.getY(), 0);
			
			final double width = this.getWidth();
			final double mediaControlsCenter = this.getHeight() / 2f;
			
			//play/pause
			final VectorGraphic playPauseGraphic = status.is_playing ? this.pauseGraphic : this.playGraphic;
			this.playPauseX = width / 2f - PAUSE_PLAY_SIZE / 2f;
			renderer.drawGraphicRectangle(playPauseGraphic, this.playPauseX, mediaControlsCenter - PAUSE_PLAY_SIZE / 2f, PAUSE_PLAY_SIZE, PAUSE_PLAY_SIZE);
			
			final double smallerGraphicY = mediaControlsCenter - CONTROL_SIZE / 2f;
			double mediaLeftOffset = width / 2f - CONTROL_SIZE / 2f - 5;
			double mediaRightOffset = width / 2f + CONTROL_SIZE / 2f + 5;
			
			//back
			this.backX = mediaLeftOffset - CONTROL_SIZE;
			renderer.drawGraphicRectangle(this.backGraphic, this.backX, smallerGraphicY, CONTROL_SIZE, CONTROL_SIZE);
			mediaLeftOffset -= CONTROL_SIZE + 5;
			
			//next
			this.nextX = mediaRightOffset;
			renderer.drawGraphicRectangle(this.nextGraphic, this.nextX, smallerGraphicY, CONTROL_SIZE, CONTROL_SIZE);
			mediaRightOffset += CONTROL_SIZE + 5;
			
			//shuffle
			final VectorGraphic shuffleGraphic = status.shuffle_state ? this.shuffleOnGraphic : this.shuffleOffGraphic;
			this.shuffleX = mediaLeftOffset - CONTROL_SIZE;
			renderer.drawGraphicRectangle(shuffleGraphic, this.shuffleX, smallerGraphicY, CONTROL_SIZE, CONTROL_SIZE);
			mediaLeftOffset -= CONTROL_SIZE + 5;
			
			//loop
			final VectorGraphic loopGraphic = status.repeat_state.equals("off") ? this.loopOffGraphic : status.repeat_state.equals("track") ? this.loopSameGraphic : this.loopAllGraphic;
			this.loopX = mediaRightOffset;
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
			mouseX -= this.getX();
			mouseY -= this.getY();
			
			ChatUtils.print("mouseX: " + mouseX + ", mouseY: " + mouseY);
			
			//pause/play button
			if(mouseX >= this.playPauseX && mouseX <= this.playPauseX + PAUSE_PLAY_SIZE && mouseY <= this.getY() + PAUSE_PLAY_SIZE) {
				ChatUtils.print("play/pause");
				api.submitTogglePlay();
				return true;
			}
			
			if(mouseY > this.getY() + CONTROL_SIZE) {
				return false;
			}
			
			//back button
			if(mouseX >= this.backX && mouseX <= this.backX + CONTROL_SIZE) {
				ChatUtils.print("back");
				api.submitPrevious();
				return true;
			}
			
			//next button
			if(mouseX >= this.nextX && mouseX <= this.nextX + CONTROL_SIZE && mouseY <= this.getY() + CONTROL_SIZE) {
				ChatUtils.print("next");
				api.submitNext();
				return true;
			}
			
			//shuffle button
			if(mouseX >= this.shuffleX && mouseX <= this.shuffleX + CONTROL_SIZE && mouseY <= this.getY() + CONTROL_SIZE) {
				ChatUtils.print("shuffle");
				api.submitToggleShuffle();
				return true;
			}
			
			//loop button
			if(mouseX >= this.loopX && mouseX <= this.loopX + CONTROL_SIZE && mouseY <= this.getY() + CONTROL_SIZE) {
				ChatUtils.print("loop");
				api.submitToggleRepeat();
				return true;
			}
			
			return false;
		}
		
		@Override
		public boolean isHovered(double mouseX, double mouseY) {
			mouseX -= SpotifyHudElement.this.getStartX();
			mouseY -= SpotifyHudElement.this.getStartY();
			
			return mouseX >= this.getX() && mouseX <= this.getX() + this.getScaledWidth() && mouseY >= this.getY() && mouseY <= this.getY() + this.getScaledHeight();
		}
		
		@Override
		public double getWidth() {
			return SpotifyHudElement.this.getWidth() - 75 - 5;
		}
		
		@Override
		public double getHeight() {
			return this.height;
		}
		
		public void setHeight(double v) {
			this.height = v;
		}
		
		@Override
		public double getScale() {
			return SpotifyHudElement.this.getScale();
		}
	}
	
}