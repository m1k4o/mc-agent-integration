package com.mcai.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-memory ring buffers for HUD state the agent reads instead of screenshots:
 * chat messages, titles/subtitles/actionbar, and generic events.
 */
public final class HudBuffer {
	public record Entry(long t, String kind, String text, String raw) {}

	private static final int CAP = 200;
	private static final Deque<Entry> CHAT = new ArrayDeque<>();
	private static final Deque<Entry> EVENTS = new ArrayDeque<>();

	private static volatile String title = "";
	private static volatile String subtitle = "";
	private static volatile String actionbar = "";
	private static volatile long titleT = 0;
	private static volatile long actionbarT = 0;

	private HudBuffer() {}

	private static synchronized void push(Deque<Entry> q, Entry e) {
		q.addLast(e);
		while (q.size() > CAP) q.removeFirst();
	}

	public static void chat(String text, String raw) {
		push(CHAT, new Entry(System.currentTimeMillis(), "chat", text, raw));
	}

	public static void event(String kind, String text) {
		push(EVENTS, new Entry(System.currentTimeMillis(), kind, text, text));
	}

	public static void setTitle(String t, String sub) {
		if (t != null) { title = t; titleT = System.currentTimeMillis(); }
		if (sub != null) { subtitle = sub; titleT = System.currentTimeMillis(); }
	}

	public static void setActionbar(String t) {
		if (t != null) { actionbar = t; actionbarT = System.currentTimeMillis(); }
	}

	public static synchronized JsonArray chatJson(int limit) {
		List<Entry> all = new ArrayList<>(CHAT);
		int from = Math.max(0, all.size() - Math.max(1, limit));
		JsonArray arr = new JsonArray();
		for (int i = from; i < all.size(); i++) {
			Entry e = all.get(i);
			JsonObject o = new JsonObject();
			o.addProperty("t", e.t());
			o.addProperty("kind", e.kind());
			o.addProperty("text", e.text());
			arr.add(o);
		}
		return arr;
	}

	public static synchronized JsonArray eventsJson(int limit) {
		List<Entry> all = new ArrayList<>(EVENTS);
		int from = Math.max(0, all.size() - Math.max(1, limit));
		JsonArray arr = new JsonArray();
		for (int i = from; i < all.size(); i++) {
			Entry e = all.get(i);
			JsonObject o = new JsonObject();
			o.addProperty("t", e.t());
			o.addProperty("kind", e.kind());
			o.addProperty("text", e.text());
			arr.add(o);
		}
		return arr;
	}

	public static JsonObject titlesJson() {
		JsonObject o = new JsonObject();
		o.addProperty("title", title);
		o.addProperty("subtitle", subtitle);
		o.addProperty("actionbar", actionbar);
		o.addProperty("titleT", titleT);
		o.addProperty("actionbarT", actionbarT);
		return o;
	}

	public static synchronized int chatSize() { return CHAT.size(); }
	public static synchronized void clear() { CHAT.clear(); EVENTS.clear(); }
}
