package com.skybreaker;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> owner: open the menu (OPEN), refresh the settings (REFRESH) or show a message (MESSAGE). */
public record ConfigView(int mode, String text) implements CustomPacketPayload {
	public static final int OPEN = 0;
	public static final int REFRESH = 1;
	public static final int MESSAGE = 2;

	public static final CustomPacketPayload.Type<ConfigView> TYPE = new CustomPacketPayload.Type<>(SkybreakerMod.id("config_view"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ConfigView> CODEC = CustomPacketPayload.codec(ConfigView::write, ConfigView::new);

	public ConfigView(RegistryFriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readUtf(32767));
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(mode);
		buf.writeUtf(text, 32767);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
