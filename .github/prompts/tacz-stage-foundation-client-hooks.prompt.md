---
name: "TACZ Stage Foundation Client Hook Guard"
description: "Stabilize client-side registrations, active mixin wiring, and in-world smoke/diagnostic verification for the current render/combat stage without stealing feature ownership from other agents."
agent: "TACZ Migration"
argument-hint: "填写 client mixin、注册接线、renderer hook、smoke 阻塞、无关编译错误隔离等基础问题"
---
处理 `TACZ-Legacy` **当前阶段的客户端基础接线 / 注册 / Mixin 激活 / gameplay smoke 守门问题**。

## 这个 Prompt 是干什么的

这个 Prompt 只用于处理**会阻塞渲染 Agent / Client UX Agent 收口**的共享基础问题，例如：

- `mixins.tacz.json` 未激活所需 client mixin
- `ClientProxy.kt` / 注册入口缺少 renderer、GUI、event 或资源 reload 接线
- runClient smoke / focused smoke 被共享基础问题挡住
- 某个共享 compile / registration 问题导致其它 Agent 无法验证自己的功能

## 这个 Prompt 不是干什么的

- 不是用来实现具体 gun render / animation / GUI 视觉功能
- 不是用来重写 `GunRefitScreen` 或 `TACZGunItemRenderer` 的业务逻辑
- 不是用来替别的 Agent 完成 feature 本体

如果问题的本质是“材质没接上”“动画没播放”“GUI 视觉不像上游”，那应交回对应业务 Prompt；Foundation 只负责把**共享入口和验证通路**打通。

## 当前阶段已知线索

- `mixins.tacz.json` 当前已激活 `client.ItemRendererMixin`；Foundation 现在要做的是识别“还缺哪些 hook / client mixin / renderer 注册”，而不是重复解决已经完成的基础激活问题
- `ClientProxy.kt` 当前已接入部分 item renderer / keybinding / client asset reload，但未必覆盖所有 render / block / first-person hook
- 当前阶段已出现“某个无关 Java 编译错误挡住完整测试”这类问题；Foundation 应负责识别并记录，而不是胡乱清理缓存
- 当前阶段已有 smoke 脚本与定向运行链路验证，但它主要停留在**启动客户端 → 载入资源 → 回到标题界面**，对下一轮真正要收口的内容（动画、音效、配件挂载、projectile、爆炸）远远不够。
- 现在 render/combat 线最缺的不是“再一个 feature”，而是**可复用的 in-world focused smoke / 诊断链路**，能让业务 Agent 快速验证“真动了、真响了、真生了、真炸了”。

## 默认关注范围

- `src/main/resources/mixins.tacz.json`
- `src/main/kotlin/com/tacz/legacy/client/ClientProxy.kt`
- `src/main/kotlin/com/tacz/legacy/common/CommonProxy.kt`
- `src/main/kotlin/com/tacz/legacy/common/foundation/**`
- `scripts/runclient_smoke.sh`
- 任何新的 focused smoke / 自动进世界 / 调试日志守门脚本
- 与 renderer / gui / mixin / bootstrap 验证相关的共享接线路径

## 执行要求

- 只修共享基础问题，不越界接管业务功能
- 若某问题明显属于渲染/动画/GUI 业务逻辑，应在输出里明确移交给对应 Agent，不要顺手写 feature
- 保持注册顺序、激活范围与 smoke 链路清晰可验证
- 不允许通过清理共享 Gradle 缓存、wrapper 或全局构建数据来“假修复”问题
- 若需要补日志、诊断、focused smoke 断言，可以做，但要可回滚、可维护
- 本轮优先帮助业务 Agent 建立以下验证能力中的**至少两项**，并尽量落成同一套 focused smoke / diagnostic 入口：
	- 自动进入测试世界并持枪（最好能切到一把普通枪 + 一把爆炸枪）
	- 自动触发一次 `inspect / reload / fire` 输入
	- 记录动画诊断：displayId、animation/state_machine 解析结果、`use_default_animation` 是否命中、state machine trigger / current state
	- 记录动画音效诊断：`sound_effect` keyframe 是否触发、sound name 是否解析到 `ResourceLocation`
	- 记录配件诊断：scope/muzzle/grip/stock 当前 attachment item、挂点/adapter 节点是否命中
	- 记录战斗诊断：客户端 shoot 包、服务端 `LivingEntityShoot` 是否接受、`world.spawnEntity` 是否执行、RPG/榴弹是否触发 explosion
- Foundation 可以补“验证入口”和“诊断钩子”，但**不要**直接实现动画逻辑或子弹业务逻辑本体

## 推荐核对的上游真值

- 与当前阻塞点直接相关的上游入口类 / mixin / renderer hook
- `PrototypeMachinery` 等 1.12.2 项目的 client registration / TESR / GUI hook 用法，可作 Forge-era 落地参考

## Legacy 侧优先落点

- `src/main/resources/mixins.tacz.json`
- `src/main/kotlin/com/tacz/legacy/client/ClientProxy.kt`
- `src/main/kotlin/com/tacz/legacy/common/**`
- `scripts/runclient_smoke.sh`
- 任何新的 smoke / diagnostic 辅助文件

## 测试与验证要求

- 至少完成一轮编译验证
- 至少完成一轮 focused smoke 或启动链路验证
- 若修的是 mixin / hook 问题，应给出“入口已激活”的证据（日志、mixin out、运行结果）
- 若还有无关阻塞未解，必须明确说明它与本次基础修复的边界
- 若补的是 gameplay smoke / 诊断链，应明确说明它覆盖到了哪一段链路、还没覆盖哪一段链路
- 标题界面 smoke 不再算收口；至少要能回答动画/音效/配件/projectile/爆炸这五类链路里，哪些已经被 focused smoke 真正覆盖

## 输出必须包含

- 阻塞问题是什么
- 上游真值源文件
- Legacy 落点文件
- 采取了哪些共享接线 / 守门修复
- 测试与 smoke 结果
- 明确说明哪些问题仍应交回 Render / Client UX Agent 继续处理
- 若新增了 in-world smoke / 诊断入口，必须说明它如何帮助验证：第一人称动画、动画音效、挂枪配件、projectile 生成、爆炸行为 中的至少两项
