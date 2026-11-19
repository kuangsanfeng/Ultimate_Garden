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


## 15. Instant Transmission 位移采集（本地测试分支）

基线为 master / 6be9b78；开始时工作区干净，本地 origin/master 与 master 一致。在 test/Instant-Transmission-dataCollect 开发，master 指针不变。本项不自动合并、提交或推送。

### 15.1 入口、配置与公共能力

| 位置 | 职责与调用关系 |
|---|---|
| [InstantTransmissionController](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/InstantTransmissionController.java) | 客户端线程单例；start/toggle/stop 为菜单和热键的统一入口；tick 负责试验、归位、采样，frame 仅推进公共鼠标视角。 |
| [TransmissionTrial](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/TransmissionTrial.java) | 计划点击 Tick、派发计数、响应观察、XZ 稳定段与落地分离；200 Tick 有界观察。 |
| [InstantTransmissionConfig](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/InstantTransmissionConfig.java) | GeneralConfig 持有；保存于现有账号隔离 general.json 的 instantTransmission 对象，沿用 Schema、校验、备份与原子写入。六项角度参数及可选 distanceBonusNote 持久化，运行状态不保存。 |
| [TransmissionLog](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/log/TransmissionLog.java) | 业务事件经 CollectionSession 和 JsonlWriter 转为只读快照；有界后台队列写入，END 后拒收，异步排空关闭；退出客户端提供有界等待。 |
| [TransmissionPositionMixin](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/TransmissionPositionMixin.java) | 在原生 handleMovePlayer 已切回客户端线程、应用位置前取坐标，RETURN 后取坐标；同 Tick 多次更新各自记录，不取消包或修改处理结果。 |

菜单：Settings → Developer Options → Instant Transmission，提供启停、状态、六项角度输入及装备距离加成备注。运行期间固定参数，输入控件禁用。默认 Yaw 0→45 / 5°，Pitch 0→-90 / -5°；未整除时补终点且只出现一次。启动校验有限角度、正步长和有序范围，并限制总试验数量。热键 Toggle Instant Transmission Test 默认 None，沿用 MacroKeyBindings 上升沿和文本输入保护。

视角：沿用 ShootoutAimFrameMixin 所在的原生鼠标消费前帧回调，调用 NativeViewInput.aim → ViewRotationPlanner，使用公共 Tanh 规划、灵敏度、反转、50 ms 步间上限及原生消费后读回；业务不直接写视角。热栏通过 VirtualKeyboard.slot(2)，观察第二格实际被选中后才使用 NativeViewInput.click 的完整 Press/Release。WindowsInputInterceptor 的现有 external owner 回调组合本测试所有权；Farm 和 Shootout 互斥启动，测试不改 Focus 设置。

### 15.2 流程与采样

启动固定脚部所在基准方块与预期站立 Y → 菜单启动时等待关闭菜单 → 实际对齐 0/0 → 切第二格并确认 → 向下复位 → 核对基准 XZ 中心和 Y（容差 0.01）→ 固定本组角度 → 收齐两个完整 Tick → 首击。

按 Yaw、Pitch、1/2/3 次点击组遍历，默认 570 组。END_CLIENT_TICK 同一阶段在相对 Tick 0、2、4 派发；不因等待前次位置更新而推迟组内点击，也不追补到同一 Tick。TimedWindow 只保存两条预采样，首击纳入 -2/-1，记录 0 至响应及落地或超时。组间先落地、归正 0/0，再按 XZ 差使用 W/A/S/D 回到基准方块；释放移动后向下使用一次，标为 RESET。为处理竖直视向的 Yaw 不唯一，测试角对齐先通过公共链路对齐本组 Yaw / Pitch 0，再对齐本组 Pitch。最后一组结束直接释放所有权，不复位；默认最后测试角为 +45/-90。

位置应用事件保留应用前后原始 XYZ，位移超过 0.05 格标为可辨识位置更新；这不是服务端技能成功认证。最后一次派发后必须观察到新响应，XZ 两个连续 Tick 稳定（0.01 格）才给出暂定终点；记录稳定段起点，再以 onGround 记录首个落地样本。响应重置稳定段，避免垂直传送沿用传送前的 Y。多击响应不足时等待有界观察，标为 LAST_USE_UNCONFIRMED 或 NO_IDENTIFIABLE_RESPONSE，不把计划次数改成观测次数。自然下落本身不作为传送证据。单击恰有一次可辨识更新仅提供配对候选，仍不能证明因果；多击统一标为配对未确认。

### 15.3 日志、清理及验证边界

实际文件：当前游戏实例下 logs/ultimategarden/instant transmission/instant-transmission.jsonl，追加写入并保留历史。每批 START/END 有绝对时间戳、batchId、单调 elapsedMs 和 Tick；每组 trialId 关联 TRIAL_START、RIGHT_CLICK、SNAPSHOT、POSITION_UPDATE、RESULT。分别保留计划次数/计划 Tick、实际派发数/时刻、可辨识更新数、原始坐标/速度/实际角度/输入状态、暂定终点及落地点；使用成功次数为 UNCONFIRMED，地形截断为 UNKNOWN。批次参数另有 PARAMETERS；中断用 CANCEL_OR_FINISH 和 END 记录原因与部分进度，不伪造完整结果。

GUI/失焦/鼠标释放/暂停、槽位变化、冲突任务、世界/连接/账号变化、手动停止、归位卡住、状态超时或日志失败均进入统一 stop，取消后续点击和转向，释放本实例键盘持键、清空窗口/试验/世界引用，结束采集并排空已接受日志。排空中的写入器完成前不接受新批次，避免批次交错。

[独立回归入口](tests/instant-transmission/run.ps1)覆盖角度端点、570 组 0/2/4 时序、前一次稳定段不能结束后一次点击、垂直更新、稳定段起点/落地分离、两条预采样、配置保存、JSONL 精度与单次 END，以及公共输入接线静态检查。测试结果及构建输出保存在忽略目录 .gradle/instant-audit/。尚未进行 SkyBlock 实机验证：技能是否接受点击、Mana/地形影响、向下复位的服务器行为、网络延迟下多击配对及最终实际角度均须手动验收；本地模拟通过不等同于服务器实测。

本次验证结果：JDK 25 下 clean build --offline 通过；新增 instant-transmission 回归 1,748 项通过，既有 context、config、gui、route、menu-input、focus、hints、hud-adjust、esp、shootout 共 10 组脚本全部退出码 0；最后加入键盘会话代号防止旧回调跨批次后，再次 build 与专属回归通过。git diff --check 通过。新增中文及英文键一致、文档新增源码链接有效。结果见 .gradle/instant-audit/final-results.tsv，最后补验见 generation-build.txt / generation-test.txt。未执行 SkyBlock 实机验证。


### 15.4 RESET 跳跃后向下归中修复

仅调整 RESET：首次归中和组间归中仍共用 SETTLE 分支。在公共鼠标链路确认 Pitch +90°、完成原有稳定等待后，释放移动键，以 VirtualKeyboard.keyDown("SPACE") 发起一次跳跃，进入 RESET_JUMP；下一客户端 Tick 先 keyUp("SPACE")，再读取 onGround 与实际 Y。只有 onGround == false 且 Y 高于起跳前，才通过原有 beginTrial(1) → NativeViewInput.click 发起一次标记为 RESET 的右键。预采样窗口保持滚动，右键后仍沿用 RESET_WAIT 的响应、落地及基准 XZ/Y 核对。

[TransmissionResetJump](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/TransmissionResetJump.java)仅持有本次 RESET 的起跳 Y、Tick、单调起点和一次性许可。未离地最多等待 20 Tick / 1 秒；首次观测离地却未高于起跳点、超时、视角失准或空中右键被占用时进入既有统一 stop 失败收尾，不重跳、不补点。stop 清除许可并沿用 keyboard.releaseAll() 释放跳跃键，停止后不能由旧等待发出右键。

修改入口为 InstantTransmissionController.tick()/stop() 和 RESET 专用许可；正式 TEST 分支、TransmissionTrial.due() 以及 0/2/4 Tick 调度未改。TransmissionRegression 新增离地前禁点、单次许可、未起跳/错过窗口、取消后禁点与重复 RESET 检查，并核对一次跳跃按下、释放顺序及统一 Stop 清理接线。SkyBlock 的实际起跳消费、空中 AOTE 响应和落地归中仍为 Not Verified In-Game。

本次 RESET 修复验证：JDK 25 的 build --offline 通过；instant-transmission 回归 1,809 项检查通过（含新增 61 项 RESET 判定/接线检查），context 与 shootout 回归退出码均为 0。执行前文件哈希对照确认仅修改控制器、RESET 专用许可、测试和本文档，既有正式试验调度与落地核对代码未变。证据保存在 .gradle/instant-reset-audit/，不进入版本控制。


### 15.5 RESET 起跳高度阈值调整

本项替代 15.4 中“离地且 Y 高于起跳前即可右键”的旧触发条件。[TransmissionResetJump.update()](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/TransmissionResetJump.java)在起跳前记录脚部 startY，离地后低于 4.0 格保持 WAIT；仅在 onGround == false 且 currentY - startY >= 4.0 的首个客户端 Tick 返回一次 CLICK，兼容单 Tick 跨越阈值。未达到阈值便落地返回 FAILED，原有未起跳/超时安全失败仍保留。clear() 同时清除起跳高度、空中标记与待触发许可，中途停止后不会补发。

控制器原有 Pitch +90° 对齐检查、一次跳跃及释放、RESET 右键派发、落地等待、基准 XZ/Y 校验与正式测试 0/2/4 Tick 调度均未修改。[TransmissionRegression](tests/instant-transmission/TransmissionRegression.java)覆盖阈值以下（含紧邻 4.0 的浮点值）、恰好 4.0、跨越 4.0、地面高度不足以触发、低跳落地失败、单次放行与停止取消。JDK 25 的 build --offline 通过，专属回归共 1,824 项检查通过；输出位于 .gradle/instant-reset-height-audit/。SkyBlock 实际跳跃高度和空中 AOTE 响应仍为 Not Verified In-Game；本次未改变跳跃能力或高度。


### 15.6 正式 Pitch 扫描改为向上

[InstantTransmissionConfig](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/InstantTransmissionConfig.java)的正式测试默认三元组改为 PITCH_START=0、PITCH_END=-90、PITCH_STEP=-5；Yaw 默认仍为 0→+45 / +5。菜单直接读取同一配置模型。axis() 支持升降序：步长必须有限非零、符号与非零范围方向一致；起止相同仅测一次，未整除时补终点且不重复。保留原有样本数量上限。默认仍为 10×19×3=570 组，最后计划视角为 +45/-90。

load() 仅在持久化对象中显式存在数值型的旧默认 Pitch 三元组 0/+90/+5 时迁移为 0/-90/-5，通过原有 General 模块的修复备份和原子保存回写；保留 Yaw、备注及其他配置，自定义 Pitch 三元组不迁移。缺失字段采用新默认值。本次只读检查 run/config 中两个 general.json，均没有 instantTransmission Pitch 字段，不需要直接改写运行配置文件。

启动对齐仍为 Yaw 0 / Pitch 0；RESET 仍对齐 +90°，单次跳跃后在空中且脚部相对高度 >=4.0 时向下使用 AOTE。控制器、RESET 专用许可、右键和 0/2/4 Tick 调度均未改。

验证：JDK 25 的 build --offline 通过；专属回归 1,873 项通过，涵盖完整默认降序、非整除终点、相等端点、错误符号/零步长拒绝、旧默认迁移回写与备份、自定义保留、最后 +45/-90 计划角度和 RESET +90 接线；config 回归通过。执行前后哈希核对范围为配置模型、专属测试与本文档。结果保存在 .gradle/instant-pitch-audit/。最终实际视角和 SkyBlock 响应仍为 Not Verified In-Game。


### 15.7 Instant Transmission 测试 HUD

HUD 标题固定为 Instant Transmission。[InstantTransmissionHud](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/instant_transmission/InstantTransmissionHud.java)由 Ultimate_GardenClient 使用 UiRegistry.hud("instant_transmission_status", ...) 注册，复用 HudPanel 的深色半透明背景、标题、白色两列文字和分隔线。正常游戏由独立 Show Instant Transmission HUD 开关控制（默认开启）；停止后保留结果直到下次成功启动。隐藏原生 HUD 或打开 Mod 菜单时沿用已有显示规则；布局编辑模式可预览。

[InstantTransmissionController.hudSnapshot()](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/InstantTransmissionController.java)提供[不可变 TransmissionHudSnapshot](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/TransmissionHudSnapshot.java)：按固定计划计算组序号/总组数，区分正式点击组和 RESET；读取正式目标/实际角度、归中对齐角度、起跳相对高度、预采样数、trial.actual/trial.planned、trial.updates 及距首击 Tick。RESET 高度通过 TransmissionResetJump.relativeHeight() 只读取得，既不改许可也不重置起跳点。阶段展示从原状态和采样器已有条件派生，区分点击序列、等待位移稳定及等待落地，不驱动业务状态。

停止前只复制标量和字符串，保留真实停止原因及最近 RESULT；自然完成使用最后一组试验快照，避免组索引递增及 trial 清空后丢失最后角度/计数。缓存只持有两份不可变显示快照和结果字符串，不保留实体、世界、输入或历史轨迹，下一次启动重置。原有输入、角度、时序、响应判定和清理顺序不变。

[TransmissionHudContent](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/instant_transmission/TransmissionHudContent.java)负责纯展示转换，使用 transmission_hud.* 双语键；具体阶段只显示相关详情。实际派发数与可辨识位置更新数分行显示，不把位置更新计数当成技能成功次数。超时、复位校验、缺少明确位移及手动停止等原因使用对应翻译；未知原因保留原始代码。

布局复用 HudAdjustmentController，以 HudId.INSTANT_TRANSMISSION 隔离位置与大小；账号 hud.json 新增 instantTransmissionBounds {x,y,width,height}，沿用归一化坐标、校验、保存、旧配置兼容机制，Farm/Shootout 的布局及开关不变。通过 Settings → HUD → Adjust HUD Position & Size 编辑，新增面板默认位于屏幕下方中部。

验证：JDK 25 的 build --offline 通过；instant-transmission 回归 1,914 项通过，覆盖阶段切换、高度、目标/实际角度、派发与更新计数分离、最终结果及原因保留、双语和只读展示接线；hud-adjust 回归 134 项通过，含新 HUD 的实际编辑控制器拖动/缩放、独立性与保存恢复（原生边界使用 stubs）。config、gui、context、shootout 回归均通过。控制器移除 HUD 专用观测/复制代码后，与本轮修改前源码一致（已考虑换行格式）；git diff --check 通过。输出保存在 .gradle/instant-hud-audit/。未做真实游戏中的面板视觉与 SkyBlock 阶段联动验证。


### 15.8 Instant Transmission HUD 独立显示开关与正常渲染修复

原正常渲染入口要求 snapshot.available() 为 true，因而未开始试验时被提前返回；布局预览绕过该条件。同时 Settings → HUD 没有对应显示开关。本次保留原有 UiRegistry 注册，仅修正自身入口。

Settings → HUD 新增 Show Instant Transmission HUD / 显示瞬间传送 HUD，使用现有 Toggle、Hint、保存反馈和模块保护机制。账号 hud.json 的 showInstantTransmissionHud 默认 true，旧配置缺失时亦为 true；仅接受布尔值，保持其他 HUD 开关及 INSTANT_TRANSMISSION 的位置/尺寸不变。

[HudConfig.showTransmission(editing)](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/display/HudConfig.java)返回 editing || showInstantTransmissionHud；[InstantTransmissionHud.render()](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/instant_transmission/InstantTransmissionHud.java)每帧读取同一配置源，不再受试验是否开始/运行或 available 标记限制。有效游戏环境下开启显示、关闭隐藏；仍沿用原生隐藏 HUD、Mod 菜单等既有环境条件。编辑模式始终允许预览；退出编辑恢复保存的显示值。切换开关不会修改控制器或最终快照，停止/结束后的最后状态和原因继续保留。

[TransmissionHudRenderRegression](tests/hud-adjust/TransmissionHudRenderRegression.java)直接执行生产渲染入口，以游戏/绘图 stubs 捕获是否绘制、标题及原因文字，覆盖未启动显示、开关即时生效、手动停止/完成、关闭时预览、退出后隐藏、布局不变及重新加载。原编辑回归追加关闭状态下拖动/缩放与保存值保留验证。JDK 25 的 build --offline、hud-adjust（135 项编辑检查 + 27 项渲染检查）、config、gui 和 instant-transmission（1,914 项）均通过；证据在 .gradle/instant-hud-toggle-audit/。原生游戏画面尚未实测，stubs 检查不等同于实机视觉验证。


### 15.9 Instant Transmission 输入冲突与启动诊断修复

本项移除 InstantTransmissionController 对 !FarmController.canEditRouteConfiguration() 的冲突判定，保留该 API 原有的路线编辑用途。[FarmController.inputOwnership()](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/farm/FarmController.java)新增只读 InputOwnership：running 为 FarmState.isRunning()，heldInput 为本实例 VirtualInput.hasHeldInput() 或 WindowsInputInterceptor.isLocallyEnabled()，actionPending 为 command.hasPendingWork() 或尚未执行的菜单动作。VirtualInput 查询自身 VirtualKeyboard 的持键集合和 VirtualMouse.leftDown；isLocallyEnabled() 仅读取 Farm 使用的本地拦截预留，不读取包含外部所有者的 shouldBlockInput()，避免把 Instant Transmission 自己算作 Farm 占用。两者均为只读查询，不读取玩家物理按键。只有这些运行/持有/动作条件占用输入；菜单开关、路线可编辑性及独立 Pest 倒计时不参与。倒计时触发待执行 Loadout 后，沿动作队列的实际状态产生冲突。未改变 Farm 路线、动作、计时器及释放语义。

[TransmissionInputCheck](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/TransmissionInputCheck.java)接收判定点一次读取的值，供 startFailure()/runtimeFailure() 共用 ownerFailure()，依次区分 Farm、Shootout 和 HUD 编辑器。运行期不再要求 onGround（RESET 跳跃仍须正常运行）；启动须落地。世界/连接引用缺失与变化分别报告，不再用原笼统的 INVALID_WORLD_GROUND_OR_INPUT_CONFLICT。可归因的视角控制前提（玩家死亡、屏幕/叠加层、暂停、失焦、鼠标未捕获）也各有原因码。

[InstantTransmissionController.start(StartSource)/waitForMenu()/usable()](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/InstantTransmissionController.java)使用同一检查快照。菜单按钮标记 MENU，热键标记 HOTKEY，旧入口保留 API。普通游戏热键请求立即校验；有屏幕时进入 WAIT_GUI，此时 running=true，HUD 仍显示等待关闭菜单，不创建虚拟键盘或采集批次。关闭菜单后等待原有原生/Farm 鼠标恢复完成（屏幕/叠加层关闭、非暂停、窗口有效且鼠标已捕获），再读取并校验落地与占用，安全通过后 activate() → START_ALIGN。等待期间真实上下文丢失/变化或已明确的争用安全终止；不新增 grabMouse、Focus 修改或输入恢复捷径。

| 原因码 | 判据 |
|---|---|
| PLAYER_UNAVAILABLE / WORLD_UNAVAILABLE / CONNECTION_UNAVAILABLE | 相应引用为空 |
| PLAYER_NOT_ON_GROUND | 启动校验时未落地 |
| INPUT_CONFLICT_FARM / INPUT_CONFLICT_SHOOTOUT / INPUT_CONFLICT_HUD_EDITOR | 对应实际占用来源 |
| WORLD_CHANGED / CONNECTION_CHANGED / ACCOUNT_CHANGED | 会话引用变化或已有上下文事件 |
| PLAYER_NOT_ALIVE / SCREEN_OPEN / OVERLAY_ACTIVE / CLIENT_PAUSED / WINDOW_INACTIVE / MOUSE_NOT_CAPTURED | 运行期已观测到的控制前提失效 |
| GUI_WAIT_TIMEOUT / GUI_RESTORE_TIMEOUT | 等待关闭菜单或鼠标恢复超时 |

拒绝启动统一走 reject()，用新的 TransmissionHudSnapshot.rejected() 覆盖旧 finalHud，不继承上一轮进度或错误。异常停止仍走统一 stop()，只取消本测试的视角/右键、释放本实例持键；不暂停、取消或清理 Farm/Shootout。不可变快照新增 terminationStage，保留旧构造器兼容；前端同时显示 START_REJECTED（拒绝启动）、RUNNING_STOP（运行中停止）等终止阶段、原阶段及原因。菜单 status、HUD 与 lastDiagnostic 使用同一原因码。

每次拒绝或异常停止在统一出口诊断一次：source、stage、phase、code，以及当时的引用可用性/变化、onGround、屏幕类型、Farm 运行/持键/动作标志、Shootout/HUD 编辑占用、暂停、焦点及鼠标捕获等只读值。已有批次写 FAILURE_DIAGNOSTIC 到原 instant-transmission.jsonl；尚未建立批次时写一条 Mod WARN 到当前游戏 latest.log，不为拒绝启动新建采集批次。正常完成/手动停止保留原结束记录；后续停止态 Tick/帧不重复诊断。日志异常不阻止本测试输入清理。

验证：JDK 25 的 build --offline 通过。[instant-start/run.ps1](tests/instant-start/run.ps1)执行生产控制器，游戏、所有者输入边界和日志使用 stubs，93 项覆盖正常热键启动、菜单关闭/鼠标恢复、独立 Pest 倒计时、Loadout 动作出现后的运行期争用、各子条件错误码、新失败覆盖旧结果、停止清理和诊断去重。instant-transmission 1,946 项、menu-input 73 项及 hud-adjust/config/gui/route/context/shootout/focus/hints/esp 全部通过，共 12 组回归退出码 0。记录在 .gradle/instant-start-audit/。尚待 SkyBlock 实机确认：同站位菜单/热键两入口的原生鼠标恢复、实际 Pest Loadout 触发时的争用与现场原因展示；本地 stubs 不等同于服务器验证。

WAIT_GUI 在本次请求中已属于 running 状态，等待期间或关闭后校验失败通过统一 stop() 标为 RUNNING_STOP，并保留 WAIT_GUI 阶段；尚未进入该等待状态就被拒绝的请求标为 START_REJECTED。输入本地/外部所有权分离另经 focus 回归验证，最终为 196 项通过。末次复验见 final-build.txt、final-start.txt、final-instant-transmission.txt、final-focus.txt；初次完整 12 组结果保留在 results.tsv。


### 15.10 Instant Transmission RESET 归位诊断（只读观测）

本次仅增加记录；保留原有 WALK 方块归属判断、按键提交顺序、一次跳跃、相对起跳高度达到 4 格且仍在空中的右键门槛、正式试验 0/2/4 Tick 调度，以及停止和落地校验行为。没有修复或预判“走过头”的原因。

**入口与生命周期**

- [InstantTransmissionController](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/InstantTransmissionController.java) 的 `beginResetDiagnostics()/resetDiagnosticSnapshot()` 提供现场只读数据。首次激活时创建初始 RESET 窗口；后续在 `observe()` 确认正式试验落地时开启下一窗口，关联上一组 `previousTrialId`。`BASE_CONFIRMED` 或统一 `stop()` 结束窗口。尚在等待正式试验响应的落地阶段也持续记录。
- [TransmissionResetDiagnostics](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/log/TransmissionResetDiagnostics.java) 持有一个 `resetId`、最近观测、最多六个计划按键、最近一次原生输入及一个固定阶段 Tick 样本；无历史轨迹集合。结束时清除临时引用。
- `tick()` 在原有 END_CLIENT_TICK 控制器入口、Tick 递增后、状态机推进前采样，阶段为 `END_CLIENT_TICK_BEFORE_TEST`。仅在 RESET 窗口写 `RESET_TICK`。窗口在该 Tick 中途开启时，补写同 Tick 已缓存的固定阶段样本（`windowOpenedThisTick`）；窗口外只保留一份内存快照，不写逐 Tick 明细。
- `enter()`、`submitResetKey()/releaseResetKeys()`、跳跃、`dispatch()`、落地校验在各自原有决策或输入位置补充记录。输入包装仍只调用原来的虚拟输入一次。

**事件与字段**

| 事件 | 实际记录 |
|---|---|
| `RESET_BEGIN / RESET_STATE_CHANGE / RESET_END` | resetId、previousTrialId、前后状态、原因及现场坐标/视角 |
| `RESET_TICK` | 状态、Tick、脚部/速度 XYZ、onGround、实际/目标 Yaw/Pitch、所在 blockX/Z、基准方块、期望中心、带符号 deltaX/Z、insideBaseBlock、归位谓词和结果 |
| `RESET_BASE_ENTER / RESET_BASE_EXIT` | 首次观测到跨界时的前后快照、方块坐标、速度和按键；表示客户端观测到的边界，不能推断两次采样之间不可见的轨迹 |
| `RESET_INPUT_SUBMITTED / RESET_INPUT_RELEASE / RESET_WALK_DECISION` | 原始提交/释放请求、前后键状态、提交原因；WALK 决策记录原代码实际使用的 floor(X/Z) 判据、结果及停止/继续走动分支 |
| `RESET_JUMP_ISSUED / RESET_AIRBORNE / RESET_HEIGHT_GATE` | 跳跃提交、首次观测离地、权威 takeoffY、相对高度、原 resetJump.update() 返回的动作及 4 格判据 |
| `RESET_RIGHT_CLICK` | 点击前现场、计划与实际 Tick、方块、水平速度、Pitch、相对高度、槽位与实际 dispatched 结果；保留原 RIGHT_CLICK |
| `RESET_POSITION_CHECK` | 当前/期望 XYZ、各轴误差、各轴 0.01 容差、xzCentered、yMatches、landingObserved、原失败表达式及原错误码 |
| `POSITION_UPDATE` | 原生应用前后 XYZ、当前状态、递增事件顺序及现场；范围扩展至无 trial 的归位期，同次更新仍只发一条事件。仅原有 trial 存在时调用其 response()，不改变正式传送响应计数 |

普通现场的 `returnPredicateUsedForControl=false` 表示只读几何观察，WALK 实际决策事件为 true；不能把两者混为控制指令。现场保留未取整原始浮点值，字段不可读取时为 null/未知或 observationAvailable=false。

**输入阶段与时间对齐**

[VirtualKeyboard.diagnosticState()](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/virtual/VirtualKeyboard.java) 只读本实例虚拟持键集合，并按实际键位绑定查询 GLFW 物理状态；不支持或无法读取的绑定为未知。控制器另记录当前 KeyMapping 状态和本 Tick 计划持键，不能把 KeyMapping 直接视为物理输入来源。

[TransmissionMovementInputMixin](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/TransmissionMovementInputMixin.java) 在本地玩家 `KeyboardInput.tick` RETURN 读取原生 keyPresses 和移动向量，阶段 `KEYBOARD_INPUT_TICK_RETURN`；它不取消输入、不修改玩家或按键。该位置表示原生键盘读取完成，后续自动跳跃/移动处理可能继续改变运动，不能宣称它是最终物理位移。Mixin 仅在当前 RESET 窗口采样，并在客户端 Mixin JSON 中注册一次。

原生读取发生在控制器 END_CLIENT_TICK 递增之前，因此记录 nativeConsumedClientTick = controllerTickAtCallback + 1；END 阶段提交的输入记录 planAppliesFromClientTick = submissionTick + 1。每条保留自身阶段与 Tick，避免把刚提交但未消费的键当作本 Tick 已实际运动的输入。

**文件与写入机制**

沿用当前游戏实例下 `logs/ultimategarden/instant transmission/instant-transmission.jsonl`，使用现有 [TransmissionLog](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/log/TransmissionLog.java)、CollectionSession 和有界 JsonlWriter，不新建文件类别，不覆盖历史记录。保留原 START/END、TRIAL_START、SNAPSHOT、RIGHT_CLICK、RESULT 等结构；新增批次内递增 `eventSequence`、RESET 关联字段。普通记录的 `elapsedMs` 表示写入提交时间；新增现场（包括嵌套前后快照）的 `sampleElapsedMs` 表示实际采样时间，均相对该批次单调起点，不输出新增的绝对单调时钟值。START/END 仍保留原绝对时间戳。

END 增加 `droppedAtClose/writtenAtClose/errorsAtClose/writerErrorAtClose`，它们是关闭请求时读数，非后台排空后的最终计数；后台结束若有丢样/错误，由原 Mod logger 报告最终统计。观测异常不阻断业务输入清理；已接受记录仍按原有关闭/后台排空流程处理。

**验证与复现材料**

JDK 25 的 `gradlew.bat build --offline` 成功。12 组现有回归 instant-transmission、instant-start、context、menu-input、focus、config、gui、hud-adjust、route、shootout、hints、esp 均退出 0。专项为 1,947 项既有传送检查、112 项 RESET JSONL/阶段/精度/边界/写入统计检查，以及 169 项生产控制器启动与归位观测检查（Minecraft、输入与部分日志边界使用 stubs）。

[StartRegression](tests/instant-start/StartRegression.java) 以生产控制器模拟：正式落地 → 走动 → 进入基准方块 → 松键 → 漂出到 Z=12.5529 → 跳跃达到 4 格 → RESET 右键 → 服务端位置应用 Z=12.5 → 落地 XZ 失败/Y 通过。验证窗口外位置更新不漏记/重复、停止后无新增记录、日志捕获开关不改变输入调用序列。额外使用本轮修改前控制器与修改后控制器执行同一场景，完整虚拟输入序列及最终状态一致。证据保存在忽略目录 `.gradle/instant-reset-diagnostics-audit/`。这些检查证明调用顺序及计数保持，不能证明日志零性能开销或真实游戏调度完全无扰动。

Not Verified In-Game：真实 Mixin 加载与原生输入消费、物理按键干扰、惯性、Hypixel 位置修正及日志负载。下次复现请打包完整 `instant-transmission.jsonl`（保留 START 至 END、RESET 与正式试验记录），不要仅截取失败行；若缺 END 或提示写入错误，同时提供对应游戏实例的 `logs/latest.log`。


### 15.11 Instant Transmission 日志实际视角回环

[TransmissionLog.event()/relativeTimes()](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/log/TransmissionLog.java) 在提交 JSONL 的边界递归复制数据，仅对实际视角字段 `yaw`、`pitch` 应用 `wrapLoggedAngle()`：先 `degrees % 360.0`，大于 180 减 360，小于等于 -180 加 360，输出范围为 **(-180°, +180°]**。不四舍五入，不丢弃跨界余量，不钳制 Pitch 到 ±90°；当前日志入口原本亦无此钳制。普通 Tick 快照、RIGHT_CLICK、RESET_TICK，以及归位事件中嵌套的 before/after/site 和列表快照统一经过该边界。

公共 `ViewAngles.wrap()` 的端点为 [-180°, 180°)，与本次日志约定不同，因此不直接复用或改变它；控制器、输入规划、玩家视角、目标 targetYaw/targetPitch、配置/菜单及测试状态流程均保持原实现。归一化只写入复制后的日志数据，不修改调用方快照。

验证：JDK 25 的 `gradlew.bat build --offline` 成功；[TransmissionRegression.angleLogChecks()](tests/instant-transmission/TransmissionRegression.java) 新增 294 项真实 JSONL 写入检查，覆盖正反跨界、±180 端点、多圈累计、小数精度、实际可达 Pitch -90/0/+90、合成越界 Pitch 不钳制、嵌套快照及原数据/目标角度保持。instant-transmission 共 2,241 项加 112 项 RESET 日志检查通过；instant-start 的 169 项生产控制器回归通过。验证输出位于 `.gradle/instant-log-angles-audit/`。未进行新的 SkyBlock 实机采集；本次仅改变日志数值表示，日志目录与批次格式不变。


### 15.12 Instant Transmission RESET 滑行预测与停稳纠偏

[TransmissionCoastPrediction.predict()](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/TransmissionCoastPrediction.java) 为 RESET 专用纯计算：从脚部 X/Z 和直接读取的水平速度（格/Tick）开始，**先检查 hypot(vx,vz) >= 0.003，再累加位置，最后将两个速度分量乘 0.546**，循环至低于阈值。低于阈值时累加 Tick 数 N=0；等价位移系数为 (1-0.546^N)/(1-0.546)。保留两轴符号及 double 精度，不额外预衰减速度，不读取其他方块摩擦参数。

**采样与决策入口**

[InstantTransmissionController.returnWalk()](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/InstantTransmissionController.java) 继续由原 END_CLIENT_TICK 调用，使用当前 Tick 原生玩家运动完成后的 position/getDeltaMovement，在提交下一 Tick 的 W/A/S/D 前预测。已核对当前依赖源码：Fabric 的 MinecraftMixin 在 Minecraft.tick RETURN 发出 END_CLIENT_TICK；该方法内的 level.tickEntities 已先执行。没有新增帧/Tick 驱动、额外位置包或新的输入实现。

WALK 内部增加 ReturnPhase（不另建 Macro 状态机）：

| 内部阶段 | 行为与退出 |
|---|---|
| APPROACH | 预测停点 floor(X/Z) 在基准方块内时立即释放持键，本 Tick 不再按下；沿当前速度方向已越过远侧边界时也释放；预测仍未到达则复用原 W/A/S/D 方向继续走 |
| COAST | 不提交移动，等待 onGround、水平速率 <0.003 且连续两次 Tick 的相邻 XZ 位移 <=0.01；记录实际停点。实际在基准方块内才进入 DOWN_ALIGN；否则用原公共视角链路对齐 0°/0° 后微调 |
| PULSE | 只向实际越出方块的坐标轴短按一 Tick 的对应移动键，下一 Tick 释放并回 COAST。一次短按后必须再次停稳才能决定下一次 |
| WAIT_LANDING | 空中已越出基准方块时取消待归中使用，释放输入，等落地后回 COAST；不在空中补点 AOTE |

纠偏对齐完成时再次读取位置和速度；若已被位置修正带回方块内或尚未停稳，就重新等稳，不盲目发出短按。保留原 WALK 无进展检查与 30 秒超时尺度，并把一次归位的 30 秒截止时间跨微调/转向重试保留，防止反复切阶段刷新等待期限；成功归中或停止时清理相关临时状态。

SETTLE 起跳前复核基准方块、落地和水平速率；未通过则返回归位，不发出跳跃。RESET_JUMP 中若空中方块归属错误，跳过右键，转入落地恢复；仍在基准方块内且达到原相对起跳高度 >=4.0 的门槛才继续原 AOTE 使用。首次 RESET 和组间 RESET 共用这些检查。使用后的 XZ 中心/Y 高度 0.01 校验保持；正式试验 beginTrial()/dispatch()、0/2/4 Tick 使用计划和采样代码未改变。

**沿用日志，新增归位字段**

- `RESET_WALK_DECISION`：改为记录预测停点的实际控制判据、初始水平速度、coastTicks、是否越过远侧及松键/继续走动决定。普通现场的实际 insideBaseBlock 仍为只读观察，不与预测判据混淆。
- `RESET_COAST_DECISION`：松键 decisionTick、初始水平速度、predictedStop、coastTicks、retention、stopSpeed、原因和 microAdjustments。
- `RESET_ACTUAL_STOP`：关联 decisionTick、预测/实测 XZ、actual-predicted 的有符号 predictionError、稳定 Tick 数及微调次数。
- `RESET_MICRO_ADJUST`：每次短按计数、预测停点及一 Tick 脉冲长度。已有逐 Tick RESET 现场额外包含 returnPhase/microAdjustments；保留 resetId、previousTrialId、输入提交/消费和位置更新记录。文件仍是 `logs/ultimategarden/instant transmission/instant-transmission.jsonl`。

**定向验证**

[tests/instant-start/run.ps1 -ReturnOnly](tests/instant-start/run.ps1) 运行 [ReturnRegression](tests/instant-start/ReturnRegression.java) 的 178 项定向检查及既有 [StartRegression.resetTraceChecks()](tests/instant-start/StartRegression.java) 的 93 项归位日志检查。只覆盖本次归位链路，游戏/输入边界使用 stubs。

| 用户提供的实测输入回放（基准 Z=[17,18)） | 计算结果 | 决策 |
|---|---|---|
| Tick 78：Z=18.450080871，vZ=-0.471337605 | N=9，Zstop=17.416369550092842 | 当前仍在块外也立即松键 |
| Tick 79：Z=17.586743253，vZ=-0.471382394 | N=9，Zstop=16.552933703358576 | 已越过远侧，先松键停稳 |

另覆盖两轴合速、零速/阈值边界、几何级数等价性、预测误差落在相邻方块后的多次独立短按、转向期间位置修正、起跳前越界、达到 4 格时越界不右键、恢复后合法使用、微调持键时停止释放及归位超时清理。JDK 25 的 `gradlew.bat build --offline` 成功，定向回归退出 0；证据在忽略目录 `.gradle/instant-return-audit/`。本次是提供数值的本地回放与模拟，未新增 SkyBlock 实机采集；真实地面滑行误差、位置修正及微调收敛效果仍为 Not Verified In-Game。


### 15.13 Instant Transmission 背向中心直线回程

InstantTransmissionController 的正式落地 → alignReturn() → RETURN_ALIGN → WALK → COAST 回程改为：每段从实际脚部到基准方块中心建立固定 Leg，以 ViewAngles.wrap(degrees(atan2(dx,-dz)))、Pitch 0 调用现有公共鼠标对齐；对齐期间释放移动键，普通回程仅持有 S。已在方块内且落地停稳时直接进入原 DOWN_ALIGN。TransmissionCoastPrediction.leg() 以水平单位方向与基准方块 XZ 矩形求入口/出口距离；预测停点沿该方向的投影达到远侧出口才算越过整块，跨过单轴中心不再构成 PREDICTED_OVERSHOT。

保持原 END_CLIENT_TICK 滑行公式与停稳条件。实际停稳在方块外时，gap() 计算到矩形边界的距离；gap>=0.5 重开正常回程，gap<0.5 才用重新对齐后的单 Tick S 脉冲，随后释放、预测滑行、停稳复核。原起跳 4 格、归中右键、落地中心/高度校验及正式采集不变。沿用归位日志，新增 RESET_RETURN_LEG 的起点、单位方向、入口/出口距离、gap 和 micro 分类。

定向验证：双轴样例 (-18.5,16.5) → (-17.5,4.5) 的 Yaw=+4.763641690726178°，S 方向为 +X/-Z；覆盖单轴跨中心仍未越过出口、整块越过、停稳在块内、gap<0.5、gap=0.5 和 gap>0.5。JDK 25 build --offline 与 tests/instant-start/run.ps1 -ReturnOnly 通过（204 项定向检查、93 项归位日志检查）。测试驱动仅在 stubs 中模拟公共鼠标完成对齐后读回的新回程角度，未直接设置生产玩家视角。真实鼠标对齐、斜向 S 行走及滑行/短脉冲收敛仍为 Not Verified In-Game。输出见 .gradle/instant-return-audit/backwards-build.txt 与 backwards-test.txt。


## 16. Instant Transmission 公共模型、规划与调试迁移（2026-11-16）

### 16.1 基线与保存记录

旧调试分支 `test/Instant-Transmission-dataCollect` 原 HEAD 为 `6be9b78ff9d7b443cf583d7daca8fd3f1674a063`。核对工作区改动属于 Instant Transmission 及必要的输入所有权、配置、HUD/测试接线后，完整保存在本地分支 **test/Instant-Transmission**，提交 **4c85591f40d52d8ea9f2dfe306edc9924e19e586**。此后切回 master，以同一 `6be9b78` 为基线按文件差异迁移；master 的 HEAD 未移动，本节正式实现尚未提交，未推送。前述第 15 章为调试演进记录，路径已同步至当前实现；行为调整的时间顺序仍保留。

### 16.2 当前目录与依赖

```text
client/function/instant_transmission/
├── flight/
│   ├── Point.java
│   ├── Capability.java
│   ├── FlightResult.java
│   └── FlightModel.java
├── routing/
│   ├── RouteStrategy.java
│   ├── RouteResult.java
│   ├── PlanningStrategy.java
│   ├── RoutePlanner.java
│   ├── Terrain.java
│   ├── ClientTerrain.java
│   └── strategy/
│       ├── DirectAimStrategy.java
│       ├── DropAlignStrategy.java
│       ├── UpperHemisphereStrategy.java
│       └── FixedRouteSearch.java
└── debug/
    ├── InstantTransmissionController.java
    ├── DebugTerrain.java
    ├── TransmissionTrial.java
    ├── TransmissionResetJump.java
    ├── TransmissionCoastPrediction.java
    ├── TransmissionInputCheck.java
    ├── TransmissionHudSnapshot.java
    └── log/
        ├── TransmissionLog.java
        └── TransmissionResetDiagnostics.java
```

公共数值入口为 [FlightModel.displacement/predict/solve/candidates](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/flight/FlightModel.java)，公共规划入口为 [RoutePlanner.plan](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/routing/RoutePlanner.java)。flight 不访问游戏；三个 strategy 只依赖 flight 和 Terrain 抽象；ClientTerrain 为可选客户端适配器。各调用持有独立的能力参数、结果和有限候选，不建立全局世界缓存或自动执行任务。debug 调用这些入口，拥有自己的输入、试验、采样与日志生命周期。

### 16.3 飞行模型与状态

- 单位：Point 为脚部世界坐标（block）；Yaw/Pitch 为度；Capability.distance 为名义格数，maxClicks 为硬上限，budget 为有限候选生成/比较预算，tolerance 为三维命中误差容差。Capability.standard() 使用距离 12、最多 32 次、预算 200000、误差 0.001，调用方可覆盖。
- 固定角度单次位移按方向 (-cos(p)sin(y), -sin(p), cos(p)cos(y)) 计算，水平逐轴 floor(projected+0.5)。12 格向上 5° 网格优先使用用户提供的 19 项 dy 表；其他向上 12 格角度使用 floor(-12sin(p)+0.62)。向下或其他距离采用基础投影最近整数估计，不把 0.62 用于向下。整数向量不归一化为 12 格。
- predict() 对一组固定角度直接叠加 D，返回逐次点、末点、实际三维误差，以及 ALREADY_REACHED/HIT_TARGET/APPROXIMATE/OUT_OF_MODEL/UNSUPPORTED 等结果。落在同一方块不抹去坐标误差。传送后的下落不属于 flight。
- MEASURED 仅标记中心 XZ 起步、12 格、Yaw 0–45°、向上已测 5° 网格及其已知方向；其他输入标为 ESTIMATED；超过 3 次额外标记 EXTRAPOLATED。非中心起点按相对离散位移估计，未宣称服务器会如何吸附到方块。
- solve()/candidates() 优先加入几何直瞄，按离散位移去重；有限 Pitch 候选包含 1° 网格、实测角度及拟合边界，Yaw 包含量化边界和区间中点。按目标距离、步长及 maxClicks 限制点击比较。精确误差优先，其后误差/点击数，遍历顺序稳定。结果明确为“已检查候选中的最优”，不是连续角域的全局完备证明；候选生成或比较截断均通过 budgetExhausted 暴露。确定超过保守能力上界标 OUT_OF_MODEL。

### 16.4 三种路线与地形

| 策略 | 选择与结果 |
|---|---|
| DROP_ALIGN / 高位对齐降落 | 固定角度候选末点必须处在目标 XZ 方块列且 Y 在目标站立高度至 +12（含两端）；优先直接落到该层，再按较低抬升/较少点击选择。输出 lastTransmission 与独立 landing；落地误差按实际 XZ 保留。检查传送净空、竖直下降通道和支撑，中途平台判阻挡 |
| UPPER_HEMISPHERE_NEAREST / 上半球最近点 | 末点三维距离 <=6 且 Y>=目标；包含零次候选。比较全部预算内合格候选，按误差/点击数选择，不在首次入球时结束 |
| DIRECT_AIM / 直瞄传送 | 默认策略，当前点已在 6 格内则零次；否则逐段几何直瞄、预测一次，再由预测点继续，首次进入 6 格即停止。检测零位移、重复点、已知阻挡和上限，不搜索其他角度。实际执行调用方可取首段后从真实位置重规划 |

[Terrain](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/routing/Terrain.java) 明确 CLEAR/BLOCKED/UNKNOWN；无地形时可离线规划，但返回 GEOMETRIC_UNKNOWN，不能声称可执行成功。上半球/直瞄未掌握自然下落支撑时 landing 为 null，不虚构落地点。有限候选无结果的原因写明 NO_CHECKED_DIRECT_CANDIDATE，预算截断与普通候选失败区分。

[ClientTerrain](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/routing/ClientTerrain.java) 只在客户端线程使用当前 ClientLevel，复用 LoadedWorldView 已加载查询保护，按 0.6×1.8 玩家净空与碰撞形状 AABB 筛选，路径采样间隔至多 0.25 格，路径/格点预算固定有界；未加载、邻居查询越界或预算不足为 UNKNOWN，不当作空气。下降路径撞到中间平台或缺少目标支撑为 BLOCKED。CLEAR 仅表示客户端几何检查通过，服务器遇墙的拒绝/截短/吸附仍未实测。DebugTerrain.current() 每次从当前世界创建适配器，不持有跨世界缓存。

### 16.5 调试、菜单、HUD、日志迁移对应

| 原调试职责 | 当前入口/处理 |
|---|---|
| 角度扫描、1/2/3 点击组、0/2/4 Tick 调度、实际事件 | [debug/InstantTransmissionController](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/InstantTransmissionController.java)、TransmissionTrial；保留原状态机与两个 Tick 预采样 |
| 回程背向中心 S、滑行预测、起跳 4 格、归中 | debug/TransmissionCoastPrediction、TransmissionResetJump 及控制器原方法，未并入路线搜索或改变执行角度 |
| 实际逐包位置更新/输入消费 | 原 TransmissionPositionMixin/TransmissionMovementInputMixin 保持包名，仅导入新 debug 控制器；继续在原生实际应用/读取位置观察 |
| 预测与路线记录 | beginTrial() 增加 recordPrediction()，用同一次计划角度/次数调用公共模型，并调用所选 RoutePlanner；新增 PREDICTION。flight/route 与 RIGHT_CLICK、POSITION_UPDATE、RESULT 的观测值分开。预测不可用记录 PREDICTION_UNAVAILABLE，不取消原试验 |
| 原始与归位日志 | debug/log 的业务适配器调用原 platform/persistence/log/JsonlWriter、CollectionSession；实际文件仍为 logs/ultimategarden/instant transmission/instant-transmission.jsonl，保留 START/END、精度、顺序、丢样与排空 |
| 菜单和热键 | 原 Settings → Developer Options → Instant Transmission、MacroKeyBindings 的 Toggle Instant Transmission Test（None）保留，同一 toggle/start/stop |
| HUD | 原 InstantTransmissionHud/TransmissionHudContent 仍在 platform/ui/adapters/instant_transmission，消费只读 TransmissionHudSnapshot，不推进业务 |

HUD 直接参考 [MacroStatusHud](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/MacroStatusHud.java) 与 [ZombieShootoutHud](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/hud/ZombieShootoutHud.java)，继续使用 Ultimate_GardenClient → UiRegistry.hud 注册、HudPanel 绘制、HudAdjustmentController 编辑与 HudConfig.HudId。原 Instant Transmission 标题、阶段、进度、角度、高度、预采样、点击/位置更新计数和最终原因均保留。Settings → HUD 的独立 showInstantTransmissionHud 默认 true，关闭后编辑预览仍可用；HUD 启用不启动测试。

[InstantTransmissionConfig.strategy](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/InstantTransmissionConfig.java) 持久化至原账号 general.json 的 instantTransmission.strategy；默认 DIRECT_AIM，缺失沿用默认，非法值校验修复；原六个角度字段、distanceBonusNote 及旧默认迁移仍保留。Developer Options 原功能折叠组增加单选 dropdown，使用稳定枚举值及中英名称，运行时不可更改本轮参数。HUD 配置仍由 hud.json 管理，不与策略共享开关或创建新偏好文件。

### 16.6 验证和限制

- JDK 25 `gradlew.bat build --offline` 成功。flight 与 routing 的纯类（不含可选 ClientTerrain）还以纯 JDK、无 Minecraft/Fabric/Gson classpath 独立编译执行 [FlightApiSmoke](tests/instant-transmission/FlightApiSmoke.java) 成功。
- [FlightRoutingRegression](tests/instant-transmission/FlightRoutingRegression.java) 67 项：四个给定向量、完整 dy 表、3 次固定角度点列、非归一化实际向量、中心/非中心及外推置信、精确坐标误差、策略持久化、+12/-上限、平台拒绝、半径 6 边界/下半球排除/全候选最近点、直瞄入球停止、零位移、重复点、上限、未知地形及预算。
- instant-transmission 原 2241 项与 RESET JSONL 112 项通过；instant-start 186 项，回程 204 项与日志追踪 93 项通过。hud-adjust、gui、config、menu-input、focus 相关脚本均退出 0。HUD 的 135 项生产编辑检查与 27 项生产渲染检查包括开关即时生效、隐藏时预览、重载、停止后最终结果；图形与游戏边界使用 stubs。账号测试中的符号链接用例因当前环境创建能力不可用而跳过，并非迁移失败。
- 保存提交的 debug 控制器经包名归一化、移除新增预测 hook 后与当前源码逐字一致，证明原调试控制流程未被路线模型替换。生产接线旧包/路径在 src/tests 的 Java/PowerShell/Python 中已无残留。结果在忽略目录 .gradle/instant-production-audit/。
- Not Verified In-Game：菜单/HUD 实际视觉、原生鼠标/按键与 Mixin 的完整运行、AOTE 位移/墙体响应、真实地形平台净空、非中心吸附和超出采样范围的预测。其他 Yaw、未测 Pitch、向下、不同能力距离及 >3 次均按上述估计/外推标记；没有追加点击速度实验或网络探测。


## 17. 四个 HUD 的独立显示配置与 Mapping 世界信息（2026-11-16）

### 菜单和配置

[GardenMenuAdapter.HudPageAdapter.hudSettings()](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/menu/GardenMenuAdapter.java) 在 Settings → HUD 保留全局 Adjust HUD Position & Size，然后按 HudId 建立 Farm Macro HUD、Zombie Shootout HUD、Instant Transmission HUD、Mapping HUD 四个独立折叠子节。每节依次为 Show、Only Show While Running、Display Mode、Compact Mode Content、Detail Mode Additional Content。内容折叠使用包含 HUD ID 的唯一键，避免四节共用展开状态；Mapping 内容设置下仅有“世界信息”分组。Only Show While Running 仅在自身 Show 开启时可编辑，禁用不清除原值。

[HudConfig](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/display/HudConfig.java) 保留 Farm 的原 displayMode、compact、detail、bounds 和开关字段；新增 panels.ZOMBIE_SHOOTOUT / INSTANT_TRANSMISSION / MAPPING 保存各自模式及内容选择，Mapping 的 show 和 Instant/Mapping 的 onlyRunning 也在对应 panel 中。Farm/Shootout 的真实运行态和 Only 设置沿用原定义。Mapping 新增独立 mappingBounds，加入同一个 HudId/编辑器机制，没有新增布局系统或偏好文件。

缺失新字段时，Zombie Shootout 与 Instant Transmission 的 compact 默认包含原来实际渲染的全部字段，附加字段为空，保持原显示。Instant 的 Only Show While Running 默认 false；Mapping Show 默认 false、Only 默认 false，启用后可在待机查看信息。内容选择由 fields(id)、mode(id)、content()/visible(id,key) 统一查询；详细模式为简洁与额外字段的并集，同一行只绘制一次。新字段仍由现有账号 hud.json 校验及原子保存路径管理；不保存世界观测数据。

### 渲染入口

- [ZombieShootoutHud](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/zombie_shoot/ZombieShootoutHud.java) 给现有行附加稳定字段 ID，并按自身配置筛选；状态标题行也可隐藏，不影响控制器。
- [InstantTransmissionHud](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/instant_transmission/InstantTransmissionHud.java) 从当前只读快照 running() 判定显隐，停止和待机在 Only=true 时隐藏，Only=false 时保留原最终结果展示。[TransmissionHudContent.rows()](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/instant_transmission/TransmissionHudContent.java) 增加字段选择器，阶段相关行仍只在原阶段出现。
- [MappingHud](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/mapping/MappingHud.java) 在 Ultimate_GardenClient 中通过原 UiRegistry.hud 注册，沿用 HudPanel、字体、主题和拖动/缩放预览。唯一内容分组为世界信息，六个字段各有开关；未知值使用双语“未知/Unknown”。taskRunning() 当前明确返回 false，是未来任务状态的绑定点；没有创建 Mapping 任务、扫描器或功能页。
- 编辑模式绕过各 HUD 的 Show/Only 限制以预览，不改写开关；退出后恢复实际显示规则。布局以各自归一化 bounds 保存，互不改变。

### 世界上下文来源与失效

[MappingWorldContext](src/client/java/com/kuangsanfeng/ultimategarden/client/function/world/WorldInfoService.java) 仅在 END_CLIENT_TICK 复制现有服务器地址和维度到只读标量快照；利用 ClientContextEvents 的世界/账号变化、断线及退出通知清空。[MappingContextState](src/client/java/com/kuangsanfeng/ultimategarden/client/function/world/WorldInfoState.java) 以世界与连接对象身份保护快照读取，因此新世界/连接尚未完成一次 Tick 更新时也不会返回旧值。

当前源码没有可复用的 Hypixel 游戏类型、岛屿/大区、子区域或服务器实例 ID 来源，这四项保持未知；不把原版生存/创造模式当作 Hypixel 游戏类型，不解析聊天，不发命令或网络请求。实例标识未被伪造或缓存。HUD 渲染只消费快照，不执行识别/扫描；本次没有地图文件读写、边界编辑、3D 预览或路线规划接入。

### 验证

JDK 25 的 build --offline 成功；hud-adjust、config、gui 回归通过，Instant Transmission 原有相关回归也通过。新增 [HudPanelsRegression](tests/hud-adjust/HudPanelsRegression.java) 666 项覆盖各面板字段/模式隔离、双语字段目录、生产 Zombie Shootout/Instant/Mapping 渲染筛选、内容并集去重、运行/停止显隐、Mapping 隐藏时预览、独立拖动/缩放和重载、世界/连接身份变化与清空。另有 137 项生产编辑器检查及 27 项原 Instant HUD 渲染检查通过；游戏、输入和绘图边界使用 stubs。菜单构建代码核对四节都独立拥有显示/内容控件，全局编辑入口保留；原 SectionLayout 回归覆盖嵌套折叠。

结果在 .gradle/hud-panels-audit/。Not Verified In-Game：四个折叠子节的实际视觉、原生游戏渲染与拖动手感、真实联机切世界/实例时序。当前未实现的 Hypixel 字段仅验证未知回退，未声称已取得服务器位置数据。当前分支及已有其他功能改动保留，未执行提交或推送。


## 18. Mapping HUD 官方位置事件与六项世界信息（2026-11-16）

本节补全第 17 章的未知字段；HUD 排版、字段筛选、Only Show While Running 与布局配置不变。只扩展现有世界上下文，不增加地图扫描、地图文件或功能页。

### 官方依赖与接线

build.gradle 使用非重映射 Loom 的标准 implementation 引入 `maven.modrinth:1A2mKfBx:SlqCF6Or`，仓库为 https://api.modrinth.com/maven 。已检查下载的官方 JAR 元数据：版本 1.0.2+build.1+mc26.1，mod ID 为 hypixel-mod-api，Minecraft 依赖 >=26.1，内嵌 mod-api-1.0.2.jar。Modrinth POM 不暴露内嵌核心类给 javac，因此另以 compileOnly 从官方 Hypixel Maven 暴露 net.hypixel:mod-api:1.0.2，不在运行时重复装载核心库。fabric.mod.json 增加 hypixel-mod-api >=1.0.2 必需依赖；发行运行环境仍须安装兼容的官方 Fabric Mod，不能仅安装 Java 核心库。

[MappingWorldContext.register()](src/client/java/com/kuangsanfeng/ultimategarden/client/function/world/WorldInfoService.java) 保留原客户端初始化入口，一次性调用 HypixelModAPI.createHandler(ClientboundLocationPacket.class, ...) 和 subscribeToEventPacket(...)。连接确认使用官方 FabricModAPI 实例的 isConnectedToHypixel() 状态，不凭 sidebar 的 SKYBLOCK 文本单独认定其他服务器是 Hypixel。只订阅官方位置事件，不定时发送 /locraw 或探测命令。

处理器复制 serverName、serverType.name()、mode、map，再经 Minecraft.execute() 回到客户端线程，由 [MappingContextState.acceptLocation()](src/client/java/com/kuangsanfeng/ultimategarden/client/function/world/WorldInfoState.java) 校验捕获的连接对象及回调代际；断线/账号清理会递增代际，排队期间已经换连接或失效的旧回调不再更新当前数据。

### 六项数据及回退

| HUD 字段 | 当前实际来源与回退 |
|---|---|
| 服务器地址 | Minecraft.getCurrentServer().ip；不是实例名 |
| 游戏类型 | 官方 getServerType().map(ServerType::name) 优先；仅官方连接状态已确认 Hypixel 时，当前可见 sidebar 标题清洗后完全匹配 SKYBLOCK / SKYBLOCK CO-OP / SKYBLOCK GUEST，才回退 SKYBLOCK |
| 岛屿/大区 | 仅 SKYBLOCK：getListedOnlinePlayers() → gui.getTabList().getNameForDisplay(info).getString() → 完整 Area: 行；缺失时用官方 mode，已明确的 hub → Hub、dynamic → Private Island，其他 ID 原样显示 |
| 子区域 | 仅 SKYBLOCK：实际 sidebar 的可见行中，以 U+23E3 或 U+0444 开头的非空文字；不把 map 当子区域。每 Tick 刷新，同行更新无需新的位置事件 |
| Minecraft 维度 | level.dimension().identifier().toString()，非 Hypixel 也保留 |
| 服务器实例 ID | 官方 getServerName() 原样优先；缺失且 SKYBLOCK 时，再取 Tab Server: mini/mega 名，最后尝试 sidebar 日期行 ^数字/数字/数字 + m/M编号，分别生成 mini/mega，保留编号字母 |

sidebar 选择与本地 26.1.2 原版 Gui 源码一致：先按玩家所属队伍颜色查询 DisplaySlot.teamColorToSlot，无槽位/目标时回退 SIDEBAR。listPlayerScores() 跳过隐藏行，按分值降序、owner 大小写不敏感排序并限前 15 行，重建 PlayerTeam.formatNameForTeam(..., entry.ownerName()) 文本。**当前映射必须用 scoreboard.getPlayersTeam(entry.owner()) 按玩家名查询所属队伍；getPlayerTeam(name) 是按队伍名查询，不能替代。**

[MappingLocationText](src/client/java/com/kuangsanfeng/ultimategarden/client/function/world/WorldInfoText.java) 集中清洗残留 § 格式码、Unicode 空白，保留区域符号和其他 Unicode，执行精确匹配。未匹配、空字段及非 SkyBlock 区域均为 null，由原 HUD 显示“未知”。map 与原始 mode 只保留在内部不可变快照，不新增 HUD 行或日志文件。

### 生命周期与发布

END_CLIENT_TICK 读取当前 Tab/sidebar、地址与维度，构建不可变 Snapshot；只有八项标量（含内部 mode/map）实际改变才替换快照。渲染阶段仍只调用 guarded snapshot，不解析聊天、读取记分板或发请求。

同实例持续更新 Tab/sidebar；新的实例、类型或 mode 事件替换整份官方 Location，并立即清除旧子区域。保留上一帧文本作为隔离基线：旧文本未刷新时不将它填回；若能从 Tab/日期行读出实例名，还要求与官方实例一致。Tab 不可用时允许用新事件的 mode 回退。世界变化立即使旧世界快照不可读并隔离旧文本；同一连接的最新官方事件保留，避免新事件先于世界对象替换时丢失。断线、账号变化、退出或非 Hypixel 会话清除官方与文本字段；非 Hypixel 仍可显示地址和维度。所有状态有界，不持久化位置数据。

### 验证与实机边界

JDK 25 的 build --offline 通过；hud-adjust 回归通过，包括原 137 项布局、27 项 Instant HUD、666 项面板检查。新增 [MappingLocationRegression](tests/hud-adjust/MappingLocationRegression.java) 的 55 项覆盖颜色/Unicode、三个精确标题、Area/模式回退、官方类型与实例优先、m/M 日期规则、Hub/Village → 同 Hub 子区域 → Garden、回大厅清空、空 map 替换、快照去重、换世界文本隔离、断线及旧连接/旧代际拒收。原生 sidebar 的队伍选择/排序/显示名拼接已核对当前源 JAR，并通过实际编译；测试不等于已登录 Hypixel 接收官方事件。

结果在 .gradle/mapping-location-audit/。Not Verified In-Game：官方 Fabric Mod 在实际游戏会话中的事件到达顺序、真实 Tab/记分板更新和跨实例过渡。尚待验收：在 SkyBlock 观察 Hub/Village，走到同实例其他子区域；转 Garden；回大厅和断线。预期区域随可见数据更新、实例始终优先使用官方 serverName，过渡期间宁可未知也不恢复旧子区域。未改 HUD 外观/配置或接入后续 Mapping 功能。


## 19. Mapping 类型与实例只取官方事件（2026-11-16）

本节替代第 18 章的游戏类型、实例 ID 文本回退规则。既有官方 Fabric 依赖 `maven.modrinth:1A2mKfBx:SlqCF6Or` 与 hypixel-mod-api 运行依赖保留，不另写网络实现。

[MappingWorldContext.register()](src/client/java/com/kuangsanfeng/ultimategarden/client/function/world/WorldInfoService.java) 继续在客户端初始化时仅注册一次 ClientboundLocationPacket handler 和订阅；HUD Show 不参与订阅生命周期。重连由官方 API 的 Hello 机制恢复订阅，本模块不重复注册。移除对 FabricModAPI 实现实例的入口查找，当前连接收到官方 Location 才开始读取相关 Tab/sidebar 文本；类型直接使用 getServerType().map(ServerType::name)，实例直接使用 getServerName()。等待事件或字段为空时保持 null，由 HUD 显示未知。

[MappingLocationText](src/client/java/com/kuangsanfeng/ultimategarden/client/function/world/WorldInfoText.java) 删除 SKYBLOCK 标题识别、Server: 行及 m/M 日期实例解析；不再读取 sidebar 标题用于推断。Tab Area 优先、官方 mode 缺省显示，以及 U+23E3/U+0444 子区域提取保留；同实例仍逐 Tick 更新。地址、维度、HUD 排版/内容开关与运行态配置未变，没有 /locraw 轮询。

[MappingContextState](src/client/java/com/kuangsanfeng/ultimategarden/client/function/world/WorldInfoState.java) 收到新实例时立即清除旧大区和子区域，隔离旧文本至内容刷新；Tab Area 缺失仍可使用当前官方事件 mode。官方类型缺失不会从 mode 或可见标题反推 SKYBLOCK；官方实例名缺失不会从 Tab/sidebar 补值。保留客户端线程发布、值未变不换快照、连接身份/代际检查和断线清理。内部 map 继续只作诊断数据。

JDK 25 build --offline 成功；hud-adjust 通过原 137 项布局、27 项 Instant HUD、666 项面板测试，以及重写后的 51 项官方来源专用检查。覆盖未收位置包、缺类型、缺实例名、误导性文本、Hub/子区域/Garden 更新、旧文本隔离、断线和迟到回调；文本不能填补官方缺失的类型/实例。证据位于 .gradle/mapping-official-only-audit/。Not Verified In-Game：实际 Hypixel 位置事件、重连 Hello 与 Tab/sidebar 更新时序。


## 20. 公共世界信息模块 function/world（2026-11-16）

### 公共读取入口与职责

原 MappingWorldContext、MappingContextState、MappingLocationText 的识别职责从 platform/context 迁入以下包，不保留第二套接线。第 17–19 章为历史演进说明，对应链接已指向当前实现。

```text
client/function/world/
├── WorldInfoService.java   原生数据采集、官方事件与生命周期装配
├── WorldInfoSnapshot.java  不可变的当前世界信息
├── WorldInfoState.java     合并、连接归属、文本失效与去重发布
└── WorldInfoText.java      格式清洗、Area/子区域提取和 mode 显示名
```

其他客户端功能统一调用 [WorldInfoService.snapshot()](src/client/java/com/kuangsanfeng/ultimategarden/client/function/world/WorldInfoService.java)，返回 [WorldInfoSnapshot](src/client/java/com/kuangsanfeng/ultimategarden/client/function/world/WorldInfoSnapshot.java)。该调用仅返回一个 volatile 发布的不可变记录；不访问 Minecraft，不采样、不解析、不发请求。全局可变状态私有，外部没有修改当前快照的接口。世界模块不依赖项目 HUD、菜单、偏好或绘图类。

Snapshot 字段：

| 字段 | 来源 |
|---|---|
| server、dimension | 当前原版服务器地址与 Minecraft 维度 |
| gameType、instance | 官方 Location 的 serverType.name()、serverName；不通过文本推断 |
| mode、map、lobbyName | 官方 Location 的 mode/map/lobbyName 原始可空值 |
| environment | 官方 Hello.getEnvironment().name()，如 PRODUCTION/BETA/TEST |
| island | 新鲜 Tab Area 优先，其次当前官方 mode 的既有显示名/原始 ID |
| subArea | SkyBlock 可见 sidebar 的 U+23E3/U+0444 行，Tick 持续刷新 |

缺失值仍为 null。新快照字段没有增加 HUD 行、菜单或配置键；MappingHud 继续用六个原字段键显示未知。

### 装配、连接身份与清理

Ultimate_GardenClient 原初始化位置改为调用 WorldInfoService.register()。保留单次 Location handler/订阅，新增单次 Hello handler；官方 API 自行根据重连 Hello 恢复位置事件订阅。HUD 开关不参与任何采集或订阅条件。

已核对 26.1.2 原版：Minecraft.getConnection() 在 player 未建立时为空，Hello 可以在 CONFIGURATION 阶段到达。因此通过 [WorldInfoConnectionAccessor](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/WorldInfoConnectionAccessor.java) 只读取得 ClientCommonPacketListenerImpl.connection，利用现有 Fabric CONFIGURATION/PLAY INIT 事件绑定同一底层 Connection。该访问器不改输入或网络行为。CONFIGURATION、PLAY 监听器替换而底层连接未变时，保留已确认的 environment。

两个官方处理器复制包字段并通过 Minecraft.execute 在客户端线程合并；底层连接身份、有效连接状态和 generation 共同拒绝旧回调。Location 在同一连接内替换完整位置字段（包括空 lobby/map），不清除环境；实例切换立即使旧区域文本失效，等待新 Tab/sidebar 刷新。世界对象切换期间保留当前连接的最新官方事件，避免事件先于世界替换时丢失数据；旧世界显示快照及旧文本失效。真实断线（含 CONFIGURATION 断线）、新底层连接或单人世界会清除旧 Hypixel 信息。客户端退出统一释放当前状态。

采集仍为 END_CLIENT_TICK。CONFIGURATION 中尚无世界时只失效世界显示，保留连接级 Hello；PLAY 有世界后合并地址、维度和文本。sidebar 的队伍颜色选择、隐藏行排除、排序、15 行限制与原版显示名格式化继续使用上一版实现；没有新增聊天解析、/locraw、地图扫描、文件或规划入口。

### Mapping HUD 接线与验证

[MappingHud](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/mapping/MappingHud.java) 仅将旧读取入口替换为 WorldInfoService.snapshot()，继续负责字段选择、Unknown 文本、面板绘制与布局。初始化注册独立于 Show/Only 设置，所以所有 HUD 关闭时其他功能仍可消费最新世界状态。

JDK 25 的 build --offline 成功。hud-adjust 脚本通过 137 项布局、27 项 Instant HUD、666 项面板/Mapping 渲染和 76 项世界来源/生命周期定向检查。世界检查包含 Hub 内子区域更新且实例不变、Hub→Garden、环境跨实例/世界保留、lobby/map 空值替换、旧 Hello/Location 回调拒绝、单人/连接清理、快照只读去重，以及 HUD 禁用不影响状态更新。源码核对 function/world 无项目 UI/配置依赖，snapshot() 体为直接读取；旧生产包与测试引用已迁移。测试证据在 .gradle/world-info-audit/。

Not Verified In-Game：新只读连接 Mixin 的运行时加载、真实 Hello 在 CONFIGURATION/PLAY 的时序、实际 Hypixel 切实例/重连及 Tab/sidebar 刷新。自动检查使用真实纯状态实现与游戏/绘图 stubs，不能替代线上验收。本次未提交、推送或切换分支。

## 21. Developer Options → Mapping 区块记录与本地地图（本次新增）

### 模块、装配与只读来源

- [MappingController](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/MappingController.java) 的 `register()` 由 `Ultimate_GardenClient.onInitializeClient()` 在现有 `WorldInfoService.register()` 后调用一次。使用项目当前 Fabric API **0.155.2+26.1.2** 的 `ClientChunkEvents.CHUNK_LOAD / CHUNK_UNLOAD` 和 `ClientTickEvents.END_CLIENT_TICK`；未升级依赖，也未引入 Forge/NeoForge。
- Mapping 只消费 `WorldInfoService.snapshot().island()` 的已解析大区名称。目录由 [MappingNames.key](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/MappingNames.java) 规范化：`Hub → hub`。Hub 的不同大厅、重新连接和 Village 等子区域均复用同一地图；子区域、实例、服务器地址、模式及整份世界快照不进入 Mapping 持久化。
- 区块加载事件从客户端初始化起持续维护当前缓存的坐标集合，即使记录关闭也保留已加载范围。开始时优先玩家区块，之后 [MappingCandidates.next](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/MappingCandidates.java) 仅从该集合选择未保存、未等待写入、未被本轮删除抑制的候选。每 Tick 最多捕获一个完整区块，最多两个待保存快照。
- `readChunk()` 按本地 Loom 26.1.2 原版源码使用 `ClientChunkCache.getChunk(x,z,ChunkStatus.FULL,false)`，跳过 null / `EmptyLevelChunk`；扫描高度取地图配置。仅在客户端线程读取方块注册名与完整 Properties，复制成不可变 [ChunkSnapshot](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/ChunkSnapshot.java)。Mapping 没有网络发送、命令、视距修改、移动或强制加载入口。

### 存储、坐标与 .litematic

- [MapConfig](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/MapConfig.java) 字段仅为 `schemaVersion=1`、`worldName`、`origin{x,y,z}`、`maxY`（不含）、`festivals` 和 `selectedFestival`。世界身份只有 `worldName`，不另存稳定键或实时上下文。
- 根目录是 `Minecraft.gameDirectory/config/ultimategarden/map/`；例如 `config/ultimategarden/map/hub/map.json`、`Hub 0 0.litematic`、`Spooky Festival/Hub 1 -1.litematic`。初始化原点为玩家当前区块最小 X/Z 和当前维度 `getMinY()`；上界为 `getMinY()+getHeight()`。所有层共享唯一原点和高度范围。
- `MapConfig.relativeX/relativeZ` 使用绝对区块坐标减原点区块坐标，负数按 `Math.floorDiv` 处理。文件局部 `(x,y,z)` 只进行一次换算：`world=(origin.x+16*i+x, origin.y+y, origin.z+16*j+z)`；NBT 不另存绝对位置。
- [LitematicIO](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/LitematicIO.java) 按目标 Litematica **0.27.14** 实际 JAR 写 `Version=7 / SubVersion=1`，数据版本来自运行时 `SharedConstants.getCurrentVersion().dataVersion().version()`。使用原版 `NbtIo.writeCompressed/readCompressed` 的 Gzip Java NBT；一份文件一个 `Chunk` 区域，Position=(0,0,0)，Size=(16,H,16)。
- 调色板空气索引为 0，保留注册名及 Properties。索引 `((y*16)+z)*16+x`，X 最快；位宽 `max(2,ceil(log2(paletteSize)))`，连续低位优先、允许跨 long，长度 `ceil(count*bits/64)`。读取验证尺寸、数组长度和索引，并保留调色板原顺序；缺文件用 `Optional.empty()` 表示未知，与真实空气区分。
- Metadata 的时间标签固定为 0，不写实际日期；不记录实体、方块实体内容或待执行 Tick。压缩先写完整临时文件，再替换目标。配置损坏、缺失配置却已有区块、名称冲突、路径越界或符号链接均拒绝静默重新初始化。

### 会话、节日层与管理操作

- 单个有界 IO worker（队列 8）负责文件操作；主线程只提交不可变快照。写入绑定仓库、配置、所选层、会话 epoch 和区块 revision；最终替换/移除覆盖文件时在提交锁内复核。停止、世界对象/连接/大区变化、换层、删除、重设原点会使旧任务失效。卸载或同坐标重新加载递增 revision；旧数据不能提交成新加载区块。
- [MapRepository](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/MapRepository.java) 的 `initialize()` 先在临时目录保存首个 None 区块和有效 map.json，再发布目录。重复初始化有效地图不写盘，不改变原点或状态。重绘允许记录停止时使用。
- [Festival](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/Festival.java) 集中定义稳定枚举 ID 和不随语言变化的目录名：None、Spooky Festival、Season of Jerry、Season of Jerry&New Year Celebration、Harvest Feast、Years of the Seal/Witch/Pig。
- **当前自动候选明确为 None**：现有 world 服务无经过验证的“正在进行的节日”数据源，官方 Hypixel Mod API 1.0.2 位置事件也无节日字段。本次未添加日历解析、查询、轮询或自动打开页面；未凭活动名字推断组合层。将来有明确的被动活动来源后，才可添加兼容别名的预选适配。
- `chooseFestival()` 选择已创建层时停止旧会话并持久化选择；未创建层仅作菜单候选，必须点击 `createFestival()`。同世界同节日只创建一次，空层也写入配置。初始化始终 None。
- `MapRepository.write()` 对节日快照逐格比较实际状态和属性，不比较调色板编号或压缩字节；不同写完整覆盖，相同重绘移除旧覆盖，无基础块时报 `BASE_CHUNK_REQUIRED`。公共磁盘读取 `read(config,layer,cx,cz)` 优先覆盖再回退 None，两者缺失返回未知。
- `resetOrigin()` 停止记录，对基础和所有节日层按 `i'=i+oldChunkX-newChunkX`、`j'=j+oldChunkZ-newChunkZ` 重新编号；先把所有旧文件移入事务临时位置，防止文件名交换覆盖，最后提交新原点。取消/失败尝试恢复所有旧文件名和旧配置；回滚失败保留事务标记，后续打开会明确拒绝。
- `deleteCurrent()` 仅删当前层当前块；删除 None 不影响节日文件，删除 (0,0) 不删除 map.json。取消旧写入，在当前连续会话抑制自动补回；显式重绘或下次启动解除抑制。`deleteWorld()` 只删除经安全解析的当前世界 Mapping 目录，保留其他世界和 Minecraft 存档。

### 菜单、热键与 HUD

- `GardenMenuAdapter.DeveloperPageAdapter.developerSettings() → mappingSettings()` 提供 **Settings → Developer Options → Mapping**，沿用折叠、下拉和卡片控件；显示世界、原点、相对区块、记录状态和当前存储层，提供节日选择/创建、重设原点、删除当前区块和删除世界。没有新增 Misc → Mapping 页、扫描预览或路线功能。
- [MappingConfirmation](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/MappingConfirmation.java) 与 [MappingConfirmButton](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/MappingConfirmButton.java) 复用两项危险操作的交互：首次单击仅进入待确认；随后另一次双击（500ms 内两次点击）才执行。普通底色 #E0F0EF，待确认 #F4DAD9；英文提示完整显示并按宽度换行。绑定世界/操作，重设还绑定目标区块；离开、折叠、切换页面/世界/目标、选择另一危险操作取消。键盘不能绕过鼠标确认。
- `MacroKeyBindings` 新增三个默认 None 的映射：`mapping_initialize`、`mapping_toggle`、`mapping_redraw`；沿用现有热键菜单及物理按下边沿，在正常游戏且非布局编辑时分别调用 `initialize/toggleRecording/redraw`。删除和重设只有菜单入口。
- `MappingHud.taskRunning()` 接到 `MappingController.INSTANCE.running()`，既有显示字段、布局、筛选配置不变；“仅运行时显示”现在随真实记录状态变化。语言资源和 `LanguageKeys.hotkey()` 补齐中英名称、状态和 Hint。

### 验证与实机边界

- JDK 25、项目 Fabric/Loom：`gradlew.bat clean build --offline` 通过；最后的小范围变更再运行 build。
- `tests/mapping/run.ps1` 覆盖临时仓库初始化/重复初始化、负坐标、25 个已观察区块的候选边界与玩家优先、语义比较、节日覆盖/回退、缺基础层拒绝、Hub 重开复用、重设和回滚、删除范围、提交处取消、未知与空气、位打包、持久化字段及三次点击确认。静态接线检查确认非加载查询和 Mapping 零网络发送入口。
- `tests/mapping/compatibility.ps1` 使用独立下载的 **Litematica 0.27.14（SHA1 8f6916fdc68349c09f55aa400fd93e730b60abd7）+ MaLiLib 0.28.11**，不加入生产依赖。目标位数组对照 2–12 位跨 long；目标解析器读取本 Mod 导出，目标序列化器生成样本再由 Mapping 读取，非对称 XYZ 和楼梯属性通过。测试只注入无图形的目录/注册表上下文，**没有启动真实游戏，也没有操作其蓝图界面**。
- 既有配置/中英文、GUI、HUD 布局与世界信息、公共 context 回归通过；具体最后结果见本轮交付说明。原有工作区改动保留，未提交或切换分支。
- 待实机：Hypixel 不同 Hub 大厅/重连与 Garden 切换、真实 ClientChunkEvents 加卸载和仅 25 个缓存区块、记录期间网络抓包、热键与菜单禁用/双击手感、真实 Litematica 0.27.14 GUI 打开导出文件。无已登录客户端，不能把这些写成已通过。

本轮最终验证记录：Mapping 86 项及静态接线检查通过；HUD 布局 137、Instant HUD 27、面板/筛选 667、世界信息 76 项通过；GUI 114/166/26/27、提示 230、输入边界 73 项通过；配置与语言回归（含 4114 项语言检查）及公共 context 各入口通过。最后 build 与 `git diff --check` 通过。保持 `master @ 6be9b78`，原有未提交改动保留；没有提交或推送。账号回归的符号链接创建用例因环境不支持跳过；Mapping 路径安全逻辑已静态核对，真实符号链接场景未动态验证。

## 22. Mapping 离线回放与记录 HUD（2026-11-17）

### 菜单与职责边界

- `GardenMenuAdapter.mappingSettings()` 把 **Settings → Developer Options → Mapping** 分为同级的「当前地图状态」和「地图回放」。原状态信息、节日创建/选择与危险操作继续调用记录器；「删除当前区块」排在「重设基准点」前面。回放打开独立 [MappingReplayScreen](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/MappingReplayScreen.java)，Esc 或原菜单键返回原页面，正在编辑文本时保留原控件输入。
- 记录器当前世界/层/玩家坐标，与回放所选本地世界/节日/中心/选区分开持有。切换预览地图或节日不调用记录器的层选择、不保存预览中心，也不改变 `map.json` 的唯一原点。没有新增 Misc → Mapping 页、路线规划、边界编辑或网络订阅。
- 回放配色集中在 Screen 的专用常量：侧栏 #E9F2EE、输入 #F3F8F6、文字 #172C26/#48665C、强调 #1B978C、分隔 #CCDCD5；中列顶底栏共同使用 #192724。侧栏独立裁剪、滚动和空白拖动；本地化长标签、下拉选项及确认文字按可用宽度排版。

### 本地索引、采样和绘制

- [MapRepository.index](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/MapRepository.java) 返回指定层已存在文件的唯一相对 `Coordinate(x,z)`；排除临时文件，并复用原安全路径检查。记录器和回放共用此索引，不另复制文件名解析规则。
- [LocalMapLibrary](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/LocalMapLibrary.java) 只扫描当前实例 `config/ultimategarden/map/` 下的有效配置。目录必须与 worldName 规范化结果一致；坏配置单独报告。普通文件与已创建节日覆盖按坐标去重合成，覆盖优先，缺覆盖读普通层。所有区块仍用原 `LitematicIO` 解码；坏文件/未知方块单独报告并跳过，不生成样板地形。
- [ReplaySelection.nearest](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/ReplaySelection.java) 按 `max(0,abs(dx)-1)^2+max(0,abs(dz)-1)^2`、原始欧氏平方、Z、X 排序，截取准确的可用区块数量。已核对 Loom 26.1.2 的 `ChunkTrackingView.isInViewDistance/isWithinDistance`；这里使用其距离思想排名，输入仍是精确区块数。大整数先与实际可用数比较，不创建不存在的坐标环。
- [ReplayMesh](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/ReplayMesh.java) 按区块最小 XZ 开始，每 N 格取一根固定采样柱，沿原始 Y 查询最高非空气块；全空气柱不借用旁柱。支持仅外露、向内 2 层和全部 Y；相邻区块数据用于实心接缝剔除。N=1..16，默认 2。新增 `ChunkSnapshot.state()` 提供只读单格访问，避免反复复制整组索引。
- [ReplayMaterials](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/mapping/ReplayMaterials.java) 由完整注册名和 Properties 恢复原版 BlockState，使用 `EmptyBlockGetter` 查询状态体素形状、地图材质颜色和遮挡属性，不读取活世界。预览采用状态形状和材质色；楼梯上下半部等状态影响几何。
- [ReplayCamera](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/ReplayCamera.java) 实现附件的同一正交基底：`(sz*x-sx*z, (sx*x+sz*z)*sin(p)-y*cos(p))`，深度为 `(sx*x+sz*z)*cos(p)+y*sin(p)`。默认 (-2.07,1.52,1.40)；水平长度最多 2.5，Y=0.12..3，接近竖直时保留最后水平朝向。视图的高度居中与 XZ 枢轴都只是显示状态。
- [ReplayRaster](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/ReplayRaster.java) 在后台进行几何深度测试和像素归属记录；不通过强制透明展示隐藏块。透明材质在不透明几何后排序合成。最终图像在客户端线程上传 DynamicTexture；保留原生 16×16 网格、暖色中心框、青色选区及上方 37 GUI 像素空白拖动带。
- [ReplaySession](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/ReplaySession.java) 持有有界单线程工作队列、128 项解码 LRU、场景 generation 和合并后的最新相机请求。文件分批读取（每批至多 4 个或约 8ms，单个解码完成后检查预算），过期工作按代际丢弃。列表/索引任务不被相机或参数变化误取消。几何只在数据/参数变化时重建，逐帧绘制不读目录、不解码 NBT；关闭页面取消后台任务并释放纹理。

### 原生交互和冻结背景

- 地图/节日来自本地索引；地图默认选当前大区对应的有效地图，否则选本地可用项。换地图清选区并回到中心 (0,0)，换节日清选区并保留中心。
- [ReplayNumbers](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/ReplayNumbers.java) 统一整数提交、最近合法值回退和滚轮钳制。分辨率仅编辑固定 `1 /` 后的分母，区块数无固定业务上限，模糊强度 0..24 默认 9。悬停数字框即可滚轮调整，边界仍消费事件；侧栏空白滚轮不缩放画布，画布滚轮不改相机向量或中心。
- 缩放目标 0.40..4.60，沿用指数滚轮响应及 85ms 时间缓动；空白横拖按 0.009 rad/GUI 像素旋转。方向键按时间平移，方向与附件一致。左键单选、Ctrl 切换、Shift 真实坐标矩形、Ctrl+Shift 追加；只从当前图像实际呈现的区块选取，像素深度归属优先，空白区域再用逆绘制顺序的投影四边形命中。右键设置临时中心并清平移。
- [MappingReplayBackgroundMixin](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/MappingReplayBackgroundMixin.java) 在原版 `GameRenderer.renderLevel` 返回时，仅对当前回放 Screen 请求一次游戏画面捕获；首次留出世界渲染暖机帧。使用原版 `Screenshot.takeScreenshot` 内存回调，不写截图文件。
- [ReplayBackdrop](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/mapping/ReplayBackdrop.java) 缩放冻结图到 GUI 像素；[ReplayBackground](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/ReplayBackground.java) 在独立有界 worker 做灰度及可分离高斯滤波。强度 0 保留灰度。地图和 UI 在滤波后的背景之上绘制，不进入高斯处理；新强度代替旧任务，页面关闭释放背景纹理和原始像素。

### 回放删除与记录器协调

- `MappingController.editLocalMap(worldName,layer,relativeCoordinates,whole)` 接收回放明确选中的本地目标，返回包含删除数与逐项失败原因的 Future。只通过已有安全 MapRepository 路径操作。节日缺覆盖时不删普通文件；删覆盖后重读普通回退，删世界只作用于选中的目录。
- 删除当前正在记录的同名地图时，先使旧写入代际失效，在原单 IO worker 上串行执行管理任务，再刷新文件索引与 HUD。批量删除的块在当前会话被抑制自动补回；其他世界的回放管理不切换玩家当前记录目标。已接受的管理任务不被世界切换时的采集队列清理误丢弃。
- 删图沿用 MappingConfirmation 的「首次仅激活、随后另一次双击」流程；退出页面、换地图或另一删除操作清除确认，键盘不能绕过鼠标二次确认。选区删除及删图后刷新合成数据、实际数量和选区，保留具体失败信息。

### Mapping HUD 的地图记录分组

- 新增不可变 [MappingRecordingSnapshot](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/MappingRecordingSnapshot.java)。记录器在 Tick、任务事件及管理回调后发布；HUD 一帧只读取一次，不扫描目录、不读 NBT、不启动任务。
- 世界信息下新增 6 个独立筛选键：`recording_origin`、`recording_chunk`、`recording_layer`、`recording_status`、`recording_current_state`、`recording_saved`。复用原 HudConfig 的模式、字段开关、布局和持久化；菜单的简洁/详细内容中分别增加「地图记录」分组。
- [MappingRecordingContent](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/mapping/MappingRecordingContent.java) 负责双语格式化。实际工作中和已排队集合互斥，当前实现仍沿用单 IO worker，测试观测为 1 个正在处理 + 1 个排队，不虚构并发。当前块优先级为记录中 → 排队中 → 本层已保存 → 节日沿用普通层 → 未记录。
- 本层计数只使用实际已提交的本层文件索引；`done` 的本轮已检查记录不充当已保存计数，普通层回退不计入节日覆盖数量。初始化、提交、切层、删除、重设原点和上下文清理刷新索引/快照；失败/取消清除实际在途状态。
- 未识别世界时 6 项均为 —；已识别但未初始化显示「未初始化」及规定占位；已初始化停止显示「已停止」，没有任务时显示「记录中 · 等待新区块」。新增行参与原 HUD 尺寸、缩放和编辑预览计算，其他 HUD 字段与设置不变。

### 验证记录与边界

- `gradlew.bat clean build --offline` 及最终 build 通过，项目 JDK/Fabric/Loom 版本不变；git diff --check 通过。保持 master @ 6be9b78，未提交、推送或切换分支。
- `tests/mapping/run.ps1`：原存储 86 项；新增回放 158 项；真实记录器配合受控客户端缓存的 HUD/管理集成通过（包括 1 处理 + 1 排队、脚下已保存时周围继续处理、负坐标、覆盖提交/删除/普通回退、离线所选地图删除隔离）。原已保存 A 的停留、卸载重载、停启仍未重新读取或改写。
- 回放定向测试包含 1/64/超大区块数、稳定排序、固定采样柱与真实 Y、全空气柱、不同行分辨率/模式、实心接缝、透明深度与重叠命中、方向基底/视角约束/缩放、矩形多选、灰度/高斯、真实楼梯属性，以及视图重置不丢失尚未完成的列表读取。
- HUD 137/27/733 项及世界信息 76 项、配置/语言（4297 项语言检查）、GUI 与公共 context 回归通过。新的记录状态还验证了未初始化、0 处理且有队列、普通回退不计覆盖数等文本分支。
- 开发 Fabric 客户端已启动并加载新 Mixin；通过临时客户端测试适配器读取 9 个本地标准 .litematic、显示原生回放、验证无焦点数值滚轮，并取得实际画面（`.gradle/mapping-replay-native/native-replay.png`）。该测试适配器仅通过本次启动参数加载，不进入生产构建；临时地图不来自服务器查询。后续重复冒烟受界面状态变化影响，不能据此声称所有真实交互已验证。
- 待人工验收：真实地图库的节日覆盖、完整鼠标组合/侧栏拖动、危险操作手感、不同 GUI 缩放、冻结背景的视觉强度、大地图性能和真实 Hypixel 运行期间的 HUD 状态。删除范围和层回退已在隔离目录自动验证；线上 Hypixel 未测试。

## 23. Mapping 回放范围、整单元采样与观察方向修正（2026-11-17）

本节替代第 22 节中精确区块数量、固定最小 XZ 采样柱、正 Y 相机及旧深度选项的规则；第 22 节其余入口、文件管理、后台队列和 HUD 记录仍沿用。工作分支保持 master @ 6be9b78，保留原有未提交内容，本次没有提交或切换分支。

### 渲染距离与采样入口

- [ReplaySelection.withinDistance / inViewDistance](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/ReplaySelection.java) 对节日覆盖与普通层合成后的实际坐标做范围筛选：`dx=max(0,abs(chunkX-centerX)-1)`、`dz` 同理，严格满足 `dx²+dz²<R²`。对照项目 Loom 26.1.2 的 `ChunkTrackingView.isWithinDistance(...,false)`，不再排序取前 N 块。只遍历本地已有坐标，缺块不补空气；使用大整数处理半径平方，极大输入不产生坐标环或溢出。
- [MappingReplayScreen](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/MappingReplayScreen.java) 使用独立的运行期 `renderDistance="16"`，左栏显示「渲染距离」，状态栏显示实际「已渲染区块」。旧实现的 64 仅是页面内存默认值，没有持久化配置键；本次不将其读为半径 64，不改地图配置、原点或记录层。右键改变预览中心后重建范围，保留无焦点数值框滚轮行为。
- [ReplaySession.build / BuildJob.batch](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/ReplaySession.java) 仅加载筛选结果，移除范围外邻块的额外解码；分批读取、有界 worker、代际取消和解码缓存仍沿用。换中心、半径或节日层会重新构建范围及边界网格。
- 新增 [ReplaySampler.sample / depthSamples](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/ReplaySampler.java)：每个 N×N XZ 单元（边缘截断）搜索完整采样空间，按 Y 降序、X 升序、Z 升序选第一个非空气块，返回真实源坐标与完整状态；整个空间为空才无样本。表层选择最高层，向下 2 层扩展到最高样本以下两个原始 Y 层（空气不延长深度），全部遍历完整有效 Y。

### 外露面、深度和相机

- [ReplayMesh.build / exterior](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/ReplayMesh.java) 先为实际入选区块建立采样体积，再按相邻状态体素形状剪去被覆盖的面片。表层保留全高度外轮廓，包括范围边缘侧壁、缺块侧壁、地形高度差及外露底面；磁盘中存在但未入选的区块不参与遮挡。同形完整立方体可竖向合并，部分遮挡用矩形差集保留未覆盖部分。
- 表层之外的两个深度模式按各自采样空间提交额外内部候选面，继续经过 [ReplayRaster](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/ReplayRaster.java) 正常背面剔除和深度测试，不改成透视显示；因此不透明实心体的最终可见画面可相同。`Part.depthSamples` 记录采样候选数。[ReplayMaterials.resolve](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/mapping/ReplayMaterials.java) 使用原版 `canOcclude()` 与实际体素形状，避免把非整立方体一概当成无遮挡。
- [ReplayCamera.axis / normalize / basis](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/ReplayCamera.java) 将 Y 改为 −3.00..+3.00、步进 0.01。正值俯视、0 平视、负值仰视；全向量接近零时保留最后有效朝向和仰角。水平约束、缩放和投影公式保持原链路，XYZ 指示同步使用同一基底。仰视时区块网格轮廓放在实际底面一侧，外露底面参与正常深度绘制。
- 新增 [ReplayDepth.options](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/ReplayDepth.java)，将当前稳定枚举 ID 放首位，其他按原枚举顺序且不重复。`MappingReplayScreen.openDepthOptions / selectDepth` 只在明确选择不同值时重建场景；当前项、Esc 和外部点击均保持设置、相机及画布，保留选中标记。未改动的相机文本不重复量化，避免打开下拉时引起视角变化。
- [en_us.json](src/main/resources/assets/ultimate_garden/lang/en_us.json) 与 [zh_cn.json](src/main/resources/assets/ultimate_garden/lang/zh_cn.json) 更新渲染距离、实际数量、分辨率整单元优先级、渲染深度和相机 Y 的名称及原生 Tooltip。深度为 Surface / Surface + 2 Layers / All（表层／向下 2 层／全部）；中文角度按实际方向显示俯角／平视／仰角，英文显示带符号 Elevation。

### 本次验证

- JDK 25，`gradlew.bat build --offline` 通过。`tests/mapping/run.ps1`：存储 86 项、回放 6914 项及真实记录器受控缓存集成通过；包括原版范围逐点对照、R=16 四个指定边界、右键中心的纯范围计算、整单元孤立高块、同高 X/Z 决胜、全空气、边缘截断、三个深度空间、范围外/缺失邻块、实心及不等高接缝、底面和负 Y 光栅。
- 配置/语言回归通过（语言 4329 项）；HUD 137/27/733 项及世界信息 76 项回归通过。上述存储/记录器检查仍在隔离测试目录执行，不改正式地图。
- 原生 26.1.2/Fabric 客户端使用仅本次启动加载的临时测试适配器和独立测试地图验证：R=16 纳入 (16,0)/(0,16)、排除 (16,16)/(17,0)，无焦点距离数值框滚轮不改变画布缩放，负 Y 绘制、正/零/负 Y 的双语角度格式、真实像素命中的右键重定中心且原点不变。Surface / Surface + 2 Layers / All 逐一验证首项与选中 ID 一致，Esc、外部点击和重选当前项均不改变图像对象或场景 generation。日志有三条 `REPLAY_RULES_DEPTH_PASS` 和最终 `REPLAY_RULES_NATIVE_PASS`；先前加载屏幕覆盖、未滚到数值框的试验不计为通过。
- 原生日志保留于 `.gradle/mapping-record-audit/replay-rules-native.txt`，仰视截图为 `.gradle/mapping-replay-rules-native/replay-negative-y.png`。临时地图在验证后清理，测试适配器不进入生产 JAR，实际 UI 配置未改写。完整中英提示字典及占位符由语言回归检查；所有 GUI 缩放下的提示气泡手感、真实大型地图库性能和复杂透明/非整块地形仍待人工观察，未进行线上 Hypixel 测试。

## 24. Mapping 路径统一、实时背景与区块边界开关（2026-11-17）

本节替代第 22 节的冻结背景处理和默认始终绘制边界线规则；地图根目录的现行说明已同步改为下列位置。其余记录、坐标、节日合成、深度与输入规则不变。

### 唯一地图根目录

- 新增 [MappingPaths.root(gameDirectory)](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/MappingPaths.java)，返回当前游戏实例的 `config/ultimategarden/map/`，不依赖账号配置目录，不回退或自动迁移旧目录。有效地图为 `config/ultimategarden/map/<world>/map.json`；普通文件和节日子目录仍沿用原布局与唯一原点。
- [MappingController.tickWork / editLocalMap](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/MappingController.java) 和 [MappingReplayScreen](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/screen/MappingReplayScreen.java) 均调用这个入口。初始化、记录、重绘、普通/节日读取、存储索引与 HUD 快照、区块删除和整图删除因此使用同一根目录。
- 回放本地列表仍经 [LocalMapLibrary.list](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/LocalMapLibrary.java) 验证名称与目录一致；管理操作仍经 [MapRepository](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/MapRepository.java) 的规范化根目录、目标世界目录和符号链接校验，不能用回放选择删除其他世界或 Mapping 根目录。

### 同帧 GPU 世界背景

- [MappingReplayBackgroundMixin](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/MappingReplayBackgroundMixin.java) 改在 `GameRenderer.render` 中、原版 `GuiRenderer.render` 调用之前执行 `MappingReplayScreen.processWorldBackground`。此时当前帧世界、实体轮廓及世界后处理已结束，地图、HUD、文字和控件尚未绘制。
- [ReplayBackdrop.processWorld](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/mapping/ReplayBackdrop.java) 每帧直接使用当前主渲染目标：水平灰度/Gaussian → 垂直 Gaussian → 写回主颜色目标。两个临时 `TextureTarget` 采用 GUI 像素尺寸，两个 `MappableRingBuffer` 传入 UV 步距和当前 sigma；强度仍为 0..24。0 的半径为 0，仍输出实时灰度，不复用首张截图，不做 CPU 读回或后台截图排队。
- 新增客户端着色器 [mapping_backdrop.vsh](src/client/resources/assets/ultimate_garden/shaders/core/mapping_backdrop.vsh) / [mapping_backdrop.fsh](src/client/resources/assets/ultimate_garden/shaders/core/mapping_backdrop.fsh)，Gaussian 核为 `exp(-0.5*i*i/(sigma*sigma))`、半径 `ceil(3*sigma)`，亮度系数 0.2126/0.7152/0.0722。地图纹理和 UI 在上述处理后正常绘制，保留清晰颜色与边缘。
- 页面继续 `isPauseScreen=false`，拖动、滚轮和方向键仍由原回放输入路径处理。后台世界没有额外输入。GUI 尺寸变化时重建临时目标，关闭页面释放目标和 uniform ring；没有世界时显示原有深色底，不保留上一世界截图。[ReplayBackground.filter](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/ReplayBackground.java) 仅保留为 CPU 数学回归参考，不再驱动运行期背景。

### 边界线偏好与画布重绘

- 左侧依次为「渲染距离 → 显示区块边界 → 高斯模糊强度」。`MappingReplayScreen` 使用现有 [ToggleSwitch](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/ToggleSwitch.java)，英文 Show Chunk Borders；双语标签和 Hint 在现有语言文件中。开关仅影响线条，不影响深度选项或外露侧壁。
- [UIConfig](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/display/UIConfig.java) 新增 `mappingReplayShowChunkBorders=true`，由 [ConfigManager](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java) 的原有 UI 配置加载/保存流程处理。首次使用、缺字段及重置默认值均为开启；保存的关闭值在重载后保留，不另建配置文件。
- `ReplaySession.Request` 带只读边界开关，`MappingReplayScreen.toggleBorders` 保存后只标记画布 dirty；[ReplayRaster.render](src/client/java/com/kuangsanfeng/ultimategarden/client/function/mapping/replay/ReplayRaster.java) 按开关跳过轮廓线像素。已解码地图和 `ReplayMesh` 不失效，像素归属与投影轮廓命中仍计算，已选区块的填色仍绘制；右键居中继续走原范围重建逻辑。

### 本次验证与实际边界

- JDK 25 / Fabric 26.1.2，`gradlew.bat build --offline` 通过。`tests/mapping/run.ps1`：存储 86 项、回放 7478 项及真实记录器受控缓存集成通过，覆盖新目录预置文件的列表、普通/节日回退、HUD 索引、区块/离线整图删除、旧目录不创建，以及边界关闭时命中/选区不变。`tests/config/run.ps1` 通过，包含开关默认、持久化关闭与旧配置缺字段回到开启；语言检查 4341 项通过。
- 原生客户端使用单独的临时 Fabric 测试适配器、复制出的单人测试世界和新根目录下的隔离地图：世界持续 Tick，背景 worldFrames 持续增加，含移动客户端测试实体的两帧零模糊背景不同且 RGB 三通道相等；强度改为 9 后 GPU 输出变化，实际截图中背景模糊而地图/UI 保持清晰。测试实体不进入服务端存档。
- 原生真实开关点击、关闭值重载、关闭边界后的选区及右键居中通过；开关切换前后 `ReplaySession.mesh` 对象和 generation 均未变化。普通/节日预览、覆盖删除保留普通文件、整图删除保留邻接世界目录通过。日志有 `MAPPING_LIVE_ZERO_GRAY_DYNAMIC_PASS` 和 `MAPPING_LIVE_NATIVE_PASS`，保留于 `.gradle/mapping-record-audit/live-native.txt`；截图 `.gradle/mapping-live-native/live-background.png`。
- 开发实例未找到用户手动搬入的正式地图，本次用预先写入新根目录的隔离样本验证；未对正式地图执行删除。原先存在的空旧目录保持相同修改时间且没有新增文件，测试实例另验证旧目录始终未创建。临时地图和复制的测试世界在验证后清理；用户实际运行实例内的既有地图、大地图性能和不同显卡/GUI 缩放仍待人工验收。

## 25. Instant Transmission 单次 Y 取整测试（2026-11-17）

- 开发者选项下保留原启停按钮、参数和热键链路，将连续 1/2/3 点击实验的子节明确命名为「Instant Transmission 整段路径测试」。新增同级「Instant Transmission Y 取整测试」与独立启停按钮。两者共用现有控制器的输入所有权、WAIT_GUI、鼠标帧更新、归位与停止清理，互斥执行；没有新增一套鼠标模拟或位置包 Mixin。
- [InstantTransmissionController.toggleYRound / prepareY / beginY / tickY](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/InstantTransmissionController.java) 冻结启动 Yaw，正式 Pitch 固定 −90..+90、间隔 15°。初次归中后用一次无技能的正常跳跃测量峰值，按峰值生成 0.1 格高度目标。每轮先归位站稳并对齐 Pitch；0 高度直接施放，其他高度短按跳跃，根据当前高度及竖直速度估计下一 Tick 高度，选择更接近目标的当前可用时机，正式轮次最多调用一次 `NativeViewInput.click`。RESET 的跳跃、4 格下视施放和回程仍沿用原流程，不计入正式单次样本。
- 新增纯计算 `TransmissionYCoverage`???? 29 ???????。高度档用 `floor((实际高度+0.05)*10)`，十进制边界左闭右开。每个 Pitch 独立维护覆盖、由其他目标补得的档、失败次数；有效包样本记入实际档，原目标失配计失败，同档三次失败后跳过，后来补得仍可覆盖。0° 在汇总中单列为水平对照。
- `positionUpdate(before,after)` 复用原生应用位置包后的既有入口；Y 测试只把当前唯一点击窗口中的一个位置更新作为可配对样本，多包标为失配而保留全部记录。`after.y` 是传送落点，点击 Y、更新前 Y、更新后 Y 与后续落地 Y 分开记录。使用实测施放 Pitch 计算 `名义Y=施放Y−L*sin(Pitch)`，保留差值及整数判断，不把任何拟合公式作为观测值。距离读取已有 `Capability.standard().distance()`，当前为名义 12 格。
- [TransmissionLog](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/log/TransmissionLog.java) 增加独立文件名入口，Y 日志写入当前实例 `logs/ultimategarden/instant transmission/instant-transmission-y-rounding.jsonl`，原实验仍写 `instant-transmission.jsonl`；继续使用有界 JSONL writer、批次 START/END、序号和单调相对时间。新增 Y_JUMP_CALIBRATION、Y_ATTEMPT_START、Y_ATTEMPT、Y_POSITION_PAIR、Y_DESCENT、Y_RESULT、Y_SUMMARY。没有施放窗口或未配到包的尝试也保留；中止保留当前尝试与批次结束原因。
- 截断标记采用客户端已加载碰撞形状的名义线段检查与实际位移偏短的保守启发式，明确不是服务端碰撞证明。原始包坐标、下降速度、落地状态仍完整保留；离线分析应排除失配及疑似受阻样本。每 Pitch 汇总覆盖档、补得档、放弃档、失配与疑似受阻数。
- 验证：Gradle Build 通过；原传送回归 2242 项、RESET 诊断 112 项、飞行/规划 67 项通过；新增高度覆盖 54 项（包括 2.0→2.13 归入 2.1、边界、三败跳过及 Pitch 隔离）；受控生产控制器验证固定 Yaw、只点击一次、应用后 Y 取值、实际档补齐和停止取消。原启动 186 项及归位 204+93 项仍通过，配置/语言回归通过（语言 4356 项）。未在 Hypixel 使用真实 AOTE 实测；跳跃峰值、释放时机、位置包可靠性和受阻标记需实机确认。

## 26. Instant Transmission 两种测试的独立快捷键（2026-11-17）

- [MacroKeyBindings](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java) 保留原 `toggle_instant_transmission` 注册 ID、`GLFW_KEY_UNKNOWN` 默认值和原键映射实例，显示名称改为 Instant Transmission Full Path Test／Instant Transmission 整段路径测试。既有 Minecraft `options.txt` 中的对应绑定键不改名、不重设，仍走 `toggle(HOTKEY)`。
- 新增独立 `toggle_instant_transmission_y_rounding`（默认未绑定），使用自己的 `KeyEdge` 与 consumeClick 清理，在原允许处理热键的屏幕条件下调用 `toggleYRound(HOTKEY)`。两项各自使用原生按键配置键，不增加另一份绑定偏好文件。按住不重复启停，菜单与热键共用相同控制器和运行状态。
- [GardenMenuAdapter.HotkeyPageAdapter](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/menu/GardenMenuAdapter.java) 在快捷键页的开发者分组中列出两项独立子节、当前按键及完整双语描述。配置按钮接入原生 `KeyBindsScreen`，沿用 Minecraft 的修改、冲突显示与保存；[LanguageKeys.hotkey](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/language/LanguageKeys.java) 为两种名称提供明确本地化入口。开发者工具内的英文整段路径标题同时统一为 Full Path Test。
- [InstantTransmissionController.toggle / toggleYRound](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/InstantTransmissionController.java) 对同种测试再次触发执行停止；另一种运行时不停止、不启动、不释放其输入，通过 `HintManager.printRequired` 提示当前测试名称（不受普通聊天提示开关隐藏），状态文本也包含当前名称。Y 菜单按钮与热键使用相同入口；HOTKEY/MENU 来源保留原诊断语义。
- 验证：Gradle Build、控制器启动 186 项、归位 204+93 项通过；扩展的受控控制器测试覆盖两种热键来源、各自启停、双向互斥提示、旧 ID 与默认值保持及新 ID 独立，配置/语言回归通过（4379 项语言检查）。绑定兼容性通过注册 ID/配置键未变化及入口接线检查确认，未改写用户现有绑定；真实物理按键、原生按键界面保存重进及线上施放尚待实机验收。

## 27. 全部自有 Hotkey 的实例级独立开关（2026-11-17）

- [HotkeySwitches](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/HotkeySwitches.java) 是输入、原版列表和冲突的唯一状态入口。`Store` 使用已有 [ConfigIO](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/storage/ConfigIO.java) 原子写入当前实例 `config/ultimategarden/hotkeys.json`，按去掉 `key.ultimate_garden.` 前缀后的稳定注册 ID 保存布尔值，缺项默认开启。此文件不位于账号目录，不随账号切换重置；损坏文件先备份，备份失败不覆盖。物理按键继续保存在原版 options.txt，不改注册 ID、默认键或用户保存的绑定。
- [GardenMenuAdapter.HotkeyPageAdapter](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/menu/GardenMenuAdapter.java) 始终遍历完整 `MacroKeyBindings.getBindings()`，每项加入现有 ToggleSwitch 风格的「启用快捷键」。普通条目和开发者分组的两个传送测试均提供开关，关闭后仍可在 Mod 页查看原按键并重新开启。
- [HotkeyStateMixin](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/HotkeyStateMixin.java) 对原生 KeyMapping 的物理事件分发、isDown、consumeClick、matches/matchesMouse 和 same 接入同一状态。禁用时跳过自身的原生 click/set 分发，清空已有 clickCount 与 down 状态，禁止菜单关闭匹配和其他专属匹配；同键的其他映射继续收到事件。启用时原映射直接恢复，无需重注册或临时改为 Unknown。
- [HotkeyListMixin](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/HotkeyListMixin.java) 仅过滤原版 KeyBindsList 的构造输入；开关版本变化后 refreshEntries 通过 [ScreenRebuildAccess](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/ScreenRebuildAccess.java) 重建列表。[HotkeyConflictMixin](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/HotkeyConflictMixin.java) 过滤 KeyEntry 的冲突候选，[HotkeyOptionsMixin](src/client/java/com/kuangsanfeng/ultimategarden/client/mixin/HotkeyOptionsMixin.java) 过滤原版重置全部及其按钮状态。Options.keyMappings、KeyMapping.ALL 和保存用的原绑定均保留，隐藏项不会因原版重置可见条目而丢失绑定。
- [MacroKeyBindings](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/MacroKeyBindings.java) 的菜单物理按键查询、Shootout 鼠标专属匹配也检查状态。`open_menu` 可以禁用，聊天命令 `/ultimategarden menu` 的注册与打开路径不依赖此开关，保留恢复入口。新 Mixin 均登记于客户端 mixin JSON，目标签名按项目 Loom 26.1.2 源码核对。
- 验证：Gradle Build 通过，新增 `tests/hotkeys/run.ps1` 检查实例默认值、保存/重载、条目隔离、恢复及原菜单注册键 M 不变；配置/语言回归通过（4389 项语言检查）。原生 Fabric 在独立 `--gameDir` 和临时测试 Mod 中运行两次：第一次禁用菜单键，原版列表隐藏、同 M 的背包映射无菜单冲突且独占事件，待处理菜单 click 清空；第二次真正重启后仍禁用，随后开启并刷新列表，菜单条目/M/冲突/输入立即恢复。日志分别有 HOTKEY_NATIVE_FIRST_PASS 和 HOTKEY_NATIVE_SECOND_PASS，保存在 `.gradle/mapping-record-audit/hotkey-native-first.txt` 与 `hotkey-native-second.txt`。测试未改动用户真实 options.txt 或开关文件；原生事件通过测试适配器调用，所有实体键鼠设备及真实 Macro 场景仍可人工验收。

## 28. 快捷键分类层级、逐级总开关与折叠卡样式（2026-11-17）

- 新增 [HotkeyGroups](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/HotkeyGroups.java)，统一拥有关系及界面顺序：Farm Macro、Carnival → Zombie Shootout、Mapping、开发者 → Mapping / Instant Teleport、HUD、首选项。19 个真实注册快捷键各有唯一归属，原 ID、默认键和已保存绑定不变。当前三个 Mapping 快捷键均属常规操作，一级 Mapping 使用它们；开发者 Mapping 暂无真实快捷键，仅保留独立分类开关，不复制条目或虚构调试功能。
- [HotkeySwitches.Store.effective](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/HotkeySwitches.java) 计算自身值与全部祖先分类值的 AND。原实例 JSON 中新增 `group:farm`、`group:carnival`、`group:carnival.zombie_shootout`、`group:mapping`、`group:developer`、`group:developer.mapping`、`group:developer.instant_teleport`、`group:hud`、`group:preferences`，缺失默认开启。`ownEnabled` 和 `groupEnabled` 分别提供保存值，供菜单保留真实开关外观；关父级不改任何子值。`setGroupEnabled` 保存成功后清除所有失效映射的 down/click 状态并递增原版列表版本。
- 原生输入、matches、列表和冲突仍统一读取 `HotkeySwitches.enabled`，其内部改为上述有效状态。已有 Mixin、列表刷新和原生 options.txt 绑定保存链路不另建一套规则；关闭首选项时菜单快捷键失效，但 `/ultimategarden menu` 仍沿原命令入口工作。
- [GardenMenuAdapter.HotkeyPageAdapter.hotkeyGroupContent](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/menu/GardenMenuAdapter.java) 递归布局，各分类展开后的首项为本级总开关，再绘制子分类或唯一所属功能。所有分类及功能始终可以展开、设置，不因父级关闭禁用编辑。功能卡仅含只读绑定值（缺失为「未绑定」）和自身开关；描述只经标题问号显示，移除常驻说明与绑定设置按钮。绑定修改仍由 Minecraft 原版按键界面负责。
- 共用 [CollapsibleSection.headerSurface / extractContents](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/CollapsibleSection.java) 对所有深度统一使用白色基础底，悬停使用当前主题 selected 色，不再对子级混合侧栏底色；焦点保留不会造成离开鼠标后仍显示悬停底色。展开/收起使用同一规则，层级仅由原有缩进表达，仍尊重项目的透明度与禁用透明度机制。白底标题使用深色文字保证暗色主题下可读。
- 验证：Gradle Build；快捷键存储、祖先 AND、关闭再开启保留子值、开发者 Mapping/普通 Mapping 隔离、Carnival/Zombie 子开关保留、分类重载、19 项唯一归属和六大根分类顺序回归通过；所有主题的白底/悬停色公式通过。GUI 会话 114、折叠布局 166、Tooltip 26、命令/倒计时边界 27 项通过，配置/语言回归通过（4434 项语言检查）。隔离 Fabric 客户端验证分类关闭后 M 的事件/列表/冲突释放及重启保存，分类开启后原绑定和冲突恢复；实际开发者父级还原保留 Y 测试的单独关闭状态。开发者 Mapping 没有实际条目，其隔离性按保存值及相邻真实功能有效性验证。
- 实际原生 UI 截图为 `.gradle/hotkey-native/groups-collapsed.png` 与 `groups-expanded.png`：六个根分类顺序、开发者与 Instant Teleport 各自首项总开关、三级功能卡的只读按键/自身开关、白底与逐级缩进已目视核对，常驻说明和绑定按钮不再出现。截图使用仅临时测试 JAR 中的离线适配器，跳过无世界时自动关菜单；不进入生产构建。先前退回标题界面的截图已被有效截图覆盖，不作为验证依据。悬停强调色通过共享颜色函数回归确认，真实鼠标操作、不同缩放和运行中世界的完整观感仍待人工验收。

## 29. Y 取整 Tick 计划、地图绘制分类及 HUD 世界筛选（2026-11-18）

- Y 测试使用 [TransmissionTickPlan](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/TransmissionTickPlan.java)：固定启动 Yaw，13 个 Pitch（−90..+90，15°），每个 Pitch 对应 0..39 共 40 个独立跳跃，整批 520 项。移除高度分档、峰值校准、补档和三败重试，旧 TransmissionYCoverage 类移除。0° 仍标记为水平对照。
- [InstantTransmissionController.beginY / tickY / fireY / finishY](src/client/java/com/kuangsanfeng/ultimategarden/client/function/instant_transmission/debug/InstantTransmissionController.java) 站稳/对齐后短按跳跃，首次观测 `!onGround && Y>起跳前Y` 的 Client Tick 定义为 0；相对 Tick 达计划档即通过原公共输入施放一次，不以当前是否落地阻挡。实际 Tick、施放 XYZ/Yaw/Pitch、速度及 onGround 独立记录，迟发标记 MISSED_TARGET_TICK，提交失败标记 DISPATCH_FAILED，无离地或无配对更新也记录。唯一可辨识位置更新的 after Y 为传送落点，before 和自然落地仍分开；多包或无包不伪造结果。每个 Pitch 输出 40 档明细、有效/失配/缺失数量和未执行数量。原整段路径、RESET、日志文件、取消输入链路保持。
- [HotkeyGroups](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/HotkeyGroups.java) 移除一级 Mapping，三个原常规地图键归入开发者 → 地图绘制，英文 Mapping。注册 ID、单项状态与按键不变。[HotkeySwitches.Store](src/client/java/com/kuangsanfeng/ultimategarden/client/runtime/hotkey/HotkeySwitches.java) 将旧 `group:mapping` 与 `group:developer.mapping` 的值按 AND 合并为后者，并移除旧根开关，避免丢掉已关闭状态；此后只有开发者、地图绘制及自身三个开关决定有效性。迁移失败保留保守的运行状态并阻止覆盖，原版列表与冲突继续消费同一有效值。
- 新增纯 [WorldIds](src/client/java/com/kuangsanfeng/ultimategarden/client/function/world/WorldIds.java)：静态列出 21 个世界，不依赖本地地图。来源核对：`WorldInfoService.snapshot().island()` 优先取 Tab Area 文本，缺失时取 `WorldInfoText.modeName(mode)`，已有 dynamic → Private Island、hub → Hub 映射。Private Island 显示名规范化为 private-island，与 `MappingNames.key("Private Island")` 的既有目录键一致，但筛选不改任何地图名称、配置或目录。匹配忽略大小写、空格/分隔符、直/弯撇号，Dungeon 与 Dungeon Hub 分开；Glacite Mineshaft 独立于 Glacite Tunnels，不把后者别名映射成矿井。矿井实例原始值尚未取得，须进入实例后实测，不假定未知 mode ID。
- [HudConfig](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/display/HudConfig.java) 增加现有 hud.json 中的 `worldFilters.<HUD ID>.enabled/worlds`。各 HUD 默认过滤关闭、21 项全选；关闭保留集合。四个实际 HUD 渲染入口在原有可见性条件之外调用 `worldAllows(id,WorldInfoService.snapshot().island())`，未知或不在所选集合时隐藏，过滤关闭不做 ID 匹配。没有匹配缓存，每帧只读当前快照，旧世界字段由原世界生命周期清空；布局编辑预览仍沿用原强制预览规则。
- [GardenMenuAdapter.hudWorldFilter](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/menu/GardenMenuAdapter.java) 为每个 HUD 建独立折叠状态，标题右侧保留 58px 给开关，沿用已有日志标题的命中分离，开关不折叠列表。21 行复用 JSON 复制的 Card/SelectionCheckbox：名称在左、复选框在右，默认白底。单选、全选和取消全选立即更新并保存 HUD 配置，无应用或提交按钮。各 HUD 的展开状态、选择与过滤状态相互独立。
- 本地验证：Gradle Build；520 项计划遍历、13 Pitch、迟发归档、无重试；受控生产控制器检查 tick 0/1/20/39，包含已落地仍按计划点击、单轮仅一次以及应用后包 Y。原传送 2242、RESET 日志 112、飞行/规划 67、启动 186、归位 204+93 回归通过。快捷键旧分类迁移/唯一归属/五大根顺序通过；HUD 137/27/733、世界上下文 77 及新增 21 世界筛选、双 HUD 隔离、未知隐藏、全选/全不选、关闭/恢复及 JSON 重载通过，语言检查 4544 项通过。尚未在 Hypixel 施放 AOTE 或进入 Glacite Mineshaft；新筛选 UI 的实机鼠标与跨区表现待验收，未声称已取得矿井原始值。

### 2026-11-18 · 折叠卡标题开关、连续背景与菜单层级

- [CollapsibleSection](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/CollapsibleSection.java) 内部持有标题开关，由 `layoutToggle()` 同步绘制和点击坐标，`mouseClicked()` 优先分发给开关；标题与问号、三角形、最右开关相互分离。修复世界筛选原先将标题和开关作为重叠 Screen 子控件的问题：26.1.2 的 `ContainerEventHandler.mouseClicked()` 只分发首个命中项，标题返回 false 不会继续尝试开关。原生菜单复现时点击 `(375,76.5)`，标题边界 `(144,62)..(405,91)`，事件已处理但配置仍 false；修复后开关改变值并保留折叠状态，三角形只改变折叠状态。
- `drawPanel()` 在 [GardenMenuAdapter.extractRenderState](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/menu/GardenMenuAdapter.java) 的子项、文字和控件之前绘制；展开底板覆盖可见内容、间距和底部留白。滚动导致标题离屏时仍绘制所属内容底板。外轮廓沿用 10px 圆角，子级底板从浅色 #F3F8F6／深色 #141D18 向内 15px 渐变为白色；悬停仅覆盖标题行。`sectionInset()` 将正文统一推进一级（14px），子标题按 State.depth 定位，右边界保持对齐。Farm、Pest、ESP、Preferences、Hint、Carnival/Shootout 及其后代共用这一机制。
- `headerToggle()` 迁移 Pest Loadout、Pest ESP 和 Box/Fill/Tracer、各级 Hotkeys 与单项开关、Hint、四个 HUD 显示开关及视角检查开关；原回调、配置键和 HotkeySwitches 有效状态机制继续使用。世界筛选与开发者 Logs 不再注册独立的重叠开关。标题文字换行后同步计入布局高度。世界筛选仍即时保存 HUD JSON，不改变选中世界。
- [GeneralConfig](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/GeneralConfig.java) 增加 `hint.groups.macro/pest/focus`，缺失默认 true。`hintSelected()` 返回保存值，`hintEnabled()` 在提示出口叠加组开关；`hintsToJson()` 保存原个人值，关闭父级不会清空选择。[HintManager.isEnabled](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/hint/HintManager.java) 继续作为实际输出门槛。
- [ViewDeviationConfig](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/ViewDeviationConfig.java) 的 Anti-Check `enabled`、`viewDeviationCheck.enabled` 默认 true，`effective(Settings)` 合并父级及原子项 enabled。[ViewDeviationCheckManager](src/client/java/com/kuangsanfeng/ultimategarden/client/macro/zombie_shoot/aim/ViewDeviationCheckManager.java) 在观测、消费后评估及提示 Tick 中检查有效值，关闭后清理旧观测和提醒。Farm View Check 保持源码原有预留状态，本次未新增农业检查算法。
- [RandomDelayConfig](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/model/RandomDelayConfig.java) 增加根 `enabled` 和每个 Key 对象的 `enabled`，缺失默认 true；`sample()` 仅在 Anti-Check 总开关、Random Delay 总开关、该项开关均打开时应用原分布，否则返回 0 Tick。ConfigManager 对同一配置实例绑定父级读取，原范围、子项值及未知 JSON 字段保留；开关影响随后取样，不重排已提交的调度任务。
- Developer Options 将原整段路径和 Y 取整测试收纳到 Instant Transmission 母项，入口、配置及执行流程不变。英文卡片标题调整为 Farm Configuration、Farm Macro Command、Crop Route Definition、Pest Loadout；Instant Transmission 名称统一，Hotkey 稳定 ID 不变。
- 验证：Gradle Build 通过；新增 `HeaderToggleConfigRegression` 51 项覆盖默认值、父子门槛、原值恢复及 JSON 重载；Hint 实际输出回归 242 项通过；配置、GUI、Hotkeys、HUD、Focus、输入、路线、Shootout 定向回归通过。原生 Fabric 离线菜单自动点击 440 项通过，覆盖六页及世界筛选 GUI scale 1/2/3、滚动、展开/收起与独立点击，保留浅/深主题截图核对。在线 Shootout 启停、实际游戏中的 HUD 跨世界显示与提示/检查效果仍需实机验收；离线 UI 使用隔离配置与测试钩子，不把离线按钮拒绝启动视为在线验证通过。

### 2026-11-18 · 2px 折叠底板、渲染提交优化与启动配置纠错

- 本节替代上一节底板渐变的 15px 宽度，现为 2px。母级仍用连续白底；嵌套底板的圆角边缘从浅色 #F3F8F6／深色 #141D18 向内过渡至白色。[CollapsibleSection.drawPanel](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/widget/CollapsibleSection.java) 持有局部 `PanelLayer` 几何缓存；宽高、主题边缘色或背景透明度变化时重建，滚动只改变绘制平移，折叠通过可见内容高度使缓存失效。标题行开关和折叠点击分发不变。
- [RoundedRect.Geometry/geometry](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/render/RoundedRect.java) 提供不可变局部矩形几何。描边将圆角之间的直边合为长矩形，提交量由圆角半径决定，不再逐行遍历整个展开高度；底板缓存这些坐标，逐帧不再生成描边几何。两圈描边与内部填充互不重叠，保持透明度和圆角覆盖。[GardenMenuAdapter.extractRenderState](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/menu/GardenMenuAdapter.java) 在提交底板前排除视口外底板，标题离屏但正文仍可见的母级继续绘制。
- 性能测量使用测试 Mod 的渲染入口计时和底层 fill 调用计数，未向正式源码加入诊断开关。相同 Settings 页面、854×480 GUI、缩放 1、58 个折叠区全展开；每阶段先预热 60 帧，再采样 300 帧。帧时间为同一提取入口相邻调用间隔，CPU 时间为菜单提取开始至结束。离线客户端的基准帧间隔约 16.7ms，不代表在线世界的帧率上限。相位间 JIT 波动使白底阶段略快于原布局；主要差异明确来自旧渐变。

| 测量状态 | 帧时间中位数 / P95（ms） | 菜单提取中位数（ms） | 底板耗时中位数（ms） | 底板 fill 调用/帧 |
|---|---:|---:|---:|---:|
| 修改前，仅原布局标题板 | 16.68 / 17.38 | 1.39 | 0.094 | 1,218 |
| 修改前，连续白底 | 16.69 / 17.53 | 0.74 | 0.055 | 1,218 |
| 修改前，白底 + 15px 渐变 | 23.04 / 25.34 | 22.34 | 21.777 | 1,082,118 |
| 修改后，白底 + 2px 渐变与缓存/裁剪 | 16.67 / 17.49 | 1.15 | 0.078 | 135 |

- 实际根因是旧描边按展开高度逐行提交，再乘以 15 圈，且给视口外长底板也生成/提交几何，造成渲染线程 CPU 开销。白底本身不是主要瓶颈。矩形几何回归逐像素对比原描边，并验证 2px 多圈覆盖与透明度不叠涂；长面板描边提交保持有界。
- [ConfigManager.userSettingsRoot](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigManager.java) 是生产账号设置根入口，初始化装配也使用同一入口：`<实例>/config/ultimategarden/user-setting/`。原账号子目录、配置复制/覆盖、初始化及重载都通过同一 `AccountDirectories` 实例解析；锁位于该根下的 `.account-storage.lock`。不自动搬迁旧文件。HotkeySwitches 的 `<实例>/config/ultimategarden/hotkeys.json` 与 MappingPaths 的 `<实例>/config/ultimategarden/map/` 保持不变。
- 当前账号第一次成功确定后，`correctingStartup/startupCorrectionPending` 仅包围这次模块加载；使用原模型加载、校验和默认值，再由共用 `encode()` 产生固定字段定义。[ConfigCanonicalizer](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/persistence/config/ConfigCanonicalizer.java) 按序列化器字段顺序整理对象，schemaVersion 固定首位；CropConfig 按随包作物定义投影，保留已有可选继承语义和用户数组。ESP 合法动态目标由既有注册规则保留，目标内部仍只输出当前字段。HUD 的个人 detail 选择不被运行期派生并集覆盖；合法颜色写法和数字表示保留，世界列表保留合法已选项顺序。已废弃开发者字段及固定对象内未知键移除。普通保存、菜单操作、后续账号切换及配置复制不额外运行这套纠错。
- 启动按紧凑有序 JSON 比较内容和顺序，仅缺失文件或需纠正时才原子写回；已正确文件再次启动保持修改时间。未来 schema 的只读保护、无效文件备份、身份校验和存储锁沿用。处理范围只有本次当前账号的十类设置 JSON，不修复其他账号；原有 UUID 归属查验仍会读取账号身份元数据，不扫描其他账号的设置内容。
- 验证：Gradle Build、配置/账号/语言、GUI、Hotkeys、HUD、ESP 与 Mapping 回归通过；新增启动纠错 64 项检查当前账号隔离、废弃/缺失/错序、动态目标、数组顺序、合法值、未来版本保护及第二次启动零重写，正确文件预设固定旧修改时间用于检测重写。另复制新目录中已有账号的 11 个文件到隔离测试目录后加载/纠错/加锁，原文件内容及修改时间不变。圆角几何 1,351,761 项逐像素检查通过；原生 Fabric 离线菜单 440 项点击检查及浅/深主题嵌套截图通过。未声称已在登录服务器的实际业务运行中完成性能或账号切换验收。

### 2026-11-18 · 公共寻路核心原型与本机地图预检（未完成正式功能）

> 本节不是功能验收通过记录。新增模块尚未接入菜单、原生输入执行或客户端生命周期，不会自行启动角色移动。H1/H2 尚未规划成功，D1 缺图；三组均未进行客户端实跑。

源码位于 `client/function/path_finding/`，不改动旧 Instant Transmission 实验模型：

| 入口 | 当前实际职责与边界 |
|---|---|
| [PathTypes](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/PathTypes.java) | 请求参数、权重、WALK/JUMP/FALL/TRANSMISSION 段及结果状态。目标目前按脚底点处理，目标 XZ 内部居中，保留原请求坐标。Options 的 12 格为离线默认，不代表已确认真实装备能力。 |
| [model/TransmissionModel](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/model/TransmissionModel.java) | 起点脚底加连续方向向量，X/Z 使用 floor(raw)+0.5，Y 使用 floor(raw+0.5)。不归一化量化位移、不使用 UP 表或 0.62 偏置。旧 FlightModel 保持独立实验用途。 |
| [terrain/MapTerrain](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/terrain/MapTerrain.java) | Worker 私有的 MapRepository/MapConfig/ChunkSnapshot 缓存，按当前节日层及基础回退读图。floorDiv/floorMod 和统一 origin 还原坐标；Mojang BlockState 保留属性，以原版碰撞形状检查玩家体积、支撑与段扫掠。缺块、扫描高度外位置不作空气。它不访问服务端、不发网络请求。 |
| [planning/PathPlanner.plan](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/planning/PathPlanner.java) | 有限候选移动图。常规模式先试同一视线直达，再作非最优距离优先搜索；权重模式使用零启发的 Dijkstra，并将朝向、累计传送距离纳入状态。结果只针对当前候选图，不能声称已实现所有合法角度的全局最优或三维粗规划。支持节点预算、取消及清楚的缺图状态。 |
| [execution/PathService](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/execution/PathService.java) | 单后台线程规划；调用方提供 Terrain 工厂。Ticket 限定发起者取消，快照与 Stage 供只读查询；Driver 端口在客户端 Tick 推进，只有确认完成才推进下一段。执行租约跨服务实例互斥；取消/关闭/显式 contextChanged 释放 Driver 并使旧规划失效。当前没有原生 Driver，也没有自动接线断线/账号/世界通知，调用方必须明确调用这些端口。 |

地图准备仅补复制本机 `map/hub` 和 `map/deep-caverns` 中运行目录缺少的文件；已有运行目录文件未覆盖，原始地图未修改。正常读取根为 `run/config/ultimategarden/map`，沿用 MappingPaths，没有重复的 map/map 目录。Hub 复制 585 个缺少文件（含配置），运行目录有 584 个蓝图区块文件；Deep Caverns 复制 111 个（含配置），有 110 个蓝图区块文件。本机 Deep Caverns 各实际数据层均未找到 `Deep Caverns 0 9.litematic`，原点仍是 `(32,0,16)`。

`tests/path-finding/run.ps1` 使用项目 runtimeClasspath、原版 Bootstrap 和正常 MapRepository 读取运行目录。三组分别执行 NORMAL、WEIGHTED，均开启技能和投影，距离 12、保守跳高 1、默认权重、节点预算 20,000。结果写入 `build/path-audit/scenarios.json`，保留原请求、解析目标、段统计、代价、缺图信息及 `executed=false / actualEndpoint=null`，不能把失败结果的空段列表当成实际零次移动。

| 场景 | 起点 → 原终点 | 常规模式 | 权重模式 | 实际执行 |
|---|---|---|---|---|
| H1 / Hub | `(0,77,-1)` → `(91,75,174)` | BUDGET_EXHAUSTED，展开 20,001 节点 | BUDGET_EXHAUSTED，展开 20,001 节点 | 未执行，无可交付路线 |
| H2 / Hub | `(0,77,-1)` → `(83,102,113)` | BUDGET_EXHAUSTED，展开 20,001 节点 | BUDGET_EXHAUSTED，展开 20,001 节点 | 未执行，无可交付路线 |
| D1 / Deep Caverns | `(4,157,84)` → `(35,12,175)` | MAP_MISSING，终点相对块 `(0,9)` | MAP_MISSING，同一文件缺失 | 未执行，未补造地形 |

构建通过；新增 18 项受控回归覆盖统一量化、负坐标、18 格 12+6、固定视线、禁用技能、水平曼哈顿代价、权重改变选择、空中范围/投影、资源预算及外部调用者取消释放。执行测试是受控 Driver，不是原生玩家移动。原有 Instant Transmission 和 Mapping 回归通过。

尚缺的正式实现：三维粗规划与完整固定距离候选搜索、可靠跳跃轨迹及实际上升代价、碰撞截断后的空中穿过验收、180 格落地及跨 6 格刷新摔落距离动作、真实装备能力确认、速度/跳高初测和环境缓存、Misc 菜单与持久化、中英资源、公共原生输入/位置包驱动与重规划、本地测试世界复现及逐组客户端实跑。没有将这些项记为已实现或已验收；原有调试功能保持可用，但本轮并未完成它们的全部新寻路验收场景。

### 2026-11-18 · Hub 200,000 节点独立复测与合并失败日志

- 先执行现有 Gradle Build 与 `tests/path-finding/run.ps1 -HubOnly` 基线；`MapScenarios` 的 `--hub-only` 只跳过 D1，不改变原有默认入口。新增 [tests/path-finding/hub.ps1](tests/path-finding/hub.ps1) 编译并独立启动四个 JVM，每次使用 [HubSearchAudit](tests/path-finding/HubSearchAudit.java) 经 MappingPaths/MapRepository/MapConfig 正常读取 `run/config/ultimategarden/map/hub`，选中层 NONE、保守跳高 1、Instant Transmission 开启、距离 12、默认权重。初始 Yaw/Pitch 为 0/0。原始起点 `(0,77,-1)` 不额外平移；目标内部解析为 H1 `(91.5,75,174.5)`、H2 `(83.5,102,113.5)`，保持输入脚底 Y，原请求另存。四组的起点与目标支撑、净空检查均为 true。
- 200,000 只由本次 Hub 执行入口通过 Options 传递，未改变全工程默认预算 20,000。[PathPlanner](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/planning/PathPlanner.java) 修正先自增再判断的越界计数：现在最多处理预算指定数目的非过期队列节点，不再记为 200,001。重复的过期队列项单独计数，不占实际展开计数。同一完整状态再次改善并重新处理仍计入实际展开。正常直达阶段最多 min(预算,1000) 次；每节点固定传送角度候选为 57 个，另有末段候选；没有已实现的粗规划或独立局部搜索预算。日志明确区分 HEADING_GRAPH_BUDGET、开放表耗尽和 MAP_MISSING。本轮碰撞、未知区块、移动候选、代价及优先级规则未放宽。
- 新增可选 [SearchAudit](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/planning/SearchAudit.java) 与 `plan(..., cancelled, audit)` 重载。未传入诊断时不写日志。约每 1,000 个已取出并通过过期检查的节点发布进度（该节点的后继尚未生成）；记录展开数、候选尝试数、已发现完整状态数、唯一位置、已展开完整状态/位置、同状态再次展开数、开放表规模/峰值、队首代价和真实优先级、当前与最近已展开节点及其代价。最近节点按三维距离定义，不与队首混同。
- MapTerrain 保留最近一次失败的实际方块位置与原因、实际成功读取的层文件路径，支持拒绝记录而不重复求解。动作候选、节点是否落地的分类查询与累计传送距离状态分类分别归属，避免把正常空中状态记作某个动作拒绝。拒绝计数是失败检查次数，不是互斥的候选数。每个动作/原因仅保留最多三个样本（首例、近目标样本、周期抽样的较新样本），采样不逐节点写盘。传送碰撞截断、粗规划、约 6 格刷新和完整落地控制器标为未实现；已存在的 180 累计计数门槛单独记录，不冒称完整落地检查。
- 交付日志为 [build/path-audit/hub-pathfinding.jsonl](build/path-audit/hub-pathfinding.jsonl)，一次命令会建立新日志，四组追加到同一文件，runId 为 `H1-NORMAL-200k` 等。包含 RUN_START、SEARCH_PROGRESS、REJECTION_SUMMARY、SEARCH_LIMIT/SEARCH_TERMINATION、成功时的 ROUTE_FOUND、RUN_END。结束记录保存实际有效预算、全部汇总、最接近节点的**部分推进路径**及实际加载文件的 SHA-256/与原图比对。失败结果的部分路径不是可执行路线；`clientExecuted=false`、`actualEndpoint=null` 明确没有实跑。每次结束刷新；异常和进程非正常退出有终止记录兜底，剩余组合继续运行。最终日志 820 行，约 851 KiB。

| runId | 状态 / 触发限制 | 实际展开 | 搜索耗时 / 总耗时（秒） | 最近已展开位置 / 剩余三维距离 |
|---|---|---:|---:|---|
| H1-NORMAL-200k | BUDGET_EXHAUSTED / HEADING_GRAPH_BUDGET | 200,000 | 33.661 / 36.585 | `(2.5,79,29.5)` / 170.182 |
| H1-WEIGHTED-200k | BUDGET_EXHAUSTED / HEADING_GRAPH_BUDGET | 200,000 | 149.597 / 152.488 | `(105.5,161,84.5)` / 125.268 |
| H2-NORMAL-200k | BUDGET_EXHAUSTED / HEADING_GRAPH_BUDGET | 200,000 | 32.333 / 35.098 | `(6.5,79,26.5)` / 118.436 |
| H2-WEIGHTED-200k | BUDGET_EXHAUSTED / HEADING_GRAPH_BUDGET | 200,000 | 156.084 / 158.950 | `(91.5,149,70.5)` / 64.203 |

- 已证实：两组常规模式在首个 1,000 节点进度点即达到本轮最小剩余距离，后续未改善；仅展开 40,258 / 38,839 个不同完整状态，却有 159,742 / 161,161 次同一完整状态再次展开，实际展开位置仅 1,387 / 1,523 个。传送落点净空拒绝约 557 万 / 546 万次，段碰撞拒绝约 169 万 / 178 万次。H2 最近推进点的一个目标方向候选 `(6.5,79,26.5)→(14.5,81,35.5)` 在方块 `(6,79,27)` 被拒；这只是具体阻塞样本，不是不可达证明。
- 已证实：权重模式没有同一完整状态再次展开，但发现约 537.6 万 / 537.8 万个完整状态、约 121 万个位置，开放列表峰值 5,683,434 / 5,685,137。最近距离分别在第 15,000 / 14,000 个进度节点后不再改善；结束队首代价均约 8.2908。说明已发生广泛的低代价前沿膨胀，而非仅在单一完整状态循环。180 计数门槛被触发 47,089 / 47,084 次；正常无支撑的空中节点分类不等同于动作失败。
- 四组终止时没有缺图记录，实际分别读取 14 / 237 / 14 / 236 个区块文件，全部与 `map/hub` 的对应原始文件 SHA-256 相同。没有开放表耗尽、超时、JVM 资源耗尽或客户端路线执行。障碍、状态重开与前沿膨胀由日志证实；是否必须增加绕行候选、改进常规规划优先级或改变状态去重仍需后续验证，本轮没有据此改算法，也不能宣称地图上不存在路线。
- 验证：Gradle Build、18 项原有受控寻路检查、诊断回归通过。诊断回归检查精确 2,001 节点上限、每 1,000 节点事件、有限样本、预算与开放表耗尽/缺图分离，以及诊断开启前后结果一致。[verify_hub_log.py](tests/path-finding/verify_hub_log.py) 校验四组启终标记、每组 200 条单调进度、200,000 实际上限、有限拒绝样本、实际读文件与原图一致及未声称客户端执行。日志初轮发现分类查询归属不准确，修正后四组均已重新运行；本节和交付文件仅采用修正后的结果。
## 分层自动寻路复测

本轮将客户端公共入口切到分层规划。旧 20,000 节点入口仍可作为对照，先前的 Hub 200,000 节点日志单独保留。D1 的当前有效终点为 `(35,12,17)`。前文对旧坐标 `(35,12,175)` 的缺块结论仅属于历史试跑。

- [CorridorPlanner](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/planning/CorridorPlanner.java) 先验证直达及少量高度和侧移的净空候选，再搜索不带朝向的空间中心线。大落差使用双向前沿，Hub 使用单向前沿。每个中心线段验证 1×2×1 净空，局部候选仅在中心线 15 格内生成，通过地图碰撞复查。空间点查询按需缓存。
- 净空捷径只在中心线两端进行有界采样。连接必须覆盖接入、横越和再接入。常规模式比较同起止点的总时间，时间包含转向、CD 与确认、普通移动及落地动作。权重模式保留两种通过复查的方案，按总代价比较，并以估时处理平局。当前解来自有界候选集，全局最优性未证实。
- [PathPlanner.planGround](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/planning/PathPlanner.java) 供第三级使用，以横向曼哈顿代价作为 A* 下界。允许传送时尝试在同一行走走廊跳过连续步行段，起终净空、支撑和重新接入必须有效。关闭传送时直接走步行分支。
- [PathService](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/execution/PathService.java) 提供 `plan`、`start`、`execute`、`stage`、`snapshot` 和所有者票据取消。客户端准备与执行在客户端线程，规划在后台线程。 [ClientPathFinding](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/execution/ClientPathFinding.java) 接入既有上下文事件、共享虚拟键盘、视角输入及位置包旁路。外部调用者提供明确的本地地图身份，正式施放还需提供已确认装备距离和装备有效性检查。菜单的本地测试支持输入地图名并使用当前指向的方块。
- [TransmissionModel](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/model/TransmissionModel.java) 以实际脚底和施放角度计算连续向量。X/Z 使用 floor 加 0.5，Y 使用 Java Math.round。每一段保留量化前施放距离进行 12 格检查，无遮挡用例保留 18 格的 12+6。同一模型供规划和单人测试使用。每次本地技能动作为一条独立的 `/tp @s`，收到位置包才确认。虚拟键盘处理行走和跳跃，垂直下落使用原生重力。
- [PathSettings](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/PathSettings.java) 将杂项菜单的模式、传送启用、默认 20 ms CD、权重和检测开关放入账号 `general.json` 的 `pathFinding`。速度复测距离默认 80 格。初测在周围轴向检查安全平坦路段，或 10 格内可走到且有 9 格净空的跳测点。未测到时采用 4.3 格每秒及 1 格跳高。测量结果只放当前会话内存，拒绝位置修正样本并在上下文变化时清理。
- [CastGate](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/execution/CastGate.java) 使用单调时钟，视角、CD 与位置确认均就绪才施放。运行时检查实际射线和量化位置，在仍可到达相同量化点时试少量近邻角度。失败可等待落地后重规划，最多两次，第二次允许以常规模式兜底。累计距离仅统计已确认施放。规划有空中累计距离守卫和近地向下施放候选。Hub 实机记录到普通落地清零，约 6 格处施放的刷新分支仍待原生验证。

地图仍经 MappingPaths 和 MapRepository 读取 `run/config/ultimategarden/map`。本轮读取的文件与 `map/hub` 或 `map/deep-caverns` 的对应文件逐一哈希一致。D1 新终点区块为 `Deep Caverns 0 0.litematic`，起终点的支撑与净空检查通过。两种模式各使用 200,000 节点，双向空间前沿各跑到 180,000 节点后仍未连通，再消耗步行阶段的 20,000 节点。搜索碰到外围缺区块和低于地图扫描下界的探测位置。缺图位置没有充当空气，也没有证明大落差路线不可达。

| 场景与模式 | 规划策略 | 粗规划 + 局部展开 | 局部施放检查 | 规划用时 | 执行终点 |
|---|---|---:|---:|---:|---|
| H1 常规 | 原飞行走廊 | 54,093 + 58 | 7,607 | 0.762 秒 | (91.5,75,174.5) |
| H1 权重 | 原飞行走廊 | 54,093 + 85 | 11,287 | 0.828 秒 | (91.5,75,174.5) |
| H2 常规 | 原飞行走廊 | 10,745 + 76 | 12,610 | 0.647 秒 | (83.5,102,113.5) |
| H2 权重 | 原飞行走廊 | 10,745 + 181 | 32,751 | 0.852 秒 | (83.5,102,113.5) |
| D1 常规 | 预算耗尽 | 180,000 + 20,000 | 1 | 8.411 秒 | 未运行 |
| D1 权重 | 预算耗尽 | 180,000 + 20,000 | 0 | 7.551 秒 | 未运行 |

Hub 每组净空探测均未选出替换。常规 H1/H2 空间状态再次展开为 809/617 次，已采纳的局部策略分别为 0/0 次，旧完整状态再次展开为 159,742/161,161 次。权重 H1/H2 的队列峰值为 37,306/12,632，旧峰值为 5,683,434/5,685,137。空间状态与局部动作状态分别计数，因此重开次数的定义与旧图存在差异。

复测入口为 [corridor.ps1](tests/path-finding/corridor.ps1)，独立启动六个 JVM。单人客户端使用 [CorridorClientAudit](tests/path-finding/CorridorClientAudit.java) 从所用本地地图导入测试路线实际经过的区块并调用公共接口。合并日志是 [corridor-pathfinding.jsonl](build/path-audit/corridor-pathfinding.jsonl)。它保留各层结果、净空连接比较、实际文件、完整规划步骤及多个客户端试跑。每次客户端试跑使用 `clientTrial` 区分，`RUN_END` 为离线规划记录，`CLIENT_END` 为独立原生结果。

构建、配置、中英语言、Mapping、Instant Transmission、既有路径及新增净空估时与 CD 回归通过。隔离客户端禁用传送后通过脚步移动到 `(40.5005,89,24.4444)`，施放及传送位置确认次数为 0。85 ms 的额外诊断试跑记录到约 99.9 ms 的最小施放间隔，随后因碰撞重规划失败而结束。

正式验收使用 `clientTrial=5`，四组从绝对 Yaw/Pitch 0°/0° 和同一起点开始。H1 常规/权重用时 3.050/2.850 秒，各确认 20 次施放。H2 常规/权重用时 2.900/3.051 秒，各确认 17 次施放。74 次实际位置包坐标全部等于当次预测，均未重规划。默认 20 ms CD 下的最短实际间隔为 41.924 至 49.756 ms，受客户端 Tick、视角和位置确认限制。四组都在 `(41.3262,89,24.5388)` 附近站稳并将已经确认的 60 格累计清零。正式装备施放未在远端服务器实测。


### 2026-11-18 · 寻路启动、分段续算和原生视角复测

- **启动和坐标确认。** `start_pathfinding` 默认未绑定，使用快捷键现有分组、启停和冲突控制。在 [ClientPathFinding](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/execution/ClientPathFinding.java) 进入 `startHotkey()`。有未过期的坐标候选时消费它，否则打开 [PathChatInput](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/execution/PathChatInput.java) 的原版聊天框实例。Enter 在本地处理，Esc 不提交，也不保存聊天草稿。[TargetCoordinates](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/TargetCoordinates.java) 从清洗后文本提取一组独立的连续三元组，可处理带前后缀、负数和小数的文本。`PathChatObservationMixin` 在原版聊天接收可见消息时转交文本。有明确 XYZ 标签的外部片段优先解析，模块自己的提示不生成候选。
- **窗口和 HUD。** 候选使用单调时钟，默认有效 5,000 ms。新候选替换旧候选，过期或上下文切换时清除。候选未确认时不会换武器或规划。[PathFindingHud](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/ui/adapters/path_finding/PathFindingHud.java) 读取确认快照，通过现有 HUD 注册、布局和世界筛选显示坐标、剩余时间和进度条。该窗口纳入 HUD 的「仅运行时显示」判断。
- **快捷栏。** [TransmissionEquipment](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/execution/TransmissionEquipment.java) 读取同步物品的 SkyBlock ID、Tuner、Ultimate Wise 和武器 Intelligence。比较顺序为射程、法术等级、Intelligence、AOTV 优先、槽位从小到大。用虚拟键盘选择槽位并读回实际物品，施放前复核装备和潜行状态。禁用技能或没有可用武器时按纯步行配置规划。单人模拟入口独立接受距离 12，不要求真实物品。
- **分段预算。** `general.json` 的 `pathFinding` 持久化 `searchNodesPerSegment`、`maximumSearchSegments` 和 `coordinateWindowMs`。默认 `200K × 10`，各搜索阶段共享单段上限。`NodeBudget` 忽略中英文逗号，解析整数和 K/M/B/T 后缀，保存经溢出校验的长整数，展示能整除的最大单位。在菜单按 Enter 或移出输入框提交，非法值保留原设置。[SegmentedPlanner](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/planning/SegmentedPlanner.java) 仅在单段节点用尽时续算，从正向前驱链重建经过局部动作检查的路径，携带精确脚底、Yaw/Pitch、着地阶段、连续传送距离及权重代价。接缝按原脚底坐标检查。不可接续或段数耗尽保留诊断前缀，执行器只启动完整路线。权重结果记录实际选出路线的计划代价和未证明全局最优状态。
- **原生视角和下落。** `ShootoutAimFrameMixin` 将寻路接到同一客户端帧入口，调用 `ShootoutAimClock`、`ShootoutAimCorrection` 与 `NativeViewInput`。独立字段 `aimPeakSpeedDegPerSecond`、`aimCurveCurvatureDeg`、`aimUpdateRateHz`、`aimCompletionToleranceDeg`、`aimCorrectionRandomRatio` 的默认值分别为 300°/s、15°、20 Hz、0.50°、0.25。对齐后从实时位置与角度重新计算并检查原始线段和量化位置。可加速的垂直下落段保留 Yaw，尽早向下瞄准。使用 CD 和位置包逐次确认，下行转向免权重，实际落地后归正按空间夹角计算。单人模拟每次只发一条无旋转参数的 `/tp`。
- **复测日志。** `tests/path-finding/corridor.ps1 -Segmented` 和 `CorridorClientAudit` 写入 [segmented-pathfinding.jsonl](build/path-audit/segmented-pathfinding.jsonl)。保留上轮 [corridor-pathfinding.jsonl](build/path-audit/corridor-pathfinding.jsonl)。本轮分别记录 `SEGMENT_START/END`、可执行前缀和预算、`CAST` 的各相位 Y 与实际 CD、`POSITION_CONFIRMATION`、落地、重规划和客户端结果。地图由正常入口读取 `run/config/ultimategarden/map`，缺区块及扫描范围外分别保留诊断。

七次离线规划读取的文件与本机 `map` 对应文件逐一哈希一致。主矩阵使用 `L=12`、普通层、默认权重和 `200K × 10`。客户端复测使用隔离单人世界和无武器本地模拟。

| 场景 | 模式 | 规划状态／展开数 | 客户端结果（trial 23） |
|---|---|---|---|
| H1 | 常规 | 完整飞行走廊，54,151 | 4 次位置确认、2 次重规划，实位约 (35.5,84.937,11.5)，重验失败 |
| H1 | 权重 | 完整飞行走廊，54,178 | 4 次位置确认、2 次重规划，同一实位，重验失败 |
| H2 | 常规 | 完整飞行走廊，10,821 | 3 次位置确认、2 次重规划，实位约 (15.5,88.312,9.5)，重验失败 |
| H2 | 权重 | 完整飞行走廊，10,926 | 3 次位置确认、2 次重规划，同一实位，重验失败 |
| D1 | 常规 | 200,000 + 120,073，第二段无法接续 | 未启动，17 步诊断前缀至 (47.5,33,69.5) |
| D1 | 权重 | 200,000 + 120,041，第二段无法接续 | 未启动，18 步诊断前缀至 (45.5,28,71.5) |

D1 两个前缀的末端均在空中，携带 168 格连续传送距离。两组第二段都达到 120,000 个空间粗规划节点的阶段上限，局部检查和步行兜底未得到可接续动作，单段仍有预算。缺文件和扫描范围外的位置单列，不能据此确定整个目标不可达。

Hub 复测中，第二跳计划从 Y=80 开始，实际施放检查时已落到约 Y=78.407，量化落点 Y=83，与计划的 Y=85 不符且线段受阻，未发送该跳。重规划开始新动作前清除上一动作的失败状态后，四组均多次推进，后续仍出现同类障碍。垂直下落对照（trial 23）以 300°/s 瞄准，三次逐跳位置确认落至 (-3.5,78,-4.5)，两次施放间隔约 50 ms。降为 25°/s 的特殊用例（trial 24）在对齐前自然接地，零次施放。禁用技能的步行段（trial 25）完成，零次施放。

Gradle 构建及寻路、配置、语言和热键回归通过。本地原版聊天框验证了 Enter 拦截斜杠命令、Esc 取消及普通聊天恢复。外部消息触发的确认窗口能正确解析并按时过期。快捷栏数据和排序使用离线夹具，线上真实武器施放仍待验证。

同一垂直段的已对齐对照（trial 26）首跳瞄准耗时约 0.35 ms，确认四跳，量化 Y 依次为 108、96、84、78。末跳有效距离为 6 格。实际落点与各跳预测相同。该垂直段以着地为终点，未覆盖着地后紧接下一动作的归正。


### 2026-11-18 · 寻路施放时序、走廊预演与末段到达

- **施放时刻与脚底预测。** `function/path_finding/model/FlightTiming` 用 `ViewRotationPlanner` 的逐帧曲线、原版鼠标输入的 float 精度及 `ShootoutAimCorrection` 估算到位时刻。预测使用寻路自身的速度、曲度、频率、容差和修正抽样。按客户端结束 Tick 调度，将可并行的就绪条件取最大值，位置包确认后才开始下一跳的原生瞄准。剩余 CD 由 `CastGate` 的实际单调时钟提供。逐 Tick 先按当前速度推进脚底位置，再用 `q=0.9800000190734863` 与重力 0.08 更新竖直速度。理论确认点从零速度起算，实时修正读取玩家当前速度和接地状态。`MapTerrain` 与 `LivePathTerrain` 检查地面、顶棚和未知区域。
- **执行前预演。** `PathService` 先保留既有走廊规划结果和预算，然后在客户端线程采集最新脚底和视角，由后台 `CorridorRepair.rehearse()` 沿选定动作所形成的有序参考走廊做有界前向搜索。在每次施放前迭代预测时刻与角度，沿用 `TransmissionModel` 的量化规则，验证连续线段、量化落点与后续参考段的连接。每个通过的动作把修正状态交给下一步。预演无法恢复时走原两次重规划预算。D1 的初始粗规划和分段预算保持独立于预演预算。
- **实时推进。** `ClientPathFinding.NativeDriver` 从实际位置重验预演候选。已经开始的瞄准保持目标稳定，仅在候选失效时切换。偏离参考点仍需通过实际施放 Tick 的连续线段、量化位置、净空和走廊连接检查。位置包确认后用实际落点更新走廊进度，可跳过已接回的参考段。对有可靠支撑的临近落点可先等待接地，再尝试近距离步行或局部传送。原生转向与下降期间逐次确认继续有效。
- **末段。** 末段优先从实际位置找纯地面路径，可包含普通跳跃。无可用地面路径时再评估合法的直接传送或先跳后施放。最后一段的进度保留实际向目标靠近的空间，即使之前越过了末端投影。地面目标需要实际接地并通过原 0.75 格容差和连接检查，空中目标沿用半径和有效传送线段的判定。
- **审计入口。** `tests/path-finding/corridor.ps1 -Timed` 写入 [timed-corridor-pathfinding.jsonl](build/path-audit/timed-corridor-pathfinding.jsonl)，隔离单人客户端用 `CorridorClientAudit` 追加实跑结果。每跳记录原计划与预测脚底 Y、理论到位用时、预计和实际施放 Tick、实际 Y 及量化结果、走廊段、修正原因和位置包确认。末段动作写入 `TERMINAL_ARRIVAL`。上轮 [segmented-pathfinding.jsonl](build/path-audit/segmented-pathfinding.jsonl) 保留供对照。

走廊连接沿经验证的施放线段及其量化位移检查。普通下落的横移与竖直段也分别检查。局部换位可以暂时保持进度，连续换位最多尝试四个不同落脚点，避免在失败分支往返。

本轮在隔离单人客户端使用相同起始视角（Yaw/Pitch 为 0°/0°）、原生转向、12 格能力和 20 ms CD。运行目录读到的地图文件与本机 `map` 同名文件哈希一致。以下客户端结果来自 `clientTrial=6`，D1 使用独立的 `RUN_END` 分段规划结果。

| 场景 | 初始模式 | 规划展开 | 离线预演 | 实际终点或可执行前缀 | 位置确认／重规划 |
|---|---|---:|---|---|---:|
| H1 | 常规 | 54,151 | 通过，21 个选择 | 到达 (91.5,75,174.5)，末段传送 | 20／1 |
| H1 | 权重 | 54,178 | 通过，21 个选择 | 到达 (91.5,75,174.5)，末段传送 | 20／1 |
| H2 | 常规 | 10,821 | 通过，17 个选择 | 到达 (83.5,102,113.5)，末段下落 | 16／1 |
| H2 | 权重 | 10,926 | 通过，17 个选择 | 到达 (83.5,102,113.5)，末段下落。第二次重规划沿用常规兜底 | 19／2 |
| D1 | 常规 | 200,000 + 120,073 | 无完整路线 | 17 步前缀，末端 (47.5,33,69.5)，未执行 | — |
| D1 | 权重 | 200,000 + 120,041 | 无完整路线 | 18 步前缀，末端 (45.5,28,71.5)，未执行 | — |

D1 在第二段触及 120,000 个粗规划节点的阶段上限，局部和步行兜底均未验证可接续动作。Hub 四组都改用了连接到前方的新第二跳，预报施放 Y 与实测均为 81.535841，位置包确认落在 (21.5,85,5.5)。其理论瞄准时间为 100 ms，实测约 106–107 ms，三组预计和实际施放 Tick 相同，一组相差一 Tick。

定向回归复现从 Y=80 以零竖直速度起算的第 7、8 Tick 高度 78.407395 和 77.890447，并验证了从已有下落速度继续、地面与顶棚碰撞、换点连通、末段普通跳跃、跳起后辅助传送、未知地形及空中目标射线。独立垂直下落场景连续确认三跳后实际接地。游戏内的真实服务端技能未参与这些复测。

Gradle 离线构建、`tests/path-finding/run.ps1` 和新增 22 项定向检查通过。禁用传送的客户端试验（`clientTrial=7`）以步行结束，确认零次施放。既有垂直下降的逐跳预测与确认由 `clientTrial=8` 单独记录。


### 2026-11-18 · 500K × 4 寻路预算与保留前沿

- `PathTypes`、`PathSettings` 和公共 `PathService` 默认每轮 `500,000` 次展开，包含首轮最多 `4` 轮，合计 `2,000,000`。已有 JSON 显式设置继续按原值加载。客户端审计使用 `ug.corridor.nodes` 和 `ug.corridor.rounds` 显式配置本轮测试。
- `CorridorPlanner.advanceRound()` 为飞行空间、局部动作和步行图分配初始配额，使用同一个轮内展开计数。初始阶段额度结束后，余额以有限片段交给仍有非过期节点的队列。步行图缺块只结束该分支。`PathPlanner.GraphSearch` 保留步行队列和已访状态，飞行空间与局部动作各自保留队列和前驱。
- `SegmentedPlanner.plan()` 优先从新的已验证前缀继续，携带脚部坐标、视角、空中阶段、传送累计距离及代价。若无新前缀且已有前沿可用，下一轮沿原队列续算。`SEGMENT_START`、`BUDGET_TRANSFER`、`STAGE` 和 `SEGMENT_END` 记录起点、阶段配额、余额转用、接续及最后坐标。客户端重规划使用自身的 `planAttempt`，240 次预演上限仍单独统计。

JDK 25 离线构建与五组定向回归通过。Dwarven Mines 复测 9 条，8 条规划成功、5 条离线预演通过、5 条进入本地客户端、3 条实际到达。P1 → P2 第 4 轮复用了第 3 轮的队列，累积 2,000,000 次展开后用完预算，最后有效前缀到 `(103.5,172,163.5)`。
D1 常规两轮分别展开 500,000 和 192,022，找到完整路线，离线预演在 240 次上限停止。权重两轮分别展开 500,000 和 190,468，找到完整路线且离线预演通过，本地客户端在 240 次预演上限停止。两者结束规划时分别余 1,307,978 和 1,309,532 次展开。

新增离线入口 [BudgetRouteAudit.java](tests/path-finding/BudgetRouteAudit.java)。复测见 [报告](build/path-audit/route-budget-500k-report.md) 和 [JSONL](build/path-audit/route-budget-500k.jsonl)。上轮报告和日志保留。


### 2026-11-18 · CPU 候选并行和重规划诊断

- [CandidateWorkers](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/planning/CandidateWorkers.java) 只并行检查当前节点的传送候选。调度线程负责预算、优先队列、去重、接续和按原顺序合并。单轮 500,000、最多四轮的预算由各阶段共享。客户端默认最多 4 个工作者，可由 JVM 参数 `ug.path.workers` 设定 1–8，进程候选线程池总上限为 8。
- `MapTerrain` 按需捕获一次不可变区块快照。缓存 Mojang 已判定非动态形状的方块碰撞盒。动态形状仍按坐标查询。缺图仍为未知。未声明支持并发查询的地形沿用串行。
- `PathService` 记录提交、后台开始和结束、预演输入、最后实测脚底及独立计划终点。重规划和预演继续在规划线程执行。客户端只消费已经完成的任务。取消、断开、切换上下文、客户端停止均使旧任务失效。
- `SearchAudit` 定期记录阶段、轮次、展开量和队列。客户端审计分别采集 tick 间隔、玩家坐标、线程栈与 CPU 时间。`CorridorRepair` 继续使用 240 次默认预演配额，测试可单独提高。预演报告最后推进点、剩余候选和最后几何判断。

P2→P5 的停住在单线程原版复现为后台持续计算。4／8 工作者的实机重规划各展开 134,112 个节点，随后止于预演。固定起点离线耗时约 62.99／25.94／22.15 秒。P1→P2 同预算的三种配置均跑满 2,000,000 节点，未生成完整路线。D1 两种模式的动作序列和节点数跨线程一致，两组均未在客户端到达。P1→P4 和控制组 P2→P6 本地到达。

RTX 3070 单独完成 6,144 条实际地图扫掠对照，结果与 CPU 一致。GPU 测试为独立批次，正式路线继续使用 CPU。JDK 25 离线构建和五组定向回归通过。结果见 [本轮报告](build/path-audit/route-parallel-report.md) 和 [合并 JSONL](build/path-audit/route-parallel.jsonl)。


### 2026-11-18 · GPU 碰撞后端、自动满线程和两区块扩圈

- `PathSettings` 的性能字段由杂项 → 自动寻路 → 性能写入现有账号配置。`gpuAcceleration` 默认开启，`cpuWorkers=0` 表示自动，取 JVM 可用逻辑处理器数。手动选择上限同此。旧 JVM 显式 `ug.path.workers` 在配置缺失时继续使用，已有 JSON 选择优先。`PathService.submit()` 在排队时读取设置。
- [GpuCollision](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/planning/GpuCollision.java) 用专用 OpenCL 工作者处理同一节点的落点净空和两段扫掠，按原顺序归并。区块和碰撞盒缓冲由单次规划会话跨批复用。`GPU_REQUEST`、`GPU_INITIALIZED`、`GPU_BATCH`、`GPU_FALLBACK` 和 `GPU_SUMMARY` 区分启用、初始化、参与和回退。选定传送动作由 CPU 复查，实时施放仍使用原有 CPU 路径。
- [SearchRegion](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/planning/SearchRegion.java) 用起终点的 XZ 包络加 15 格形成首轮掩码。此前没有显式全局 XZ 掩码。首轮失败后将它按区块中心欧氏距离扩展 2 块，恢复挂起的空间、动作和步行候选，保留原队列及已验证前缀。高度规则和 500,000 × 4 展开预算沿用现有约定。掩码、展开、读图和反向前沿分别记录。

RTX 3070 实际处理了正式规划和客户端重规划候选。本轮默认配置 8 组有 7 组生成完整计划，1 组客户端实际到达。P1→P2 两种后端均用满 2,000,000 节点，扩圈后实际展开了 69 个新增区块，指定的 10 个区块都在首轮掩码内，未读取或展开。两条满线程对照的 GPU 端到端规划耗时均高于 CPU。

Java 25 离线构建和六组寻路回归通过。强制 GPU 初始化失败时确认回退到 CPU。结果见 [复测报告](build/path-audit/route-gpu-report.md) 和 [合并 JSONL](build/path-audit/route-gpu.jsonl)。上轮产物保留。



### 2026-11-18 · 路线 ESP、输入阻断和终点中继

- [PathRouteESP](src/client/java/com/kuangsanfeng/ultimategarden/client/function/esp/adapters/PathRouteESP.java) 将已通过预演的路线经现有 `ESPManager` 发布。当前动作目标使用较亮的屏幕中心引导线，实际动作与待执行节点按顺序连线并描边。规划线程沿走廊弯折和 15 格参考半径生成 2 格单元外壳，只绘制占用单元面对空邻居的边界。填充不透明度为 18%，使用地形深度测试。新预演通过时发布路线，动作推进或跳过参考点时更新引导，重规划、终止和上下文变化时清空快照。
- [PhysicalGameInput](src/client/java/com/kuangsanfeng/ultimategarden/client/platform/input/PhysicalGameInput.java) 分别记录显式 U 焦点释放和当前寻路输入阻断。寻路在预演通过后的首个正式动作派发时取得阻断，执行中重规划保留其所有权。首次进入阻断清空相关游戏按键及鼠标待处理输入。原版键盘分发按当前动作绑定筛选，包括九个热栏槽位。鼠标钩子阻断游戏按键、滚轮和原始视角位移。界面输入和虚拟输入沿用各自的来源。停掉一个所有者只释放对应阻断。
- [ClientPathFinding](src/client/java/com/kuangsanfeng/ultimategarden/client/function/path_finding/execution/ClientPathFinding.java) 在 Farm 已用的主动 GUI 输入入口接收背包、Esc、聊天和命令键。先取消当前票据、释放寻路输入、清掉旧 ESP，再由原版打开界面。待恢复目标仅在内存保留，绑定客户端世界、连接和可用的实例上下文。开始热键从当前脚部坐标、视角和运动状态重新规划，实际到达或上下文变化时清除目标。日志分别记录计划目标、停止脚部坐标、恢复脚部坐标与阻断状态。

JDK 25 离线构建、六组寻路回归和热键回归通过。本地 Fabric 中，P2→P6 经 E 停止、实际走动和转向、使用开始热键恢复，确认到达 `(-9.5,145,-19.5)`。最终尝试确认 28 次传送，途中一次自动重规划成功替换旧路线，到达后实测清除旧目标、ESP 和阻断。槽位 1 改绑 R 后，验证了两类阻断各自及重叠时对原版输入的筛选和虚拟热栏操作。原版聊天可继续输入数字，两个阻断解除后 R 与滚轮恢复。截图核对四层 ESP，边界检查未发现共用面。P2→P5 的重规划事件记录旧 ESP 清除和输入阻断保持，之后仍触及预演上限。八组规划复测有七组生成完整计划，两组通过离线预演并进入客户端，一组实际到达。见 [复测报告](build/path-audit/route-esp-relay-report.md) 和 [合并 JSONL](build/path-audit/route-esp-relay.jsonl)。

### 2026-11-19 · CORRIDOR 通道模式与临时接回导航

- **模式与配置。** `PathTypes.Mode.CORRIDOR` 是第三个可保存模式，和 NORMAL、WEIGHTED 并列。新建或缺字段的配置使用每轮 2,000,000 节点、2 轮，每次全局规划总上限 4,000,000。明确保存的预算仍按原字段加载。菜单和本地化新增 Corridor / 通道模式，CPU 保留当前固定分批。
- **通道执行。** `CorridorPlanner` 继续用既有空间和动作搜索生成经过几何验证的有序路线。CORRIDOR 分支和 ESP 使用 25 格，NORMAL、WEIGHTED 的原通道半径仍为 15 格。 `CorridorRepair` 把参考段及其下落、传送取整的折点作为连续中心线，按玩家脚底到三维线段的距离判断范围。推进日志输出弧长。候选优先比较当前朝向、终点方向和局部中心线趋势，保留低转向前进的选项。参考点参与投影和方向参考。预演逐步验证动作，施放前由实际脚底与视角重算射线、量化落点及后继可行性。
- **临时接回。** `CorridorRejoin` 从客户端采集的状态在规划线程搜索地面、跳跃、落下和经时序与碰撞验算的传送后继，限定 32 格、96 次展开、10 个动作。客户端确认进展期间使用实际地形复查普通动作和传送，落点还需有合法的后继。成功后 `PathService` 保留原走廊，按实际进度、脚底、速度和视角预演后半段，替换旧动作。局部失败才进入有界的全局回退，第二次全局重规划会检查上一份全局搜索的输入是否有实质变化。两种旧模式继续使用原有参考点候选，恢复入口同样使用临时搜索。
- **瞄准和观测。** `ViewRotationPlanner` 用最短 Yaw 与线性 Pitch 共用现有帧预算，`NativeViewInput` 与 `FlightTiming` 共用有边界的 Pitch 量化。 `CORRIDOR_REGION_ACTION`、`CORRIDOR_CAST_CONTINUATION`、`REJOIN_NAV_*` 和 `REPLAN_SUPPRESSED` 分别记录候选、实时施放后继、接回和去重。终态记录原生输入消费期间逐帧累计的 Yaw/Pitch 转动。


- **本次验证。** Java 25 离线构建与五项定向回归通过。两条 Dwarven Mines 本地客户端试验均在初次区域预演触限，角色留在起点。P2→P5 的第二次等价全局重规划被抑制。真实动作推进和临时接回仍待客户端验证。[验证报告](build/path-audit/corridor-mode-v1/corridor-mode-report.md)


### 2026-11-19 · CORRIDOR 冻结路线与实验性预演

- `FrozenCorridorReplay` 从首个成功规划段和配套初始预演事件构建冻结快照，验证有序动作链、弯折中心线、地图指纹与碰撞物理指纹。离线入口直接恢复 `CorridorRepair`，不调用全局规划。P1→P2 与 P2→P5 的原预演基线逐项复现。
- `ExperimentalCorridorRehearsal` 复用 CORRIDOR 的预测及碰撞检查，以 5/8/15/25 格保留分层前沿，先用精确静态净空筛选，再验证完整射线。缓存和精确去重绑定单次只读地图快照与执行状态。现有正式预演保持原策略。
- `RollingCorridorReplay` 使用 96 格方向参考和 5／8／12 动作缓冲，传送用原模型重验，每条路线共用一个展开预算。普通地面输入闭环仍未实现，遇到不支持的动作时停止。全局规划、生产线程与接回执行入口未变。[八次对照与限制](build/path-audit/corridor-rehearsal-optimization/report.md)

### 2026-11-19 · CORRIDOR Phase 2 离线混合动作实验

- `OfflineMotionDriver` 为现有 FlightTiming 提供逐 Tick 输入，复用瞄准、重力、碰撞和传送量化。独立冻结首步 WALK 与 Dwarven Mines 的 Y=196.5 半砖落地通过；水平输入和台阶仍属实验模型。
- `MixedRollingReplay` 新增完整状态校验、尾部补充、独立终点窗口及调用原接回规划的实验路径。CP2 的跃起传送测试断言与原量化落点冲突，依赖的整线滚动与接回验收按停止规则跳过。未启用到客户端。
- `CorridorPhase2Audit` 和本地检查点脚本记录冻结基线、物理和终点断言，以及 CACHE_ONLY、DEDUP_ONLY 分离对照。原路线快照和生产代码仍沿用现有版本。[Phase 2 报告](build/path-audit/corridor-phase2/report.md)


### CORRIDOR Phase 2 续跑 · 离线运动与接回验证

- `OfflineMotionDriver` 记录施放帧的实际运动状态、原始射线终点、量化落点和确认后的接地状态。`CorridorPhase2Audit` 把 M11 拆为有支撑、无支撑两种空中施放场景，并逐例写出 M01–M16 的结果，合计 17 项通过。
- `MixedRollingReplay` 在派发前检查完整起始状态和进度。空中缓冲不足且局部恢复无法继续时，先推进一帧无输入物理，再保存终态。短场景覆盖冻结 P1 的实际步行交接及前瞻补充、过期状态拦截、接回后重建缓冲并派发正常动作，以及 Y=196.5 的原始终点判定。真实异步发布和 LocalPlayer 逐 Tick 一致性待验证。
- `CorridorPhase2Audit` 的续跑入口支持 `validate`、`physics`、`short C1…C4` 和 `end-to-end <route> ROLLING_NO_CACHE`，用 `ug.phase2.output` 指定续跑目录。两条整线均在耗尽两次局部恢复机会后止于 `GLOBAL_REPLAN_REQUIRED`。离线入口未进入全局规划，没有启动客户端。[续跑摘要](build/path-audit/corridor-phase2-resume/summary.json)


### CORRIDOR 滚动补充定向诊断

- 离线入口 `CorridorPhase2Audit refill-diagnosis <route>` 从现有冻结输入回放到指定窗口。按 `windowId` 和 `rejoinId` 记录实际状态、已验证前缀及预测尾、搜索退出分支和开放节点数、逐 Tick 复核、发布裁剪及接回交接。
- 两次前缀回放复现 W1–W3。三窗的新动作均通过逐 Tick 复核，发布前因没有稳定终态被裁掉。搜索只报告退出时的开放节点数，现有接口无法续用前沿。[本轮摘要](build/path-audit/corridor-refill-diagnosis/summary.json)


### CORRIDOR 实验性补充的候选接受

- `MixedRollingReplay` 将原有逐 Tick 复核、链尾稳定裁剪和发布门槛作为实验性窗口搜索的候选接受条件。候选复核只构建局部过渡；未发布的候选节点仍可扩展，同一次搜索沿用队列、访问记录和剩余额度。通过后一次提交，派发前继续校验实际状态。
- `CorridorPhase2Audit stable-tail <w1|w2|w3>` 恢复已保存的窗口，并用相同输入对照原策略。诊断每窗限额为首次候选后最多展开 128 个节点、共复核最多 8 条链。三窗均触及复核上限；C1 与脚本化候选的平地离线场景通过，合成场景验证了拒绝首链后继续搜索、发布稳定链并派发首个动作。[本轮摘要](build/path-audit/corridor-refill-stable-tail/summary.json)


### CORRIDOR W3 有效前缀与落地探针

- 实验性补充按窗口剩余动作容量限制逐 Tick 复核，键包含实际动作参数与复合动作边界。同一窗口只复核一次相同的有效前缀。已占满容量且无法发布的分支无需继续追加，因为后继无法改变进入复核的动作。
- `CorridorPhase2Audit w3-effective-prefix` 直接恢复历史快照。W3 复核 8 条不同候选，仍无可发布链；第 8 和第 11 步的原始复核终态分别使用既有局部 24 Tick 时限完成自然下落探针，均未触及支撑。[结果摘要](build/path-audit/corridor-refill-effective-prefix/summary.json)


### NORMAL / WEIGHTED 多前沿寻路与施放时序

- `RouteCastTiming.castTick` 将现有逐帧瞄准估计对齐到客户端 Tick，正常施放增加一拍，高度修正时省去这一拍，CD、确认和瞄准门槛并行取最大值。`CorridorRepair` 预演和 `ClientPathFinding.NativeDriver` 使用同一门槛，实际施放仍须通过原生瞄准、射线和量化落点复核。
- NORMAL／WEIGHTED 维护已确认动作的原线路线进度与高度差，从最新实测状态重新求解偏移候选。接续校验通过后恢复正常门槛，局部候选受阻时可转入原接回搜索。WEIGHTED 全局重规划继续沿用保存权重。
- `CorridorPlanner` 的动作细化保留不同走法的优先队列。可并行的地图查询通过固定候选池分配分支切片，预留并汇总同轮预算。每个分支复用父动作链，空闲工作者可接收新分叉。候选工作者内部的几何批量同步执行，防止等待同一线程池。粗搜仍采用原队列，完整运动状态在定时预演阶段去重。已发现的完成路线在最快估时的 1.15 倍内比较瓶颈、连续高风险空中动作和累计风险。
- `CorridorRepair` 可以为连续上升转近平飞提出一次辅助上升组合，复核顶棚、真实量化落点和后续瞬移窗口。稳定贴地候选可优先于窄窗口的空中方案。全局几何规划完成后的定时预演与接回仍通过现有 `PathService` 流程提交。
- Java 25 离线 Gradle Build 通过。定向回归覆盖逐步 Pitch 和 Yaw 量化、N／N+1 门槛、辅助上升与顶棚阻断，以及共享预算下的并行分支。六项隔离客户端审计的独立终态与证据见[路线结果](build/path-audit/normal-weighted-robust-routes/routes.md)和[结构化摘要](build/path-audit/normal-weighted-robust-routes/summary.json)。


- **客户端结果。** P1→P2 两档各耗尽 400 万展开。P2→P5 两档各有两次预演触限，同状态重规划随后被抑制。P2→P6 NORMAL 在确认 4 次瞬移后失败，WEIGHTED 确认 27 次并实际到达 `(-9.5,145,-19.5)`。WEIGHTED 观察到跨 9 跳的高度修正和接回后恢复一拍。六次均未选用辅助上升。实跑后独立时序适配并通过定向复验，历史冻结物理指纹一致。
- **当前验证边界。** 15% 时间窗基于已经发现的候选，精细预演失败仍走现有接回或重规划流程。跨阶段恢复几何前沿，以及辅助上升的两跳耗时纳入全局路线比较，仍未接入。


### P2→P6 NORMAL / WEIGHTED 定向诊断

- 隔离审计开关 `ug.corridor.targetedDiagnosis` 记录第 2 跳的包应用、物理前后、原生视角和施放门槛，并在 NORMAL 第 5 跳进入恢复或 WEIGHTED 第 2 跳确认后停止。候选检查只写诊断事件。Java 25 构建与定向回归通过。
- 两个短前缀均于第 6 Tick 派发第 2 跳，确认了预演与运行时采样间已有一次重力更新。历史 NORMAL 的第 7 Tick 原因仍缺门槛快照。历史第 5 跳离线模型复现原候选被拒，并验证到 `(101.5,198,160.5)` 的一条替代接续。[诊断摘要](build/path-audit/p2-p6-targeted-diagnosis/summary.md)


### NORMAL / WEIGHTED 确认交接、候选回退与故障记录

- `RouteCastTiming` 区分包应用和确认消费。普通／权重预演从预测的确认消费状态推进下一跳，实际复核使用已经消费确认的实测状态。物理交接与 N／N+1 瞄准门槛分开。
- `LocalCandidateFallback` 在施放前拒绝后，从最新实测状态按原候选顺序重选，累计复核上限为 7。没有可用替代时使用既有接回流程。位置待确认时保留原动作。
- `PathFailureTrace` 默认在 `logs/path-finding/failures.jsonl` 异步导出阻断、晚发、替换、局部恢复和失败终止的关联窗口。`ClientTerrain` 在原碰撞检查中提供命中的方块。两次短前缀和离线回归记录了晚发后确认、实际射线拒绝后替换，并复现历史建模替代候选。实跑后补充替代结果的关联导出，定向夹具通过。[报告与日志](build/path-audit/p2-p6-timing-and-fallback/summary.md)

### 寻路发布与调试隔离

- 本地 `test/path-finder-debug` 保存发布前的客户端审计入口、隔离启动器和短窗口夹具。发布 source set 只包含正常物品使用和服务器位置确认。主树删除本地寻路测试按钮与 `CorridorClientAudit`。保留合法的农场业务命令。
- `PathSettings` 为缺失配置提供 60 Hz 原生瞄准刷新和 GPU 关闭。客户端与预演读取同一瞄准配置，物理 Tick 保持独立。`DetectedLocalMap` 用当前连接验证过的世界标识匹配本地 `map.json` 和区块索引，切换连接、世界或区域使会话和显示失效。识别结果在寻路设置只读展示。
- `PathLatency` 用与 Zombie Shootout 相同的原生 Ping/Pong 传输，独立匹配请求和连接。客户端消费工作线程报告的动作计算阶段，从实际执行起点每三秒检查周期采样。任务结束时丢弃尚未返回的结果。
- `InputRequests` 按来源合并各输入通道。路径会话在采样起点前申请输入阻断并在终态归还。U 持有鼠标位移与捕获释放请求。农场沿用原生逻辑抓取与桌面指针释放的协调，GUI、现有宏停止入口和程序内部模拟输入保留原有事件路径。
- `PathSearchESP` 提供按尝试有界的节点和动作快照。粗搜从实际展开位置取样，动作层使用已预演的瞬移起点和量化落点、实时拒绝点和已执行移动轨迹。通道壳层独立读取模式限制。HUD 读取会话的阶段、预算、动作、恢复、输入及延迟。没有成本统计或现场观测的数据保持空白。
- 开发者选项中的 `Enable Pathfinding Log` 默认关闭，开启后随下一次寻路会话创建记录器。日志写入运行目录的 `logs/path-finding/failures.jsonl`。`PathFailureTrace` 用动作、候选、确认与事件序号串起晚发、实时复核、改选及最终处置，按增量交给异步 JSONL 写入器。瞄准证据包含外层阈值、修正阈值和最近一次原生转动结果。
- 账号配置页末尾的恢复功能使用 `MappingConfirmButton` 二次确认交互和账号修订号校验。正式配置编码器生成所选文件的默认内容，写入成功后重新加载该文件。
- Java 25 离线发布构建和 `PathReleaseRegression` 覆盖默认值、地图匹配、输入所有权、延迟时钟、ESP、HUD、单文件恢复和故障日志夹具。鼠标捕获与 Mixin 的真实回调、服务器地图识别及实测延迟由后续现场确认。

### 寻路任务所有权、末端连接与独立日志

- `ClientPathFinding` 分开保存候选坐标、已提交坐标、活动票据和显式 GUI 暂停目标。确认新坐标时清空旧恢复目标。结果只由对应会话提交，终态清理动作、ESP、输入请求、延迟和规划结果。HUD 可保留最近处置文本。
- 绑定的寻路热键在原生键鼠事件入口处理。活动会话收到按下事件时立即取消自己的票据，按住产生的重复事件不会重新启动寻路。已发出的瞬移由 `PathPositionReceipt` 按世界、连接和会话隔离，停止后匹配的迟到更新只解除旧确认屏障。
- `CorridorRepair.terminalDecision` 在终点附近先用现有局部地形和运动模型验证步行、跳跃或自然下落，保留半砖脚底高度。普通移动失败时仅选取通过既有传送模型且具备普通后继的末端候选，派发前复核实时落点的后继。日志区分普通无路、地图缺失、预算触限和候选处置。
- 日志启用时，`PathTaskLog` 在当前实例的 `logs/ultimategarden/` 为每个独立任务创建 JSONL。本地时间精确到毫秒，文件名包含地图和目标，重名采用独占创建并附短 runId。首末行为 `TASK_START` 和 `TASK_END`。重规划、接回及故障证据共用本次任务的文件，历史共享文件照常保留。
- Java 25 定向夹具为 `PathTaskLifecycleRegression`。覆盖目标切换、后台取消、迟到更新隔离、末端动作及独立日志。测试含合成地形和输入决策，未启动客户端或连接服务器。

### 寻路请求分流

- `PathRequest` 携带来源、操作、坐标及用途。手动、聊天确认和功能调用通过 `START_NEW` 启动新票据。即使坐标相同，也按新任务生命周期初始化。运行中的寻路热键只停止，暂停路线需通过寻路菜单的显式恢复按钮提交 `RESUME_EXPLICIT`。
- `PathService` 在采样新起点前作废并清理旧票据。`NativeDriver` 持有本任务的测量和确认状态。工作结果按尝试代数校验；`REJOIN_CURRENT` 和 `REPLAN_CURRENT` 校验任务票据，使用恢复起点并保留原终点。
- 单路线 JSONL 增加 `PATH_REQUEST`、`TASK_INITIALIZED`、`FIRST_ACTION_HANDOFF` 与 `FIRST_ACTION_DISPATCH`。请求记录包含原目标、新旧票据和 runId，以及实际处置。任务内恢复沿用原文件。
