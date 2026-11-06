# Death Return（死亡回归）

[English](#english) | 中文

一个 Minecraft **26.3 + Fabric** mod：死亡时，整个世界（含玩家背包、位置、成就、统计）自动回溯到上次 `/save` 的检查点。灵感来自《Returnal》式的 roguelike 时间循环。

```
/save → 冒险 → 死亡 → （无死亡画面）→ 世界回到 /save 那一刻 → 再来一次
```

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/) ≥ 0.20.1 的 Minecraft 26.3 实例
2. 放入 [Fabric API](https://modrinth.com/mod/fabric-api)（0.165.0+26.3 或更新）
3. 放入本 mod 的 jar（见 [Releases](../../releases)）→ `mods/` 目录
4. 无需开启作弊，指令与 `/help` 同级

## 玩法

| 指令 | 效果 |
|---|---|
| `/save` | 创建检查点（覆盖上一个），死亡后将回到此刻 |
| `/load` | 手动回溯到检查点（仅单人世界） |
| `/deathreturn status` | 查看检查点状态 |
| （死亡） | 自动回溯：不出现死亡画面，直接回到检查点时刻 |

- 检查点是**文件级完整快照**：世界、玩家、背包、成就、统计全部一起回溯，无实体复制/数据不一致问题。
- 首次进入世界若没有检查点，会自动创建初始检查点（可配置关闭）。
- 死亡掉落物会随回档自然消失，逻辑自洽。

## 工作原理

### /save（服务端线程上执行，世界短暂停顿）
1. 暂停自动保存（等价 `/save-off`）
2. `saveEverything` 强制落盘（等价 `/save-all flush`）
3. 恢复自动保存
4. 世界目录复制到 `saves/deathreturn-backups/<世界名>/`（先写临时目录、校验文件数，再原子替换，崩溃安全）

### 死亡回溯（客户端编排）
1. 服务端 `ALLOW_DEATH` 事件检测到房主死亡 → 发自定义包给客户端
2. 客户端立即接管屏幕（"死亡回归"过场屏，无死亡画面）
3. `mc.disconnect()` 断开并**同步等待**内置服务器完全停止（文件句柄全部关闭）
4. 工作线程：删除世界目录内容，把检查点镜像回去（跳过 `session.lock`）
5. `WorldOpenFlows.openWorld` 自动重新进入世界（原版加载屏）

## 构建

```bash
# 需要 JDK 25
JAVA_HOME=<jdk25路径> ./gradlew build
# 产物: build/libs/deathreturn-1.0.0.jar
```

已配置国内镜像（Gradle 走腾讯镜像、依赖走阿里云）。工具链：Loom 1.19、Loader 0.20.1、Fabric API 0.165.0+26.3、**Mojang 官方映射**（26.x 起 yarn 已停更）。

## 配置 `config/deathreturn.json`

| 项 | 默认 | 说明 |
|---|---|---|
| `autoInitialBackup` | `true` | 首次进入世界自动建初始检查点 |
| `deathRewind` | `true` | 死亡自动回溯（关闭则只有 /load 手动回溯） |
| `backupFolder` | `deathreturn-backups` | 检查点存放目录（saves 下） |

## 测试状态

已在 26.3 + Fabric 实机验证：

- [x] 新建世界 → 自动初始检查点（~100ms）
- [x] `/save`、`/deathreturn status` 正常工作
- [x] 死亡 → 无死亡画面 → 过场 → 自动回到检查点时刻：位置、背包、物品、状态效果、视角全部还原
- [x] 大世界（1.21GB）实测：初始检查点 3.5s、`/save` 3.5~4.5s、死亡回溯 4.1s（NVMe，约 300MB/s；线性外推 5GB 存档约 15~20s）
- [x] 回溯进行中强制结束进程：检查点完好无损（132/132 文件、level.dat 正常）；世界目录处于半恢复状态属预期，用检查点手动镜像即可修复
- [x] 与 Sodium 0.9.3-beta.1 + Lithium 0.26.3 共存：死亡回溯全程无崩溃，重进后渲染正常

## 已知限制 / 未来工作

- 回溯仅支持**单人世界**（专用服务器需要"停服-换档-重启"或区块热切换，暂未实现）
- `/save` 是同步复制，大世界会停顿数秒（可改为后台线程 + save-off 窗口）
- 回溯进行到一半时进程被杀，世界目录需要手动从检查点镜像修复（检查点本身永远安全）
- LAN 联机中房主死亡会踢掉所有玩家（未做多玩家协调）

## 许可

[MIT](LICENSE)

---

## English

A Minecraft **26.3 + Fabric** mod with a roguelike time loop: when you die, the **entire save** (world, inventory, position, advancements, stats) is rolled back to the checkpoint created by `/save` — no death screen, no title screen, just a seamless transition back to that moment.

- `/save` — create/overwrite the checkpoint (file-level full snapshot of the world folder)
- `/load` — manually rewind (singleplayer only)
- Death — automatic rewind with a custom transition screen; the world is restored by mirroring the checkpoint folder over the live world after the integrated server fully stops

Requires Fabric Loader ≥ 0.20.1 and Fabric API on Minecraft 26.3. Commands need no cheat permissions. MIT licensed.
