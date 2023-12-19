package me.john200410.spotify.ui;

import org.rusherhack.client.api.feature.hud.ResizeableHudElement;
import org.rusherhack.client.api.render.RenderContext;
import org.rusherhack.client.api.render.graphic.TextureGraphic;

/**
 * @author John200410
 */
public class SpotifyHudElement extends ResizeableHudElement {
	
	private TextureGraphic graphic = null;
	
	public SpotifyHudElement() {
		super("Spotify");
		
		//try loading graphic
		/*
		try {
			this.graphic = new TextureGraphic("exampleplugin/graphics/rh_head.png", 235, 234);
		} catch (Throwable t) {
			t.printStackTrace();
		}
		 */
	}
	
	@Override
	public void renderContent(RenderContext context, int mouseX, int mouseY) {
		//positions are relative to the top left corner of the hud element, so start drawing stuff from 0,0
		
		if (this.graphic != null) {
			this.getRenderer().drawGraphicRectangle(this.graphic, 0, 0, this.getWidth(), this.getHeight());
		}
	}
	
	@Override
	public double getWidth() {
		return 200;
	}
	
	@Override
	public double getHeight() {
		return 200;
	}
}
