---
name: "TACZ Stage Client UX GUI & I18n"
description: "Fix TACZ-Legacy Client UX: Revert/Refactor GunRefitScreen to previous working structure."
agent: "TACZ Migration"
argument-hint: "本轮强绑定：GUI严重变形，需要回滚或重构 GunRefitScreen 回复正常布局。"
---
修复 `TACZ` **沉浸式改装 GUI (GunRefitScreen) 渲染严重变形错位的问题，以及部分 I18N 提示**。

## ⚠️ 第二轮通报与新问题修复（本轮重点）
改装界面 (GunRefitScreen) 在本轮迭代后不仅没有好转，反而排版彻底崩溃（见图2），这是因为你前一轮对布局做了大量不兼容的改动，且由于没有 GUI 测试框架而直接蒙眼盲写。

**本轮要求非常简单且强硬**：
1. **彻底废弃之前错误的重构思路**，参考旧版稳定代码（已提取到 `.agent-workspace/old-gui-reference/`）！旧版代码来自 `89fc159`（origin/master 初始检查点），这是 GUI 布局最后一次正常工作的版本。你也可以通过 `git show 89fc159:src/main/kotlin/com/tacz/legacy/client/gui/<file>` 查看原始文件。把之前的 `GunRefitScreen` 以及底下的组件 (`AttachmentSlot`, `InventoryWidget` 等) 的基础绘图和排版参数重新原样搬回来。
2. **切忌盲目套入现代 Flex 布局**：1.12.2 是基于绝对坐标系 `TaczModularUI` 或基础坐标进行画图的。如果老版本能正常跑，说明 `drawBackground` 的原点和基于中心点（`width / 2`）相对偏移才是对的。
3. **枪械渲染 (GunModel) 的视口投影**：目前界面里的枪械图标由于透视/拉伸变窄变扁，并且没有对发光部分做出正确阴影投射。还原界面的同时把枪模显示的 `GlStateManager.scale()` 或 `RenderHelper.enableGUIStandardItemLighting()` 也修好。

## 默认关注范围
- `src/main/kotlin/com/tacz/legacy/client/gui/GunRefitScreen.kt`
- `src/main/kotlin/com/tacz/legacy/client/gui/components/**`
- `src/main/kotlin/com/tacz/legacy/client/gui/TACZGuiModelPreviewRenderer.kt` — 其中 `previewViewMatrix()` 目前是临时 fallback（返回单位矩阵），需要把上游 `BedrockGunModel.getRefitAttachmentViewPath(AttachmentType)` 移植过来才能让改装预览视角正常工作
- 从 `.agent-workspace/old-gui-reference/` 查阅旧版 GUI 文件（来自 `89fc159`）
- 旧版 GUI 代码已提取到 `.agent-workspace/old-gui-reference/` 供参考（只读，不要修改该目录）

## 额外移植任务：BedrockGunModel.getRefitAttachmentViewPath
上游 TACZ 的 `BedrockGunModel.java` 有一个 `getRefitAttachmentViewPath(AttachmentType)` 方法，用于在改装 GUI 中获取枪附件预览的骨骼路径。Legacy 的 `BedrockGunModel` 还没有此方法。你需要参考上游实现把它移植过来，然后在 `TACZGuiModelPreviewRenderer.previewViewMatrix()` 中去掉 `// TODO` 并恢复真实调用。

## 执行要求
- 你的唯一目标是把屏幕变得哪怕和最老的可用版本一样，也不允许再出现右半边全部空白、居中框拉伸、黑边框乱入的诡异状态。如果不确定，直接 Copy 旧版。
