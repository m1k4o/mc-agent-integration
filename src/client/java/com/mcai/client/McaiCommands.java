package com.mcai.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** /mcai + /mc-agent-integration client command tree. JSON-only output for agents. */
public final class McaiCommands {
	private McaiCommands() {}

	record Cmd(String name, String usage, String desc) {}

	private static final Cmd[] OUTPUT = {
		new Cmd("state", "/mcai state", "full tick snapshot: pos/rot/dim/health/flags/xp/world/env"),
		new Cmd("pos", "/mcai pos", "compact position + rotation"),
		new Cmd("world", "/mcai world", "time/weather/difficulty + biome/light at feet"),
		new Cmd("chat", "/mcai chat [limit]", "last N chat messages from memory (no screenshot)"),
		new Cmd("titles", "/mcai titles", "current title/subtitle/actionbar from memory"),
		new Cmd("sidebar", "/mcai sidebar", "scoreboard sidebar lines from memory"),
		new Cmd("bossbars", "/mcai bossbars", "active boss bars with progress"),
		new Cmd("inventory", "/mcai inventory", "player inventory (empty runs collapsed to emptyRanges) + openContainer if a container is open"),
		new Cmd("container", "/mcai container", "open container/menu slots + title"),
		new Cmd("held", "/mcai held", "mainhand + offhand detail"),
		new Cmd("entities", "/mcai entities [radius] [limit]", "nearby entities with type/pos/dist/hp"),
		new Cmd("target", "/mcai target", "crosshair block/entity + raycast block"),
		new Cmd("scan", "/mcai scan [radius]", "block census cube around player (agent map instead of screenshot)"),
		new Cmd("effects", "/mcai effects", "active mob effects with amplifier/ticks"),
		new Cmd("hud", "/mcai hud", "combined chat+titles+sidebar+bossbars in one call"),
		new Cmd("keys", "/mcai keys", "currently held simulated keys with ticks left"),
		new Cmd("events", "/mcai events [limit]", "recent captured events (chat/title/death/hurt)"),
		new Cmd("screen", "/mcai screen", "which GUI screen is open (chat/inventory/chest/death/pause)"),
		new Cmd("alive", "/mcai alive", "liveness ping: hasWorld + open screen"),
		new Cmd("recipe", "/mcai recipe <item> [limit]", "recipe-book entries crafting an item, with ingredient options + ids for craft")
	};

	private static final Cmd[] INPUT = {
		new Cmd("say", "/mcai say <message>", "send chat message"),
		new Cmd("run", "/mcai run <command...>", "execute client command (without leading slash)"),
		new Cmd("look", "/mcai look <yaw> <pitch>", "set look rotation instantly"),
		new Cmd("lookat", "/mcai lookat <x> <y> <z>", "look at world coordinates"),
		new Cmd("press", "/mcai press <key> [ticks]", "hold key: forward back left right jump sneak sprint attack use"),
		new Cmd("release", "/mcai release <key>", "release a held key"),
		new Cmd("stop", "/mcai stop", "release all held keys"),
		new Cmd("click", "/mcai click <slot> [button] [mode]", "click a slot in the open container/menu (button 0=left 1=right, mode pickup|quickmove|swap|throw)"),
		new Cmd("select", "/mcai select <0-8>", "select hotbar slot"),
		new Cmd("drop", "/mcai drop [all]", "drop selected stack (or all if 'all')"),
		new Cmd("attack", "/mcai attack", "single attack pulse (1 tick)"),
		new Cmd("use", "/mcai use", "single use pulse (1 tick)"),
		new Cmd("interact", "/mcai interact [hand]", "right-click the crosshair entity (traders, boats, levers via entity)"),
		new Cmd("place", "/mcai place [hand]", "place held block against the crosshair block face"),
		new Cmd("craft", "/mcai craft <recipeId> [one]", "place a recipe from `recipe` into the open crafting grid (default shift-clicks full stack)"),
		new Cmd("equip", "/mcai equip <slot>", "shift-click a menu slot to auto-equip armor"),
		new Cmd("close", "/mcai close", "close the open screen (container/chat/inventory)"),
		new Cmd("respawn", "/mcai respawn", "respawn if dead"),
		new Cmd("clear", "/mcai clear", "clear captured chat/event buffers")
	};

	public static JsonObject helpJson() {
		JsonObject o = Json.obj();
		o.addProperty("ok", true);
		o.addProperty("mod", "mc-agent-integration");
		o.addProperty("note", "machine-readable agent API. No args = this help. All output is JSON.");
		JsonArray in = new JsonArray();
		for (Cmd c : INPUT) {
			JsonObject co = new JsonObject();
			co.addProperty("name", c.name());
			co.addProperty("usage", c.usage());
			co.addProperty("desc", c.desc());
			in.add(co);
		}
		JsonArray out = new JsonArray();
		for (Cmd c : OUTPUT) {
			JsonObject co = new JsonObject();
			co.addProperty("name", c.name());
			co.addProperty("usage", c.usage());
			co.addProperty("desc", c.desc());
			out.add(co);
		}
		o.add("input", in);
		o.add("output", out);
		return o;
	}

	private static int reply(FabricClientCommandSource src, JsonObject o) {
		src.sendFeedback(Component.literal(Json.str(o)));
		return 1;
	}

	private static int replyErr(FabricClientCommandSource src, Exception e) {
		return reply(src, Json.err(e.toString()));
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> d) {
		LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("mcai")
			.executes(ctx -> reply(ctx.getSource(), helpJson()))
			// ---- output ----
			.then(ClientCommands.literal("state").executes(ctx -> {
				try { return reply(ctx.getSource(), Sensors.stateJson(Minecraft.getInstance())); }
				catch (Exception e) { return replyErr(ctx.getSource(), e); }
			}))
			.then(ClientCommands.literal("pos").executes(ctx -> {
				try {
					Minecraft mc = Minecraft.getInstance();
					JsonObject o = Json.obj();
					if (mc.player == null) { o.addProperty("ok", false); o.addProperty("error", "no_player"); return reply(ctx.getSource(), o); }
					o.addProperty("ok", true);
					o.addProperty("x", mc.player.getX());
					o.addProperty("y", mc.player.getY());
					o.addProperty("z", mc.player.getZ());
					o.addProperty("yaw", mc.player.getYRot());
					o.addProperty("pitch", mc.player.getXRot());
					return reply(ctx.getSource(), o);
				} catch (Exception e) { return replyErr(ctx.getSource(), e); }
			}))
			.then(ClientCommands.literal("world").executes(ctx -> {
				try {
					JsonObject st = Sensors.stateJson(Minecraft.getInstance());
					JsonObject o = Json.obj();
					o.addProperty("ok", st.has("ok") && st.get("ok").getAsBoolean());
					if (st.has("world")) o.add("world", st.get("world"));
					if (st.has("env")) o.add("env", st.get("env"));
					if (st.has("pos")) o.add("pos", st.get("pos"));
					return reply(ctx.getSource(), o);
				} catch (Exception e) { return replyErr(ctx.getSource(), e); }
			}))
			.then(ClientCommands.literal("chat")
				.executes(ctx -> {
					JsonObject o = Json.obj();
					o.addProperty("ok", true);
					o.add("messages", HudBuffer.chatJson(20));
					return reply(ctx.getSource(), o);
				})
				.then(ClientCommands.argument("limit", IntegerArgumentType.integer(1, 200))
					.executes(ctx -> {
						int lim = IntegerArgumentType.getInteger(ctx, "limit");
						JsonObject o = Json.obj();
						o.addProperty("ok", true);
						o.add("messages", HudBuffer.chatJson(lim));
						return reply(ctx.getSource(), o);
					})))
			.then(ClientCommands.literal("titles").executes(ctx -> {
				try {
					JsonObject o = HudBuffer.titlesJson();
					o.addProperty("ok", true);
					return reply(ctx.getSource(), o);
				} catch (Exception e) { return replyErr(ctx.getSource(), e); }
			}))
			.then(ClientCommands.literal("sidebar").executes(ctx -> {
				try { return reply(ctx.getSource(), Sensors.sidebarJson(Minecraft.getInstance())); }
				catch (Exception e) { return replyErr(ctx.getSource(), e); }
			}))
			.then(ClientCommands.literal("bossbars").executes(ctx -> {
				try { return reply(ctx.getSource(), Sensors.bossbarJson(Minecraft.getInstance())); }
				catch (Exception e) { return replyErr(ctx.getSource(), e); }
			}))
			.then(ClientCommands.literal("inventory").executes(ctx -> {
				try { return reply(ctx.getSource(), Sensors.inventoryJson(Minecraft.getInstance())); }
				catch (Exception e) { return replyErr(ctx.getSource(), e); }
			}))
			.then(ClientCommands.literal("container").executes(ctx -> {
				try { return reply(ctx.getSource(), Sensors.containerJson(Minecraft.getInstance())); }
				catch (Exception e) { return replyErr(ctx.getSource(), e); }
			}))
			.then(ClientCommands.literal("held").executes(ctx -> {
				try {
					Minecraft mc = Minecraft.getInstance();
					JsonObject o = Json.obj();
					if (mc.player == null) { o.addProperty("ok", false); o.addProperty("error", "no_player"); return reply(ctx.getSource(), o); }
					o.addProperty("ok", true);
					o.add("mainhand", Sensors.stackJson(mc.player.getMainHandItem()));
					o.add("offhand", Sensors.stackJson(mc.player.getOffhandItem()));
					return reply(ctx.getSource(), o);
				} catch (Exception e) { return replyErr(ctx.getSource(), e); }
			}))
			.then(ClientCommands.literal("entities")
				.executes(ctx -> {
					try { return reply(ctx.getSource(), Sensors.entitiesJson(Minecraft.getInstance(), 32, 32)); }
					catch (Exception e) { return replyErr(ctx.getSource(), e); }
				})
				.then(ClientCommands.argument("radius", IntegerArgumentType.integer(1, 128))
					.executes(ctx -> {
						try { return reply(ctx.getSource(), Sensors.entitiesJson(Minecraft.getInstance(), IntegerArgumentType.getInteger(ctx, "radius"), 32)); }
						catch (Exception e) { return replyErr(ctx.getSource(), e); }
					})
					.then(ClientCommands.argument("limit", IntegerArgumentType.integer(1, 128))
						.executes(ctx -> {
							try { return reply(ctx.getSource(), Sensors.entitiesJson(Minecraft.getInstance(), IntegerArgumentType.getInteger(ctx, "radius"), IntegerArgumentType.getInteger(ctx, "limit"))); }
							catch (Exception e) { return replyErr(ctx.getSource(), e); }
						}))))
			.then(ClientCommands.literal("target").executes(ctx -> {
				try { return reply(ctx.getSource(), Sensors.targetJson(Minecraft.getInstance())); }
				catch (Exception e) { return replyErr(ctx.getSource(), e); }
			}))
			.then(ClientCommands.literal("scan")
				.executes(ctx -> {
					try { return reply(ctx.getSource(), Sensors.scanJson(Minecraft.getInstance(), 8)); }
					catch (Exception e) { return replyErr(ctx.getSource(), e); }
				})
				.then(ClientCommands.argument("radius", IntegerArgumentType.integer(1, 24))
					.executes(ctx -> {
						try { return reply(ctx.getSource(), Sensors.scanJson(Minecraft.getInstance(), IntegerArgumentType.getInteger(ctx, "radius"))); }
						catch (Exception e) { return replyErr(ctx.getSource(), e); }
					})))
			.then(ClientCommands.literal("effects").executes(ctx -> {
				try { return reply(ctx.getSource(), Sensors.effectsJson(Minecraft.getInstance())); }
				catch (Exception e) { return replyErr(ctx.getSource(), e); }
			}))
			.then(ClientCommands.literal("hud").executes(ctx -> {
				try {
					Minecraft mc = Minecraft.getInstance();
					JsonObject o = Json.obj();
					o.addProperty("ok", true);
					o.add("messages", HudBuffer.chatJson(20));
					o.add("titles", HudBuffer.titlesJson());
					o.add("sidebar", Sensors.sidebarJson(mc));
					o.add("bossbars", Sensors.bossbarJson(mc));
					return reply(ctx.getSource(), o);
				} catch (Exception e) { return replyErr(ctx.getSource(), e); }
			}))
			.then(ClientCommands.literal("keys").executes(ctx -> {
				JsonObject o = Json.obj();
				o.addProperty("ok", true);
				o.add("held", KeyHoldQueue.statusJson());
				return reply(ctx.getSource(), o);
			}))
			.then(ClientCommands.literal("events")
				.executes(ctx -> {
					JsonObject o = Json.obj();
					o.addProperty("ok", true);
					o.add("events", HudBuffer.eventsJson(30));
					return reply(ctx.getSource(), o);
				})
				.then(ClientCommands.argument("limit", IntegerArgumentType.integer(1, 200))
					.executes(ctx -> {
						JsonObject o = Json.obj();
						o.addProperty("ok", true);
						o.add("events", HudBuffer.eventsJson(IntegerArgumentType.getInteger(ctx, "limit")));
						return reply(ctx.getSource(), o);
					})))
			.then(ClientCommands.literal("screen").executes(ctx -> {
				try { return reply(ctx.getSource(), Sensors.screenJson(Minecraft.getInstance())); }
				catch (Exception e) { return replyErr(ctx.getSource(), e); }
			}))
			.then(ClientCommands.literal("alive").executes(ctx -> {
				try {
					Minecraft mc = Minecraft.getInstance();
					JsonObject o = Json.obj();
					o.addProperty("ok", true);
					o.addProperty("t", System.currentTimeMillis());
					o.addProperty("hasWorld", mc.player != null && mc.level != null);
					o.add("screen", Sensors.screenJson(mc));
					return reply(ctx.getSource(), o);
				} catch (Exception e) { return replyErr(ctx.getSource(), e); }
			}))
			.then(ClientCommands.literal("recipe")
				.then(ClientCommands.argument("item", StringArgumentType.greedyString())
					.executes(ctx -> {
						try { return reply(ctx.getSource(), Recipes.recipeJson(Minecraft.getInstance(), StringArgumentType.getString(ctx, "item"), 10)); }
						catch (Exception e) { return replyErr(ctx.getSource(), e); }
					})))
			// ---- input ----
			.then(ClientCommands.literal("say")
				.then(ClientCommands.argument("message", StringArgumentType.greedyString())
					.executes(ctx -> {
						try {
							String msg = StringArgumentType.getString(ctx, "message");
							Minecraft mc = Minecraft.getInstance();
							if (mc.player == null || mc.getConnection() == null) return reply(ctx.getSource(), Json.err("no_world"));
							mc.getConnection().sendChat(msg);
							JsonObject o = Json.ok();
							o.addProperty("action", "say");
							o.addProperty("message", msg);
							return reply(ctx.getSource(), o);
						} catch (Exception e) { return replyErr(ctx.getSource(), e); }
					})))
			.then(ClientCommands.literal("run")
				.then(ClientCommands.argument("command", StringArgumentType.greedyString())
					.executes(ctx -> {
						try {
							String cmd = StringArgumentType.getString(ctx, "command");
							Minecraft mc = Minecraft.getInstance();
							if (mc.player == null || mc.getConnection() == null) return reply(ctx.getSource(), Json.err("no_world"));
							String stripped = cmd.startsWith("/") ? cmd.substring(1) : cmd;
							mc.getConnection().sendCommand(stripped);
							JsonObject o = Json.ok();
							o.addProperty("action", "run");
							o.addProperty("command", stripped);
							return reply(ctx.getSource(), o);
						} catch (Exception e) { return replyErr(ctx.getSource(), e); }
					})))
			.then(ClientCommands.literal("look")
				.then(ClientCommands.argument("yaw", FloatArgumentType.floatArg(-180f, 180f))
					.then(ClientCommands.argument("pitch", FloatArgumentType.floatArg(-90f, 90f))
						.executes(ctx -> {
							try {
								float yaw = FloatArgumentType.getFloat(ctx, "yaw");
								float pitch = FloatArgumentType.getFloat(ctx, "pitch");
								Minecraft mc = Minecraft.getInstance();
								if (mc.player == null) return reply(ctx.getSource(), Json.err("no_player"));
								mc.player.setYRot(yaw);
								mc.player.setXRot(pitch);
								JsonObject o = Json.ok();
								o.addProperty("action", "look");
								o.addProperty("yaw", yaw);
								o.addProperty("pitch", pitch);
								return reply(ctx.getSource(), o);
							} catch (Exception e) { return replyErr(ctx.getSource(), e); }
						}))))
			.then(ClientCommands.literal("lookat")
				.then(ClientCommands.argument("x", FloatArgumentType.floatArg())
					.then(ClientCommands.argument("y", FloatArgumentType.floatArg())
						.then(ClientCommands.argument("z", FloatArgumentType.floatArg())
							.executes(ctx -> {
								try {
									float x = FloatArgumentType.getFloat(ctx, "x");
									float y = FloatArgumentType.getFloat(ctx, "y");
									float z = FloatArgumentType.getFloat(ctx, "z");
									Minecraft mc = Minecraft.getInstance();
									if (mc.player == null) return reply(ctx.getSource(), Json.err("no_player"));
									var eye = mc.player.getEyePosition();
									double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
									double h = Math.sqrt(dx * dx + dz * dz);
									float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
									float pitch = (float) (-Math.toDegrees(Math.atan2(dy, h)));
									mc.player.setYRot(yaw);
									mc.player.setXRot(pitch);
									JsonObject o = Json.ok();
									o.addProperty("action", "lookat");
									o.addProperty("yaw", yaw);
									o.addProperty("pitch", pitch);
									return reply(ctx.getSource(), o);
								} catch (Exception e) { return replyErr(ctx.getSource(), e); }
							})))))
			.then(ClientCommands.literal("press")
				.then(ClientCommands.argument("key", StringArgumentType.word())
					.executes(ctx -> {
						String key = StringArgumentType.getString(ctx, "key").toLowerCase();
						String err = KeyHoldQueue.press(key, 1);
						if (err != null) return reply(ctx.getSource(), Json.err(err));
						JsonObject o = Json.ok();
						o.addProperty("action", "press");
						o.addProperty("key", key);
						o.addProperty("ticks", 1);
						return reply(ctx.getSource(), o);
					})
					.then(ClientCommands.argument("ticks", IntegerArgumentType.integer(1, 1200))
						.executes(ctx -> {
							String key = StringArgumentType.getString(ctx, "key").toLowerCase();
							int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
							String err = KeyHoldQueue.press(key, ticks);
							if (err != null) return reply(ctx.getSource(), Json.err(err));
							JsonObject o = Json.ok();
							o.addProperty("action", "press");
							o.addProperty("key", key);
							o.addProperty("ticks", ticks);
							return reply(ctx.getSource(), o);
						}))))
			.then(ClientCommands.literal("release")
				.then(ClientCommands.argument("key", StringArgumentType.word())
					.executes(ctx -> {
						String key = StringArgumentType.getString(ctx, "key").toLowerCase();
						String err = KeyHoldQueue.release(key);
						if (err != null) return reply(ctx.getSource(), Json.err(err));
						JsonObject o = Json.ok();
						o.addProperty("action", "release");
						o.addProperty("key", key);
						return reply(ctx.getSource(), o);
					})))
			.then(ClientCommands.literal("stop").executes(ctx -> {
				KeyHoldQueue.releaseAll();
				JsonObject o = Json.ok();
				o.addProperty("action", "stop");
				return reply(ctx.getSource(), o);
			}))
			.then(ClientCommands.literal("click")
				.then(ClientCommands.argument("slot", IntegerArgumentType.integer(0, 200))
					.executes(ctx -> clickSlot(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot"), 0, "pickup"))
					.then(ClientCommands.argument("button", IntegerArgumentType.integer(0, 8))
						.executes(ctx -> {
							try {
								String mode = "pickup";
								try { mode = ctx.getArgument("mode", String.class); } catch (Exception ignored) {}
								return clickSlot(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot"), IntegerArgumentType.getInteger(ctx, "button"), mode);
							} catch (Exception e) { return replyErr(ctx.getSource(), e); }
						})
						.then(ClientCommands.argument("mode", StringArgumentType.word())
							.executes(ctx -> clickSlot(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot"), IntegerArgumentType.getInteger(ctx, "button"), StringArgumentType.getString(ctx, "mode")))))))
			.then(ClientCommands.literal("select")
				.then(ClientCommands.argument("slot", IntegerArgumentType.integer(0, 8))
					.executes(ctx -> {
						try {
							int slot = IntegerArgumentType.getInteger(ctx, "slot");
							Minecraft mc = Minecraft.getInstance();
							if (mc.player == null) return reply(ctx.getSource(), Json.err("no_player"));
							mc.player.getInventory().setSelectedSlot(slot);
							JsonObject o = Json.ok();
							o.addProperty("action", "select");
							o.addProperty("slot", slot);
							return reply(ctx.getSource(), o);
						} catch (Exception e) { return replyErr(ctx.getSource(), e); }
					})))
			.then(ClientCommands.literal("drop")
				.executes(ctx -> dropSel(ctx.getSource(), false))
				.then(ClientCommands.literal("all").executes(ctx -> dropSel(ctx.getSource(), true))))
			.then(ClientCommands.literal("attack").executes(ctx -> {
				String err = KeyHoldQueue.press("attack", 1);
				if (err != null) return reply(ctx.getSource(), Json.err(err));
				JsonObject o = Json.ok();
				o.addProperty("action", "attack");
				return reply(ctx.getSource(), o);
			}))
			.then(ClientCommands.literal("use").executes(ctx -> {
				String err = KeyHoldQueue.press("use", 1);
				if (err != null) return reply(ctx.getSource(), Json.err(err));
				JsonObject o = Json.ok();
				o.addProperty("action", "use");
				return reply(ctx.getSource(), o);
			}))
			.then(ClientCommands.literal("interact")
				.executes(ctx -> interactHand(ctx.getSource(), "main"))
				.then(ClientCommands.argument("hand", StringArgumentType.word())
					.executes(ctx -> interactHand(ctx.getSource(), StringArgumentType.getString(ctx, "hand")))))
			.then(ClientCommands.literal("place")
				.executes(ctx -> placeHand(ctx.getSource(), "main"))
				.then(ClientCommands.argument("hand", StringArgumentType.word())
					.executes(ctx -> placeHand(ctx.getSource(), StringArgumentType.getString(ctx, "hand")))))
			.then(ClientCommands.literal("craft")
				.then(ClientCommands.argument("recipeId", IntegerArgumentType.integer(0))
					.executes(ctx -> {
						try { return reply(ctx.getSource(), Recipes.craftJson(Minecraft.getInstance(), IntegerArgumentType.getInteger(ctx, "recipeId"), true)); }
						catch (Exception e) { return replyErr(ctx.getSource(), e); }
					})
					.then(ClientCommands.literal("one").executes(ctx -> {
						try { return reply(ctx.getSource(), Recipes.craftJson(Minecraft.getInstance(), IntegerArgumentType.getInteger(ctx, "recipeId"), false)); }
						catch (Exception e) { return replyErr(ctx.getSource(), e); }
					}))))
			.then(ClientCommands.literal("equip")
				.then(ClientCommands.argument("slot", IntegerArgumentType.integer(0, 200))
					.executes(ctx -> {
						try {
							Minecraft mc = Minecraft.getInstance();
							if (mc.player == null || mc.gameMode == null) return reply(ctx.getSource(), Json.err("no_world"));
							var menu = mc.player.containerMenu;
							if (menu == null) return reply(ctx.getSource(), Json.err("no_menu"));
							int slot = IntegerArgumentType.getInteger(ctx, "slot");
							if (slot < 0 || slot >= menu.slots.size()) return reply(ctx.getSource(), Json.err("bad_slot:" + slot + " size=" + menu.slots.size()));
							mc.gameMode.handleContainerInput(menu.containerId, slot, 0,
								net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc.player);
							JsonObject o = Json.ok();
							o.addProperty("action", "equip");
							o.addProperty("slot", slot);
							try { o.add("after", Sensors.stackJson(menu.slots.get(slot).getItem())); }
							catch (Exception ignored) {}
							return reply(ctx.getSource(), o);
						} catch (Exception e) { return replyErr(ctx.getSource(), e); }
					})))
			.then(ClientCommands.literal("close").executes(ctx -> {
				try {
					Minecraft mc = Minecraft.getInstance();
					mc.setScreen(null);
					KeyHoldQueue.releaseAll();
					JsonObject o = Json.ok();
					o.addProperty("action", "close");
					return reply(ctx.getSource(), o);
				} catch (Exception e) { return replyErr(ctx.getSource(), e); }
			}))
			.then(ClientCommands.literal("respawn").executes(ctx -> {
				try {
					Minecraft mc = Minecraft.getInstance();
					if (mc.player == null) return reply(ctx.getSource(), Json.err("no_player"));
					if (!mc.player.isAlive()) {
						mc.player.respawn();
						JsonObject o = Json.ok();
						o.addProperty("action", "respawn");
						return reply(ctx.getSource(), o);
					}
					JsonObject o = Json.ok();
					o.addProperty("action", "respawn");
					o.addProperty("note", "already_alive");
					return reply(ctx.getSource(), o);
				} catch (Exception e) { return replyErr(ctx.getSource(), e); }
			}))
			.then(ClientCommands.literal("clear").executes(ctx -> {
				HudBuffer.clear();
				JsonObject o = Json.ok();
				o.addProperty("action", "clear");
				return reply(ctx.getSource(), o);
			}));

		d.register(root);
		// alias: /mc-agent-integration mirrors /mcai (no-args prints same help)
		LiteralArgumentBuilder<FabricClientCommandSource> alias = ClientCommands.literal("mc-agent-integration")
			.executes(ctx -> reply(ctx.getSource(), helpJson()));
		d.register(alias);
	}

	private static int interactHand(FabricClientCommandSource src, String handS) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.gameMode == null) return reply(src, Json.err("no_world"));
			var hit = mc.hitResult;
			if (!(hit instanceof net.minecraft.world.phys.EntityHitResult ehr))
				return reply(src, Json.err("no_entity_target (crosshair is not on an entity; check target)"));
			var hand = handS.startsWith("off")
				? net.minecraft.world.InteractionHand.OFF_HAND
				: net.minecraft.world.InteractionHand.MAIN_HAND;
			var result = mc.gameMode.interact(mc.player, ehr.getEntity(), ehr, hand);
			JsonObject o = Json.ok();
			o.addProperty("action", "interact");
			o.addProperty("hand", hand.name());
			o.addProperty("result", result.toString());
			return reply(src, o);
		} catch (Exception e) { return replyErr(src, e); }
	}

	private static int placeHand(FabricClientCommandSource src, String handS) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.gameMode == null) return reply(src, Json.err("no_world"));
			var hit = mc.hitResult;
			if (!(hit instanceof net.minecraft.world.phys.BlockHitResult bhr))
				return reply(src, Json.err("no_block_target (crosshair is not on a block; check target)"));
			var hand = handS.startsWith("off")
				? net.minecraft.world.InteractionHand.OFF_HAND
				: net.minecraft.world.InteractionHand.MAIN_HAND;
			var result = mc.gameMode.useItemOn(mc.player, hand, bhr);
			JsonObject o = Json.ok();
			o.addProperty("action", "place");
			o.addProperty("hand", hand.name());
			o.addProperty("result", result.toString());
			return reply(src, o);
		} catch (Exception e) { return replyErr(src, e); }
	}

	private static int clickSlot(FabricClientCommandSource src, int slot, int button, String modeStr) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.gameMode == null) return reply(src, Json.err("no_world"));
			var menu = mc.player.containerMenu;
			if (menu == null) return reply(src, Json.err("no_menu"));
			if (slot < 0 || slot >= menu.slots.size()) return reply(src, Json.err("bad_slot:" + slot + " size=" + menu.slots.size()));
			var input = parseClickInput(modeStr);
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
			return reply(src, o);
		} catch (Exception e) { return replyErr(src, e); }
	}

	static net.minecraft.world.inventory.ContainerInput parseClickInput(String modeStr) {
		if (modeStr == null) return net.minecraft.world.inventory.ContainerInput.PICKUP;
		return switch (modeStr.toLowerCase()) {
			case "quickmove", "quick_move", "quick", "move", "shift" ->
				net.minecraft.world.inventory.ContainerInput.QUICK_MOVE;
			case "swap" -> net.minecraft.world.inventory.ContainerInput.SWAP;
			case "throw", "drop" -> net.minecraft.world.inventory.ContainerInput.THROW;
			case "pickupall", "pickup_all", "all" ->
				net.minecraft.world.inventory.ContainerInput.PICKUP_ALL;
			case "clone", "middle" -> net.minecraft.world.inventory.ContainerInput.CLONE;
			case "craft", "quickcraft", "quick_craft" ->
				net.minecraft.world.inventory.ContainerInput.QUICK_CRAFT;
			default -> net.minecraft.world.inventory.ContainerInput.PICKUP;
		};
	}

	private static int dropSel(FabricClientCommandSource src, boolean all) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) return reply(src, Json.err("no_player"));
			var inv = mc.player.getInventory();
			int sel = inv.getSelectedSlot();
			var stack = inv.getItem(sel);
			if (stack.isEmpty()) {
				JsonObject o = Json.ok();
				o.addProperty("action", "drop");
				o.addProperty("note", "empty_hand");
				return reply(src, o);
			}
			int count = all ? stack.getCount() : 1;
			var removed = inv.removeItem(sel, count);
			if (!removed.isEmpty()) mc.player.drop(removed, false);
			JsonObject o = Json.ok();
			o.addProperty("action", "drop");
			o.addProperty("all", all);
			o.addProperty("count", count);
			return reply(src, o);
		} catch (Exception e) { return replyErr(src, e); }
	}
}
