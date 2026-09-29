package com.mcai.client.mixin;

import com.mcai.client.HudBuffer;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Capture titles/subtitles/actionbar from memory. Method names follow official Mojang mappings. */
@Mixin(Gui.class)
public class GuiTitleMixin {
	@Inject(method = "setTitle", at = @At("HEAD"))
	private void mcai$onTitle(Component title, CallbackInfo ci) {
		try {
			HudBuffer.setTitle(title == null ? "" : title.getString(), null);
			HudBuffer.event("title", title == null ? "" : title.getString());
		} catch (Exception ignored) {}
	}

	@Inject(method = "setSubtitle", at = @At("HEAD"))
	private void mcai$onSubtitle(Component subtitle, CallbackInfo ci) {
		try {
			HudBuffer.setTitle(null, subtitle == null ? "" : subtitle.getString());
			HudBuffer.event("subtitle", subtitle == null ? "" : subtitle.getString());
		} catch (Exception ignored) {}
	}

	@Inject(method = "setOverlayMessage", at = @At("HEAD"))
	private void mcai$onActionbar(Component text, boolean animate, CallbackInfo ci) {
		try {
			HudBuffer.setActionbar(text == null ? "" : text.getString());
			HudBuffer.event("actionbar", text == null ? "" : text.getString());
		} catch (Exception ignored) {}
	}
}
