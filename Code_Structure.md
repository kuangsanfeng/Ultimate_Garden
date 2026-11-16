# Ultimate Garden 工程结构与复用导航

> 公共模块重构：2026-11-16（Asia/Taipei）。稳定基线 master：48fa5b9b711003ad00148336c8d44f6bb35b43ba；集成分支 test/structure_shift。
> 原工作区保持master及原提交；所有重构位于独立worktree ultimate_garden-structure_shift。任务按detached HEAD验证后提交，按编号保留12次no-ff合并；最终提交见交付记录。
> 第1–12章保留工程导航，12.3–12.15增量记录本轮公共提取；旧类型部分保留兼容外观，实际新入口见最终复用表。服务器命中、RTT及在线生命周期仍需实机验收。

## 快速任务导航

客户端 Java 链接均从项目根目录出发；完整文件、方法索引见[源码索引](#source-index)。

| 要实现什么 | 优先查看模块和入口 |
|---|---|
| 加一个生命周期受控的 Macro | [MacroManager.Module/register](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/MacroManager.java)（目前仅 Farm 注册）；对照 `ZombieShootoutController.register/start/stop` 的独立生命周期 |
| 修改客户端事件或账号切换 | [Ultimate_GardenClient.onInitializeClient](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java) |
| 调整农业路线、行时间、额外换行 | [FarmController.tick](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java) → [RouteController](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteController.java)；`CropDefinition` |
| Spawn、Warp、Loadout、GUI 操作 | [FarmController](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java) 动作入口 → [FarmCommandController](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/action/FarmCommandController.java) → 自有 `ActionScheduler` |
| 调整害虫消息或倒计时 | [CommandListener.register](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandListener.java) → [FarmController.automateTaskByMonitorMessage](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java)、`PestLoadoutTimer` |
| 增加服务器消息观察 | [CommandListener](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandListener.java)，复用 GAME 事件，不另读 latest.log |
| 修改 Shootout 运动、优先级、重锁定 | [ZombieShootoutController](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java)、[ShootoutModel](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/model/ShootoutModel.java) / Policy / TargetRound / CandidateSelection |
| 修改本地视角更新 | [ShootoutAimFrameMixin](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/ShootoutAimFrameMixin.java) → aimFrame → [ShootoutMouseInput.aim](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutMouseInput.java) |
| 排查不发射 | [ShootoutAimDiagnostics](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ShootoutAimDiagnostics.java)；串联 solutionSequence、inputSequence、attemptSequence |
| 新增 ESP | [ESPManager.register/registerFixed](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPManager.java)，生产不可变 `ESPTarget`，不要另建渲染事件 |
| 新增 HUD / 编辑布局 | [HudPanel](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/HudPanel.java)、[HudAdjustmentController](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/HudAdjustmentController.java)、[HudConfig.HudId](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/display/HudConfig.java) |
| 新增菜单设置 | [UltimateGardenScreen](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) 的 heading/toggle/input/nestedDelaySection（私有装配助手）；`LanguageManager/HelpTooltip` |
| 配置保存、账号隔离、迁移 | [ConfigManager](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java)、[ConfigIO](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/storage/ConfigIO.java)、[AccountDirectories](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/storage/AccountDirectories.java)，不直接覆盖用户 JSON |
| 输入来源、释放和失焦 | [输入与 Focus](#input)，先查各 source scope 和 owner supplier |
| 找可直接复用或可提取的能力 | [Reusable Components](#reuse) |

## 目录

- [1. 工程与构建](#project)
- [2. 事件、线程与跨模块时序](#events)
- [3. 输入与 Focus](#input)
- [4. 模块职责、状态与清理](#modules)
- [5. 业务调用链](#flows)
- [6. 配置与状态读取](#config)
- [7. Reusable Components](#reuse)
- [8. 建议提取的接口边界](#extraction)
- [9. Mixin 清单](#mixins)
- [10. 全量源码导航索引](#source-index)
- [11. 资源与测试索引](#assets-tests)
- [12. 审计结果与边界](#verification)
- [公共模块重构最终入口与验收](#structure-shift)
- [13. 客户端目录迁移与验证](#package-migration)

<a id="project"></a>
## 1. 工程与构建

- [build.gradle](build.gradle)：Fabric Loom 分离 `main/client` source set，并合并到同一个 `ultimate_garden` Mod；Java release/source/target 为 **25**，构建普通包及 sourcesJar。没有把 `tests/` 配置成 Gradle 标准 Java test source set。
- [gradle.properties](gradle.properties)：Minecraft **26.1.2**、Fabric Loader **0.19.3**、Fabric API **0.155.2+26.1.2**、Mod **1.0.2**、Loom `1.17-SNAPSHOT`；配置缓存关闭。以这些实际声明为准，勿从旧版本映射推断 API。
- [settings.gradle](settings.gradle)、[Gradle Wrapper](gradle/wrapper/gradle-wrapper.properties)：插件仓库及固定 Wrapper 分发来源（Gradle 9.5.1）；[CI](.github/workflows/build.yml) 用 Microsoft JDK25 执行 `./gradlew build`，没有调用独立 tests 脚本。
- [fabric.mod.json](src/main/resources/fabric.mod.json)：公共与客户端入口，公共/客户端两套 Mixin 配置；description/contact 仍有模板内容，不代表实际业务状态。
- `src/main/java`：公共初始化和服务器示例 Mixin；业务主要位于 `src/client/java`。
- `src/client/resources/ultimategarden`：默认作物定义与 Shootout 静态实测模型；`src/main/resources/assets/ultimate_garden/lang`：中英文语言字典。
- `tests/`：独立 Java main、Python 审查和 PowerShell 运行脚本，含模拟 Minecraft/GLFW 的 stubs。编译成功不能代替这些脚本，也不能代替游戏验证。
- `docs/` 是补充文档，不是模型或配置的运行时权威来源。`build/`、`.gradle/`、`run/` 属于构建/本地实例产物，不列为源码复用入口。

### 1.1 目录地图

```text
client/ [1]
├── Ultimate_GardenClient.java
├── function/ [0]
│   ├── collection/ [2]
│   │   └── zombie_shoot/ [8]
│   │       └── compat/ [2]
│   ├── esp/ [3]
│   │   ├── adapters/ [2]
│   │   ├── render/ [3]
│   │   └── style/ [4]
│   └── observation/ [0]
│       ├── entity/ [2]
│       ├── query/ [4]
│       └── sampling/ [1]
├── macro/ [1]
│   ├── farm/ [3]
│   │   ├── action/ [2]
│   │   ├── crop/ [2]
│   │   ├── hint/ [1]
│   │   ├── pest/ [1]
│   │   └── route/ [6]
│   └── zombie_shoot/ [2]
│       ├── aim/ [8]
│       ├── model/ [3]
│       ├── shot/ [3]
│       └── targeting/ [4]
├── mixin/ [13]
├── platform/ [0]
│   ├── context/ [2]
│   ├── gui/ [5]
│   ├── hint/ [2]
│   ├── input/ [0]
│   │   ├── view/ [6]
│   │   └── virtual/ [3]
│   ├── network/ [3]
│   ├── persistence/ [0]
│   │   ├── config/ [1]
│   │   │   ├── model/ [3]
│   │   │   │   ├── display/ [3]
│   │   │   │   ├── farm/ [5]
│   │   │   │   └── shootout/ [2]
│   │   │   └── storage/ [3]
│   │   └── log/ [2]
│   │       └── zombie_shoot/ [3]
│   │           └── compat/ [4]
│   ├── ui/ [3]
│   │   ├── adapters/ [0]
│   │   │   ├── farm/ [2]
│   │   │   ├── menu/ [1]
│   │   │   └── zombie_shoot/ [1]
│   │   ├── hud/ [4]
│   │   ├── language/ [3]
│   │   ├── render/ [2]
│   │   ├── screen/ [1]
│   │   ├── theme/ [1]
│   │   └── widget/ [8]
│   └── window/ [4]
└── runtime/ [0]
    ├── command/ [3]
    ├── feature/ [1]
    │   └── adapters/ [1]
    ├── hotkey/ [1]
    └── scheduler/ [2]
```

<a id="events"></a>
## 2. 事件、线程与跨模块时序

### 2.1 初始化顺序

`Ultimate_Garden.onInitialize` 提供 Mod ID、Logger 和资源 ID 工具。客户端 `Ultimate_GardenClient.onInitializeClient`：

1. 从当前 User 建立账号身份并初始化 `ConfigManager`。
2. 建立 ESPManager，注册 Pest 与 Shootout ESP，再关闭注册阶段并初始化渲染回调。
3. 建立 WindowsState、独立的 legacyMouseClickScheduler、VirtualKeyboard/VirtualMouse、FocusController 和 InputInterceptor。
4. 建立 VirtualInput、FarmController、MacroManager；MacroManager 当前只注册 Farm。
5. 注册独立 Shootout，并用 supplier 将 Shootout 输入所有权接入共享拦截器；Farm 也通过 externalInputOwner 拒绝冲突。
6. 注册两个 HUD、屏幕事件、账号变化、断线与退出清理，最后注册快捷键、客户端命令与聊天入口。

### 2.2 驱动矩阵

| 阶段 | 当前驱动 | 时序约束 |
|---|---|---|
| START_CLIENT_TICK | HUD 布局编辑检查；User/账号变化检查 | 账号变更先结束编辑、abort Farm、清兼容队列、使 ESP generation 失效，再激活配置；Shootout 另在自己的 Tick 检查 accountRevision |
| END_CLIENT_TICK：Shootout.register | `tick()` 后 `ZombieShootCollector.update(running)` | 先业务决策再被动采集；采集器不另注册第二个 END Tick |
| END_CLIENT_TICK：MacroKeyBindings.register | 按键处理与 `manager.tick()` | MacroManager 遍历当前注册模块；Farm 暂停时仍有命令/计时逻辑需要被驱动，不要只在 running 时调用 |
| 每个客户端帧 | `ShootoutAimFrameMixin` 在 Minecraft.runTick 调用 MouseHandler.handleAccumulatedMovement 前通知 aimFrame | 不在帧回调求解、选目标、射击或主动测 Ping；只消费 Tick 发布的解 |
| LevelRenderEvents.END_EXTRACTION | ESPManager 收集实体和样式的不可变 Frame | 世界读取在提取阶段；Frame 附 generation |
| LevelRenderEvents.END_MAIN | ESPRenderer.draw | 仅消费提取快照；generation 不匹配不画；不重新做业务扫描或预瞄求解 |
| HudElementRegistry | 两个 HUD.render | 读取配置和业务快照；布局编辑时允许显示预览，不改变业务开关 |
| GAME 消息 | CommandListener | getString 后转交 Farm/Pest 与非 overlay 的 Shootout 自动启动检测；不取消服务器消息 |
| ScreenEvents.AFTER_INIT | FarmController.onPlayerScreenOpened | PlayerGuiInput 区分真实 GUI 操作与命令动作；不等同于所有 GUI 都主动暂停所有 Macro |
| DISCONNECT / CLIENT_STOPPING | 各 owner 清理 | Farm abort；Shootout stop；ESP generation/allocator；HUD 编辑结束；日志及分析收尾 |

上述是当前注册和调用结构。事件之间的具体运行时耗时、第三方 Mixin 的干预，不能由静态源码证明。

### 2.3 三种时间不能混用

- ActionScheduler：FIFO，`schedule(ms)` 向上换算成 50ms 单位的 Tick 数，实际执行还受队列装载/执行 Tick 语义影响。`scheduleDelayTicks(n)` 保留同语义；`scheduleAfterTicks(n)` 内部 n-1，用于相邻回调精确间隔。clear 不代表重置 paused。
- 农业行时间：RouteController 在 running 且非 commandRunning 时 incrementTick；totalTicks 同步增加。RandomDelay 的行抖动在当前路线 Tick 中抽样，不应假定是每行只抽一次。
- Shootout：CD、失效宽限、自动启动、RTT 和帧步进使用单调纳秒时间；不是服务器精确 Tick 时钟。20–120 Hz 是调度目标，受 FPS 限制；卡顿跳过旧期限，dt 仍受原 50ms 上限约束。

<a id="input"></a>
## 3. 输入与 Focus

### 3.1 两条虚拟输入路径

**Farm 路径**：VirtualInput 组合 VirtualKeyboard 与 VirtualMouse，保存按住状态及菜单 suspend/resume 快照。键盘和动作 GUI 输入由 PlayerGuiInput.virtualDispatch 及对应 Mixin 区分来源；调用者应通过现成包装接口，不直接操作 KeyMapping 字段。

**Shootout 路径**：ShootoutMouseInput 使用 source-scoped dispatch 标识，保存旧 accumulatedDX/DY，提交本步相对位移，调用 ShootoutMouseAccessor 的原生 turnPlayer，再恢复旧累计值。右键为完整 Press/Release；cancel 仅清自己拥有的右键与 clickPending。不得把它与 Farm 的长按左键或 legacyMouseClickScheduler 混用。

原生调用返回后读取真实视角：ViewObserver 的 before → submitted → 原生 turn → consumed 对齐；Aim & Shot Diagnostics 的 APPLIED 也在该原生调用返回后读取。现实现每帧最多一步、每次同步消费，输入范围序号的起止相同，不存在“固定等一 Tick”保证。

### 3.2 阻断与来源

WindowsInputInterceptor 持有 WindowsState 和外部 owner supplier：

- Farm enable/disable 管理自身拦截状态。
- Shootout 的一般 owner、鼠标移动 owner、右键 owner 分开：Auto Aim OFF 不接管鼠标移动；Auto Shoot 实际失效时不接管玩家右键。
- 本 Mod 菜单通过 uiInputAllowed 放行 UI；HUD 布局编辑有专用输入路由。
- 右键 release 有独立放行处理，避免真实玩家在接管前按下的右键无法释放。
- WindowsMouseMixin 在判断 source scope 后才检查手动射击限制/真实输入拦截。Shootout 虚拟右键不再次登记成手动尝试。
- 被手动限制拒绝的真实 Press 直接丢弃，不排队补发；仅获准输入在 onButton RETURN 通知 manualInputProcessed。

### 3.3 Focus

WindowsFocusController 与 WindowsState 管理 released、光标和恢复输入的回调；WindowsFocusMixin/WindowsPauseMixin 是原生窗口/暂停行为适配。Farm 的 Hotkey 包装会调用带 Hint 的恢复路径；命令工作流也可能重应用焦点状态。Shootout 并不调用 Farm 的启停状态机，且帧输出检查暂停、窗口活动及 mouse grabbed 状态。

**遗留边界**：初始化创建的 legacyMouseClickScheduler 没有 Tick 驱动。当前 Farm 不靠 VirtualMouse.mouseClick 的该队列完成动作；不要把“补一个 tick”当作无行为变化的整理。

<a id="modules"></a>
## 4. 模块职责、状态与清理

下表中的类均在[全量源码索引](#source-index)提供项目相对链接及公开入口。

| 模块/owner | 职责、入口与调用方 | 依赖与持有状态 | 初始化/清理时机 |
|---|---|---|---|
| 公共入口 Ultimate_Garden | onInitialize、id；Fabric 调用 | Mod Logger/资源命名空间 | 启动；无业务任务 |
| 客户端入口 Ultimate_GardenClient | onInitializeClient；组装所有服务和事件 | ConfigManager、Farm、MacroManager、Windows、ESP、observedUser、兼容队列 | 启动；账号变化/断线/退出通知各 owner |
| MacroManager | register/start/pause/reset/tick；快捷键、聊天调用 | 有序模块 Map，当前 Farm | 初始化注册；实际状态与清理由 Module 自己持有；Shootout 不在此 Map |
| FarmController / FarmState | startMacro/pauseMacro/resetMacro/tick、菜单和动作入口 | RouteController、FarmCommandController、菜单暂停快照、动作队列、选择等待、行计时 | 手动暂停取消动作；GUI 暂停保留所需快照；abortForWorldChange 清输入、命令、菜单和路线状态 |
| RouteController | tick、nextLine/previousLine、runCurrentMovement；Farm 调用 | 作物定义、行事件、SetLineYP、命令队列 | 行结束重置行 Tick；路线 reset/abort 由 Farm 管理；不自建全局事件 |
| FarmCommandController | spawnPoint/warpGarden/loadout/pestLoadout/spawnPests、tick/cancel/abortWorld | 自有 ActionScheduler、工作流、Loadout GUI 预期、PestLoadoutTimer、输入和 Focus | cancel 清动作；abortWorld 额外清世界相关状态/计时；GUI hold 不应偷偷推进队列动作 |
| FarmCommandInput | 旧字符、文本、Tap/Return、WarpGarden 等适配；当前没有生产构造调用 | VirtualInput、InputScheduler、输入中间状态 | 需调用方驱动 tick/cancel；当前 FarmCommandController 直接用 VirtualInput |
| PestLoadoutTimer | start/tick/cancel、finished 标记；FarmCommandController 调用 | 客户端 Tick 倒计数 | start 重置；业务消费 clearFinished；世界 abort/cancel 按实际路径区分 |
| Crop/Route 数据 | CropConfig/CropDefinition 为运行作物定义；旧 CropDatabase/RouteDatabase 仍存在 | 默认资源、自定义作物、Tick/槽位/移动键/换行设置 | 配置加载/编辑；勿新写业务绕过 CropConfig 回旧静态表 |
| 命令/聊天/Hint | CommandTree、CommandListener、CommandHelper、HintManager/FarmHints | MacroManager/Farm 引用、Hint 分类开关、聊天初始化选择 | 注册一次；不读日志文件监听服务器 |
| 快捷键 | MacroKeyBindings.register；原生 KeyMapping | KeyEdge、重绑定及 GUI/文本输入门控 | END Tick 检查；长按的边沿状态由私有 KeyEdge 对象持有 |
| 输入/窗口 | VirtualInput、PlayerGuiInput.virtualDispatch、Windows* | 持有键鼠、来源作用域、UI 暂态、Focus/拦截状态 | owner cancel/releaseAll；GUI 切换及世界变化清瞬态 |
| Shootout controller | 独立 start/stop/tick/aimFrame；快捷键、菜单、聊天触发 | targets/lamps/shots/current、固定原点、账号 revision、解、CD、确认、轮次、RTT | start 新原点/基线；stop 取消输入和确认、清缓存、END 日志；无自动走回原点 |
| Shootout 模型/采样 | ShootoutModel/Speed/BabyStall/Policy | 只读路径与弹道、每实体短窗口、固定优先级 | 模型首次加载；实体替换不继承旧身份；stop 清 targets |
| Shootout 输入/时钟 | ShootoutMouseInput、AimClock/TanhAim/AimCorrection | source scope、last step/next deadline、单轮 R | 目标周期/暂停/失焦重置步进；R 由原修正规则清理；不因为诊断重置 |
| Shootout 自动启动/RTT | ShootoutAutoStart、ShootoutRtt、ShootoutPongMixin | 单 pending 启动、连接上下文、单 Ping 请求与 token | 200ms 后重查；Stop/世界变化取消；迟到响应不复活 Macro |
| Anti-Check | ViewDeviationCheckManager、ExpectedViewTracker、ViewWarningClock | 单瞄准周期 expected YP/pending、警告锁存、Toast/声音 | 首次移动启用；确认发箭/停止清空；Farm 配置预留但无检测效果 |
| ESP | ESPManager → ESPRenderer → render/style | 提取回调注册表、不可变 Frame、generation、GPU allocator | 初始化封闭注册；断线失效；退出 close |
| PestESP | collect；ESPManager 调用 | PestConfig，只读加载实体 | 逐帧提取，BAT/SILVERFISH 合用既有目标样式，无专用长轨迹 |
| Shootout ESP | ZombieShootoutESP.register/collect | ESPConfig + controller InterceptDebug | 只显示 Macro 当前有效僵尸解；Macro 停止隐藏，并非独立预测器 |
| HUD/布局 | MacroStatusHud、ZombieShootoutHud、HudPanel、HudAdjustmentController | 配置、只读快照、每 HudId 布局、选中边缘、光标句柄 | render 可预览隐藏 HUD；finish 保存；断线/世界变化取消并释放光标 |
| 菜单/语言/主题 | UltimateGardenScreen、LanguageManager、ThemePalette、widget | 页面、sectionStates、编辑草稿、保存/复制反馈、Tooltip 时钟 | Screen init/build；关闭触发 Farm 菜单会话结束；折叠状态不等于业务开关 |
| 配置存储 | ConfigManager/ConfigIO/AccountDirectories | 模块对象、原 JSON、保护集合、账号身份/revision、锁 | 启动/账号激活加载；保存验证路径；未来 schema/备份失败禁止覆盖 |
| 被动采集 | ZombieShootCollector + Session/Tracker/Path/Block/Analyzer | 独立采集原点、Tag、有限缓存、单分析任务 | Macro+Logs+类别门控；停用拒收，后台排空；不会启动 Macro |
| 诊断写入 | ShootoutAimDiagnostics、ZombieShootLogger/Snapshot | 批次序号、有限关联表、拒绝限频、有界队列/写线程 | START/END；退出等待；写入失败仅停诊断，不作为游戏控制信号 |

<a id="flows"></a>
## 5. 业务调用链

### 5.1 Farm 路线、换行与菜单

`MacroKeyBindings → MacroManager.startByHotkey → FarmController.startMacroByHotkey/startMacro → RouteController.runCurrentMovement`。

正常 Tick：FarmController 检查世界/GUI → command.tick → route.tick。菜单会话活跃时走 tickMenuSession，不能继续照常执行路线和队列。RouteController 达行限 → stopMovement → 切 eventSelect、增加 eventCount → 可选 lineChangeMovement → continueLine → 可选 SetLineYP.setAlternate → startNextLine。额外移动阶段与视角/槽位阶段使用同一命令队列，不要在真正进入该阶段前抢先切槽。

路线全部完成：Pest 模式恢复 Focus 并 reset；Farm 模式排队等待500ms → warpGarden → resetRoute → 恢复攻击/移动。**不能把所有 Warp 等待都视为 commandRunning 已冻结行计时**，以具体置位点为准。

UltimateGardenScreen 的 begin/end 菜单会话与真实 Inventory/Chat/PauseScreen 来源不同；GuiPauseState 保存是否允许恢复，FarmController.menuActions（Deque）管 FIFO，手动 Pause/Reset 撤销恢复意图。关闭 GUI 不应绕过玩家明确停止操作再自动恢复。

### 5.2 Spawn / Warp / Loadout / Pest

动作通过 FarmController 的统一入口暂停/恢复和排队，FarmCommandController 管串行任务（其 Workflow 是状态枚举，不是独立工作流引擎）：

- spawnPoint/warpGarden：原生服务器命令与排队回调；依赖连接、世界和原动作状态。
- loadout：发送 loadout，按现有 Focus 状态重应用并等待完成。
- pestLoadout：打开 Loadout GUI → 有限轮询 screen → 选槽4 → 等待关闭/必要时关闭 → 恢复 Focus 与回调。
- spawnPests：先选槽7、打开 Loadout → 选槽5 → 关闭/恢复 → 模拟 Inventory 键阶段。
- automateTaskByMonitorMessage：Pest 开关开启才处理 `Pest have spawned in Plot`；现为格式码去除后的包含判断，不是严格服务器身份认证。排队100ms后再次检查开关并启动计时。
- Pest 倒计时是独立客户端 Tick 计时，菜单暂停/动作队列 hold 与其是否推进有不同规则；应同时检查 command.tick、timer.tick、cancel 和 abortWorld，勿将两种取消合并。

### 5.3 Zombie Shootout

1. 手动开关或默认未绑定的原生快捷键进入 start；自动启动复用 GAME 消息，匹配 Carnival Cowboy 的完整开场格式，单调200ms后重查上下文，再进入同一 start。
2. start 检查世界、Farm 冲突、输入占用等，固定脚部原点；扫描30格内加载 Zombie，按头盔+胸甲与 isBaby 分类八类。
3. 每 Tick 投影到 PATH_A/B，保留残差/局部偏移；每类速度窗口独立，默认0.30s，默认速度只在初期参与，成熟后使用实测，允许0。
4. 幼年卡住窗口默认0.40s，累计真实 X/Z 水平位移，与类型资源速度×20×秒数×0.50比较；完整有效窗口才判定。卡住时保留当前实际位置与幼年纵向点，移动提前量/运动横向偏移为零；重力补偿仍在。
5. 选择顺序：点亮灯 → 待补刀 → 普通僵尸按 Diamond Baby/Adult、Gold Baby/Adult、Iron Baby/Adult、Leather Baby/Adult，同类 first 顺序。锁定后不因新高优先级对象抢占当前周期。
6. select 无常规候选则每 Tick 最多重扫一次；仍空则 Relock 保留新一轮已处理存活目标的顺序。TargetRound 给每轮一个补刀机会，旧 Attempt 的验证结果不能污染新轮；当前锁失效有100ms宽限，未锁候选无宽限直接顺延。
7. 预瞄和发射前复算均调用同一 ShootoutModel.solve；灯为固定命中点。僵尸使用路径弧长、方向、局部偏移、未来 AABB 和既有30%/70%横向取点。原生 RTT/50 只补偿一次；没有有效 RTT 走安全等待。
8. 帧步骤读取最新有效解，用 Tanh：v=peak×tanh(e/c)，step=min(e,v×dt)，三维角速度预算、Yaw wrap 和 Pitch 限制。默认20Hz，可配置20–120整数；不在帧中额外求解或射击。
9. 外层容差触发修正，每轮只抽一次 R；进入 R 内结束，不制造随机目标偏移。射击阈值为外层×shootToleranceRatio，不能绕过活跃修正。Auto Aim/Auto Shoot 为独立有效门控。
10. 自动射击：有效目标/解、修正结束、实际角差、CD、目标碰撞验证通过后完整右键。Occlusion Check 默认OFF，仅跳过障碍门槛，目标命中体积验证仍保留。
11. Arrow Spawn Confirmation ON：新箭矢身份+出现时序确认，Owner 不参与；CD 到期可重试，自动超时安全停止。OFF：右键完成作为乐观派发。
12. 手动 Auto Shoot OFF：真实 Press 登记，原生 onButton RETURN 通知；不施加自动 CD/自动补点。确认 ON 等新箭矢，手动超时只清尝试；OFF 乐观完成。同一攻击完成入口负责下一目标。
13. 超过固定原点1格、断线/世界或账号变化、异常及手动操作统一 stop，清输入、解、确认、短轨迹、RTT、提示与日志；无自动返回站位或自动重启。

**功能专用数据**：[zombie_shoot_theoretical_models.json](src/client/resources/ultimategarden/zombie_shoot_theoretical_models.json) 包含 PATH_A 141点、PATH_B 151点、八类速度和经验箭矢参数。它不是通用 Minecraft 物理定律。模型点按弧长插值，端点反射是观测边界近似。八类初速 block/tick 依序为0.118768、0.166667、0.135417、0.187500、0.149144、0.208307、0.164702、0.230583；钻石幼年为估计值。弹道读取资源的初速经验式、drag=.99、gravity=.05，保持先位置更新再阻力/重力的顺序；不要另维护第二份速度表。

数值入口是 `ShootoutModel.solve(eye, DoubleFunction<Vec3> target)`：在0.001–40 Tick内以0.125 Tick粗分段寻找符号变化，再二分35次，返回首个有效根的 ticks/yaw/pitch/target。目标函数由 Controller 提供；灯返回固定命中点，移动僵尸按 cd/aim 等待+RTT/50+飞行Tick预测。Path.at 是相邻实测点的分段线性插值，match 对两路残差>1格或残差差<0.35格返回null；不是完美圆弧或重新拟合。collisionCheck 区分障碍和目标灯表面命中，使用局部已加载几何；与纯数值根的有效性是两道检查。

### 5.4 ESP / HUD / 诊断

共享渲染阶段见[事件矩阵](#events)。PestESP 检测 BAT/SILVERFISH，提取时做插值；Shootout ESP 读取当前 Macro 的 InterceptDebug，只画0.5格线框，皮革铜红、铁银白、金金、钻石青蓝，灯/未知/无解隐藏。当前实现**仍依赖 Macro 运行**，不要按旧“完全解耦”任务描述调用不存在的独立扫描器。

HUD 各自总开关和 Only Show While Running 独立；布局编辑强制预览但不改显示开关。HudAdjustmentController 按 HudId 保存归一化 x/y/width/height；不是一份全局 scale。两者共用 HudPanel 样式，Shootout 快照不应推进 ready/random/求解逻辑。

开发者 Logs：运行内存总开关每次启动OFF、默认折叠；账号 JSON 只存子项。17项被动采集使用独立 Session/Tag、固定原点、30格裁剪、48格/Tick的方块预算与缓存，后台分析只收不可变数据。日志分类器要求四件盔甲一致，与 Macro 的头胸规则不同；不一致进入 Diagnostics。

新增 Aim & Shot Diagnostics 是第18项独立子项，默认OFF。只写 `logs/ultimategarden/zombie shoot/aim-shot-diagnostics.jsonl`：START/END带绝对时间，普通行带 elapsedMs/clientTick。REGULAR/FIRE_RECHECK 解序号 → QUEUED（包括零量化）→ 原生调用返回后的 APPLIED → SHOT_GATE → SHOT_DISPATCH/ARROW_CONFIRMATION 关联。相同拒绝250ms采样，原因变化、首次进入射击容差仍拒绝、首次放行立即记录；未执行角差检查时存null，不为日志重算。普通行无批次ID，批次外已存在的尝试序号可null。诊断不推断60Hz故障原因，未采到实机对比过程。

写入器默认 Session 使用独立文件；诊断追加模式在END后关闭当前文件，后续START顺序追加。队列4096条、结构预算500000单位、批量128；诊断有固定终止槽。Stop/开关OFF拒收新样本，已接受数据后台排空；底层文件系统失败不能保证所有行落盘，不把失败写成成功。采集异常不应驱动 Macro 控制。

### 5.5 Expected View Deviation Check

调用链：`aimFrame → viewCheck.poll`（会话已有时检测无输入意外变化）；实际移动前 `ShootoutMouseInput → ViewObserver.before` 初始化/比较 → `submitted` 根据同一次 dx/dy、灵敏度和 invert 更新 expected → 原生 turnPlayer → `consumed` 对齐 actual。expected 从周期初始值累加，不每帧覆盖为 actual，也不与未来目标方向比较。ExpectedViewTracker 只保留当前预期值、pending 标记及异常锁存，无无限历史数组。

偏差是两个方向单位向量的 acos(clamp(dot,-1,1))，单位度；默认5°警告、15°停止，配置要求 warning<stop。一次连续异常只触发一轮，恢复正常才可再触发。ViewWarningClock 用单调时间控制默认5s，400ms节拍播放 `SoundEvents.NOTE_BLOCK_BELL` 的 SimpleSoundInstance，同时显示 `Potential View Check` SystemToast；非阻塞、不每Tick新建五秒任务。严重偏差调用注入的统一stop回调，原因 VIEW_DEVIATION_EXCEEDED。

resetAimCycle（目标周期变化/已确认或调试派发结束）与 stop 清 tracker、toast、sound 和 clock；等待CD/新箭矢期间保持当前检测周期。Farm 配置不调用任何该检测器。Anti-Check 本身不记录轨迹；独立开发者 Aim & Shot Diagnostics 的显式可选日志是另一条链，不将其数据作为 View Check 的控制输入。

<a id="config"></a>
## 6. 配置与状态读取

### 6.1 存储流程

ConfigManager 从 Fabric configDir/ultimategarden 建账号目录；AccountIdentity 校验 UUID/名称，AccountDirectories 用 account.json 验证归属、处理同 UUID 改名、拒绝不可信目录/重定向，锁保护多步存储。实际模块文件路径由 ConfigManager 的文件映射 及 AccountDirectories.file 产生，不自行拼另一套账号路径。

load：先默认对象 → ConfigIO 严格单 JSON object → schema 校验/字段迁移与修复 → 需要时备份。未来 schema、身份不可信或备份失败进入受保护状态，不能覆盖原文件。save：保留原 JSON 未知字段，调用模块序列化，再原子替换；ATOMIC_MOVE 不支持时回退普通替换。账号复制先 stage/backup，再逐文件替换；失败做回滚并报告无法恢复项，不是跨文件数据库事务。

菜单、HUD、业务共享 ConfigManager 持有的模块对象；运行态（Macro running、Logs master、输入所有权、待确认、实时轨迹）不等于配置开关。切账号重置/加载同一组模块对象并增加 revision；不要缓存旧账号 JSON 成为第二状态源。

### 6.2 模块与权威字段

| 文件 / 类 | 内容及状态边界 |
|---|---|
| general.json / GeneralConfig | 菜单暂停/恢复、Hint 总开关及分类；菜单子项禁用不清保存值 |
| farm.json / FarmConfig | 当前作物、模式、模拟作物选择，运行状态在 FarmState |
| ui.json / UIConfig | 语言、主题、accent、透明度；LanguageManager/ThemePalette 读取 |
| crops.json / CropConfig、CropDefinition | 作物移动键、行数、行 Tick、额外移动、换行槽位/角度相关设置；默认来自 default-crops.json |
| pest.json / PestConfig | autoPestLoadout、pestLoadoutDelayTicks（默认2500）、pestESPEnabled |
| esp.json / ESPConfig | general、目标 box/fill/tracer 样式和 Shootout 框开关；独立 schema/migration 方法，不假定统一版本 |
| random_delay.json / RandomDelayConfig | 每 Key 独立 minTicks/maxTicks；sample 输出 Tick，调用点决定抽样频率 |
| hud.json / HudConfig | Farm/Shootout 总开关与 only-running；compact/detail 内容；bounds 与 zombieShootoutBounds 独立，保留旧 Farm 布局 |
| anti_check.json / ViewDeviationConfig | farm/zombieShootout 独立 enabled=false、warning=5°、stop=15°、duration=5s；Farm 目前仅配置 |
| zombie_shootout.json / ZombieShootoutConfig | Setting enum 为完整字段/范围权威；Auto Aim ON、Auto Shoot OFF、Manual Restriction ON、Occlusion OFF；运行 enabled 不持久化 |

Shootout 当前默认：peak=300°/s、curvature=15°、aimUpdateRateHz=20（20–120整数）、aimCompletionToleranceDeg=.50°、aimCorrectionRandomRatio=.25、shootToleranceRatio=1.50 → 射击容差.75°及随机结束阈值0–.125°。八类 speedWindowsSeconds 各.30s；baby stall .40s/.50；CD .55s+jitter 0–.10s；baby顶部偏移.10格；RTT测量15s且最低.5s。snapAngleDeg 仅兼容旧配置，不再参与Tanh或菜单。以上只用于新/缺失字段，保留用户值。

`developerLogs` 子项可持久化；Logs 总开关仅 ZombieShootLogGate 内存持有。所有静态模型、日志数据和实时样本都不应塞进配置 JSON。

### 6.3 菜单装配与层级

入口 [MenuController.open](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/MenuController.java) 创建 [UltimateGardenScreen](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java)。Page 枚举为 Home / Farm / Pests / ESP / Hotkeys / Settings / Misc；build 按页装配，positionContent 结合折叠区的 hiddenHeightBefore 调整滚动内容。sectionStates 保存当前屏幕的折叠状态，不是业务启用状态。

| 页面/区域 | 装配方法和内容 |
|---|---|
| Home | home；当前农业概览及状态 |
| Farm | farm、cropLineChangeFields、applyCropDefinition；作物/模式、原有动作、作物定义编辑 |
| Pests | pests；PestLoadout 开关、倒计时和 Pest ESP 入口 |
| ESP | espSettings、espTarget、espComponent；general 与 Pest 样式；Carnival → Zombie Shootout ESP → Show Intercept Box |
| Hotkeys | hotkeys；显示 MacroKeyBindings.getBindings 的原生重绑定结果 |
| Settings | settings；General、Appearance、Hint、HUD、Anti-Check、账号配置、Developer Options |
| Settings → HUD | hudSettings；调整布局按钮、Farm/Shootout 独立总开关及 only-running；既有 Farm Compact/Detail 内容仍存在 |
| Settings → Anti-Check | randomDelaySettings、viewDeviationSettings；随机延迟与 Farm/Shootout 分开的视角检测参数 |
| Settings → Developer Options | developerSettings；Carnival → Zombie Shootout → Logs 总开关/折叠及各记录类别 |
| Misc | shootoutSettings；Carnival → Zombie Shootout 的控制与数值设置 |

Shootout 当前控件顺序：启停、Auto Aim（子项 Auto Shoot，再子项 Occlusion Check；Manual Shot Restriction 与 Auto Shoot 同级）、Auto Start、Arrow Spawn Confirmation、状态；peak/curvature/rate；分隔线；外层容差及内缩两个 ratio；分隔线；CD及内缩 jitter；分隔线；折叠速度采样组（八个 window + 两个 stall 参数）、组外 Baby Offset；分隔线；RTT interval、Ping、目标信息。Snap Angle 控件已移除。

toggle 的 value supplier 与 enabled supplier 分开：禁用只限制编辑，保留真实保存值及 ON/OFF 外观。数值框使用当前 draft，act 捕获校验错误，saved 显示保存结果；不要从显示字符串反推运行状态。HelpTooltip 按 frame/clip/hover 管理浮层，MenuInputGraphics 保留文本选中、光标与输入法预编辑的渲染路径；页面重建时应留意未保存 draft 和 focus。

### 6.4 快捷键、客户端命令与网络边界

[MacroKeyBindings](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java) 用原生 KeyMapping 注册，实际绑定取玩家 Controls；下列只列源码初始默认值。Num2 旧采集快捷键不在当前注册表中，不应照旧分支文档假设存在。

| 动作 | 默认键 |
|---|---|
| Farm Start/Pause；Reset | Num+；Num− |
| Next/Previous Line | ]；[ |
| Set Spawn；Warp Garden | Num*；Num/ |
| Pest Timer；Cancel Pest Timer | Num7；Num8 |
| Loadout；Select Crop | Num9；NumDecimal |
| Zombie Shootout；Toggle HUD | None；None |
| Open Menu | M |
| Mouse Focus | U |

以上绑定的文本来源由 LanguageKeys/LanguageManager 和语言 JSON 共同处理；按键的 display name 使用原生翻译。START/STOP 边沿与文本输入门控见 register/WindowsInputMixin，鼠标重绑定还走 WindowsMouseMixin。

[CommandTree.register](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandTree.java) 注册客户端 `/ultimategarden`，含 help/helper、hotkey、initialize、menu 及 unknown fallback。CommandHelper.initialize 进入 Farm 的聊天选择流程；CommandListener.ALLOW_CHAT 对被消费的初始化输入返回 false，不发往服务器。GAME 接收仅观察，不隐藏原消息。

网络使用分两类：FarmCommandController 通过现成连接发送原有业务命令；Shootout 唯一新增探测是 ServerboundPingRequestPacket，ShootoutPongMixin 匹配 handlePongResponse。后者先判本模块 token，再将更新派发到客户端线程；无关响应交原版，属于本模块但已过期的 token 会被消费并忽略。ShootoutRtt 用固定前缀+不回绕序号、连接对象和当前 request 匹配；单请求超时5s，间隔从请求发送时间算起。当前是独立单请求跟踪器，没有构造原生 PingDebugMonitor；不改 KeepAlive、不使用外部网络服务。


<a id="reuse"></a>
## 7. Reusable Components

本表保留重构前评估，已提取能力以12.15最终入口为准。状态分类中的专用模型仍不应提升为通用默认。

| 能力与入口（相对链接见源码索引） | 实际调用方 / 相近实现 | 参数和单位 | 状态/生命周期 | 复用条件 | 当前状态 |
|---|---|---|---|---|---|
| [ActionScheduler](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/scheduler/ActionScheduler.java).schedule/scheduleDelayTicks/scheduleAfterTicks/tick/clear | FarmCommandController、SetLineYP；另有无驱动兼容实例 | ms或Tick，串行FIFO | owner持有 current/queue/remaining/paused，owner清 | 客户端Tick；取消时释放所持输入；精确间隔用AfterTicks | 可直接复用 |
| [MacroKeyBindings](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java).KeyEdge.rising（私有） | MacroKeyBindings；Shootout toggle | 按下boolean | 每绑定独立边沿 | 原生重绑定、GUI文本门控 | 嵌入绑定类的私有辅助对象，非公开服务 |
| [PlayerGuiInput](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/gui/PlayerGuiInput.java).virtualDispatch / request / consume | Virtual输入、Keyboard/GUI Mixins、Farm | 来源作用域/GUI种类 | 暂态标记，Farm Tick/abort清 | 必须区分动作发起与真实玩家输入 | 可直接复用，先读各Mixin入口 |
| [VirtualInput](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/virtual/VirtualInput.java) suspendForMenu/resumeAfterMenu/releaseAll | FarmController、命令、Route | 键名、槽位；不是服务器Tick | 自身held快照，owner释放 | 客户端线程、同一owner，不混外部输入 | 可直接复用包装，业务决策仍Farm专用 |
| [ShootoutMouseInput](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutMouseInput.java).aim/click/cancel | Shootout；与VirtualMouse行为不同 | yaw/pitch度、peak度/s、c度、dt秒 | dispatch、右键ownership、观察回调 | 本地线程/抓鼠标/合法视角；原生敏感度及反转 | 嵌入业务 |
| [ShootoutTanhAim](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutTanhAim.java).rotation/rawDelta、[ShootoutAimCompletion](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutAimCompletion.java).error | Shootout、Anti-Check/诊断 | 三维角度、raw鼠标量、灵敏度 | 纯数值，无历史 | MC坐标约定；不要把单位向量夹角当Yaw+Pitch平方和 | 可抽通用纯数学 |
| [ShootoutAimClock](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutAimClock.java).step/reset | aimFrame；与ActionScheduler用途不同 | 纳秒、Hz20–120、返回秒 | next/last/rate，暂停/目标周期清 | 每帧最多调用一步，无catch-up | 嵌入业务，但时间边界清楚 |
| [ShootoutShotClock](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/shot/ShootoutShotClock.java) / TargetLoss / AutoStart / Rtt | Shootout；各自超时语义不同 | 单调ns、s、RTT ms | CD、连续失效、pending/context、token | 不共享一份全局时钟状态；重置由业务明确调用 | 专用策略外壳，可共享计时原语 |
| [ShootoutSpeed](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/model/ShootoutSpeed.java).observe / BabyStall.observe | 每Target持有；Pest只有倒计时 | arc block/tick 或 X/Z+ns，窗口s→Tick | 有界样本；缺口/突变清，保留真实0 | 不能把signed均值与累计水平距离混为一谈 | 嵌入业务 |
| [ZombieShootEntityTracker](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/entity/ZombieShootEntityTracker.java).observe/identify | 被动采集；Macro另有UUID/对象跟踪 | entityID/UUID/对象/tick/世界位置 | Tag及段落，retain/missing/session结束 | 不依靠entityID永久身份；对象替换分段 | 嵌入业务，身份策略可抽 |
| [ZombieShootSnapshot](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ZombieShootSnapshot.java)、Logger.offer/endBatch/close | 被动采集与AimShot诊断 | immutable map→JSONL，结构预算而非字节数 | 队列/worker/terminal；显式close | 世界/Entity不能跨线程；不要在渲染线程直接写磁盘 | 批写可复用，路径/摘要仍嵌入业务 |
| [ConfigIO](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/storage/ConfigIO.java).read/write/backup/replaceTransaction | ConfigManager/AccountDirectories | UTF-8 JSON object、PathCheck | 临时文件/备份；失败回滚报告 | 上层负责schema/路径和账号校验 | 可直接复用 |
| [ConfigManager](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java)、模块load/toJson | 菜单、HUD、全部业务 | 模块对象/原JSON、字段范围 | 单一共享对象、protectedFiles/revision | 不创建旁路写入；保留未知字段和保存值 | 存储框架可复用，模块映射嵌入工程 |
| [CollapsibleSection](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/CollapsibleSection.java)、[ToggleSwitch](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/ToggleSwitch.java)、EditBox/input、[HelpTooltip](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/HelpTooltip.java) | UltimateGardenScreen各页 | GUI缩放坐标、suppliers | sectionStates、hover时间；重建清focus | disabled不能把值强制变false；折叠不影响业务 | 控件可直接复用，Screen装配助手可抽 |
| [LanguageManager](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/language/LanguageManager.java)、[ThemePalette](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/theme/ThemePalette.java)、[RoundedRect](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/render/RoundedRect.java) | 菜单/HUD/Tooltip | key/格式参数、ARGB/透明度 | 配置驱动；不另存第二主题状态 | en_us/zh_cn同步，动态枚举键补齐 | 可直接复用 |
| [HudPanel](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/HudPanel.java)、[HudAdjustmentController](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/HudAdjustmentController.java) | 两个HUD | 归一化bounds、HudId、GUI坐标 | selected/drag/layout/cursors，finish释放 | render预览与运行显示分开；切世界取消 | 面板可复用；编辑器仍有具体HudId耦合 |
| [ESPManager](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPManager.java).register/registerFixed、[ESPTarget](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPTarget.java)/Style | Pest与Shootout ESP | 世界AABB、颜色/alpha/线宽 | 注册一次、Frame/generation/GPU owner | 提取与绘制分离；before initialize注册 | 可直接复用 |
| [ESPBoxRenderer](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPBoxRenderer.java)/FillRenderer/TracerRenderer | ESPRenderer | 世界坐标与camera offset、timeMs | GPU buffers由上层持有 | 合法渲染阶段/匹配管线/深度状态 | 可直接复用几何输出 |
| [ZombieShootBlockCollector](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootBlockCollector.java).boundedView/offset、OcclusionAnalyzer.intersects | 采集；Shootout.localView/碰撞循环相近 | BlockPos、局部AABB、线段 | 加载范围/缓存有效期由owner保证 | 未加载不是Air；视觉遮挡不等于物理碰撞 | 几何可抽，缓存策略业务专用 |
| [GuiPauseState](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/gui/GuiPauseState.java)、[FarmController](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java).menuActions | Farm菜单/GUI；Shootout只等待GUI不走此流程 | 会话source、恢复许可、FIFO动作 | snapshot、active/open、abort | 恢复只一次，用户stop优先 | 当前Farm适配清楚，不能直接套Shootout |
| [HintManager](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/hint/HintManager.java)、[FarmHints](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/hint/FarmHints.java) | 命令、Farm、Focus | hint key/参数/分类开关 | 配置决定输出，非游戏决策 | 不用Hint文本反向判断业务状态 | 通知可复用，Farm映射专用 |
| [ShootoutModel](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/model/ShootoutModel.java)/Policy、[RouteData](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteData.java)/作物定义 | 各自业务 | 路线弧长、经验弹道、类别或农作物 | 只读模型/配置 | 保留场景单位与证据边界 | 专用模型 |

<a id="extraction"></a>
## 8. 建议提取的接口边界

以下为重构前的接口建议；本轮已实现的范围、实际入口和保留政策见12.3–12.15。未纳入本轮的建议仍不是可调用服务。

| 现有实现位置 | 具体潜在调用场景 | 建议接口边界与不应迁入的内容 |
|---|---|---|
| [ShootoutMouseInput](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutMouseInput.java) + [ShootoutTanhAim](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutTanhAim.java) + ViewObserver | 将来其他只需本地平滑转向的客户端功能 | 输入owner、相对位移提交、敏感度映射、消费回调；纯旋转计算与提交器分离。不要迁入僵尸目标、射击ready或固定路径 |
| [ShootoutAimClock](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutAimClock.java)、[ActionScheduler](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/scheduler/ActionScheduler.java)、[PestLoadoutTimer](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/pest/PestLoadoutTimer.java)、[ViewWarningClock](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ViewWarningClock.java) | HUD动画采样或新的低频观测器 | 注入单调Clock、到期/跳帧策略与reset；TickFIFO保留独立类型，不统一成一个含糊delay(ms) |
| [ShootoutSpeed](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/model/ShootoutSpeed.java)/BabyStall、[ZombieShootEntityTracker](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/entity/ZombieShootEntityTracker.java) | 新实体速度HUD或加载范围研究 | IdentityKey、连续样本判据、bounded window和聚合器；默认速度表、0.5阈值和路径投影留业务层 |
| [ZombieShootLogger](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ZombieShootLogger.java)/Snapshot + [ShootoutAimDiagnostics](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ShootoutAimDiagnostics.java) | Farm动作阶段诊断、Pest事件审计 | JsonlSink.offer(immutableRecord)、Batch.start/end、路径策略、失败/丢样通知；线程与队列可共用设计，勿将采集Session元数据强加所有日志 |
| [FarmCommandController](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/action/FarmCommandController.java) 的工作流/取消、[GuiPauseState](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/gui/GuiPauseState.java)/[FarmController](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java).menuActions | 新需要等待库存GUI的宏动作 | CancellableActionOwner、GuiPauseSnapshot、完成/失败回调；Loadout槽位、命令字符串和Farm恢复决策留业务 |
| [FarmController](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java)与ShootoutController的互斥supplier | 未来第三个输入Macro | InputLease(owner, keyboard/move/buttons)、释放与冲突原因；当前不是集中仲裁器，不能仅靠register就认为互斥完成 |
| [AccountDirectories](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/storage/AccountDirectories.java) + [ConfigIO](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/storage/ConfigIO.java) + [ConfigManager](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java) | 为新的业务模块提供账号设置 | AccountStore、ModuleCodec(default/load/validate/toJson/schema)；UI复制政策与具体Module enum留集成层 |
| [UltimateGardenScreen](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java)装配助手/[CollapsibleSection](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/CollapsibleSection.java) | 新业务层级菜单及批量数值设置 | SettingBinding、父子enabled supplier、独立value supplier、Section布局模型；不把控件当作配置源 |
| [HudAdjustmentController](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/HudAdjustmentController.java)/[HudPanel](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/HudPanel.java) | 新增第三个HUD | HudDescriptor(id,renderPreview,bounds,minSize)、布局存储适配；编辑器不直接引用每个具体Macro |
| [ESPManager](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPManager.java) + 两种局部BlockGetter + OcclusionAnalyzer | 其他只读实体/环境可视化 | LoadedGeometryView、ray/segment-AABB纯函数、snapshot provider；半径30/3、缓存寿命及目标选择留业务 |
| [CommandListener](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandListener.java) + Farm消息包含判断 + [ShootoutAutoStart](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ShootoutAutoStart.java).matches（含文本规范化） | 新NPC事件、Pest消息变体 | 单一消息dispatcher和格式规范化、来源上下文；不能把规范化字符串宣称服务器身份认证 |

<a id="mixins"></a>
## 9. Mixin 清单

以两个实际资源配置文件为注册权威；下面按源码列出目标和注入方法字符串。Accessor/Invoker是接口，不是独立Tick服务。示例Mixin保留模板性质，不应当成业务调度入口。

| Mixin / Accessor | 目标 | 注入方法 / 访问字段 | 作用 |
|---|---|---|---|
| [ExampleClientMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/ExampleClientMixin.java) | `Minecraft.class` | `run` | 模板 run HEAD，无业务实现 |
| [InventoryInputMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/InventoryInputMixin.java) | `KeyMapping.class` | `click`、`consumeClick`、`release` | 跟随原生 KeyMapping click/consume/release 保留 Inventory 来源次序 |
| [MenuFarmInputMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/MenuFarmInputMixin.java) | `Minecraft.class` | `setScreen`、`tick`、`handleKeybinds` | 真实 GUI 来源、菜单按键与 Farm 输入恢复的原生桥接 |
| [ShootoutAimFrameMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/ShootoutAimFrameMixin.java) | `Minecraft.class` | `runTick` | 在每帧原生累计鼠标消费前调用 aimFrame |
| [ShootoutPongMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/ShootoutPongMixin.java) | `ClientPacketListener.class` | `handlePongResponse` | 仅消费模块自有 Ping token；回到客户端线程更新 RTT |
| [WindowsFocusMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsFocusMixin.java) | `Minecraft.class` | `setScreen`、`isWindowActive` | 释放状态下的窗口活跃/光标适配 |
| [WindowsInputMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsInputMixin.java) | `KeyboardHandler.class` | `keyPress(JILnet/minecraft/client/input/KeyEvent;)V` | 键盘物理输入、停止键及 GUI 来源通知/阻断 |
| [WindowsMissTimeMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsMissTimeMixin.java) | `MouseHandler.class` | `grabMouse` | grabMouse 后设置 missTime 的现有兼容行为 |
| [WindowsMouseMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsMouseMixin.java) | `MouseHandler.class` | `grabMouse`、`onButton`、`onScroll`、`onMove`、`handleAccumulatedMovement` | 鼠标来源、布局编辑、手动射击门控、拦截和释放 |
| [WindowsPauseMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsPauseMixin.java) | `Minecraft.class` | `pauseIfInactive` | 释放焦点时跳过原有 inactive 暂停入口 |
| [ExampleMixin.java](src/main/java/com/kuangsanfeng/ultimategarden/mixin/ExampleMixin.java) | `MinecraftServer.class` | `loadLevel` | 模板服务端 loadLevel HEAD，无业务实现 |
| [KeyboardHandlerAccessor.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/KeyboardHandlerAccessor.java) | `KeyboardHandler.class` | `Invoker:keyPress` | 虚拟键盘调用原生键事件 |
| [KeyMappingAccessor.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/KeyMappingAccessor.java) | `KeyMapping.class` | `Accessor:key` | 取得用户重绑定后的原生键 |
| [ShootoutMouseAccessor.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/ShootoutMouseAccessor.java) | `MouseHandler.class` | `Accessor:accumulatedDX`、`Accessor:accumulatedDY`、`Accessor:isRightPressed`、`Invoker:turnPlayer`、`Invoker:onButton` | 保存/替换累计相对位移并调用原生 turnPlayer；原生右键状态 |

注册资源：[公共 Mixin](src/main/resources/ultimate_garden.mixins.json)、[客户端 Mixin](src/client/resources/ultimate_garden.client.mixins.json)。两者 required=true、JAVA_25、defaultRequire=1；Minecraft 升级应同时核对注入签名与 tests 中的静态 wiring 审查。

<a id="source-index"></a>
## 10. 全量源码导航索引

> 入口链接已跟随搬包更新；当前包分层与完整清单见第13节。兼容外观仍转接同一实现。

覆盖本次快照全部 **133 个生产 Java 文件**。下面的“入口”由当前声明核对，包含构造器之外的 public/protected 方法（记录构造器名也可能出现在列表中）；“内部关键方法”只用于定位，不表示公开复用接口。枚举、record、接口的隐式方法、无访问修饰符的方法及重载参数请打开文件核对。

“类型引用方”来自生产源码对该类型名的直接静态引用（包含 import、签名与方法体），不是运行时调用图；长列表仅显示前五个，具体实际调用链以第2–5章为准。标为“无外部类型引用”不等于 Fabric/Mixin/资源不会调用。各组的状态 owner、依赖与释放规则见第4章，纯数值/record 不应自行注册事件。

### 10.1 `client`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java) | `onInitializeClient`、`getConfigManager`、`getMacroManager`、`getFarmController`、`getWindowsFocusController`、`getWindowsInputInterceptor`、`getWindowsState` | [ViewDeviationCheckManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ViewDeviationCheckManager.java)、[HintManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/hint/HintManager.java)、[MacroKeyBindings.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java)、[CropDatabase.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/crop/CropDatabase.java)、[FarmHints.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/hint/FarmHints.java)（另 10 个文件引用） |

### 10.2 `client/anti_check`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/anti_check/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [ExpectedViewTracker.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ExpectedViewTracker.java) | `active`、`begin`、`submit`、`consumed`、`stationary`、`evaluate`、`clear` | [ViewDeviationCheckManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ViewDeviationCheckManager.java) |
| [ViewDeviationCheckManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ViewDeviationCheckManager.java) | `clear`、`poll`、`before`、`submitted`、`consumed`、`tick` | [ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) |
| [ViewWarningClock.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ViewWarningClock.java) | `begin`、`active`、`pulse`、`clear` | [ViewDeviationCheckManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ViewDeviationCheckManager.java) |

### 10.3 `client/command`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/command/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [CommandHelper.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandHelper.java) | `execute`、`showHotkeys`、`initialize` | [CommandTree.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandTree.java) |
| [CommandListener.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandListener.java) | `register` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java) |
| [CommandTree.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandTree.java) | `register` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java) |

### 10.4 `client/config`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/config/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [AccountDirectories.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/storage/AccountDirectories.java) | `Profile`、`legacy`、`lock`、`list`、`resolve`、`verified`、`file`、`hasLegacy`、`regular` | [ConfigIO.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/storage/ConfigIO.java)、[ConfigManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java)、[UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |
| [AccountIdentity.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/storage/AccountIdentity.java) | 以类型/字段或注入为入口 | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[AccountDirectories.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/storage/AccountDirectories.java)、[ConfigManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java) |
| [ConfigIO.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/storage/ConfigIO.java) | `CopyResult`、`read`、`backup`、`backupForMigration`、`write`、`replaceTransaction` | [AccountDirectories.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/storage/AccountDirectories.java)、[ConfigManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java) |
| [ConfigManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java) | `getViewDeviationConfig`、`getZombieShootoutConfig`、`activateAccount`、`matchesAccount`、`currentAccount`、`accountRevision`、`setCurrentAccountCopyGuard`、`isPendingRestart`、`pendingRestartModules`、`accountProfiles`、`hasLegacyConfiguration`、`getHudConfig`、`getRandomDelayConfig`、`getPestConfig`、`getESPConfig`、`getGeneralConfig`、`getFarmConfig`、`getCropConfig`、`getUIConfig`、`save`、`saveFarmConfig`、`saveUIConfig`、`saveAll`、`reset`、`CopyPlan`、`CopyReport`、`copyConfiguration`<br>内部：`verifyStorage`、`load`、`readFields`、`saveUnlocked`、`copyUnlocked` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[AccountDirectories.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/storage/AccountDirectories.java)、[CropConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/farm/CropConfig.java)、[MacroKeyBindings.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java)、[ESPManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPManager.java)（另 9 个文件引用） |
| [CropConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/farm/CropConfig.java) | `load`、`getCrop`、`get`、`toJson`、`editableValues`、`updateCrop`、`wasRepaired`、`resetToDefault` | [ConfigManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java)、[RouteController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteController.java) |
| [CropData.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/farm/CropData.java) | `getCropType`、`getName`、`getTickLimit`、`getLines`、`getMovementKeys`、`getYaw`、`getPitch`、`getYawA`、`getPitchA`、`getYawB`、`getPitchB` | 无外部类型引用；核对资源或 owner 装配 |
| [CropDefinition.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/farm/CropDefinition.java) | `keys`、`getName`、`getType`、`isExecutable`、`getTickLimit`、`getLines`、`LineChangeMovement`、`LineChangeYawPitch`、`lineChangeMovement`、`lineChangeYawPitch`、`useAlternateSlot`、`getYaw`、`getPitch`、`getHintYaw`、`getHintPitch`、`getMovementKeys` | [CropConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/farm/CropConfig.java)、[CropDatabase.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/crop/CropDatabase.java)、[RouteController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteController.java) |
| [ESPConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/display/ESPConfig.java) | `isShowShootoutInterceptBox`、`setShowShootoutInterceptBox`、`clearForAccount`、`hasTarget`、`registerTarget`、`getTarget`、`isThroughWalls`、`setThroughWalls`、`getCycleDurationMs`、`setCycleDurationMs`、`resetToDefault`、`getBox`、`getFill`、`getTracer`、`snapshot`、`isEnabled`、`setEnabled`、`getColorMode`、`setColorMode`、`getColor`、`setColor`、`getAlpha`、`setAlpha`、`getLineWidth`、`setLineWidth`、`load`、`requiresMigration`、`toJson` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[ConfigManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java)、[PestESP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/PestESP.java)、[ZombieShootoutESP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/ZombieShootoutESP.java)、[UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |
| [FarmConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/farm/FarmConfig.java) | `getSchemaVersion`、`getSelectedCropType`、`setSelectedCropType`、`getSelectedFarmMode`、`setSelectedFarmMode`、`getSelectedSimulatedCropType`、`setSelectedSimulatedCropType`、`getSelectedCropTypeId`、`getSelectedFarmModeId`、`getSelectedSimulatedCropTypeId`、`cropNameFromId`、`farmModeFromId`、`resetToDefault` | [ConfigManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java)、[FarmController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java)、[MenuController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/MenuController.java)、[UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |
| [GeneralConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/GeneralConfig.java) | `hintEnabled`、`setHintEnabled`、`loadHints`、`hintsToJson`、`isAutoPauseFarmWhenOpeningMenu`、`setAutoPauseFarmWhenOpeningMenu`、`isAutoResumeFarmAfterGuiClose`、`setAutoResumeFarmAfterGuiClose`、`getSchemaVersion`、`isShowModChatHints`、`setShowModChatHints`、`resetToDefault` | [ConfigManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java)、[HintManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/hint/HintManager.java)、[UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |
| [HudConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/display/HudConfig.java) | `Bounds`、`bounds`、`setBounds`、`x`、`y`、`width`、`height`、`isFarmOnlyWhileRunning`、`setFarmOnlyWhileRunning`、`isShowZombieShootoutHud`、`setShowZombieShootoutHud`、`isZombieOnlyWhileRunning`、`setZombieOnlyWhileRunning`、`showFarm`、`showZombie`、`isShowHud`、`setShowHud`、`getMode`、`setMode`、`compact`、`detail`、`visible`、`setCompact`、`setDetail`、`resetToDefault`、`load`、`toJson` | [ConfigManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java)、[HudAdjustmentController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/HudAdjustmentController.java)、[MacroStatusHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/MacroStatusHud.java)、[ZombieShootoutHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/ZombieShootoutHud.java)、[UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |
| [PestConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/farm/PestConfig.java) | `isPestESPEnabled`、`setPestESPEnabled`、`getSchemaVersion`、`isAutoPestLoadout`、`setAutoPestLoadout`、`getPestLoadoutDelayTicks`、`setPestLoadoutDelayTicks`、`resetToDefault` | [ConfigManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java)、[FarmCommandController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/action/FarmCommandController.java)、[PestESP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/PestESP.java) |
| [RandomDelayConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/RandomDelayConfig.java) | `defaults`、`Range`、`resetToDefault`、`get`、`set`、`sample`、`load`、`toJson` | [ConfigManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java)、[FarmCommandController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/action/FarmCommandController.java)、[RouteController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteController.java)、[SetLineYP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/SetLineYP.java)、[UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |
| [UIConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/display/UIConfig.java) | `getMenuTransparency`、`setMenuTransparency`、`getLanguage`、`setLanguage`、`getSchemaVersion`、`getUiThemeMode`、`setUiThemeMode`、`getAccentColor`、`setAccentColor`、`resetToDefault` | [ConfigManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java)、[UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java)、[ThemePalette.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/theme/ThemePalette.java) |
| [ViewDeviationConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/ViewDeviationConfig.java) | `warning`、`stop`、`duration`、`farm`、`zombie`、`resetToDefault`、`load`、`toJson` | [ViewDeviationCheckManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ViewDeviationCheckManager.java)、[ConfigManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java) |
| [ZombieShootLogCategory.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/shootout/ZombieShootLogCategory.java) | `folder`、`zombie`、`legacy` | [ZombieShootoutConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/shootout/ZombieShootoutConfig.java)、[ZombieShootCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootCollector.java)、[ZombieShootLogGate.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootLogGate.java)、[ZombieShootSession.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ZombieShootSession.java)、[ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java)（另 1 个文件引用） |
| [ZombieShootoutConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/shootout/ZombieShootoutConfig.java) | `window`、`isOcclusionCheckEnabled`、`setOcclusionCheckEnabled`、`setLogOptionsChanged`、`logCategories`、`logEnabled`、`setLogEnabled`、`isManualShotRestrictionEnabled`、`setManualShotRestrictionEnabled`、`manualShotRestrictionEditable`、`manualShotRestrictionEffective`、`setAutomationChangeListener`、`isAutoAimEnabled`、`isAutoShootEnabled`、`autoShootEffective`、`setAutoAimEnabled`、`setAutoShootEnabled`、`isArrowSpawnConfirmationEnabled`、`setArrowSpawnConfirmationEnabled`、`isAutoStartZombieShootout`、`setAutoStartCancellation`、`setAutoStartZombieShootout`、`resetToDefault`、`get`、`set`、`load`、`toJson` | [ConfigManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java)、[ZombieShootLogGate.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootLogGate.java)、[ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java)、[UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |

### 10.5 `client/hint`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/hint/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [HintManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/hint/HintManager.java) | `isEnabled`、`printFor`、`printHint`、`print`、`printRequired`、`printNull` | [CommandHelper.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandHelper.java)、[CommandTree.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandTree.java)、[FarmController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java)、[FarmHints.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/hint/FarmHints.java)、[WindowsFocusController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/window/WindowsFocusController.java) |
| [KeyNameMap.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/hint/KeyNameMap.java) | `get` | 无外部类型引用；核对资源或 owner 装配 |

### 10.6 `client/hotkey`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/hotkey/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [MacroKeyBindings.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java) | `Binding`、`getBindings`、`getMenuKeyName`、`matchesShootoutMouse`、`matchesShootoutKey`、`matchesMenuKey`、`clearMenuClicks`、`isMenuKeyPhysicallyDown`、`rising`、`getKeyName`、`register` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[HintManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/hint/HintManager.java)、[WindowsInputMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsInputMixin.java)、[WindowsMouseMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsMouseMixin.java)、[UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |

### 10.7 `client/input`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/input/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [PlayerGuiInput.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/gui/PlayerGuiInput.java) | `isVirtualDispatch`、`virtualDispatch`、`request`、`consume`、`inventoryClick`、`inventoryConsumed`、`inventoryReleased`、`clearInventoryRequest`、`clearTransient`、`clear` | [VirtualKeyboard.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/virtual/VirtualKeyboard.java)、[FarmController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java)、[InventoryInputMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/InventoryInputMixin.java)、[WindowsInputMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsInputMixin.java) |
| [VirtualInput.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/virtual/VirtualInput.java) | `HeldActions`、`reapplyHeldState`、`suspendForMenu`、`resumeAfterMenu`、`actionKeyDown`、`actionKeyUp`、`actionMouseDown`、`actionMouseUp`、`keyDown`、`keyUp`、`keyClick`、`slot`、`typeCharacter`、`mouseDown`、`mouseUp`、`mouseClick`、`mouseMove`、`releaseAll` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[FarmController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java)、[FarmCommandController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/action/FarmCommandController.java)、[FarmCommandInput.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/action/FarmCommandInput.java)、[RouteController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteController.java)（另 3 个文件引用） |
| [VirtualKeyboard.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/virtual/VirtualKeyboard.java) | `setInputAllowed`、`reapplyHeldState`、`keyDown`、`keyUp`、`keyClick`、`slot`、`typeCharacter`、`releaseAll` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[VirtualInput.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/virtual/VirtualInput.java) |
| [VirtualMouse.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/virtual/VirtualMouse.java) | `mouseDown`、`mouseUp`、`mouseClick`、`mouseMove`、`releaseAll`、`restoreState`、`reapplyHeldState`、`isLeftDown` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[VirtualInput.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/virtual/VirtualInput.java)、[WindowsMouseMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsMouseMixin.java) |

### 10.8 `client/macro`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/macro/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [GuiPauseState.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/gui/GuiPauseState.java) | `open`、`close`、`start`、`pause`、`fail`、`finish`、`abort`、`isActive`、`isOpen`、`hasFailed`、`wasRunningAtOpen`、`getIntent`、`shouldPauseOnToggle`、`canChangeRoute` | [FarmController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java) |
| [MacroManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/MacroManager.java) | `register`、`getFarmController`、`start`、`pause`、`reset`、`startByHotkey`、`pauseByHotkey`、`resetByHotkey`、`tick`、`restoreInputState`、`handleInitializeInput`、`automateTaskByMonitorMessage` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[CommandListener.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandListener.java)、[MacroKeyBindings.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java)、[FarmController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java) |

### 10.9 `client/macro/esp`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/esp/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [ESPManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPManager.java) | `Frame`、`registerFixed`、`invalidateFrames`、`register`、`initialize` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[ESPRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPRenderer.java)、[ZombieShootoutESP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/ZombieShootoutESP.java) |
| [ESPRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPRenderer.java) | `draw`、`close` | [ESPManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPManager.java) |
| [ESPTarget.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPTarget.java) | 以类型/字段或注入为入口 | [ESPManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPManager.java)、[PestESP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/PestESP.java)、[ZombieShootoutESP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/ZombieShootoutESP.java) |

### 10.10 `client/esp/render`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/esp/render/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [ESPBoxRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPBoxRenderer.java) | `draw`、`line` | [ESPRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPRenderer.java) |
| [ESPFillRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPFillRenderer.java) | `draw` | [ESPRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPRenderer.java) |
| [ESPTracerRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPTracerRenderer.java) | `draw` | [ESPRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPRenderer.java) |

### 10.11 `client/esp/style`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/esp/style/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [ESPColorMode.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPColorMode.java) | 以类型/字段或注入为入口 | [ESPConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/display/ESPConfig.java)、[ESPColorProvider.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPColorProvider.java)、[ESPStyle.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPStyle.java)、[ZombieShootoutESP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/ZombieShootoutESP.java)、[UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |
| [ESPColorProvider.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPColorProvider.java) | `color` | [ESPBoxRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPBoxRenderer.java)、[ESPFillRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPFillRenderer.java)、[ESPTracerRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPTracerRenderer.java) |
| [ESPStyle.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPStyle.java) | 以类型/字段或注入为入口 | [ESPConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/display/ESPConfig.java)、[ESPBoxRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPBoxRenderer.java)、[ESPFillRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPFillRenderer.java)、[ESPTracerRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPTracerRenderer.java)、[ESPColorProvider.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPColorProvider.java)（另 2 个文件引用） |
| [ESPTargetStyle.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPTargetStyle.java) | `hasVisibleComponent` | [ESPConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/display/ESPConfig.java)、[ESPManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPManager.java)、[ZombieShootoutESP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/ZombieShootoutESP.java) |

### 10.12 `client/macro/farm`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [FarmController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java) | `getState`、`getConfig`、`getCommand`、`startMacroByHotkey`、`setExternalInputOwner`、`startMacro`、`pauseMacroByHotkey`、`pauseMacro`、`resetMacroByHotkey`、`resetMacro`、`runCurrentMovement`、`stopMovement`、`restoreInputState`、`tick`、`onPlayerScreenOpened`、`reapplyMenuInput`、`beginUltimateGardenMenu`、`endUltimateGardenMenu`、`endMenuPause`、`isMenuSessionActive`、`shouldPauseByHotkey`、`isMenuOpen`、`getRunStatus`、`getHudStopReason`、`canEditRouteConfiguration`、`requestMenuStart`、`queueMenuAction`、`getQueuedMenuActions`、`applySavedSelection`、`abortForWorldChange`、`nextLine`、`previousLine`、`changeCropType`、`handleInitializeInput`、`spawnByHotkey`、`spawn`、`loadout`、`warpGarden`、`automateTaskByMonitorMessage`<br>内部：`tickMenuSession` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[CommandHelper.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandHelper.java)、[CommandTree.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandTree.java)、[MacroKeyBindings.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java)、[MacroManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/MacroManager.java)（另 3 个文件引用） |
| [FarmState.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmState.java) | `getLineChangePhase`、`setLineChangePhase`、`getTotalTicks`、`isRunning`、`setRunning`、`isEventChange`、`setEventChange`、`isEventSelect`、`setEventSelect`、`isCommandRunning`、`setCommandRunning`、`getCropType`、`setCropType`、`getModType`、`setModType`、`isPestsMode`、`isFarmMode`、`getTick`、`setTick`、`incrementTick`、`resetTick`、`getEventCount`、`setEventCount`、`incrementEventCount`、`decrementEventCount`、`resetRoute`、`reset` | [FarmController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java)、[FarmCommandController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/action/FarmCommandController.java)、[RouteController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteController.java)、[MacroStatusHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/MacroStatusHud.java) |
| [MacroConstants.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/MacroConstants.java) | 以类型/字段或注入为入口 | 无外部类型引用；核对资源或 owner 装配 |

### 10.13 `client/macro/farm/action`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/action/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [FarmCommandController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/action/FarmCommandController.java) | `getWorkflow`、`markSpawnScheduled`、`getRandomDelays`、`setRandomDelays`、`isExpectingLoadoutScreen`、`isBusinessResumePending`、`isPestLoadoutPending`、`discardDeferredActions`、`getPestLoadoutRemainingTicks`、`hasPendingWork`、`hasActionFailed`、`clearActionFailure`、`abortWorld`、`getScheduler`、`isRunning`、`tick`、`cancel`、`startPestLoadoutTimer`、`cancelPestLoadoutTimer`、`isPestLoadoutTimerRunning`、`setPestLoadoutTimerFinished`、`warpGarden`、`spawnPoint`、`spawnMushroomMovement`、`lineChangeMovement`、`loadout`、`pestLoadout`、`spawnPests` | [FarmController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java)、[RouteController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteController.java)、[ActionScheduler.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/scheduler/ActionScheduler.java)、[MacroStatusHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/MacroStatusHud.java) |
| [FarmCommandInput.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/action/FarmCommandInput.java) | `isCommandRunning`、`tick`、`SendChar`、`SendText`、`Tap`、`Return`、`WarpGarden`、`SpawnPoint`、`Loadout`、`cancel` | 无外部类型引用；核对资源或 owner 装配 |

### 10.14 `client/macro/farm/crop`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/crop/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [CropDatabase.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/crop/CropDatabase.java) | `get`、`exists` | [RouteDatabase.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteDatabase.java) |
| [CropType.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/crop/CropType.java) | `getValue`、`isCustom`、`fromValue` | [CropConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/farm/CropConfig.java)、[CropDefinition.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/farm/CropDefinition.java)、[FarmConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/farm/FarmConfig.java)、[FarmController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java)、[FarmHints.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/hint/FarmHints.java)（另 6 个文件引用） |

### 10.15 `client/macro/farm/hint`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/hint/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [FarmHints.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/hint/FarmHints.java) | `printHint` | [FarmController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java)、[FarmCommandController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/action/FarmCommandController.java)、[PestLoadoutTimer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/pest/PestLoadoutTimer.java)、[RouteController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteController.java) |

### 10.16 `client/macro/farm/pest`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/pest/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [PestESP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/PestESP.java) | `collect` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java) |
| [PestLoadoutTimer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/pest/PestLoadoutTimer.java) | `start`、`tick`、`getRemainingTicks`、`isRunning`、`isFinished`、`clearFinished`、`cancel` | [FarmCommandController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/action/FarmCommandController.java) |

### 10.17 `client/macro/farm/route`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [RouteController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteController.java) | `setRouteAllowed`、`stopKeyboardMouse`、`runCurrentMovement`、`stopMovement`、`tick`、`nextLine`、`previousLine`<br>内部：`stopKeyboard`、`onLineFinished`、`continueLine`、`startNextLine`、`finishFarmRoute` | [FarmController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java) |
| [RouteData.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteData.java) | `getRoute`、`useAlternateSlot`、`getCropType` | [RouteDatabase.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteDatabase.java) |
| [RouteDatabase.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteDatabase.java) | `get` | 无外部类型引用；核对资源或 owner 装配 |
| [RouteKey.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteKey.java) | 以类型/字段或注入为入口 | [RouteData.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteData.java)、[RouteDatabase.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteDatabase.java) |
| [SetLineYP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/SetLineYP.java) | `setNormal`、`setAlternate` | [RouteController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteController.java)、[ActionScheduler.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/scheduler/ActionScheduler.java) |
| [SlotSelector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/SlotSelector.java) | `getSlot` | 无外部类型引用；核对资源或 owner 装配 |

### 10.18 `client/macro/zombie_shoot`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [ShootoutAimClock.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutAimClock.java) | `reset`、`step` | [ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) |
| [ShootoutAimCompletion.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutAimCompletion.java) | `error`、`aligned` | [ExpectedViewTracker.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ExpectedViewTracker.java)、[ShootoutMouseInput.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutMouseInput.java)、[ShootoutTanhAim.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutTanhAim.java)、[ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) |
| [ShootoutAimCorrection.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutAimCorrection.java) | `active`、`threshold`、`clear`、`ready` | [ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) |
| [ShootoutAimDiagnostics.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ShootoutAimDiagnostics.java) | `batchGeneration`、`active`、`sync`、`tick`、`end`、`shutdown`、`solution`、`solutionId`、`inputContext`、`queued`、`applied`、`gate`、`nextAttempt`、`linkAttempt`、`dispatch`、`confirmation` | [ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) |
| [ShootoutArrowConfirmation.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/shot/ShootoutArrowConfirmation.java) | `Observation`、`Attempt`、`Confirmation`、`clearSession`、`reset`、`pending`、`detection`、`confirmation`、`rememberDispatched`、`cancel`、`attempted`、`timedOut`、`supported`、`attemptedManual`、`observe` | [ShootoutAimDiagnostics.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ShootoutAimDiagnostics.java)、[ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) |
| [ShootoutAutoStart.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ShootoutAutoStart.java) | `Context`、`matches`、`cancel`、`pending`、`receive`、`due` | [ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) |
| [ShootoutBabyStall.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/model/ShootoutBabyStall.java) | `stalled`、`ready`、`clear`、`observe` | [ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) |
| [ShootoutCandidateSelection.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/targeting/ShootoutCandidateSelection.java) | `clear`、`select`、`firstOrRescan`、`first` | [ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) |
| [ShootoutLampCollision.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/shot/ShootoutLampCollision.java) | `hits` | [ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) |
| [ShootoutModel.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/model/ShootoutModel.java) | `Projection`、`Solution`、`at`、`tangent`、`project`、`load`、`match`、`finite`、`wrap`、`reflect`、`speed`、`sum`、`fall`、`velocity`、`arrow`、`solve` | [ShootoutAimCompletion.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutAimCompletion.java)、[ShootoutAimDiagnostics.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ShootoutAimDiagnostics.java)、[ShootoutLampCollision.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/shot/ShootoutLampCollision.java)、[ShootoutTanhAim.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutTanhAim.java)、[ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) |
| [ShootoutMouseInput.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutMouseInput.java) | `setDiagnosticsObserver`、`aimMode`、`setViewObserver`、`dispatching`、`clearPendingMotion`、`resetAim`、`aim`、`click`、`consumed`、`cancel` | [ViewDeviationCheckManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ViewDeviationCheckManager.java)、[ShootoutAimDiagnostics.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ShootoutAimDiagnostics.java)、[ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java)、[WindowsMouseMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsMouseMixin.java) |
| [ShootoutPolicy.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/targeting/ShootoutPolicy.java) | `Metadata`、`priority`、`retryAllowed`、`metadata` | [ShootoutModel.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/model/ShootoutModel.java)、[ShootoutTargetRound.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/targeting/ShootoutTargetRound.java)、[ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) |
| [ShootoutRtt.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/network/ShootoutRtt.java) | `status`、`milliseconds`、`pending`、`owns`、`stop`、`start`、`tick`、`pong` | [ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) |
| [ShootoutShotClock.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/shot/ShootoutShotClock.java) | `reset`、`ready`、`remainingTicks`、`fired` | [ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) |
| [ShootoutSpeed.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/model/ShootoutSpeed.java) | `Estimate`、`measuredSpeed`、`observe` | [ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) |
| [ShootoutTanhAim.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutTanhAim.java) | `Rotation`、`step`、`rotation`、`rawDelta` | [ShootoutAimDiagnostics.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ShootoutAimDiagnostics.java)、[ShootoutMouseInput.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutMouseInput.java) |
| [ShootoutTargetLoss.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/targeting/ShootoutTargetLoss.java) | `clear`、`expired` | [ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) |
| [ShootoutTargetRound.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/targeting/ShootoutTargetRound.java) | `Attempt`、`handled`、`retry`、`regularEligible`、`relockEligible`、`dispatched`、`verify`、`beginRelock` | [ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) |
| [ZombieShootArrowAngles.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootArrowAngles.java) | `wrap`、`direction`、`sample` | [ZombieShootCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootCollector.java) |
| [ZombieShootBlockCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootBlockCollector.java) | `Cell`、`offset`、`boundedView`、`getBlockState`、`getFluidState`、`getBlockEntity`、`getHeight`、`getMinY`、`request`、`tick`、`snapshot` | [ZombieShootCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootCollector.java)、[ZombieShootOcclusionAnalyzer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootOcclusionAnalyzer.java) |
| [ZombieShootClassifier.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootClassifier.java) | `classify`、`items` | [ZombieShootCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootCollector.java) |
| [ZombieShootCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootCollector.java) | `setGateChanged`、`logsEnabled`、`setLogsEnabled`、`optionsChanged`、`update`、`enabled`、`status`、`modelStatus`、`count`、`resetCounters`、`stop`、`shutdown` | [ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java)、[UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |
| [ZombieShootCoordinateSystem.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootCoordinateSystem.java) | `insideRelative`、`contains`、`relative`、`displacement` | [ZombieShootBlockCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootBlockCollector.java)、[ZombieShootCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootCollector.java)、[ZombieShootPathAnalyzer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootPathAnalyzer.java)、[ZombieShootSession.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ZombieShootSession.java) |
| [ZombieShootEntityTracker.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/entity/ZombieShootEntityTracker.java) | `Previous`、`Sample`、`Identity`、`identify`、`markMissing`、`identities`、`observedAt`、`observe`、`retain` | [ZombieShootCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootCollector.java) |
| [ZombieShootLogGate.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootLogGate.java) | `enabled`、`setEnabled`、`categories`、`collecting` | [ZombieShootCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootCollector.java) |
| [ZombieShootLogger.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ZombieShootLogger.java) | `offer`、`endBatch`、`count`、`close`、`awaitClose`、`finished` | [ShootoutAimDiagnostics.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ShootoutAimDiagnostics.java)、[ZombieShootSession.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ZombieShootSession.java) |
| [ZombieShootOcclusionAnalyzer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootOcclusionAnalyzer.java) | `intersects`、`analyze` | [ZombieShootCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootCollector.java) |
| [ZombieShootPathAnalyzer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootPathAnalyzer.java) | `Result`、`turnInterior`、`analyze` | [ZombieShootCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootCollector.java) |
| [ZombieShootPathCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootPathCollector.java) | `Point`、`add`、`snapshot` | [ZombieShootCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootCollector.java)、[ZombieShootPathAnalyzer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootPathAnalyzer.java) |
| [ZombieShootSession.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ZombieShootSession.java) | `select`、`accepts`、`registerTag`、`emit`、`stop` | [ZombieShootBlockCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootBlockCollector.java)、[ZombieShootCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootCollector.java) |
| [ZombieShootSnapshot.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ZombieShootSnapshot.java) | `map` | [ShootoutAimDiagnostics.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ShootoutAimDiagnostics.java)、[ZombieShootArrowAngles.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootArrowAngles.java)、[ZombieShootBlockCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootBlockCollector.java)、[ZombieShootCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootCollector.java)、[ZombieShootLogger.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ZombieShootLogger.java)（另 4 个文件引用） |
| [ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java) | `InterceptDebug`、`HudSnapshot`、`hudSnapshot`、`interceptDebug`、`pingStatus`、`onRttPong`、`aimFrame`、`onManualShotBlockedByInterceptor`、`blockManualShot`、`onManualButton`、`manualInputProcessed`、`cancelAutoStart`、`onStartMessage`、`enabled`、`state`、`status`、`pingText`、`targetText`、`intercepting`、`interceptingAim`、`interceptingShoot`、`toggle`、`start`、`stop`、`register`、`classify`、`getBlockState`、`getFluidState`、`getBlockEntity`、`getHeight`、`getMinY`<br>内部：`aimReady`、`resetAimCycle`、`clearFrameAim`、`requestFrameAim`、`completeShot`、`automationChanged`、`holdInvalidTarget`、`observe`、`select`、`selectObserved`、`prediction`、`verify`、`tick`、`collisionCheck` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[CommandListener.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandListener.java)、[MacroKeyBindings.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java)、[ZombieShootoutESP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/ZombieShootoutESP.java)、[ShootoutAimFrameMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/ShootoutAimFrameMixin.java)（另 5 个文件引用） |
| [ZombieShootoutESP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/ZombieShootoutESP.java) | `color`、`bounds`、`register` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java) |

### 10.19 `client/mixin`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [ExampleClientMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/ExampleClientMixin.java) | 以类型/字段或注入为入口 | 无外部类型引用；核对资源或 owner 装配 |
| [InventoryInputMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/InventoryInputMixin.java) | 以类型/字段或注入为入口 | 无外部类型引用；核对资源或 owner 装配 |
| [KeyMappingAccessor.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/KeyMappingAccessor.java) | 以类型/字段或注入为入口 | [MacroKeyBindings.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java)、[VirtualKeyboard.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/virtual/VirtualKeyboard.java)、[ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java)、[InventoryInputMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/InventoryInputMixin.java)、[UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |
| [KeyboardHandlerAccessor.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/KeyboardHandlerAccessor.java) | 以类型/字段或注入为入口 | [VirtualKeyboard.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/virtual/VirtualKeyboard.java) |
| [MenuFarmInputMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/MenuFarmInputMixin.java) | 以类型/字段或注入为入口 | 无外部类型引用；核对资源或 owner 装配 |
| [ShootoutAimFrameMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/ShootoutAimFrameMixin.java) | 以类型/字段或注入为入口 | 无外部类型引用；核对资源或 owner 装配 |
| [ShootoutMouseAccessor.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/ShootoutMouseAccessor.java) | 以类型/字段或注入为入口 | [ShootoutMouseInput.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutMouseInput.java) |
| [ShootoutPongMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/ShootoutPongMixin.java) | 以类型/字段或注入为入口 | 无外部类型引用；核对资源或 owner 装配 |
| [WindowsFocusMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsFocusMixin.java) | 以类型/字段或注入为入口 | 无外部类型引用；核对资源或 owner 装配 |
| [WindowsInputMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsInputMixin.java) | 以类型/字段或注入为入口 | 无外部类型引用；核对资源或 owner 装配 |
| [WindowsMissTimeMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsMissTimeMixin.java) | 以类型/字段或注入为入口 | 无外部类型引用；核对资源或 owner 装配 |
| [WindowsMouseMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsMouseMixin.java) | 以类型/字段或注入为入口 | 无外部类型引用；核对资源或 owner 装配 |
| [WindowsPauseMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsPauseMixin.java) | 以类型/字段或注入为入口 | 无外部类型引用；核对资源或 owner 装配 |

### 10.20 `client/scheduler`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/scheduler/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [ActionScheduler.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/scheduler/ActionScheduler.java) | `setPaused`、`isPaused`、`getRemainingTicks`、`isRunning`、`tick`、`schedule`、`scheduleDelayTicks`、`scheduleAfterTicks`、`clear` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[VirtualMouse.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/virtual/VirtualMouse.java)、[FarmCommandController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/action/FarmCommandController.java)、[SetLineYP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/SetLineYP.java) |
| [InputScheduler.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/scheduler/InputScheduler.java) | `delayMillis`、`delayTicks`、`tick`、`isWaiting`、`getRemainingTicks`、`clear` | [FarmCommandInput.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/action/FarmCommandInput.java) |

### 10.21 `client/ui`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/ui/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [MenuController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/MenuController.java) | `open`、`saveFarmSelection` | [CommandTree.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandTree.java)、[MacroKeyBindings.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java)、[UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |

### 10.22 `client/ui/hud`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/ui/hud/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [HudAdjustmentController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/HudAdjustmentController.java) | `Layout`、`selected`、`selectedLayout`、`isActive`、`begin`、`freeCursor`、`tick`、`layout`、`move`、`button`、`finish` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[MacroKeyBindings.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java)、[ZombieShootoutController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/ZombieShootoutController.java)、[WindowsInputMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsInputMixin.java)、[WindowsMouseMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsMouseMixin.java)（另 6 个文件引用） |
| [HudPanel.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/HudPanel.java) | 以类型/字段或注入为入口 | [MacroStatusHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/MacroStatusHud.java)、[ZombieShootoutHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/ZombieShootoutHud.java) |
| [MacroStatusHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/MacroStatusHud.java) | `render` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java) |
| [ZombieShootoutHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/ZombieShootoutHud.java) | `render` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java) |

### 10.23 `client/ui/language`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/ui/language/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [LanguageKeys.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/language/LanguageKeys.java) | `component`、`operation`、`hotkey` | [UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |
| [LanguageManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/language/LanguageManager.java) | `current`、`select`、`text`、`optional`、`keyName`、`translate`、`localize`、`errorKey`、`cropName` | [HudPanel.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/HudPanel.java)、[MacroStatusHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/MacroStatusHud.java)、[ZombieShootoutHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/ZombieShootoutHud.java)、[LanguageKeys.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/language/LanguageKeys.java)、[UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java)（另 2 个文件引用） |
| [LanguageType.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/language/LanguageType.java) | `code`、`englishName`、`optionLabel`、`flagResource` | [ConfigManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java)、[UIConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/display/UIConfig.java)、[LanguageManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/language/LanguageManager.java)、[LanguageSelector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/LanguageSelector.java) |

### 10.24 `client/ui/render`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/ui/render/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [MenuInputGraphics.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/render/MenuInputGraphics.java) | `text`、`fill`、`textHighlight`、`requestCursor`、`setPreeditOverlay` | [UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |
| [RoundedRect.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/render/RoundedRect.java) | `fill`、`outline` | [HudPanel.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/HudPanel.java)、[MacroStatusHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/MacroStatusHud.java)、[ZombieShootoutHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/ZombieShootoutHud.java)、[UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java)、[CollapsibleSection.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/CollapsibleSection.java)（另 6 个文件引用） |

### 10.25 `client/ui/screen`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/ui/screen/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) | `finishHudAdjustment`、`keepsFarmingInput`、`isPauseScreen`、`added`、`removed`、`onClose`、`tick`、`keyPressed`、`keyReleased`、`init`、`mouseScrolled`、`mouseClicked`、`extractBackground`、`extractRenderState`<br>内部：`Placement`、`Card`、`Label`、`Divider`、`Choice`、`palette`、`closeMenu`、`build`、`act`、`saved`、`heading`、`sectionInset`、`sectionX`、`sectionWidth`、`info`、`note`、`place`、`button`、`toggle`、`dropdown`、`input`、`divider`、`dismissChoices`、`choiceLabel`、`cropChoices`、`home`、`totalLines`、`progress`、`farm`、`cropLineChangeFields`、`cropDefinitionSaveButton`、`applyCropDefinition`、`fieldLabel`、`timerText`、`pests`、`hotkeys`、`shootoutSettings`、`shootoutInput`、`settings`、`developerSettings`、`hintSettings`、`hintToggle`、`hudSettings`、`nestedDelaySection`、`viewDeviationSettings`、`randomDelaySettings`、`canEditFarmConfiguration`、`accountName`、`fileNames`、`accountConfiguration`、`hasUnsavedDrafts`、`executeAccountCopy`、`espSettings`、`espTarget`、`espComponent`、`espInput`、`helpText`、`runStatus`、`cropName`、`statusText`、`sectionVisible`、`displayedY`、`positionContent` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[MacroKeyBindings.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java)、[VirtualKeyboard.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/virtual/VirtualKeyboard.java)、[FarmController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java)、[MenuFarmInputMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/MenuFarmInputMixin.java)（另 4 个文件引用） |

### 10.26 `client/ui/theme`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/ui/theme/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [ThemePalette.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/theme/ThemePalette.java) | `bg`、`fg`、`multiplyAlpha`、`from`、`blend` | [MacroStatusHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/MacroStatusHud.java)、[ZombieShootoutHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/ZombieShootoutHud.java)、[MenuInputGraphics.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/render/MenuInputGraphics.java)、[UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java)、[CollapsibleSection.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/CollapsibleSection.java)（另 7 个文件引用） |

### 10.27 `client/ui/widget`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/ui/widget/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [CollapsibleSection.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/CollapsibleSection.java) | `State`、`depth`、`inset`、`isExpanded`、`isContentVisible`、`toggle`、`measure`、`hiddenHeight`、`hiddenHeightBefore`、`mouseClicked`、`state`、`finishContent`、`extractContents` | [UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |
| [DropdownSelector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/DropdownSelector.java) | 以类型/字段或注入为入口 | [UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |
| [HelpTooltip.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/HelpTooltip.java) | `alpha`、`displayed`、`clear`、`update`、`beginFrame`、`clip`、`isIconAt`、`iconAfterText`、`renderOverlay` | [UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java)、[CollapsibleSection.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/CollapsibleSection.java)、[StyledButton.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/StyledButton.java) |
| [LanguageSelector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/LanguageSelector.java) | `isOpen`、`dismiss`、`onPress`、`extractContents`、`handleOverlayClick`、`renderOverlay` | [UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |
| [SelectionCheckbox.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/SelectionCheckbox.java) | `extractContents` | [UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |
| [StyledButton.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/StyledButton.java) | `onPress`、`withHelp`、`extractContents`、`updateWidgetNarration` | [UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java)、[CollapsibleSection.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/CollapsibleSection.java)、[DropdownSelector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/DropdownSelector.java)、[LanguageSelector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/LanguageSelector.java)、[SelectionCheckbox.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/SelectionCheckbox.java)（另 1 个文件引用） |
| [ToggleSwitch.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/ToggleSwitch.java) | `extractContents` | [UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |
| [TransparencySlider.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/TransparencySlider.java) | `percentage`、`setValue`、`applyValue`、`updateMessage`、`keyPressed`、`extractWidgetRenderState` | [UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |

### 10.28 `client/window`

目录：`src/client/java/com/kuangsanfeng/ultimategarden/client/window/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [WindowsFocusController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/window/WindowsFocusController.java) | `releaseFocusWithHint`、`releaseFocus`、`restoreFocusWithHint`、`restoreFocus`、`toggle`、`useGameFocusForPlayerGui`、`restoreGameMouseAfterMenu`、`reapplyCursorMode`、`isReleased` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[FarmController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java)、[FarmCommandController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/action/FarmCommandController.java)、[RouteController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/route/RouteController.java) |
| [WindowsInputInterceptor.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/window/WindowsInputInterceptor.java) | `setExternalOwner`、`setExternalMouseOwners`、`blockMouseButton`、`setUiInputAllowed`、`enable`、`disable`、`shouldBlockInput`、`blockKeyboard`、`blockMouseScroll`、`blockMouseMove` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[FarmController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java)、[WindowsInputMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsInputMixin.java)、[WindowsMouseMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsMouseMixin.java) |
| [WindowsNativeBridge.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/window/WindowsNativeBridge.java) | `getWindowHandle`、`setForegroundWindow`、`releaseCursor` | 无外部类型引用；核对资源或 owner 装配 |
| [WindowsState.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/window/WindowsState.java) | `isMouseReleased`、`setMouseReleased`、`isInputIntercepting`、`setInputIntercepting` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[WindowsFocusMixin.java](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WindowsFocusMixin.java)、[MacroStatusHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/MacroStatusHud.java)、[WindowsFocusController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/window/WindowsFocusController.java)、[WindowsInputInterceptor.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/window/WindowsInputInterceptor.java) |

### 10.29 `main`

目录：`src/main/java/com/kuangsanfeng/ultimategarden/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [Ultimate_Garden.java](src/main/java/com/kuangsanfeng/ultimategarden/Ultimate_Garden.java) | `onInitialize`、`id` | [Ultimate_GardenClient.java](src/client/java/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[ConfigIO.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/storage/ConfigIO.java)、[ConfigManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java)、[CropConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/farm/CropConfig.java)、[ESPConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/display/ESPConfig.java)（另 7 个文件引用） |

### 10.30 `main/mixin`

目录：`src/main/java/com/kuangsanfeng/ultimategarden/mixin/`。

| 文件 | 入口 / 内部定位 | 类型引用方（静态导航） |
|---|---|---|
| [ExampleMixin.java](src/main/java/com/kuangsanfeng/ultimategarden/mixin/ExampleMixin.java) | 以类型/字段或注入为入口 | 无外部类型引用；核对资源或 owner 装配 |

### 10.31 遗留与使用范围提醒

- `InputScheduler` 是单一倒计时，delayTicks 取 max 延长等待，不是 ActionScheduler 的 FIFO。它只被旧 `FarmCommandInput` 引用；当前生产代码没有构造 FarmCommandInput。
- `MacroConstants`、`CropDatabase`、`RouteDatabase`、`CropData`、`RouteData` 保留旧数据/接口。现有执行路线的定义来自 CropConfig/CropDefinition，新增配置应沿当前入口接入。
- `VirtualMouse.mouseMove` 不是 Shootout 的帧级视角实现；实际移动入口是 ShootoutMouseInput，避免只凭注释或旧命名判断可复用性。
- `WindowsNativeBridge` 是现有窗口桥接，并不代表项目安装了第二套 OS 键鼠钩子服务；实际普通输入门控见 Mixins 与 WindowsInputInterceptor。
- 模板 ExampleMixin/ExampleClientMixin 保留注册，但没有实现新的 Macro 功能。Farm View Check 同样只保存配置，不建采样会话。

<a id="assets-tests"></a>
## 11. 资源与测试索引

### 11.1 资源和模型

| 文件 | 用途 |
|---|---|
| [esp_tracer.vsh](src/client/resources/assets/ultimate_garden/shaders/core/esp_tracer.vsh) | Tracer 顶点着色器，配合 ESPTracerRenderer 的现有管线 |
| [cn.png](src/client/resources/assets/ultimate_garden/textures/gui/language/cn.png) | 语言选择器中国旗帜资源 |
| [us.png](src/client/resources/assets/ultimate_garden/textures/gui/language/us.png) | 语言选择器美国旗帜资源 |
| [ultimate_garden.client.mixins.json](src/client/resources/ultimate_garden.client.mixins.json) | 客户端13项 Mixin/Accessor 注册 |
| [default-crops.json](src/client/resources/ultimategarden/default-crops.json) | CropConfig 默认作物定义（并非每次路线 Tick 重新加载） |
| [zombie_shoot_theoretical_models.json](src/client/resources/ultimategarden/zombie_shoot_theoretical_models.json) | ShootoutModel 的只读路径、速度及箭矢参数；场景专用，非日志输出 |
| [icon.png](src/main/resources/assets/ultimate_garden/icon.png) | Mod 图标 |
| [en_us.json](src/main/resources/assets/ultimate_garden/lang/en_us.json) | 英文翻译；动态 Setting/日志枚举/KeyMapping 键也在此维护 |
| [zh_cn.json](src/main/resources/assets/ultimate_garden/lang/zh_cn.json) | 中文翻译；界面由 LanguageManager 选择并保留英文回退 |
| [fabric.mod.json](src/main/resources/fabric.mod.json) | Fabric 模块元数据、入口及依赖声明 |
| [ultimate_garden.mixins.json](src/main/resources/ultimate_garden.mixins.json) | 公共模板 Mixin 注册 |

### 11.2 被动日志类别与输出

配置类别权威为 [ZombieShootLogCategory](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/shootout/ZombieShootLogCategory.java)。以下为实际18项；每项默认OFF，master gate 不持久化。

| 开关类别 | 输出目录 / 文件与职责 |
|---|---|
| LEATHER_ADULT / LEATHER_BABY | zombies/leather_adult、zombies/leather_baby |
| IRON_ADULT / IRON_BABY | zombies/iron_adult、zombies/iron_baby |
| GOLD_ADULT / GOLD_BABY | zombies/gold_adult、zombies/gold_baby |
| DIAMOND_ADULT / DIAMOND_BABY | zombies/diamond_adult、zombies/diamond_baby |
| ARROWS | arrows；位置、速度、实体朝向、玩家方向比较分别保存 |
| PATH_POINTS / PATH_MODELS / TURN_EVENTS | paths/raw、paths/models、paths/turns；原始轨迹不被拟合覆盖 |
| BLOCKS / BLOCK_SCANS / OCCLUSION | environment/blocks、environment/scans、environment/occlusion；UNKNOWN 不等于 Air |
| TAGS / DIAGNOSTICS | tags、diagnostics；Session+Tag 关联及异常信息 |
| AIM_SHOT_DIAGNOSTICS | 根目录固定 aim-shot-diagnostics.jsonl，追加式 START/END 批次 |

普通类别文件名与 Session 元数据由 ZombieShootSession.emit/stop 生成，后台 Logger 输出 summary；不要把目录名当作实体身份。Tag 是本地8位大写十六进制递增值，实体 UUID+对象用于关联；Session 使用独立身份空间，跨 Session 必须联合 sessionId+tag。未启用的类别在 emit 接受边界过滤；内部为依赖计算读取字段并不等于写入全部类别。

采集坐标：原点记录脚部、眼部、originYaw/Pitch；世界轴平移为 relativeX/Y/Z，不随当前视角旋转；固定30格三维球。范围离开/缺口以轨迹段区分，不当成掉头端点。方块中心用原生 floor 的 BlockPos，7×7×7 局部范围再裁剪到固定球，未加载不当 Air。模型、遮挡和原始轨迹仅反映客户端可观测数据。

### 11.3 测试入口与覆盖边界

测试通过各目录 `run.ps1` 驱动，不是 JUnit 自动发现。部分脚本直接 javac 编译选定生产类+stubs；Shootout 脚本通过临时 Gradle init task 解析 client runtimeClasspath，再编译/执行 Java main，并运行 WiringAudit.py。必须先看脚本到底编译了哪些类，不能把 stub 的成功当作原生 Minecraft 输入/网络/渲染实测。

| 测试目录/入口 | 实际覆盖主题 |
|---|---|
| [config](tests/config/run.ps1) | 模块读写、账号隔离/复制、默认值/修复、随机延迟、换行与 HUD、Pest 配置 |
| [route](tests/route/run.ps1) | 行 Tick、换行阶段、随机延迟运行语义 |
| [menu-input](tests/menu-input/run.ps1) | 虚拟按住、菜单 suspend/resume 与取消 |
| [gui](tests/gui/run.ps1) | GUI 生命周期/来源、嵌套折叠、Hint、透明度、语言、Pest菜单及静态事件次序 |
| [focus](tests/focus/run.ps1) | released/cursor/restore 等 Focus 状态 |
| [hints](tests/hints/run.ps1) | Hint 分类、键名和本地化输出 |
| [hud-adjust](tests/hud-adjust/run.ps1) | 独立 HUD bounds、选择、拖动/缩放与清理 |
| [esp](tests/esp/run.ps1) | ESP样式/几何/Pest过滤及 tracer shader 静态审查 |
| [shootout](tests/shootout/run.ps1) | 模型、优先级、目标轮转/重锁、速度/卡住、Tanh/Hz、双层容差、真实/虚拟输入、统一箭矢确认、开关/RTT/自动启动/View Check/日志 |

测试源完整索引（stubs 是隔离依赖，不是新的生产实现）：

- **config**：[AccountRegression.java](tests/config/AccountRegression.java)、[ConfigRegression.java](tests/config/ConfigRegression.java)、[HudConfigRegression.java](tests/config/HudConfigRegression.java)、[LineChangeConfigRegression.java](tests/config/LineChangeConfigRegression.java)、[MenuPauseConfigRegression.java](tests/config/MenuPauseConfigRegression.java)、[PestDetectionRegression.java](tests/config/PestDetectionRegression.java)、[RandomDelayRegression.java](tests/config/RandomDelayRegression.java)、[run.ps1](tests/config/run.ps1)。
- **esp**：[ESPRegression.java](tests/esp/ESPRegression.java)、[PestESPRegression.java](tests/esp/PestESPRegression.java)、[run.ps1](tests/esp/run.ps1)、[TracerShaderRegression.java](tests/esp/TracerShaderRegression.java)。
  - 隔离依赖：[LevelExtractionContext.java](tests/esp/pest-stubs/net/fabricmc/fabric/api/client/rendering/v1/level/LevelExtractionContext.java)、[EntityType.java](tests/esp/pest-stubs/net/minecraft/world/entity/EntityType.java)。
- **focus**：[FocusRegression.java](tests/focus/FocusRegression.java)、[run.ps1](tests/focus/run.ps1)。
  - 隔离依赖：[HintManager.java](tests/focus/stubs/com/kuangsanfeng/ultimategarden/client/platform/hint/HintManager.java)、[HudAdjustmentController.java](tests/focus/stubs/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/HudAdjustmentController.java)、[Minecraft.java](tests/focus/stubs/net/minecraft/client/Minecraft.java)、[GLFW.java](tests/focus/stubs/org/lwjgl/glfw/GLFW.java)。
- **gui**：[CollapsibleSectionRegression.java](tests/gui/CollapsibleSectionRegression.java)、[GuiLifecycleRegression.java](tests/gui/GuiLifecycleRegression.java)、[HelpTooltipRegression.java](tests/gui/HelpTooltipRegression.java)、[InventoryEventOrderRegression.py](tests/gui/InventoryEventOrderRegression.py)、[LanguageRegression.java](tests/gui/LanguageRegression.java)、[PestMenuRegression.java](tests/gui/PestMenuRegression.java)、[run.ps1](tests/gui/run.ps1)、[TransparencyRegression.java](tests/gui/TransparencyRegression.java)。
  - 隔离依赖：[FarmHints.java](tests/gui/stubs/com/kuangsanfeng/ultimategarden/client/macro/farm/hint/FarmHints.java)、[Minecraft.java](tests/gui/stubs/net/minecraft/client/Minecraft.java)。
- **hints**：[HintRegression.java](tests/hints/HintRegression.java)、[run.ps1](tests/hints/run.ps1)。
  - 隔离依赖：[MacroKeyBindings.java](tests/hints/stubs/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java)、[Ultimate_GardenClient.java](tests/hints/stubs/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[Minecraft.java](tests/hints/stubs/net/minecraft/client/Minecraft.java)、[Component.java](tests/hints/stubs/net/minecraft/network/chat/Component.java)。
- **hud-adjust**：[HudAdjustmentRegression.java](tests/hud-adjust/HudAdjustmentRegression.java)、[run.ps1](tests/hud-adjust/run.ps1)。
  - 隔离依赖：[UltimateGardenScreen.java](tests/hud-adjust/stubs/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java)、[Ultimate_GardenClient.java](tests/hud-adjust/stubs/com/kuangsanfeng/ultimategarden/client/Ultimate_GardenClient.java)、[Minecraft.java](tests/hud-adjust/stubs/net/minecraft/client/Minecraft.java)、[GLFW.java](tests/hud-adjust/stubs/org/lwjgl/glfw/GLFW.java)。
- **menu-input**：[MenuInputRegression.java](tests/menu-input/MenuInputRegression.java)、[run.ps1](tests/menu-input/run.ps1)。
  - 隔离依赖：[VirtualKeyboard.java](tests/menu-input/stubs/com/kuangsanfeng/ultimategarden/client/platform/input/virtual/VirtualKeyboard.java)、[Minecraft.java](tests/menu-input/stubs/net/minecraft/client/Minecraft.java)、[MouseHandler.java](tests/menu-input/stubs/net/minecraft/client/MouseHandler.java)。
- **route**：[RandomDelayRuntimeRegression.java](tests/route/RandomDelayRuntimeRegression.java)、[RowTimingRegression.java](tests/route/RowTimingRegression.java)、[run.ps1](tests/route/run.ps1)。
  - 隔离依赖：[VirtualInput.java](tests/route/stubs/com/kuangsanfeng/ultimategarden/client/platform/input/virtual/VirtualInput.java)、[FarmHints.java](tests/route/stubs/com/kuangsanfeng/ultimategarden/client/macro/farm/hint/FarmHints.java)、[WindowsFocusController.java](tests/route/stubs/com/kuangsanfeng/ultimategarden/client/platform/window/WindowsFocusController.java)。
- **shootout**：[AimCorrectionRegression.java](tests/shootout/AimCorrectionRegression.java)、[AimRateRegression.java](tests/shootout/AimRateRegression.java)、[AimShotDiagnosticsRegression.java](tests/shootout/AimShotDiagnosticsRegression.java)、[ArrowConfirmationToggleRegression.java](tests/shootout/ArrowConfirmationToggleRegression.java)、[AutoStartRegression.java](tests/shootout/AutoStartRegression.java)、[BabyStallRegression.java](tests/shootout/BabyStallRegression.java)、[DeveloperLogsRegression.java](tests/shootout/DeveloperLogsRegression.java)、[InterceptESPRegression.java](tests/shootout/InterceptESPRegression.java)、[ManualRestrictionRegression.java](tests/shootout/ManualRestrictionRegression.java)、[ManualShotRegression.java](tests/shootout/ManualShotRegression.java)、[RelockRegression.java](tests/shootout/RelockRegression.java)、[RttRegression.java](tests/shootout/RttRegression.java)、[run.ps1](tests/shootout/run.ps1)、[ShootoutControlsRegression.java](tests/shootout/ShootoutControlsRegression.java)、[ShootoutInputRegression.java](tests/shootout/ShootoutInputRegression.java)、[ShootoutRegression.java](tests/shootout/ShootoutRegression.java)、[ShootoutSettingsLayoutRegression.java](tests/shootout/ShootoutSettingsLayoutRegression.java)、[ShotConfirmationRegression.java](tests/shootout/ShotConfirmationRegression.java)、[TanhAimRegression.java](tests/shootout/TanhAimRegression.java)、[TargetRotationRegression.java](tests/shootout/TargetRotationRegression.java)、[ViewDeviationRegression.java](tests/shootout/ViewDeviationRegression.java)、[WiringAudit.py](tests/shootout/WiringAudit.py)。
  - 隔离依赖：[Minecraft.java](tests/shootout/stubs/net/minecraft/client/Minecraft.java)。

### 11.4 构建和补充资料

- 本地 Java25；`JAVA_HOME` 要指向实际 JDK。完整构建命令：`.\gradlew.bat build`；依赖已缓存时可用 `.\gradlew.bat build --offline`。本文不将缓存存在与否写成已验证。
- 业务回归命令：`powershell -File tests/<module>/run.ps1`，选择上表实际目录；独立脚本会写 build/ 测试产物，不写生产配置。
- [gradlew.bat](gradlew.bat)、[gradlew](gradlew)、[Wrapper 配置](gradle/wrapper/gradle-wrapper.properties)、[CI build](.github/workflows/build.yml)；CI Gradle build 不会自动覆盖所有独立回归脚本。
- [README](README.md)、[LICENSE](LICENSE)、[Zombie_Shootout.md](docs/Zombie_Shootout.md)。补充业务文档可能滞后，需按本文入口重新核对源码。

<a id="verification"></a>
## 12. 审计结果与边界

### 12.1 本次核对范围

- 对照当前 src/main 与 src/client 的全部生产 Java 文件，读取初始化、关键控制器、Mixin、配置、输入、日志与渲染实现，并建立全文件入口索引；资源、测试及 Gradle 以实际文件为准。
- 核对共享事件注册、原生输入消费时序、Farm 与 Shootout 的独立 owner、账号切换/世界失效的清理入口。复用表的提取项均为建议，本次没有实施提取。
- 核对文档所有项目相对文件链接及显式目录锚点；保存前后对 src、tests、docs 与根目录既有文件进行内容摘要比较，确认除本文外无改动。
- 本次为文档审计，**未重跑 Gradle Build、独立回归脚本或游戏客户端**，未把旧报告的通过结果当作本次结果。可运行命令见第11章。

### 12.2 必须保留的不确定性

- 静态代码可确认算法、入口和清理分支存在，不能证明服务器接受输入、灯是否实际命中、卡住恢复准确率或20/60/120Hz的实机行为；这些均 **Not Verified In-Game**。
- 未提供已登录可复现客户端，Aim & Shot Diagnostics 没有完成“20Hz能发射/60Hz卡住”的两批服务器实测；日志格式与关联入口已存在，不应先把故障归因于时序。
- View Check 仅是本地 expected/actual 轨迹监测，不能把偏差认定为服务器反作弊证据；Farm 检测占位无效。原生RTT响应能否在实际Hypixel会话按期收到也需实测。
- 局部遮挡是假设范围内的碰撞检查，未加载区域保留未知；采集路径端点是观测边界，不能证明真实端点。速度/箭矢模型是特定样本经验值。
- 当前源码有若干旧接口、模板 Mixin 和未接入的兼容队列；本文如实标记，未删除或顺手启用。

后续变更应先定位 owner 和更新阶段，再确认单位、取消条件及配置状态源。修改通用能力前，检查本表实际引用方与回归入口，避免把单业务政策迁成全局默认。


### 12.3 Structure Shift / 01-client-context

稳定起点为 master 48fa5b9b711003ad00148336c8d44f6bb35b43ba；原工作区干净，重构位于独立 worktree，采用 detached HEAD 验证后建立任务分支。

- [ClientContextEvents](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/context/ClientContextEvents.java) 统一转发断线、退出、账号加载前后与世界变化通知；[ContextSignal](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/context/ContextSignal.java) 按订阅顺序同步通知，单次发布使用订阅快照。
- ESP、Shootout、入口中的 Farm/HUD 收尾通过公共断线/退出桥接，仍按原先注册顺序执行；公共层不持有或停止业务状态。Fabric桥接各注册一次。
- 账号切换保留原有 HUD结束、Farm abort、兼容队列清空和ESP失效，然后配置激活，再次Farm abort 的顺序。accountBefore/accountAfter在配置激活两侧发出，不提前改写配置。
- 世界通知放在Farm既有世界观察阶段，不增加Tick驱动；HUD结束与ESP快照失效由各owner处理。Shootout、Farm及采集原有世界有效性防线保留，避免改变原有先后关系。
- 自动验证：Gradle build成功；ContextRegression通过同步顺序和发布中订阅的快照行为；现有独立回归按执行证据核对。真实账号、断线和世界切换仍待游戏验收。


### 12.4 Structure Shift / 02-rtt

公共 [NativeRttService](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/network/NativeRttService.java) 管理单请求、连接身份、token、超时、迟到响应、RTT毫秒及 sampledAtNanos。业务仍决定 start/tick/stop 和间隔，补偿仍为 RTT/50。

[ClientRttConnection](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/network/ClientRttConnection.java) 持有同一个连接服务，负责原生探测包构造、Pong匹配后的客户端线程派发、采样回调及断线清理。ShootoutPongMixin直接调用公共桥接；Shootout注册采样回调更新现有ping字段，HUD和预测继续共用该字段。旧ShootoutRtt保留为兼容类型，生产不再构造独立探测器。

验证：Gradle build成功；RttServiceRegression验证响应配对、采样时刻、超时、旧会话及连接更换；完整Shootout回归通过，包括原有RTT数值/调度测试和新的公共接线检查。原生Hypixel响应与HUD实机显示待验收。


### 12.5 Structure Shift / 03-world-query

公共 observation/ 新增 LoadedWorldView、SpatialQueries、ObservationCache 和 ScanBudget。采集与发射前碰撞共用 LoadedWorldView，业务传入原有范围和加载判据；异常仍区分未知，不把未加载变为空气。采集坐标及343格偏移和遮挡线段/AABB相交委托公共几何。扫描预算仍48格/阶段，缓存仍8192格/512中心及原有100Tick复查；缓存和队列仍随业务世界会话销毁，不增加世界读取次数。

状态归属：各采集器持有自己的预算与缓存；LoadedWorldView只持有本次查询上下文，不跨世界保存。SpatialQueries为纯函数。业务仍决定形状及相交结果的含义。

验证：SpatialRegression覆盖球边界、负坐标平移、343偏移、线段碰撞、缓存清空/容量及预算；Gradle build和完整Shootout回归通过。未加载邻居仍在原来读取前抛出未知异常，实机跨世界/可视碰撞待验收。


### 12.6 Structure Shift / 04-entity-tracking

公共 observation/EntityObservationTracker 接管会话实体身份、UUID与对象/ID替换、首次/最近观测、缺失/恢复及位置段落记录；classification由调用方提供。clear释放当前会话，跨会话身份不混用。采集器直接持有公共实例，旧ZombieShootEntityTracker为兼容适配。Shootout运行目标以公共identity变更判断是否新建Target，但保留自己的目标Tag、first排序、速度、轮次和200Tick保留条件；512目标容量在登记前检查。

验证：EntityRegression覆盖ID复用、对象替换、短时缺失恢复、分类变化及会话隔离；Gradle build及完整Shootout回归通过，包括采集身份、重锁定和旧结果隔离。两个分类器规则未合并。实机实体重载与切世界待验收。


### 12.7 Structure Shift / 05-sample-window

公共 observation/TimedWindow<T> 持有有界样本、调用方提供的时间提取器、闭区间过期、保留跨窗口边界段、缺样重置及不可变snapshot。不同实例不共享时间状态；时间单位由调用方约定。速度窗口用Client Tick、幼年卡住窗口用单调纳秒；各自仍在业务中计算平均速度和累计XZ位移，默认速度退役、真实零位移、缺口与突变规则不变。窗口在目标/世界会话清理时一并释放。

验证：WindowRegression覆盖容量、快照、过期边界、跨段保留和重置；Gradle build与完整Shootout回归通过，包括速度、正常掉头、卡住和恢复。未增加采样、求解或日志输出。


### 12.8 Structure Shift / 06-log

公共 client/log/JsonSnapshot、JsonlWriter 管理JSON安全快照、JSONL编码、4096条/500000结构单位队列、128条批处理、追加批次END终止槽、后台排空、关闭及错误/丢样计数。每个通道独立持有实例，公共层不引用Macro；completion回调由适配器提供。

log/zombie_shoot/ 包含ZombieShootLogger（原目录/摘要策略）、ZombieShootSession（类别、文件、Session元数据与Tag扩充）和ShootoutAimDiagnostics（原事件结构、序号/限频/START-END规则）。macro下旧同名类型为兼容外观，实际队列和工作线程均归日志模块。后台只接收不可变值，不读取世界。目录与行格式未改。

验证：Gradle build与完整Shootout回归通过；tests/context/run.ps1统一执行公共回归，LogIsolationRegression验证双通道独立关闭/排空、关闭后拒收、文件系统失败隔离。原采集17类及诊断追加批次回归通过。真实游戏日志对照仍待验收。


### 12.9 Structure Shift / 07-developer-collection

client/collection/CollectionGate<C>持有运行内存总开关和类别集合；CollectionSession<C>提供唯一会话ID、类别过滤、不可变快照提交和stop后的拒收边界，输出交给日志sink。业务运行条件由调用方传入，不由公共层查询Macro。

collection/zombie_shoot/ZombieShootCollector承接原实体/箭矢/路径/方块采样、分析调度及何时开启规则，并组合observation组件。原macro入口仅为单实例兼容外观。log/zombie_shoot/ZombieShootSession用公共CollectionSession接受数据，但仍负责本功能文件名、原点和元数据。Macro Stop只结束自己的批次；日志writer继续排空。

验证：Gradle build、公共CollectionRegression和完整Shootout回归通过。覆盖总开关默认OFF、类别保留、停止后拒收、重开新会话、原17类输出与诊断独立开关及后台排空。未新增Tick注册、没有自动开启Macro或采集。真实游戏采集量对照待验收。


### 12.10 Structure Shift / 08-view-input

client/input/ViewAngles提供三维角差和Yaw规范化；ViewRotationPlanner提供原有Tanh整步旋转及灵敏度量化；NativeViewInput持有本地输入来源、提交/消费、取消和观察回调。旧Shootout类型为兼容外观，实际实现不再依赖路径或弹道模型。业务仍提供终点、峰值、曲度和dt，并保留既有帧时钟、修正及射击判定。

ExpectedViewTracker与ViewWarningClock移至input，ViewWarningPresenter负责Toast/铃声与清理；Anti-Check适配器继续提供配置、文字和停止回调。before/submitted/consumed及诊断APPLIED原生消费后读回顺序不变，不额外等待Tick，不新增输入或调度。

验证：Gradle build、完整Shootout回归通过，含Tanh/跨界/极点、5504原生消费者模拟输入检查、208诊断关联检查及69700频率调度检查。测试runner改为同时编译新消费者与Minecraft stub，避免真实类字段描述符混用。未获得在线客户端，20/60Hz真实鼠标与提醒行为仍待验收。


### 12.11 Structure Shift / 09-gui-session

client/gui/GuiInputSource承接真实/虚拟输入来源及Inventory点击消费次序；GuiSessionState持有暂停意图、快照与显式恢复许可；GuiWait提供界面谓词、原有轮询次数超时及取消generation。旧PlayerGuiInput/GuiPauseState为兼容适配，Farm保留何时暂停、来源资格和路线编辑政策。

FarmCommandController四个Loadout观察阶段使用公共wait谓词和超时，仍由原ActionScheduler以50/100ms轮询。cancel/abort/discard使generation失效，已排队回调先核对generation；槽位、命令、动作顺序与原有失败处理保留。

验证：Gradle build、公共GuiWaitRegression、GUI/route/menu-input及Shootout回归通过。覆盖恢复一次、手动停机优先、输入来源、等待/取消及世界清理。未新增Tick、未改变计时或输入拦截。真实背包/聊天/ESC/命令GUI与断线仍待验收。


### 12.12 Structure Shift / 10-feature-registry

client/feature/FeatureRegistry提供唯一ID注册、启停委托和类型化只读snapshot查询；不保存业务running、不驱动Tick/帧/暂停。feature/adapters/ClientFeatures组装Farm、Shootout、Pest计时、开发者采集及HUD布局的原入口与业务生成快照。需要屏幕上下文的HUD编辑不提供无参start。

断线/退出的原接线按原顺序通过registry转发到对应owner，未重复注册Tick或重复推进状态。Farm仍由MacroManager驱动，Shootout仍由自身Tick与帧驱动。运行字段和停止原因仍属于各功能，HUD原读取链保持只读。

验证：Gradle build、公共FeatureRegistryRegression、完整Shootout回归通过，验证重复注册拒绝、查询不调用启停、只读映射及上下文启动保护。实际断线后的输入/GUI恢复与HUD显示待验收。


### 12.13 Structure Shift / 11-esp

公共client/esp/承接ESPManager、ESPRenderer、ESPTarget、style与render子目录，管理注册、提取/绘制、generation、GPU资源和退出清理。业务适配器迁入esp/adapters/PestESP与ZombieShootoutESP。初始化调用新位置，测试与文档相对路径同步迁移。

Pest仍读取原加载实体/插值规则；Shootout仍仅读取当前Macro的有效交汇快照，不独立扫描或求解。0.5格线框、四材质颜色、开关、深度和generation规则不变。公共层不选择业务目标。

验证：Gradle build、ESP与完整Shootout回归通过；包含4242项真实GPU tracer检查（RTX3070）与Pest/交汇框几何检查。GPU检查不是实际游戏场景验证；游戏中切世界及显示条件仍待验收。


### 12.14 Structure Shift / 12-ui

公共client/ui/继续提供widget、render、language、theme、HUD布局编辑；新增SettingBinding<T>分离保存值与编辑许可，现有Toggle装配使用该绑定。UiRegistry统一菜单工厂与HUD注册，拒绝重复ID，保留原HUD ID和MenuController入口。HudPanel公开既有绘制原语，不改变样式。

ui/adapters/GardenMenuAdapter承接界面装配，按功能分为Farm/Pest/Shootout/Preferences/Developer/HUD/ESP/Home/Hotkey/Hint/AntiCheck/Account内部适配器；复用同一屏幕的draft、折叠、焦点和滚动上下文。旧ui/screen/UltimateGardenScreen只保留原生屏幕身份与构造入口，现有输入/GUI钩子判断不变。FarmSelectionBinding承接作物保存校验与业务入口。HUD业务渲染分别位于adapters/farm与adapters/zombie_shoot，原类为兼容外观。

验证：Gradle build、context/gui/config/hud-adjust/shootout回归通过，覆盖禁用但保留真实值、菜单路径/顺序/折叠、双语资源、保存兼容、HUD显隐与拖缩放。初次迁移的三个泛型参数转发编译错误已修复并重新构建回归。未改配置权威来源、默认值、字体或颜色；真实菜单和HUD交互待游戏验收。


<a id="structure-shift"></a>
### 12.15 Structure Shift 集成结果与最终入口

12个已验证任务提交按编号以no-ff合并；合并端点dcb4fb6ef01a2107e88fc2bf4c108af5a382d9f7，逐次检查集成树与对应任务树一致。完整分支/提交/验证表及手动验收见[Structure Shift验证记录](docs/Structure_Shift_Validation.md)。

| 能力 | 最终机制入口 | 业务适配与状态/清理 |
|---|---|---|
| 上下文 | context/ClientContextEvents.onDisconnect/onStopping/onChange、ContextSignal.publish | 保留注册顺序；业务owner清状态；断线/退出finally释放公共世界引用 |
| RTT | network/NativeRttService.start/tick/pong/stop、sampledAtNanos；ClientRttConnection | 同一连接服务；Shootout决定探测间隔和补偿，不迁移业务安全策略 |
| 世界/几何 | observation/LoadedWorldView、SpatialQueries、ObservationCache、ScanBudget | 范围/预算/未知政策由采集或碰撞适配器提供，各会话分别持有 |
| 身份 | observation/EntityObservationTracker.identify/observe/markMissing/clear | 运行和采集分类独立；Target轮次、Tag排序和保留政策留业务 |
| 窗口 | observation/TimedWindow.expireInclusive/retainBoundary/resetIf/snapshot | Tick速度与纳秒XZ累计分别聚合，真实0与原判据保留 |
| 日志 | log/JsonSnapshot、JsonlWriter.offer/endBatch/close/awaitClose | log/zombie_shoot适配文件、类别、事件与批次；独立通道后台排空 |
| 采集 | collection/CollectionGate、CollectionSession.submit/stop | collection/zombie_shoot适配原条件；submit/stop互斥，stop后拒收 |
| 视角 | input/ViewAngles、ViewRotationPlanner、NativeViewInput | 原帧驱动不变；业务给终点/参数；原生消费后读回 |
| 观测/提醒 | input/ExpectedViewTracker、ViewWarningClock/Presenter | Anti-Check给配置和停止回调；每周期清理；Farm占位保持 |
| GUI | gui/GuiInputSource、GuiSessionState、GuiWait | Farm决定暂停/恢复及步骤；取消generation使旧等待回调失效 |
| 功能状态 | feature/FeatureRegistry.register/start/stop/snapshot | feature/adapters/ClientFeatures生成各快照；不驱动Tick/帧 |
| ESP | esp/ESPManager、ESPRenderer、style/render | esp/adapters读取业务数据；原颜色/几何/generation不变 |
| UI | ui/SettingBinding、UiRegistry、widget/render/language/theme/hud | ui/adapters/GardenMenuAdapter内按功能装配；HUD分别适配；旧Screen保留原生身份 |

总调试：Java25 clean build成功，10个run.ps1入口全部返回0。集成收尾修正后公共与Shootout回归再次通过；新增并发栅栏测试确认stop等待在途submit、结束后拒收。真实GPU tracer通过；独立runClient启动并观察到本地世界两种HUD停止态。用户正在操作开发窗口后停止抢占输入，Hypixel射击/RTT、菜单交互、在线账号/世界切换仍待手动验收。

本轮没有改动模型/资源内容、默认值、原帧和Tick调度、输入所有权或Focus政策；不推送、不合并master、不删除任务分支。任务逐项记录见12.3–12.14，新文件导航如下。

- [CollectionGate](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/CollectionGate.java)
- [CollectionSession](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/CollectionSession.java)
- [ZombieShootCollector](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootCollector.java)
- [ClientContextEvents](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/context/ClientContextEvents.java)
- [ContextSignal](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/context/ContextSignal.java)
- [PestESP](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/PestESP.java)
- [ZombieShootoutESP](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/ZombieShootoutESP.java)
- [ESPManager](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPManager.java)
- [ESPRenderer](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPRenderer.java)
- [ESPTarget](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPTarget.java)
- [ESPBoxRenderer](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPBoxRenderer.java)
- [ESPFillRenderer](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPFillRenderer.java)
- [ESPTracerRenderer](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPTracerRenderer.java)
- [ESPColorMode](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPColorMode.java)
- [ESPColorProvider](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPColorProvider.java)
- [ESPStyle](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPStyle.java)
- [ESPTargetStyle](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPTargetStyle.java)
- [ClientFeatures](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/feature/adapters/ClientFeatures.java)
- [FeatureRegistry](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/feature/FeatureRegistry.java)
- [GuiInputSource](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/gui/GuiInputSource.java)
- [GuiSessionState](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/gui/GuiSessionState.java)
- [GuiWait](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/gui/GuiWait.java)
- [ExpectedViewTracker](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/view/ExpectedViewTracker.java)
- [NativeViewInput](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/view/NativeViewInput.java)
- [ViewAngles](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/view/ViewAngles.java)
- [ViewRotationPlanner](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/view/ViewRotationPlanner.java)
- [ViewWarningClock](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/view/ViewWarningClock.java)
- [ViewWarningPresenter](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/view/ViewWarningPresenter.java)
- [JsonlWriter](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/JsonlWriter.java)
- [JsonSnapshot](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/JsonSnapshot.java)
- [ShootoutAimDiagnostics](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/ShootoutAimDiagnostics.java)
- [ZombieShootLogger](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/ZombieShootLogger.java)
- [ZombieShootSession](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/ZombieShootSession.java)
- [ClientRttConnection](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/network/ClientRttConnection.java)
- [NativeRttService](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/network/NativeRttService.java)
- [EntityObservationTracker](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/entity/EntityObservationTracker.java)
- [LoadedWorldView](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/query/LoadedWorldView.java)
- [ObservationCache](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/query/ObservationCache.java)
- [ScanBudget](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/query/ScanBudget.java)
- [SpatialQueries](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/query/SpatialQueries.java)
- [TimedWindow](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/sampling/TimedWindow.java)
- [MacroStatusHud](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/farm/MacroStatusHud.java)
- [FarmSelectionBinding](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/farm/FarmSelectionBinding.java)
- [GardenMenuAdapter](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/menu/GardenMenuAdapter.java)
- [ZombieShootoutHud](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/zombie_shoot/ZombieShootoutHud.java)
- [SettingBinding](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/SettingBinding.java)
- [UiRegistry](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/UiRegistry.java)


<a id="package-migration"></a>
## 13. 客户端目录迁移与验证

迁移基线：test/structure_shift @ a245acb438d59209b2a6fe38e288bdcb06a110d6；开始时工作区干净。master保持48fa5b9b711003ad00148336c8d44f6bb35b43ba。本轮仅在当前工作区搬包，不切换分支、不提交、不推送。

共166个客户端Java文件，其中134个迁移位置。按平台/运行支撑、配置、Shootout业务三批逐批编译；保留类名、兼容外观、原生屏幕身份及Mixin原包。GardenMenuAdapter整体位于platform/ui/adapters/menu，内部页面共享状态未拆分。

配置存储与模型分组；跨功能配置位于model直属层。运行模型、瞄准周期、目标管理和射击确认分别进入Shootout子包；只服务采集的分类、箭矢方向、轨迹、方块和遮挡实现进入runtime/world/collection/zombie_shoot。同名兼容外观以compat子包区分，未删除转接入口。

验证：迁移前Build和10个run.ps1均通过；迁移后Java25 clean build及相同10个入口均通过。账号符号链接测试在两次运行均因环境不支持而SKIP。最初的默认包测试导入补全问题已修复，断言未删；静态审查继续检查原32个Shootout文件。

语义核对：应用记录的全限定类名映射并忽略package/import/空白后，166个生产文件执行文本与基线一致，未放宽访问权限。模型、语言、Shader、Fabric入口和Mixin JSON保持原内容；配置/日志路径、字段、默认值与格式不变。

运行时：独立运行目录启动迁移后开发客户端，日志确认Mod/Mixin、音频和资源渲染初始化成功。窗口被Windows系统重启提示遮挡，未操作系统提示；菜单/HUD/ESP交互及Hypixel功能未标为实测通过。

证据保存在.gradle/package-migration/：baseline、mapping.json、各批编译、verified-clean-build.txt及verified-*.txt。环境产物不进入版本控制。

### 13.1 分层与调用方向

- Ultimate_GardenClient保留装配；platform提供上下文、网络、窗口/GUI、输入、UI、提示与持久化。
- runtime保留功能注册、原有调度、消息/命令/快捷键，以及world下观测/采集/ESP三组并列能力。
- macro保存Farm和Shootout执行。控制器与自动启动留Shootout根包；公共视角层不读取路径/弹道。
- 采集适配器组合观测并提交日志适配；公共写入器继续独立排空，不增加Tick或写线程。
- 输入Mixin仍识别UltimateGardenScreen具体类型；NativeViewInput仍通过原Accessor消费，before/submitted/consumed次序不变。

### 13.2 手动验收

1. 测试实例打开菜单，切中英文，检查折叠、Hint/Tooltip、子项禁用与保存值；退出重开状态一致。
2. 分别编辑两个HUD的位置/大小、隐藏和only-running；退出及重启后布局独立恢复。检查Pest和交汇框ESP原来源、颜色、隐藏条件。
3. 验证Farm启停、背包/聊天/ESC/Mod菜单暂停恢复、Loadout/Pest计时；停止或断线后不得恢复旧任务。
4. Hypixel验证Shootout 20/60/120Hz、自动/手动右键、确认ON/OFF、灯与僵尸优先级和重锁定；原频率、CD、速度及弹道不变。
5. 验证RTT启动/周期/Stop、Logs类别和START/END/排空，以及账号/世界切换清理。在线行为仍为Not Verified In-Game。

### 13.3 完整迁移映射

旧路径用于追溯，新路径为当前实现；均从client/开始。

| 原路径 | 当前路径 |
|---|---|
| `client/anti_check/ExpectedViewTracker.java` | [macro/zombie_shoot/aim/ExpectedViewTracker.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ExpectedViewTracker.java) |
| `client/anti_check/ViewDeviationCheckManager.java` | [macro/zombie_shoot/aim/ViewDeviationCheckManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ViewDeviationCheckManager.java) |
| `client/anti_check/ViewWarningClock.java` | [macro/zombie_shoot/aim/ViewWarningClock.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ViewWarningClock.java) |
| `client/collection/CollectionGate.java` | [runtime/world/collection/CollectionGate.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/CollectionGate.java) |
| `client/collection/CollectionSession.java` | [runtime/world/collection/CollectionSession.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/CollectionSession.java) |
| `client/collection/zombie_shoot/ZombieShootCollector.java` | [runtime/world/collection/zombie_shoot/ZombieShootCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootCollector.java) |
| `client/command/CommandHelper.java` | [runtime/command/CommandHelper.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandHelper.java) |
| `client/command/CommandListener.java` | [runtime/command/CommandListener.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandListener.java) |
| `client/command/CommandTree.java` | [runtime/command/CommandTree.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/command/CommandTree.java) |
| `client/config/AccountDirectories.java` | [platform/persistence/config/storage/AccountDirectories.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/storage/AccountDirectories.java) |
| `client/config/AccountIdentity.java` | [platform/persistence/config/storage/AccountIdentity.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/storage/AccountIdentity.java) |
| `client/config/ConfigIO.java` | [platform/persistence/config/storage/ConfigIO.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/storage/ConfigIO.java) |
| `client/config/ConfigManager.java` | [platform/persistence/config/ConfigManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java) |
| `client/config/CropConfig.java` | [platform/persistence/config/model/farm/CropConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/farm/CropConfig.java) |
| `client/config/CropData.java` | [platform/persistence/config/model/farm/CropData.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/farm/CropData.java) |
| `client/config/CropDefinition.java` | [platform/persistence/config/model/farm/CropDefinition.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/farm/CropDefinition.java) |
| `client/config/ESPConfig.java` | [platform/persistence/config/model/display/ESPConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/display/ESPConfig.java) |
| `client/config/FarmConfig.java` | [platform/persistence/config/model/farm/FarmConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/farm/FarmConfig.java) |
| `client/config/GeneralConfig.java` | [platform/persistence/config/model/GeneralConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/GeneralConfig.java) |
| `client/config/HudConfig.java` | [platform/persistence/config/model/display/HudConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/display/HudConfig.java) |
| `client/config/PestConfig.java` | [platform/persistence/config/model/farm/PestConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/farm/PestConfig.java) |
| `client/config/RandomDelayConfig.java` | [platform/persistence/config/model/RandomDelayConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/RandomDelayConfig.java) |
| `client/config/UIConfig.java` | [platform/persistence/config/model/display/UIConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/display/UIConfig.java) |
| `client/config/ViewDeviationConfig.java` | [platform/persistence/config/model/ViewDeviationConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/ViewDeviationConfig.java) |
| `client/config/ZombieShootLogCategory.java` | [platform/persistence/config/model/shootout/ZombieShootLogCategory.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/shootout/ZombieShootLogCategory.java) |
| `client/config/ZombieShootoutConfig.java` | [platform/persistence/config/model/shootout/ZombieShootoutConfig.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/shootout/ZombieShootoutConfig.java) |
| `client/context/ClientContextEvents.java` | [platform/context/ClientContextEvents.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/context/ClientContextEvents.java) |
| `client/context/ContextSignal.java` | [platform/context/ContextSignal.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/context/ContextSignal.java) |
| `client/esp/adapters/PestESP.java` | [runtime/world/esp/adapters/PestESP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/PestESP.java) |
| `client/esp/adapters/ZombieShootoutESP.java` | [runtime/world/esp/adapters/ZombieShootoutESP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/ZombieShootoutESP.java) |
| `client/esp/ESPManager.java` | [runtime/world/esp/ESPManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPManager.java) |
| `client/esp/ESPRenderer.java` | [runtime/world/esp/ESPRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPRenderer.java) |
| `client/esp/ESPTarget.java` | [runtime/world/esp/ESPTarget.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPTarget.java) |
| `client/esp/render/ESPBoxRenderer.java` | [runtime/world/esp/render/ESPBoxRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPBoxRenderer.java) |
| `client/esp/render/ESPFillRenderer.java` | [runtime/world/esp/render/ESPFillRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPFillRenderer.java) |
| `client/esp/render/ESPTracerRenderer.java` | [runtime/world/esp/render/ESPTracerRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPTracerRenderer.java) |
| `client/esp/style/ESPColorMode.java` | [runtime/world/esp/style/ESPColorMode.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPColorMode.java) |
| `client/esp/style/ESPColorProvider.java` | [runtime/world/esp/style/ESPColorProvider.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPColorProvider.java) |
| `client/esp/style/ESPStyle.java` | [runtime/world/esp/style/ESPStyle.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPStyle.java) |
| `client/esp/style/ESPTargetStyle.java` | [runtime/world/esp/style/ESPTargetStyle.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPTargetStyle.java) |
| `client/feature/adapters/ClientFeatures.java` | [runtime/feature/adapters/ClientFeatures.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/feature/adapters/ClientFeatures.java) |
| `client/feature/FeatureRegistry.java` | [runtime/feature/FeatureRegistry.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/feature/FeatureRegistry.java) |
| `client/gui/GuiInputSource.java` | [platform/gui/GuiInputSource.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/gui/GuiInputSource.java) |
| `client/gui/GuiSessionState.java` | [platform/gui/GuiSessionState.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/gui/GuiSessionState.java) |
| `client/gui/GuiWait.java` | [platform/gui/GuiWait.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/gui/GuiWait.java) |
| `client/hint/HintManager.java` | [platform/hint/HintManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/hint/HintManager.java) |
| `client/hint/KeyNameMap.java` | [platform/hint/KeyNameMap.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/hint/KeyNameMap.java) |
| `client/hotkey/MacroKeyBindings.java` | [runtime/hotkey/MacroKeyBindings.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java) |
| `client/input/ExpectedViewTracker.java` | [platform/input/view/ExpectedViewTracker.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/view/ExpectedViewTracker.java) |
| `client/input/NativeViewInput.java` | [platform/input/view/NativeViewInput.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/view/NativeViewInput.java) |
| `client/input/PlayerGuiInput.java` | [platform/gui/PlayerGuiInput.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/gui/PlayerGuiInput.java) |
| `client/input/ViewAngles.java` | [platform/input/view/ViewAngles.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/view/ViewAngles.java) |
| `client/input/ViewRotationPlanner.java` | [platform/input/view/ViewRotationPlanner.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/view/ViewRotationPlanner.java) |
| `client/input/ViewWarningClock.java` | [platform/input/view/ViewWarningClock.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/view/ViewWarningClock.java) |
| `client/input/ViewWarningPresenter.java` | [platform/input/view/ViewWarningPresenter.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/view/ViewWarningPresenter.java) |
| `client/input/VirtualInput.java` | [platform/input/virtual/VirtualInput.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/virtual/VirtualInput.java) |
| `client/input/VirtualKeyboard.java` | [platform/input/virtual/VirtualKeyboard.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/virtual/VirtualKeyboard.java) |
| `client/input/VirtualMouse.java` | [platform/input/virtual/VirtualMouse.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/virtual/VirtualMouse.java) |
| `client/log/JsonlWriter.java` | [platform/persistence/log/JsonlWriter.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/JsonlWriter.java) |
| `client/log/JsonSnapshot.java` | [platform/persistence/log/JsonSnapshot.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/JsonSnapshot.java) |
| `client/log/zombie_shoot/ShootoutAimDiagnostics.java` | [platform/persistence/log/zombie_shoot/ShootoutAimDiagnostics.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/ShootoutAimDiagnostics.java) |
| `client/log/zombie_shoot/ZombieShootLogger.java` | [platform/persistence/log/zombie_shoot/ZombieShootLogger.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/ZombieShootLogger.java) |
| `client/log/zombie_shoot/ZombieShootSession.java` | [platform/persistence/log/zombie_shoot/ZombieShootSession.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/ZombieShootSession.java) |
| `client/macro/GuiPauseState.java` | [platform/gui/GuiPauseState.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/gui/GuiPauseState.java) |
| `client/macro/zombie_shoot/ShootoutAimClock.java` | [macro/zombie_shoot/aim/ShootoutAimClock.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutAimClock.java) |
| `client/macro/zombie_shoot/ShootoutAimCompletion.java` | [macro/zombie_shoot/aim/ShootoutAimCompletion.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutAimCompletion.java) |
| `client/macro/zombie_shoot/ShootoutAimCorrection.java` | [macro/zombie_shoot/aim/ShootoutAimCorrection.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutAimCorrection.java) |
| `client/macro/zombie_shoot/ShootoutAimDiagnostics.java` | [platform/persistence/log/zombie_shoot/compat/ShootoutAimDiagnostics.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ShootoutAimDiagnostics.java) |
| `client/macro/zombie_shoot/ShootoutArrowConfirmation.java` | [macro/zombie_shoot/shot/ShootoutArrowConfirmation.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/shot/ShootoutArrowConfirmation.java) |
| `client/macro/zombie_shoot/ShootoutBabyStall.java` | [macro/zombie_shoot/model/ShootoutBabyStall.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/model/ShootoutBabyStall.java) |
| `client/macro/zombie_shoot/ShootoutCandidateSelection.java` | [macro/zombie_shoot/targeting/ShootoutCandidateSelection.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/targeting/ShootoutCandidateSelection.java) |
| `client/macro/zombie_shoot/ShootoutLampCollision.java` | [macro/zombie_shoot/shot/ShootoutLampCollision.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/shot/ShootoutLampCollision.java) |
| `client/macro/zombie_shoot/ShootoutModel.java` | [macro/zombie_shoot/model/ShootoutModel.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/model/ShootoutModel.java) |
| `client/macro/zombie_shoot/ShootoutMouseInput.java` | [macro/zombie_shoot/aim/ShootoutMouseInput.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutMouseInput.java) |
| `client/macro/zombie_shoot/ShootoutPolicy.java` | [macro/zombie_shoot/targeting/ShootoutPolicy.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/targeting/ShootoutPolicy.java) |
| `client/macro/zombie_shoot/ShootoutRtt.java` | [platform/network/ShootoutRtt.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/network/ShootoutRtt.java) |
| `client/macro/zombie_shoot/ShootoutShotClock.java` | [macro/zombie_shoot/shot/ShootoutShotClock.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/shot/ShootoutShotClock.java) |
| `client/macro/zombie_shoot/ShootoutSpeed.java` | [macro/zombie_shoot/model/ShootoutSpeed.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/model/ShootoutSpeed.java) |
| `client/macro/zombie_shoot/ShootoutTanhAim.java` | [macro/zombie_shoot/aim/ShootoutTanhAim.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ShootoutTanhAim.java) |
| `client/macro/zombie_shoot/ShootoutTargetLoss.java` | [macro/zombie_shoot/targeting/ShootoutTargetLoss.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/targeting/ShootoutTargetLoss.java) |
| `client/macro/zombie_shoot/ShootoutTargetRound.java` | [macro/zombie_shoot/targeting/ShootoutTargetRound.java](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/targeting/ShootoutTargetRound.java) |
| `client/macro/zombie_shoot/ZombieShootArrowAngles.java` | [runtime/world/collection/zombie_shoot/ZombieShootArrowAngles.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootArrowAngles.java) |
| `client/macro/zombie_shoot/ZombieShootBlockCollector.java` | [runtime/world/collection/zombie_shoot/ZombieShootBlockCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootBlockCollector.java) |
| `client/macro/zombie_shoot/ZombieShootClassifier.java` | [runtime/world/collection/zombie_shoot/ZombieShootClassifier.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootClassifier.java) |
| `client/macro/zombie_shoot/ZombieShootCollector.java` | [runtime/world/collection/zombie_shoot/compat/ZombieShootCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootCollector.java) |
| `client/macro/zombie_shoot/ZombieShootCoordinateSystem.java` | [runtime/world/collection/zombie_shoot/ZombieShootCoordinateSystem.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootCoordinateSystem.java) |
| `client/macro/zombie_shoot/ZombieShootEntityTracker.java` | [runtime/world/observation/entity/ZombieShootEntityTracker.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/entity/ZombieShootEntityTracker.java) |
| `client/macro/zombie_shoot/ZombieShootLogGate.java` | [runtime/world/collection/zombie_shoot/compat/ZombieShootLogGate.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootLogGate.java) |
| `client/macro/zombie_shoot/ZombieShootLogger.java` | [platform/persistence/log/zombie_shoot/compat/ZombieShootLogger.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ZombieShootLogger.java) |
| `client/macro/zombie_shoot/ZombieShootOcclusionAnalyzer.java` | [runtime/world/collection/zombie_shoot/ZombieShootOcclusionAnalyzer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootOcclusionAnalyzer.java) |
| `client/macro/zombie_shoot/ZombieShootPathAnalyzer.java` | [runtime/world/collection/zombie_shoot/ZombieShootPathAnalyzer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootPathAnalyzer.java) |
| `client/macro/zombie_shoot/ZombieShootPathCollector.java` | [runtime/world/collection/zombie_shoot/ZombieShootPathCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootPathCollector.java) |
| `client/macro/zombie_shoot/ZombieShootSession.java` | [platform/persistence/log/zombie_shoot/compat/ZombieShootSession.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ZombieShootSession.java) |
| `client/macro/zombie_shoot/ZombieShootSnapshot.java` | [platform/persistence/log/zombie_shoot/compat/ZombieShootSnapshot.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/log/zombie_shoot/compat/ZombieShootSnapshot.java) |
| `client/network/ClientRttConnection.java` | [platform/network/ClientRttConnection.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/network/ClientRttConnection.java) |
| `client/network/NativeRttService.java` | [platform/network/NativeRttService.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/network/NativeRttService.java) |
| `client/observation/EntityObservationTracker.java` | [runtime/world/observation/entity/EntityObservationTracker.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/entity/EntityObservationTracker.java) |
| `client/observation/LoadedWorldView.java` | [runtime/world/observation/query/LoadedWorldView.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/query/LoadedWorldView.java) |
| `client/observation/ObservationCache.java` | [runtime/world/observation/query/ObservationCache.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/query/ObservationCache.java) |
| `client/observation/ScanBudget.java` | [runtime/world/observation/query/ScanBudget.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/query/ScanBudget.java) |
| `client/observation/SpatialQueries.java` | [runtime/world/observation/query/SpatialQueries.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/query/SpatialQueries.java) |
| `client/observation/TimedWindow.java` | [runtime/world/observation/sampling/TimedWindow.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/sampling/TimedWindow.java) |
| `client/scheduler/ActionScheduler.java` | [runtime/scheduler/ActionScheduler.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/scheduler/ActionScheduler.java) |
| `client/scheduler/InputScheduler.java` | [runtime/scheduler/InputScheduler.java](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/scheduler/InputScheduler.java) |
| `client/ui/adapters/farm/MacroStatusHud.java` | [platform/ui/adapters/farm/MacroStatusHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/farm/MacroStatusHud.java) |
| `client/ui/adapters/FarmSelectionBinding.java` | [platform/ui/adapters/farm/FarmSelectionBinding.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/farm/FarmSelectionBinding.java) |
| `client/ui/adapters/GardenMenuAdapter.java` | [platform/ui/adapters/menu/GardenMenuAdapter.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/menu/GardenMenuAdapter.java) |
| `client/ui/adapters/zombie_shoot/ZombieShootoutHud.java` | [platform/ui/adapters/zombie_shoot/ZombieShootoutHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/zombie_shoot/ZombieShootoutHud.java) |
| `client/ui/hud/HudAdjustmentController.java` | [platform/ui/hud/HudAdjustmentController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/HudAdjustmentController.java) |
| `client/ui/hud/HudPanel.java` | [platform/ui/hud/HudPanel.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/HudPanel.java) |
| `client/ui/hud/MacroStatusHud.java` | [platform/ui/hud/MacroStatusHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/MacroStatusHud.java) |
| `client/ui/hud/ZombieShootoutHud.java` | [platform/ui/hud/ZombieShootoutHud.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/ZombieShootoutHud.java) |
| `client/ui/language/LanguageKeys.java` | [platform/ui/language/LanguageKeys.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/language/LanguageKeys.java) |
| `client/ui/language/LanguageManager.java` | [platform/ui/language/LanguageManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/language/LanguageManager.java) |
| `client/ui/language/LanguageType.java` | [platform/ui/language/LanguageType.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/language/LanguageType.java) |
| `client/ui/MenuController.java` | [platform/ui/MenuController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/MenuController.java) |
| `client/ui/render/MenuInputGraphics.java` | [platform/ui/render/MenuInputGraphics.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/render/MenuInputGraphics.java) |
| `client/ui/render/RoundedRect.java` | [platform/ui/render/RoundedRect.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/render/RoundedRect.java) |
| `client/ui/screen/UltimateGardenScreen.java` | [platform/ui/screen/UltimateGardenScreen.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/UltimateGardenScreen.java) |
| `client/ui/SettingBinding.java` | [platform/ui/SettingBinding.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/SettingBinding.java) |
| `client/ui/theme/ThemePalette.java` | [platform/ui/theme/ThemePalette.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/theme/ThemePalette.java) |
| `client/ui/UiRegistry.java` | [platform/ui/UiRegistry.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/UiRegistry.java) |
| `client/ui/widget/CollapsibleSection.java` | [platform/ui/widget/CollapsibleSection.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/CollapsibleSection.java) |
| `client/ui/widget/DropdownSelector.java` | [platform/ui/widget/DropdownSelector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/DropdownSelector.java) |
| `client/ui/widget/HelpTooltip.java` | [platform/ui/widget/HelpTooltip.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/HelpTooltip.java) |
| `client/ui/widget/LanguageSelector.java` | [platform/ui/widget/LanguageSelector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/LanguageSelector.java) |
| `client/ui/widget/SelectionCheckbox.java` | [platform/ui/widget/SelectionCheckbox.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/SelectionCheckbox.java) |
| `client/ui/widget/StyledButton.java` | [platform/ui/widget/StyledButton.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/StyledButton.java) |
| `client/ui/widget/ToggleSwitch.java` | [platform/ui/widget/ToggleSwitch.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/ToggleSwitch.java) |
| `client/ui/widget/TransparencySlider.java` | [platform/ui/widget/TransparencySlider.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/TransparencySlider.java) |
| `client/window/WindowsFocusController.java` | [platform/window/WindowsFocusController.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/window/WindowsFocusController.java) |
| `client/window/WindowsInputInterceptor.java` | [platform/window/WindowsInputInterceptor.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/window/WindowsInputInterceptor.java) |
| `client/window/WindowsNativeBridge.java` | [platform/window/WindowsNativeBridge.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/window/WindowsNativeBridge.java) |
| `client/window/WindowsState.java` | [platform/window/WindowsState.java](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/window/WindowsState.java) |


## 14. runtime/world → function 目录迁移

本轮将client/runtime/world整体迁为client/function，保留observation、collection、esp及其子包结构，31个Java文件原样搬迁。同步更新生产源码、测试与PowerShell/Python路径；Mixin自身包位置及资源路径不变。旧路径在历史记录和迁移表中仅供追溯，现行链接和目录图已更新。

调用关系不变：客户端装配与业务适配器引用function下ESP、观测及采集；日志适配仍位于platform/persistence/log；FeatureRegistry不增加Tick/帧驱动。未新增兼容类、放宽访问权限或修改业务算法、配置字段、默认值与日志格式。

验证记录和执行前快照位于.gradle/function-migration/，不进入版本控制。完整clean build与全部tests子目录run.ps1结果见下方最终记录。保留当前分支与HEAD，未提交或推送。

### 14.1 迁移映射

| 原位置 | 当前位置 |
|---|---|
| client/runtime/world/collection/CollectionGate.java | [client/function/collection/CollectionGate.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/CollectionGate.java) |
| client/runtime/world/collection/CollectionSession.java | [client/function/collection/CollectionSession.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/CollectionSession.java) |
| client/runtime/world/esp/ESPManager.java | [client/function/esp/ESPManager.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPManager.java) |
| client/runtime/world/esp/ESPRenderer.java | [client/function/esp/ESPRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPRenderer.java) |
| client/runtime/world/esp/ESPTarget.java | [client/function/esp/ESPTarget.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/ESPTarget.java) |
| client/runtime/world/observation/entity/EntityObservationTracker.java | [client/function/observation/entity/EntityObservationTracker.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/entity/EntityObservationTracker.java) |
| client/runtime/world/observation/entity/ZombieShootEntityTracker.java | [client/function/observation/entity/ZombieShootEntityTracker.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/entity/ZombieShootEntityTracker.java) |
| client/runtime/world/observation/query/LoadedWorldView.java | [client/function/observation/query/LoadedWorldView.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/query/LoadedWorldView.java) |
| client/runtime/world/observation/query/ObservationCache.java | [client/function/observation/query/ObservationCache.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/query/ObservationCache.java) |
| client/runtime/world/observation/query/ScanBudget.java | [client/function/observation/query/ScanBudget.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/query/ScanBudget.java) |
| client/runtime/world/observation/query/SpatialQueries.java | [client/function/observation/query/SpatialQueries.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/query/SpatialQueries.java) |
| client/runtime/world/observation/sampling/TimedWindow.java | [client/function/observation/sampling/TimedWindow.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/observation/sampling/TimedWindow.java) |
| client/runtime/world/esp/adapters/PestESP.java | [client/function/esp/adapters/PestESP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/PestESP.java) |
| client/runtime/world/esp/adapters/ZombieShootoutESP.java | [client/function/esp/adapters/ZombieShootoutESP.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/ZombieShootoutESP.java) |
| client/runtime/world/esp/render/ESPBoxRenderer.java | [client/function/esp/render/ESPBoxRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPBoxRenderer.java) |
| client/runtime/world/esp/render/ESPFillRenderer.java | [client/function/esp/render/ESPFillRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPFillRenderer.java) |
| client/runtime/world/esp/render/ESPTracerRenderer.java | [client/function/esp/render/ESPTracerRenderer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/render/ESPTracerRenderer.java) |
| client/runtime/world/esp/style/ESPColorMode.java | [client/function/esp/style/ESPColorMode.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPColorMode.java) |
| client/runtime/world/esp/style/ESPColorProvider.java | [client/function/esp/style/ESPColorProvider.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPColorProvider.java) |
| client/runtime/world/esp/style/ESPStyle.java | [client/function/esp/style/ESPStyle.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPStyle.java) |
| client/runtime/world/esp/style/ESPTargetStyle.java | [client/function/esp/style/ESPTargetStyle.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/style/ESPTargetStyle.java) |
| client/runtime/world/collection/zombie_shoot/ZombieShootArrowAngles.java | [client/function/collection/zombie_shoot/ZombieShootArrowAngles.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootArrowAngles.java) |
| client/runtime/world/collection/zombie_shoot/ZombieShootBlockCollector.java | [client/function/collection/zombie_shoot/ZombieShootBlockCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootBlockCollector.java) |
| client/runtime/world/collection/zombie_shoot/ZombieShootClassifier.java | [client/function/collection/zombie_shoot/ZombieShootClassifier.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootClassifier.java) |
| client/runtime/world/collection/zombie_shoot/ZombieShootCollector.java | [client/function/collection/zombie_shoot/ZombieShootCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootCollector.java) |
| client/runtime/world/collection/zombie_shoot/ZombieShootCoordinateSystem.java | [client/function/collection/zombie_shoot/ZombieShootCoordinateSystem.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootCoordinateSystem.java) |
| client/runtime/world/collection/zombie_shoot/ZombieShootOcclusionAnalyzer.java | [client/function/collection/zombie_shoot/ZombieShootOcclusionAnalyzer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootOcclusionAnalyzer.java) |
| client/runtime/world/collection/zombie_shoot/ZombieShootPathAnalyzer.java | [client/function/collection/zombie_shoot/ZombieShootPathAnalyzer.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootPathAnalyzer.java) |
| client/runtime/world/collection/zombie_shoot/ZombieShootPathCollector.java | [client/function/collection/zombie_shoot/ZombieShootPathCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/ZombieShootPathCollector.java) |
| client/runtime/world/collection/zombie_shoot/compat/ZombieShootCollector.java | [client/function/collection/zombie_shoot/compat/ZombieShootCollector.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootCollector.java) |
| client/runtime/world/collection/zombie_shoot/compat/ZombieShootLogGate.java | [client/function/collection/zombie_shoot/compat/ZombieShootLogGate.java](src/client/java/com/kuangsanfeng/ultimategarden/client/function/collection/zombie_shoot/compat/ZombieShootLogGate.java) |

本轮起点：a84a8570442e13d0b6b65bcfa3301322f4232ef7；开始时工作区干净。

### 14.2 验证结果

- 使用 JDK 25 执行 `gradlew.bat clean build --offline`：通过。
- 逐项执行 `tests/{context,config,gui,route,menu-input,focus,hints,hud-adjust,esp,shootout}/run.ps1`：10 组全部通过，退出码均为 0。
- 与执行前快照逐文件对照，生产 Java 文本仅有包名及路径映射变化；源码和测试未遗留旧包名或旧路径。资源文件未变，`git diff --check` 通过。
- 分支仍为 `master`，HEAD 与上述起点一致。未执行提交或推送。
- 本次未进行游戏内验证；输入、网络与服务器交互的实机表现仍需手动验收，回归脚本通过不等同于 Hypixel 实测。
