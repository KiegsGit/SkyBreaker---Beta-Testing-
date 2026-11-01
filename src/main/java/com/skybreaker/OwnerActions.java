package com.skybreaker;

import net.minecraft.server.level.ServerPlayer;

/** Owner-only actions shared by the /skybreaker command and the in-game Commands screen. Callers must check {@link Owner} first. */
public final class OwnerActions {
	public static String reload() {
		SkybreakerConfig.load();
		return "Config reloaded from config/skybreaker.json.";
	}

	public static String cancelAll() {
		return "Stopped " + StrikeManager.cancelAll() + " active strike(s).";
	}

	public static String clearCooldowns() {
		StrikeItem.clearCooldowns();
		return "All remote cooldowns cleared.";
	}

	public static String zoneHere(ServerPlayer p, int radius) {
		radius = Math.max(4, Math.min(radius, 2000));
		SkybreakerConfig cfg = SkybreakerConfig.get();
		cfg.protectedZones.add(Guard.dimId(p.level()) + " " + Math.round(p.getX()) + " " + Math.round(p.getZ()) + " " + radius);
		cfg.validate();
		SkybreakerConfig.save();
		return "Protected zone #" + cfg.protectedZones.size() + " created here (radius " + radius + "). Strikes are banned inside it.";
	}

	public static String status() {
		SkybreakerConfig c = SkybreakerConfig.get();
		return "Active strikes: " + StrikeManager.activeCount() + "/" + c.maxActiveStrikes
			+ " | fire permission: " + c.firePermission
			+ " | cutscene: " + c.cutsceneMode
			+ " | ammo: " + (c.ammoEnabled ? "on" : "off")
			+ " | zones: " + c.protectedZones.size()
			+ " | banned dimensions: " + c.blockedDimensions.size();
	}

	private OwnerActions() {
	}
}
