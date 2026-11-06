package com.deathreturn;

import com.deathreturn.backup.BackupManager;
import com.deathreturn.command.ModCommands;
import com.deathreturn.network.RewindPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 死亡回归：死亡时整个世界回溯到上次 /save 的检查点。
 *
 * 服务端（含单人内置服务器）负责：检查点的创建（文件级快照）与死亡检测；
 * 客户端负责：断开连接、替换世界文件、自动重新进入世界。
 */
public class DeathReturnMod implements ModInitializer {
	public static final String MOD_ID = "deathreturn";
	/** 构建标识：游戏内 /deathreturn 会显示，用于确认加载的是新版 jar */
	public static final String VERSION = "1.0.1";
	public static final Logger LOGGER = LoggerFactory.getLogger("DeathReturn");

	public static DeathReturnConfig config;

	@Override
	public void onInitialize() {
		config = DeathReturnConfig.load();

		ModCommands.register();
		PayloadTypeRegistry.clientboundPlay().register(RewindPayload.TYPE, RewindPayload.CODEC);

		ServerLifecycleEvents.SERVER_STARTED.register(BackupManager::onServerStarted);

		ServerPlayerEvents.ALLOW_DEATH.register((player, damageSource, amount) -> {
			MinecraftServer server = player.level().getServer();
			if (!config.deathRewind || server == null || !server.isSingleplayer()) {
				return true; // 仅单人世界自动回溯
			}
			if (!server.isSingleplayerOwner(player.nameAndId())) {
				return true; // 只有房主本人的死亡触发回溯
			}
			if (BackupManager.isBusy()) {
				LOGGER.warn("Player died while checkpoint IO is running, skipping rewind");
				return true;
			}
			String levelName = BackupManager.levelName();
			if (levelName == null || !BackupManager.hasCheckpoint()) {
				LOGGER.info("Player died but no checkpoint exists yet, vanilla death applies");
				return true;
			}
			LOGGER.info("Player died - telling client to rewind to checkpoint");
			ServerPlayNetworking.send(player, new RewindPayload(levelName, "death"));
			return true; // 死亡在服务端照常进行，客户端收到包后会断开并回档
		});
	}
}
