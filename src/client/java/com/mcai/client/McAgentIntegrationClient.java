package com.mcai.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

public class McAgentIntegrationClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		try {
			FileBridge.init(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir());
		} catch (Exception ignored) {}
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> McaiCommands.register(dispatcher));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			try { KeyHoldQueue.tick(); } catch (Exception ignored) {}
			try { StateTracker.tick(mc); } catch (Exception ignored) {}
			try { FileBridge.tick(mc); } catch (Exception ignored) {}
		});

		try {
			ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) -> {
				try {
					String text = message.getString();
					HudBuffer.chat(text, text);
					HudBuffer.event("chat", text);
				} catch (Exception ignored) {}
			});
		} catch (Exception ignored) {}
		try {
			ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
				try {
					String text = message.getString();
					if (overlay) HudBuffer.setActionbar(text);
					else HudBuffer.chat(text, text);
					HudBuffer.event(overlay ? "actionbar" : "game", text);
				} catch (Exception ignored) {}
			});
		} catch (Exception ignored) {}
		try {
			ClientSendMessageEvents.CHAT.register(message -> {
				try { HudBuffer.event("sent_chat", message); } catch (Exception ignored) {}
			});
		} catch (Exception ignored) {}
		try {
			ClientSendMessageEvents.COMMAND.register(cmd -> {
				try { HudBuffer.event("sent_cmd", cmd); } catch (Exception ignored) {}
			});
		} catch (Exception ignored) {}
	}
}
