package com.mcai.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Key-simulation movement: hold vanilla KeyMappings for N ticks.
 * Keys: forward back left right jump sneak sprint attack use.
 * Driven from ClientTickEvents.END_CLIENT_TICK.
 */
public final class KeyHoldQueue {
	private record Hold(KeyMapping key, int ticksLeft, boolean toggle) {}
	private static final Map<String, Hold> HELD = new ConcurrentHashMap<>();

	private KeyHoldQueue() {}

	public static KeyMapping resolve(Minecraft mc, String name) {
		return switch (name) {
			case "forward" -> mc.options.keyUp;
			case "back" -> mc.options.keyDown;
			case "left" -> mc.options.keyLeft;
			case "right" -> mc.options.keyRight;
			case "jump" -> mc.options.keyJump;
			case "sneak" -> mc.options.keyShift;
			case "sprint" -> mc.options.keySprint;
			case "attack" -> mc.options.keyAttack;
			case "use" -> mc.options.keyUse;
			default -> null;
		};
	}

	public static String press(String name, int ticks) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options == null) return "no_options";
		KeyMapping key = resolve(mc, name);
		if (key == null) return "unknown_key:" + name;
		int t = ticks <= 0 ? 1 : Math.min(ticks, 1200);
		key.setDown(true);
		HELD.put(name, new Hold(key, t, false));
		return null;
	}

	public static String release(String name) {
		Hold h = HELD.remove(name);
		if (h != null) h.key().setDown(false);
		else {
			// best effort: resolve and release anyway
			Minecraft mc = Minecraft.getInstance();
			if (mc.options != null) {
				KeyMapping key = resolve(mc, name);
				if (key != null) key.setDown(false);
				else return "unknown_key:" + name;
			}
		}
		return null;
	}

	public static void releaseAll() {
		for (Map.Entry<String, Hold> e : HELD.entrySet()) {
			try { e.getValue().key().setDown(false); } catch (Exception ignored) {}
		}
		HELD.clear();
	}

	/** Called every client tick end. */
	public static void tick() {
		if (HELD.isEmpty()) return;
		for (Map.Entry<String, Hold> e : Map.copyOf(HELD).entrySet()) {
			Hold h = e.getValue();
			if (h.toggle()) continue;
			int left = h.ticksLeft() - 1;
			if (left <= 0) {
				HELD.remove(e.getKey());
				try { h.key().setDown(false); } catch (Exception ignored) {}
			} else {
				HELD.put(e.getKey(), new Hold(h.key(), left, false));
				// re-assert held (vanilla may consume attack/use clicks)
				try { h.key().setDown(true); } catch (Exception ignored) {}
			}
		}
	}

	public static com.google.gson.JsonArray statusJson() {
		com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
		for (Map.Entry<String, Hold> e : HELD.entrySet()) {
			com.google.gson.JsonObject o = new com.google.gson.JsonObject();
			o.addProperty("key", e.getKey());
			o.addProperty("ticksLeft", e.getValue().ticksLeft());
			arr.add(o);
		}
		return arr;
	}
}
