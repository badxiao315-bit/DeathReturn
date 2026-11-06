package com.deathreturn.client;

import com.deathreturn.DeathReturnMod;
import com.deathreturn.backup.BackupManager;
import com.deathreturn.client.screen.RewindScreen;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 客户端回溯编排：
 *
 * 阶段 STOPPING  - 客户端线程：断开连接，mc.disconnect 会同步等待内置服务器完全停止
 *                  （期间原版自己会显示“保存世界”的过场；服务器停止后我们传入的屏幕成为当前屏幕）
 * 阶段 RESTORING - 工作线程：删除世界目录内容，把检查点复制回去（带进度）
 * 阶段 REENTERING- 客户端线程：WorldOpenFlows.openWorld 重新进入世界（原版加载屏）
 *
 * 全程玩家不会看到死亡画面或标题界面。
 */
public final class RewindController {
	public enum Phase { STOPPING, RESTORING, REENTERING, DONE, ERROR }

	private static final Logger LOGGER = LoggerFactory.getLogger("DeathReturn-Rewind");

	private static final AtomicReference<Phase> phase = new AtomicReference<>(Phase.DONE);
	private static volatile float progress = -1f; // -1 = 不确定进度
	private static volatile String error;
	private static volatile boolean active;
	private static volatile RewindScreen screen;

	private RewindController() {}

	public static Phase phase() {
		return phase.get();
	}

	public static float progress() {
		return progress;
	}

	public static String error() {
		return error;
	}

	public static boolean isActive() {
		return active;
	}

	/** 仅可在客户端主线程调用（网络包回调里已 execute 到主线程）。 */
	public static void begin(String levelName, String reason) {
		Minecraft mc = Minecraft.getInstance();
		if (active) {
			LOGGER.warn("Rewind already in progress, ignoring request");
			return;
		}
		if (mc.getSingleplayerServer() == null) {
			LOGGER.warn("Rewind requested but no integrated server is running (multiplayer not supported)");
			return;
		}
		if (!BackupManager.hasCheckpoint()) {
			LOGGER.warn("Rewind requested but no checkpoint exists");
			return;
		}
		active = true;
		error = null;
		progress = -1f;
		phase.set(Phase.STOPPING);

		RewindScreen rewindScreen = new RewindScreen(reason);
		screen = rewindScreen;
		mc.setScreenAndShow(rewindScreen); // 先接管屏幕，避免死亡画面闪现

		try {
			// 断开并等待内置服务器完全停止；返回后所有世界文件句柄已关闭
			mc.disconnect(rewindScreen, false);
		} catch (Throwable t) {
			fail(t);
			return;
		}

		phase.set(Phase.RESTORING);
		Thread worker = new Thread(() -> restoreAndRejoin(mc, levelName), "deathreturn-restore");
		worker.setDaemon(true);
		worker.start();
	}

	private static void restoreAndRejoin(Minecraft mc, String levelName) {
		try {
			Path worldDir = BackupManager.worldDir();
			if (worldDir == null) {
				throw new IllegalStateException("world directory unknown");
			}
			long start = System.currentTimeMillis();
			java.util.concurrent.atomic.AtomicLong totalBytes = new java.util.concurrent.atomic.AtomicLong(-1);
			BackupManager.restoreCheckpoint(worldDir, (done, total) -> {
				totalBytes.set(total);
				progress = total > 0 ? (float) ((double) done / total) : -1f;
			});
			progress = 1f;
			LOGGER.info("Checkpoint restored in {} ms ({})", System.currentTimeMillis() - start,
					totalBytes.get() > 0 ? String.format("%.1f MB", totalBytes.get() / 1048576.0) : "size unknown");

			mc.execute(() -> {
				phase.set(Phase.REENTERING);
				try {
					// 原版世界列表点击进入世界的同款入口，自动接管加载流程
					mc.createWorldOpenFlows().openWorld(levelName, () -> {
					});
					phase.set(Phase.DONE);
					active = false;
					screen = null;
				} catch (Throwable t) {
					fail(t);
				}
			});
		} catch (Throwable t) {
			fail(t);
		}
	}

	private static void fail(Throwable t) {
		LOGGER.error("Death Return rewind failed", t);
		error = t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
		phase.set(Phase.ERROR);
		active = false;
		// 屏幕留在原地显示错误与返回按钮；由用户手动回主菜单或重进世界
	}
}
