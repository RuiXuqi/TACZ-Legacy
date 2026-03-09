# TACZ-Legacy 工作区整理与迭代检查点（2026-03-09）

## 目的

本文件用于在本地提交前，整理当前 `TACZ-Legacy` 工作区的**变动文件范围**与**迭代历史脉络**，避免在一次 checkpoint commit 中混入来源不明的改动。

## 当前工作区变动规模

- 已修改的 tracked 文件：`67`
- 新增的 untracked 文件：`25`
- 本地协作目录 `.agent-workspace/`：**不纳入 Git 提交**

## 本次工作区改动分组

### 1. Prompt / 文档 / 构建与 smoke 工具

涉及文件：
- `.github/prompts/tacz-stage-audio-engine-compat.prompt.md`
- `.github/prompts/tacz-stage-client-ux-gui-i18n.prompt.md`
- `.github/prompts/tacz-stage-render-animation-first-person.prompt.md`
- `docs/AGENT_SCREENSHOT_WORKFLOW.md`
- `docs/TACZ_AGENT_MIGRATION_PLAN.md`
- `docs/TACZ_AUDIO_ENGINE_PLAN.md`
- `docs/FIRST_PERSON_RENDER_LEGACY_REFERENCE.md`
- `build.gradle.kts`
- `scripts/capture_window.sh`
- `scripts/runclient_focused_smoke.sh`

主要内容：
- 更新迁移 prompt，使 render / GUI / audio agent 的职责边界更清晰。
- 扩展 focused smoke 的截图 / marker / 守门能力。
- 补充第一人称旧渲染链参考文档，避免未来再次把死代码当真值。

### 2. 第一人称渲染、动画与客户端表现

涉及文件（代表）：
- `src/main/kotlin/com/tacz/legacy/client/event/FirstPersonRenderGunEvent.kt`
- `src/main/kotlin/com/tacz/legacy/client/event/FirstPersonRenderMatrices.kt`
- `src/main/java/com/tacz/legacy/mixin/minecraft/client/ItemRendererMixin.java`
- `src/main/java/com/tacz/legacy/mixin/minecraft/client/MinecraftMixin.java`
- `src/main/java/com/tacz/legacy/mixin/minecraft/client/EntityRendererInvoker.java`
- `src/main/kotlin/com/tacz/legacy/common/event/PreventGunLeftClickHandler.kt`
- `src/main/kotlin/com/tacz/legacy/client/gameplay/LegacyClientGunAnimationDriver.kt`
- `src/main/kotlin/com/tacz/legacy/client/gameplay/LegacyClientPlayerGunBridge.kt`
- `src/main/kotlin/com/tacz/legacy/client/gameplay/LegacyClientShootCoordinator.kt`
- `src/main/kotlin/com/tacz/legacy/client/renderer/entity/RenderKineticBullet.kt`
- `src/main/kotlin/com/tacz/legacy/client/renderer/bloom/TACZBloomBridge.kt`
- `src/main/kotlin/com/tacz/legacy/client/renderer/bloom/TACZBloomHooks.kt`
- `src/main/kotlin/com/tacz/legacy/client/renderer/bloom/TACZFirstPersonBloomRenderer.kt`
- `src/main/kotlin/com/tacz/legacy/client/animation/screen/RefitTransform.kt`

主要内容：
- 继续巩固第一人称 total takeover 渲染链。
- 修复 equip/左键/第一人称 tracer/Bloom/枪焰等表现层 parity。
- 补充第一人称矩阵辅助与渲染诊断能力。

### 3. Bedrock 模型、显示定义与 gun-pack 客户端资源

涉及文件（代表）：
- `src/main/java/com/tacz/legacy/api/client/animation/Animations.java`
- `src/main/java/com/tacz/legacy/client/model/BedrockAnimatedModel.java`
- `src/main/java/com/tacz/legacy/client/model/BedrockAttachmentModel.java`
- `src/main/java/com/tacz/legacy/client/model/BedrockGunModel.java`
- `src/main/java/com/tacz/legacy/client/model/FunctionalBedrockPart.java`
- `src/main/java/com/tacz/legacy/client/model/IFunctionalRenderer.java`
- `src/main/java/com/tacz/legacy/client/model/bedrock/BedrockModel.java`
- `src/main/java/com/tacz/legacy/client/model/bedrock/BedrockPart.java`
- `src/main/java/com/tacz/legacy/client/model/bedrock/BedrockRenderMode.java`
- `src/main/java/com/tacz/legacy/client/model/listener/model/ModelAdditionalMagazineListener.java`
- `src/main/java/com/tacz/legacy/client/model/functional/AttachmentRender.java`
- `src/main/java/com/tacz/legacy/client/model/functional/MuzzleFlashRender.java`
- `src/main/java/com/tacz/legacy/client/resource/GunDisplayInstance.java`
- `src/main/java/com/tacz/legacy/client/resource/gltf/GltfAnimationData.java`
- `src/main/java/com/tacz/legacy/client/resource/gltf/GltfAnimationParser.java`
- `src/main/java/com/tacz/legacy/client/resource/pojo/display/ammo/AmmoDisplay.java`
- `src/main/java/com/tacz/legacy/client/resource/pojo/display/ammo/AmmoEntityDisplay.java`
- `src/main/java/com/tacz/legacy/client/resource/pojo/display/gun/GunDisplay.java`
- `src/main/java/com/tacz/legacy/client/resource/pojo/display/gun/AmmoCountStyle.java`
- `src/main/kotlin/com/tacz/legacy/client/resource/TACZClientAssetManager.kt`

主要内容：
- 补齐 GLTF 动画资源消费链。
- 增加 ammo entity / ammo_count_style / additional magazine 等显示真值。
- 扩展 Bedrock render mode、functional render 与显示定义解析。

### 4. 音频运行时与声效桥接

涉及文件：
- `src/main/kotlin/com/tacz/legacy/client/audio/TACZAudioRuntime.kt`
- `src/main/kotlin/com/tacz/legacy/client/audio/TACZAudioTypes.kt`
- `src/main/kotlin/com/tacz/legacy/client/audio/TACZClientAudioHooks.kt`
- `src/main/kotlin/com/tacz/legacy/client/audio/TACZOpenALSoundEngine.kt`
- `src/main/kotlin/com/tacz/legacy/client/sound/TACZClientGunSoundCoordinator.kt`
- `src/main/java/com/tacz/legacy/client/sound/GunSoundInstance.java`
- `src/main/java/com/tacz/legacy/client/sound/GunSoundPlayManager.java`
- `src/main/java/com/tacz/legacy/client/sound/TACZClientSoundHandle.java`
- `src/main/kotlin/com/tacz/legacy/common/resource/TACZGunSoundRouting.kt`
- `src/main/kotlin/com/tacz/legacy/sound/SoundManager.kt`

主要内容：
- 将 direct OpenAL 主链重新接回生产路径。
- 增补客户端枪械音效协调层与 sound handle 抽象。
- 收口 focused smoke 中的音效验收链路。

### 5. Combat / Network / Gameplay parity

涉及文件（代表）：
- `src/main/java/com/tacz/legacy/client/animation/statemachine/GunAnimationStateContext.java`
- `src/main/java/com/tacz/legacy/mixin/minecraft/LivingEntityMixin.java`
- `src/main/kotlin/com/tacz/legacy/common/ShooterTickHandler.kt`
- `src/main/kotlin/com/tacz/legacy/common/entity/LegacyEntities.kt`
- `src/main/kotlin/com/tacz/legacy/common/entity/shooter/BurstFireTaskScheduler.kt`
- `src/main/kotlin/com/tacz/legacy/common/entity/shooter/LivingEntityBolt.kt`
- `src/main/kotlin/com/tacz/legacy/common/entity/shooter/LivingEntityDrawGun.kt`
- `src/main/kotlin/com/tacz/legacy/common/entity/shooter/LivingEntityReload.kt`
- `src/main/kotlin/com/tacz/legacy/common/entity/shooter/LivingEntityShoot.kt`
- `src/main/kotlin/com/tacz/legacy/common/entity/shooter/ShooterDataHolder.kt`
- `src/main/kotlin/com/tacz/legacy/common/resource/GunDataAccessor.kt`
- `src/main/kotlin/com/tacz/legacy/common/resource/TACZGunPackRuntime.kt`
- `src/main/kotlin/com/tacz/legacy/common/resource/TACZDataScriptManager.kt`
- `src/main/kotlin/com/tacz/legacy/common/entity/shooter/TACZGunScriptAPI.kt`
- `src/main/kotlin/com/tacz/legacy/common/network/TACZNetworkHandler.kt`
- `src/main/kotlin/com/tacz/legacy/common/network/message/client/ClientMessageUnloadAttachment.kt`
- `src/main/kotlin/com/tacz/legacy/common/network/message/client/ClientMessageRefitGunCreative.kt`

主要内容：
- 修正 burst fire、reload timing、needCheckAmmo、脚本时间基准。
- 增补数据脚本管理与枪械脚本 API。
- 强化 bullet spawn / tracer / shooter 同步的运行时语义。

### 6. GUI / Refit / Overlay / Item 接线

涉及文件：
- `src/main/kotlin/com/tacz/legacy/client/gui/GunRefitScreen.kt`
- `src/main/kotlin/com/tacz/legacy/client/gui/TACZGuiModelPreviewRenderer.kt`
- `src/main/kotlin/com/tacz/legacy/client/event/LegacyClientOverlayEventHandler.kt`
- `src/main/kotlin/com/tacz/legacy/common/application/refit/LegacyGunRefitRuntime.kt`
- `src/main/kotlin/com/tacz/legacy/common/item/LegacyItems.kt`
- `src/main/kotlin/com/tacz/legacy/api/item/IGun.kt`
- `src/main/java/com/tacz/legacy/api/client/other/KeepingItemRenderer.java`
- `src/main/java/com/tacz/legacy/runtime/client/KeepingItemRendererRuntimeBridge.java`

主要内容：
- 收口 GunRefitScreen、预览矩阵、属性面板与创造模式附件流。
- 接回 1.12.2 下 keep-item 语义桥接。
- 扩展 overlay 对 ammo / crosshair / heat 等 display 数据的消费。

### 7. 测试回归

新增 / 修改测试覆盖：
- 音频：`TACZAudioRuntimeTest`、`TACZOpenALSoundEngineTest`
- 动画与矩阵：`GltfAnimationControllerTest`、`FirstPersonRenderMatricesTest`
- 资源与脚本：`TACZClientAssetLoadPlanTest`、`TACZClientLuaScriptEnvironmentTest`、`TACZClientAssetManagerLuaScriptInjectionTest`
- gameplay：`LegacyClientShootCoordinatorBurstStateTest`、`BurstFireTaskSchedulerTest`、`EntityKineticBulletShotDirectionTest`、`GunDataAccessorReloadTimingTest`
- UI / display：`AmmoDisplayParsingTest`、`GunDisplayParsingTest`、`LegacyGunRefitRuntimeTest`

## 最近可见迭代历史（Git 已提交基线）

从最近提交历史看，当前工作区是建立在如下检查点之后继续演进的：

1. `89fc159` — `chore: checkpoint migration progress and agent workflow`
2. `5aecc61` — `feat: port TextShow gun-mounted text display system and animation driver improvements`
3. `658a6e5` — `docs: update migration plan and prompt with TextShow completion`
4. `3931364` — `feat: port first-person procedural animations, fire feedback and muzzle flash`
5. `3e14442` — `test: enforce inspect animation testing in focused smoke`
6. `09dbee6` — `docs: triage latest manual testing reports`

## 当前未入库的本地迭代脉络

`09dbee6` 之后，工作区继续累积了以下本地迭代：

- 重新梳理并补强 render / GUI / audio agent prompt；
- focused smoke 扩展为真实的 inspect / refit / tracer / bloom / audio / reload 验收门；
- 第一人称渲染链继续对齐旧版 Legacy 与上游 TACZ 的 total takeover 语义；
- GLTF 动画、ammo entity、crosshair、additional magazine、Bloom、tracer 与枪焰链路继续收口；
- direct OpenAL 主链与客户端枪械音效桥接重新并入生产路径；
- refit / creative attachment / keep-item / first-person preview 继续打磨；
- burst scheduler、reload timing、脚本时间戳与 needCheckAmmo 等 gameplay parity 继续补齐；
- 为避免未来重复踩坑，新增 `docs/FIRST_PERSON_RENDER_LEGACY_REFERENCE.md` 记录旧版第一人称渲染真值。

## 提交边界

建议本次提交纳入：
- 所有 `src/main/**`、`src/test/**`、`.github/prompts/**`、`docs/**`、`scripts/**`、`build.gradle.kts` 下的当前变动

明确不纳入：
- `.agent-workspace/**`
- `build/**`
- `logs/**`
- `run/**`
- focused smoke 截图、临时脚本、聊天缓存等本地产物

## 建议提交说明

由于本次是跨 render / audio / gameplay / docs 的工作区整理与阶段性入库，建议使用本地 checkpoint 风格的提交信息，例如：

- `chore: checkpoint latest migration worktree`
- 或 `feat: checkpoint render audio gameplay parity worktree`
