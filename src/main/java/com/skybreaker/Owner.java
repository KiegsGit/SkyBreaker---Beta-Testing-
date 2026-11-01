package com.skybreaker;

import io.netty.channel.local.LocalAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.SocketAddress;

/**
 * Decides who the "server owner" is. This is NOT the same as an operator:
 * <ul>
 *   <li>singleplayer / LAN: the player hosting the world (the one on the local connection),</li>
 *   <li>dedicated server: the console, plus every UUID listed in {@code owners} in config/skybreaker.json.</li>
 * </ul>
 * Only UUIDs are trusted, never names. Every owner-only action calls {@link #require} again on the server,
 * so a modified client cannot bypass it.
 */
public final class Owner {
	public static boolean isOwner(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		if (SkybreakerConfig.get().owners.contains(player.getUUID().toString())) {
			return true;
		}
		MinecraftServer server = player.level().getServer();
		if (server != null && !server.isDedicatedServer()) {
			return isLocalConnection(player);
		}
		return false;
	}

	private static boolean isLocalConnection(ServerPlayer player) {
		try {
			SocketAddress addr = player.connection.getRemoteAddress();
			return addr instanceof LocalAddress;
		} catch (Throwable t) {
			return false;
		}
	}

	/** Server-side gate for every owner-only packet / command. Warns the player and (optionally) the owners on failure. */
	private static final java.util.Map<java.util.UUID, Long> LAST_WARN = new java.util.HashMap<>();

	public static boolean require(ServerPlayer player, String what) {
		if (isOwner(player)) {
			return true;
		}
		if (player != null) {
			long nowMs = System.currentTimeMillis();
			Long last = LAST_WARN.get(player.getUUID());
			if (last != null && nowMs - last < 5000) {
				return false; // throttle: no log / owner spam from a client that keeps retrying
			}
			LAST_WARN.put(player.getUUID(), nowMs);
			player.sendSystemMessage(Component.literal("\u00a7c[Skybreaker] Only the server owner can do that. This attempt was logged."));
			SkybreakerMod.LOGGER.warn("Blocked non-owner {} ({}) from: {}", player.getName().getString(), player.getUUID(), what);
			if (SkybreakerConfig.get().warnOwnerOnBypass) {
				MinecraftServer server = player.level().getServer();
				if (server != null) {
					for (ServerPlayer p : server.getPlayerList().getPlayers()) {
						if (p != player && isOwner(p)) {
							p.sendSystemMessage(Component.literal("\u00a76[Skybreaker] \u00a7e" + player.getName().getString()
								+ " tried to use an owner-only function (" + what + ") and was blocked."));
						}
					}
				}
			}
		}
		return false;
	}

	private Owner() {
	}
}
