# LetMeSee — Minecraft Folia 只读容器插件

一个轻量级 Minecraft Folia/Paper 插件，允许 OP 通过指令 **只读** 查看任意坐标上的容器物品，绕过 Lands、QuickShop 等保护插件的限制。

## 特性

- **只读查看** — 通过 `/lms` 指令以只读模式打开任何箱子、木桶、潜影盒、熔炉等容器
- **绕过保护** — 直接读取方块数据，无需调用 Lands / QuickShop / WorldGuard 等保护 API
- **Folia 兼容** — 使用 `Bukkit.getRegionScheduler().run()` 在正确的区域线程执行操作
- **中文界面** — 容器名称自动本地化（箱子、熔炉、漏斗等）
- **审计日志** — 每次查看都会在控制台记录玩家、坐标和容器类型
- **末影箱不适用** — 末影箱是玩家私有背包，无法只读查看；目标区块未加载时会提前提示

## 适用场景

- 管理员检查玩家容器是否存在违规物品
- 调试和排查容器数据问题
- 绕过地皮/领地保护查看公共设施

## 指令

| 指令 | 权限 | 说明 |
|------|------|------|
| `/lms` | `letmesee.use` | 查看准星正对的容器（最多 10 格） |
| `/lms <世界> <X> <Y> <Z>` | `letmesee.use` | 只读打开指定坐标的容器 |

## 权限

| 权限节点 | 默认 | 说明 |
|----------|------|------|
| `letmesee.use` | op | 允许使用 `/lms` 指令 |

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

## 构建

### 前置要求

- Java 21+
- Gradle 8.10+

### 编译

```bash
./gradlew build
```

编译产物位于 `build/libs/letmesee-1.0.3.jar`

### GitHub Actions

- 推送代码或创建 Pull Request 时，`Build` workflow 会自动构建检查，
  构建产物（jar）可在 Actions 页面的 Artifacts 中下载
- `main` 分支每次推送都会触发 `Release` workflow，自动构建并发布 GitHub Release
 （tag 格式 `v<版本>-<构建号>`，jar 作为附件上传），无需手动打 tag
- CI 使用 GitHub Actions 提供的 Gradle 8.10，不依赖本地 Gradle Wrapper

### 手动编译（无需 Gradle）

项目包含 `build.ps1` PowerShell 脚本，可自动下载依赖并编译：

```powershell
.\build.ps1
```

## 兼容性

| 服务端 | 模式 |
|--------|------|
| Folia / Paper / Leaf 等 | 区域调度模式：在目标区域线程读取，回到玩家线程打开 |
| Spigot / CraftBukkit | 同步模式：单线程直读直开（无跨线程操作） |

启动时插件会自动检测并记录当前模式。`plugin.yml` 保留 `folia-supported: true`，
Spigot 会忽略该字段正常加载。

## 安装

1. 将 `letmesee-1.0.3.jar` 放入服务器的 `plugins/` 目录
2. 重启服务器（不要用 `/reload`，会导致监听器重复注册）

## 工作原理

1. 玩家输入 `/lms` 时，插件读取玩家准星正对的方块；也可以继续输入 `/lms world x y z`
2. Folia/Paper 上使用 `Bukkit.getRegionScheduler().run()` 在目标坐标区域线程读取并克隆物品，
   再切回玩家自己的调度器；Spigot 上单线程同步直读（逻辑隔离在 `FoliaCompat` 中）
3. 把克隆结果放进由 `ReadOnlyHolder` 标记的虚拟库存并打开只读视图；
   每次查看都会在控制台记一条审计日志
4. 查看期间 `InventoryListener` 会取消全部点击和拖拽，避免拿走、放入或复制物品

## 开发

```kotlin
// build.gradle.kts 依赖
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}
```

## 许可

MIT License
