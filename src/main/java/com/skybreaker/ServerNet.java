package com.skybreaker;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Server side of the Sky Breaker Config screen. Nothing here trusts the client: owner check on every packet. */
public final class ServerNet {
	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(ConfigAction.TYPE, ConfigAction.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ConfigView.TYPE, ConfigView.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ConfigAction.TYPE, (payload, context) -> handle(context.player(), payload));
	}

	private static void handle(ServerPlayer player, ConfigAction a) {
		if (!Owner.require(player, "config screen (" + a.action() + " " + a.key() + ")")) {
			return;
		}
		switch (a.action()) {
			case ConfigAction.OPEN -> ServerPlayNetworking.send(player, new ConfigView(ConfigView.OPEN, ConfigSettings.snapshot()));
			case ConfigAction.SET -> {
				String err = ConfigSettings.apply(a.key(), a.value());
				if (err != null) {
					ServerPlayNetworking.send(player, new ConfigView(ConfigView.MESSAGE, "\u00a7c" + err));
				}
				ServerPlayNetworking.send(player, new ConfigView(ConfigView.REFRESH, ConfigSettings.snapshot()));
			}
			case ConfigAction.CMD -> {
				String msg = switch (a.key()) {
					case "reload" -> OwnerActions.reload();
					case "cancelall" -> OwnerActions.cancelAll();
					case "clearcd" -> OwnerActions.clearCooldowns();
					case "zonehere" -> OwnerActions.zoneHere(player, 64);
					case "status" -> OwnerActions.status();
					default -> "Unknown action.";
				};
				ServerPlayNetworking.send(player, new ConfigView(ConfigView.MESSAGE, "\u00a7a" + msg));
				ServerPlayNetworking.send(player, new ConfigView(ConfigView.REFRESH, ConfigSettings.snapshot()));
			}
			default -> {
			}
		}
	}

	private ServerNet() {
	}
}
