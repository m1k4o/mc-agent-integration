package com.mcai.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

/**
 * Shared command executor used by the file bridge (terminal `mcai` CLI).
 * Parses a single line like "press forward 20" or "chat 20" and returns JSON.
 * Must be called on the client thread (file poll already runs there).
 */
public final class McaiExec {
	private McaiExec() {}

	public static JsonObject executeLine(String line) {
		Minecraft mc = Minecraft.getInstance();
		if (line == null || line.isBlank()) return McaiCommands.helpJson();
		String trimmed = line.trim();
		// strip leading slash + mcai prefix if present (" /mcai state " -> "state")
		String low = trimmed.toLowerCase();
		if (low.startsWith("/")) trimmed = trimmed.substring(1).trim();
		low = trimmed.toLowerCase();
		if (low.equals("mcai") || low.equals("mc-agent-integration")) return McaiCommands.helpJson();
		if (low.startsWith("mcai ")) trimmed = trimmed.substring(5).trim();
		else if (low.startsWith("mc-agent-integration ")) trimmed = trimmed.substring(21).trim();

		String[] parts = trimmed.split("\\s+");
		String cmd = parts[0].toLowerCase();
		try {
			return switch (cmd) {
				case "help", "?" -> McaiCommands.helpJson();
				case "state" -> Sensors.stateJson(mc);
				case "pos" -> {
					JsonObject o = Json.obj();
					if (mc.player == null) { o.addProperty("ok", false); o.addProperty("error", "no_player"); yield o; }
					o.addProperty("ok", true);
					o.addProperty("x", mc.player.getX());
					o.addProperty("y", mc.player.getY());
					o.addProperty("z", mc.player.getZ());
					o.addProperty("yaw", mc.player.getYRot());
					o.addProperty("pitch", mc.player.getXRot());
					yield o;
				}
				case "world" -> {
					JsonObject st = Sensors.stateJson(mc);
					JsonObject o = Json.obj();
					o.addProperty("ok", st.has("ok") && st.get("ok").getAsBoolean());
					if (st.has("world")) o.add("world", st.get("world"));
					if (st.has("env")) o.add("env", st.get("env"));
					if (st.has("pos")) o.add("pos", st.get("pos"));
					yield o;
				}
				case "chat" -> {
					int lim = parts.length > 1 ? clampInt(parts[1], 1, 200, 20) : 20;
					JsonObject o = Json.obj();
					o.addProperty("ok", true);
					o.add("messages", HudBuffer.chatJson(lim));
					yield o;
				}
				case "events" -> {
					int lim = parts.length > 1 ? clampInt(parts[1], 1, 200, 30) : 30;
					JsonObject o = Json.obj();
					o.addProperty("ok", true);
					o.add("events", HudBuffer.eventsJson(lim));
					yield o;
				}
				case "titles" -> {
					JsonObject o = HudBuffer.titlesJson();
					o.addProperty("ok", true);
					yield o;
				}
				case "sidebar" -> Sensors.sidebarJson(mc);
				case "bossbars" -> Sensors.bossbarJson(mc);
				case "inventory" -> Sensors.inventoryJson(mc);
				case "container" -> Sensors.containerJson(mc);
				case "held" -> {
					JsonObject o = Json.obj();
					if (mc.player == null) { o.addProperty("ok", false); o.addProperty("error", "no_player"); yield o; }
					o.addProperty("ok", true);
					o.add("mainhand", Sensors.stackJson(mc.player.getMainHandItem()));
					o.add("offhand", Sensors.stackJson(mc.player.getOffhandItem()));
					yield o;
				}
				case "entities" -> {
					int r = parts.length > 1 ? clampInt(parts[1], 1, 128, 32) : 32;
					int lim2 = parts.length > 2 ? clampInt(parts[2], 1, 128, 32) : 32;
					yield Sensors.entitiesJson(mc, r, lim2);
				}
				case "target" -> Sensors.targetJson(mc);
				case "scan" -> {
					int r = parts.length > 1 ? clampInt(parts[1], 1, 24, 8) : 8;
					yield Sensors.scanJson(mc, r);
				}
				case "effects" -> Sensors.effectsJson(mc);
				case "hud" -> {
					JsonObject o = Json.obj();
					o.addProperty("ok", true);
					o.add("messages", HudBuffer.chatJson(20));
					o.add("titles", HudBuffer.titlesJson());
					o.add("sidebar", Sensors.sidebarJson(mc));
					o.add("bossbars", Sensors.bossbarJson(mc));
					yield o;
				}
				case "keys" -> {
					JsonObject o = Json.obj();
					o.addProperty("ok", true);
					o.add("held", KeyHoldQueue.statusJson());
					yield o;
				}
				case "screen" -> Sensors.screenJson(mc);
				case "alive" -> {
					JsonObject o = Json.obj();
					o.addProperty("ok", true);
					o.addProperty("t", System.currentTimeMillis());
					o.addProperty("hasWorld", mc.player != null && mc.level != null);
					try { o.add("screen", Sensors.screenJson(mc)); } catch (Exception ignored) {}
					yield o;
				}
				case "recipe" -> {
					String want = parts.length > 1 ? rest(parts, 1) : "";
					int lim = 10;
					// trailing number = limit ("recipe stick 5")
					if (parts.length > 2) {
						try {
							lim = Integer.parseInt(parts[parts.length - 1]);
							want = join(parts, 1, parts.length - 1);
						} catch (NumberFormatException ignored) {}
					}
					yield Recipes.recipeJson(mc, want, lim);
				}
				case "say" -> {
					if (mc.player == null || mc.getConnection() == null) yield Json.err("no_world");
					String msg = rest(parts, 1);
					if (msg.isEmpty()) yield Json.err("usage: say <message>");
					mc.getConnection().sendChat(msg);
					JsonObject o = Json.ok();
					o.addProperty("action", "say");
					o.addProperty("message", msg);
					yield o;
				}
				case "run" -> {
					if (mc.player == null || mc.getConnection() == null) yield Json.err("no_world");
					String c = rest(parts, 1);
					if (c.isEmpty()) yield Json.err("usage: run <command>");
					String stripped = c.startsWith("/") ? c.substring(1) : c;
					mc.getConnection().sendCommand(stripped);
					JsonObject o = Json.ok();
					o.addProperty("action", "run");
					o.addProperty("command", stripped);
					yield o;
				}
				case "look" -> {
					if (mc.player == null) yield Json.err("no_player");
					if (parts.length < 3) yield Json.err("usage: look <yaw> <pitch>");
					float yaw = Float.parseFloat(parts[1]);
					float pitch = Float.parseFloat(parts[2]);
					mc.player.setYRot(yaw);
					mc.player.setXRot(pitch);
					JsonObject o = Json.ok();
					o.addProperty("action", "look");
					o.addProperty("yaw", yaw);
					o.addProperty("pitch", pitch);
					yield o;
				}
				case "lookat" -> {
					if (mc.player == null) yield Json.err("no_player");
					if (parts.length < 4) yield Json.err("usage: lookat <x> <y> <z>");
					float x = Float.parseFloat(parts[1]);
					float y = Float.parseFloat(parts[2]);
					float z = Float.parseFloat(parts[3]);
					var eye = mc.player.getEyePosition();
					double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
					double h = Math.sqrt(dx * dx + dz * dz);
					float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
					float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
					mc.player.setYRot(yaw);
					mc.player.setXRot(pitch);
					JsonObject o = Json.ok();
					o.addProperty("action", "lookat");
					o.addProperty("yaw", yaw);
					o.addProperty("pitch", pitch);
					yield o;
				}
				case "press" -> {
					if (parts.length < 2) yield Json.err("usage: press <key> [ticks]");
					String key = parts[1].toLowerCase();
					int ticks = parts.length > 2 ? clampInt(parts[2], 1, 1200, 1) : 1;
					String err = KeyHoldQueue.press(key, ticks);
					if (err != null) yield Json.err(err);
					JsonObject o = Json.ok();
					o.addProperty("action", "press");
					o.addProperty("key", key);
					o.addProperty("ticks", ticks);
					yield o;
				}
				case "release" -> {
					if (parts.length < 2) yield Json.err("usage: release <key>");
					String key = parts[1].toLowerCase();
					String err = KeyHoldQueue.release(key);
					if (err != null) yield Json.err(err);
					JsonObject o = Json.ok();
					o.addProperty("action", "release");
					o.addProperty("key", key);
					yield o;
				}
				case "stop" -> {
					KeyHoldQueue.releaseAll();
					JsonObject o = Json.ok();
					o.addProperty("action", "stop");
					yield o;
				}
				case "click" -> {
					if (parts.length < 2) yield Json.err("usage: click <slot> [button] [mode]");
					if (mc.player == null || mc.gameMode == null) yield Json.err("no_world");
					var menu = mc.player.containerMenu;
					if (menu == null) yield Json.err("no_menu");
					int slot = Integer.parseInt(parts[1]);
					int button = parts.length > 2 && isInt(parts[2]) ? Integer.parseInt(parts[2]) : 0;
					String mode = parts.length > 3 ? parts[3]
						: (parts.length > 2 && !isInt(parts[2]) ? parts[2] : "pickup");
					if (slot < 0 || slot >= menu.slots.size()) yield Json.err("bad_slot:" + slot + " size=" + menu.slots.size());
					var input = McaiCommands.parseClickInput(mode);
					mc.gameMode.handleContainerInput(menu.containerId, slot, button, input, mc.player);
					JsonObject o = Json.ok();
					o.addProperty("action", "click");
					o.addProperty("slot", slot);
					o.addProperty("button", button);
					o.addProperty("mode", input.name());
					try {
						o.add("after", Sensors.stackJson(menu.slots.get(slot).getItem()));
						o.add("carried", Sensors.stackJson(menu.getCarried()));
					} catch (Exception ignored) {}
					yield o;
				}
				case "select" -> {
					if (mc.player == null) yield Json.err("no_player");
					if (parts.length < 2) yield Json.err("usage: select <0-8>");
					int slot = Integer.parseInt(parts[1]);
					mc.player.getInventory().setSelectedSlot(slot);
					JsonObject o = Json.ok();
					o.addProperty("action", "select");
					o.addProperty("slot", slot);
					yield o;
				}
				case "drop" -> {
					if (mc.player == null) yield Json.err("no_player");
					boolean all = parts.length > 1 && parts[1].equalsIgnoreCase("all");
					var inv = mc.player.getInventory();
					int sel = inv.getSelectedSlot();
					var stack = inv.getItem(sel);
					if (stack.isEmpty()) {
						JsonObject o = Json.ok();
						o.addProperty("action", "drop");
						o.addProperty("note", "empty_hand");
						yield o;
					}
					int count = all ? stack.getCount() : 1;
					var removed = inv.removeItem(sel, count);
					if (!removed.isEmpty()) mc.player.drop(removed, false);
					JsonObject o = Json.ok();
					o.addProperty("action", "drop");
					o.addProperty("all", all);
					o.addProperty("count", count);
					yield o;
				}
				case "attack" -> {
					String err = KeyHoldQueue.press("attack", 1);
					if (err != null) yield Json.err(err);
					JsonObject o = Json.ok();
					o.addProperty("action", "attack");
					yield o;
				}
				case "use" -> {
					String err = KeyHoldQueue.press("use", 1);
					if (err != null) yield Json.err(err);
					JsonObject o = Json.ok();
					o.addProperty("action", "use");
					yield o;
				}
				case "respawn" -> {
					if (mc.player == null) yield Json.err("no_player");
					if (!mc.player.isAlive()) {
						mc.player.respawn();
						JsonObject o = Json.ok();
						o.addProperty("action", "respawn");
						yield o;
					}
					JsonObject o = Json.ok();
					o.addProperty("action", "respawn");
					o.addProperty("note", "already_alive");
					yield o;
				}
				case "clear" -> {
					HudBuffer.clear();
					JsonObject o = Json.ok();
					o.addProperty("action", "clear");
					yield o;
				}
				case "close" -> {
					// Must run on the client thread; file poll and chat both are.
					mc.setScreen(null);
					KeyHoldQueue.releaseAll();
					JsonObject o = Json.ok();
					o.addProperty("action", "close");
					yield o;
				}
				case "interact" -> {
					if (mc.player == null || mc.gameMode == null) yield Json.err("no_world");
					var hit = mc.hitResult;
					if (!(hit instanceof net.minecraft.world.phys.EntityHitResult ehr))
						yield Json.err("no_entity_target (crosshair is not on an entity; check target)");
					String handS = parts.length > 1 ? parts[1].toLowerCase() : "main";
					var hand = handS.startsWith("off")
						? net.minecraft.world.InteractionHand.OFF_HAND
						: net.minecraft.world.InteractionHand.MAIN_HAND;
					var result = mc.gameMode.interact(mc.player, ehr.getEntity(), ehr, hand);
					JsonObject o = Json.ok();
					o.addProperty("action", "interact");
					o.addProperty("hand", hand.name());
					o.addProperty("result", result.toString());
					try {
						var e = ehr.getEntity();
						o.addProperty("entity", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
						o.addProperty("name", e.getName().getString());
					} catch (Exception ignored) {}
					yield o;
				}
				case "place" -> {
					if (mc.player == null || mc.gameMode == null) yield Json.err("no_world");
					var hit = mc.hitResult;
					if (!(hit instanceof net.minecraft.world.phys.BlockHitResult bhr))
						yield Json.err("no_block_target (crosshair is not on a block; check target)");
					String handS = parts.length > 1 ? parts[1].toLowerCase() : "main";
					var hand = handS.startsWith("off")
						? net.minecraft.world.InteractionHand.OFF_HAND
						: net.minecraft.world.InteractionHand.MAIN_HAND;
					var result = mc.gameMode.useItemOn(mc.player, hand, bhr);
					JsonObject o = Json.ok();
					o.addProperty("action", "place");
					o.addProperty("hand", hand.name());
					o.addProperty("result", result.toString());
					yield o;
				}
				case "craft" -> {
					if (parts.length < 2) yield Json.err("usage: craft <recipeId> [shift]");
					int rid = Integer.parseInt(parts[1]);
					boolean shift = parts.length < 3 || !parts[2].equalsIgnoreCase("one");
					yield Recipes.craftJson(mc, rid, shift);
				}
				case "equip" -> {
					// Shift-click a menu slot: vanilla auto-equips armor onto the player.
					if (mc.player == null || mc.gameMode == null) yield Json.err("no_world");
					var menu = mc.player.containerMenu;
					if (menu == null) yield Json.err("no_menu");
					if (parts.length < 2) yield Json.err("usage: equip <slot>");
					int slot = Integer.parseInt(parts[1]);
					if (slot < 0 || slot >= menu.slots.size()) yield Json.err("bad_slot:" + slot + " size=" + menu.slots.size());
					mc.gameMode.handleContainerInput(menu.containerId, slot, 0,
						net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc.player);
					JsonObject o = Json.ok();
					o.addProperty("action", "equip");
					o.addProperty("slot", slot);
					try {
						o.add("after", Sensors.stackJson(menu.slots.get(slot).getItem()));
					} catch (Exception ignored) {}
					yield o;
				}
				default -> Json.err("unknown_command:" + cmd + " (try: help)");
			};
		} catch (NumberFormatException e) {
			return Json.err("bad_number:" + e.getMessage());
		} catch (Exception e) {
			return Json.err(e.toString());
		}
	}

	private static boolean isInt(String s) {
		try { Integer.parseInt(s); return true; }
		catch (NumberFormatException e) { return false; }
	}

	private static String rest(String[] parts, int from) {
		return join(parts, from, parts.length);
	}

	private static String join(String[] parts, int from, int to) {
		if (parts.length <= from || to <= from) return "";
		StringBuilder sb = new StringBuilder();
		for (int i = from; i < Math.min(to, parts.length); i++) {
			if (i > from) sb.append(' ');
			sb.append(parts[i]);
		}
		return sb.toString();
	}

	private static int clampInt(String s, int min, int max, int def) {
		try {
			int v = Integer.parseInt(s);
			return Math.max(min, Math.min(max, v));
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
