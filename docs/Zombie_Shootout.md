# Zombie Shootout 实现与验证

基线为当前 master。仅新增正式 Macro 所需组件；旧测试分支的采集器、Num2、Tag 日志不作为运行依赖。此文记录本地代码，服务器接受输入、命中率及灯奖励均为 **Not Verified In-Game**。

## 文件与入口

| 文件（src/client/java/com/kuangsanfeng/ultimategarden/client 下） | 关键入口 |
|---|---|
| macro/zombie_shoot/ZombieShootoutController.java | register、start、stop、tick、observe、select、verify、collisionCheck |
| macro/zombie_shoot/ShootoutModel.java | load、Path.at/project/tangent、match、reflect、velocity/arrow、solve |
| macro/zombie_shoot/ShootoutSpeed.java | observe；每个 Target 独立实例 |
| macro/zombie_shoot/ShootoutPolicy.java | ORDER、metadata：固定优先级、积分/生命周期 |
| macro/zombie_shoot/ShootoutShotClock.java | ready、remainingTicks、fired；System.nanoTime deadline |
| macro/zombie_shoot/ShootoutMouseInput.java | aim、expo、step、click、cancel、dispatching |
| config/ZombieShootoutConfig.java | Setting、set、load、toJson |
| mixin/ShootoutMouseAccessor.java | 原生 turnPlayer/onButton invoker，accumulatedDX/DY accessor |
| config/ConfigManager.java | ZOMBIE_SHOOTOUT 账号模块及现有备份/schema/原子写入 |
| Ultimate_GardenClient.java | 独立注册、外部输入 owner 与 Farm 启动 guard supplier |
| window/WindowsInputInterceptor.java | 默认 false 的外部输入 owner，不修改 WindowsState |
| macro/farm/FarmController.java | 仅新增默认 false 的输入所有权 supplier 与 startMacro 单一入口保护 |
| mixin/WindowsMouseMixin.java | 来源作用域绕过、虚拟右键不执行 grabMouse |
| mixin/WindowsInputMixin.java | 运行时放行当前原生 Stop 键，即使被重绑定至移动键 |
| hotkey/MacroKeyBindings.java | 新增 None 默认绑定，独立 KeyEdge，菜单/文本门控 |
| ui/screen/UltimateGardenScreen.java | Misc → Carnival → Zombie Shootout，四级控件 |
| ui/language/LanguageKeys.java | Hotkeys 页映射 |

注册资源：`src/client/resources/ultimate_garden.client.mixins.json`。中英文名称/Hint 与原生绑定资源：`src/main/resources/assets/ultimate_garden/lang/{en_us,zh_cn}.json`。

## 静态模型与数值方法

资源 `src/client/resources/ultimategarden/zombie_shoot_theoretical_models.json` 保持原样，并打入 client 资源。首次启动加载一次；严格校验 A/B 顺序和 141/151 点、递增弧长、八类速度及有限参数。起点是本次启动实际脚部位置，坐标轴平行世界轴，不根据玩家 yaw 旋转。

路径是实际点列的连续分段线性插值。对每条线段作最近点投影；最佳残差 ≤1 block，双路径残差差 ≥0.35 才接受。阈值为当前实现的保守几何门槛，不是已验证的游戏规则。运动方向使用连续 Δs；反射使用非负模，保留实际位置相对中心线的三维偏移。观测端点尚未确认为实际掉头点。

每次启动须先有一个匹配路径的僵尸积累至少三个有效运动样本、速度估计可靠，才确认当前 Session 的模型空间对应关系；此前即使发现点亮的灯也不射击，状态为 MODEL_SCENE_NOT_MATCHED。该确认仅在当前固定原点/世界的运行会话有效，重启清除；不是对服务器小游戏状态的协议级证明。

八类速度只读取 JSON。Helmet/Chestplate 同材质和原生 isBaby 决定类型，腿/靴不参与；钻石幼年速度仍是 JSON 中注明的估算值。窗口默认 6 Tick；成熟前默认值作为一个样本，成熟后仅用窗口内实际 |Δs|。方向独立取最新有效位移符号，掉头前后不互相抵消速度大小。初始 speedMagnitude 为默认值，方向 UNKNOWN，不发射。实际零速保留，异常/间断重建窗口；成熟后至少 max(2,窗口/2) 个实际样本才可靠。

成熟时间始终按该实体首次观察 Tick 计算；缺 Tick 或异常只清实际样本，不重置首次时间，因此默认速度不会在成熟后被重新混入。

默认速度退出状态也会锁存；用户随后扩大窗口时仍不重新引入默认值。最小一 Tick 窗口可使用一个有效实际样本；更大窗口的可靠性门槛为 min(窗口,max(2,窗口/2))。

V0=a+b·sin²(2yaw)，drag/gravity 由 JSON 读取。`arrow` 与“position+=velocity → velocity*=drag → vy-=gravity”的整数 Tick 顺序等价。求根在正飞行时间 .001–40 Tick 内粗扫、二分，选择首次符号变化的根；不保证捕获无符号变化的切触根。每次求解后以原生视角再次校验有效弹道。

玩家列表 PlayerInfo.getLatency 为原生 RTT，L=RTT/50，不再加 RTT/2；无主动探测。总目标预测时间为 max(剩余CD,预计瞄准时间)+1 Tick原生输入消费+L+飞行时间。预计瞄准时间是角差/峰值速度的近似，Expo 实际耗时可能更长；最终仍须实际对齐后重算才能发射。

## 输入与状态机

STOPPED/ERROR → start → WAITING_FOR_WORLD → SELECTING_TARGET → AIMING → READY_TO_FIRE → FIRING → 下一目标；菜单或 Overlay 打开时等待，不执行瞄准/射击。运行值不保存，停止不自动恢复。

`ShootoutMouseInput` 写入相对鼠标增量后调用原生 MouseHandler.turnPlayer，并恢复原累积字段。原生 sensitivity、invert X/Y、float 转换和 Pitch clamp 继续生效；没有玩家角度 setter、自定义视角包或额外网络探测。输入使用 double 增量，没有整数像素截断；下次更新使用实际角差继续修正。Smooth Camera/Scoping 被拒绝，以避免假设错误的灵敏度模型。

Expo Out 按同一动作的进度推进；目标更新不重置进度。Snap Angle 内线性对齐；Expo 完成后仍以限幅线性步跟踪后续目标变化。二维角差合并限幅，使用单调时间（每步最多 .05 秒）限制 catch-up Tick 的速度；原生 float 角度有数值量化误差。Snap Angle 不是射击误差。实际射击容差按命中体积/距离计算，限制在 .03–.2 度。

右键通过原生 onButton Press/Release，正常 KeyMapping click 在后续原生 Tick 消费；source scope 防止被 Mod 拦截或 grabMouse 改变 Focus。若 Use 已被玩家持有则不提交；必须将 Use 绑定在原生右键。未消费的 Macro click 在 Stop/GUI 时撤销。没有定时异步发射任务。

输入拦截只是共享服务的外部 owner，不写 WindowsState.mouseReleased/inputIntercepting，停止后 owner 自然失效。普通 GUI 仍接收输入，手动 Focus 设置不被覆盖。启动要求移动键释放；不抢占 Farm 已有运行/恢复意图或命令任务，Farm startMacro 也拒绝独立 owner 已持有输入时启动。

Shootout 运行时的移动键拦截使用 Options 实际重绑定（含 Jump/Shift/Sprint）；Stop 和菜单键优先放行。鼠标绑定的 Stop 在拦截游戏鼠标前识别。原有 Farm 的键盘拦截规则未改。启动也拒绝玩家已持有 Attack/Use，避免抢占并释放玩家输入。

三维位移 >1 block、死亡/玩家失效、世界/账号变化、断线、真实窗口失焦或 Farm 业务占用会停止。退出清空目标、待验证射击和未消费输入，不移动回起点。现有 Farm / Pest / ESP / HUD 逻辑保留。

## 目标、遮挡与射击反馈

优先级严格为灯、Diamond Baby/Adult、Gold Baby/Adult、Iron Baby/Adult、Leather Baby/Adult；同级首次观察顺序稳定。已锁定周期不因新灯打断。灯发现是每 Tick 最多 4096 个候选位置的分批球形扫描；不是瞬间全覆盖，发现延迟取决于扫描游标与区块加载。已知灯在选择时核对 LIT，发射后同一持续点亮状态不会重复创建任务。

灯区块暂时卸载时保留已处理标记，但不射击；只有实际观察到熄灭或方块改变才移除。重新加载仍点亮的同一位置不会因此重复排队。

成年头部点为真实高度顶部向下 18%；幼年体使用可调顶部偏移，均约束在 AABB 内。横向采用未来局部运动方向上的有效宽度，在中心前移20%；静止时中心。灯取实际碰撞形状的朝向观察者表面内点。

只有目标附近 [-3,3]³ 的已加载真实碰撞盒参与环境校验。BlockGetter 邻居依赖超出局部范围或未加载即 Unknown；不会当 Air。整数 Tick 弹道段与未来 AABB、扩张原生箭矢半径的局部方块相交。该检查是离散近似，不是服务器碰撞证明；其他中途环境按用户场景假设为空。

成功提交输入后乐观排除目标，CD 与下一轮瞄准并行。到预计命中+完整RTT+1 Tick输入消费+4 Tick反馈余量后，若同一对象/UUID 仍在当 Tick 有效观测且存活，安排一次补刀。这里只能证明客户端仍观察到存活，不能证明箭矢必然打空；消失/自然超时/卸载不伪称命中。补刀在当前周期结束后、灯之后选择，补刀后不再重试。

## 配置

账号下 `zombie_shootout.json`（schemaVersion=1），与账号复制/备份/未来版本保护集成；正在运行时不允许向当前账号复制配置。未知字段保留，`enabled` 不保存。

| 字段 | 默认 | 校验范围 |
|---|---:|---|
| aimPeakSpeedDegPerSecond | 500 | 1–2000 °/s |
| snapAngleDeg | 15 | 0–180° |
| speedWindowsSeconds 各八类 | .30 | .05–3秒，ceil(seconds×20) |
| shotCooldownSeconds | .55 | .5–10秒 |
| shotJitterMinSeconds / MaxSeconds | 0 / .10 | 0–10秒，Min≤Max |
| babyAimTopOffsetBlocks | .10 | .001–1 block，运行时再限制至实际高度 |

菜单值即时应用并按现有机制保存；无效输入保留旧值、显示现有失败反馈。CD 使用单调 deadline，每次射击只抽一次均匀 Jitter。设置修改用于后续抽样，不追溯修改已安排的当前 CD。

## 验证边界

`tests/shootout/run.ps1` 使用真实模型资源及生产数值/配置类，覆盖投影、反射、符号约定、离散弹道等价、不同 Ping/CD/方向/类型的截击回代、速度窗口与静止、异常分段、固定优先级、Expo 和角速度步长、CD 一次抽样、配置持久化与未知字段。原生鼠标回调与服务器状态不在这些数值测试内。

同一脚本还以模拟的原生鼠标消费者执行生产 ShootoutMouseInput，验证灵敏度/双轴反转、总角度限幅、增量字段恢复、完整右键周期、玩家已持有输入不被释放、释放异常与待消费 click 的清理。WiringAudit.py 静态检查既有绑定、生命周期、资源字节原样打包及操作边界；这些均不能替代真实 Mixin 运行。

既有配置/账号/语言、Farm 路线、GUI、Focus 测试作为回归；其中窗口和原生输入部分使用桩。Gradle build 通过不等于 Mixin 已在真实游戏运行。

**待实机验证：** Native Mixin 注入与反转/灵敏度手感、真实输入拦截、停止/失焦/菜单的输入清理、真实灯发现与奖励反馈、服务器武器右键响应、命中率/补刀、原点站位和曲线匹配、观察端点反射、箭矢原生尺寸与该武器碰撞的适配、所有 Tick 性能。没有测试这些场景时不得称为实测成功。

本次自动验证：Gradle Build 成功；3,076 项数值/配置检查、59 项生产鼠标逻辑与模拟原生消费者检查、49 项静态接线与打包审查通过。既有配置/账号/语言、Route、GUI、Focus 回归通过；账号符号链接检查因环境权限跳过。以上不包含真实 Hypixel 游戏测试。
