package com.skybreaker;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where strikes are allowed to act: banned dimensions, protected zones (e.g. spawn) and claim mods.
 * Claim mods are asked through the optional "Common Protection API" (eu.pb4.common.protection.api.CommonProtection),
 * which FTB Chunks, Open Parties and Claims and others implement. It is called by reflection, so it is never required.
 * Also carries "who is striking and how strong" for the strike that is currently ticking.
 */
public final class Guard {
	private static UUID currentOwner = null;
	private static double currentPower = 1.0;

	public static void begin(UUID owner, double power) {
		currentOwner = owner;
		currentPower = power;
		CLAIM_CACHE.clear();
	}

	public static void end() {
		currentOwner = null;
		currentPower = 1.0;
		CLAIM_CACHE.clear();
	}

	/** Damage multiplier of the strike that is currently ticking (Overcharge Chip). */
	public static double power() {
		return currentPower;
	}

	/** "minecraft:overworld" etc. Parsed from the key's string form so it does not depend on mapping names. */
	public static String dimId(ServerLevel level) {
		String s = level.dimension().toString(); // ResourceKey[minecraft:dimension / minecraft:overworld]
		int i = s.lastIndexOf("/ ");
		int j = s.lastIndexOf(']');
		return (i >= 0 && j > i) ? s.substring(i + 2, j).trim() : s;
	}

	public static boolean dimensionBlocked(ServerLevel level) {
		return SkybreakerConfig.get().blockedDimensions.contains(dimId(level));
	}

	/** True if the point lies inside any protected zone of its dimension. */
	public static boolean inZone(ServerLevel level, double x, double z) {
		String dim = dimId(level);
		for (String zone : SkybreakerConfig.get().protectedZones) {
			String[] t = zone.trim().split("\\s+");
			if (t.length != 4 || !t[0].equals(dim)) {
				continue;
			}
			try {
				double dx = x - Double.parseDouble(t[1]);
				double dz = z - Double.parseDouble(t[2]);
				double r = Double.parseDouble(t[3]);
				if (dx * dx + dz * dz <= r * r) {
					return true;
				}
			} catch (NumberFormatException ignored) {
				// malformed entry: ignore it
			}
		}
		return false;
	}

	/** May a strike be AIMED at this point? (Used when firing.) Returns null if yes, otherwise the reason. */
	public static String aimDenied(ServerLevel level, Vec3 target) {
		if (dimensionBlocked(level)) {
			return "Strikes are banned in this dimension.";
		}
		if (inZone(level, target.x, target.z)) {
			return "This area is protected - strikes are banned here.";
		}
		return null;
	}

	/** May the running strike destroy this block? */
	public static boolean canDestroy(ServerLevel level, BlockPos pos) {
		if (dimensionBlocked(level) || inZone(level, pos.getX() + 0.5, pos.getZ() + 0.5)) {
			return false;
		}
		return claimAllows(level, pos);
	}

	/** May the running strike hurt this entity? Entities inside protected zones are safe. */
	public static boolean canHurt(Entity e) {
		if (e.level() instanceof ServerLevel sl) {
			return !dimensionBlocked(sl) && !inZone(sl, e.getX(), e.getZ());
		}
		return true;
	}

	// ---------- claim mods (Common Protection API, by reflection) ----------
	private static final Map<Long, Boolean> CLAIM_CACHE = new HashMap<>();
	private static boolean apiSearched = false;
	private static Method canBreak = null;

	private static boolean claimAllows(ServerLevel level, BlockPos pos) {
		if (!SkybreakerConfig.get().respectClaims || currentOwner == null) {
			return true;
		}
		if (!apiSearched) {
			apiSearched = true;
			try {
				Class<?> api = Class.forName("eu.pb4.common.protection.api.CommonProtection");
				for (Method m : api.getMethods()) {
					if (m.getName().equals("canBreakBlock") && m.getParameterCount() == 4) {
						canBreak = m;
					}
				}
				if (canBreak != null) {
					SkybreakerMod.LOGGER.info("Common Protection API found: claims will be respected.");
				}
			} catch (Throwable t) {
				// not installed: nothing to ask
			}
		}
		if (canBreak == null) {
			return true;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(currentOwner);
		if (owner == null) {
			return true;
		}
		long key = (((long) pos.getX() >> 4) & 0xFFFFFFFFL) | ((((long) pos.getZ() >> 4) & 0xFFFFFFFFL) << 32);
		Boolean cached = CLAIM_CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		boolean ok = true;
		try {
			Object profile = Player.class.getMethod("getGameProfile").invoke(owner);
			Object res = canBreak.invoke(null, level, pos, profile, owner);
			ok = !(res instanceof Boolean b) || b;
		} catch (Throwable t) {
			SkybreakerMod.LOGGER.warn("Common Protection API call failed, ignoring claims: {}", t.toString());
			canBreak = null;
		}
		CLAIM_CACHE.put(key, ok);
		return ok;
	}

	private Guard() {
	}
}
