package com.mcai.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import net.minecraft.client.Minecraft;

/**
 * File bridge between the game and the terminal `mcai` CLI.
 * Dir: <gameDir>/mcai/
 *   state.json, hud.json, inventory.json, container.json — snapshots every few ticks
 *   alive.json — heartbeat {t, ok, hasWorld}
 *   cmd.json — CLI request {id, line} (written atomically via cmd.tmp rename)
 *   resp.json — mod response {id, result:{...}} (written atomically via resp.tmp rename)
 */
public final class FileBridge {
	private static Path dir;
	private static Path stateFile, hudFile, invFile, contFile, aliveFile, screenFile, cmdFile, respFile;
	private static int tickCount = 0;
	private static String lastCmdId = "";

	private FileBridge() {}

	public static void init(Path gameDir) {
		try {
			dir = gameDir.resolve("mcai");
			Files.createDirectories(dir);
			stateFile = dir.resolve("state.json");
			hudFile = dir.resolve("hud.json");
			invFile = dir.resolve("inventory.json");
			contFile = dir.resolve("container.json");
			aliveFile = dir.resolve("alive.json");
			screenFile = dir.resolve("screen.json");
			cmdFile = dir.resolve("cmd.json");
			respFile = dir.resolve("resp.json");
		} catch (Exception e) {
			com.mcai.McAgentIntegration.LOGGER.warn("[mcai] file bridge init failed: {}", e.toString());
		}
	}

	public static Path dir() { return dir; }

	public static void tick(Minecraft mc) {
		if (dir == null) return;
		tickCount++;
		try {
			// heartbeat every tick (cheap, tiny)
			writeJson(aliveFile, aliveJson(mc));
		} catch (Exception ignored) {}
		if (tickCount % 5 != 0) {
			// still poll inbox every tick
			pollInbox(mc);
			return;
		}
		try {
			if (mc.player != null && mc.level != null) {
				writeJson(stateFile, Sensors.stateJson(mc));
				try {
					JsonObject hud = Json.obj();
					hud.addProperty("ok", true);
					hud.add("messages", HudBuffer.chatJson(20));
					hud.add("titles", HudBuffer.titlesJson());
					hud.add("sidebar", Sensors.sidebarJson(mc));
					hud.add("bossbars", Sensors.bossbarJson(mc));
					writeJson(hudFile, hud);
				} catch (Exception ignored) {}
				try { writeJson(invFile, Sensors.inventoryJson(mc)); } catch (Exception ignored) {}
				try { writeJson(contFile, Sensors.containerJson(mc)); } catch (Exception ignored) {}
				try { writeJson(screenFile, Sensors.screenJson(mc)); } catch (Exception ignored) {}
			} else {
				writeJson(aliveFile, aliveJson(mc));
			}
		} catch (Exception ignored) {}
		pollInbox(mc);
	}

	private static JsonObject aliveJson(Minecraft mc) {
		JsonObject o = Json.obj();
		o.addProperty("ok", true);
		o.addProperty("t", System.currentTimeMillis());
		boolean hasWorld = mc.player != null && mc.level != null;
		o.addProperty("hasWorld", hasWorld);
		o.addProperty("tick", tickCount);
		return o;
	}

	private static void pollInbox(Minecraft mc) {
		try {
			if (!Files.exists(cmdFile)) return;
			String text = Files.readString(cmdFile, StandardCharsets.UTF_8).trim();
			if (text.isEmpty()) return;
			JsonObject req;
			try {
				req = JsonParser.parseString(text).getAsJsonObject();
			} catch (Exception e) {
				return; // CLI mid-write; wait for atomic rename to finish
			}
			if (!req.has("id") || !req.has("line")) return;
			String id = req.get("id").getAsString();
			if (id.equals(lastCmdId)) return; // already processed; wait for CLI to pick up resp
			String line = req.get("line").getAsString();
			JsonObject result;
			try {
				result = McaiExec.executeLine(line);
			} catch (Exception e) {
				result = Json.err(e.toString());
			}
			JsonObject resp = Json.obj();
			resp.addProperty("id", id);
			resp.add("result", result);
			resp.addProperty("t", System.currentTimeMillis());
			writeJson(respFile, resp);
			lastCmdId = id;
			try { Files.deleteIfExists(cmdFile); } catch (Exception ignored) {}
		} catch (Exception ignored) {}
	}

	private static void writeJson(Path file, JsonObject o) throws IOException {
		Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
		Files.writeString(tmp, Json.str(o), StandardCharsets.UTF_8);
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
		try {
			Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
		} catch (Exception ignored) {}
	}
}
