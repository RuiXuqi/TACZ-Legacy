---
name: "TACZ Stage Render Animation First Person"
description: "Fix TACZ-Legacy first-person gun animation, default animation fallback, and animation-driven sound playback."
agent: "TACZ Migration"
argument-hint: "填写第一人称枪械不显示、手模渲染、动画无法播放等问题、上游文件或验收标准"
---
迁移并修复 `TACZ` 的**第一人称手持模型动画链、默认动画回退、状态机生命周期与动画音效播放链**。

## 当前危机与必须修复的严重 BUG （本轮重点）
1. **上一轮已经补过一批 trigger，但标准枪械仍然几乎完全静止**。这说明问题不再只是“有没有喂 trigger”，而是**状态机拿到的 animation prototype / 生命周期 / 声音推进链仍然不对**。
   - 当前一个非常强的线索是：`Applied Armorer-v1.1.4.1-for114+.zip` 里的 `special_melee_task_manager` 居然能看到 idle，而普通枪几乎全不播。这更像“少数资源自带完整动画还能跑，其它枪依赖的默认回退语义丢了”，而不是 listener → bone → render 整条链完全死亡。
   - Legacy 当前的 `src/main/java/com/tacz/legacy/client/resource/pojo/display/gun/GunDisplay.java` 没有上游 `use_default_animation` / `default_animation` 等字段；`GunDisplayInstance.checkAnimation()` 也只加载 display 自己的 `animation`，没有把默认 rifle/pistol 动画补进 controller。
   - 真实枪包仍在大量使用这套契约，例如 `assets/tacz/.../display/guns/ak47_display.json` 里就有 `"use_default_animation": "rifle"`。
   **任务**：先按上游 `GunDisplayInstance.checkAnimation()` 对齐**默认动画回退语义**，不要再只盯着 trigger plumbing。
2. **动画相关音效完全听不到**。当前不能只说“sound_effects 已经能 parse”，必须把**运行时播放链**补齐并验证。
   - Legacy 已经有：`SoundEffectKeyframesSerializer` → `Animations.createAnimationFromBedrock(...)` → `ObjectAnimationSoundChannel` → `ObjectAnimationRunner.update()/updateSoundOnly()`。
   - 但 Legacy 缺上游 `TickAnimationEvent(RenderTickEvent)` / `AnimateGeoItemRenderer.visualUpdate()` 等价的**sound-only / visualUpdate 驱动**，而且如果默认动画原型没被装入 controller，声音关键帧也根本不会跑到。
   **任务**：对齐上游 `visualUpdate()` / `updateSoundOnly()` 语义，证明 `draw / shoot / reload / inspect` 至少一组动画音效能真实播放，而不是只在日志里说“链路存在”。
3. **近战 / 刺刀动画输入仍不完整**。
   - `LegacyClientPlayerGunBridge.processMeleeInput()` 目前只发 `ClientMessagePlayerMelee()`，但默认状态机脚本 `default_state_machine.lua` 明确在等 `INPUT_BAYONET_MUZZLE / INPUT_BAYONET_STOCK / INPUT_BAYONET_PUSH`。
   - 对照上游 `LocalPlayerMelee.java`，需要把 melee 输入真正触发到状态机，而不是只做 gameplay 包发送。
4. **第一人称状态机生命周期仍比上游薄太多**。
   - 继续核对 `FirstPersonRenderGunEvent.kt` 与上游 `AnimateGeoItemRenderer.needReInit()/tryInit()/tryExit()`、`FirstPersonRenderEvent`、`LocalPlayerDraw.doPutAway()` 的差异。
   - 不允许留下“切枪 / 重新持枪后状态残留”“只初始化一次后长期不重建”“put_away 缺失导致下次 draw 状态错乱”这类隐性问题。

## 默认关注范围
- `src/main/kotlin/com/tacz/legacy/client/event/FirstPersonRenderGunEvent.kt`
- `src/main/kotlin/com/tacz/legacy/client/gameplay/LegacyClientPlayerGunBridge.kt`
- `src/main/kotlin/com/tacz/legacy/client/gameplay/LegacyClientGunAnimationDriver.kt`
- `src/main/java/com/tacz/legacy/client/resource/GunDisplayInstance.java`
- `src/main/java/com/tacz/legacy/client/resource/pojo/display/gun/GunDisplay.java`
- `src/main/java/com/tacz/legacy/api/client/animation/**`

## 执行要求
- 必须直接复用现有 `GunDisplayInstance.getAnimationStateMachine()` / `AnimationController` / `BedrockAnimatedModel`，不要新建第二套第一人称状态机。
- **不要把“又补了几个 trigger”当成完成**；必须证明标准 rifle / pistol 枪械在 controller 中拿到了非空 prototype，并真实进入动画播放。
- 优先复用上游现成语义：`use_default_animation`、`default_animation`、`visualUpdate()`、`INPUT_BAYONET_*`、draw/put_away 生命周期。
- 可以加 focused test、轻量日志或临时诊断，但不要顺手接管配件渲染、第三人称 primitive 模型或 projectile 战斗本体。
- 若在多 Agent 环境中运行，注意 `GunDisplay.java` / `GunDisplayInstance.java` 与数据/材质线可能存在文件冲突；**不要并行编辑同一文件**。

## 必验场景
- 至少选一把使用 `"use_default_animation": "rifle"` 的标准枪（例如 `ak47` 级别资源）验证：`draw / idle / walk / run / shoot / reload / inspect` 不再是静止模型。
- 至少验证一条 melee / bayonet 输入链，证明 `INPUT_BAYONET_*` 真正命中状态机。
- 至少验证一组动画音效：`draw` 或 `reload` 或 `shoot` 的 keyframe sound 确实在游戏里可听到。

## 输出必须包含
- 上游真值源文件（特别是 `GunDisplayInstance.checkAnimation()`、`TickAnimationEvent`、`AnimateGeoItemRenderer.visualUpdate()`、`LocalPlayerMelee`、相关 `LocalPlayer*` trigger 入口）。
- Legacy 落点文件。
- 明确说明这次补上了哪些内容：默认动画回退、哪些 trigger、哪些生命周期保护、是否增加了 `visualUpdate()` 驱动。
- 至少一条真实运行链路证据，证明第一人称动画**和其音效**不再只是静态摆件。
- 必须明确回答：为什么之前只有 `special_melee_task_manager` 之类的个别资源看起来还能动，而标准枪几乎完全不播。
