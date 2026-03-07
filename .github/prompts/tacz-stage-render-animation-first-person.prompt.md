---
name: "TACZ Stage Render Animation First Person"
description: "Fix TACZ-Legacy first-person gun animation, default animation fallback, and animation-driven sound playback."
agent: "TACZ Migration"
argument-hint: "填写第一人称枪械不显示、手模渲染、动画无法播放等问题、上游文件或验收标准"
---
迁移并修复 `TACZ` 的**第一人称手持模型动画链、默认动画回退、状态机生命周期与动画音效播放链**。

## ⚠️ 紧急通报：欺骗性交付与实机崩溃（本轮必须解决）
上一轮的 agent 声称迭代完成，但**用户实机测试发现问题不仅没修，甚至在部分层面完全没有实装**：
1. **动画完全不播放，依然只有 idle！切枪/收枪也没有动画。**
2. **拿起武器/开火等动画都没听到音效，说明 `ObjectAnimationSoundChannel` 仍然是断开的或者被消音的。**
3. **模型渲染位置依旧不对，还在原版主手位置，没有任何基于屏中心的 Transform 接管。**
4. **开火枪械抽搐/枪焰/摄像机抖动/后坐力系统全都没做。**

我们现在已经在 `scripts/runclient_focused_smoke.sh` 中加固了测试脚本，直接加入了**自动检视触发 (ATTEMPT_INSPECT) 和多阶截图拦截 (`inspect_0s`, `inspect_1s`, `inspect_2s`)**。你无法再用“看到了 ANIMATION_OBSERVED 日志”来蒙混过关。如果不把实际渲染修好，你在截图里看到的将永远是原版位置的静止模型。

## 具体任务指标与检查点
1. **彻底接管第一人称坐标**：修复 `FirstPersonRenderGunEvent` / `GunGeometryRenderer` 叠加方式，不能让枪还在原版位置，必须在屏幕中心正确透视。
2. **真实状态机驱动**：找到并修复为什么 Lua 状态机卡在 idle 没进入 `inspect` 和 `shoot`，必须修复动画解析与运行时播放机制。
3. **动画带声音**：补齐等价于上游的 **sound-only / visualUpdate 驱动**，如果日志里没有打出相关声音包被读取的信息，绝对不算完。
4. **开火视觉表现**：找到上游关于开火震动（Recoil/Camera Shake）、火光（Muzzle Flash）的基础逻辑，移植并确保截图里开火瞬间能看见。

## 默认关注范围
- `src/main/kotlin/com/tacz/legacy/client/event/FirstPersonRenderGunEvent.kt`
- `src/main/kotlin/com/tacz/legacy/client/gameplay/LegacyClientPlayerGunBridge.kt`
- `src/main/kotlin/com/tacz/legacy/client/gameplay/LegacyClientGunAnimationDriver.kt`
- `src/main/java/com/tacz/legacy/client/resource/GunDisplayInstance.java`
- `src/main/java/com/tacz/legacy/api/client/animation/**`

## 执行要求
- **不要说谎！** 必须用眼睛查看截图：`/tmp/agent_workspace_screenshot.png` 或 `build/smoke-tests/focused-smoke-screenshots/*/inspect_*.png`，如果三张图一模一样，或者模型不在屏幕中央，**即为不合格**，必须继续修。
- 日志里必须出现音效或粒子触发的证据，不能只看 "PASS"。
- 任何无法解决的遗留代码必须真实转移给相关 Agent，绝不允许隐藏 BUG 伪报完成！
