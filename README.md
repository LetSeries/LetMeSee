# LetMeSee — Minecraft 只读查看插件

允许 OP 通过指令**只读**查看任意容器、玩家背包与末影箱，无需经过 Lands、QuickShop、WorldGuard 等保护插件。Folia / Paper / Spigot 均支持。

## 特性

- **只读查看** — 通过 `/lms` 指令以只读模式打开箱子、木桶、潜影盒、熔炉等容器，
  或用 `/lms inv|ec <玩家>` 查看在线玩家的背包与末影箱
- **绕过保护** — 直接读取方块与库存数据，不调用任何保护插件 API
- **双模式兼容** — Folia 下走区域 / 实体调度线程；Paper / Spigot 走同步直读（单线程，天然安全）
- **中文界面** — 容器名称自动本地化（箱子、熔炉、漏斗等）；玩家库存标题显示所属玩家
- **审计日志** — 每次查看都会在控制台记录查看者、目标（坐标或玩家名）与容器类型
- **只读保护** — 查看期间拦截全部点击和拖拽，物品拿不走、放不进、复制不了
- **自动刷新** — 视图按配置间隔自动同步目标变化；内容未变化时跳过重写，不闪烁；
  目标消失、变尺寸或下线时自动关闭

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
| `container-view` | true | 是否启用容器查看（准星模式与坐标模式） |
| `player-view` | true | 是否启用玩家库存查看（`/lms inv\|ec`） |
| `max-target-distance` | 10 | `/lms` 准星模式最大距离，范围 1~64，非法值自动回退 |
| `audit-log` | true | 是否在控制台记录审计日志 |
| `audit-file` | false | 是否同时追加写入插件目录下的 `audit.log` |
| `refresh-interval-seconds` | 5 | 只读视图自动刷新间隔（秒），0 关闭，范围 0~60，非法值回退 5 |

布尔开关必须写成 `true` / `false`（可带引号）；写成 `"no"`、`1` 这类无法识别的值时，
两个查看开关按**关闭**处理、审计按「控制台开 / 文件关」处理——宁可少开不可多开。
`/lms reload` 的回显会显示实际生效值。

修改后可用 `/lms reload` 即时生效，无需重启。注意生效范围：
`container-view`、`player-view`、`max-target-distance`、审计开关立即生效，
并且**已打开的视图也会随之关闭**——权限被撤销或功能被关闭后，下一次刷新（最多
`refresh-interval-seconds` 秒）就会结束该视图并提示；
`refresh-interval-seconds` 只影响**之后新打开**的视图，已经在看的视图仍按打开时的间隔刷新，
重新打开一次即可应用新间隔。

## 权限

| 权限节点 | 默认 | 说明 |
|----------|------|------|
| `letmesee.use` | op | 允许使用 `/lms` 查看容器 |
| `letmesee.player` | op | 允许使用 `/lms inv\|ec` 查看玩家库存 |
| `letmesee.reload` | op | 允许使用 `/lms reload` 重载配置 |

三个权限互相独立：只授予 `letmesee.player` 的组也能使用 `inv|ec`，不需要额外的
`letmesee.use`；`letmesee.reload` 同理。

## 安全说明

- 本插件设计目标就是绕过领地 / 商店保护查看容器，**授权限即授偷看权**：
  `letmesee.use` 可看全服任意容器，`letmesee.player` 可看任意在线玩家背包与末影箱。
  请只授予可信管理员，默认 op 即可，不要下放给普通玩家组。
- 审计日志记录查看者 UUID、被查看者 UUID、坐标与容器名，并在界面**成功打开后**写入，
  不会把失败或被打断的打开记成一次成功查看。`audit-log: true`
  输出到控制台/server log；`audit-file: true` 会额外追加到插件目录的 `audit.log`
  （UTC 时间戳、单行记录）。审计文件只追加，不自动轮转；保留与清理由服主负责，
  它是有用的运维日志，但不是防篡改的法证记录。
- 玩家名支持前缀匹配，多个候选时插件会拒绝打开并列出全名，防止看错人。
- 容器标题与聊天提示里的容器名会先去掉 `§` 颜色代码：改名潜影盒等玩家可控名称
  无法借标题重绘「[只读]」标记或伪造容器身份；审计记录保留完整原名。
- 每次刷新前都会复核查看者权限与功能开关，撤销授权后已打开的视图会被强制结束；
  界面打开若被其它插件取消（`InventoryOpenEvent`），不会产生审计记录。

## 统计（bStats）

插件接入了 [bStats](https://bstats.org) 匿名统计，用于了解插件在多少服务器上运行、
大家用的是 Folia 还是 Paper/Spigot。收集内容只有服务器元数据
（服务端类型/版本、在线玩家数、插件数量等），**不包含任何容器内容、玩家行为、
坐标或 `/lms` 的使用记录**。

- 插件 ID 尚未在 `MetricsSupport.PLUGIN_ID` 中填入（保持 `0`）时统计直接跳过；
  在 [bstats.org/what-is-my-plugin-id](https://bstats.org/what-is-my-plugin-id)
  注册后把数字填进去即启用
- 不想上报？删除或清空服务端 `plugins/bStats/config.json` 中的 `enabled`（改为 false）
  即可全局关闭，插件其余功能不受影响
- 自定义图表 `server_type` 需要在 bStats 后台登记同名图表，未登记会被服务端忽略
- `build.ps1` 手工构建的产物不含统计依赖，运行时会自动跳过统计（仅一条日志），
  正式发布请使用 Gradle / CI 构建的 jar

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
| Folia / 其它区域化服务端 | 区域调度模式：容器在目标区域线程读取，玩家库存走实体调度器，均回到查看者线程打开 |
| Paper / Spigot / CraftBukkit | 同步模式：单线程直读直开（无跨线程操作） |

启动时插件会检测服务端本体是否区域化（以 Folia 独有的区域化服务端主类为准，
而不是只看调度 API 是否存在——后者在 Paper 上也有），并在日志中记录当前模式。
`plugin.yml` 保留 `folia-supported: true`，Spigot 会忽略该字段正常加载。

## 安装

1. 从 GitHub Release 下载 `letmesee-<版本>.jar`，放入服务器的 `plugins/` 目录
2. 重启服务器（不要用 `/reload`，会导致监听器重复注册）

## 工作原理

1. 玩家输入 `/lms` 时，插件读取玩家准星正对的方块；也可以继续输入 `/lms world x y z`
   （支持 `~` 相对坐标；目标区块未加载或 Y 越界时提前提示）
2. Folia 上使用区域调度器在目标坐标区域线程读取并克隆物品，
   再切回查看者自己的实体调度器；Paper / Spigot 上使用同步路径直读。
   Folia 专属逻辑隔离在 `FoliaCompat` 中，Spigot 上连类加载都不会触发。
3. 把克隆结果放进由 `ReadOnlyHolder` 标记的虚拟库存并打开只读视图；
   每次查看都会在控制台记一条审计日志
4. 查看期间 `InventoryListener` 会取消全部点击和拖拽，避免拿走、放入或复制物品
5. 刷新定时器按 `refresh-interval-seconds` 重读目标并同步到视图；内容与当前视图
   完全一致时跳过写入与客户端同步，避免每隔几秒刷一次屏；每次刷新前复核权限与开关；
   容器消失、变尺寸、被查看玩家下线时自动关闭视图并结束会话；
   关闭界面、退出游戏、打开新视图、插件卸载都会取消定时任务，不泄漏

## 常见问题

- **打开后物品不变？** 检查 `refresh-interval-seconds` 是否为 0（关闭刷新），
  或用 `/lms reload` 确认配置已生效。
- **提示"目标不在当前区域"？** Folia 下准星目标与你不在同一区域时无法直读，
  改用 `/lms <世界> <X> <Y> <Z>` 坐标模式。
- **Spigot 上能用吗？** 能，启动日志会显示 Bukkit 同步模式，功能一致。
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

编译产物位于 `build/libs/letmesee-<版本>.jar`（阴影 jar，内含重定位后的 bStats，
是安装到服务器的唯一产物）。单测用 `gradle test`（JUnit 5，零服务端依赖）。

Gradle 插件（shadow）的解析在 `settings.gradle.kts` 里配置为阿里云镜像优先、
官方 Gradle Plugin Portal 兜底——Plugin Portal 在部分网络环境不可达时也能构建。

### GitHub Actions

- 推送代码或创建 Pull Request 时，`Build` workflow 会自动构建（含单测），
  构建产物（jar）可在 Actions 页面的 Artifacts 中下载
- `main` 分支每次推送都会触发 `Release` workflow，自动构建并发布 GitHub Release
  （tag 格式 `v<版本>-<构建号>`，jar 作为附件上传），无需手动打 tag
- CI 使用 GitHub Actions 提供的 Gradle 8.10，不依赖本地 Gradle Wrapper

### 手动编译（无需安装 Gradle）

项目包含 `build.ps1` PowerShell 脚本，自动下载依赖并编译（Windows PowerShell 5.1+
或跨平台 PowerShell 7 均可，classpath 分隔符按平台处理）：

```powershell
.\build.ps1
```

脚本版本号与 paper-api 版本从 `build.gradle.kts` 读取（`gradle build` 会校验两处一致），
升版本只需改一处。注意：依赖清单是脚本内手写的精简版，与 Gradle 解析的完整依赖图
可能有出入——正式构建请以 `gradle build` / CI 为准。

## 项目结构

```
src/main/java/com/letmesee/
├── LetMeSee.java          # 插件入口：注册命令、监听器，启动时检测运行模式并回显配置
├── LMSCommand.java        # /lms 命令：参数解析，Folia/Spigot 双路径分流
├── CommandRouter.java     # 子命令路由与权限判定（纯函数，可单测）
├── PlayerNameResolver.java# 玩家名前缀解析（纯函数，可单测：歧义时拒绝打开）
├── SessionGuard.java      # 已打开视图的持续授权检查（纯函数，可单测）
├── PlayerViews.java       # 玩家背包/末影箱快照与只读打开（中立层）
├── LegacyRefresher.java   # 同步路径（Paper/Spigot）：打开视图 + 定时刷新
├── FoliaCompat.java       # Folia 隔离层（区域线程读取，玩家线程打开）
├── ServerCompat.java      # 零依赖环境检测，经反射进入 FoliaCompat
├── MetricsSupport.java    # bStats 匿名统计接入（未配置 ID 或依赖缺失时自动跳过）
├── ContainerNames.java    # 方块类型到中文名的映射（任意线程可调用）
├── LMSConfig.java         # config.yml 视图：查看距离、审计开关、刷新间隔
├── ContainerSnapshots.java# 容器读取、物品克隆、内容比对、名称清洗与审计消息
├── AuditLog.java          # 审计记录格式化与落盘（控制台 / audit.log，防伪造单行）
├── ReadOnlyViews.java     # 只读虚拟库存创建与标题格式化（三条打开路径共用）
├── ViewSession.java       # 只读查看会话：注册、刷新应用、关闭清理
├── InventoryListener.java # 取消只读界面的点击/拖拽，关闭与退出时结束会话
├── LMSTabCompleter.java   # 世界名、坐标、玩家名补全
└── ReadOnlyHolder.java    # 标记虚拟只读库存
```

## 适用场景

- 管理员检查玩家容器是否存在违规物品
- 调试和排查容器数据问题
- 绕过地皮/领地保护查看公共设施

## 更新记录

### 1.2.5

- 新增 bStats 匿名统计（未配置插件 ID 时自动跳过；不收集任何查看行为数据）
- Gradle 改用 shadow 打包（goooler 维护的 fork——原版 8.1.1 停更，内置 ASM 不支持
  Java 21 的 class 文件）：bStats 被重定位进 `com.letmesee.libs.bstats`，
  `gradle build` 会校验产物包含必需资源且重定位确实发生
- 普通 jar 不再单独产出，`build/libs` 只保留最终阴影 jar

### 1.2.4

- 安全：已打开的视图在每次刷新前复核权限与功能开关——撤销 `letmesee.use` /
  `letmesee.player` 或关闭 `container-view` / `player-view` 后，视图会在下一次刷新被强制结束
- 安全：布尔开关不再「fail open」——`container-view: "false"` 这类带引号或无法识别的
  写法按关闭处理（审计按「控制台开 / 文件关」），不再出现「以为关了其实还开着」
- 安全：容器名先去掉 `§` 颜色代码再进标题与聊天提示，玩家可控名称无法重绘只读标记
- 修复：Folia 玩家库存视图重复调用 `openInventory`（多余的关闭事件与闪烁隐患）；
  Folia 两条打开路径不再经过多余的回调转手
- 修复：界面被其它插件取消打开（`InventoryOpenEvent` 被取消）时，不再发送成功提示、
  不再写审计记录、不再注册会话
- 构建：`build.ps1` 每次重建 classes（默认不再打包旧类）、依赖缓存校验魔法数、
  classpath 分隔符按平台取（Linux/macOS 的 pwsh 可用）、与 Gradle 同样输出调试信息、
  固定 UTF-8 编码；`gradle build` 校验 build.ps1 与 build.gradle.kts 的 paper-api 版本一致
- 构建：Gradle 编译显式固定 UTF-8 源码编码
- 发布：Release 版本解析兼容单双引号，产物不唯一时直接失败
- 文档：项目结构补 `AuditLog` / `SessionGuard`，说明开关取值规则与持续授权行为

### 1.2.3

- 优化：玩家名前缀解析抽成纯函数 `PlayerNameResolver` 并补回归测试（歧义、空输入、大小写）
- 修复：`/lms inv ""` 这类空输入不再退化为「匹配到所有人都算命中」
- 构建：`gradle build` 现在会校验产物必须包含 `plugin.yml` 与 `config.yml`，缺资源直接构建失败
- 发布：Release 版本号改为从构建出的 jar 中读取，不再依赖 `build.gradle.kts` 的书写格式
- 文档：明确 `/lms reload` 的生效范围（刷新间隔只对新打开的视图生效）

### 1.2.2

- 修复 Folia 卸载时跨线程关闭界面，以及目标玩家调度任务退休时查看者无反馈
- 修复玩家位置不可读时带前导空格的相对坐标未被拒绝
- 修复 PowerShell 构建漏打包 `config.yml`，并在产物中检查必需资源
- 修复审计写入时机：改为界面打开成功后记录
- 修正文档中 Paper/Folia 调度路径描述

### 1.2.1

- 新增可选审计文件：`audit-file: true` 后追加到插件目录 `audit.log`，带 UTC 时间戳；控制台与文件开关互相独立
- 审计记录强制单行化，避免恶意名称把伪造记录注入日志

### 1.2.0

- 修复：只授予 `letmesee.player` 时 `/lms inv|ec` 仍被 `letmesee.use` 拦截
- 修复：Paper 被误判为 Folia 区域调度模式；改为按服务端本体检测，
  Paper / Spigot 统一走同步路径
- 优化：刷新时内容未变化则跳过写入与客户端同步，视图不再周期性闪烁
- 优化：只读库存创建与标题格式化收敛到 `ReadOnlyViews`，三条打开路径行为一致
- 优化：关闭自动刷新时也注册会话，玩家退出、插件卸载仍能统一清理
- 优化：启动日志回显当前生效配置；`/lms` 路由与权限判定独立成纯函数并补齐单测

## 许可

MIT License
