package com.mcai.client.mixin;

import com.mcai.client.HudBuffer;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Capture every chat HUD line from memory so the agent never screenshots text. */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {
	@Inject(method = "addClientSystemMessage", at = @At("HEAD"))
	private void mcai$onClientSys(Component message, CallbackInfo ci) {
		try {
			String text = message.getString();
			HudBuffer.chat(text, text);
		} catch (Exception ignored) {}
	}

	@Inject(method = "addServerSystemMessage", at = @At("HEAD"))
	private void mcai$onServerSys(Component message, CallbackInfo ci) {
		try {
			String text = message.getString();
			HudBuffer.chat(text, text);
		} catch (Exception ignored) {}
	}

	@Inject(method = "addPlayerMessage", at = @At("HEAD"))
	private void mcai$onPlayerMsg(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
		try {
			String text = message.getString();
			HudBuffer.chat(text, text);
		} catch (Exception ignored) {}
	}
}
