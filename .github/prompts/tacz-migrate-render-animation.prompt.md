---
name: "TACZ Migrate Render Animation"
description: "Migrate TACZ rendering, model, animation, client resource, and client mixin systems into TACZ-Legacy with parity and runtime validation."
agent: "TACZ Migration"
argument-hint: "填写要迁移的渲染/动画功能、上游文件或验收约束"
---
迁移 `TACZ` 的**渲染 / 模型 / 动画 / 客户端资源 / 客户端 Mixin 系统**到 `TACZ-Legacy`。

当前阶段说明：

- 数据/枪包兼容、战斗/网络、Client UX 的本阶段迭代已完成；此外，渲染侧的 **Bedrock model / gun display / client asset manager / gun item TEISR 基础设施** 也已完成一轮大迁移。当前 `TACZ-Legacy` 已有可继续复用的基座：`client/resource/pojo/model/**`、`client/resource/pojo/display/gun/**`、`client/resource/serialize/Vector3fSerializer.java`、`client/model/bedrock/**`、`client/resource/TACZClientAssetManager.kt`、`client/renderer/item/TACZGunItemRenderer.kt`，以及 `ClientProxy.kt` 中的接线。
- 当前 render 线默认应建立在这些已落地基座之上：若任务只是基础 display 解析、基础模型装载、纹理解析、item renderer 骨架或 Bedrock cube/part/model 结构接线，应优先归类为**回归 / parity 缺口 / 剩余增强**，而不是重新从零实现渲染底座。
- 当前已存在的验证基线包括 `BedrockModelParsingTest.kt`、`GunDisplayParsingTest.kt`，并且已有一轮 `runClient` smoke 验证 `TACZClientAssetManager` 能从 gun pack 成功加载 `110` 个 display、`166` 个 model、`166` 个 texture。
- 当前 render 线剩余重点已收敛到：动画状态机 / 关键帧插值 / bone animation application、ammo/attachment display POJO 与 renderer、muzzle flash / shell ejection、第一人称 hand/scope 渲染链路，以及这些表现层与现有 `ServerMessage*` 事件 / shooter 状态 / runtime snapshot 的继续接线。
- 若任务主要是 HUD / tooltip / 输入桥接 / `gun_smith_table` 基础 GUI-container-craft 流，优先改用 `client ux` Prompt，而不是把屏幕/交互问题都塞进渲染 Prompt。
- 若任务涉及 scope 视觉、第一/第三人称动作、枪械 display、动画采样、客户端特效或与 `ServerMessage*` 事件绑定的表现层反馈，则应优先归到本 Prompt。

默认关注范围：

- `client/resource/**`
- `client/model/**`
- `client/renderer/**`
- `client/animation/**`
- `api/client/**`
- `mixin/client/**`
- 与这些链路直接相关的 Legacy 渲染管线与客户端表现接线

执行要求：

- 保持上游的数学逻辑、动画采样、插值、姿态合成、资源解析与渲染时序语义一致
- 不允许只迁资源解析或只迁渲染壳子；必须保证整条表现链路真实可达
- 对插值器、姿态采样、可见性过滤、资源解析、时序计算等逻辑密集部分补测试
- 至少完成一轮编译/测试验证和一轮真实运行链路验证（如 `runClient` smoke、focused render path、调试日志/覆盖确认）

当前阶段对接重点：

- 优先消费已经落地的数据与战斗基座：`TACZGunPackRuntimeRegistry.getSnapshot()` / `TACZRuntimeSnapshot`、`LegacyItems` 的 item type 注册、`IGun.getGunId()`、`TACZNetworkHandler` 与现有 shooter/network 状态
- 不要重做 display / gun id / item type 的平行缓存；优先把渲染、模型、动画和客户端资源链路接到现有 runtime snapshot 与枪械 item 消费路径上
- 优先复用 `TACZClientAssetManager`、`client/resource/pojo/**`、`client/model/bedrock/**` 与 `TACZGunItemRenderer` 的现有资产加载 / 模型构建 / item 渲染基座，不要并行再造第二套 bedrock/display cache
- 若涉及 scope / attachment / refit 的表现层，请同步消费 `TACZGunPackPresentation` 已有的 builtin attachment / zoom / laser config helper，与 `IGun` / `IAttachment` 的 accessor 真值保持一致
- 若涉及射击 / 换弹 / 近战的客户端表现，优先消费现有 `common/network/message/event/**` 事件消息并与渲染/音效系统接线

通用补充要求：

- 可参考工作区中的 `PrototypeMachinery` 等现成 `1.12.2` 项目作为落地示例，但行为真值仍以上游 `TACZ` 为准
- 原版源码可优先参考：`TACZ-Legacy/build/rfg/minecraft-src/java/net/minecraft/**`（1.12.2）与 `TACZ/build/tmp/expandedArchives/forge-1.20.1-47.3.19_mapped_parchment_2023.08.20-1.20.1-sources.jar_*/net/minecraft/**`（1.20.1；若哈希后缀变化，按 `*sources.jar_*` 搜索）
- 在 Multi-Agent 环境下按文件或子系统切分所有权，避免多个 Agent 修改同一组文件
- 若 Gradle 构建问题明显不是本次改动引起，先记录并跳过，不要清理共享 Gradle 缓存或全局构建数据
- 可自由选择 `Java` 或 `Kotlin`，前提是更便于接线、贴近周边模块且不会增加维护负担

输出必须包含：

- 上游真值源文件
- Legacy 落点文件
- 测试结果
- 运行链路验证结果（不是 compile-only）
- 明确说明这次复用了哪些现成 render/client resource 基座（如 POJO、bedrock model、asset manager、TEISR），以及哪些仍属于剩余 animation / first-person / 特效子轨
- 任何必须保留的非 1:1 结构差异及原因
