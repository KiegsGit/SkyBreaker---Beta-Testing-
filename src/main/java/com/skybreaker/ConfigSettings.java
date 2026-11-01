package com.skybreaker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * The settings that can be edited from the in-game "Sky Breaker Config" screen and /skybreaker set.
 * Server-side only logic: values are parsed, clamped, validated and saved here, so a hacked client cannot
 * write anything that is not on this list or outside its range.
 */
public final class ConfigSettings {
	public record Setting(String key, String label, String cat, char type, double min, double max, double step, String[] options) {
	}

	public static final List<Setting> ALL = new ArrayList<>();

	private static void b(String cat, String key, String label) {
		ALL.add(new Setting(key, label, cat, 'b', 0, 1, 1, null));
	}

	private static void i(String cat, String key, String label, double min, double max, double step) {
		ALL.add(new Setting(key, label, cat, 'i', min, max, step, null));
	}

	private static void d(String cat, String key, String label, double min, double max, double step) {
		ALL.add(new Setting(key, label, cat, 'd', min, max, step, null));
	}

	private static void e(String cat, String key, String label, String... options) {
		ALL.add(new Setting(key, label, cat, 'e', 0, 0, 1, options));
	}

	static {
		String a = "Access & limits";
		e(a, "firePermission", "Who can fire", "EVERYONE", "CREATIVE_ONLY", "ALLOWLIST", "OWNER_ONLY");
		e(a, "cutsceneMode", "Cutscene for", "FIRER", "OWNER", "EVERYONE");
		b(a, "respectClaims", "Respect claim mods");
		b(a, "warnOwnerOnBypass", "Warn owner on bypass");
		i(a, "maxActiveStrikes", "Max strikes (server)", 1, 50, 1);
		i(a, "maxActiveStrikesPerPlayer", "Max strikes (player)", 1, 10, 1);
		d(a, "globalCooldownSeconds", "Global cooldown (s)", 0, 600, 1);

		String s = "Safety & damage";
		b(s, "terrainDestruction", "Terrain destruction");
		b(s, "protectBlockEntities", "Protect chests etc.");
		b(s, "damagePlayers", "Damage players");
		b(s, "damageOwner", "Damage the shooter");
		i(s, "maxBlocksPerStrike", "Max blocks / strike", 0, 50000, 500);
		d(s, "maxTargetRange", "Max target range", 8, 128, 8);
		d(s, "maxBlockHardness", "Max block hardness", 0, 100, 5);

		String t = "Terrain per weapon";
		b(t, "railgunTerrain", "Railgun terrain");
		b(t, "laserTerrain", "Laser terrain");
		b(t, "meteorTerrain", "Meteor terrain");
		b(t, "blackHoleTerrain", "Singularity terrain");
		b(t, "nukeTerrain", "Nuke terrain");
		b(t, "trackTerrain", "Tracking terrain");
		b(t, "napalmFire", "Napalm places fire");
		b(t, "empRedstone", "EMP wrecks redstone");

		String f = "Cutscene & effects";
		b(f, "cinematic", "Cutscene HUD");
		b(f, "cinematicCamera", "Cutscene camera");
		b(f, "cameraShake", "Camera shake");
		b(f, "slowMotion", "Slow motion (solo)");
		b(f, "lightning", "Lightning");
		b(f, "debris", "Debris");
		d(f, "particleScale", "Particle amount", 0.2, 1.0, 0.1);

		String m = "Ammo & chips";
		b(m, "ammoEnabled", "Ammo required");
		d(m, "chipCooldownReduction", "Targeting chip bonus", 0, 0.9, 0.05);
		d(m, "overchargeDamageBonus", "Overcharge bonus", 0, 2.0, 0.05);
		d(m, "efficiencyFreeShotChance", "Efficiency free-shot", 0, 0.9, 0.05);

		String c = "Cooldowns (s)";
		d(c, "railgunCooldownSeconds", "Orbital Railgun", 0, 600, 5);
		d(c, "laserCooldownSeconds", "Satellite Laser", 0, 600, 5);
		d(c, "meteorCooldownSeconds", "Meteor Barrage", 0, 600, 5);
		d(c, "blackHoleCooldownSeconds", "Singularity Cannon", 0, 600, 5);
		d(c, "empCooldownSeconds", "EMP Pulse", 0, 600, 5);
		d(c, "napalmCooldownSeconds", "Orbital Napalm", 0, 600, 5);
		d(c, "nukeCooldownSeconds", "Tactical Nuke", 0, 1200, 10);
		d(c, "trackCooldownSeconds", "Tracking Strike", 0, 600, 5);
	}

	private static Setting find(String key) {
		for (Setting st : ALL) {
			if (st.key().equals(key)) {
				return st;
			}
		}
		return null;
	}

	/** Reads the current value of a setting as text. */
	private static String read(Setting st) {
		try {
			Field f = SkybreakerConfig.class.getField(st.key());
			Object v = f.get(SkybreakerConfig.get());
			return String.valueOf(v);
		} catch (ReflectiveOperationException e) {
			return "";
		}
	}

	/** JSON snapshot sent to the owner's screen. */
	public static String snapshot() {
		JsonArray arr = new JsonArray();
		for (Setting st : ALL) {
			JsonObject o = new JsonObject();
			o.addProperty("k", st.key());
			o.addProperty("l", st.label());
			o.addProperty("c", st.cat());
			o.addProperty("t", String.valueOf(st.type()));
			o.addProperty("v", read(st));
			o.addProperty("min", st.min());
			o.addProperty("max", st.max());
			o.addProperty("s", st.step());
			if (st.options() != null) {
				JsonArray opts = new JsonArray();
				for (String op : st.options()) {
					opts.add(op);
				}
				o.add("o", opts);
			}
			arr.add(o);
		}
		return arr.toString();
	}

	/** Parses, clamps and stores one value, then validates and saves. Returns an error text or null on success. */
	public static String apply(String key, String raw) {
		Setting st = find(key);
		if (st == null) {
			return "Unknown setting: " + key;
		}
		try {
			Field f = SkybreakerConfig.class.getField(key);
			SkybreakerConfig cfg = SkybreakerConfig.get();
			switch (st.type()) {
				case 'b' -> f.setBoolean(cfg, Boolean.parseBoolean(raw.trim()));
				case 'i' -> f.setInt(cfg, (int) Math.round(Math.max(st.min(), Math.min(st.max(), Double.parseDouble(raw.trim())))));
				case 'd' -> {
					double v = Math.max(st.min(), Math.min(st.max(), Double.parseDouble(raw.trim())));
					v = Math.round(v * 1000.0) / 1000.0;
					if (f.getType() == float.class) {
						f.setFloat(cfg, (float) v);
					} else {
						f.setDouble(cfg, v);
					}
				}
				case 'e' -> {
					String match = null;
					for (String op : st.options()) {
						if (op.equalsIgnoreCase(raw.trim())) {
							match = op;
						}
					}
					if (match == null) {
						return "Allowed values: " + String.join(", ", st.options());
					}
					f.set(cfg, match);
				}
				default -> {
					return "Bad setting type";
				}
			}
			cfg.validate();
			SkybreakerConfig.save();
			return null;
		} catch (NumberFormatException e) {
			return "Not a valid number: " + raw;
		} catch (ReflectiveOperationException e) {
			return "Could not set " + key;
		}
	}

	private ConfigSettings() {
	}
}
