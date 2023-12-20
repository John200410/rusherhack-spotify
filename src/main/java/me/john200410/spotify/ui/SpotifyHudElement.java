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
import org.rusherhack.client.api.utils.ChatUtils;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.utils.ColorUtils;
import org.rusherhack.core.utils.Timer;

import java.awt.*;
import java.io.IOException;
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
	private final BooleanSetting authenticateButton;
	
	/**
	 * Graphics
	 */
	private final VectorGraphic playGraphic;
	private final VectorGraphic pauseGraphic;
	
	/**
	 * Variables
	 */
	private ResourceLocation trackThumbnailResourceLocation;
	private DynamicTexture trackThumbnailTexture;
	private final SpotifyPlugin plugin;
	private final Timer updateTimer = new Timer();
	private Status.Data.Song song = null;
	
	public SpotifyHudElement(SpotifyPlugin plugin) throws IOException {
		super("Spotify");
		this.plugin = plugin;
		
		//load graphics
		this.playGraphic = new VectorGraphic("spotify/graphics/play.svg", 24, 24);
		this.pauseGraphic = new VectorGraphic("spotify/graphics/pause.svg", 24, 24);
		
		//dummy setting whos only purpose is to be clicked to open the web browser
		this.authenticateButton = new BooleanSetting("Authenticate", true) {
			@Override
			public Boolean getValue() {
				return true;
			}
		}.setVisibility(() -> !this.isAuthenticated())
		 .onChange((b) -> {
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
			return;
		}
		
		ChatUtils.print(status.data.song.name);
		
		if(this.song == null || !this.song.equals(status.data.song)) {
			this.song = status.data.song;
			
			//update texture
			Status.Data.Song.Thumbnail thumbnail = null;
			for(Status.Data.Song.Thumbnail t : this.song.thumbnails) {
				if(t.width < 360 && t.height < 360) {
					thumbnail = t;
					break;
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
				
				final HttpResponse<byte[]> bytesResponse = SpotifyPlugin.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
				final byte[] imageBytes = bytesResponse.body();
				
				this.trackThumbnailTexture = new DynamicTexture(NativeImage.read(imageBytes));
				this.trackThumbnailResourceLocation = mc.getTextureManager().register("rusherhack-spotify-track-thumbnail/", this.trackThumbnailTexture);
			} catch(URISyntaxException | IOException | InterruptedException e) {
				this.plugin.getLogger().error(e.getMessage());
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void renderContent(RenderContext context, int mouseX, int mouseY) {
		final IRenderer2D renderer = this.getRenderer();
		final IFontRenderer fr = this.getFontRenderer();
		
		//background
		renderer._drawRoundedRectangle(0, 0, this.getWidth(), this.getHeight(), 5, true, false, 0, this.getFillColor(), 0);
		
		if(!this.isAuthenticated()) {
			fr.drawString("Not authenticated with spotify!", 5, 10, -1);
			fr.drawString("Click the \"Authenticate\" button", 5, 30, -1);
			fr.drawString("in the settings to authenticate.", 5, 40, -1);
			return;
		}
	}
	
	@Override
	public void postRender(RenderContext context, int mouseX, int mouseY) {
		if(this.trackThumbnailResourceLocation == null || !this.isAuthenticated()) return;
		
		//draw track thumbnail
		final PoseStack matrixStack = context.pose();
		final GuiGraphics graphics = context.graphics();
		
		//matrixStack.pushPose();
		//matrixStack.translate(0, 0, -250);
		graphics.blit(this.trackThumbnailResourceLocation, 5, 5, 0, 0, 70, 70, 70, 70);
		//matrixStack.popPose();
	}
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return super.mouseClicked(mouseX, mouseY, button);
	}
	
	@Override
	public double getWidth() {
		return 200;
	}
	
	@Override
	public double getHeight() {
		return 80;
	}
	
	private int getFillColor() {
		//TODO: return color based on song
		return ColorUtils.transparency(Color.BLACK.getRGB(), 0.5f);
	}
	
	private boolean isAuthenticated() {
		return this.plugin.getAPI() != null;
	}
	
}