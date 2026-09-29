package com.mcai.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/** Recipe-book queries and recipe placement (crafting support). */
public final class Recipes {
	private Recipes() {}

	private static String itemIdOf(net.minecraft.world.item.Item item) {
		try { return BuiltInRegistries.ITEM.getKey(item).toString(); }
		catch (Exception e) { return "unknown"; }
	}

	private static boolean matches(String id, String want) {
		String w = want.toLowerCase();
		String lid = id.toLowerCase();
		if (lid.equals(w)) return true;
		int colon = lid.indexOf(':');
		String path = colon >= 0 ? lid.substring(colon + 1) : lid;
		if (path.equals(w)) return true;
		return lid.endsWith("/" + w) || path.endsWith("/" + w);
	}

	public static JsonObject recipeJson(Minecraft mc, String want, int limit) {
		JsonObject o = Json.obj();
		if (mc.player == null || mc.level == null) {
			o.addProperty("ok", false);
			o.addProperty("error", "no_world");
			return o;
		}
		if (want == null || want.isBlank()) {
			o.addProperty("ok", false);
			o.addProperty("error", "usage: recipe <item>");
			return o;
		}
		int lim = Math.max(1, Math.min(limit <= 0 ? 10 : limit, 25));
		try {
			var ctx = SlotDisplayContext.fromLevel(mc.level);
			JsonArray arr = new JsonArray();
			int scanned = 0;
			outer:
			for (var col : mc.player.getRecipeBook().getCollections()) {
				for (RecipeDisplayEntry e : col.getRecipes()) {
					scanned++;
					List<net.minecraft.world.item.ItemStack> results;
					try { results = e.display().result().resolveForStacks(ctx); }
					catch (Exception ex) { continue; }
					boolean hit = false;
					JsonArray resultIds = new JsonArray();
					for (var s : results) {
						if (s == null || s.isEmpty()) continue;
						String id = itemIdOf(s.getItem());
						if (resultIds.size() < 4) resultIds.add(id);
						if (!hit && matches(id, want)) hit = true;
					}
					if (!hit) continue;
					JsonObject ro = new JsonObject();
					ro.addProperty("recipeId", e.id().index());
					ro.add("results", resultIds);
					try { ro.addProperty("category", e.category().toString()); }
					catch (Exception ignored) {}
					try { ro.addProperty("craftable", col.isCraftable(e.id())); }
					catch (Exception ignored) {}
					JsonArray ings = new JsonArray();
					try {
						var reqs = e.craftingRequirements();
						if (reqs.isPresent()) {
							for (var ing : reqs.get()) {
								JsonObject io = new JsonObject();
								JsonArray opts = new JsonArray();
								int more = 0;
								try {
									var it = ing.items().iterator();
									while (it.hasNext()) {
										var h = it.next();
										if (opts.size() < 8) opts.add(itemIdOf(h.value()));
										else more++;
									}
								} catch (Exception ignored) {}
								io.add("options", opts);
								if (more > 0) io.addProperty("more", more);
								ings.add(io);
							}
						}
					} catch (Exception ignored) {}
					ro.add("ingredients", ings);
					arr.add(ro);
					if (arr.size() >= lim) break outer;
				}
			}
			o.addProperty("ok", true);
			o.addProperty("query", want);
			o.addProperty("scanned", scanned);
			o.add("recipes", arr);
		} catch (Exception e) {
			o.addProperty("ok", false);
			o.addProperty("error", e.toString());
		}
		return o;
	}

	public static JsonObject craftJson(Minecraft mc, int recipeId, boolean shift) {
		JsonObject o = Json.obj();
		if (mc.player == null || mc.gameMode == null) {
			o.addProperty("ok", false);
			o.addProperty("error", "no_world");
			return o;
		}
		try {
			var menu = mc.player.containerMenu;
			if (menu == null) {
				o.addProperty("ok", false);
				o.addProperty("error", "no_menu");
				return o;
			}
			mc.gameMode.handlePlaceRecipe(menu.containerId,
				new net.minecraft.world.item.crafting.display.RecipeDisplayId(recipeId), shift);
			o.addProperty("ok", true);
			o.addProperty("action", "craft");
			o.addProperty("recipeId", recipeId);
			o.addProperty("shift", shift);
		} catch (Exception e) {
			o.addProperty("ok", false);
			o.addProperty("error", e.toString());
		}
		return o;
	}
}
