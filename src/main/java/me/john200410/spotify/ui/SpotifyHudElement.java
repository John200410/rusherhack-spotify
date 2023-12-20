package me.john200410.spotify.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import me.john200410.spotify.SpotifyPlugin;
import me.john200410.spotify.http.SpotifyAPI;
import me.john200410.spotify.http.Status;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.rusherhack.client.api.feature.hud.ResizeableHudElement;
import org.rusherhack.client.api.render.IRenderer2D;
import org.rusherhack.client.api.render.RenderContext;
import org.rusherhack.client.api.render.font.IFontRenderer;
import org.rusherhack.client.api.render.graphic.VectorGraphic;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.utils.ColorUtils;
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
	
	/**
	 * Settings
	 */
	private final BooleanSetting authenticateButton = new BooleanSetting("Authenticate", true)
			.setVisibility(() -> !this.isAuthenticated());
	
	/**
	 * Graphics
	 */
	private final VectorGraphic playGraphic, pauseGraphic;
	private final VectorGraphic backGraphic, nextGraphic;
	private final VectorGraphic shuffleOnGraphic, shuffleOffGraphic;
	private final VectorGraphic loopOffGraphic, loopAllGraphic, loopSameGraphic;
	
	/**
	 * Variables
	 */
	private final ResourceLocation trackThumbnailResourceLocation;
	private final DynamicTexture trackThumbnailTexture;
	private final SpotifyPlugin plugin;
	private final Timer updateTimer = new Timer();
	private Status.Data.Song song = null;
	
	public SpotifyHudElement(SpotifyPlugin plugin) throws IOException {
		super("Spotify");
		this.plugin = plugin;
		
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
		
		this.trackThumbnailTexture = new DynamicTexture(640, 640, false);
		this.trackThumbnailTexture.setFilter(true, true);
		this.trackThumbnailResourceLocation = mc.getTextureManager().register("rusherhack-spotify-track-thumbnail/", this.trackThumbnailTexture);
		
		//dummy setting whos only purpose is to be clicked to open the web browser
		this.authenticateButton.onChange((b) -> {
			this.authenticateButton.setValue(true);
			try {
				Desktop.getDesktop().browse(new URI("http://localhost:4000/"));
			} catch(IOException | URISyntaxException e) {
				this.plugin.getLogger().error(e.getMessage());
				e.printStackTrace();
			}
		});
		
		this.registerSettings(authenticateButton);
	}
	
	@Override
	public void tick() {
		
		if(!this.isAuthenticated()) {
			return;
		}
		
		final SpotifyAPI api = this.plugin.getAPI();
		
		if(this.updateTimer.passed(5000)) {
			api.updateStatus();
			this.updateTimer.reset();
		}
		
		final Status status = api.getCurrentStatus();
		if(status == null) {
			//ChatUtils.print("Status null?");
			return;
		}
		
		//ChatUtils.print(status.data.song.name);
		
		if(this.song == null || !this.song.equals(status.data.song)) {
			this.song = status.data.song;
			
			//update texture
			if(this.song.thumbnails.length > 0) {
				
				//highest resolution thumbnail
				Status.Data.Song.Thumbnail thumbnail = null;
				for(Status.Data.Song.Thumbnail t : this.song.thumbnails) {
					
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
				
				try {
					final HttpRequest request = HttpRequest.newBuilder(new URI(thumbnail.url))
														   .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.102 Safari/537.36")
														   .build();
					
					final HttpResponse<InputStream> response = SpotifyPlugin.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
					
					final NativeImage nativeImage = NativeImage.read(response.body());
					this.trackThumbnailTexture.setPixels(nativeImage);
					this.trackThumbnailTexture.upload();
					this.trackThumbnailTexture.setFilter(true, true);
				} catch(URISyntaxException | IOException | InterruptedException e) {
					this.plugin.getLogger().error(e.getMessage());
					e.printStackTrace();
				}
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
		
		if(api == null) {
			fr.drawString("Not authenticated with spotify!", 5, 10, -1);
			fr.drawString("Click the \"Authenticate\" button", 5, 30, -1);
			fr.drawString("in the settings to authenticate.", 5, 40, -1);
			return;
		}
		
		final Status status = api.getCurrentStatus();
		
		if(status == null) {
			fr.drawString("Failed to load status", 5, 10, -1);
			return;
		}
		
		final Status.Data data = status.data;
		final Status.Data.Song song = data.song;
		
		if(song == null) {
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
		
		fr.drawString("by Artist, Artist2", leftOffset, topOffset, Color.LIGHT_GRAY.getRGB());
		topOffset += fr.getFontHeight() + 1;
		
		fr.drawString("on " + song.album, leftOffset, topOffset, Color.LIGHT_GRAY.getRGB());
		topOffset += fr.getFontHeight() + 1;
		
		/////////////////////////////////////////////////////////////////////
		
		/////////////////////////////////////////////////////////////////////
		//bottom
		/////////////////////////////////////////////////////////////////////
		double bottomOffset = this.getHeight() - 5;
		
		//progress bar
		final double progressBarWidth = contentWidth;
		final double progressBarHeight = 2;
		final double progress = (double) data.progress.current / (double) data.progress.total;
		
		renderer._drawRoundedRectangle(leftOffset, bottomOffset - progressBarHeight, progressBarWidth, progressBarHeight, 1, true, false, 0, Color.GRAY.getRGB(), 0);
		renderer._drawRoundedRectangle(leftOffset, bottomOffset - progressBarHeight, progressBarWidth * progress, progressBarHeight, 1, true, false, 0, Color.WHITE.getRGB(), 0);
		bottomOffset -= progressBarHeight + 1;
		
		//duration
		final String current = String.format("%d:%02d", data.progress.current / 60000, data.progress.current / 1000 % 60);
		final String length = String.format("%d:%02d", data.progress.total / 60000, data.progress.total / 1000 % 60);
		final double durationHeight = fr.getFontHeight() + 1;
		fr.drawString(current, leftOffset, bottomOffset - durationHeight, Color.LIGHT_GRAY.getRGB());
		fr.drawString(length, leftOffset + contentWidth - fr.getStringWidth(length), bottomOffset - durationHeight, Color.LIGHT_GRAY.getRGB());
		bottomOffset -= durationHeight - 1;
		
		//media controls
		final double mediaControlsHeight = bottomOffset - topOffset;
		final double mediaControlsCenter = bottomOffset - mediaControlsHeight / 2f;
		
		//play/pause
		final VectorGraphic playPauseGraphic = data.is_playing ? this.pauseGraphic : this.playGraphic;
		final double playPauseSize = 20;
		renderer.drawGraphicRectangle(playPauseGraphic, leftOffset + contentWidth / 2f - playPauseSize / 2f, mediaControlsCenter - playPauseSize / 2f, playPauseSize, playPauseSize);
		
		final double graphicSize = 16;
		final double smallerGraphicsY = mediaControlsCenter - graphicSize / 2f;
		double mediaLeftOffset = leftOffset + contentWidth / 2f - graphicSize / 2f - 5;
		double mediaRightOffset = leftOffset + contentWidth / 2f + graphicSize / 2f + 5;
		
		//back
		renderer.drawGraphicRectangle(this.backGraphic, mediaLeftOffset - graphicSize, smallerGraphicsY, graphicSize, graphicSize);
		mediaLeftOffset -= graphicSize + 5;
		//next
		renderer.drawGraphicRectangle(this.nextGraphic, mediaRightOffset, smallerGraphicsY, graphicSize, graphicSize);
		mediaRightOffset += graphicSize + 5;
		
		//shuffle
		final VectorGraphic shuffleGraphic = data.shuffling ? this.shuffleOnGraphic : this.shuffleOffGraphic;
		renderer.drawGraphicRectangle(shuffleGraphic, mediaLeftOffset - graphicSize, smallerGraphicsY, graphicSize, graphicSize);
		mediaLeftOffset -= graphicSize + 5;
		
		//loop
		final VectorGraphic loopGraphic = this.loopOffGraphic;
		renderer.drawGraphicRectangle(loopGraphic, mediaRightOffset, smallerGraphicsY + 1, graphicSize, graphicSize);
		mediaRightOffset += graphicSize + 5;
		
		/////////////////////////////////////////////////////////////////////
	}
	
	@Override
	public void postRender(RenderContext context, int mouseX, int mouseY) {
		if(this.trackThumbnailTexture.getPixels() == null || !this.isAuthenticated()) return;
		
		//draw track thumbnail
		final PoseStack matrixStack = context.pose();
		final GuiGraphics graphics = context.graphics();
		
		//matrixStack.pushPose();
		//matrixStack.translate(0, 0, -250);
		graphics.blit(this.trackThumbnailResourceLocation, 5, 5, 0, 0, 65, 65, 65, 65);
		//matrixStack.popPose();
	}
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
	
	private boolean isAuthenticated() {
		return this.plugin.getAPI() != null;
	}
	
}