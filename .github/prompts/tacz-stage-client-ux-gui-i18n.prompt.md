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
1. **彻底废弃之前错误的重构思路**，建议直接去抄 Git 的老提交！用户给定的旧稳定 Commit 是 `6302dff23e380f4e7b1d54395723f9d4bfc04277`。你需要回到那个版本（通过 `git show 6302dff23e380f4e7b1d54395723f9d4bfc04277:path_to_old_file` 或直接查阅历史），把之前的 `GunRefitScreen` 以及底下的组件 (`AttachmentSlot`, `InventoryWidget` 等) 的基础绘图和排版参数重新原样搬回来。
2. **切忌盲目套入现代 Flex 布局**：1.12.2 是基于绝对坐标系 `TaczModularUI` 或基础坐标进行画图的。如果老版本能正常跑，说明 `drawBackground` 的原点和基于中心点（`width / 2`）相对偏移才是对的。
3. **枪械渲染 (GunModel) 的视口投影**：目前界面里的枪械图标由于透视/拉伸变窄变扁，并且没有对发光部分做出正确阴影投射。还原界面的同时把枪模显示的 `GlStateManager.scale()` 或 `RenderHelper.enableGUIStandardItemLighting()` 也修好。

## 默认关注范围
- `src/main/kotlin/com/tacz/legacy/client/gui/GunRefitScreen.kt`
- `src/main/kotlin/com/tacz/legacy/client/gui/components/**`
- 从 `6302dff23e380f4e7b1d54395723f9d4bfc04277` 里查阅旧版 `GunRefitScreen` 相关文件

## 执行要求
- 你的唯一目标是把屏幕变得哪怕和最老的可用版本一样，也不允许再出现右半边全部空白、居中框拉伸、黑边框乱入的诡异状态。如果不确定，直接 Copy 旧版。
