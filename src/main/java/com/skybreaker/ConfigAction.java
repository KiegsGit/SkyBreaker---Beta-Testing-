package com.skybreaker;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server request from the Sky Breaker Config screen. The server re-checks that the sender is the owner EVERY time. */
public record ConfigAction(String action, String key, String value) implements CustomPacketPayload {
	public static final String OPEN = "OPEN";
	public static final String SET = "SET";
	public static final String CMD = "CMD";

	public static final CustomPacketPayload.Type<ConfigAction> TYPE = new CustomPacketPayload.Type<>(SkybreakerMod.id("config_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ConfigAction> CODEC = CustomPacketPayload.codec(ConfigAction::write, ConfigAction::new);

	public ConfigAction(RegistryFriendlyByteBuf buf) {
		this(buf.readUtf(32), buf.readUtf(64), buf.readUtf(256));
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeUtf(action, 32);
		buf.writeUtf(key, 64);
		buf.writeUtf(value, 256);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
