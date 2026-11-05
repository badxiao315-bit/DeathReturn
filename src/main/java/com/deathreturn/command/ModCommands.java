package com.deathreturn.command;

import com.deathreturn.DeathReturnMod;
import com.deathreturn.backup.BackupManager;
import com.deathreturn.network.RewindPayload;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * /save  - 创建检查点（覆盖上一个）
 * /load  - 回溯到检查点（仅单人世界）
 * /deathreturn status - 查看检查点状态
 */
public final class ModCommands {
	private static final SimpleCommandExceptionType NO_CHECKPOINT =
			new SimpleCommandExceptionType(Component.translatable("deathreturn.checkpoint.none"));
	private static final SimpleCommandExceptionType SINGLEPLAYER_ONLY =
			new SimpleCommandExceptionType(Component.translatable("deathreturn.load.singleplayer_only"));

	private static final DateTimeFormatter TIME_FMT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

	private ModCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> {
			// 与 /help 同级：无任何权限要求（/load 的单人房主限制在执行逻辑里）
			dispatcher.register(Commands.literal("save")
					.executes(ctx -> save(ctx.getSource())));

			dispatcher.register(Commands.literal("load")
					.executes(ctx -> load(ctx.getSource())));

			dispatcher.register(Commands.literal("deathreturn")
					.executes(ctx -> version(ctx.getSource()))
					.then(Commands.literal("status").executes(ctx -> status(ctx.getSource()))));
		});
	}

	private static int version(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("§6[死亡回归]§r Death Return " + DeathReturnMod.VERSION), false);
		return 1;
	}

	private static int save(CommandSourceStack source) {
		String err = BackupManager.createCheckpoint("manual");
		if (err != null) {
			source.sendFailure(Component.translatable("deathreturn.checkpoint.failed", err));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("deathreturn.checkpoint.created"), false);
		return 1;
	}

	private static int load(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		if (!server.isSingleplayer() || !server.isSingleplayerOwner(player.nameAndId())) {
			throw SINGLEPLAYER_ONLY.create();
		}
		if (!BackupManager.hasCheckpoint()) {
			throw NO_CHECKPOINT.create();
		}
		if (BackupManager.isBusy()) {
			source.sendFailure(Component.translatable("deathreturn.checkpoint.busy"));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("deathreturn.load.started"), false);
		ServerPlayNetworking.send(player, new RewindPayload(BackupManager.levelName(), "manual"));
		return 1;
	}

	private static int status(CommandSourceStack source) {
		if (BackupManager.hasCheckpoint()) {
			long created = BackupManager.checkpointCreatedAt();
			String time = created > 0 ? TIME_FMT.format(Instant.ofEpochMilli(created)) : "?";
			source.sendSuccess(() -> Component.translatable("deathreturn.status.exists", time), false);
		} else {
			source.sendSuccess(() -> Component.translatable("deathreturn.status.none"), false);
		}
		return 1;
	}
}
