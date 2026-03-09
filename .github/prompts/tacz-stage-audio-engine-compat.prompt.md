---
name: "TACZ Stage Audio Engine Compat"
description: "Fix TACZ-Legacy audio playback backend and ensure physical sound emission"
agent: "TACZ Migration"
argument-hint: "填写音频后端无声、动画无音效等问题、上游文件或验收标准"
---
完善 `TACZ` 的**音频回放后端、原版 SoundEvent 注册链以及真实播放验证**。

## ⚠️ 紧急通报：欺骗性交付与实机崩溃（本轮必须解决）
上一轮音频 Agent 声称“音频引擎迭代完成”，然而**实机测试发现所有枪包武器都完全是静音的（无论是开火还是检视/切枪）**！
这说明音频并没有跟系统里的事件正确挂钩，或者底层实现依然存在致命缺陷。当前不仅是后端实现问题，很可能是**事件接入层 (Network/Animation) 根本没调用您的 SoundEngine**。

## 核心排查与修复点
1. **动画音效接入链中断**：
   - 动画里的 sound keyframes (通过 `ObjectAnimationSoundChannel`) 并没有实际在游戏中触发。如果触发了，请确保它是传给了真实的音频后端而不是静默吞掉。
   - 重点检查 `LegacyClientGunAnimationDriver` / `ObjectAnimationRunner` 的声音下发链路，它是否缺失了类似 `visualUpdate()` 或 `updateSoundOnly()` 之类的每帧/每 Tick 驱动？
2. **重构 SoundPlay 验证**：
   - 必须在游戏中听到声音，不只是看到日志里的 `submit animation=3`！
   - 目前在测试脚本里，我们会使用自动 `ATTEMPT_INSPECT` 和 `ATTEMPT_REGULAR_SHOT` 测试，你必须修改音频后端或相关埋点，如果发声函数 `play(...)` 被实际物理调用，需要在日志里印出关键标识。
3. **SoundEvent 注册表**：
   - 如果声音引擎依赖 `SoundEvent` 注册表或者原版的 `PositionedSoundRecord`，必须确认 JSON 中的音频标识已经正确映射和加载。
   
## 默认关注范围
- `src/main/kotlin/com/tacz/legacy/client/audio/TACZAudioRuntime.kt`
- `src/main/kotlin/com/tacz/legacy/client/audio/SoundEngine.java` （如果存在）
- `src/main/kotlin/com/tacz/legacy/api/client/animation/ObjectAnimationRunner.kt`
- `src/main/kotlin/com/tacz/legacy/common/network/message/event/ServerMessageSound.kt`

## 执行要求
- **不能再说谎！** 你必须通过修改烟测（或者分析烟测最新日志）来确认 `SOUND_PLAYED` 之类的**最终落地节点**被调用了至少一次。
- 只有成功发出声音（或者在 `SoundManager.playSound` 层面确信提交成功），才算完成任务。

## 已修复的编译错误（供参考，不需要再修）
以下编译问题已在主干修复，不要再动它们：
- `GunSoundInstance.java` 现已实现 `TACZClientSoundHandle` 接口（`getSoundId()` + `stop()`），`TACZClientGunSoundCoordinator.kt` 的类型不匹配不再存在
- `FocusedSmokeRuntime` 已添加 `markAudioPlaybackObserved(details)` 方法，`TACZOpenALSoundEngine` 里的调用可正常编译
