package com.deathreturn.backup;

import com.deathreturn.DeathReturnConfig;
import com.deathreturn.DeathReturnMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 检查点（世界目录快照）的生命周期管理。
 *
 * /save 流程（在服务器线程上执行，期间世界短暂停顿）：
 *   1. 暂停自动保存（等价 /save-off）
 *   2. saveEverything 强制落盘全部区块与玩家数据（等价 /save-all flush）
 *   3. 恢复自动保存
 *   4. 世界目录完整复制到临时目录，成功后原子替换旧检查点
 *
 * 回档流程（在服务器完全停止后由客户端调用）：
 *   把检查点目录镜像回世界目录（先删后拷，跳过 session.lock）。
 */
public final class BackupManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static MinecraftServer server;
	private static Path worldDir;
	private static Path backupDir;
	private static volatile boolean busy;

	private BackupManager() {}

	public static void onServerStarted(MinecraftServer s) {
		server = s;
		// LEVEL_DATA_FILE = level.dat，其父目录即世界目录
		worldDir = s.getWorldPath(LevelResource.LEVEL_DATA_FILE).getParent();
		DeathReturnConfig cfg = DeathReturnMod.config;
		backupDir = worldDir.getParent().resolve(cfg.backupFolder).resolve(worldDir.getFileName().toString());

		DeathReturnMod.LOGGER.info("Death Return attached: world={}, checkpoint={}", worldDir, backupDir);

		// 首次进入该世界时自动建立初始检查点，保证“死亡必回溯”有落脚点
		if (cfg.autoInitialBackup && !hasCheckpoint()) {
			String err = createCheckpoint("initial");
			if (err == null) {
				DeathReturnMod.LOGGER.info("Initial checkpoint created automatically");
			} else {
				DeathReturnMod.LOGGER.error("Failed to create initial checkpoint: {}", err);
			}
		}
	}

	public static boolean hasCheckpoint() {
		return backupDir != null && Files.isRegularFile(backupDir.resolve("level.dat"));
	}

	public static String levelName() {
		return worldDir != null ? worldDir.getFileName().toString() : null;
	}

	public static Path worldDir() {
		return worldDir;
	}

	public static Path backupDir() {
		return backupDir;
	}

	public static boolean isBusy() {
		return busy;
	}

	/** 检查点创建时间（毫秒），无检查点时为 -1。 */
	public static long checkpointCreatedAt() {
		Path meta = metaFile();
		if (meta != null && Files.isRegularFile(meta)) {
			try {
				var obj = GSON.fromJson(Files.readString(meta), Map.class);
				Object t = obj.get("createdAt");
				if (t instanceof Number n) return n.longValue();
			} catch (Exception ignored) {
			}
		}
		return -1;
	}

	/**
	 * 创建检查点。必须在服务器线程调用（/save 指令、SERVER_STARTED 事件均满足）。
	 *
	 * @return null 表示成功，否则为错误描述
	 */
	public static String createCheckpoint(String reason) {
		if (server == null || worldDir == null) {
			return "world not attached";
		}
		synchronized (BackupManager.class) {
			if (busy) {
				return "checkpoint IO already in progress";
			}
			busy = true;
		}
		try {
			long start = System.currentTimeMillis();

			// 1-2. 落盘：暂停自动保存 -> 全量 flush(suppressLogs=false, flush=true, sync=true) -> 恢复
			boolean prevAutoSave = server.isAutoSave();
			server.setAutoSave(false);
			try {
				server.saveEverything(false, true, true);
			} finally {
				server.setAutoSave(prevAutoSave);
			}

			// 3. 复制到临时目录，成功后替换旧检查点（崩溃安全：旧检查点在新检查点完成前不会被破坏）
			Path tmp = backupDir.resolveSibling(backupDir.getFileName().toString() + ".tmp");
			WorldFileOps.deleteTree(tmp);
			WorldFileOps.copyTree(worldDir, tmp, null);

			// 复制完整性校验：文件数不一致视为失败，绝不落下一个残缺的检查点
			long srcCount = WorldFileOps.countFiles(worldDir);
			long dstCount = WorldFileOps.countFiles(tmp);
			if (srcCount != dstCount) {
				WorldFileOps.deleteTree(tmp);
				throw new IOException("checkpoint incomplete: copied " + dstCount + " of " + srcCount + " files");
			}

			WorldFileOps.deleteTree(backupDir);
			Files.move(tmp, backupDir, StandardCopyOption.REPLACE_EXISTING);

			long elapsed = System.currentTimeMillis() - start;
			writeMeta(reason, elapsed);
			DeathReturnMod.LOGGER.info("Checkpoint created in {} ms ({})", elapsed, reason);
			return null;
		} catch (IOException e) {
			DeathReturnMod.LOGGER.error("Checkpoint creation failed", e);
			return e.getClass().getSimpleName() + ": " + e.getMessage();
		} finally {
			busy = false;
		}
	}

	/**
	 * 回档：将检查点镜像回世界目录。必须在服务器完全停止后调用（文件句柄已全部关闭）。
	 */
	public static void restoreCheckpoint(Path targetWorldDir, WorldFileOps.ProgressListener progress) throws IOException {
		if (!hasCheckpoint()) {
			throw new IOException("no checkpoint available");
		}
		WorldFileOps.mirrorTree(backupDir, targetWorldDir, progress);
	}

	private static Path metaFile() {
		return backupDir != null ? backupDir.resolveSibling(backupDir.getFileName().toString() + ".meta.json") : null;
	}

	private static void writeMeta(String reason, long elapsedMs) {
		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("createdAt", System.currentTimeMillis());
		meta.put("reason", reason);
		meta.put("elapsedMs", elapsedMs);
		meta.put("levelName", levelName());
		try {
			Files.writeString(metaFile(), GSON.toJson(meta));
		} catch (IOException e) {
			DeathReturnMod.LOGGER.warn("Failed to write checkpoint meta", e);
		}
	}
}
