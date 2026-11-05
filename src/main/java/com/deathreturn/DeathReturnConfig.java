package com.deathreturn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 死亡回归 mod 的配置，存放在 config/deathreturn.json。
 */
public class DeathReturnConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 世界首次打开时若还没有检查点，自动创建一个初始检查点。 */
    public boolean autoInitialBackup = true;

    /** 玩家死亡时自动回溯到检查点（仅单人世界生效）。 */
    public boolean deathRewind = true;

    /** 检查点存放目录（位于 saves 目录下）。 */
    public String backupFolder = "deathreturn-backups";

    public static DeathReturnConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("deathreturn.json");
        DeathReturnConfig cfg = new DeathReturnConfig();
        if (Files.exists(path)) {
            try {
                cfg = GSON.fromJson(Files.readString(path), DeathReturnConfig.class);
                if (cfg == null) cfg = new DeathReturnConfig();
            } catch (Exception e) {
                DeathReturnMod.LOGGER.error("Failed to read config, using defaults", e);
                cfg = new DeathReturnConfig();
            }
        }
        // 写回文件，让新出现的配置项可见
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(cfg));
        } catch (IOException e) {
            DeathReturnMod.LOGGER.error("Failed to write config", e);
        }
        return cfg;
    }
}
