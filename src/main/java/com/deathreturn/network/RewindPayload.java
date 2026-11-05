package com.deathreturn.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 -> 客户端：通知客户端立即开始回溯流程。
 *
 * @param levelName 世界目录名（saves 下的一级目录名），用于回档后重新进入
 * @param reason    触发原因："death" 或 "manual"
 */
public record RewindPayload(String levelName, String reason) implements CustomPacketPayload {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("deathreturn", "rewind");
	public static final CustomPacketPayload.Type<RewindPayload> TYPE = new CustomPacketPayload.Type<>(ID);

	public static final StreamCodec<ByteBuf, RewindPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, RewindPayload::levelName,
			ByteBufCodecs.STRING_UTF8, RewindPayload::reason,
			RewindPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
