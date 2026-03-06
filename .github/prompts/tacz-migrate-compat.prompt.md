---
name: "TACZ Migrate Compat"
description: "Migrate TACZ optional compatibility layers such as KubeJS, JEI, Cloth config, controller support, and shader/animation integrations into TACZ-Legacy."
agent: "TACZ Migration"
argument-hint: "填写要迁移的兼容模块、上游文件或验收约束"
---
迁移 `TACZ` 的**第三方兼容层**到 `TACZ-Legacy`。

默认关注范围：

- `compat/**`
- 与兼容模块直接相关的 Legacy `integration/**`、`client/**`、`common/**`、`mixin/**` 接线
- 可选依赖门禁、运行时探测、降级回退逻辑

执行要求：

- 保持兼容功能的启用条件、降级行为与可选依赖安全性
- 不允许把第三方依赖强耦合进核心领域逻辑
- 对易回归的兼容门禁、注册条件、数据桥接逻辑补测试
- 验证未安装依赖时不会崩，安装依赖后功能链路真实可达

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
- 有/无依赖两种场景下的验证结论
- 任何因 1.12.2 生态差异导致的必要改写说明
