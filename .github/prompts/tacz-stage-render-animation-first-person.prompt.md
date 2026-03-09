---
name: "TACZ Stage Render Animation First Person"
description: "Fix TACZ-Legacy first-person gun rendering, matrix transforms, animation playback, and visual effects."
agent: "TACZ Migration"
argument-hint: "填写第一人称枪械不显示、手模渲染、动画无法播放等问题、上游文件或验收标准"
---
迁移并修复 `TACZ` 的**第一人称手持模型渲染管线、矩阵变换链、动画轨道播放与视觉效果**。

## ⚠️ 紧急通报
上一轮 agent 的交付与用户实机结果不一致：**渲染仍然是挂的**。本轮必须以截图和实际视觉结果为准，不能只看日志。

## 已验证可用的旧矩阵变换参考（修 bug 时以此为基线，不要整条推翻）
`src/main/kotlin/com/tacz/legacy/client/event/FirstPersonRenderGunEvent.kt` 已经落地了一版**可作为旧正确参考**的第一人称矩阵链。你需要在这个基线之上修 bug，而不是重写另一套坐标系统。

### 第一人称渲染入口
`onRenderHand(event: RenderSpecificHandEvent)` 的矩阵顺序应保持为：
1. `applyVanillaFirstPersonTransform(handSide, equipProgress, swingProgress)`
   - `transformSideFirstPerson()`：`translate(side * 0.56f, -0.52f + equipProgress * -0.6f, -0.72f)`
   - `transformFirstPerson()`：Y/Z/X 挥动旋转，再补一次 `side * -45°`
2. **View bob 补偿**
   - 使用 `renderArmPitch/renderArmYaw` 差值抵消原版手持抖动
   - 对应 `xRot * -0.1f` / `yRot * -0.1f` 的额外旋转
3. **root node 视角偏移**
   - `clampedXRot = tanh(xRot / 25) * 25`
   - `clampedYRot = tanh(yRot / 25) * 25`
   - 写入 `rootNode.offsetX/offsetY`，并追加 `additionalQuaternion.rotateX/rotateY`
4. **基岩模型坐标修正**
   - `translate(0, 1.5, 0)`
   - `rotate(180°, Z)`
5. `applyFirstPersonPositioningTransform(model, stack, aimingProgress)`
   - 以 `idleSightPath` 和 `resolveAimingViewPath(stack)` 为参考
   - 使用 `FirstPersonRenderMatrices.buildPositioningNodeInverse()` 计算逆矩阵
   - idle 权重固定 1，aiming 权重为 `aimingProgress`
6. `applyAnimationConstraintTransform(model, aimingProgress)`
   - 根据 `constraintPath` / `constraintObject` 做逆向补偿
   - `inverseTranslation.mul(translationICA.x() - 1f, translationICA.y() - 1f, 1f - translationICA.z())`
   - 注意 Bedrock 旋转导致 xy 方向和普通 MC 直觉不同

### 程序化持枪运动
以下逻辑已经是可参考的旧正确实现：
- `applyShootSwayAndRotation()`：开火后 300ms 内的 rootNode X/Y sway + Y rotation noise
- `applyJumpingSway()`：跳跃/落地时基于 `posY` 与 `SecondOrderDynamics` 的延迟摆动
- `onCameraSetup() -> applyCameraAnimation()`：将 `cameraAnimationObject.rotationQuaternion` 转为 yaw/pitch/roll 增量
- `cacheMuzzleRenderOffset()`：在 render 前缓存 `muzzleFlashPosPath` 当前帧矩阵位置，供枪焰/粒子使用

### 关键矩阵辅助文件
- `src/main/kotlin/com/tacz/legacy/client/event/FirstPersonRenderMatrices.kt`
  - `buildPositioningNodeInverse()`
  - `buildAimingPositioningTransform()`
  - `interpolateMatrix()`
- `src/main/java/com/tacz/legacy/util/math/MathUtil.java`
  - `applyMatrixLerp()`
  - `getEulerAngles()`
  - `multiplyQuaternion()`

### 上游矩阵真值参考
- `TACZ/src/main/java/com/tacz/guns/client/event/FirstPersonRenderGunEvent.java`
- `TACZ/src/main/java/com/tacz/guns/client/event/FirstPersonRenderEvent.java`

如果画面仍然偏在原版主手位置、没有居中透视，优先逐步检查上述 1→6 的顺序是否被破坏，而不是乱调常量碰运气。

## 已验证可参考的旧动画链（可以引用）
以下动画链条可以作为旧代码参考：
- `src/main/kotlin/com/tacz/legacy/client/gameplay/LegacyClientGunAnimationDriver.kt`
  - `prepareContext()`
  - `trigger()` / `triggerIfInitialized()`
  - `visualUpdateHeldGun()`
  - `visualUpdateExitingAnimation()`
  - `beginPutAway()`
- `src/main/java/com/tacz/legacy/api/client/animation/AnimationController.java`
  - 多轨道动画调度入口
  - `update()` 中驱动 runner 与声音通道
- `src/main/java/com/tacz/legacy/api/client/animation/ObjectAnimationRunner.java`
  - 单动画采样
- `src/main/java/com/tacz/legacy/api/client/animation/ObjectAnimationSoundChannel.java`
  - 动画关键帧音效触发
- `src/main/java/com/tacz/legacy/api/client/animation/interpolator/CustomInterpolator.java`
  - 已移植的插值实现（linear / Catmull-Rom / slerp / squad）
- `src/main/java/com/tacz/legacy/client/resource/GunDisplayInstance.java`
  - 模型、纹理、状态机实例持有者

### ⛔ 不要参考旧动画过渡逻辑
旧代码里**动画 transition / blend 那部分是坏的**，本轮不要把它当真值：
- 不要以 `transition()`、`isTransitioning()`、`getTransitionTo()` 这一套为迁移目标
- 不要把“修 transition”当作本轮第一优先级
- 如果 inspect / shoot / draw 没播放，优先查：
  1. Lua 状态机是否真的 `trigger()` 到了对应输入
  2. `AnimationController.runAnimation()` 是否被调用
  3. 动画 JSON 是否被正确解析
  4. `ObjectAnimationSoundChannel.update()` 是否真的执行
  5. 资源路径 / sourceId / gunId 归一化是否正确

## 具体任务指标与检查点
1. **第一人称坐标正确**：枪要在屏幕中央靠下正确透视，不能还留在原版主手偏移位置。
2. **状态机真实驱动**：idle、inspect、shoot、draw 至少要能切换并播放，不能永远卡在 idle。
3. **动画带声音**：开火、拉栓、换弹等动画关键帧音效必须能听到，并且日志能证明资源被触发。
4. **开火视觉表现**：后坐力、枪焰、摄像机动画必须实际可见；截图不能还是静止模型。

## 默认关注范围
- `src/main/kotlin/com/tacz/legacy/client/event/FirstPersonRenderGunEvent.kt`
- `src/main/kotlin/com/tacz/legacy/client/event/FirstPersonRenderMatrices.kt`
- `src/main/kotlin/com/tacz/legacy/client/gameplay/LegacyClientPlayerGunBridge.kt`
- `src/main/kotlin/com/tacz/legacy/client/gameplay/LegacyClientGunAnimationDriver.kt`
- `src/main/java/com/tacz/legacy/client/resource/GunDisplayInstance.java`
- `src/main/java/com/tacz/legacy/api/client/animation/**`
- `src/main/java/com/tacz/legacy/util/math/MathUtil.java`

## 执行要求
- **不要说谎！** 必须用眼睛查看截图：`/tmp/agent_workspace_screenshot.png` 或 `build/smoke-tests/focused-smoke-screenshots/*/inspect_*.png`。如果三张图一模一样，或者模型不在屏幕中央，即为不合格。
- 日志里必须出现音效或粒子触发证据，不能只看 `PASS`。
- 矩阵变换链已经有旧正确参考，除非确认某一步就是错的，否则不要整段推翻重写。
- **不要碰动画过渡（transition）逻辑**，那部分旧代码本来就是坏的。
- 任何无法解决的遗留问题必须真实记录并转移，绝不允许伪报完成。
