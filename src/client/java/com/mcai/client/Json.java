package com.mcai.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class Json {
	public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private Json() {}

	public static JsonObject obj() {
		return new JsonObject();
	}

	public static JsonObject ok() {
		JsonObject o = new JsonObject();
		o.addProperty("ok", true);
		return o;
	}

	public static JsonObject err(String message) {
		JsonObject o = new JsonObject();
		o.addProperty("ok", false);
		o.addProperty("error", message);
		return o;
	}

	public static String str(JsonObject o) {
		return GSON.toJson(o);
	}
}
