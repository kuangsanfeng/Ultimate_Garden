
# Structure Shift 集成验证与手动验收

基线 master：48fa5b9b711003ad00148336c8d44f6bb35b43ba。独立工作区：G:/instrument/JetBrains/IdeaProjects/ultimate_garden-structure_shift。集成分支：test/structure_shift。任务分支保留在 test/structure_shift_tasks/；不推送，不合并 master。最终提交号见交付消息或本分支 git rev-parse HEAD。

## 分支与逐项验证

下表提交均在 detached HEAD 上完成验证后建立分支；后续任务从上一项提交继续。每项均已通过 Gradle Build。12项以编号顺序 --no-ff 合并，逐次核对树与任务提交一致。

| 第三级分支 | 已验证提交 | 自动验证 |
|---|---|---|
| test/structure_shift_tasks/01-client-context | 6a868e63d48cbf1949ca21f84b5116a0fb723af1 | Build、context、现有9类回归：通过 |
| test/structure_shift_tasks/02-rtt | fd89eb73c947ff6254a4f48a73730af809b2ae22 | Build、RttServiceRegression、Shootout：通过 |
| test/structure_shift_tasks/03-world-query | 5cfdf65c3f5caf2ad9230ffd02e8c5afa0803c03 | Build、SpatialRegression、Shootout：通过 |
| test/structure_shift_tasks/04-entity-tracking | 9c0efc6138443260a85af519235421a1716bfe8d | Build、EntityRegression、Shootout：通过 |
| test/structure_shift_tasks/05-sample-window | 15ad4dbdbb9c3ca8e4d62e7da7605d36a1b5dbdf | Build、WindowRegression、Shootout：通过 |
| test/structure_shift_tasks/06-log | 123add8afd3a58fab3be69fd18adc8a085721899 | Build、LogIsolationRegression、Shootout：通过 |
| test/structure_shift_tasks/07-developer-collection | b2c90a0452508f39de04e698855c513840a6e90b | Build、CollectionRegression、Shootout：通过 |
| test/structure_shift_tasks/08-view-input | 8b2ba0dd15e39df189a4df1b6de8869967437dd1 | Build、Tanh/View/Input/Hz/诊断回归：通过 |
| test/structure_shift_tasks/09-gui-session | c34cfb6a9aca5acb7cef609e4c885d826f6d0ff2 | Build、GuiWait、gui/route/menu-input/Shootout：通过 |
| test/structure_shift_tasks/10-feature-registry | be48d268ffbbffcf331332000edc4fcd3cb69e05 | Build、FeatureRegistryRegression、Shootout：通过 |
| test/structure_shift_tasks/11-esp | 42e9aa8e3fc0a8318b600affd0540e7fc711d05d | Build、esp真实GPU、Shootout：通过 |
| test/structure_shift_tasks/12-ui | 018333fe41236807203d2e3c4e9d6f1fbbde8bc3 | Build、context/gui/config/hud-adjust/Shootout：通过 |


## 总调试

- 合并端点：dcb4fb6ef01a2107e88fc2bf4c108af5a382d9f7；其后只有总调试生命周期收尾及交付文档提交。
- Java 25，Gradle clean build --offline 成功；构建中的 test NO-SOURCE 不代表独立回归已执行。
- context、config、gui、route、menu-input、focus、hints、hud-adjust、esp、shootout：10个独立run.ps1均返回0。相关输出位于build/integration-*.txt，公共及Shootout收尾修正重跑输出单独保留。
- 关键证据：69700项频率调度、5504项原生鼠标消费者模拟、208项20/60Hz诊断关联、4242项真实GPU tracer检查。检查计数来自脚本输出，不等同于游戏命中率。
- 总调试修正：公共上下文桥接在断线/退出的finally释放旧世界引用；CollectionSession.submit/stop互斥，使停止等待在途提交结束，之后拒收。新增并发栅栏测试通过；公共和完整Shootout回归重跑通过。
- 静态核对：原模型、语言/资源及Gradle文件保持基线内容；公共目录与业务适配方向分开；文档链接检查和git diff --check通过。
- 未删除兼容入口：旧Shootout、GUI、HUD等类型保留外观。它们不另建驱动器、采集会话或写线程。
- 日志公共层独立于Macro；文件目录、类别、START/END及Session摘要由日志适配器承接。断线/Stop只结束所属批次，已接受记录后台排空。

## 客户端观察与边界

独立worktree执行runClient --offline，Mod/Fabric/Mixin、音频与渲染成功加载。通过computer-use观察到独立开发客户端进入本地单人世界，Farm与Zombie Shootout HUD显示停止状态。另有用户正在运行的第三方多人客户端，未替换其Mod，也未对其发送操作。

开发窗口出现用户输入后停止争抢输入；本次没有把菜单交互、射击或Hypixel反馈写成实测通过。开发客户端保留给用户继续验收。服务器RTT、实际攻击/灯命中、20/60Hz故障是否重现、在线切账号/切世界及异常输入恢复均为 Not Verified In-Game。

## 最终调用关系

1. 客户端入口组装公共服务；ClientContextEvents转发上下文，业务owner清自己的状态。账号配置前后顺序保留。
2. MacroManager仍只驱动Farm；Shootout仍使用自己的Client Tick及原有帧入口。FeatureRegistry只注册、转发生命周期和查询快照，不驱动Tick/帧。
3. 业务适配器提供输入终点/参数；input公共层规划并调用原生消费，before → submitted → consumed与诊断时序保留。Focus、阻断与所有权保持原机制。
4. Shootout决定RTT探测启停及间隔，network公共服务负责原生包配对和连接状态；补偿仍由Shootout按RTT/50计算。
5. collection适配器负责何时/采什么，公共会话与observation提供身份/查询/窗口；数据交给log适配器，再进入公共JSONL写入器。
6. ESP业务适配器提供目标和样式；公共esp提取/绘制/清GPU。Shootout ESP仍依赖当前Macro预测，不恢复此前撤销的独立预测。
7. UI业务页面/HUD适配器只转发原业务请求和读取状态；共用配置、控件、绑定、语言/主题、菜单/HUD注册与布局编辑。

## 手动验收步骤

以下均待用户确认，建议使用测试实例，不覆盖正在运行的正式实例配置。

| 操作 | 预期现象 |
|---|---|
| M打开菜单；浏览Home/Farm/Pests/ESP/Hotkeys/Settings/Misc，切中英文、滚动和折叠 | 原菜单路径、Hint、顺序和禁用外观一致；无异常或输入穿透 |
| Auto Aim/Auto Shoot及手动限制/遮挡父子切换；关闭再开启父项 | Disabled时保留子项值/颜色，恢复后按保存值；无额外右键或鼠标移动 |
| 关闭两个HUD总开关进入布局编辑；分别拖动/缩放后退出并重启 | 隐藏HUD仍可预览；布局独立保存；正常显示规则恢复 |
| Farm启动、打开背包/聊天/ESC/Mod菜单，手动暂停再关闭GUI | 延续原暂停/恢复语义；手动停机优先；无残留按住、重复Tick或重复动作 |
| Spawn/Warp/Loadout/Pest倒计时，等待期间断线 | 原步骤/延迟不变，断线取消旧动作，不跨世界继续操作 |
| Hypixel启动Shootout；观察Ping及间隔，再Stop | 原生主动RTT与HUD/预测同源；Stop后不继续探测或接纳旧响应 |
| 同配置对比20/60/120Hz瞄准；分别手动和自动射击、确认ON/OFF | 步长/角速度、CD和箭矢确认保留；一次箭矢不重复确认；停止后无延迟输入 |
| 多种僵尸/灯、最后存活目标补刀、幼年卡住再移动 | 原优先级、静止灯弹道、重锁定、速度与卡住恢复保持 |
| Logs手动开启选定类别，采集/诊断后Stop；重开，再退出游戏 | 已启用类别写盘，历史保留；批次正确结束并排空；重启总开关OFF |
| 切世界/断线/切账号；停止后重新启动各功能 | 旧任务/轨迹/ESP快照/声音失效，配置隔离；新会话不受旧响应影响 |
| 打开Pest和Shootout ESP并切换目标/世界 | 原颜色、0.5格线框、深度与隐藏条件不变 |
| 启用View Check，在安全测试场景观察真实异常 | 本地轨迹警告/严重停止沿用原规则；不把普通偏差认定为服务器反作弊 |

## 证据位置

- build/integration-clean-build.txt：干净构建。
- build/integration-results.tsv：各回归退出码；integration-*.txt为完整输出。
- build/integration-lifecycle-build.txt、integration-shootout-final.txt：总调试收尾后构建及Shootout重跑。
- tests/context/run.ps1：新公共能力自动回归入口。
- build/dev-client-out.txt、build/dev-client-error.txt及run/logs/latest.log：开发客户端运行证据。
- 原工作区仍在master；使用git worktree list定位两个工作区，不要在原工作区直接切到已被占用的集成分支。
