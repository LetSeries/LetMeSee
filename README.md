# LetMeSee — Minecraft 只读查看插件

允许 OP 通过指令**只读**查看任意容器、玩家背包与末影箱，无需经过 Lands、QuickShop、WorldGuard 等保护插件。Folia / Paper / Spigot 均支持。

## 特性

- **只读查看** — 通过 `/lms` 指令以只读模式打开箱子、木桶、潜影盒、熔炉等容器，
  或用 `/lms inv|ec <玩家>` 查看在线玩家的背包与末影箱
- **绕过保护** — 直接读取方块与库存数据，不调用任何保护插件 API
- **双模式兼容** — Folia / Paper 下走区域 / 实体调度线程，Spigot 下自动降级为同步直读
- **中文界面** — 容器名称自动本地化（箱子、熔炉、漏斗等）；玩家库存标题显示所属玩家
- **审计日志** — 每次查看都会在控制台记录查看者、目标（坐标或玩家名）与容器类型
- **只读保护** — 查看期间拦截全部点击和拖拽，物品拿不走、放不进、复制不了
- **自动刷新** — 视图按配置间隔自动同步目标变化；目标消失、变尺寸或下线时自动关闭

## 指令

| 指令 | 权限 | 说明 |
|------|------|------|
| `/lms` | `letmesee.use` | 查看准星正对的容器（距离上限见 `max-target-distance`） |
| `/lms <世界> <X> <Y> <Z>` | `letmesee.use` | 只读打开指定坐标的容器（支持 `~` 相对坐标） |
| `/lms inv <玩家>` | `letmesee.player` | 只读查看在线玩家的背包 |
| `/lms ec <玩家>` | `letmesee.player` | 只读查看在线玩家的末影箱 |
| `/lms reload` | `letmesee.reload` | 重载 config.yml（控制台可用） |

坐标参数支持 Tab 补全（世界名、当前坐标、`~`）；`inv|ec` 后补在线玩家名。

## 配置（config.yml）

| 项 | 默认 | 说明 |
|----|------|------|
| `max-target-distance` | 10 | `/lms` 准星模式最大距离，范围 1~64，非法值自动回退 |
| `audit-log` | true | 是否在控制台记录审计日志 |
| `refresh-interval-seconds` | 5 | 只读视图自动刷新间隔（秒），0 关闭，范围 0~60，非法值回退 5 |

修改后可用 `/lms reload` 即时生效，无需重启。

## 权限

| 权限节点 | 默认 | 说明 |
|----------|------|------|
| `letmesee.use` | op | 允许使用 `/lms` 查看容器 |
| `letmesee.player` | op | 允许使用 `/lms inv\|ec` 查看玩家库存 |
| `letmesee.reload` | op | 允许使用 `/lms reload` 重载配置 |

## 支持容器类型

- 箱子 / 陷阱箱（含双箱）
- 木桶
- 潜影盒（所有 16 色）
- 熔炉 / 高炉 / 烟熏炉
- 漏斗
- 投掷器 / 发射器
- 酿造台
- 合成器
- 雕纹书架 / 讲台 / 唱片机 / 饰纹陶罐
- 在线玩家的背包 / 末影箱（`/lms inv|ec`）

不支持：方块形态的末影箱（私有背包，无公开读取 API）、试炼宝库 / 试炼刷怪笼
（Bukkit API 拿不到内容）。

## 兼容性

| 服务端 | 模式 |
|--------|------|
| Folia / Paper / Leaf 等 | 区域调度模式：容器在目标区域线程读取，玩家库存走实体调度器，均回到查看者线程打开 |
| Spigot / CraftBukkit | 同步模式：单线程直读直开（无跨线程操作） |

启动时插件会自动检测并记录当前模式。`plugin.yml` 保留 `folia-supported: true`，
Spigot 会忽略该字段正常加载。

## 安装

1. 从 GitHub Release 下载 `letmesee-<版本>.jar`，放入服务器的 `plugins/` 目录
2. 重启服务器（不要用 `/reload`，会导致监听器重复注册）

## 工作原理

1. 玩家输入 `/lms` 时，插件读取玩家准星正对的方块；也可以继续输入 `/lms world x y z`
   （支持 `~` 相对坐标；目标区块未加载或 Y 越界时提前提示）
2. Folia/Paper 上使用区域调度器在目标坐标区域线程读取并克隆物品，
   再切回查看者自己的调度器；Spigot 上单线程同步直读（Folia 专属逻辑隔离在 `FoliaCompat` 中，
   Spigot 上连类加载都不会触发）
3. 把克隆结果放进由 `ReadOnlyHolder` 标记的虚拟库存并打开只读视图；
   每次查看都会在控制台记一条审计日志
4. 查看期间 `InventoryListener` 会取消全部点击和拖拽，避免拿走、放入或复制物品
5. 刷新定时器按 `refresh-interval-seconds` 重读目标并同步到视图；
   容器消失、变尺寸、被查看玩家下线时自动关闭视图并结束会话；
   关闭界面、退出游戏、打开新视图、插件卸载都会取消定时任务，不泄漏

## 常见问题

- **打开后物品不变？** 检查 `refresh-interval-seconds` 是否为 0（关闭刷新），
  或用 `/lms reload` 确认配置已生效。
- **提示"目标不在当前区域"？** Folia 下准星目标与你不在同一区域时无法直读，
  改用 `/lms <世界> <X> <Y> <Z>` 坐标模式。
- **Spigot 上能用吗？** 能，启动日志会显示"降级为 Bukkit 同步模式"，功能一致。
- **能拿走物品吗？** 不能，所有点击和拖拽都会被取消；视图里的是克隆副本，
  原容器不受任何影响。

## 构建

### 前置要求

- Java 21+
- Gradle 8.10（系统安装；仓库未跟踪 Wrapper jar，所以 `./gradlew` 不可用）

### 编译

```bash
gradle build
```

编译产物位于 `build/libs/letmesee-<版本>.jar`。单测用 `gradle test`（JUnit 5，零服务端依赖）。

### GitHub Actions

- 推送代码或创建 Pull Request 时，`Build` workflow 会自动构建（含单测），
  构建产物（jar）可在 Actions 页面的 Artifacts 中下载
- `main` 分支每次推送都会触发 `Release` workflow，自动构建并发布 GitHub Release
  （tag 格式 `v<版本>-<构建号>`，jar 作为附件上传），无需手动打 tag
- CI 使用 GitHub Actions 提供的 Gradle 8.10，不依赖本地 Gradle Wrapper

### 手动编译（无需 Gradle）

项目包含 `build.ps1` PowerShell 脚本，可自动下载依赖并编译：

```powershell
.\build.ps1
```

脚本版本号从 `build.gradle.kts` 读取，升版本只需改一处。

## 项目结构

```
src/main/java/com/letmesee/
├── LetMeSee.java          # 插件入口：注册命令、监听器，启动时检测运行模式
├── LMSCommand.java        # /lms 命令：参数解析，Folia/Spigot 双路径分流
├── PlayerViews.java       # 玩家背包/末影箱快照与只读打开（中立层）
├── LegacyRefresher.java   # Spigot 同步路径：打开视图 + 定时刷新
├── FoliaCompat.java       # Folia/Paper 隔离层（区域线程读取，玩家线程打开）
├── ServerCompat.java      # 零依赖环境检测，经反射进入 FoliaCompat
├── ContainerNames.java    # 方块类型到中文名的映射（任意线程可调用）
├── LMSConfig.java         # config.yml 视图：查看距离、审计开关、刷新间隔
├── ContainerSnapshots.java# 容器读取、物品克隆与审计日志（与服务端实现无关）
├── ViewSession.java       # 只读查看会话：注册、刷新应用、关闭清理
├── InventoryListener.java # 取消只读界面的点击/拖拽，关闭与退出时结束会话
├── LMSTabCompleter.java   # 世界名、坐标、玩家名补全
└── ReadOnlyHolder.java    # 标记虚拟只读库存
```

## 适用场景

- 管理员检查玩家容器是否存在违规物品
- 调试和排查容器数据问题
- 绕过地皮/领地保护查看公共设施

## 许可

MIT License
