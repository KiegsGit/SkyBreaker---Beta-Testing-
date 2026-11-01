package com.skybreaker.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skybreaker.ConfigAction;
import com.skybreaker.ConfigView;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The "Sky Breaker Config" button on the Esc menu and its screens (menu -> Config / Commands).
 * The button is visible to everybody, but the SERVER decides: a non-owner just gets warned (and it is logged).
 * Every change is sent to the server, which re-checks the owner on each packet.
 */
final class ConfigUi {
	/** Latest settings from the server (only ever received by the owner). */
	static List<Row> rows = new ArrayList<>();
	static String pendingStatus = "";

	record Row(String key, String label, String cat, char type, String value, double min, double max, double step, List<String> options) {
	}

	static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof PauseScreen) {
				Screens.getWidgets(screen).add(Button.builder(Component.literal("Sky Breaker Config"), b -> requestOpen())
					.bounds(6, 6, 130, 20).build());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(ConfigView.TYPE, (payload, context) -> receive(payload));
	}

	private static void requestOpen() {
		if (!ClientPlayNetworking.canSend(ConfigAction.TYPE)) {
			pendingStatus = "This server does not have Skybreaker installed.";
			return;
		}
		ClientPlayNetworking.send(new ConfigAction(ConfigAction.OPEN, "", ""));
	}

	static void send(String action, String key, String value) {
		if (ClientPlayNetworking.canSend(ConfigAction.TYPE)) {
			ClientPlayNetworking.send(new ConfigAction(action, key, value));
		}
	}

	private static void receive(ConfigView v) {
		Minecraft mc = Minecraft.getInstance();
		switch (v.mode()) {
			case ConfigView.OPEN -> {
				rows = parse(v.text());
				mc.gui.setScreen(new MenuScreen());
			}
			case ConfigView.REFRESH -> {
				rows = parse(v.text());
				if (mc.gui.screen() instanceof OwnerScreen s) {
					s.onData();
				}
			}
			default -> {
				if (mc.gui.screen() instanceof OwnerScreen s) {
					s.setStatus(v.text());
				} else {
					pendingStatus = v.text();
				}
			}
		}
	}

	static List<Row> parse(String json) {
		List<Row> out = new ArrayList<>();
		try {
			JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
			for (JsonElement el : arr) {
				JsonObject o = el.getAsJsonObject();
				List<String> opts = new ArrayList<>();
				if (o.has("o")) {
					for (JsonElement e : o.getAsJsonArray("o")) {
						opts.add(e.getAsString());
					}
				}
				out.add(new Row(o.get("k").getAsString(), o.get("l").getAsString(), o.get("c").getAsString(), o.get("t").getAsString().charAt(0),
					o.get("v").getAsString(), o.get("min").getAsDouble(), o.get("max").getAsDouble(), o.get("s").getAsDouble(), opts));
			}
		} catch (Exception e) {
			// malformed data: show nothing rather than crash
		}
		return out;
	}

	// ------------------------------------------------------------------------------------------------
	abstract static class OwnerScreen extends Screen {
		protected StringWidget status;
		protected String statusText = pendingStatus;

		protected OwnerScreen(String title) {
			super(Component.literal(title));
			pendingStatus = "";
		}

		void setStatus(String text) {
			statusText = text;
			if (status != null) {
				status.setMessage(Component.literal(text));
			}
		}

		void onData() {
		}

		protected void header(String text) {
			addRenderableWidget(new StringWidget(width / 2 - 150, 10, 300, 12, Component.literal(text), font));
		}

		protected void statusLine(int y) {
			status = new StringWidget(width / 2 - 200, y, 400, 12, Component.literal(statusText), font);
			addRenderableWidget(status);
		}

	}

	/** The pop-up after pressing the button: choose Config or Commands. */
	static final class MenuScreen extends OwnerScreen {
		MenuScreen() {
			super("Sky Breaker");
		}

		@Override
		protected void init() {
			int cx = width / 2;
			int y = height / 2 - 40;
			header("Sky Breaker - owner panel");
			addRenderableWidget(Button.builder(Component.literal("Config"), b -> Minecraft.getInstance().gui.setScreen(new SettingsScreen(0)))
				.bounds(cx - 100, y, 200, 20).build());
			addRenderableWidget(Button.builder(Component.literal("Commands"), b -> Minecraft.getInstance().gui.setScreen(new CommandsScreen()))
				.bounds(cx - 100, y + 26, 200, 20).build());
			addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
				.bounds(cx - 100, y + 62, 200, 20).build());
			statusLine(y + 92);
		}
	}

	/** Paged settings editor. Toggles flip, option buttons cycle, numbers have - / + buttons. */
	static final class SettingsScreen extends OwnerScreen {
		private static final int PER_PAGE = 6;
		private int page;

		SettingsScreen(int page) {
			super("Sky Breaker Config");
			this.page = page;
		}

		private List<List<Row>> pages() {
			Map<String, List<Row>> byCat = new LinkedHashMap<>();
			for (Row r : rows) {
				byCat.computeIfAbsent(r.cat(), k -> new ArrayList<>()).add(r);
			}
			List<List<Row>> pages = new ArrayList<>();
			for (List<Row> list : byCat.values()) {
				for (int i = 0; i < list.size(); i += PER_PAGE) {
					pages.add(list.subList(i, Math.min(list.size(), i + PER_PAGE)));
				}
			}
			return pages;
		}

		@Override
		void onData() {
			rebuildWidgets();
		}

		@Override
		protected void init() {
			List<List<Row>> pages = pages();
			if (pages.isEmpty()) {
				header("No settings received");
				addRenderableWidget(Button.builder(Component.literal("Back"), b -> Minecraft.getInstance().gui.setScreen(new MenuScreen()))
					.bounds(width / 2 - 100, height / 2, 200, 20).build());
				return;
			}
			page = Math.max(0, Math.min(page, pages.size() - 1));
			List<Row> list = pages.get(page);
			header(list.get(0).cat() + "  (" + (page + 1) + "/" + pages.size() + ")");
			int x0 = width / 2 - 110;
			int y = 30;
			for (Row r : list) {
				addRow(r, x0, y);
				y += 24;
			}
			int by = Math.min(height - 54, y + 8);
			addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> go(-1)).bounds(width / 2 - 110, by, 70, 20).build());
			addRenderableWidget(Button.builder(Component.literal("Back"), b -> Minecraft.getInstance().gui.setScreen(new MenuScreen())).bounds(width / 2 - 35, by, 70, 20).build());
			addRenderableWidget(Button.builder(Component.literal("Next >"), b -> go(1)).bounds(width / 2 + 40, by, 70, 20).build());
			statusLine(by + 26);
		}

		private void go(int d) {
			int n = pages().size();
			page = (page + d + n) % n;
			rebuildWidgets();
		}

		private void addRow(Row r, int x0, int y) {
			switch (r.type()) {
				case 'b' -> {
					boolean on = Boolean.parseBoolean(r.value());
					addRenderableWidget(Button.builder(Component.literal(r.label() + ": " + (on ? "\u00a7aON" : "\u00a7cOFF")),
						b -> send(ConfigAction.SET, r.key(), String.valueOf(!on))).bounds(x0, y, 220, 20).build());
				}
				case 'e' -> addRenderableWidget(Button.builder(Component.literal(r.label() + ": \u00a7e" + r.value()), b -> {
					int i = r.options().indexOf(r.value());
					send(ConfigAction.SET, r.key(), r.options().get((i + 1) % r.options().size()));
				}).bounds(x0, y, 220, 20).build());
				default -> {
					double cur = 0;
					try {
						cur = Double.parseDouble(r.value());
					} catch (NumberFormatException ignored) {
					}
					final double value = cur;
					addRenderableWidget(Button.builder(Component.literal("-"), b -> send(ConfigAction.SET, r.key(), fmt(value - r.step())))
						.bounds(x0, y, 20, 20).build());
					addRenderableWidget(Button.builder(Component.literal(r.label() + ": \u00a7e" + fmt(value)), b -> {
					}).bounds(x0 + 22, y, 176, 20).build());
					addRenderableWidget(Button.builder(Component.literal("+"), b -> send(ConfigAction.SET, r.key(), fmt(value + r.step())))
						.bounds(x0 + 200, y, 20, 20).build());
				}
			}
		}

		private static String fmt(double v) {
			return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.2f", v);
		}
	}

	/** One-click owner commands. Longer ones (zones, allowlist, owners, dimensions) are typed: see the hint line. */
	static final class CommandsScreen extends OwnerScreen {
		CommandsScreen() {
			super("Sky Breaker Commands");
		}

		@Override
		protected void init() {
			int cx = width / 2;
			int y = 30;
			header("Sky Breaker Commands");
			y = cmd("Reload config  (/skybreaker reload)", "reload", cx, y);
			y = cmd("Show status", "status", cx, y);
			y = cmd("Stop all active strikes", "cancelall", cx, y);
			y = cmd("Clear all remote cooldowns", "clearcd", cx, y);
			y = cmd("Protect my position (radius 64)", "zonehere", cx, y);
			addRenderableWidget(Button.builder(Component.literal("Back"), b -> Minecraft.getInstance().gui.setScreen(new MenuScreen()))
				.bounds(cx - 110, y + 6, 220, 20).build());
			statusLine(y + 34);
			addRenderableWidget(new StringWidget(cx - 200, y + 50, 400, 12,
				Component.literal("\u00a77Typed: /skybreaker zone | allow | owner | dimension | set"), font));
		}

		private int cmd(String label, String key, int cx, int y) {
			addRenderableWidget(Button.builder(Component.literal(label), b -> send(ConfigAction.CMD, key, ""))
				.bounds(cx - 110, y, 220, 20).build());
			return y + 24;
		}
	}

	private ConfigUi() {
	}
}
