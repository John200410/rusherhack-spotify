package me.john200410.spotify.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.rusherhack.client.api.feature.hud.ResizeableHudElement;
import org.rusherhack.client.api.render.IRenderer2D;
import org.rusherhack.client.api.render.RenderContext;
import org.rusherhack.client.api.render.graphic.TextureGraphic;

import java.awt.*;

/**
 * @author John200410
 */
public class SpotifyHudElement extends ResizeableHudElement {
	
	private final ResourceLocation trackThumnailResourceLocation;
	private final DynamicTexture trackThumbnailTexture;
	
	public SpotifyHudElement() {
		super("Spotify");
		
		this.trackThumbnailTexture = new DynamicTexture(300, 300, false);
		this.trackThumnailResourceLocation = mc.getTextureManager().register("rusherhack-spotify-track-thumbnail/", this.trackThumbnailTexture);
	}
	
	@Override
	public void renderContent(RenderContext context, int mouseX, int mouseY) {
		final IRenderer2D renderer = this.getRenderer();
		
		//background
		renderer._drawRoundedRectangle(0, 0, this.getWidth(), this.getHeight(), 5, true, false, 0, this.getFillColor(), 0);
		
	}
	
	@Override
	public void postRender(RenderContext context, int mouseX, int mouseY) {
		//draw track thumbnail
		context.graphics().blit(this.trackThumnailResourceLocation, 5, 5, 0, 0, 70, 70, 300, 300);
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
		return 75;
	}
	
	private int getFillColor() {
		//TODO: return color based on song
		return Color.BLACK.getRGB();
	}
	
}