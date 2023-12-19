package me.john200410.spotify.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import me.john200410.spotify.SpotifyPlugin;
import me.john200410.spotify.Status;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.rusherhack.client.api.feature.hud.ResizeableHudElement;
import org.rusherhack.client.api.render.IRenderer2D;
import org.rusherhack.client.api.render.RenderContext;
import org.rusherhack.core.utils.ColorUtils;

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
	
	private ResourceLocation trackThumbnailResourceLocation;
	private DynamicTexture trackThumbnailTexture;
	private final SpotifyPlugin plugin;
	
	/**
	 * Variables
	 */
	private Status.Data.Song song = null;
	
	public SpotifyHudElement(SpotifyPlugin plugin) {
		super("Spotify");
		this.plugin = plugin;
	}
	
	@Override
	public void tick() {
		if(this.song == null || !this.song.equals(this.plugin.getSong())) {
			this.song = this.plugin.getSong();
			
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
		
		//background
		renderer._drawRoundedRectangle(0, 0, this.getWidth(), this.getHeight(), 5, true, false, 0, this.getFillColor(), 0);
	}
	
	@Override
	public void postRender(RenderContext context, int mouseX, int mouseY) {
		if(this.trackThumbnailResourceLocation == null) return;
		
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
	
}