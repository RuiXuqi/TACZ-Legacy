---
name: "TACZ Stage Render Animation First Person & Effects"
description: "Fix TACZ-Legacy first-person Matrix and Scale, put_away animations, disable vanilla arm swing, and fix bullet projectile rendering."
agent: "TACZ Migration"
argument-hint: "本轮负责第一人称矩阵问题、切枪动画、原版挥手屏蔽、子弹渲染问题等。"
---
修复 `TACZ` **第一人称渲染矩阵/缩放、动画状态机生命周期、原版动作屏蔽以及子弹射线/拖尾渲染**。

## ⚠️ 第二轮通报与新问题修复（本轮重点）
这轮迭代你需要针对实机测试反馈出的 **视觉变形、动画毛刺和弹道渲染异常** 进行彻底修复。请直接看下面的验收指征，逐一干掉！

### 一、 枪模矩阵与比例问题（图示看起来变形/不对头）
1. **模型矩阵缩放可能有问题**：目前整体看起来视觉比例依旧不对。请严格核对上游 第一人称视角/Aim Pose 变换下应用的基础 Scale、Translation 和 Rotation，以及它们在 1.12.2 Renderer 中被乘入的顺序。
2. **检查 FOV 与 Screen 投影**：模型如果拉伸或者不协调，有可能是 `FirstPersonRenderGunEvent` 里的视角设置或者 `GlStateManager.scale()` 用错。

### 二、 动画状态机（切枪与收枪）
1. **切枪动画问题**：枪切到另一把枪时，没触发 `put_away`（收回）旧枪动画。只有切到空手时存在收枪动画。必须保证 `put_away` 在换主手物品（如果是两把枪）时的生命周期正确流转。
2. **收枪动画（put_away）毛刺**：当前收枪时瞬间会看到手部复位再收回（似乎走了错误的 fallback 或者原始模型默认姿态露出）。请修复切枪瞬间的状态清空平滑过渡问题。

### 三、 屏蔽原版挥手动作与卡死问题
1. **点左键上下抽搐**：在某些枪按左键会疯狂抽搐。你必须拦截原版 Minecraft 的 **挖块/攻击时的手臂挥动 (arm swing / swingProgress)** 进度，强制归零，否则原版逻辑会和枪体动画冲突叠加。
2. **有些枪开不出火卡死（例如 timeless50）**：在没有报错的情况下点了没反应，可能是这把枪的动作资源缺失或者动画卡死导致了后续状态闭塞，请找出这类开不出火且抽搐的异常处理分支。

### 四、 子弹/弹道渲染问题（白方块与没有拖尾）
1. **子弹目前是个飘在空中的白方块**：渲染出的实体并未和枪管/落点对齐，飞行方向表现是乱飘的（尽管落点判断正确）。
2. **没有拖尾（Trail）**：上游 TACZ `EntityKineticBullet` 会有对应的专门渲染器 `RenderKineticBullet` 来绘制细长的火光发光射线和烟雾拖尾。你需要完善此特性的 1.12.2 Porting，使子弹有速度感且视觉不偏移。

## 默认关注范围
- `src/main/kotlin/com/tacz/legacy/client/event/FirstPersonRenderGunEvent.kt`
- `src/main/kotlin/com/tacz/legacy/client/gameplay/LegacyClientPlayerGunBridge.kt`
- `src/main/kotlin/com/tacz/legacy/client/render/item/GunGeometryRenderer.kt`
- `src/main/kotlin/com/tacz/legacy/client/render/entity/RenderKineticBullet.kt` (子弹渲染器)
- 动画状态机切枪/卸载生命周期控制部分。

## 执行要求
- 必须找到原版臂膀挥动被触发的源头并用 Event Cancel 彻底拦截。
- 子弹必须渲染成“高亮直线拖尾”而不是漂浮的方块。必须用代码将起/终点做插值。
- 切枪行为要在代码层面强制触发生命周期注销或切枪过渡。
