package com.mcai.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Edge-triggered event hooks the agent polls via `events`: death, respawn,
 * significant hurt (health drops), and container open/close. Chat/titles/
 * actionbar/send are captured separately by mixins + Fabric message events.
 * Called every client tick end; all state transitions are debounced by
 * comparing against the previous tick.
 */
public final class StateTracker {
	private static boolean lastAlive = true;
	private static float lastHp = -1f;
	private static String lastScreen = "none";
	private static String lastContainerTitle = "";

	private StateTracker() {}

	public static void tick(Minecraft mc) {
		try {
			if (mc.player == null || mc.level == null) {
				lastHp = -1f;
				lastAlive = true;
				if (!lastScreen.equals("none")) {
					lastScreen = "none";
					lastContainerTitle = "";
				}
				return;
			}
			var p = mc.player;

			// death / respawn edges
			boolean alive = p.isAlive();
			if (lastAlive && !alive) {
				HudBuffer.event("death", String.format("died at %.0f,%.0f,%.0f hp=0", p.getX(), p.getY(), p.getZ()));
			} else if (!lastAlive && alive) {
				HudBuffer.event("respawn", "player alive again");
			}
			lastAlive = alive;

			// hurt: report health drops of >= 1.0 (half a heart), with before/after
			try {
				float hp = p.getHealth();
				if (lastHp >= 0 && hp < lastHp - 0.001f && (lastHp - hp) >= 1.0f) {
					HudBuffer.event("hurt", String.format("hp %.0f->%.0f (-%.0f)", lastHp, hp, lastHp - hp));
				}
				lastHp = hp;
			} catch (Exception ignored) {}

			// container open/close edges
			String screenName = mc.screen == null ? "none" : mc.screen.getClass().getSimpleName();
			boolean isContainer = mc.screen instanceof AbstractContainerScreen;
			String title = "";
			if (isContainer) {
				try { title = mc.screen.getTitle().getString(); } catch (Exception ignored) {}
			}
			boolean wasContainer = lastScreen.startsWith("container:");
			if (isContainer && (!wasContainer || !lastContainerTitle.equals(title))) {
				HudBuffer.event("container_open", title.isEmpty() ? screenName : title);
			} else if (!isContainer && wasContainer) {
				HudBuffer.event("container_close", lastContainerTitle.isEmpty() ? lastScreen : lastContainerTitle);
			} else if (!screenName.equals(lastScreen.replace("container:", "")) && !isContainer && !wasContainer) {
				// screen changed between non-container screens (e.g. death/pause/chat); record cheaply
				if (!screenName.equals("none") && !lastScreen.equals("none")
					&& !screenName.equals("ChatScreen") && !lastScreen.equals("ChatScreen")) {
					HudBuffer.event("screen", screenName);
				}
			}
			lastScreen = isContainer ? "container:" + screenName : screenName;
			lastContainerTitle = title;
		} catch (Exception ignored) {}
	}

	public static void reset() {
		lastAlive = true;
		lastHp = -1f;
		lastScreen = "none";
		lastContainerTitle = "";
	}
}
