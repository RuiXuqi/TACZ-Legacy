# Copilot instructions — TACZ-Legacy

## 项目定位
- 这是 **TACZ 在 Minecraft 1.12.2 Forge 的 Kotlin 移植工程**。
- 迁移目标不是“功能删减版”，而是以 **功能一致性、数据兼容性、可扩展渲染架构** 为核心。

## 硬约束（优先级最高）
1. **美术素材可复用**：资源命名、目录结构与格式转换策略应优先保证复用。
2. **枪包/数据包兼容**：外部包格式尽量保持兼容；若存在版本差异，必须提供迁移层或适配器。
3. **功能一致性**：同名功能在行为上应与 TACZ 对齐（允许底层实现不同）。
4. **渲染管线重构**：必须走可扩展框架化设计，而非散点式 TESR/事件堆叠。
5. **架构分离但不割裂 MC**：核心程序逻辑尽量去 MC 依赖以支持单测；但必须保留清晰、可维护的 MC 适配层，不做不可逆硬分叉。

## 技术与构建
- Forge: `1.12.2-14.23.5.2847`
- Kotlin: Forgelin-Continuous
- Build: RetroFuturaGradle
- Mixin: MixinBooter + `mixins.tacz.json`

常用命令：
- 初始化：`gradlew setupDecompWorkspace`
- 客户端：`gradlew runClient`
- 服务端：`gradlew runServer`
- 构建：`gradlew build`

## 代码组织约定（参考 PrototypeMachinery 风格）
- 按职责分层：
  - `api/`：稳定对外接口（尽量保持简洁）
  - `common/`：跨端核心逻辑（数据、注册、网络协议定义）
  - `client/`：渲染、输入、客户端 UI
  - `integration/`：第三方兼容层（可选依赖）
  - `mixin/`：最小侵入式补丁，按目标域分包
- Mixin 源码统一放在 `src/main/java/com/tacz/legacy/mixin/**`（不要放在 Kotlin 源集）。
- 禁止把高层业务逻辑直接塞进 Mixin。
- 建议增加边界层：
  - `domain/`：纯 Kotlin 领域逻辑（禁止 `net.minecraft.*`）
  - `application/`：用例编排
  - `infrastructure/mc/`：Minecraft/Forge 适配器

## 命名约定
- 通常**不允许**新增以 `Legacy` 作为类名前缀的命名；`Legacy` 不应作为默认品牌前缀滥用。
- 若类名需要明确声明模组领域或产品归属，优先使用 `TACZ` 作为前缀，而不是 `Legacy`。
- 该规则主要约束**新增命名与重命名决策**；不要仅为了追求规则一致而对无关历史类名做大规模重命名。

## 渲染框架约定
- 渲染层按“阶段（Stage）+ 提交（Submit）+ 执行（Execute）”组织：
  - 逻辑系统产出 `RenderData`
  - 渲染系统只消费 `RenderData`
  - 批处理、透明顺序、后处理（如 bloom）在统一调度器中执行
- 所有新增渲染特性必须可开关、可回退、可诊断（debug HUD 或日志）。
- 可参考 Kirino-Engine 的思想：`FramePhase/FSM`、`RenderPass+Subpass`、`Headless/Graphics` 双视图。

## 兼容性实践
- 与枪包兼容相关的字段/键名不要随意改名。
- 若不得不改，新增“兼容读取 + 新格式写入”的过渡策略。
- 资源路径尽量保持 `assets/tacz/...` 语义一致。

## 迁移评估与汇报约定
- 当用户要求评估 `TACZ` → `TACZ-Legacy` 的迁移工作量时，**默认以代码量为主量纲**，不要直接给出“人天 / 周数 / 工期”估算，除非用户明确要求时间评估。
- 代码量统计默认只看主源码：
  - 上游基线：`TACZ/src/main/java/com/tacz/guns/**`
  - Legacy 现状：`TACZ-Legacy/src/main/java/**` 与 `TACZ-Legacy/src/main/kotlin/**`
  - 默认排除：`build/`、`bin/`、生成物、`src/test/**`、`resources/**`
- 汇报时必须给出**统计口径**（例如“源码文件数 + 物理源码行数”），避免把资源文件、文档、生成产物混入代码工作量。
- 结果必须按模块/子系统拆分，而不是只报一个总数。优先沿用上游顶层包分桶（如 `client`、`api`、`resource`、`network`、`entity`、`compat`、`mixin`）。
- 对需要**重写而非直译移植**的区域（尤其渲染、输入、Mixin、资源兼容层），要明确说明“不是 1:1 port”，但仍以“承载该能力需要落多少代码”来估算，而不是改用时间口径。
- 若当前 `TACZ-Legacy` 分支只有资源/文档骨架、几乎没有主源码，应明确说明：**剩余代码工作量下限接近上游对应模块总代码量**，并把这视作“从零承载”的估算起点。
- 评估输出建议至少包含三层：
  1. 上游总代码量基线
  2. Legacy 当前已落地代码量
  3. 剩余代码量与高风险重写区（如渲染/动画/资源兼容）
- 美术素材、模型、动画 JSON、枪包资源的复用价值可以单独说明，但**不要并入代码量统计**；它们只能作为“降低非代码迁移成本”的背景信息。

## 变更原则
- 优先小步提交：每次改动应可编译、可验证。
- 避免无关重构；先实现兼容，再做内部优化。
- 修改构建开关（`use_mixins/use_coremod/...`）后，提醒重新 setup 工作区。
