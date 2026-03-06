---
name: "TACZ Migrate Foundation"
description: "Migrate TACZ bootstrap, config, registration, and foundation systems into TACZ-Legacy with parity, tests, and runtime validation."
agent: "TACZ Migration"
argument-hint: "填写要迁移的基础系统、上游文件或验收约束"
---
迁移 `TACZ` 的**基础启动与注册系统**到 `TACZ-Legacy`。

默认关注范围：

- `GunMod.java`
- `config/**`
- `init/**`
- `event/**`
- `block/**`
- `command/**`
- `particles/**`
- `sound/**`
- 与这些链路直接相关的 Legacy 启动/注册接线

执行要求：

- 保持上游初始化顺序、注册行为和运行时可达性
- 不允许留下“注册了但游戏链路永远不会触发”的死接线
- 若存在行为性逻辑（例如事件门禁、注册映射、配置兼容），补测试
- 至少完成一轮编译/测试验证，并补一轮启动链路验证（如 smoke / focused runClient）

通用补充要求：

- 可参考工作区中的 `PrototypeMachinery` 等现成 `1.12.2` 项目作为落地示例，但行为真值仍以上游 `TACZ` 为准
- 原版源码可优先参考：`TACZ-Legacy/build/rfg/minecraft-src/java/net/minecraft/**`（1.12.2）与 `TACZ/build/tmp/expandedArchives/forge-1.20.1-47.3.19_mapped_parchment_2023.08.20-1.20.1-sources.jar_*/net/minecraft/**`（1.20.1；若哈希后缀变化，按 `*sources.jar_*` 搜索）
- 在 Multi-Agent 环境下按文件或子系统切分所有权，避免多个 Agent 修改同一组文件
- 若 Gradle 构建问题明显不是本次改动引起，先记录并跳过，不要清理共享 Gradle 缓存或全局构建数据
- 可自由选择 `Java` 或 `Kotlin`，前提是更便于接线、贴近周边模块且不会增加维护负担

输出必须包含：

- 上游真值源文件
- `TACZ-Legacy` 落点文件
- 测试与运行验证结果
- 若因 1.12.2 / Forge 差异产生必要调整，说明差异及原因
