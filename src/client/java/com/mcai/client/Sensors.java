package com.mcai.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

/** Read-only sensors: game state from client memory, serialized as JSON. */
public final class Sensors {
	private Sensors() {}

	private static String itemId(ItemStack stack) {
		try {
			return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		} catch (Exception e) {
			return "unknown";
		}
	}

	public static JsonObject stackJson(ItemStack stack) {
		JsonObject o = new JsonObject();
		o.addProperty("empty", stack.isEmpty());
		if (stack.isEmpty()) return o;
		o.addProperty("id", itemId(stack));
		o.addProperty("count", stack.getCount());
		try { o.addProperty("name", stack.getHoverName().getString()); } catch (Exception ignored) {}
		try { o.addProperty("maxCount", stack.getMaxStackSize()); } catch (Exception ignored) {}
		return o;
	}

	private static String rangeStr(int a, int b) {
		return a == b ? String.valueOf(a) : a + "-" + b;
	}

	/**
	 * Token-efficient slot dump: only non-empty slots are listed individually,
	 * contiguous empty runs collapse into "emptyRanges" (e.g. ["8-29"]).
	 */
	private static JsonObject compactSlots(int size, java.util.function.IntFunction<ItemStack> get) {
		JsonObject o = Json.obj();
		o.addProperty("size", size);
		JsonArray filled = new JsonArray();
		JsonArray emptyRanges = new JsonArray();
		int nonEmpty = 0, rangeStart = -1;
		for (int i = 0; i < size; i++) {
			ItemStack s;
			try { s = get.apply(i); } catch (Exception e) { continue; }
			if (s == null || s.isEmpty()) {
				if (rangeStart < 0) rangeStart = i;
			} else {
				if (rangeStart >= 0) { emptyRanges.add(rangeStr(rangeStart, i - 1)); rangeStart = -1; }
				JsonObject so = stackJson(s);
				so.addProperty("slot", i);
				filled.add(so);
				nonEmpty++;
			}
		}
		if (rangeStart >= 0) emptyRanges.add(rangeStr(rangeStart, size - 1));
		o.add("slots", filled);
		o.add("emptyRanges", emptyRanges);
		o.addProperty("nonEmpty", nonEmpty);
		return o;
	}

	public static JsonObject stateJson(Minecraft mc) {
		JsonObject o = Json.obj();
		if (mc.player == null || mc.level == null) {
			o.addProperty("ok", false);
			o.addProperty("error", "no_world");
			return o;
		}
		o.addProperty("ok", true);
		var p = mc.player;
		var level = mc.level;
		JsonObject pos = new JsonObject();
		pos.addProperty("x", p.getX());
		pos.addProperty("y", p.getY());
		pos.addProperty("z", p.getZ());
		pos.addProperty("yaw", p.getYRot());
		pos.addProperty("pitch", p.getXRot());
		pos.addProperty("onGround", p.onGround());
		o.add("pos", pos);
		try {
			o.addProperty("dimension", level.dimension().identifier().toString());
		} catch (Exception e) {
			o.addProperty("dimension", "?");
		}
		try {
			JsonObject health = new JsonObject();
			health.addProperty("hp", p.getHealth());
			health.addProperty("max", p.getMaxHealth());
			health.addProperty("food", p.getFoodData().getFoodLevel());
			health.addProperty("saturation", p.getFoodData().getSaturationLevel());
			health.addProperty("air", p.getAirSupply());
			health.addProperty("armor", p.getArmorValue());
			o.add("health", health);
		} catch (Exception ignored) {}
		try {
			JsonObject flags = new JsonObject();
			flags.addProperty("alive", p.isAlive());
			flags.addProperty("sprinting", p.isSprinting());
			flags.addProperty("crouching", p.isCrouching());
			flags.addProperty("swimming", p.isSwimming());
			flags.addProperty("flying", p.getAbilities().flying);
			flags.addProperty("creative", p.getAbilities().instabuild);
			try { flags.addProperty("breaking", mc.gameMode != null && mc.gameMode.isDestroying()); }
			catch (Exception ignored2) {}
			o.add("flags", flags);
		} catch (Exception ignored) {}
		try {
			JsonObject xp = new JsonObject();
			xp.addProperty("level", p.experienceLevel);
			xp.addProperty("progress", p.experienceProgress);
			xp.addProperty("total", p.totalExperience);
			o.add("xp", xp);
		} catch (Exception ignored) {}
		try {
			JsonObject w = new JsonObject();
			try { w.addProperty("gameTime", level.getLevelData().getGameTime()); } catch (Exception ignored2) {}
			try { w.addProperty("dayTime", level.getOverworldClockTime()); } catch (Exception ignored2) {}
			try { w.addProperty("raining", level.isRaining()); } catch (Exception ignored2) {}
			try { w.addProperty("thundering", level.isThundering()); } catch (Exception ignored2) {}
			try { w.addProperty("difficulty", level.getLevelData().getDifficulty().getDisplayName().getString()); } catch (Exception ignored2) {}
			o.add("world", w);
		} catch (Exception ignored) {}
		try {
			BlockPos bp = p.blockPosition();
			JsonObject env = new JsonObject();
			env.addProperty("blockX", bp.getX());
			env.addProperty("blockY", bp.getY());
			env.addProperty("blockZ", bp.getZ());
			try {
				String biome = level.getBiome(bp).unwrapKey().map(k -> k.identifier().toString()).orElse("?");
				env.addProperty("biome", biome);
			} catch (Exception ignored2) {}
			try {
				env.addProperty("light", level.getLightEngine().getRawBrightness(bp, 0));
			} catch (Exception ignored2) {}
			o.add("env", env);
		} catch (Exception ignored) {}
		o.add("heldKeys", KeyHoldQueue.statusJson());
		return o;
	}

	public static JsonObject inventoryJson(Minecraft mc) {
		JsonObject o = Json.obj();
		if (mc.player == null) {
			o.addProperty("ok", false);
			o.addProperty("error", "no_player");
			return o;
		}
		o.addProperty("ok", true);
		Inventory inv = mc.player.getInventory();
		o.addProperty("selected", inv.getSelectedSlot());
		JsonObject compact = compactSlots(inv.getContainerSize(), inv::getItem);
		o.add("slots", compact.get("slots"));
		o.add("emptyRanges", compact.get("emptyRanges"));
		o.addProperty("size", compact.get("size").getAsInt());
		o.addProperty("nonEmpty", compact.get("nonEmpty").getAsInt());
		try {
			o.add("offhand", stackJson(mc.player.getOffhandItem()));
			o.add("mainhand", stackJson(mc.player.getMainHandItem()));
		} catch (Exception ignored) {}
		// If a container is open, include it so one `inventory` call covers both.
		try {
			AbstractContainerMenu menu = mc.player.containerMenu;
			boolean open = menu != null && menu.containerId != 0
				&& mc.screen instanceof AbstractContainerScreen;
			o.addProperty("containerOpen", open);
			if (open) o.add("openContainer", containerSummary(mc, menu));
		} catch (Exception ignored) {}
		return o;
	}

	/** Shared container summary used by `container` and embedded in `inventory`. */
	private static JsonObject containerSummary(Minecraft mc, AbstractContainerMenu menu) {
		JsonObject o = Json.obj();
		try { o.addProperty("syncId", menu.containerId); } catch (Exception ignored) {}
		String title = "";
		try {
			if (mc.screen instanceof AbstractContainerScreen<?> s) title = s.getTitle().getString();
		} catch (Exception ignored) {}
		o.addProperty("title", title);
		try {
			o.addProperty("screen", mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
		} catch (Exception ignored) {}
		try {
			// Split chest-side slots from player-inventory slots: the menu lists the
			// container's own slots first, then the player's (same Inventory object).
			int split = menu.slots.size();
			try {
				Inventory pinv = mc.player.getInventory();
				for (int i = 0; i < menu.slots.size(); i++) {
					if (menu.slots.get(i).container == pinv) { split = i; break; }
				}
			} catch (Exception ignored) {}
			o.addProperty("containerSize", split);
			o.addProperty("playerSlotsFrom", split);
			JsonObject compact = compactSlots(menu.slots.size(), i -> menu.slots.get(i).getItem());
			o.add("slots", compact.get("slots"));
			o.add("emptyRanges", compact.get("emptyRanges"));
			o.addProperty("size", compact.get("size").getAsInt());
			o.addProperty("nonEmpty", compact.get("nonEmpty").getAsInt());
		} catch (Exception e) {
			o.addProperty("slotsError", e.toString());
		}
		try { o.add("carried", stackJson(menu.getCarried())); } catch (Exception ignored) {}
		return o;
	}

	public static JsonObject screenJson(Minecraft mc) {
		JsonObject o = Json.obj();
		o.addProperty("ok", true);
		try {
			var screen = mc.screen;
			o.addProperty("open", screen != null);
			if (screen == null) {
				o.addProperty("name", "none");
				o.addProperty("title", "");
			} else {
				o.addProperty("name", screen.getClass().getSimpleName());
				try { o.addProperty("title", screen.getTitle().getString()); }
				catch (Exception ignored) { o.addProperty("title", ""); }
				o.addProperty("isContainer", screen instanceof AbstractContainerScreen);
				o.addProperty("isChat", screen instanceof net.minecraft.client.gui.screens.ChatScreen);
				o.addProperty("isInventory", screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen);
			}
		} catch (Exception e) {
			o.addProperty("ok", false);
			o.addProperty("error", e.toString());
		}
		return o;
	}

	public static JsonObject containerJson(Minecraft mc) {
		JsonObject o = Json.obj();
		if (mc.player == null) {
			o.addProperty("ok", false);
			o.addProperty("error", "no_player");
			return o;
		}
		AbstractContainerMenu menu = mc.player.containerMenu;
		if (menu == null) {
			o.addProperty("ok", false);
			o.addProperty("error", "no_menu");
			return o;
		}
		o.addProperty("ok", true);
		for (var entry : containerSummary(mc, menu).entrySet()) o.add(entry.getKey(), entry.getValue());
		return o;
	}

	public static JsonObject entitiesJson(Minecraft mc, int radius, int limit) {
		JsonObject o = Json.obj();
		if (mc.player == null || mc.level == null) {
			o.addProperty("ok", false);
			o.addProperty("error", "no_world");
			return o;
		}
		int r = Math.max(1, Math.min(radius <= 0 ? 32 : radius, 128));
		int lim = Math.max(1, Math.min(limit <= 0 ? 32 : limit, 128));
		o.addProperty("ok", true);
		o.addProperty("radius", r);
		JsonArray arr = new JsonArray();
		try {
			Vec3 c = mc.player.position();
			AABB box = new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
			var list = mc.level.getEntities(mc.player, box, e -> true);
			int n = 0;
			for (Entity e : list) {
				if (n >= lim) break;
				JsonObject eo = new JsonObject();
				try {
					eo.addProperty("id", e.getId());
					eo.addProperty("uuid", e.getUUID().toString());
					eo.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
					eo.addProperty("name", e.getName().getString());
					eo.addProperty("x", e.getX());
					eo.addProperty("y", e.getY());
					eo.addProperty("z", e.getZ());
					eo.addProperty("dist", Math.sqrt(e.distanceToSqr(c)));
					eo.addProperty("alive", e.isAlive());
					if (e instanceof net.minecraft.world.entity.LivingEntity le) {
						eo.addProperty("hp", le.getHealth());
					}
				} catch (Exception ex) {
					eo.addProperty("error", ex.toString());
				}
				arr.add(eo);
				n++;
			}
			o.addProperty("count", n);
		} catch (Exception e) {
			o.addProperty("ok", false);
			o.addProperty("error", e.toString());
		}
		o.add("entities", arr);
		return o;
	}

	public static JsonObject targetJson(Minecraft mc) {
		JsonObject o = Json.obj();
		if (mc.player == null || mc.level == null) {
			o.addProperty("ok", false);
			o.addProperty("error", "no_world");
			return o;
		}
		o.addProperty("ok", true);
		try {
			HitResult hit = mc.hitResult;
			if (hit == null || hit.getType() == HitResult.Type.MISS) {
				o.addProperty("type", "miss");
			} else if (hit instanceof BlockHitResult bhr) {
				o.addProperty("type", "block");
				BlockPos pos = bhr.getBlockPos();
				o.addProperty("x", pos.getX());
				o.addProperty("y", pos.getY());
				o.addProperty("z", pos.getZ());
				try {
					var st = mc.level.getBlockState(pos);
					o.addProperty("id", BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString());
					o.addProperty("face", bhr.getDirection().getName());
				} catch (Exception ignored) {}
			} else if (hit instanceof EntityHitResult ehr) {
				o.addProperty("type", "entity");
				Entity e = ehr.getEntity();
				o.addProperty("entityType", BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
				o.addProperty("name", e.getName().getString());
			} else {
				o.addProperty("type", hit.getType().toString());
			}
		} catch (Exception e) {
			o.addProperty("ok", false);
			o.addProperty("error", e.toString());
		}
		try {
			Vec3 eye = mc.player.getEyePosition();
			Vec3 look = mc.player.getLookAngle();
			double reach = 5.0;
			Vec3 end = eye.add(look.x * reach, look.y * reach, look.z * reach);
			BlockHitResult ray = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
			if (ray.getType() != HitResult.Type.MISS) {
				JsonObject ro = new JsonObject();
				ro.addProperty("x", ray.getBlockPos().getX());
				ro.addProperty("y", ray.getBlockPos().getY());
				ro.addProperty("z", ray.getBlockPos().getZ());
				ro.addProperty("id", BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(ray.getBlockPos()).getBlock()).toString());
				o.add("ray", ro);
			}
		} catch (Exception ignored) {}
		return o;
	}

	public static JsonObject scanJson(Minecraft mc, int radius) {
		JsonObject o = Json.obj();
		if (mc.player == null || mc.level == null) {
			o.addProperty("ok", false);
			o.addProperty("error", "no_world");
			return o;
		}
		int r = Math.max(1, Math.min(radius <= 0 ? 8 : radius, 24));
		o.addProperty("ok", true);
		o.addProperty("radius", r);
		JsonObject counts = new JsonObject();
		try {
			Level level = mc.level;
			BlockPos base = mc.player.blockPosition();
			for (int dx = -r; dx <= r; dx++)
				for (int dy = -r; dy <= r; dy++)
					for (int dz = -r; dz <= r; dz++) {
						BlockPos p = base.offset(dx, dy, dz);
						String id;
						try { id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString(); }
						catch (Exception e) { id = "?"; }
						counts.addProperty(id, counts.has(id) ? counts.get(id).getAsInt() + 1 : 1);
					}
		} catch (Exception e) {
			o.addProperty("ok", false);
			o.addProperty("error", e.toString());
		}
		o.add("counts", counts);
		return o;
	}

	public static JsonObject sidebarJson(Minecraft mc) {
		JsonObject o = Json.obj();
		if (mc.level == null) {
			o.addProperty("ok", false);
			o.addProperty("error", "no_world");
			return o;
		}
		try {
			Scoreboard sb = mc.level.getScoreboard();
			Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
			if (obj == null) {
				o.addProperty("ok", true);
				o.addProperty("present", false);
				return o;
			}
			o.addProperty("ok", true);
			o.addProperty("present", true);
			o.addProperty("objective", obj.getName());
			try { o.addProperty("display", obj.getDisplayName().getString()); } catch (Exception ignored) {}
			JsonArray lines = new JsonArray();
			try {
				Collection<PlayerScoreEntry> scores = sb.listPlayerScores(obj);
				for (PlayerScoreEntry s : scores) {
					JsonObject so = new JsonObject();
					try {
						so.addProperty("owner", s.owner());
						so.addProperty("value", s.value());
					} catch (Exception ex) {
						so.addProperty("error", ex.toString());
					}
					lines.add(so);
				}
			} catch (Exception e) {
				o.addProperty("linesError", e.toString());
			}
			o.add("lines", lines);
		} catch (Exception e) {
			o.addProperty("ok", false);
			o.addProperty("error", e.toString());
		}
		return o;
	}

	@SuppressWarnings("unchecked")
	public static JsonObject bossbarJson(Minecraft mc) {
		JsonObject o = Json.obj();
		try {
			var overlay = mc.gui.getBossOverlay();
			JsonArray arr = new JsonArray();
			try {
				Field f = overlay.getClass().getDeclaredField("events");
				f.setAccessible(true);
				Object map = f.get(overlay);
				if (map instanceof Map<?, ?> m) {
					for (Object event : m.values()) {
						JsonObject eo = new JsonObject();
						try {
							var be = (net.minecraft.world.BossEvent) event;
							eo.addProperty("name", be.getName().getString());
							eo.addProperty("progress", be.getProgress());
							eo.addProperty("color", be.getColor().getName());
							eo.addProperty("overlay", be.getOverlay().getName());
						} catch (Exception ex) {
							eo.addProperty("error", ex.toString());
						}
						arr.add(eo);
					}
				}
			} catch (Exception e) {
				o.addProperty("eventsError", e.toString());
			}
			o.addProperty("ok", true);
			o.add("bars", arr);
		} catch (Exception e) {
			o.addProperty("ok", false);
			o.addProperty("error", e.toString());
		}
		return o;
	}

	public static JsonObject effectsJson(Minecraft mc) {
		JsonObject o = Json.obj();
		if (mc.player == null) {
			o.addProperty("ok", false);
			o.addProperty("error", "no_player");
			return o;
		}
		try {
			JsonArray arr = new JsonArray();
			for (var inst : mc.player.getActiveEffects()) {
				JsonObject eo = new JsonObject();
				try {
					eo.addProperty("id", BuiltInRegistries.MOB_EFFECT.getKey(inst.getEffect().value()).toString());
					eo.addProperty("amp", inst.getAmplifier());
					eo.addProperty("ticks", inst.getDuration());
					eo.addProperty("ambient", inst.isAmbient());
					eo.addProperty("visible", inst.isVisible());
				} catch (Exception ex) {
					eo.addProperty("error", ex.toString());
				}
				arr.add(eo);
			}
			o.addProperty("ok", true);
			o.add("effects", arr);
		} catch (Exception e) {
			o.addProperty("ok", false);
			o.addProperty("error", e.toString());
		}
		return o;
	}
}
